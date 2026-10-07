param(
    [Parameter(Mandatory = $true)]
    [string]$Email
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'demo-common.ps1')

$emailNormalized = $Email.Trim().ToLowerInvariant()
if ($emailNormalized -notmatch '^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$') {
    throw 'Email is not valid.'
}

$password = [Environment]::GetEnvironmentVariable('DB_PASSWORD', 'Process')
if ([string]::IsNullOrWhiteSpace($password)) {
    $secure = Read-Host 'Enter DB_PASSWORD for this local demo' -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}
if ([string]::IsNullOrWhiteSpace($password)) {
    throw 'DB_PASSWORD cannot be empty.'
}

$container = Get-DemoContainerByPort (Get-DemoContainers) 3307
if ($null -eq $container -or $container.Status -notmatch '^Up ') {
    throw 'The local demo MySQL container is not running on host port 3307.'
}

$safeEmail = $emailNormalized.Replace("'", "''")
$sql = @"
INSERT IGNORE INTO user_roles (user_id, role_id, granted_by)
SELECT u.id, r.id, NULL
FROM users u
JOIN roles r ON r.code = 'ROLE_ADMIN'
WHERE u.email_normalized = '$safeEmail';

SELECT u.email, r.code
FROM users u
JOIN user_roles ur ON ur.user_id = u.id
JOIN roles r ON r.id = ur.role_id
WHERE u.email_normalized = '$safeEmail'
ORDER BY r.code;
"@

$env:MYSQL_PWD = $password
try {
    $output = docker exec -e MYSQL_PWD $container.Names mysql -h 127.0.0.1 -u smartmeal -D smart_meal_planner -N -e $sql
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not promote the account.'
    }
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}

if (-not ($output -match 'ROLE_ADMIN')) {
    throw "No registered account was found for $emailNormalized."
}

Write-Host "Admin role granted to $emailNormalized" -ForegroundColor Green
$output | ForEach-Object { Write-Host $_ }
Write-Host 'Sign out and sign in again so a new JWT contains ROLE_ADMIN.'
