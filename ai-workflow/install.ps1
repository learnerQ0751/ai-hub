param(
    [ValidateSet('claude', 'cursor', 'both', 'generic')]
    [string]$Agent = 'both',
    [switch]$Force,
    [switch]$NewProject
)

$ErrorActionPreference = 'Stop'

$PackageRoot = $PSScriptRoot
$ProjectRoot = Split-Path -Parent $PackageRoot

function Write-Utf8NoBom {
    param([string]$Path, [string]$Content)
    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Content, $encoding)
}

function Install-TextAdapter {
    param([string]$Source, [string]$Target, [string]$Marker)
    if (-not (Test-Path $Source)) {
        throw "Adapter not found: $Source"
    }
    $sourceContent = [System.IO.File]::ReadAllText($Source)
    if (Test-Path $Target) {
        $existing = [System.IO.File]::ReadAllText($Target)
        if ($existing.Contains($Marker)) {
            Write-Host "Already installed: $Target"
            return
        }
        if ($Force) {
            Write-Utf8NoBom -Path $Target -Content $sourceContent
            Write-Host "Overwritten: $Target"
        } else {
            Write-Utf8NoBom -Path $Target -Content ($existing.TrimEnd() + "`r`n`r`n" + $sourceContent)
            Write-Host "Appended: $Target"
        }
    } else {
        Write-Utf8NoBom -Path $Target -Content $sourceContent
        Write-Host "Created: $Target"
    }
}

function Install-Claude {
    $source = Join-Path $PackageRoot 'adapters\claude\CLAUDE.md'
    $target = Join-Path $ProjectRoot 'CLAUDE.md'
    Install-TextAdapter -Source $source -Target $target -Marker '<!-- AI-WORKFLOW -->'
}

function Install-Cursor {
    $source = Join-Path $PackageRoot 'adapters\cursor\ai-workflow.mdc'
    $targetDir = Join-Path $ProjectRoot '.cursor\rules'
    New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
    $target = Join-Path $targetDir 'ai-workflow.mdc'
    if ((Test-Path $target) -and -not $Force) {
        Write-Host "Already installed: $target"
        return
    }
    Copy-Item $source $target -Force
    Write-Host "Created: $target"
}

function Install-Generic {
    $source = Join-Path $PackageRoot 'adapters\generic\AGENTS.md'
    $target = Join-Path $ProjectRoot 'AGENTS.md'
    Install-TextAdapter -Source $source -Target $target -Marker '<!-- AI-WORKFLOW -->'
}

if ($NewProject) {
    $projectFile = Join-Path (Join-Path $PackageRoot 'docs') 'PROJECT.md'
    $template = Join-Path $PackageRoot 'templates\PROJECT.template.md'
    Copy-Item $template $projectFile -Force
    Write-Host "PROJECT.md reset from template."

    # 需求索引为项目专属：新项目重置为空索引（保留备份），避免带入旧项目需求路径
    $indexFile = Join-Path $PackageRoot 'requirements\INDEX.md'
    if (Test-Path $indexFile) {
        Copy-Item $indexFile "$indexFile.bak" -Force
        $emptyIndex = @"
# 需求索引（项目专属）

> 本项目暂无外部需求源。需求可由用户口述，按 ``templates/REQ.md`` 起草。
> 有外部需求文档时，在 ``PROJECT.md`` 的「外部文档源」填写路径并在此登记。

| REQ | 名称 | 来源 | 状态 |
|---|---|---|---|
| — | — | — | — |
"@
        $utf8 = New-Object System.Text.UTF8Encoding($false)
        [System.IO.File]::WriteAllText($indexFile, $emptyIndex, $utf8)
        Write-Host "requirements/INDEX.md reset (backup: INDEX.md.bak)."
    }
}

switch ($Agent) {
    'claude' { Install-Claude }
    'cursor' { Install-Cursor }
    'both' { Install-Claude; Install-Cursor }
    'generic' { Install-Generic }
}

Write-Host ''
Write-Host 'AI workflow installed.'
Write-Host '  Standards: ai-workflow/docs/STANDARDS.md + ai-workflow/docs/PROJECT.md'
Write-Host '  Workflow:  ai-workflow/docs/WORKFLOW.md'
Write-Host '  Baseline:  ai-workflow/DESIGN.md + ai-workflow/STATUS.md'
Write-Host '  Records:   ai-workflow/records/REQ-*.md'
Write-Host '  Acceptance:ai-workflow/acceptance/REQ-*.md'
Write-Host ''
Write-Host 'Next:'
Write-Host '  1. Edit ai-workflow/docs/PROJECT.md for this project.'
Write-Host '  2. Copy templates/REQ.md -> records/REQ-xxx.md and templates/ACCEPTANCE.md -> acceptance/REQ-xxx.md.'
Write-Host '  3. Start your agent from the project root.'
if (Test-Path (Join-Path $PackageRoot 'records')) {
    $exampleRecords = Get-ChildItem (Join-Path $PackageRoot 'records') -Filter 'REQ-*.md' -ErrorAction SilentlyContinue
    if ($exampleRecords) {
        Write-Host '  Note: records/ and acceptance/ contain example records; remove them for a new project.'
    }
}
