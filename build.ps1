param(
    [string[]]$Tasks = @('assembleDebug', 'assembleDebugAndroidTest'),
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
    $candidates = @(
        (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'),
        (Join-Path $env:LOCALAPPDATA 'Programs\Android Studio\jbr')
    )
    $jdks = Join-Path $env:USERPROFILE '.jdks'
    if (Test-Path -LiteralPath $jdks) {
        $candidates += Get-ChildItem -LiteralPath $jdks -Directory |
            Where-Object { $_.Name -match 'jbr.*21|jdk-21|jdk-17|jdk-25' } |
            Sort-Object Name -Descending | Select-Object -ExpandProperty FullName
    }
    $env:JAVA_HOME = $candidates | Where-Object { Test-Path -LiteralPath (Join-Path $_ 'bin\javac.exe') } | Select-Object -First 1
}
if (-not $env:JAVA_HOME) { throw 'Install JDK 17 or newer and set JAVA_HOME.' }

if (-not (Test-Path -LiteralPath 'local.properties')) {
    $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    if (-not (Test-Path -LiteralPath $sdk)) { throw 'Install Android SDK 36.1 and build-tools 36.0.0, or set ANDROID_HOME.' }
    ('sdk.dir=' + $sdk.Replace('\', '/').Replace(':', '\:')) | Set-Content -LiteralPath 'local.properties' -Encoding ascii
}

$gradleArgs = @('--no-daemon', '--console=plain')
if ($Offline) { $gradleArgs += '--offline' }
if (-not $Offline) {
    $proxy = Get-ItemProperty -LiteralPath 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings' -ErrorAction SilentlyContinue
    if ($proxy.ProxyEnable -eq 1 -and $proxy.ProxyServer -match '^([^:;=]+):(\d+)$') {
        $gradleArgs += "-Dhttps.proxyHost=$($Matches[1])", "-Dhttps.proxyPort=$($Matches[2])", "-Dhttp.proxyHost=$($Matches[1])", "-Dhttp.proxyPort=$($Matches[2])"
    }
}
& (Join-Path $PSScriptRoot 'gradlew.bat') @gradleArgs @Tasks
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if ($Tasks -contains 'assembleDebug') {
    $apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk'
    if (Test-Path -LiteralPath $apk) {
        New-Item -ItemType Directory -Force -Path 'artifacts' | Out-Null
        Copy-Item -LiteralPath $apk -Destination 'artifacts\WuCang-1.0.0-debug.apk' -Force
        Write-Output 'APK: artifacts\WuCang-1.0.0-debug.apk'
    }
}
