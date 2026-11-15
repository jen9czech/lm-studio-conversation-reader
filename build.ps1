param([string[]]$Tasks = @('assembleDebug', 'testDebugUnitTest', 'lintDebug'))
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $localJava = Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot '.tools/java') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($localJava) { $env:JAVA_HOME = $localJava.FullName }
    $env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.tools/gradle-home'
    if (Test-Path -LiteralPath '.tools/android-sdk') {
        $env:ANDROID_HOME = Join-Path $PSScriptRoot '.tools/android-sdk'
    }
    if (Test-Path -LiteralPath '.tools/gradle-8.13/bin/gradle.bat') {
        & .tools/gradle-8.13/bin/gradle.bat --no-daemon @Tasks
    } else {
        & .\gradlew.bat --no-daemon @Tasks
    }
    if ($LASTEXITCODE -ne 0) { throw "Build failed ($LASTEXITCODE)." }
} finally { Pop-Location }
