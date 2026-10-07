Set-StrictMode -Version Latest

$script:DemoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$script:DemoStateDirectory = Join-Path $script:DemoRoot '.local-demo'
$script:DemoStatePath = Join-Path $script:DemoStateDirectory 'state.json'

function Get-DemoState {
    if (Test-Path $script:DemoStatePath) {
        return (Get-Content $script:DemoStatePath -Raw | ConvertFrom-Json)
    }
    return [pscustomobject]@{ processes = [pscustomobject]@{}; containers = [pscustomobject]@{} }
}

function Save-DemoState($state) {
    New-Item -ItemType Directory -Force -Path $script:DemoStateDirectory | Out-Null
    $tempPath = Join-Path $script:DemoStateDirectory 'state.tmp'
    $state | ConvertTo-Json -Depth 5 | Set-Content -Path $tempPath -Encoding UTF8
    Move-Item -Force -Path $tempPath -Destination $script:DemoStatePath
}

function Get-DemoProperty($object, [string]$name) {
    if ($null -eq $object) { return $null }
    $property = $object.PSObject.Properties[$name]
    if ($null -eq $property) { return $null }
    return $property.Value
}

function Set-DemoProperty($object, [string]$name, $value) {
    $object | Add-Member -NotePropertyName $name -NotePropertyValue $value -Force
}

function Remove-DemoProperty($object, [string]$name) {
    $null = $object.PSObject.Properties.Remove($name)
}

function Get-DemoOwnedProcess($record, [string]$service) {
    if ($null -eq $record) { return $null }
    $process = Get-Process -Id ([int]$record.pid) -ErrorAction SilentlyContinue
    if ($null -eq $process) { return $null }
    try {
        if ($process.StartTime.ToUniversalTime().ToString('o') -ne $record.startedUtc) { return $null }
        $details = Get-CimInstance Win32_Process -Filter "ProcessId = $($record.pid)" -ErrorAction Stop
        if ($details.CommandLine -notlike '*demo-worker.ps1*' -or
            $details.CommandLine -notlike "*-Service $service*") { return $null }
    } catch { return $null }
    return $process
}

function Get-DemoDescendantIds([int]$rootPid) {
    $all = @(Get-CimInstance Win32_Process -ErrorAction Stop)
    $result = @($rootPid)
    do {
        $children = @($all | Where-Object {
            $result -contains [int]$_.ParentProcessId -and $result -notcontains [int]$_.ProcessId
        } | ForEach-Object { [int]$_.ProcessId })
        $result += $children
    } while ($children.Count -gt 0)
    return $result
}

function Stop-DemoProcessTree($process) {
    $ErrorActionPreference = 'Continue'
    taskkill.exe /PID $process.Id /T /F 1>$null 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "Could not stop script-owned PID $($process.Id)."
    }
}

function Get-DemoPortOwner([int]$port) {
    $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue)
    if ($listeners.Count -eq 0) { return $null }
    return [int]$listeners[0].OwningProcess
}

function Assert-DemoPortFree([int]$port, [string]$service) {
    $owner = Get-DemoPortOwner $port
    if ($null -ne $owner) {
        throw "Port $port is occupied by PID $owner; cannot start $service. Stop that process yourself or choose a free port."
    }
}

function Test-DemoHttp([string]$url, [string]$expectedStatus) {
    try {
        $response = Invoke-RestMethod -Uri $url -TimeoutSec 2 -ErrorAction Stop
        return $response.status -eq $expectedStatus
    } catch { return $false }
}

function Get-DemoContainers {
    $ErrorActionPreference = 'Continue'
    $lines = @(docker container ls -a --format '{{json .}}' 2>$null)
    if ($LASTEXITCODE -ne 0) { throw 'Docker is unavailable. Start Docker Desktop and check access to the Docker engine.' }
    return @($lines | Where-Object { $_ -match '^\{' } | ForEach-Object { $_ | ConvertFrom-Json })
}

function Get-DemoContainerByPort([object[]]$containers, [int]$port) {
    return $containers | Where-Object { $_.Ports -match "(?<!\d):${port}->" } |
        Select-Object -First 1
}

function Test-DemoOwnedPort($state, [string]$service, [int]$port) {
    $record = Get-DemoProperty $state.processes $service
    $process = Get-DemoOwnedProcess $record $service
    if ($null -eq $process) { return $false }
    $portOwner = Get-DemoPortOwner $port
    return $null -ne $portOwner -and
        @(Get-DemoDescendantIds $process.Id) -contains $portOwner
}

function Get-DemoContainerId([string]$name) {
    $ErrorActionPreference = 'Continue'
    $id = @(docker inspect --format '{{.Id}}' $name 2>$null)
    if ($LASTEXITCODE -ne 0 -or $id.Count -eq 0) { return $null }
    return [string]$id[0]
}
