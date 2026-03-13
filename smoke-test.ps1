param(
    [string]$BaseUrl = "http://localhost:8080/api"
)

$ErrorActionPreference = "Stop"

function Invoke-JsonRequest {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Url,
        [object]$Body,
        [string]$Token
    )

    $headers = @{
        "Content-Type" = "application/json"
    }

    if ($Token) {
        $headers["Authorization"] = "Bearer $Token"
    }

    $params = @{
        Method  = $Method
        Uri     = $Url
        Headers = $headers
    }

    if ($null -ne $Body) {
        $params["Body"] = ($Body | ConvertTo-Json -Depth 10)
    }

    return Invoke-RestMethod @params
}

Write-Host "== Login user A =="
$userA = Invoke-JsonRequest `
    -Method "POST" `
    -Url "$BaseUrl/auth/login" `
    -Body @{
        phoneNumber = "+375291111111"
        displayName = "Alex A"
    }
$userA | ConvertTo-Json -Depth 10

Write-Host "== Login user B =="
$userB = Invoke-JsonRequest `
    -Method "POST" `
    -Url "$BaseUrl/auth/login" `
    -Body @{
        phoneNumber = "+375292222222"
        displayName = "Alex B"
    }
$userB | ConvertTo-Json -Depth 10

Write-Host "== Send first message from A to B =="
$message1 = Invoke-JsonRequest `
    -Method "POST" `
    -Url "$BaseUrl/messages" `
    -Token $userA.token `
    -Body @{
        recipientUserId = $userB.userId
        text = "Привет от A"
    }
$message1 | ConvertTo-Json -Depth 10

Write-Host "== Chats for A =="
$chatsA = Invoke-JsonRequest `
    -Method "GET" `
    -Url "$BaseUrl/chats" `
    -Token $userA.token
$chatsA | ConvertTo-Json -Depth 10

Write-Host "== Chats for B =="
$chatsB = Invoke-JsonRequest `
    -Method "GET" `
    -Url "$BaseUrl/chats" `
    -Token $userB.token
$chatsB | ConvertTo-Json -Depth 10

$chatId = $message1.chatId

Write-Host "== Send second message from B to A =="
$message2 = Invoke-JsonRequest `
    -Method "POST" `
    -Url "$BaseUrl/messages" `
    -Token $userB.token `
    -Body @{
        chatId = $chatId
        text = "Привет от B"
    }
$message2 | ConvertTo-Json -Depth 10

Write-Host "== History for chat =="
$history = Invoke-JsonRequest `
    -Method "GET" `
    -Url "$BaseUrl/messages/chat/$chatId?limit=50" `
    -Token $userA.token
$history | ConvertTo-Json -Depth 10

Write-Host "== Smoke test completed =="
