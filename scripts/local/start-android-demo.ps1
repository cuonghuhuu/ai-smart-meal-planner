param(
    [string]$DeviceId = 'emulator-5554'
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

foreach ($command in @('adb', 'flutter')) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "Required command '$command' was not found on PATH."
    }
}

$devices = @(adb devices | Select-String -Pattern "^$([regex]::Escape($DeviceId))\s+device$")
if ($devices.Count -eq 0) {
    throw "Android device $DeviceId is not connected. Start LDPlayer and confirm 'adb devices'."
}

$backend = Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue
if ($null -eq $backend) {
    throw 'Spring Boot is not listening on host port 8080. Run start-demo.ps1 first.'
}

adb -s $DeviceId reverse tcp:8080 tcp:8080 | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "adb reverse failed for $DeviceId."
}

Set-Location (Join-Path $root 'mobile_app')
Write-Host "Starting Flutter on $DeviceId -> host Spring Boot through adb reverse :8080" -ForegroundColor Green
& flutter run -d $DeviceId '--dart-define=APP_ENV=development' '--dart-define=API_BASE_URL=http://127.0.0.1:8080'
exit $LASTEXITCODE
