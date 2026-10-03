param(
    [switch]$Install,
    [string]$Serial
)

$ErrorActionPreference = "Stop"

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,
        [Parameter(ValueFromRemainingArguments = $true)]
        [string[]]$Arguments
    )

    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed ($LASTEXITCODE): $FilePath $($Arguments -join ' ')"
    }
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repoRoot

Write-Host "== Wallpaper Fit Slideshow local verification =="
Write-Host "Repo: $repoRoot"

if (-not $env:JAVA_HOME) {
    $studioJbr = "C:\Program Files\Android\Android Studio\jbr"
    if (Test-Path (Join-Path $studioJbr "bin\java.exe")) {
        $env:JAVA_HOME = $studioJbr
        Write-Host "JAVA_HOME: $env:JAVA_HOME (Android Studio JBR)"
    }
}

$javaExe = $null
if ($env:JAVA_HOME) {
    $javaExe = Join-Path $env:JAVA_HOME "bin\java.exe"
} else {
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($javaCommand) {
        $javaExe = $javaCommand.Source
    }
}

if (-not $javaExe -or -not (Test-Path $javaExe)) {
    throw "Java was not found. Install Android Studio or JDK 17+."
}

$sdk = $env:ANDROID_HOME
if (-not $sdk) {
    $sdk = $env:ANDROID_SDK_ROOT
}
if (-not $sdk) {
    $defaultSdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    if (Test-Path $defaultSdk) {
        $sdk = $defaultSdk
    }
}

if (-not $sdk -or -not (Test-Path $sdk)) {
    throw "Android SDK was not found. Check ANDROID_HOME, ANDROID_SDK_ROOT, or Android Studio SDK settings."
}

$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk

$platform36 = Join-Path $sdk "platforms\android-36"
if (-not (Test-Path $platform36)) {
    throw "Android SDK Platform 36 is missing. Install Android 16 / API 36 from SDK Manager."
}

Write-Host "Android SDK: $sdk"
Invoke-Checked $javaExe "-version"

Write-Host ""
Write-Host "[1/3] Unit tests"
Invoke-Checked ".\gradlew.bat" "--no-daemon" "testDebugUnitTest"

Write-Host ""
Write-Host "[2/3] Android lint"
Invoke-Checked ".\gradlew.bat" "--no-daemon" "lintDebug"

Write-Host ""
Write-Host "[3/3] Debug APK"
Invoke-Checked ".\gradlew.bat" "--no-daemon" "assembleDebug"

$apk = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) {
    throw "Build finished but APK was not found: $apk"
}

Write-Host ""
Write-Host "PASS: local tests / lint / debug build"
Write-Host "APK: $apk"

if (-not $Install) {
    exit 0
}

$adb = Join-Path $sdk "platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    throw "adb was not found. Install Android SDK Platform-Tools."
}

Write-Host ""
Write-Host "== Install to device =="
Invoke-Checked $adb "start-server"

$deviceLines = & $adb devices | Select-Object -Skip 1 | Where-Object {
    $_ -match "\tdevice$"
}
if (-not $deviceLines) {
    throw "No authorized ADB device found. Check USB debugging and device authorization."
}

$adbArgs = @()
if ($Serial) {
    $adbArgs += "-s"
    $adbArgs += $Serial
}

& $adb @adbArgs install -r $apk
if ($LASTEXITCODE -ne 0) {
    throw "APK install failed."
}

Write-Host "PASS: installed debug APK"
