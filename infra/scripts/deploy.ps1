param(
    [string]$EnvFile = ".env.deploy"
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$resolvedEnvFile = Join-Path $repoRoot $EnvFile

if (-not (Test-Path $resolvedEnvFile)) {
    throw "Env file not found: $resolvedEnvFile. Copy .env.deploy.example to .env.deploy first."
}

$composeFiles = @(
    "-f", (Join-Path $repoRoot "docker-compose.deploy.yml")
)

docker compose --env-file $resolvedEnvFile @composeFiles pull
docker compose --env-file $resolvedEnvFile @composeFiles up -d
docker compose --env-file $resolvedEnvFile @composeFiles ps

$backendHealthUrl = Get-Content $resolvedEnvFile |
    Where-Object { $_ -match '^ALEX_BACKEND_HEALTHCHECK_URL=' } |
    ForEach-Object { $_.Split('=')[1] } |
    Select-Object -First 1

if (-not $backendHealthUrl) {
    $backendHealthUrl = "http://localhost:8080/actuator/health"
}

& (Join-Path $PSScriptRoot "wait-for-http.ps1") -Url $backendHealthUrl -TimeoutSeconds 180
