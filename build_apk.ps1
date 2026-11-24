param(
    [string]$CustomBuildRoot = "",
    [switch]$Lint,
    [switch]$SkipRelease
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($CustomBuildRoot) -and (Test-Path "D:\deepseek\builds\gdou-haiyu-schedule")) {
    $CustomBuildRoot = "D:\deepseek\builds\gdou-haiyu-schedule"
}
if ((Test-Path "D:\deepseek\gradle-home") -and [string]::IsNullOrWhiteSpace($env:GRADLE_USER_HOME)) {
    $env:GRADLE_USER_HOME = "D:\deepseek\gradle-home"
}
if ((Test-Path "D:\deepseek\jdk-17\bin\java.exe") -and (-not (Test-Path "$env:JAVA_HOME\bin\java.exe"))) {
    $env:JAVA_HOME = "D:\deepseek\jdk-17"
    $env:PATH = "D:\deepseek\jdk-17\bin;$env:PATH"
}
if ((Test-Path "D:\deepseek\android-sdk") -and [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
    $env:ANDROID_HOME = "D:\deepseek\android-sdk"
}

$gradleArgs = @("testDebugUnitTest")
if ($Lint) {
    $gradleArgs += "lintDebug"
}
if (-not $SkipRelease) {
    $gradleArgs += "assembleRelease"
}
$gradleArgs += "--no-daemon"

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

if (-not $SkipRelease) {
    $outDir = if (Test-Path "D:\deepseek\qa-artifacts\gdou-haiyu-schedule") {
        "D:\deepseek\qa-artifacts\gdou-haiyu-schedule"
    } else {
        Join-Path $PSScriptRoot "output"
    }
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null

    $apkSrc = if (-not [string]::IsNullOrWhiteSpace($CustomBuildRoot)) {
        Join-Path $CustomBuildRoot "app\outputs\apk\release\app-release.apk"
    } else {
        Join-Path $PSScriptRoot "app\build\outputs\apk\release\app-release.apk"
    }

    $gradleFile = Join-Path $PSScriptRoot "app\build.gradle.kts"
    $verMatch = [regex]::Match((Get-Content $gradleFile -Raw), 'versionName\s*=\s*"([^"]+)"')
    $versionName = if ($verMatch.Success) { $verMatch.Groups[1].Value } else { "latest" }
    $apkDst = Join-Path $outDir "HaiyuSchedule-GDOU-v$versionName.apk"
    Copy-Item -Force $apkSrc $apkDst
    Write-Host "APK ready at: $apkDst" -ForegroundColor Green
}
