[CmdletBinding()]
param(
    [string]$Repository,
    [string]$PrivatePatternFile
)

$ErrorActionPreference = 'Stop'
if (-not $Repository) {
    $Repository = Split-Path -Parent $PSScriptRoot
}
$repositoryPath = (Resolve-Path -LiteralPath $Repository).Path
$excludedDirectoryNames = @(
    '.git', '.gradle', '.build-tools', '.idea', '.mixin.out',
    'build', 'dist', 'run', 'runs', 'target', 'out'
)
$errors = [System.Collections.Generic.List[string]]::new()

$files = @(Get-ChildItem -LiteralPath $repositoryPath -File -Recurse -Force | Where-Object {
    $relativeParts = $_.FullName.Substring($repositoryPath.Length).TrimStart('\') -split '[\\/]'
    -not ($relativeParts | Where-Object { $_ -in $excludedDirectoryNames })
})

$relativeFiles = @($files | ForEach-Object {
    $_.FullName.Substring($repositoryPath.Length + 1).Replace('\', '/')
})

$forbiddenPathPatterns = @(
    @{ Name = 'nested Git configuration'; Regex = '(?i)^\.gitmodules$' },
    @{ Name = 'legacy private CI'; Regex = '(?i)^\.jenkins/' },
    @{ Name = 'private work material'; Regex = '(?i)(^|/)(records|notes|private|private-records|construction|construction-logs|backups|artifacts|crash-reports|evidence|logs?)(/|$)' },
    @{ Name = 'private pattern file'; Regex = '(?i)(^|/)(private|sensitive)[-_].*\.(json|txt|yml|yaml)$' },
    @{ Name = 'local instance operation script'; Regex = '(?i)^tools/(launch|install|copy|sync)-.*(client|instance|runtime).*\.ps1$' },
    @{ Name = 'retained build transcript'; Regex = '(?i)(^|/)(build[-_]?(final|log)|console[-_]?output)\.(txt|log)$' }
)
foreach ($relative in $relativeFiles) {
    foreach ($pattern in $forbiddenPathPatterns) {
        if ($relative -match $pattern.Regex) {
            $errors.Add("$($pattern.Name) entered the repository: $relative")
        }
    }
}

$allowedBinaryFiles = @(
    'sable_rapier/src/main/resources/natives/sable_rapier/sable_rapier_binaries.zip.l4z'
)
$binaryExtensions = @('.jar', '.zip', '.l4z', '.dll', '.so', '.dylib', '.exe', '.class', '.7z', '.rar', '.gz')
foreach ($file in $files) {
    $relative = $file.FullName.Substring($repositoryPath.Length + 1).Replace('\', '/')
    if ($file.Extension.ToLowerInvariant() -in $binaryExtensions -and $relative -notin $allowedBinaryFiles) {
        $errors.Add("Unexpected binary or third-party archive: $relative")
    }
}

$contentPatterns = [System.Collections.Generic.List[object]]::new()
foreach ($pattern in @(
    @{ Name = 'absolute Windows path'; Regex = '(?i)(?<![A-Za-z0-9])[A-Z]:\\[^\r\n"''<>|]+' },
    @{ Name = 'absolute user-home path'; Regex = '(?i)(?<![A-Za-z0-9])/(Users|home)/[A-Za-z0-9._-]+(/|$)' },
    @{ Name = 'local file URI'; Regex = '(?i)\bfile:/+([A-Z]:/|Users/|home/)' },
    @{ Name = 'email address'; Regex = '(?i)(?<![A-Za-z0-9._%+-])[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}(?![A-Za-z0-9.-])' }
)) {
    $contentPatterns.Add($pattern)
}

if (-not $PrivatePatternFile) {
    $PrivatePatternFile = $env:SABLE_PRIVATE_PATTERN_FILE
}
if (-not $PrivatePatternFile) {
    $configuredPatternFile = & git -C $repositoryPath config --local --get sable.privatePatternFile 2>$null
    if ($LASTEXITCODE -eq 0 -and $configuredPatternFile) {
        $PrivatePatternFile = ($configuredPatternFile | Select-Object -First 1).Trim()
    }
}
if ($PrivatePatternFile) {
    $resolvedPatternFile = (Resolve-Path -LiteralPath $PrivatePatternFile).Path
    $repositoryPrefix = $repositoryPath.TrimEnd('\') + '\'
    if ($resolvedPatternFile.StartsWith($repositoryPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'The private pattern file must be stored outside the repository.'
    }
    $privatePatternDocument = Get-Content -LiteralPath $resolvedPatternFile -Raw | ConvertFrom-Json
    if ($privatePatternDocument.version -ne 1 -or -not $privatePatternDocument.patterns) {
        throw 'The private pattern file has an unsupported schema.'
    }
    $privatePatternIndex = 0
    foreach ($privatePattern in $privatePatternDocument.patterns) {
        $privatePatternIndex++
        if (-not $privatePattern.regex) {
            throw "Private pattern $privatePatternIndex has no regex."
        }
        try {
            [void][regex]::new([string]$privatePattern.regex)
        }
        catch {
            throw "Private pattern $privatePatternIndex is not a valid regular expression."
        }
        $contentPatterns.Add(@{
            Name = "private identifier $privatePatternIndex"
            Regex = [string]$privatePattern.regex
        })
    }
}
$textExtensions = @('.java', '.groovy', '.gradle', '.properties', '.json', '.toml', '.xml', '.md', '.txt', '.ps1', '.cmd', '.yml', '.yaml', '.rs', '.cs', '.glsl', '.patch', '.diff')
foreach ($file in $files | Where-Object { $_.Extension.ToLowerInvariant() -in $textExtensions -and $_.Length -le 5MB }) {
    $relative = $file.FullName.Substring($repositoryPath.Length + 1).Replace('\', '/')
    $content = [System.IO.File]::ReadAllText($file.FullName)
    foreach ($pattern in $contentPatterns) {
        if ($content -match $pattern.Regex) {
            $errors.Add("$($pattern.Name) found in $relative")
        }
    }
}

foreach ($requiredLicense in @(
    'LICENSE.md',
    'THIRD_PARTY_NOTICES.md',
    'sable_companion/LICENSE',
    'sable_rapier/src/main/resources/natives/sable_rapier/LICENSE-RAPIER',
    'test-tools/LICENSE'
)) {
    if ($requiredLicense -notin $relativeFiles) {
        $errors.Add("Required license or notice is missing: $requiredLicense")
    }
}

if ($errors.Count -gt 0) {
    Write-Error ("Public-tree audit failed:`n - " + ($errors -join "`n - "))
}

Write-Host "Public-tree audit passed: $($files.Count) files checked."
