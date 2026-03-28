param(
    [string]$EnvFile = ".env.local",
    [switch]$RemoveVolumes
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$resolvedEnvFile = Join-Path $repoRoot $EnvFile

if (-not (Test-Path $resolvedEnvFile)) {
    throw "Env file not found: $resolvedEnvFile."
}

$args = @(
    "--env-file", $resolvedEnvFile,
    "-f", (Join-Path $repoRoot "docker-compose.yml"),
    "down"
)

if ($RemoveVolumes) {
    $args += "--volumes"
}

docker compose @args
