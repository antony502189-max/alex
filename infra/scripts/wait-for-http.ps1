param(
    [Parameter(Mandatory = $true)][string]$Url,
    [int]$TimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)

while ((Get-Date) -lt $deadline) {
    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 10
        if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
            Write-Host "HTTP health check is ready: $Url"
            exit 0
        }
    } catch {
        Start-Sleep -Seconds 3
        continue
    }

    Start-Sleep -Seconds 3
}

throw "Timed out waiting for HTTP endpoint: $Url"
