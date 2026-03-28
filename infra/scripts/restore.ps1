param(
    [string]$EnvFile = ".env.deploy",
    [Parameter(Mandatory = $true)][string]$BackupDir
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$resolvedEnvFile = Join-Path $repoRoot $EnvFile
$composeFile = Join-Path $repoRoot "docker-compose.deploy.yml"
$resolvedBackupDir = if ([System.IO.Path]::IsPathRooted($BackupDir)) { $BackupDir } else { Join-Path $repoRoot $BackupDir }

if (-not (Test-Path $resolvedEnvFile)) {
    throw "Env file not found: $resolvedEnvFile"
}

if (-not (Test-Path $resolvedBackupDir)) {
    throw "Backup directory not found: $resolvedBackupDir"
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
$volumes = @("postgres-data", "cassandra-data", "minio-data", "caddy-data", "caddy-config", "prometheus-data", "alertmanager-data", "grafana-data")

docker compose --env-file $resolvedEnvFile -f $composeFile down

foreach ($volume in $volumes) {
    $archivePath = Join-Path $resolvedBackupDir "${volume}.tgz"
    if (-not (Test-Path $archivePath)) {
        throw "Missing archive: $archivePath"
    }

    $fullVolume = "${projectName}_${volume}"
    docker volume create $fullVolume | Out-Null

    docker run --rm `
        -v "${fullVolume}:/target" `
        alpine:3.20 `
        sh -c "rm -rf /target/* /target/.[!.]* /target/..?* 2>/dev/null || true"

    docker run --rm `
        -v "${fullVolume}:/target" `
        -v "${resolvedBackupDir}:/backup:ro" `
        alpine:3.20 `
        sh -c "tar -xzf /backup/${volume}.tgz -C /target"
}

docker compose --env-file $resolvedEnvFile -f $composeFile up -d
& (Join-Path $PSScriptRoot "wait-for-http.ps1") -Url $healthcheckUrl -TimeoutSeconds 180

Write-Host "Restore completed from: $resolvedBackupDir"
