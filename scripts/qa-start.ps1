# 本工作树一键启动：console(18081) → gateway(18080) → 前端 Vite(5175)
# 端口被其他工作树占用时直接报错退出，不杀别人的进程。
param(
    [string]$Root = 'E:\pro\ai-hub-qa',
    [int]$ConsolePort = 18081,
    [int]$GatewayPort = 18080,
    [int]$FrontendPort = 5175,
    [string]$MasterKey = 'sk-local-master-key',
    [string]$SecretKey = 'qa-local-secret'
)

$ErrorActionPreference = 'Stop'
$logDir = Join-Path $env:TEMP 'ai-hub-qa'
New-Item -ItemType Directory -Force $logDir | Out-Null

function Get-PortOwner([int]$Port) {
    $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $conn) { return $null }
    $proc = Get-CimInstance Win32_Process -Filter "ProcessId=$($conn.OwningProcess)" -ErrorAction SilentlyContinue
    [pscustomobject]@{ Pid = $conn.OwningProcess; CommandLine = $proc.CommandLine }
}

function Test-Ours([int]$Port) {
    $owner = Get-PortOwner $Port
    if (-not $owner) { return $false }
    if ($owner.CommandLine -and $owner.CommandLine -match [regex]::Escape($Root)) {
        Write-Host "端口 $Port 已被本工作树进程占用 (PID $($owner.Pid))，跳过启动"
        return $true
    }
    throw "端口 $Port 被其他进程占用：PID $($owner.Pid) $($owner.CommandLine)。请换端口，不要动别人的进程。"
}

function Wait-Health([string]$Url, [int]$Seconds = 120) {
    for ($i = 0; $i -lt $Seconds; $i += 2) {
        Start-Sleep -Seconds 2
        try {
            $resp = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
            if ($resp.StatusCode -eq 200) { return $true }
        } catch {}
    }
    return $false
}

$env:GATEWAY_MASTER_KEY = $MasterKey
$env:GATEWAY_SECRET_KEY = $SecretKey

if (-not (Test-Ours $ConsolePort)) {
    $p = Start-Process java -ArgumentList '-jar', "$Root\ai-hub-console\target\ai-hub-console-0.0.1-SNAPSHOT-exec.jar" `
        -WorkingDirectory $Root -RedirectStandardOutput "$logDir\console.out.log" `
        -RedirectStandardError "$logDir\console.err.log" -PassThru -WindowStyle Hidden
    Write-Host "console 启动 PID=$($p.Id)，等待健康检查..."
    if (-not (Wait-Health "http://127.0.0.1:$ConsolePort/actuator/health")) { throw "console 未就绪，见 $logDir\console.out.log" }
    Write-Host "console OK http://127.0.0.1:$ConsolePort"
}

if (-not (Test-Ours $GatewayPort)) {
    $p = Start-Process java -ArgumentList '-jar', "$Root\ai-hub-gateway\target\ai-hub-gateway-0.0.1-SNAPSHOT-exec.jar" `
        -WorkingDirectory $Root -RedirectStandardOutput "$logDir\gateway.out.log" `
        -RedirectStandardError "$logDir\gateway.err.log" -PassThru -WindowStyle Hidden
    Write-Host "gateway 启动 PID=$($p.Id)，等待健康检查..."
    if (-not (Wait-Health "http://127.0.0.1:$GatewayPort/actuator/health")) { throw "gateway 未就绪，见 $logDir\gateway.out.log" }
    Write-Host "gateway OK http://127.0.0.1:$GatewayPort"
}

$appDir = Join-Path $Root 'frontend\app'
if (-not (Test-Ours $FrontendPort)) {
    $p = Start-Process npm.cmd -ArgumentList 'run', 'dev', '--', '--port', $FrontendPort, '--strictPort' `
        -WorkingDirectory $appDir -RedirectStandardOutput "$logDir\vite-$FrontendPort.out.log" `
        -RedirectStandardError "$logDir\vite-$FrontendPort.err.log" -PassThru -WindowStyle Hidden
    Write-Host "前端启动 PID=$($p.Id)，等待 Vite..."
    if (-not (Wait-Health "http://127.0.0.1:$FrontendPort/")) { throw "前端未就绪，见 $logDir\vite-$FrontendPort.out.log" }
    Write-Host "前端 OK http://127.0.0.1:$FrontendPort"
}

Write-Host ''
Write-Host "console : http://127.0.0.1:$ConsolePort  (master key 来自 GATEWAY_MASTER_KEY)"
Write-Host "gateway : http://127.0.0.1:$GatewayPort"
Write-Host "frontend: http://127.0.0.1:$FrontendPort"
Write-Host "日志    : $logDir"
