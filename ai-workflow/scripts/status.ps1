param(
    [string]$OutputPath = ""
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

$ScriptRoot = $PSScriptRoot
$PackageRoot = Split-Path -Parent $ScriptRoot
$ProjectRoot = Split-Path -Parent $PackageRoot
$RecordDir = Join-Path $PackageRoot 'records'
$StatusPath = if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    Join-Path $PackageRoot 'STATUS.md'
} else {
    $OutputPath
}

function Get-Records {
    $records = @()
    if (-not (Test-Path $RecordDir)) {
        return $records
    }
    Get-ChildItem -Path $RecordDir -Filter 'REQ-*.md' | Sort-Object Name | ForEach-Object {
        $file = $_
        $raw = Get-Content -Path $file.FullName -Raw -Encoding UTF8
        $title = $file.BaseName
        if ($raw -match '(?m)^#\s+(.+)$') {
            $title = $Matches[1].Trim()
        }
        $status = '未知'
        if ($raw -match '(?m)^-\s*状态：\s*(.+)$') {
            $status = $Matches[1].Trim()
        }
        $interfaces = '—'
        if ($raw -match '(?m)^-\s*接口：\s*(.+)$') {
            $interfaces = $Matches[1].Trim()
        }
        $commit = '—'
        if ($raw -match '(?m)^-\s*提交：\s*(.+)$') {
            $commit = $Matches[1].Trim()
        }
        $done = ([regex]::Matches($raw, '(?m)^\s*-\s*\[x\]')).Count
        $todo = ([regex]::Matches($raw, '(?m)^\s*-\s*\[ \]')).Count
        $records += [pscustomobject]@{
            Id = $file.BaseName
            Title = $title
            Status = $status
            Interfaces = $interfaces
            Commit = $commit
            Done = $done
            Todo = $todo
        }
    }
    return $records
}

function Get-Endpoints {
    $endpoints = @()
    $srcDir = Join-Path $ProjectRoot 'src\main\java'
    if (-not (Test-Path $srcDir)) {
        return $endpoints
    }
    Get-ChildItem -Path $srcDir -Recurse -Filter '*Controller.java' | ForEach-Object {
        $file = $_
        $lines = Get-Content -Path $file.FullName -Encoding UTF8
        $classBase = ''
        foreach ($line in $lines) {
            if ($line -match '@RequestMapping\s*\(\s*(?:value\s*=\s*)?\{?\s*"([^"]+)"') {
                $classBase = $Matches[1]
                break
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
                if ($paths.Count -eq 0) {
                    $paths = @('')
                }
                $methodName = ''
                for ($k = $i; $k -lt [Math]::Min($i + 20, $lines.Count); $k++) {
                    if ($lines[$k] -match '(?:public|protected|private)\s+[^\(\)]+\s+(\w+)\s*\(') {
                        $methodName = $Matches[1]
                        break
                    }
                }
                foreach ($p in $paths) {
                    $full = $classBase + $p
                    if (-not $full.StartsWith('/')) {
                        $full = '/' + $full
                    }
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
    return @($endpoints | Sort-Object Path, Method -Unique)
}

function Get-TestSummary {
    $testDir = Join-Path $ProjectRoot 'src\test\java'
    if (-not (Test-Path $testDir)) {
        return '未找到测试目录'
    }
    $files = @(Get-ChildItem -Path $testDir -Recurse -Filter '*Test.java')
    $methods = 0
    foreach ($file in $files) {
        $raw = Get-Content -Path $file.FullName -Raw -Encoding UTF8
        $methods += ([regex]::Matches($raw, '@Test')).Count
    }
    return "$methods 个 @Test / $($files.Count) 个测试类"
}

$records = @(Get-Records)
$endpoints = @(Get-Endpoints)
$testSummary = Get-TestSummary

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add('<!-- STATUS:GENERATED:START -->')
$lines.Add('> 本区块由 `ai-workflow/scripts/status.ps1` 生成；手工备注写在下方。')
$lines.Add('')
$lines.Add("**最后更新**：$(Get-Date -Format 'yyyy-MM-dd HH:mm')")
$lines.Add('')
$lines.Add('## 需求进度')
$lines.Add('')
$lines.Add('| REQ | 名称 | 状态 | 验收 | 接口 | 提交 |')
$lines.Add('|---|---|---|---|---|---|')
if ($records.Count -eq 0) {
    $lines.Add('| — | 暂无需求记录 | — | — | — | — |')
} else {
    foreach ($r in $records) {
        $accept = if (($r.Done + $r.Todo) -gt 0) { "$($r.Done)/$($r.Done + $r.Todo)" } else { '—' }
        $lines.Add("| $($r.Id) | $($r.Title) | $($r.Status) | $accept | $($r.Interfaces) | $($r.Commit) |")
    }
}
$lines.Add('')
$lines.Add('## 接口清单（来自 Controller）')
$lines.Add('')
$lines.Add('| 方法 | 路径 | Handler |')
$lines.Add('|---|---|---|')
if ($endpoints.Count -eq 0) {
    $lines.Add('| — | 暂无 Controller 接口 | — |')
} else {
    foreach ($e in $endpoints) {
        $lines.Add("| $($e.Method) | $($e.Path) | $($e.Handler) |")
    }
}
$lines.Add('')
$lines.Add('## 测试')
$lines.Add('')
$lines.Add("- $testSummary")
$lines.Add('')
$lines.Add('## 怎么演示')
$lines.Add('')
$lines.Add('- 按 `records/REQ-*.md` 的验收标准逐项验证。')
$lines.Add('- 项目有 `scripts/acceptance.ps1` 时可直接运行。')
$lines.Add('')
$lines.Add('## 下一步')
$lines.Add('')
$open = @($records | Where-Object { $_.Status -ne '已完成' })
if ($open.Count -eq 0) {
    $lines.Add('- 暂无进行中的需求。')
} else {
    foreach ($r in $open) {
        $lines.Add("- $($r.Id) $($r.Title)（$($r.Status)，剩余 $($r.Todo) 项验收）")
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

$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($StatusPath, $new, $utf8)

Write-Host "Updated: $StatusPath"
Write-Host "  records: $($records.Count) | endpoints: $($endpoints.Count) | tests: $testSummary"
