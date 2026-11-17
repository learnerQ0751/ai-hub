# 基线冒烟：健康检查、鉴权隔离、Key 生命周期、控制面只读端点。
# 只读为主；写入仅限本次生成的 qa-smoke-* 租户 Key。
param(
    [string]$ConsoleBase = 'http://127.0.0.1:18081',
    [string]$GatewayBase = 'http://127.0.0.1:18080',
    [string]$MasterKey = 'sk-local-master-key'
)

$ErrorActionPreference = 'Stop'
$results = [System.Collections.Generic.List[object]]::new()

# 用 HttpClient 而非 Invoke-WebRequest：PS 5.1 在异常分支读不到错误响应体。
Add-Type -AssemblyName System.Net.Http
$script:http = [System.Net.Http.HttpClient]::new()
$script:http.Timeout = [TimeSpan]::FromSeconds(15)

function Invoke-Qa {
    param([string]$Method, [string]$Url, [hashtable]$Headers, [string]$Body)
    $req = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), $Url)
    if ($Headers) { foreach ($k in $Headers.Keys) { [void]$req.Headers.TryAddWithoutValidation($k, $Headers[$k]) } }
    if ($Body) { $req.Content = [System.Net.Http.StringContent]::new($Body, [Text.Encoding]::UTF8, 'application/json') }
    try {
        $resp = $script:http.SendAsync($req).GetAwaiter().GetResult()
        $text = if ($resp.Content) { $resp.Content.ReadAsStringAsync().GetAwaiter().GetResult() } else { '' }
        [pscustomobject]@{ Status = [int]$resp.StatusCode; Body = $text }
    } catch {
        [pscustomobject]@{ Status = -1; Body = $_.Exception.Message }
    }
}

function Assert-Check {
    param([string]$Name, [bool]$Pass, [string]$Detail)
    $results.Add([pscustomobject]@{ Name = $Name; Pass = $Pass; Detail = $Detail })
    $mark = if ($Pass) { 'PASS' } else { 'FAIL' }
    Write-Host "[$mark] $Name $Detail"
}

$admin = @{ Authorization = "Bearer $MasterKey" }

$r = Invoke-Qa GET "$ConsoleBase/actuator/health" $null $null
Assert-Check 'console 健康检查 200' ($r.Status -eq 200) "status=$($r.Status)"

$r = Invoke-Qa GET "$GatewayBase/actuator/health" $null $null
Assert-Check 'gateway 健康检查 200' ($r.Status -eq 200) "status=$($r.Status)"

$r = Invoke-Qa GET "$ConsoleBase/v1/admin/models" $admin $null
Assert-Check 'master key 访问 console 管理接口' ($r.Status -eq 200) "status=$($r.Status)"

$r = Invoke-Qa GET "$ConsoleBase/v1/admin/models" $null $null
Assert-Check '无凭证访问 console 返回 401' ($r.Status -eq 401) "status=$($r.Status)"

$r = Invoke-Qa GET "$GatewayBase/v1/models" $admin $null
$isErr = $false
try { $isErr = ($r.Body | ConvertFrom-Json).error.type -eq 'authorization_error' } catch {}
Assert-Check 'master key 打 gateway 返回 403 + 统一错误体' ($r.Status -eq 403 -and $isErr) "status=$($r.Status)"

$tenant = "qa-smoke-$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())"
$gen = Invoke-Qa POST "$ConsoleBase/key/generate" $admin (@{
    models = @(); tenant_id = $tenant; project_id = 'smoke'; rpm_limit = 60; tpm_limit = 10000; max_budget = 5
} | ConvertTo-Json -Compress)
$key = $null
try { $key = ($gen.Body | ConvertFrom-Json).key } catch {}
Assert-Check '生成虚拟 Key' ($gen.Status -eq 200 -and $key) "status=$($gen.Status)"

if ($key) {
    $vk = @{ Authorization = "Bearer $key" }
    $r = Invoke-Qa GET "$GatewayBase/v1/models" $vk $null
    Assert-Check '虚拟 Key 访问 gateway 模型列表' ($r.Status -eq 200) "status=$($r.Status)"

    $r = Invoke-Qa GET "$ConsoleBase/v1/admin/keys" $vk $null
    Assert-Check '虚拟 Key 访问 console 返回 403' ($r.Status -eq 403) "status=$($r.Status)"

    $r = Invoke-Qa GET "$ConsoleBase/v1/admin/keys?tenant_id=$tenant" $admin $null
    $keys = @()
    try { $keys = @(($r.Body | ConvertFrom-Json).items) } catch {}
    Assert-Check '按租户查询 Key 列表' ($r.Status -eq 200 -and $keys.Count -ge 1) "count=$($keys.Count)"
    $keyHash = $keys[0].key_hash

    $r = Invoke-Qa POST "$ConsoleBase/v1/admin/keys/$keyHash/block" $admin $null
    $blocked = $false
    try { $blocked = ($r.Body | ConvertFrom-Json).blocked } catch {}
    Assert-Check '封禁 Key 生效' ($r.Status -eq 200 -and $blocked -eq $true) "status=$($r.Status) blocked=$blocked"

    # 封禁 Key 按既有契约返回 401 auth_error（见 KeyManagementIntegrationTest）
    $r = Invoke-Qa GET "$GatewayBase/v1/models" $vk $null
    $blockedErr = $false
    try { $blockedErr = ($r.Body | ConvertFrom-Json).error.type -eq 'auth_error' } catch {}
    Assert-Check '封禁后虚拟 Key 被拒 401' ($r.Status -eq 401 -and $blockedErr) "status=$($r.Status)"

    $r = Invoke-Qa POST "$ConsoleBase/v1/admin/keys/$keyHash/unblock" $admin $null
    $unblocked = $false
    try { $unblocked = (($r.Body | ConvertFrom-Json).blocked) -eq $false } catch {}
    Assert-Check '解封 Key 生效' ($r.Status -eq 200 -and $unblocked) "status=$($r.Status) not_blocked=$unblocked"

    $r = Invoke-Qa GET "$GatewayBase/v1/models" $vk $null
    Assert-Check '解封后虚拟 Key 恢复' ($r.Status -eq 200) "status=$($r.Status)"
}

foreach ($ep in @('/v1/mcp/servers', '/v1/usage', '/v1/audit-logs?limit=1', '/v1/admin/logs?limit=1')) {
    $r = Invoke-Qa GET "$ConsoleBase$ep" $admin $null
    Assert-Check "控制面只读 $ep" ($r.Status -eq 200) "status=$($r.Status)"
}

$failed = @($results | Where-Object { -not $_.Pass })
Write-Host ''
Write-Host ("共 {0} 项：通过 {1}，失败 {2}" -f $results.Count, ($results.Count - $failed.Count), $failed.Count)
if ($failed.Count -gt 0) { exit 1 }
