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
    $projectFile = Join-Path $PackageRoot 'PROJECT.md'
    $template = Join-Path $PackageRoot 'templates\PROJECT.template.md'
    Copy-Item $template $projectFile -Force
    Write-Host "PROJECT.md reset from template."
}

switch ($Agent) {
    'claude' { Install-Claude }
    'cursor' { Install-Cursor }
    'both' { Install-Claude; Install-Cursor }
    'generic' { Install-Generic }
}

Write-Host ''
Write-Host 'AI workflow installed.'
Write-Host '  Standards: ai-workflow/STANDARDS.md + ai-workflow/PROJECT.md'
Write-Host '  Workflow:  ai-workflow/WORKFLOW.md'
Write-Host '  Records:   ai-workflow/records/REQ-*.md'
Write-Host ''
Write-Host 'Next:'
Write-Host '  1. Edit ai-workflow/PROJECT.md for this project.'
Write-Host '  2. Copy ai-workflow/templates/REQ.md to ai-workflow/records/REQ-xxx.md.'
Write-Host '  3. Start your agent from the project root.'
if (Test-Path (Join-Path $PackageRoot 'records')) {
    $exampleRecords = Get-ChildItem (Join-Path $PackageRoot 'records') -Filter 'REQ-*.md' -ErrorAction SilentlyContinue
    if ($exampleRecords) {
        Write-Host '  Note: records/ contains example records; remove them for a new project.'
    }
}
