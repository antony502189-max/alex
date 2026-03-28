param(
    [string]$EnvFile = ".env.deploy",
    [string]$BackupRoot = ".\backups"
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$resolvedEnvFile = Join-Path $repoRoot $EnvFile
$composeFile = Join-Path $repoRoot "docker-compose.deploy.yml"

if (-not (Test-Path $resolvedEnvFile)) {
    throw "Env file not found: $resolvedEnvFile"
}

function Get-EnvValue {
    param(
        [string]$Path,
        [string]$Key,
        [string]$DefaultValue = ""
    )

    $value = Get-Content $Path |
        Where-Object { $_ -match "^$Key=" } |
        ForEach-Object { $_.Split('=', 2)[1] } |
        Select-Object -First 1

    if ($value) {
        return $value
    }

    return $DefaultValue
}

$projectName = Get-EnvValue -Path $resolvedEnvFile -Key "COMPOSE_PROJECT_NAME" -DefaultValue "alex-prod"
$healthcheckUrl = Get-EnvValue -Path $resolvedEnvFile -Key "ALEX_BACKEND_HEALTHCHECK_URL" -DefaultValue "http://localhost:8080/actuator/health"
$timestamp = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
$backupBase = Join-Path $repoRoot $BackupRoot
$backupDir = Join-Path $backupBase $timestamp
$volumes = @("postgres-data", "cassandra-data", "minio-data", "caddy-data", "caddy-config", "prometheus-data", "alertmanager-data", "grafana-data")

New-Item -ItemType Directory -Force -Path $backupDir | Out-Null

docker compose --env-file $resolvedEnvFile -f $composeFile stop

foreach ($volume in $volumes) {
    $fullVolume = "${projectName}_${volume}"
    docker volume inspect $fullVolume | Out-Null
    docker run --rm `
        -v "${fullVolume}:/source:ro" `
        -v "${backupDir}:/backup" `
        alpine:3.20 `
        sh -c "cd /source && tar -czf /backup/${volume}.tgz ."
}

@(
    "project_name=$projectName"
    "timestamp=$timestamp"
    "env_file=$EnvFile"
    "healthcheck_url=$healthcheckUrl"
    "volumes=$($volumes -join ',')"
) | Set-Content -Path (Join-Path $backupDir "manifest.txt")

docker compose --env-file $resolvedEnvFile -f $composeFile up -d
& (Join-Path $PSScriptRoot "wait-for-http.ps1") -Url $healthcheckUrl -TimeoutSeconds 180

Write-Host "Backup completed: $backupDir"
