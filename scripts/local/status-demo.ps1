$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'demo-common.ps1')

$rows = @()
try {
    $containers = @(Get-DemoContainers)
    $mysql = Get-DemoContainerByPort $containers 3307
    $mailpit = Get-DemoContainerByPort $containers 1025
    $rows += [pscustomobject]@{
        Service = 'MySQL :3307'; Status = if ($null -ne $mysql -and $mysql.Status -match '^Up ' -and
            $mysql.Image -like 'mysql:8.*') { 'RUNNING' } else { 'DOWN' }
        Detail = if ($null -ne $mysql) { $mysql.Names } else { '-' }
    }
    $rows += [pscustomobject]@{
        Service = 'Mailpit :1025/:8025'; Status = if ($null -ne $mailpit -and
            $mailpit.Status -match '^Up ' -and $mailpit.Ports -match '(?<!\d):8025->') {
            'RUNNING'
        } else { 'DOWN' }
        Detail = if ($null -ne $mailpit) { $mailpit.Names } else { '-' }
    }
} catch {
    $rows += [pscustomobject]@{ Service = 'MySQL :3307'; Status = 'UNKNOWN'; Detail = 'Docker unavailable' }
    $rows += [pscustomobject]@{ Service = 'Mailpit :1025/:8025'; Status = 'UNKNOWN'; Detail = 'Docker unavailable' }
}
$rows += [pscustomobject]@{
    Service = 'FastAPI :8000'; Status = if (Test-DemoHttp 'http://127.0.0.1:8000/health' 'ok') {
        'HEALTHY'
    } else { 'DOWN' }; Detail = '/health'
}
$rows += [pscustomobject]@{
    Service = 'Spring Boot :8080'; Status = if (Test-DemoHttp 'http://localhost:8080/actuator/health' 'UP') {
        'HEALTHY'
    } else { 'DOWN' }; Detail = '/actuator/health'
}
$webPid = Get-DemoPortOwner 3000
$rows += [pscustomobject]@{
    Service = 'Flutter Web :3000'; Status = if ($null -ne $webPid) { 'LISTENING' } else { 'DOWN' }
    Detail = if ($null -ne $webPid) { "PID $webPid" } else { '-' }
}
$rows | Format-Table -AutoSize
if (@($rows | Where-Object { $_.Status -in @('DOWN', 'UNKNOWN') }).Count -gt 0) { exit 1 }
