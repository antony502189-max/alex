param(
    [string]$Tag = "alex-backend:validate",
    [switch]$NoCache
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$dockerfile = Join-Path $repoRoot "backend\Dockerfile"
$buildContext = Join-Path $repoRoot "backend"

if (-not (Test-Path $dockerfile)) {
    throw "Backend Dockerfile not found: $dockerfile"
}

$env:DOCKER_BUILDKIT = "1"

$buildArgs = @("build", "-f", $dockerfile, "-t", $Tag)
if ($NoCache) {
    $buildArgs += "--no-cache"
}
$buildArgs += $buildContext

docker @buildArgs
docker run --rm --entrypoint sh $Tag -c "test -s /app/app.jar"
docker image inspect $Tag --format "{{.Id}}"
