param([switch]$NoPrompt)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'demo-common.ps1')

function Require-Command([string]$name) {
    if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
        throw "Required command '$name' was not found on PATH."
    }
}

function Read-DemoSecret([string]$name) {
    $value = [Environment]::GetEnvironmentVariable($name, 'Process')
    if (-not [string]::IsNullOrWhiteSpace($value)) { return $value }
    if ($NoPrompt) { throw "$name is required in the process environment." }
    $secure = Read-Host "Enter $name for this local demo" -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

function Wait-Demo([string]$name, [int]$seconds, [scriptblock]$check) {
    $deadline = (Get-Date).AddSeconds($seconds)
    do {
        if (& $check) { Write-Host "$name ready"; return }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "$name did not become ready within $seconds seconds. See .local-demo/logs/."
}

function Assert-DemoProcessAlive($state, [string]$service) {
    if ($null -eq (Get-DemoOwnedProcess (Get-DemoProperty $state.processes $service) $service)) {
        throw "$service exited before becoming ready. See .local-demo/logs/$service.err.log."
    }
}

function Ensure-DemoContainer([string]$service, [string]$name, [string]$image,
    [int[]]$ports, [string[]]$runOptions, $state) {
    $containers = @(Get-DemoContainers)
    $candidate = Get-DemoContainerByPort $containers $ports[0]
    if ($null -eq $candidate) {
        $candidate = @($containers | Where-Object { $_.Names -eq $name }) | Select-Object -First 1
    }
    if ($null -ne $candidate) {
        $imagePattern = if ($service -eq 'mysql') { 'mysql:8.*' } else { 'axllent/mailpit:*' }
        if ($candidate.Image -notlike $imagePattern) {
            throw "Container $($candidate.Names) on the $service slot uses unexpected image $($candidate.Image)."
        }
        if ($candidate.Status -notmatch '^Up ') {
            foreach ($port in $ports) { Assert-DemoPortFree $port $service }
            $ErrorActionPreference = 'Continue'
            try { docker start $candidate.Names 2>$null | Out-Null }
            finally { $ErrorActionPreference = 'Stop' }
            if ($LASTEXITCODE -ne 0) { throw "Could not start $service container $($candidate.Names)." }
            Set-DemoProperty $state.containers $service ([pscustomobject]@{
                id = (Get-DemoContainerId $candidate.Names); name = $candidate.Names
            })
            Save-DemoState $state
            $started = @(Get-DemoContainers | Where-Object { $_.Names -eq $candidate.Names }) |
                Select-Object -First 1
            foreach ($port in $ports) {
                if ($null -eq $started -or $started.Ports -notmatch "(?<!\d):${port}->") {
                    throw "Container $($candidate.Names) does not publish required host port $port."
                }
            }
        } else {
            foreach ($port in $ports) {
                if ($candidate.Ports -notmatch "(?<!\d):${port}->") {
                    throw "Container $($candidate.Names) does not publish required host port $port."
                }
            }
            Write-Host "$service container already running: $($candidate.Names)"
        }
        return
    }
    foreach ($port in $ports) { Assert-DemoPortFree $port $service }
    $ErrorActionPreference = 'Continue'
    try { $id = @(docker run -d --name $name @runOptions $image 2>$null) }
    finally { $ErrorActionPreference = 'Stop' }
    if ($LASTEXITCODE -ne 0) { throw "Could not create $service container. Check Docker Desktop and image availability." }
    Set-DemoProperty $state.containers $service ([pscustomobject]@{ id = [string]$id[0]; name = $name })
    Save-DemoState $state
    Write-Host "$service container started: $name"
}

function Ensure-DemoProcess([string]$service, [int]$port, [string]$python, $state) {
    $record = Get-DemoProperty $state.processes $service
    $owned = Get-DemoOwnedProcess $record $service
    $portOwner = Get-DemoPortOwner $port
    if ($null -ne $owned) {
        $descendants = @(Get-DemoDescendantIds $owned.Id)
        if ($null -ne $portOwner -and $descendants -notcontains $portOwner) {
            throw "Port $port belongs to unrelated PID $portOwner; $service cannot be reused."
        }
        if ((Get-DemoProperty $record 'stdinClosed') -eq $true) {
            Write-Host "$service process already running (PID $($owned.Id))"
            return
        }
        Write-Host "Restarting script-owned $service worker to detach terminal input"
        Stop-DemoProcessTree $owned
        $deadline = (Get-Date).AddSeconds(10)
        while ($null -ne (Get-DemoPortOwner $port) -and (Get-Date) -lt $deadline) {
            Start-Sleep -Milliseconds 250
        }
        $portOwner = Get-DemoPortOwner $port
    }
    if ($null -ne $portOwner) {
        throw "Port $port is occupied by unexpected PID $portOwner. Stop it yourself before starting $service."
    }
    Remove-DemoProperty $state.processes $service
    $worker = Join-Path $PSScriptRoot 'demo-worker.ps1'
    $logs = Join-Path $script:DemoStateDirectory 'logs'
    New-Item -ItemType Directory -Force -Path $logs | Out-Null
    $inputFile = Join-Path $script:DemoStateDirectory 'empty.stdin'
    if (-not (Test-Path -LiteralPath $inputFile)) {
        New-Item -ItemType File -Path $inputFile -ErrorAction Stop | Out-Null
    }
    if ((Get-Item -LiteralPath $inputFile).Length -ne 0) {
        throw 'The local demo input file is not empty: .local-demo/empty.stdin'
    }
    $arguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ('"' + $worker + '"'),
        '-Service', $service, '-Root', ('"' + $script:DemoRoot + '"'))
    if ($service -eq 'ai') { $arguments += @('-Python', ('"' + $python + '"')) }
    $process = Start-Process -FilePath 'powershell.exe' -ArgumentList $arguments -PassThru `
        -WindowStyle Hidden -RedirectStandardInput $inputFile `
        -RedirectStandardOutput (Join-Path $logs "$service.out.log") `
        -RedirectStandardError (Join-Path $logs "$service.err.log")
    Set-DemoProperty $state.processes $service ([pscustomobject]@{
        pid = $process.Id; startedUtc = $process.StartTime.ToUniversalTime().ToString('o')
        stdinClosed = $true
    })
    Save-DemoState $state
    Write-Host "$service process started (PID $($process.Id))"
}

try {
    foreach ($command in @('docker', 'java', 'mvn', 'flutter')) { Require-Command $command }
    $python = Join-Path $script:DemoRoot '.venv\Scripts\python.exe'
    if (-not (Test-Path $python)) {
        $python = Join-Path $script:DemoRoot 'ai_service\.venv\Scripts\python.exe'
    }
    if (-not (Test-Path $python)) { throw 'Python virtual environment missing. Create .venv or ai_service/.venv and install ai_service[vision].' }
    & $python --version | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Python virtual environment could not run.' }
    $null = Get-DemoContainers
    $password = Read-DemoSecret 'DB_PASSWORD'
    if ([string]::IsNullOrWhiteSpace($password)) { throw 'DB_PASSWORD cannot be empty.' }
    $token = Read-DemoSecret 'AI_INTERNAL_SERVICE_TOKEN'
    if ($token.Length -lt 32) { throw 'AI_INTERNAL_SERVICE_TOKEN must contain at least 32 characters.' }
    $env:DB_PASSWORD = $password
    $env:AI_INTERNAL_SERVICE_TOKEN = $token
    $state = Get-DemoState

    $env:MYSQL_PASSWORD = $password
    $randomBytes = New-Object byte[] 48
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($randomBytes) } finally { $generator.Dispose() }
    $env:MYSQL_ROOT_PASSWORD = [Convert]::ToBase64String($randomBytes)
    try {
        Ensure-DemoContainer 'mysql' 'smartmeal-demo-mysql' 'mysql:8.4.11' @(3307) @(
            '-p', '127.0.0.1:3307:3306', '-e', 'MYSQL_DATABASE=smart_meal_planner',
            '-e', 'MYSQL_USER=smartmeal', '-e', 'MYSQL_PASSWORD', '-e', 'MYSQL_ROOT_PASSWORD'
        ) $state
    } finally {
        Remove-Item Env:MYSQL_PASSWORD, Env:MYSQL_ROOT_PASSWORD -ErrorAction SilentlyContinue
    }
    $env:MYSQL_PWD = $password
    try {
        Wait-Demo 'MySQL' 90 {
            $container = Get-DemoContainerByPort (Get-DemoContainers) 3307
            if ($null -eq $container -or $container.Status -notmatch '^Up ') { return $false }
            $ErrorActionPreference = 'Continue'
            docker exec -e MYSQL_PWD $container.Names mysql -h 127.0.0.1 -u smartmeal `
                -D smart_meal_planner -N -e 'SELECT 1' 1>$null 2>$null
            return $LASTEXITCODE -eq 0
        }
    } finally { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }

    Ensure-DemoContainer 'mailpit' 'smartmeal-demo-mailpit' 'axllent/mailpit:latest' @(1025, 8025) @(
        '-p', '127.0.0.1:1025:1025', '-p', '127.0.0.1:8025:8025'
    ) $state
    Wait-Demo 'Mailpit' 30 { $null -ne (Get-DemoPortOwner 8025) }

    $env:AI_VISION_DEVICE = if ($env:AI_VISION_DEVICE) { $env:AI_VISION_DEVICE } else { '0' }
    $env:AI_RECOGNITION_CONFIDENCE = '0.10'
    Ensure-DemoProcess 'ai' 8000 $python $state
    Wait-Demo 'FastAPI' 90 {
        Assert-DemoProcessAlive $state 'ai'
        (Test-DemoOwnedPort $state 'ai' 8000) -and
        (Test-DemoHttp 'http://127.0.0.1:8000/health' 'ok')
    }

    $env:DB_URL = 'jdbc:mysql://localhost:3307/smart_meal_planner?connectionTimeZone=UTC'
    $env:DB_USERNAME = 'smartmeal'
    $env:AI_BASE_URL = 'http://127.0.0.1:8000'
    $env:CORS_ALLOWED_ORIGINS = 'http://localhost:3000'
    Ensure-DemoProcess 'backend' 8080 $python $state
    Wait-Demo 'Spring Boot' 180 {
        Assert-DemoProcessAlive $state 'backend'
        (Test-DemoOwnedPort $state 'backend' 8080) -and
        (Test-DemoHttp 'http://localhost:8080/actuator/health' 'UP')
    }

    Ensure-DemoProcess 'flutter' 3000 $python $state
    Wait-Demo 'Flutter Web' 180 {
        Assert-DemoProcessAlive $state 'flutter'
        Test-DemoOwnedPort $state 'flutter' 3000
    }
    Write-Host 'Demo ready: http://localhost:3000  Mailpit: http://localhost:8025'
} catch {
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
