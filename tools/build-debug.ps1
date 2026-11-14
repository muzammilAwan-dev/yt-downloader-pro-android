# UNTESTED helper: run tests, build the debug APK, copy it to dist\ with a SHA-256.
# Usage (from repo root, PowerShell):  .\tools\build-debug.ps1   [-SkipTests]
param([switch]$SkipTests)
$ErrorActionPreference = "Stop"

function Run-Gradle([string[]]$gradleArgs) {
    & .\gradlew.bat @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed (exit $LASTEXITCODE): $($gradleArgs -join ' ')" }
}

if (-not $SkipTests) { Run-Gradle @("testDebugUnitTest", "lintDebug", "--stacktrace") }
Run-Gradle @("assembleDebug", "--stacktrace")

New-Item -ItemType Directory -Force -Path dist | Out-Null
Get-ChildItem -Path app\build\outputs\apk\debug -Filter *.apk | ForEach-Object {
    Copy-Item $_.FullName -Destination dist -Force
}
Get-ChildItem dist -Filter *.apk | ForEach-Object {
    $h = (Get-FileHash $_.FullName -Algorithm SHA256).Hash
    "$h  $($_.Name)"
} | Tee-Object -FilePath dist\SHA256SUMS.txt
Write-Host "Done. Install with: adb install -r dist\<file>.apk   (or copy dist\*.apk to the phone)"
