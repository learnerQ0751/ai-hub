param(
    [string]$OutputPath = ""
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

$ScriptRoot = $PSScriptRoot
$PackageRoot = Split-Path -Parent $ScriptRoot
$ProjectRoot = Split-Path -Parent $PackageRoot
$RecordDir = Join-Path $PackageRoot 'records'
$AcceptanceDir = Join-Path $PackageRoot 'acceptance'
$StatusPath = if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    Join-Path $PackageRoot 'STATUS.md'
} else {
    $OutputPath
}

function Split-ListText {
    param([string]$Text)
    if ([string]::IsNullOrWhiteSpace($Text)) { return @() }
    $t = $Text.Trim()
    if ($t.StartsWith('[') -and $t.EndsWith(']')) { $t = $t.Substring(1, $t.Length - 2) }
    if ([string]::IsNullOrWhiteSpace($t)) { return @() }
    return @($t.Split(',') | ForEach-Object {
        $v = $_.Trim().Trim('"').Trim("'").Trim()
        if (-not [string]::IsNullOrWhiteSpace($v)) { $v }
    })
}

function Get-Impact($raw) {
    $impact = [ordered]@{
        ModulesAdd = @(); ModulesChange = @()
        InterfacesAdd = @(); InterfacesChange = @(); InterfacesRemove = @()
        TablesAdd = @(); TablesChange = @()
        Config = @(); DependsOn = @(); Compat = ''; Gate = ''; Regression = @()
    }
    $m = [regex]::Match($raw, '(?s)##\s*4\.\s*影响面[\s\S]*?```yaml([\s\S]*?)```')
    if (-not $m.Success) { return $impact }
    $section = ''
    foreach ($line in ($m.Groups[1].Value -split "`r?`n")) {
        if ($line -match '^(\w+):\s*(.*)$') {
            $section = $Matches[1]
            $rest = $Matches[2].Trim()
            switch ($section) {
                'config' { $impact.Config = Split-ListText $rest }
                'depends_on' { $impact.DependsOn = Split-ListText $rest }
                'compat' { $impact.Compat = $rest }
                'gate' { $impact.Gate = $rest }
                'regression' { $impact.Regression = Split-ListText $rest }
            }
            continue
        }
        if ($line -match '^\s+(add|change|remove):\s*(.*)$') {
            $sub = $Matches[1]
            $val = Split-ListText $Matches[2]
            switch ($section) {
                'modules' { if ($sub -eq 'add') { $impact.ModulesAdd = $val } elseif ($sub -eq 'change') { $impact.ModulesChange = $val } }
                'interfaces' {
                    if ($sub -eq 'add') { $impact.InterfacesAdd = $val }
                    elseif ($sub -eq 'change') { $impact.InterfacesChange = $val }
                    elseif ($sub -eq 'remove') { $impact.InterfacesRemove = $val }
                }
                'tables' { if ($sub -eq 'add') { $impact.TablesAdd = $val } elseif ($sub -eq 'change') { $impact.TablesChange = $val } }
            }
        }
    }
    return $impact
}

function Get-Records {
    $records = @()
    if (-not (Test-Path $RecordDir)) { return $records }
    Get-ChildItem -Path $RecordDir -Filter 'REQ-*.md' | Sort-Object Name | ForEach-Object {
        $file = $_
        $raw = Get-Content -Path $file.FullName -Raw -Encoding UTF8
        $title = $file.BaseName
        if ($raw -match '(?m)^#\s+(.+)$') {
            $title = $Matches[1].Trim()
            $title = $title -replace '^REQ-[^：]+：\s*', ''
        }
        $status = '未知'
        if ($raw -match '(?m)^-\s*状态：\s*(.+)$') { $status = $Matches[1].Trim() }
        $interfaces = '—'
        if ($raw -match '(?m)^-\s*接口：\s*(.+)$') { $interfaces = $Matches[1].Trim() }
        $commit = '—'
        if ($raw -match '(?m)^-\s*提交：\s*(.+)$') { $commit = $Matches[1].Trim() }
        $depends = '—'
        if ($raw -match '(?m)^-\s*依赖需求：\s*(.+)$') { $depends = $Matches[1].Trim() }
        $records += [pscustomobject]@{
            Id = $file.BaseName
            Title = $title
            Status = $status
            Interfaces = $interfaces
            Commit = $commit
            DependsOn = $depends
            Impact = Get-Impact $raw
        }
    }
    return $records
}

function Get-Acceptance {
    $items = @{}
    if (-not (Test-Path $AcceptanceDir)) { return $items }
    Get-ChildItem -Path $AcceptanceDir -Filter 'REQ-*.md' | ForEach-Object {
        $raw = Get-Content -Path $_.FullName -Raw -Encoding UTF8
        $status = '未知'
        if ($raw -match '(?m)^-\s*状态：\s*(.+)$') { $status = $Matches[1].Trim() }
        $done = ([regex]::Matches($raw, '(?m)^\s*-\s*\[x\]')).Count
        $todo = ([regex]::Matches($raw, '(?m)^\s*-\s*\[ \]')).Count
        $items[$_.BaseName] = [pscustomobject]@{
            Status = $status
            Done = $done
            Todo = $todo
            Path = "acceptance/$($_.Name)"
        }
    }
    return $items
}

function Get-ModuleSourceDirs {
    param([string]$Child)
    Get-ChildItem -Path $ProjectRoot -Directory -Filter 'ai-hub-*' | ForEach-Object {
        $candidate = Join-Path $_.FullName $Child
        if (Test-Path $candidate) { $candidate }
    }
}

function Get-Endpoints {
    $endpoints = @()
    foreach ($srcDir in @(Get-ModuleSourceDirs 'src\main\java')) {
        Get-ChildItem -Path $srcDir -Recurse -Filter '*Controller.java' | ForEach-Object {
            $file = $_
            $lines = Get-Content -Path $file.FullName -Encoding UTF8
            $classBase = ''
            foreach ($line in $lines) {
                if ($line -match '@RequestMapping\s*\(\s*(?:value\s*=\s*)?\{?\s*"([^"]+)"') {
                    $classBase = $Matches[1]; break
                }
            }
            for ($i = 0; $i -lt $lines.Count; $i++) {
                $line = $lines[$i]
                if ($line -match '@(Get|Post|Put|Delete|Patch)Mapping\s*\((.*)$') {
                    $httpMethod = $Matches[1].ToUpper()
                    $args = $Matches[2]
                    $j = $i
                    while (($args -notmatch '\)') -and ($j -lt ($lines.Count - 1))) {
                        $j++
                        $args = $args + ' ' + $lines[$j]
                    }
                    $paths = @([regex]::Matches($args, '"([^"]+)"') | ForEach-Object { $_.Groups[1].Value })
                    if ($paths.Count -eq 0) { $paths = @('') }
                    $methodName = ''
                    for ($k = $i; $k -lt [Math]::Min($i + 20, $lines.Count); $k++) {
                        if ($lines[$k] -match '(?:public|protected|private)\s+[^\(\)]+\s+(\w+)\s*\(') {
                            $methodName = $Matches[1]; break
                        }
                    }
                    foreach ($p in $paths) {
                        $full = $classBase + $p
                        if (-not $full.StartsWith('/')) { $full = '/' + $full }
                        $endpoints += [pscustomobject]@{
                            Method = $httpMethod
                            Path = $full
                            Handler = ($file.BaseName + '#' + $methodName)
                        }
                    }
                    $i = $j
                }
            }
        }
    }
    return @($endpoints | Sort-Object Path, Method -Unique)
}

function Get-Tables {
    $tables = @()
    foreach ($dir in @(Get-ModuleSourceDirs 'src\main\resources\db')) {
        Get-ChildItem -Path $dir -Recurse -Filter '*.sql' | ForEach-Object {
            $raw = Get-Content -Path $_.FullName -Raw -Encoding UTF8
            foreach ($m in [regex]::Matches($raw, '(?i)CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?([a-zA-Z0-9_]+)')) {
                $tables += $m.Groups[1].Value
            }
        }
    }
    return @($tables | Sort-Object -Unique)
}

function Get-ConfigKeys {
    $yml = Join-Path (Join-Path $ProjectRoot 'ai-hub-start') 'src\main\resources\application.yml'
    if (-not (Test-Path $yml)) { return @() }
    return @(Get-Content -Path $yml -Encoding UTF8 |
        Where-Object { $_ -match '^[a-zA-Z][a-zA-Z0-9\-]*:' } |
        ForEach-Object { ($_ -split ':')[0].Trim() })
}


function Get-DataModel {
    $path = Join-Path (Join-Path $PackageRoot 'docs') 'DATA-MODEL.md'
    $groups = @()
    if (-not (Test-Path $path)) { return $groups }
    $current = $null
    $inCurrentSection = $false
    foreach ($line in (Get-Content -Path $path -Encoding UTF8)) {
        if ($line -match '^##\s*当前表') { $inCurrentSection = $true; continue }
        if ($line -match '^##\s*规划表') { $inCurrentSection = $false; $current = $null; continue }
        if (-not $inCurrentSection) { continue }
        if ($line -match '^###\s*(.+)$') {
            $current = [pscustomobject]@{ Name = $Matches[1].Trim(); Rows = @() }
            $groups += $current
            continue
        }
        if (($null -ne $current) -and ($line -match '^\|(.+)\|$')) {
            $cells = @(($Matches[1] -split '\|') | ForEach-Object { $_.Trim() })
            if ($cells.Count -ge 5) {
                if (($cells[0] -match '^表$') -or ($cells[0] -match '^:?-{2,}:?$')) { continue }
                $current.Rows += [pscustomobject]@{
                    Table = $cells[0].Trim('`')
                    Purpose = $cells[1]
                    Relation = $cells[2]
                    Status = $cells[3]
                    Req = $cells[4]
                }
            }
        }
    }
    return $groups
}

function Get-TestSummary {
    $testDirs = @(Get-ModuleSourceDirs 'src\test\java')
    if ($testDirs.Count -eq 0) { return '未找到测试目录' }
    $files = @($testDirs | ForEach-Object { Get-ChildItem -Path $_ -Recurse -Filter '*Test.java' })
    $methods = 0
    foreach ($file in $files) {
        $raw = Get-Content -Path $file.FullName -Raw -Encoding UTF8
        $methods += ([regex]::Matches($raw, '@Test')).Count
    }
    return "$methods 个 @Test / $($files.Count) 个测试类"
}

$records = @(Get-Records)
$acceptance = Get-Acceptance
$endpoints = @(Get-Endpoints)
$tables = @(Get-Tables)
$dataModel = @(Get-DataModel)
$configKeys = @(Get-ConfigKeys)
$testSummary = Get-TestSummary

$allInterfaces = @()
$allTables = @()
foreach ($r in $records) {
    foreach ($i in @($r.Impact.InterfacesAdd) + @($r.Impact.InterfacesChange)) {
        $allInterfaces += [pscustomobject]@{ Item = $i; Req = $r.Id }
    }
    foreach ($t in @($r.Impact.TablesAdd) + @($r.Impact.TablesChange)) {
        $allTables += [pscustomobject]@{ Item = $t; Req = $r.Id }
    }
}

function Related-Reqs {
    param([string]$PathOrTable, $Mapping)
    $hits = @($Mapping | Where-Object { -not [string]::IsNullOrWhiteSpace($_.Item) -and $_.Item -like "*$PathOrTable*" } | ForEach-Object { $_.Req } | Sort-Object -Unique)
    if ($hits.Count -eq 0) { '—' } else { ($hits -join ', ') }
}

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add('<!-- STATUS:GENERATED:START -->')
$lines.Add('> 本区块由 `ai-workflow/scripts/status.ps1` 生成；手工备注写在下方。')
$lines.Add('')
$lines.Add("**最后更新**：$(Get-Date -Format 'yyyy-MM-dd HH:mm')")
$lines.Add('')
$lines.Add('## 需求进度')
$lines.Add('')
$lines.Add('| REQ | 名称 | 状态 | 兼容级别 | 验收 | 依赖 | 提交 |')
$lines.Add('|---|---|---|---|---|---|---|')
if ($records.Count -eq 0) {
    $lines.Add('| — | 暂无需求记录 | — | — | — | — | — |')
} else {
    foreach ($r in $records) {
        $acc = $acceptance[$r.Id]
        $accText = if ($null -ne $acc) { "$($acc.Status)（$($acc.Done)/$($acc.Done + $acc.Todo)）" } else { '—' }
        $compat = if ([string]::IsNullOrWhiteSpace($r.Impact.Compat)) { '—' } else { $r.Impact.Compat }
        $lines.Add("| $($r.Id) | $($r.Title) | $($r.Status) | $compat | $accText | $($r.DependsOn) | $($r.Commit) |")
    }
}
$lines.Add('')
$lines.Add('## 接口清单（来自 Controller）')
$lines.Add('')
$lines.Add('| 方法 | 路径 | Handler | 涉及需求 |')
$lines.Add('|---|---|---|---|')
if ($endpoints.Count -eq 0) {
    $lines.Add('| — | 暂无 Controller 接口 | — | — |')
} else {
    foreach ($e in $endpoints) {
        $related = Related-Reqs $e.Path $allInterfaces
        $lines.Add("| $($e.Method) | $($e.Path) | $($e.Handler) | $related |")
    }
}
$lines.Add('')
$lines.Add('## 数据表速查（语义来自 docs/DATA-MODEL.md，DDL 校验）')
$lines.Add('')
$knownTables = @()
if ($dataModel.Count -eq 0) {
    $lines.Add('> 未找到 docs/DATA-MODEL.md，以下仅为 DDL 扫描结果。')
    $lines.Add('')
    $lines.Add('| 表 | 涉及需求 |')
    $lines.Add('|---|---|')
    foreach ($t in $tables) { $lines.Add("| $t | $(Related-Reqs $t $allTables) |") }
    $lines.Add('')
} else {
    foreach ($g in $dataModel) {
        $lines.Add("### $($g.Name)")
        $lines.Add('')
        $lines.Add('| 表 | 用途 | 关键关系 | 状态 | 涉及需求 | DDL |')
        $lines.Add('|---|---|---|---|---|---|')
        foreach ($r in $g.Rows) {
            $knownTables += $r.Table
            $hasDdl = if ($tables -contains $r.Table) { '✓' } else { '—' }
            $related = @(@(Related-Reqs $r.Table $allTables) + @($r.Req)) | Where-Object { (-not [string]::IsNullOrWhiteSpace($_)) -and ($_ -ne '—') } | Select-Object -Unique
            $reqText = if ($related.Count -gt 0) { $related -join ', ' } else { '—' }
            $lines.Add("| $($r.Table) | $($r.Purpose) | $($r.Relation) | $($r.Status) | $reqText | $hasDdl |")
        }
        $lines.Add('')
    }
    $unregistered = @($tables | Where-Object { $knownTables -notcontains $_ })
    if ($unregistered.Count -gt 0) {
        $lines.Add('### 未登记（DDL 中存在，DATA-MODEL 缺失）')
        $lines.Add('')
        $lines.Add('| 表 | 涉及需求 |')
        $lines.Add('|---|---|')
        foreach ($t in $unregistered) { $lines.Add("| $t | $(Related-Reqs $t $allTables) |") }
        $lines.Add('')
    }
}
$lines.Add('## 配置（application.yml 顶层键）')
$lines.Add('')
if ($configKeys.Count -eq 0) { $lines.Add('- —') } else { foreach ($k in $configKeys) { $lines.Add("- ``$k``") } }
$lines.Add('')
$lines.Add('## 变更矩阵（接口 / 表 → 涉及需求）')
$lines.Add('')
$lines.Add('| 对象 | 涉及需求 |')
$lines.Add('|---|---|')
if (($allInterfaces.Count + $allTables.Count) -eq 0) {
    $lines.Add('| — | 无影响面声明 |')
} else {
    $matrix = @($allInterfaces + $allTables | Where-Object { -not [string]::IsNullOrWhiteSpace($_.Item) } | ForEach-Object {
        [pscustomobject]@{ Item = ($_.Item -replace '（[^）]*）', '').Trim(); Req = $_.Req }
    })
    foreach ($group in ($matrix | Group-Object Item | Sort-Object Name)) {
        $reqs = @($group.Group | ForEach-Object { $_.Req } | Sort-Object -Unique)
        $lines.Add("| $($group.Name) | $($reqs -join ', ') |")
    }
}
$lines.Add('')
$lines.Add('## 模块依赖')
$lines.Add('')
$lines.Add('- `start → service → core → manager/integration → api → dal → common`（ArchUnit 强制，禁止反向）')
$lines.Add('')
$lines.Add('## 测试')
$lines.Add('')
$lines.Add("- $testSummary")
$lines.Add('')
$lines.Add('## 下一步')
$lines.Add('')
$pending = @($records | Where-Object { $_.Status -notmatch '已完成' })
$pendingAccept = @($records | Where-Object {
    $acc = $acceptance[$_.Id]
    $null -ne $acc -and $acc.Status -notmatch '已验收'
})
if ($pendingAccept.Count -gt 0) {
    foreach ($r in $pendingAccept) {
        $lines.Add("- 验收 $($r.Id) $($r.Title) → 打开 ``$($acceptance[$r.Id].Path)``")
    }
}
if ($pending.Count -gt 0) {
    foreach ($r in $pending) {
        if ($pendingAccept -notcontains $r) {
            $lines.Add("- 实现 $($r.Id) $($r.Title)（$($r.Status)）")
        }
    }
} elseif ($pendingAccept.Count -eq 0) {
    $next = $null
    $indexFile = Join-Path (Join-Path $PackageRoot 'requirements') 'INDEX.md'
    if (Test-Path $indexFile) {
        $indexRows = @()
        foreach ($line in (Get-Content $indexFile -Encoding UTF8)) {
            if ($line -match '^\|\s*(REQ-[0-9.]+)\s*\|\s*([^|]+)\|\s*([^|]*)\|\s*([^|]*)\|\s*([^|]+)\|\s*([^|]*)\|') {
                $indexRows += [pscustomobject]@{
                    Id = $Matches[1].Trim()
                    Name = $Matches[2].Trim()
                    Source = $Matches[4].Trim()
                    Status = $Matches[5].Trim()
                    Deps = $Matches[6].Trim()
                }
            }
        }
        $done = @($indexRows | Where-Object { $_.Status -match '已完成' } | ForEach-Object { $_.Id })
        foreach ($row in $indexRows) {
            if ($row.Status -match '已完成') { continue }
            $deps = @($row.Deps -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ -match '^REQ-' })
            $blocked = @($deps | Where-Object { $done -notcontains $_ })
            if ($blocked.Count -eq 0) { $next = $row; break }
        }
    }
    if ($null -ne $next) {
        $lines.Add("- 实现 $($next.Id) $($next.Name)（$($next.Status)）→ 需求卡：$($next.Source)")
    } else {
        $lines.Add('- 暂无待办需求。')
    }
}
$lines.Add('')
$lines.Add('<!-- STATUS:GENERATED:END -->')
$generated = ($lines -join "`r`n")

$manual = "`r`n`r`n## 手工备注`r`n`r`n<!-- 可手动补充；脚本只替换上方生成区块 -->`r`n"

if (Test-Path $StatusPath) {
    $existing = Get-Content -Path $StatusPath -Raw -Encoding UTF8
    if ($existing -match '<!-- STATUS:GENERATED:START -->[\s\S]*?<!-- STATUS:GENERATED:END -->') {
        $new = [regex]::Replace($existing,
            '<!-- STATUS:GENERATED:START -->[\s\S]*?<!-- STATUS:GENERATED:END -->', $generated)
    } else {
        $new = $existing.TrimEnd() + "`r`n`r`n" + $generated + $manual
    }
} else {
    $new = "# 项目状态`r`n`r`n" + $generated + $manual
}

$new = $new -replace '<!-- STATUS:GENERATED:END -->\s*## 手工备注', "<!-- STATUS:GENERATED:END -->`r`n`r`n## 手工备注"
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($StatusPath, $new, $utf8)

Write-Host "Updated: $StatusPath"
Write-Host "  records: $($records.Count) | endpoints: $($endpoints.Count) | tables: $($tables.Count) | tests: $testSummary"
