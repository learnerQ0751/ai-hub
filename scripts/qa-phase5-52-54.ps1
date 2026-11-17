# 第五期 5.2 幂等 / 5.3 混合检索 / 5.4 切片顺序与邻居扩展 实测脚本
# 依赖：console 18081 / gateway 18080 已启动，DASHSCOPE_API_KEY / DEEPSEEK_API_KEY 已注入进程
param(
    [string]$ConsoleBase = 'http://127.0.0.1:18081',
    [string]$GatewayBase = 'http://127.0.0.1:18080',
    [string]$MasterKey = 'sk-local-master-key',
    [string]$ProjectId = '',
    [string]$HybridOffBase = ''
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$script:http = [System.Net.Http.HttpClient]::new()
$script:http.Timeout = [TimeSpan]::FromSeconds(90)
$results = [System.Collections.Generic.List[object]]::new()

function Send-Qa {
    param([string]$Method, [string]$Url, [string]$Token, [string]$Json,
          [hashtable]$Headers, [System.Net.Http.HttpContent]$Content)
    $req = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), $Url)
    if ($Token) { [void]$req.Headers.TryAddWithoutValidation('Authorization', "Bearer $Token") }
    if ($Headers) { foreach ($k in $Headers.Keys) { [void]$req.Headers.TryAddWithoutValidation($k, $Headers[$k]) } }
    if ($Json) { $req.Content = [System.Net.Http.StringContent]::new($Json, [Text.Encoding]::UTF8, 'application/json') }
    if ($Content) { $req.Content = $Content }
    $r = $script:http.SendAsync($req).GetAwaiter().GetResult()
    $text = $r.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $h = @{}
    foreach ($x in $r.Headers) { $h[$x.Key] = ($x.Value -join ',') }
    [pscustomobject]@{ Status = [int]$r.StatusCode; Body = $text; Headers = $h }
}

function Check {
    param([string]$Name, [bool]$Pass, [string]$Detail)
    $results.Add([pscustomobject]@{ Name = $Name; Pass = $Pass; Detail = $Detail })
    $mark = if ($Pass) { 'PASS' } else { 'FAIL' }
    Write-Host "[$mark] $Name $Detail"
}

function Upload-Doc {
    param([string]$Path, [string]$Project)
    $form = [System.Net.Http.MultipartFormDataContent]::new()
    $file = [System.Net.Http.ByteArrayContent]::new([IO.File]::ReadAllBytes($Path))
    $file.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::new('text/markdown')
    $form.Add($file, 'file', [IO.Path]::GetFileName($Path))
    $form.Add([System.Net.Http.StringContent]::new($Project, [Text.Encoding]::UTF8), 'project_id')
    Send-Qa POST "$ConsoleBase/v1/rag/documents" $MasterKey $null $null $form
}

# ---------- 准备项目与凭据 ----------
if (-not $ProjectId) { $ProjectId = "qa-rag-$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())" }
Write-Host "项目：$ProjectId"
$gen = Send-Qa POST "$ConsoleBase/key/generate" $MasterKey (@{
    models = @(); tenant_id = 'default'; project_id = $ProjectId
    rpm_limit = 120; tpm_limit = 200000
} | ConvertTo-Json -Compress)
$vk = $null
try { $vk = ($gen.Body | ConvertFrom-Json).key } catch {}
Check '生成测试虚拟 Key' ($gen.Status -eq 200 -and $vk) "status=$($gen.Status)"
Set-Content "$env:TEMP\ai-hub-qa\rag-key.txt" $vk -Encoding ascii
Set-Content "$env:TEMP\ai-hub-qa\rag-project.txt" $ProjectId -Encoding ascii

# ---------- 造文档 ----------
$docDir = Join-Path $env:TEMP 'ai-hub-qa\rag-docs'
New-Item -ItemType Directory -Force $docDir | Out-Null
$docA = Join-Path $docDir 'tidal.md'
$docB = Join-Path $docDir 'storage.md'
@'
# 潮汐发电工程手册

## 选址条件
潮汐发电站的选址需要同时满足三个条件。第一，潮差要足够大，通常要求平均潮差超过三米，否则能量密度过低，单位造价不划算。第二，海域地形要有利于围垦或布置水轮机，海湾口窄、内部开阔的地形最适合。第三，要靠近负荷中心或已有变电站，减少海底电缆长度。选址阶段还必须评估航道、渔业和军事用海冲突，任何一项不通过都不能进入下一阶段。

## 维护周期
潮汐涡轮机的维护周期通常分为三级。日常巡检每月一次，主要检查叶片表面附着物、螺栓扭矩和防腐涂层。每季度进行一次水下检查，重点看轴承密封和齿轮箱油位。每三到五年进行一次大修，需要把涡轮机整体吊出水面更换易损件。维护窗口要避开大潮和台风季，否则作业风险显著上升，保险费用也会增加。

## 电价机制
潮汐能并网的电价机制一般采用两部制。容量电价按装机容量结算，保障固定成本回收；电量电价按实际上网电量结算，反映变动成本。由于潮汐出力具有半日周期，调度机构通常按可预测曲线安排消纳。部分地区对潮汐发电给予绿色证书或补贴，以弥补早期投资的高成本，补贴期限一般为十年。

## 生态影响
潮汐发电的生态影响主要集中在三个方面。一是坝体或水轮机改变流速，影响泥沙输运和底栖生物栖息地。二是叶片可能对鱼类造成撞击风险，需要在机组选型和运行策略上做规避。三是施工期的悬浮物和噪声会短期影响海洋生物。环评要求长期监测并设置生态流量通道，监测数据按季度公开。
'@ | Set-Content $docA -Encoding UTF8
@'
# 光伏储能系统安全规范

## 电池热失控防护
光伏储能系统的安全标准主要包括电池热失控防护、消防联动和电气隔离。电池簇之间应设置防火隔板，簇内配置温度与气体探测器。一旦检测到异常温升，系统要在三秒内切断直流回路并启动排风。消防系统采用全氟己酮或细水雾，禁止使用可能导电的灭火介质。

## 储能变流器效率
储能变流器的效率曲线在额定功率的百分之四十到八十区间最高，典型峰值效率可达百分之九十八。轻载和重载区间效率下降明显，因此选型时不要只看峰值效率。变流器应支持无功调节和低电压穿越，满足并网导则要求。日常运行要记录直流侧与交流侧的电量差，用于核算损耗。

## 并网验收
并网验收包括保护定值校验、电能质量测试和通信联调。电能质量重点看谐波、电压偏差和闪变。通信联调要验证遥测、遥信和遥控点表与调度主站一致。验收资料至少保存五年，包括试验报告、定值单和竣工图。
'@ | Set-Content $docB -Encoding UTF8

$upA = Upload-Doc $docA $ProjectId
$docAId = $null
try { $docAId = ($upA.Body | ConvertFrom-Json).doc_id } catch {}
Check '上传文档 A（潮汐）' (($upA.Status -eq 201 -or $upA.Status -eq 200) -and $docAId) "status=$($upA.Status) doc=$docAId"
$upB = Upload-Doc $docB $ProjectId
$docBId = $null
try { $docBId = ($upB.Body | ConvertFrom-Json).doc_id } catch {}
Check '上传文档 B（储能）' (($upB.Status -eq 201 -or $upB.Status -eq 200) -and $docBId) "status=$($upB.Status) doc=$docBId"

$list = Send-Qa GET "$ConsoleBase/v1/rag/documents?project_id=$ProjectId" $MasterKey
$docCount = 0
try { $docCount = @(($list.Body | ConvertFrom-Json).documents).Count } catch {}
Check '文档列表可查' ($list.Status -eq 200 -and $docCount -ge 2) "status=$($list.Status) count=$docCount"

# ---------- 5.3 混合检索 ----------
function Rag-Search {
    param([string]$Base, [string]$Query, [int]$TopK = 5)
    Send-Qa POST "$Base/v1/rag/search" $vk (@{
        query = $Query; project_id = $ProjectId; top_k = $TopK
    } | ConvertTo-Json -Compress)
}

$s1 = Rag-Search $GatewayBase '潮汐涡轮机的维护周期是多久'
$hits1 = @()
try { $hits1 = @(($s1.Body | ConvertFrom-Json).results) } catch {}
Check '5.3 检索有命中' ($s1.Status -eq 200 -and $hits1.Count -ge 1) "status=$($s1.Status) hits=$($hits1.Count)"

if ($hits1.Count -ge 1) {
    $first = $hits1[0]
    $fields = $first.PSObject.Properties.Name
    $hasFour = ($fields -contains 'match_type') -and ($fields -contains 'vector_score') -and
        ($fields -contains 'keyword_score') -and ($fields -contains 'rrf_score')
    Check '5.3 命中含四字段' $hasFour "fields=$(($fields | Where-Object { $_ -in 'match_type','vector_score','keyword_score','rrf_score' }) -join ',')"
    $types = @($hits1 | ForEach-Object { $_.match_type }) -join ','
    Write-Host "       match_type: $types"
    Write-Host "       scores: $(@($hits1 | ForEach-Object { "v=$($_.vector_score) k=$($_.keyword_score) rrf=$($_.rrf_score)" }) -join ' | ')"
    $hybridHit = @($hits1 | Where-Object { $null -ne $_.rrf_score -and $_.match_type -in @('both','vector','keyword') })
    Check '5.3 混合检索产生 RRF 分' ($hybridHit.Count -ge 1) "hybridHits=$($hybridHit.Count)"
    $bothOrKeyword = @($hits1 | Where-Object { $_.match_type -eq 'both' -or $_.match_type -eq 'keyword' })
    Check '5.3 字面路参与融合（match_type 含 both/keyword）' ($bothOrKeyword.Count -ge 1) "count=$($bothOrKeyword.Count)"
}

$s2 = Rag-Search $GatewayBase '全氟己酮 细水雾 消防'
$hits2 = @()
try { $hits2 = @(($s2.Body | ConvertFrom-Json).results) } catch {}
Check '5.3 第二组查询有命中' ($s2.Status -eq 200 -and $hits2.Count -ge 1) "status=$($s2.Status) hits=$($hits2.Count)"

# ---------- 5.4 切片顺序与邻居扩展 ----------
if ($hits1.Count -ge 1) {
    $s3 = Rag-Search $GatewayBase '潮汐涡轮机的维护周期是多久' 3
    $hits3 = @()
    try { $hits3 = @(($s3.Body | ConvertFrom-Json).results) } catch {}
    $searchIds = @($hits3 | ForEach-Object { $_.chunk_id })
    $ans = Send-Qa POST "$GatewayBase/v1/rag/answer" $vk (@{
        query = '潮汐涡轮机的维护周期是多久'; project_id = $ProjectId
        model = 'chat-default'; top_k = 3
    } | ConvertTo-Json -Compress)
    $ansBody = $null
    try { $ansBody = $ans.Body | ConvertFrom-Json } catch {}
    Check '5.4 answer 返回 200' ($ans.Status -eq 200) "status=$($ans.Status)"
    if ($ansBody) {
        Write-Host "       answer: $($ansBody.answer)"
        Write-Host "       citations: $(@($ansBody.citations | ForEach-Object { "$($_.index):$($_.chunk_id)" }) -join ', ')"
        $citeIds = @($ansBody.citations | ForEach-Object { $_.chunk_id })
        Check '5.4 有引用' ($citeIds.Count -ge 1) "citations=$($citeIds.Count)"
        $allFromHits = $true
        foreach ($id in $citeIds) { if ($searchIds -notcontains $id) { $allFromHits = $false } }
        Check '5.4 引用只含原命中 chunk（邻居未混入）' ($allFromHits -and $citeIds.Count -gt 0) "citeIds=$($citeIds.Count) 全部在检索命中内=$allFromHits"
        $indexes = @($ansBody.citations | ForEach-Object { $_.index })
        $seq = $true
        for ($i = 0; $i -lt $indexes.Count; $i++) { if ($indexes[$i] -ne ($i + 1)) { $seq = $false } }
        Check '5.4 引用编号从 1 连续' $seq "indexes=$($indexes -join ',')"
        Check '5.4 no_hit=false' ($ansBody.no_hit -eq $false) "no_hit=$($ansBody.no_hit)"
        $ordered = $true
        for ($i = 1; $i -lt $citeIds.Count; $i++) {
            if ($citeIds[$i] -lt $citeIds[$i - 1]) { $ordered = $false }
        }
        Check '5.4 引用按切片序号升序' $ordered "citeIds=$($citeIds -join ',')"
    }
}

# ---------- 5.3 关闭混合检索（独立 gateway 实例） ----------
if ($HybridOffBase) {
    $off = Rag-Search $HybridOffBase '潮汐涡轮机的维护周期是多久' 5
    $offHits = @()
    try { $offHits = @(($off.Body | ConvertFrom-Json).results) } catch {}
    Check '5.3(hv-off) 检索有命中' ($off.Status -eq 200 -and $offHits.Count -ge 1) "status=$($off.Status) hits=$($offHits.Count)"
    if ($offHits.Count -ge 1) {
        $allRrfNull = $true
        foreach ($h in $offHits) { if ($null -ne $h.rrf_score) { $allRrfNull = $false } }
        Check '5.3(hv-off) rrf_score 全为 null' $allRrfNull "types=$(@($offHits | ForEach-Object { $_.match_type }) -join ',')"
        Write-Host "       hv-off scores: $(@($offHits | ForEach-Object { "v=$($_.vector_score) k=$($_.keyword_score) rrf=$($_.rrf_score)" }) -join ' | ')"
    }
}

# ---------- 5.2 幂等 ----------
$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$alias = "qa-idem-$stamp"
$body1 = @{ alias = $alias; tenant_id = 'default'; description = 'qa idem 1'; status = 'ACTIVE' } | ConvertTo-Json -Compress
$body2 = @{ alias = $alias; tenant_id = 'default'; description = 'qa idem 2'; status = 'ACTIVE' } | ConvertTo-Json -Compress
$idemKey = "qa-idem-key-$stamp"

$r1 = Send-Qa POST "$ConsoleBase/v1/admin/models" $MasterKey $body1 @{ 'Idempotency-Key' = $idemKey }
Check '5.2 首次写请求成功' ($r1.Status -eq 200) "status=$($r1.Status)"
$r2 = Send-Qa POST "$ConsoleBase/v1/admin/models" $MasterKey $body1 @{ 'Idempotency-Key' = $idemKey }
$replay = $r2.Headers.Keys -contains 'X-Idempotent-Replay'
Check '5.2 同键同体重放' ($r2.Status -eq 200 -and $replay -and $r2.Headers['X-Idempotent-Replay'] -eq 'true') "status=$($r2.Status) replay=$($r2.Headers['X-Idempotent-Replay'])"
Check '5.2 重放返回首次响应体' ($r2.Body -eq $r1.Body) "sameBody=$($r2.Body -eq $r1.Body)"
$r3 = Send-Qa POST "$ConsoleBase/v1/admin/models" $MasterKey $body2 @{ 'Idempotency-Key' = $idemKey }
$conflict = $false
try { $conflict = ($r3.Body | ConvertFrom-Json).error.type -eq 'idempotency_error' } catch {}
Check '5.2 同键不同体返回 409 idempotency_error' ($r3.Status -eq 409 -and $conflict) "status=$($r3.Status)"

$idemKey2 = "qa-idem-key2-$stamp"
$badBody = '{"description":"missing alias"}'
$r4 = Send-Qa POST "$ConsoleBase/v1/admin/models" $MasterKey $badBody @{ 'Idempotency-Key' = $idemKey2 }
Check '5.2 业务失败返回 4xx' ($r4.Status -ge 400 -and $r4.Status -lt 500) "status=$($r4.Status)"
$r5 = Send-Qa POST "$ConsoleBase/v1/admin/models" $MasterKey $body1 @{ 'Idempotency-Key' = $idemKey2 }
Check '5.2 失败后同键可重试（键已释放）' ($r5.Status -eq 200) "status=$($r5.Status)"

# 清理测试模型
$del1 = Send-Qa DELETE "$ConsoleBase/v1/admin/models/$alias" $MasterKey
Check '清理测试模型' ($del1.Status -eq 200 -or $del1.Status -eq 204) "status=$($del1.Status)"

$failed = @($results | Where-Object { -not $_.Pass })
Write-Host ''
Write-Host ("共 {0} 项：通过 {1}，失败 {2}；项目 {3}" -f $results.Count, ($results.Count - $failed.Count), $failed.Count, $ProjectId)
if ($failed.Count -gt 0) { exit 1 }
