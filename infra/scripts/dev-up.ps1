param(
    [string]$EnvFile = ".env.local",
    [switch]$NoBuild
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$resolvedEnvFile = Join-Path $repoRoot $EnvFile

if (-not (Test-Path $resolvedEnvFile)) {
    throw "Env file not found: $resolvedEnvFile. Copy .env.local.example to .env.local first."
}

$args = @(
    "--env-file", $resolvedEnvFile,
    "-f", (Join-Path $repoRoot "docker-compose.yml"),
    "up",
    "-d"
)

if (-not $NoBuild) {
    $args += "--build"
}

docker compose @args
docker compose --env-file $resolvedEnvFile -f (Join-Path $repoRoot "docker-compose.yml") ps

$backendHealthUrl = Get-Content $resolvedEnvFile |
    Where-Object { $_ -match '^ALEX_BACKEND_HEALTHCHECK_URL=' } |
    ForEach-Object { $_.Split('=')[1] } |
    Select-Object -First 1

$waitScript = Join-Path $PSScriptRoot "wait-for-http.ps1"
if (-not $backendHealthUrl) {
    $backendHealthUrl = "http://localhost:8080/actuator/health"
}

& $waitScript -Url $backendHealthUrl -TimeoutSeconds 180

Write-Host ""
$backendPort = Get-Content $resolvedEnvFile |
    Where-Object { $_ -match '^ALEX_BACKEND_PORT=' } |
    ForEach-Object { $_.Split('=')[1] } |
    Select-Object -First 1

if (-not $backendPort) {
    $backendPort = "8080"
}

Write-Host "Backend: http://localhost:$backendPort"
Write-Host "Frontend Expo env template: frontend/.env.example"
