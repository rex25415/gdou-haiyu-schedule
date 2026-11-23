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

$outDir = Join-Path $PSScriptRoot "output"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$apkSrc = if (-not [string]::IsNullOrWhiteSpace($CustomBuildRoot)) {
    Join-Path $CustomBuildRoot "app\outputs\apk\release\app-release.apk"
} else {
    Join-Path $PSScriptRoot "app\build\outputs\apk\release\app-release.apk"
}

$apkDst = Join-Path $outDir "海屿课表-广东海洋大学-v2.4.0.apk"
Copy-Item -Force $apkSrc $apkDst
Write-Host "APK ready at: $apkDst" -ForegroundColor Green
