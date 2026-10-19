param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [string]$MasterKey = "sk-local-master-key"
)

$ErrorActionPreference = "Stop"

$okBody = '{"model":"chat-default","messages":[{"role":"user","content":"Say hi in one word."}]}'
$badModelBody = '{"model":"no-such-model","messages":[{"role":"user","content":"hi"}]}'
$streamBody = '{"model":"chat-default","stream":true,"messages":[{"role":"user","content":"hi"}]}'

Write-Host "--- 1. 200 case ---"
curl.exe -s -w "`nHTTP %{http_code}`n" -X POST "$BaseUrl/v1/chat/completions" `
    -H "Content-Type: application/json" `
    -H "Authorization: Bearer $MasterKey" `
    -d $okBody

Write-Host "`n--- 2. unknown model (expect 400) ---"
curl.exe -s -w "`nHTTP %{http_code}`n" -X POST "$BaseUrl/v1/chat/completions" `
    -H "Content-Type: application/json" `
    -H "Authorization: Bearer $MasterKey" `
    -d $badModelBody

Write-Host "`n--- 3. no auth (expect 401) ---"
curl.exe -s -w "`nHTTP %{http_code}`n" -X POST "$BaseUrl/v1/chat/completions" `
    -H "Content-Type: application/json" `
    -d $okBody

Write-Host "`n--- 4. stream rejected (expect 400) ---"
curl.exe -s -w "`nHTTP %{http_code}`n" -X POST "$BaseUrl/v1/chat/completions" `
    -H "Content-Type: application/json" `
    -H "Authorization: Bearer $MasterKey" `
    -d $streamBody
