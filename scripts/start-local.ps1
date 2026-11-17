<#
.SYNOPSIS
ai-hub 本地一键启动：Postgres/Redis（Docker）+ console(18081) + gateway(18080) + 前端 Vite(5175)。
仅服务本机验收，不做反向代理与 HTTPS。

.DESCRIPTION
- 幂等：已在运行且健康的目标端口直接复用，不重复拉起。
- 依赖：Docker Desktop（Postgres/Redis）、JDK 21、Maven、Node.js/npm。
- 敏感配置（数据库口令、上游 key）只走本进程环境变量，不落盘、不打印。

.PARAMETER Build
强制重新打包后端（mvn -DskipTests package）。

.PARAMETER SkipBuild
不检查源码变更，直接用现有 jar。

.PARAMETER Stop
停止本脚本启动的进程（按端口记录）。

.EXAMPLE
.\scripts\start-local.ps1

.EXAMPLE
.\scripts\start-local.ps1 -Build

.EXAMPLE
.\scripts\start-local.ps1 -Stop
#>
param(
    [int]$ConsolePort = 18081,
    [int]$GatewayPort = 18080,
    [int]$FrontendPort = 5175,
    [string]$AdminUser = 'admin',
    [string]$AdminPassword = 'admin123',
    [switch]$Build,
    [switch]$SkipBuild,
    [switch]$Stop
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $env:TEMP 'ai-hub-local'
New-Item -ItemType Directory -Force -Path $RunDir | Out-Null
$StateFile = Join-Path $RunDir 'state.json'

$ConsoleJar = Join-Path $RepoRoot 'ai-hub-console\target\ai-hub-console-0.0.1-SNAPSHOT-exec.jar'
$GatewayJar = Join-Path $RepoRoot 'ai-hub-gateway\target\ai-hub-gateway-0.0.1-SNAPSHOT-exec.jar'
$FrontendDir = Join-Path $RepoRoot 'frontend\app'

function Write-Step([string]$Message) {
    Write-Host "[ai-hub] $Message" -ForegroundColor Cyan
}

function Get-PortOwner([int]$Port) {
    $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $conn) { return $null }
    return $conn.OwningProcess
}

function Test-Url([string]$Url) {
    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
        $content = $response.Content
        if ($content -is [byte[]]) {
            $content = [Text.Encoding]::UTF8.GetString($content)
        }
        return [pscustomobject]@{ Ok = $true; Status = $response.StatusCode; Content = $content }
    } catch {
        return [pscustomobject]@{ Ok = $false; Status = 0; Content = '' }
    }
}

function Wait-Health([string]$Url, [int]$TimeoutSeconds, [string]$Name, [string]$LogFile) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $probe = Test-Url $Url
        if ($probe.Ok -and $probe.Content -match '"status"\s*:\s*"UP"') {
            return $probe
        }
        Start-Sleep -Seconds 2
    }
    if (Test-Path $LogFile) {
        Write-Host "--- $Name 日志末尾（$LogFile）---" -ForegroundColor DarkYellow
        Get-Content $LogFile -Tail 30
    }
    throw "$Name 在 $TimeoutSeconds 秒内未就绪：$Url"
}

function New-PasswordHash([string]$Password) {
    $saltBytes = New-Object byte[] 16
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($saltBytes) } finally { $rng.Dispose() }
    $kdf = New-Object Security.Cryptography.Rfc2898DeriveBytes(
        [Text.Encoding]::UTF8.GetBytes($Password), $saltBytes, 210000,
        [Security.Cryptography.HashAlgorithmName]::SHA256)
    try { $hashBytes = $kdf.GetBytes(32) } finally { $kdf.Dispose() }
    $b64 = [Convert]::ToBase64String
    return "pbkdf2-sha256`$210000`$" + $b64.Invoke($saltBytes) + "`$" + $b64.Invoke($hashBytes)
}

function Read-State {
    if (Test-Path $StateFile) {
        try { return Get-Content $StateFile -Raw | ConvertFrom-Json } catch { }
    }
    return $null
}

function Save-State($State) {
    $State | ConvertTo-Json | Set-Content -Path $StateFile -Encoding UTF8
}

function Stop-Tracked {
    $state = Read-State
    if ($null -eq $state) {
        Write-Step '没有本脚本的启动记录，无需停止。'
        return
    }
    foreach ($entry in @($state.processes)) {
        if ($null -eq $entry) { continue }
        $proc = Get-Process -Id $entry.pid -ErrorAction SilentlyContinue
        if ($null -ne $proc) {
            # /T 连带子进程（前端经 cmd.exe 拉起 node）
            & taskkill.exe /T /F /PID $entry.pid 2>$null | Out-Null
            Write-Step "已停止 $($entry.name) (pid=$($entry.pid))"
        }
        if ($null -ne $entry.port) {
            Start-Sleep -Milliseconds 300
            $owner = Get-PortOwner ([int]$entry.port)
            if ($null -ne $owner -and $owner -ne $entry.pid) {
                & taskkill.exe /T /F /PID $owner 2>$null | Out-Null
                Write-Step "已释放端口 $($entry.port) (pid=$owner)"
            }
        }
    }
    Remove-Item $StateFile -Force -ErrorAction SilentlyContinue
}

if ($Stop) {
    Stop-Tracked
    exit 0
}

# ---------- 前置检查 ----------
foreach ($cmd in @('java', 'mvn', 'node', 'npm', 'docker')) {
    if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) {
        throw "缺少命令：$cmd，请先安装并加入 PATH。"
    }
}

try {
    docker info | Out-Null
} catch {
    throw 'Docker 未运行：请先启动 Docker Desktop。'
}

# ---------- 依赖服务 ----------
Write-Step '启动 Postgres(pgvector) 与 Redis（docker compose）...'
Push-Location $RepoRoot
try {
    docker compose -f docker-compose.yml up -d postgres redis
    if ($LASTEXITCODE -ne 0) { throw 'docker compose 启动 Postgres/Redis 失败。' }
} finally {
    Pop-Location
}

foreach ($port in @(5432, 6379)) {
    $deadline = (Get-Date).AddSeconds(60)
    while ($null -eq (Get-PortOwner $port) -and (Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 2
    }
    if ($null -eq (Get-PortOwner $port)) { throw "依赖端口 $port 未就绪。" }
}

# ---------- 后端构建 ----------
$needBuild = $Build.IsPresent
if (-not $needBuild -and -not $SkipBuild.IsPresent) {
    if (-not (Test-Path $ConsoleJar) -or -not (Test-Path $GatewayJar)) {
        $needBuild = $true
    } else {
        $jarTime = (Get-Item $ConsoleJar).LastWriteTime
        $newer = Get-ChildItem -Path (Join-Path $RepoRoot 'ai-hub-*') -Recurse -File -Include *.java, *.xml, *.yml -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -match '\\src\\main\\' -and $_.LastWriteTime -gt $jarTime } |
            Select-Object -First 1
        if ($null -ne $newer) {
            Write-Step "检测到源码比 jar 新（$($newer.Name)），需要重新打包。"
            $needBuild = $true
        }
    }
}

if ($needBuild) {
    Write-Step '打包后端（mvn -q -DskipTests package），首次可能数分钟...'
    Push-Location $RepoRoot
    try {
        mvn -q -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw '后端打包失败，请先修复编译错误（或加 -SkipBuild 使用现有 jar）。' }
    } finally {
        Pop-Location
    }
} else {
    Write-Step '使用现有后端 jar（未检测到需要重新打包）。'
}

# ---------- 环境变量（仅本进程/子进程，不落盘） ----------
$env:DB_URL = 'jdbc:postgresql://localhost:5432/aihub'
$env:DB_USER = 'aihub'
$env:DB_PASSWORD = 'aihub'
$env:REDIS_HOST = 'localhost'
$env:REDIS_PORT = '6379'
$env:GATEWAY_MASTER_KEY = 'local-master-key'
$env:GATEWAY_SECRET_KEY = 'local-secret-key'
$env:CONSOLE_PORT = "$ConsolePort"
$env:GATEWAY_PORT = "$GatewayPort"
$env:CONSOLE_SESSION_ENABLED = 'true'
$env:CONSOLE_SESSION_USERNAME = $AdminUser
$env:CONSOLE_SESSION_PASSWORD_HASH = (New-PasswordHash $AdminPassword)
$env:CONSOLE_SESSION_COOKIE_SECURE = 'false'

$state = Read-State
if ($null -eq $state) { $state = [pscustomobject]@{ processes = @() } }
$reused = @()

function Add-Tracked([string]$Name, [int]$ProcessId, [int]$Port) {
    $state.processes = @($state.processes) + [pscustomobject]@{ name = $Name; pid = $ProcessId; port = $Port }
    Save-State $state
}

# ---------- console ----------
$consoleUrl = "http://127.0.0.1:$ConsolePort/actuator/health"
$consoleHealth = Test-Url $consoleUrl
if ($consoleHealth.Ok -and $consoleHealth.Content -match '"status"\s*:\s*"UP"') {
    Write-Step "console 已在 $ConsolePort 运行，复用。"
    $reused += "console:$ConsolePort"
} else {
    if ($null -ne (Get-PortOwner $ConsolePort)) {
        throw "端口 $ConsolePort 已被占用但健康检查不通过，请先停止占用进程（pid=$(Get-PortOwner $ConsolePort)）。"
    }
    Write-Step "启动 console（端口 $ConsolePort）..."
    $out = Join-Path $RunDir "console-$ConsolePort.out.log"
    $err = Join-Path $RunDir "console-$ConsolePort.err.log"
    $proc = Start-Process -FilePath 'java' -ArgumentList @('-jar', $ConsoleJar) -WorkingDirectory $RepoRoot `
        -RedirectStandardOutput $out -RedirectStandardError $err -WindowStyle Hidden -PassThru
    Add-Tracked "console:$ConsolePort" $proc.Id $ConsolePort
    $consoleHealth = Wait-Health $consoleUrl 150 'console' $out
}

# ---------- gateway ----------
$gatewayUrl = "http://127.0.0.1:$GatewayPort/actuator/health"
$gatewayHealth = Test-Url $gatewayUrl
if ($gatewayHealth.Ok -and $gatewayHealth.Content -match '"status"\s*:\s*"UP"') {
    Write-Step "gateway 已在 $GatewayPort 运行，复用。"
    $reused += "gateway:$GatewayPort"
} else {
    if ($null -ne (Get-PortOwner $GatewayPort)) {
        throw "端口 $GatewayPort 已被占用但健康检查不通过，请先停止占用进程（pid=$(Get-PortOwner $GatewayPort)）。"
    }
    Write-Step "启动 gateway（端口 $GatewayPort）..."
    $out = Join-Path $RunDir "gateway-$GatewayPort.out.log"
    $err = Join-Path $RunDir "gateway-$GatewayPort.err.log"
    $proc = Start-Process -FilePath 'java' -ArgumentList @('-jar', $GatewayJar) -WorkingDirectory $RepoRoot `
        -RedirectStandardOutput $out -RedirectStandardError $err -WindowStyle Hidden -PassThru
    Add-Tracked "gateway:$GatewayPort" $proc.Id $GatewayPort
    $gatewayHealth = Wait-Health $gatewayUrl 150 'gateway' $out
}

# ---------- 前端 ----------
$frontendUrl = "http://127.0.0.1:$FrontendPort/"
$frontendProbe = Test-Url $frontendUrl
if ($frontendProbe.Ok) {
    Write-Step "前端已在 $FrontendPort 运行，复用。"
    $reused += "frontend:$FrontendPort"
} else {
    if ($null -ne (Get-PortOwner $FrontendPort)) {
        throw "端口 $FrontendPort 已被占用但页面不可访问，请先停止占用进程（pid=$(Get-PortOwner $FrontendPort)）。"
    }
    if (-not (Test-Path (Join-Path $FrontendDir 'node_modules'))) {
        Write-Step '安装前端依赖（npm ci）...'
        Push-Location $FrontendDir
        try {
            npm ci
            if ($LASTEXITCODE -ne 0) { throw 'npm ci 失败。' }
        } finally {
            Pop-Location
        }
    }
    Write-Step "启动前端 Vite（端口 $FrontendPort，直连 console $ConsolePort）..."
    $env:VITE_USE_MOCK_SESSION = 'false'
    $env:CONSOLE_PROXY_TARGET = "http://127.0.0.1:$ConsolePort"
    $out = Join-Path $RunDir "frontend-$FrontendPort.out.log"
    $err = Join-Path $RunDir "frontend-$FrontendPort.err.log"
    $proc = Start-Process -FilePath 'cmd.exe' -ArgumentList @('/c', "npm run dev -- --port $FrontendPort --strictPort") `
        -WorkingDirectory $FrontendDir -RedirectStandardOutput $out -RedirectStandardError $err -WindowStyle Hidden -PassThru
    Add-Tracked "frontend:$FrontendPort" $proc.Id $FrontendPort
    $deadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $deadline) {
        $frontendProbe = Test-Url $frontendUrl
        if ($frontendProbe.Ok) { break }
        Start-Sleep -Seconds 2
    }
    if (-not $frontendProbe.Ok) {
        if (Test-Path $out) { Get-Content $out -Tail 30 }
        throw "前端在 90 秒内未就绪：$frontendUrl"
    }
}

# ---------- 汇总 ----------
$consoleFinal = Test-Url $consoleUrl
$gatewayFinal = Test-Url $gatewayUrl
$frontendFinal = Test-Url $frontendUrl

Write-Host ''
Write-Host '================ ai-hub 本地验收环境 ================' -ForegroundColor Green
Write-Host "控制台前端 : http://localhost:$FrontendPort"
Write-Host "Console API: http://localhost:$ConsolePort"
Write-Host "Gateway API: http://localhost:$GatewayPort"
Write-Host "登录账号   : $AdminUser / （脚本参数 -AdminPassword，默认 admin123）"
Write-Host "本机 Master Key（仅调试）: local-master-key"
Write-Host ''
Write-Host "健康检查 console : HTTP $($consoleFinal.Status) $($consoleFinal.Content)"
Write-Host "健康检查 gateway : HTTP $($gatewayFinal.Status) $($gatewayFinal.Content)"
Write-Host "健康检查 frontend: HTTP $($frontendFinal.Status)"
Write-Host ''
Write-Host "日志目录: $RunDir"
Write-Host "停止服务: .\scripts\start-local.ps1 -Stop"
if ($reused.Count -gt 0) {
    Write-Host ''
    Write-Host "注意：以下端口是复用已有进程，未重新注入本次环境变量：$($reused -join ', ')" -ForegroundColor Yellow
    Write-Host '      其中前端若为手工启动，默认仍是 mock 登录；要验证真实后端会话，请先停掉该前端进程再重跑本脚本。' -ForegroundColor Yellow
}
Write-Host '=====================================================' -ForegroundColor Green
