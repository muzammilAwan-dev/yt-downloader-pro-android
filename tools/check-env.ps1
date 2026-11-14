# UNTESTED: verify the Android build environment. Usage: .\tools\check-env.ps1
$ok = $true
function Check($name, [scriptblock]$cmd) {
    try { $out = & $cmd 2>&1 | Select-Object -First 1; Write-Host ("[ OK ] {0,-14} {1}" -f $name, $out) }
    catch { Write-Host ("[FAIL] {0,-14} {1}" -f $name, $_.Exception.Message); $script:ok = $false }
}
Check "git"           { git --version }
Check "java"          { java -version }
Check "ANDROID_HOME"  { if (-not $env:ANDROID_HOME -or -not (Test-Path $env:ANDROID_HOME)) { throw "ANDROID_HOME not set or missing" } else { $env:ANDROID_HOME } }
Check "sdkmanager"    { sdkmanager --version }
Check "adb"           { adb version }
Check "gradle wrapper" { if (-not (Test-Path .\gradlew.bat)) { throw "gradlew.bat missing (generate the wrapper once)" } else { (.\gradlew.bat --version | Select-String "Gradle ").Line } }
if ($ok) { Write-Host "`nEnvironment OK" } else { Write-Host "`nEnvironment has problems"; exit 1 }
