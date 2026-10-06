$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'demo-common.ps1')

$state = Get-DemoState
$failed = $false
foreach ($service in @('flutter', 'backend', 'ai')) {
    $record = Get-DemoProperty $state.processes $service
    if ($null -eq $record) { continue }
    $process = Get-DemoOwnedProcess $record $service
    if ($null -eq $process) {
        Write-Host "${service}: recorded process is gone or no longer matches; skipped"
        continue
    }
    try {
        Stop-DemoProcessTree $process
    } catch {
        Write-Warning "Could not stop $service PID $($process.Id); check it manually."
        $failed = $true
        continue
    }
    Write-Host "$service stopped (PID $($process.Id))"
    Remove-DemoProperty $state.processes $service
    Save-DemoState $state
}

foreach ($service in @('mailpit', 'mysql')) {
    $record = Get-DemoProperty $state.containers $service
    if ($null -eq $record) { continue }
    try {
        $null = Get-DemoContainers
        $currentId = Get-DemoContainerId $record.name
        if ($currentId -ne $record.id) {
            Write-Warning "$service container identity changed; skipped."
            Remove-DemoProperty $state.containers $service
            Save-DemoState $state
            continue
        }
        docker stop $record.id 1>$null 2>$null
        if ($LASTEXITCODE -ne 0) { throw 'docker stop failed' }
        Write-Host "$service container stopped: $($record.name)"
        Remove-DemoProperty $state.containers $service
        Save-DemoState $state
    } catch {
        Write-Warning "Could not stop $service container; check Docker access."
        $failed = $true
    }
}
if ($failed) { exit 1 }
