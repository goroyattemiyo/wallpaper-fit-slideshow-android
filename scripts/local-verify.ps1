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

$javaExe = if ($env:JAVA_HOME) {
    Join-Path $env:JAVA_HOME "bin\java.exe"
} else {
    (Get-Command java.exe -ErrorAction Stop).Source
}
if (-not (Test-Path $javaExe)) {
    throw "Javaが見つかりません。Android StudioのJBRまたはJDK 17+を用意してください。"
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
    throw "Android SDKが見つかりません。ANDROID_HOME/ANDROID_SDK_ROOT または Android Studio SDK を確認してください。"
}

$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk

if (-not (Test-Path (Join-Path $sdk "platforms\android-36"))) {
    throw "Android SDK Platform 36 がありません。Android Studio > SDK Manager で Android 16 (API 36) を追加してください。"
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
    throw "Buildは終了しましたがAPKが見つかりません: $apk"
}
Write-Host ""
Write-Host "PASS: local tests / lint / debug build"
Write-Host "APK: $apk"

if (-not $Install) {
    exit 0
}

$adb = Join-Path $sdk "platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    throw "adbが見つかりません。SDK Platform-Toolsを確認してください。"
}

Write-Host ""
Write-Host "== Install to device =="
Invoke-Checked $adb "start-server"

$deviceLines = & $adb devices | Select-Object -Skip 1 | Where-Object {
    $_ -match "\tdevice$"
}
if (-not $deviceLines) {
    throw "ADB接続済み端末がありません。USBデバッグと接続許可を確認してください。"
}

$adbArgs = @()
if ($Serial) {
    $adbArgs += @("-s", $Serial)
}

& $adb @adbArgs install -r $apk
if ($LASTEXITCODE -ne 0) {
    throw "APK install failed."
}

Write-Host "PASS: installed debug APK"
