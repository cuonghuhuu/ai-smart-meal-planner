param(
    [Parameter(Mandatory)][ValidateSet('ai', 'backend', 'flutter')][string]$Service,
    [Parameter(Mandatory)][string]$Root,
    [string]$Python
)

$ErrorActionPreference = 'Stop'
switch ($Service) {
    'ai' {
        Set-Location $Root
        $env:PYTHONPATH = Join-Path $Root 'ai_service'
        & $Python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
    }
    'backend' {
        Set-Location (Join-Path $Root 'backend')
        & mvn -B -ntp spring-boot:run '-Dspring-boot.run.profiles=local'
    }
    'flutter' {
        Set-Location (Join-Path $Root 'mobile_app')
        & flutter run -d chrome --web-hostname localhost --web-port 3000 '--dart-define=APP_ENV=development' '--dart-define=API_BASE_URL=http://localhost:8080'
    }
}
exit $LASTEXITCODE
