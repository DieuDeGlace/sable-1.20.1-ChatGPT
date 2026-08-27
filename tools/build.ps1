[CmdletBinding()]
param(
    [ValidateSet('release', 'test', 'quick', 'audit')]
    [string]$Mode = 'release',
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$repositoryPath = (Resolve-Path -LiteralPath (Split-Path -Parent $PSScriptRoot)).Path
$toolCache = Join-Path $repositoryPath '.build-tools'
$distributionDirectory = Join-Path $repositoryPath 'dist'
$gradleVersion = '8.14.3'
$gradleSha256 = 'BD71102213493060956EC229D946BEEE57158DBD89D0E62B91BCA0FA2C5F3531'
$jdkVersion = '17.0.20.1'
$jdkSha256 = '3D9006956FC8AF5601CD24FFC4F468BEF48279C7EBD8171B9BDF90D0AABFBF1F'
$includeTestTools = $Mode -in @('test', 'quick')
$skipTests = $Mode -eq 'quick'

function Get-Sha256 {
    param([Parameter(Mandatory)][string]$Path)

    $stream = [System.IO.File]::OpenRead($Path)
    $algorithm = [System.Security.Cryptography.SHA256]::Create()
    try {
        return (($algorithm.ComputeHash($stream) | ForEach-Object { $_.ToString('x2') }) -join '').ToUpperInvariant()
    }
    finally {
        $algorithm.Dispose()
        $stream.Dispose()
    }
}

function Get-JavaMajorVersion {
    param([Parameter(Mandatory)][string]$JavaExecutable)

    $previousErrorAction = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $versionText = (& $JavaExecutable -version 2>&1 | Out-String)
    }
    finally {
        $ErrorActionPreference = $previousErrorAction
    }
    if ($LASTEXITCODE -ne 0 -or $versionText -notmatch 'version\s+"(?<major>\d+)') {
        return $null
    }
    return [int]$Matches.major
}

function Find-Java17 {
    $candidates = [System.Collections.Generic.List[string]]::new()
    if ($env:JAVA_HOME) {
        $candidates.Add((Join-Path $env:JAVA_HOME 'bin\java.exe'))
    }
    $pathJava = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($pathJava) {
        $candidates.Add($pathJava.Source)
    }

    foreach ($candidate in $candidates | Select-Object -Unique) {
        if ((Test-Path -LiteralPath $candidate -PathType Leaf) -and
            (Get-JavaMajorVersion -JavaExecutable $candidate) -eq 17) {
            return (Split-Path -Parent (Split-Path -Parent $candidate))
        }
    }
    return $null
}

function Get-VerifiedDownload {
    param(
        [Parameter(Mandatory)][string]$Uri,
        [Parameter(Mandatory)][string]$Destination,
        [Parameter(Mandatory)][string]$ExpectedSha256
    )

    if (Test-Path -LiteralPath $Destination -PathType Leaf) {
        $existingHash = Get-Sha256 -Path $Destination
        if ($existingHash -eq $ExpectedSha256) {
            return
        }
        [System.IO.File]::Delete($Destination)
    }

    Write-Host "Downloading $Uri"
    Invoke-WebRequest -UseBasicParsing -Uri $Uri -OutFile $Destination
    $actualHash = Get-Sha256 -Path $Destination
    if ($actualHash -ne $ExpectedSha256) {
        [System.IO.File]::Delete($Destination)
        throw "Checksum verification failed for $Uri. Expected $ExpectedSha256, received $actualHash."
    }
}

function Install-Java17 {
    if (-not [Environment]::Is64BitOperatingSystem -or $env:OS -ne 'Windows_NT') {
        throw 'Automatic JDK installation supports 64-bit Windows. Install a Java 17 JDK and run this script again.'
    }

    New-Item -ItemType Directory -Path $toolCache -Force | Out-Null
    $jdkArchive = Join-Path $toolCache "microsoft-jdk-$jdkVersion-windows-x64.zip"
    $jdkExtractRoot = Join-Path $toolCache "microsoft-jdk-$jdkVersion-windows-x64"
    $jdkUri = "https://aka.ms/download-jdk/microsoft-jdk-$jdkVersion-windows-x64.zip"
    Get-VerifiedDownload -Uri $jdkUri -Destination $jdkArchive -ExpectedSha256 $jdkSha256

    $javaExecutable = Get-ChildItem -LiteralPath $jdkExtractRoot -Filter java.exe -File -Recurse -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match '[\\/]bin[\\/]java\.exe$' } |
        Select-Object -First 1
    if (-not $javaExecutable) {
        if (Test-Path -LiteralPath $jdkExtractRoot) {
            Remove-Item -LiteralPath $jdkExtractRoot -Recurse -Force
        }
        New-Item -ItemType Directory -Path $jdkExtractRoot -Force | Out-Null
        Expand-Archive -LiteralPath $jdkArchive -DestinationPath $jdkExtractRoot
        $javaExecutable = Get-ChildItem -LiteralPath $jdkExtractRoot -Filter java.exe -File -Recurse |
            Where-Object { $_.FullName -match '[\\/]bin[\\/]java\.exe$' } |
            Select-Object -First 1
    }
    if (-not $javaExecutable -or (Get-JavaMajorVersion -JavaExecutable $javaExecutable.FullName) -ne 17) {
        throw 'The verified Microsoft OpenJDK archive did not contain a usable Java 17 runtime.'
    }
    return (Split-Path -Parent (Split-Path -Parent $javaExecutable.FullName))
}

function Install-Gradle {
    New-Item -ItemType Directory -Path $toolCache -Force | Out-Null
    $gradleArchive = Join-Path $toolCache "gradle-$gradleVersion-bin.zip"
    $gradleHome = Join-Path $toolCache "gradle-$gradleVersion"
    $gradleExecutable = Join-Path $gradleHome 'bin\gradle.bat'
    if (-not (Test-Path -LiteralPath $gradleExecutable -PathType Leaf)) {
        Get-VerifiedDownload `
            -Uri "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip" `
            -Destination $gradleArchive `
            -ExpectedSha256 $gradleSha256
        if (Test-Path -LiteralPath $gradleHome) {
            Remove-Item -LiteralPath $gradleHome -Recurse -Force
        }
        Expand-Archive -LiteralPath $gradleArchive -DestinationPath $toolCache
    }
    if (-not (Test-Path -LiteralPath $gradleExecutable -PathType Leaf)) {
        throw "Gradle executable is missing after extraction: $gradleExecutable"
    }
    return $gradleExecutable
}

function Move-BuildLogs {
    param(
        [Parameter(Mandatory)][string]$SourceDirectory,
        [Parameter(Mandatory)][string]$Component
    )

    if (-not (Test-Path -LiteralPath $SourceDirectory -PathType Container)) {
        return
    }

    $logRoot = $env:SABLE_BUILD_LOG_DIR
    if (-not $logRoot) {
        $logRoot = Join-Path ([System.IO.Path]::GetTempPath()) 'SableBuildLogs'
    }
    New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
    $logDestination = Join-Path $logRoot (
        '{0}-{1}-{2}' -f (Get-Date -Format 'yyyyMMdd-HHmmss-fff'), $PID, $Component)
    Move-Item -LiteralPath $SourceDirectory -Destination $logDestination
    Write-Host "Build logs moved outside the repository: $logDestination"
}

function Assert-ProductionJar {
    param([Parameter(Mandatory)][string]$JarPath)

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        $entries = @($archive.Entries | ForEach-Object { $_.FullName.Replace('\', '/') })
        foreach ($required in @(
            'META-INF/mods.toml',
            'sable.mixins.json',
            'sable-create.mixins.json',
            'sable-forge.mixins.json',
            'assets/sable/flywheel/internal/common.vert',
            'assets/sable/flywheel/internal/api_impl.glsl',
            'assets/sable/flywheel/internal/light_lut.glsl',
            'assets/sable/flywheel/internal/instancing/light.glsl',
            'assets/sable/flywheel/internal/instancing/main.vert',
            'assets/sable/flywheel/internal/indirect/light.glsl',
            'assets/sable/flywheel/internal/indirect/matrices.glsl',
            'assets/sable/flywheel/internal/indirect/main.vert',
            'assets/sable/flywheel/light/flat.glsl',
            'assets/sable/flywheel/light/smooth.glsl',
            'assets/sable/flywheel/light/smooth_when_embedded.glsl',
            'dev/ryanhcode/sable/neoforge/mixin/compatibility/immersiveengineering/TurretBlockEntityMixin.class',
            'dev/ryanhcode/sable/neoforge/mixin/compatibility/veil/PostProcessingManagerMixin.class',
            'dev/ryanhcode/sable/neoforge/mixin/compatibility/veil/VeilDeferredRendererMixin.class',
            'dev/ryanhcode/sable/util/DiagnosticLogLimiter.class',
            'LICENSE.md',
            'THIRD_PARTY_NOTICES.md'
        )) {
            if ($required -notin $entries) {
                throw "Production JAR is missing $required"
            }
        }
        $forbidden = @($entries | Where-Object {
            $_ -match '(^|/)(validation|evidence|construction|logs?)(/|$)' -or
            $_ -match '(^|/)SableForge.*(Validation|Probe|Telemetry)[^/]*\.class$'
        })
        if ($forbidden.Count -gt 0) {
            throw "Development-only content entered the production JAR: $($forbidden -join ', ')"
        }

        # Mixin configuration is executable production metadata. Check every
        # configured class generically so selective source roots cannot publish
        # a configuration entry without its corresponding class file.
        foreach ($configName in @('sable.mixins.json', 'sable-create.mixins.json', 'sable-forge.mixins.json')) {
            $configEntry = $archive.GetEntry($configName)
            if ($null -eq $configEntry) {
                throw "Production JAR is missing $configName"
            }
            $configStream = $configEntry.Open()
            try {
                $configReader = [System.IO.StreamReader]::new($configStream, [System.Text.Encoding]::UTF8, $true, 4096, $true)
                try {
                    $config = $configReader.ReadToEnd() | ConvertFrom-Json
                }
                finally {
                    $configReader.Dispose()
                }
            }
            finally {
                $configStream.Dispose()
            }

            foreach ($section in @('mixins', 'client', 'server')) {
                $property = $config.PSObject.Properties[$section]
                if ($null -eq $property) {
                    continue
                }
                foreach ($mixin in @($property.Value)) {
                    $className = if ([string]::IsNullOrWhiteSpace([string]$config.package)) {
                        [string]$mixin
                    }
                    else {
                        "$($config.package).$mixin"
                    }
                    $classPath = $className.Replace('.', '/') + '.class'
                    if ($classPath -notin $entries) {
                        throw "Production mixin config $configName references missing class $classPath"
                    }
                }
            }
        }

        $abiChecks = @(
            @{
                Entry = 'dev/ryanhcode/sable/physics/ReactionWheelManager.class'
                Forbidden = 'm_58900_'
                Message = 'Reaction wheel interface call was remapped as a Minecraft BlockEntity method'
            },
            @{
                Entry = 'dev/ryanhcode/sable/neoforge/mixin/compatibility/create/hose_pulley/HosePulleyFluidHandlerMixin.class'
                Forbidden = 'net/neoforged/neoforge/fluids/FluidStack'
                Message = 'NeoForge FluidStack descriptor entered the Forge hose pulley mixin'
            },
            @{
                Entry = 'dev/ryanhcode/sable/api/block/propeller/BlockEntityPropeller.class'
                Required = @('m_58904_', 'm_58899_')
                Message = 'Propeller API is missing its remapped BlockEntity accessor bridge'
            },
            @{
                Entry = 'dev/ryanhcode/sable/api/SubLevelAssemblyHelper.class'
                Required = @('calculateHangingSupportBox', 'm_121921_', 'm_82383_')
                Forbidden = 'm_122428_'
                Message = 'Hanging-entity migration is not using the direction-agnostic 1.21 support-box backport'
            },
            @{
                Entry = 'dev/ryanhcode/sable/util/DiagnosticLogLimiter.class'
                Required = @('DIAGNOSTIC_LOG_TOTAL_LIMIT', 'DIAGNOSTIC_LOG_PER_PROBE_LIMIT', 'ConcurrentHashMap')
                Message = 'Production diagnostics are missing the configurable shared probe budget'
            },
            @{
                Entry = 'dev/ryanhcode/sable/mixin/port/sublevel_render/LevelRendererMixin.class'
                Required = @(
                    'sable$renderSubLevelBlockEntities',
                    'sable$renderSingleBlockSubLevels',
                    'Lorg/spongepowered/asm/mixin/injection/Redirect;',
                    '(Lnet/minecraft/client/renderer/LevelRenderer;Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)Ljava/util/Set;',
                    '(Lnet/minecraft/client/renderer/DimensionSpecialEffects;Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)Z'
                )
                Message = 'Forge 1.20.1 render-stage bridges still allocate renderLevel CallbackInfo locals instead of redirecting their exact operations'
            },
            @{
                Entry = 'dev/ryanhcode/sable/neoforge/mixin/admitted/GlobalSubLevelBlockEntityMixin.class'
                Required = @(
                    'sable$captureWorldRenderBasis',
                    'Lorg/spongepowered/asm/mixin/injection/Redirect;',
                    '(Lcom/mojang/blaze3d/vertex/PoseStack;)V',
                    'Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V'
                )
                Forbidden = @(
                    'org/spongepowered/asm/mixin/injection/callback/CallbackInfo',
                    'Lorg/spongepowered/asm/mixin/injection/ModifyVariable;'
                )
                Message = 'Global block-entity pose capture is not localized to its own PoseStack push operation'
            },
            @{
                Entry = 'dev/ryanhcode/sable/neoforge/mixin/block_outline_render/LevelRendererMixin.class'
                Required = @(
                    'sable$renderSubLevelHighlight',
                    'sable$renderSubLevelHitOutline',
                    'Lorg/spongepowered/asm/mixin/injection/Redirect;',
                    'Lorg/spongepowered/asm/mixin/gen/Invoker;',
                    'ForgeHooksClient',
                    'renderHitOutline'
                )
                Forbidden = @(
                    'org/spongepowered/asm/mixin/injection/callback/CallbackInfo',
                    'com/llamalad7/mixinextras/injector/wrapoperation',
                    'com/llamalad7/mixinextras/sugar/Share'
                )
                Message = 'Block-outline transforms still share renderLevel locals instead of redirecting owned calls'
            },
            @{
                Entry = 'dev/ryanhcode/sable/mixin/block_decal_render/LevelRendererMixin.class'
                Required = @(
                    'sable$translateBlockDamage',
                    'Lorg/spongepowered/asm/mixin/injection/Redirect;',
                    'Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V',
                    'destructionProgress',
                    'renderBreakingTexture'
                )
                Forbidden = @(
                    'org/spongepowered/asm/mixin/injection/callback/CallbackInfo',
                    'com/llamalad7/mixinextras/injector/wrapoperation',
                    'com/llamalad7/mixinextras/sugar/Local'
                )
                Message = 'Block-damage transform is not localized before the decal consumer captures its PoseStack matrices'
            }
        )
        foreach ($check in $abiChecks) {
            $entry = $archive.GetEntry($check.Entry)
            if ($null -eq $entry) {
                throw "Production JAR is missing $($check.Entry)"
            }
            $stream = $entry.Open()
            try {
                $reader = [System.IO.BinaryReader]::new($stream)
                try {
                    $bytes = $reader.ReadBytes([int]$entry.Length)
                    $classText = [System.Text.Encoding]::ASCII.GetString($bytes)
                }
                finally {
                    $reader.Dispose()
                }
            }
            finally {
                $stream.Dispose()
            }
            if ($check.ContainsKey('Forbidden')) {
                foreach ($forbidden in @($check.Forbidden)) {
                    if (-not [string]::IsNullOrEmpty($forbidden) -and $classText.Contains($forbidden)) {
                        throw $check.Message
                    }
                }
            }
            foreach ($required in @($check.Required)) {
                if (-not [string]::IsNullOrEmpty($required) -and -not $classText.Contains($required)) {
                    throw $check.Message
                }
            }
        }
    }
    finally {
        $archive.Dispose()
    }
}

$auditScript = Join-Path $PSScriptRoot 'audit-public-tree.ps1'
if (Test-Path -LiteralPath $auditScript -PathType Leaf) {
    & $auditScript -Repository $repositoryPath
}
if ($Mode -eq 'audit') {
    Write-Host 'Audit complete. No build was requested.'
    return
}

$javaHome = Find-Java17
if (-not $javaHome) {
    if ($Offline) {
        throw 'Java 17 was not found and offline mode cannot download it.'
    }
    $javaHome = Install-Java17
}
$env:JAVA_HOME = $javaHome
$env:Path = (Join-Path $javaHome 'bin') + [IO.Path]::PathSeparator + $env:Path

$gradleExecutable = Join-Path $toolCache "gradle-$gradleVersion\bin\gradle.bat"
if (-not (Test-Path -LiteralPath $gradleExecutable -PathType Leaf)) {
    if ($Offline) {
        throw 'Gradle is not cached. Run once without -Offline so the verified distribution can be downloaded.'
    }
    else {
        $gradleExecutable = Install-Gradle
    }
}

$buildArguments = @('--no-daemon', '--stacktrace', ':forge:clean', ':forge:build')
if ($Offline) {
    $buildArguments += '--offline'
}
if ($skipTests) {
    $buildArguments += @('-x', ':forge:test')
}

Write-Host "Build mode: $Mode"
Write-Host "Building Sable with Java $((Get-JavaMajorVersion -JavaExecutable (Join-Path $javaHome 'bin\java.exe'))) and Gradle $gradleVersion"
& $gradleExecutable @buildArguments
$sableBuildExitCode = $LASTEXITCODE
Move-BuildLogs -SourceDirectory (Join-Path $repositoryPath 'forge\logs') -Component 'forge'
if ($sableBuildExitCode -ne 0) {
    throw "Sable build failed with exit code $sableBuildExitCode"
}

$properties = @{}
foreach ($line in Get-Content -LiteralPath (Join-Path $repositoryPath 'gradle.properties')) {
    if ($line -match '^\s*([^#!][^=]*)=(.*)$') {
        $properties[$Matches[1].Trim()] = $Matches[2].Trim()
    }
}
$artifactName = "sable-forge-$($properties.minecraft_version)-$($properties.version).jar"
$artifactPath = Join-Path $repositoryPath "forge\build\libs\$artifactName"
if (-not (Test-Path -LiteralPath $artifactPath -PathType Leaf)) {
    throw "Expected production JAR was not created: $artifactPath"
}
Assert-ProductionJar -JarPath $artifactPath

New-Item -ItemType Directory -Path $distributionDirectory -Force | Out-Null
$generatedNames = @(
    'SHA256SUMS.txt',
    $artifactName
)
foreach ($generatedName in $generatedNames) {
    $generatedPath = Join-Path $distributionDirectory $generatedName
    if (Test-Path -LiteralPath $generatedPath -PathType Leaf) {
        [System.IO.File]::Delete($generatedPath)
    }
}
foreach ($oldTestJar in Get-ChildItem -LiteralPath $distributionDirectory -Filter 'sable-test-tools-forge-1.20.1-*.jar' -File -ErrorAction SilentlyContinue) {
    [System.IO.File]::Delete($oldTestJar.FullName)
}
$distributionJar = Join-Path $distributionDirectory $artifactName
Copy-Item -LiteralPath $artifactPath -Destination $distributionJar -Force
$hashLines = [System.Collections.Generic.List[string]]::new()
$artifactHash = Get-Sha256 -Path $distributionJar
$hashLines.Add("$artifactHash  $artifactName")

if ($includeTestTools) {
    $testToolsPath = Join-Path $repositoryPath 'test-tools'
    $testArguments = @('--no-daemon', '--stacktrace', '-p', $testToolsPath, 'clean', 'build')
    if ($Offline) {
        $testArguments += '--offline'
    }
    & $gradleExecutable @testArguments
    $testToolsBuildExitCode = $LASTEXITCODE
    Move-BuildLogs -SourceDirectory (Join-Path $testToolsPath 'logs') -Component 'test-tools'
    if ($testToolsBuildExitCode -ne 0) {
        throw "Sable Test Tools build failed with exit code $testToolsBuildExitCode"
    }
    $testJars = @(Get-ChildItem -LiteralPath (Join-Path $testToolsPath 'build\libs') -Filter 'sable-test-tools-forge-1.20.1-*.jar' -File |
        Where-Object { $_.Name -notmatch '-sources\.jar$' })
    if ($testJars.Count -ne 1) {
        throw "Expected one Sable Test Tools JAR, found $($testJars.Count)."
    }
    $testJar = $testJars[0]
    $testDestination = Join-Path $distributionDirectory $testJar.Name
    Copy-Item -LiteralPath $testJar.FullName -Destination $testDestination -Force
    $testHash = Get-Sha256 -Path $testDestination
    $hashLines.Add("$testHash  $($testJar.Name)")
}

[System.IO.File]::WriteAllLines(
    (Join-Path $distributionDirectory 'SHA256SUMS.txt'),
    $hashLines,
    [System.Text.UTF8Encoding]::new($false))

Write-Host ''
Write-Host "Build complete: $distributionJar"
Write-Host "SHA-256: $artifactHash"
if ($includeTestTools) {
    Write-Host "Sable Test Tools and checksums are also in $distributionDirectory"
}
