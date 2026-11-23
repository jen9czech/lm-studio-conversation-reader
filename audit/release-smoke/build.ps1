param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '../../.tools/release-smoke-build'),
    [string]$DebugKeystore = (Join-Path ([Environment]::GetFolderPath('UserProfile')) '.android/debug.keystore')
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$localJava = Get-ChildItem (Join-Path $root '.tools/java') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if ($localJava) { $env:JAVA_HOME = $localJava.FullName }
$sdk = Join-Path $root '.tools/android-sdk'
if (!(Test-Path $sdk)) { $sdk = $env:ANDROID_HOME }
if (!$sdk -or !$env:JAVA_HOME) { throw 'Set ANDROID_HOME and JAVA_HOME before building the smoke APK.' }
$jar = Join-Path $sdk 'platforms/android-36/android.jar'
$tools = Join-Path $sdk 'build-tools/35.0.0'
$null = New-Item -ItemType Directory -Path $OutputDirectory -Force
$output = (Resolve-Path -LiteralPath $OutputDirectory).Path
$classes = Join-Path $output 'classes'
$null = New-Item -ItemType Directory -Path $classes -Force
$keystore = (Resolve-Path -LiteralPath $DebugKeystore).Path

function Invoke-Checked([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$(Split-Path -Leaf $Program) exited with $LASTEXITCODE." }
}

Push-Location $output
try {
    Invoke-Checked (Join-Path $env:JAVA_HOME 'bin/javac.exe') @('--release', '11', '-encoding', 'UTF-8', '-classpath', $jar, '-d', $classes, (Join-Path $PSScriptRoot 'Runner.java'))
    Invoke-Checked (Join-Path $env:JAVA_HOME 'bin/jar.exe') @('cf', 'runner.jar', '-C', $classes, '.')
    Invoke-Checked (Join-Path $tools 'd8.bat') @('--lib', $jar, '--min-api', '26', '--output', $output, 'runner.jar')
    Invoke-Checked (Join-Path $tools 'aapt.exe') @('package', '-f', '-M', (Join-Path $PSScriptRoot 'AndroidManifest.xml'), '-I', $jar, '-F', 'unsigned.apk')
    Invoke-Checked (Join-Path $tools 'aapt.exe') @('add', 'unsigned.apk', 'classes.dex')
    Invoke-Checked (Join-Path $tools 'zipalign.exe') @('-f', '4', 'unsigned.apk', 'aligned.apk')
    Invoke-Checked (Join-Path $tools 'apksigner.bat') @('sign', '--ks', $keystore, '--ks-key-alias', 'androiddebugkey', '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--out', 'smoke.apk', 'aligned.apk')
    Get-Item (Join-Path $output 'smoke.apk')
} finally { Pop-Location }
