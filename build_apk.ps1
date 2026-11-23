param(
    [string]$CustomBuildRoot = ""
)

$ErrorActionPreference = "Stop"

$gradleArgs = @("testDebugUnitTest", "assembleRelease", "--no-daemon")
if (-not [string]::IsNullOrWhiteSpace($CustomBuildRoot)) {
    $gradleArgs = @("-PcustomBuildRoot=$CustomBuildRoot") + $gradleArgs
}

$wrapper = Join-Path $PSScriptRoot "gradlew.bat"
if (Test-Path $wrapper) {
    & $wrapper @gradleArgs
} else {
    gradle @gradleArgs
}
if ($LASTEXITCODE -ne 0) {
    throw "Gradle build failed with exit code $LASTEXITCODE"
}

$outDir = Join-Path $PSScriptRoot "output"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$apkSrc = if (-not [string]::IsNullOrWhiteSpace($CustomBuildRoot)) {
    Join-Path $CustomBuildRoot "app\outputs\apk\release\app-release.apk"
} else {
    Join-Path $PSScriptRoot "app\build\outputs\apk\release\app-release.apk"
}

$gradleFile = Join-Path $PSScriptRoot "app\build.gradle.kts"
$verMatch = [regex]::Match((Get-Content $gradleFile -Raw), 'versionName\s*=\s*"([^"]+)"')
$versionName = if ($verMatch.Success) { $verMatch.Groups[1].Value } else { "latest" }
$apkDst = Join-Path $outDir "海屿空间-广东海洋大学-v$versionName.apk"
Copy-Item -Force $apkSrc $apkDst
Write-Host "APK ready at: $apkDst" -ForegroundColor Green
