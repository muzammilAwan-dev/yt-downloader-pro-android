# Verify the Android build environment. Usage (from repo root):
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\check-env.ps1
# The ExecutionPolicy flag is needed on machines where running local scripts is disabled; it affects this process only.
$ok = $true
function Check($name, [scriptblock]$cmd) {
    try { $out = & $cmd 2>&1 | Select-Object -First 1; Write-Host ("[ OK ] {0,-14} {1}" -f $name, $out) }
    catch { Write-Host ("[FAIL] {0,-14} {1}" -f $name, $_.Exception.Message); $script:ok = $false }
}
Check "git"            { git --version }
Check "java"           { java -version }
Check "JAVA_HOME"      { if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) { throw "JAVA_HOME not set or missing bin\java.exe" } else { $env:JAVA_HOME } }
Check "ANDROID_HOME"   { if (-not $env:ANDROID_HOME -or -not (Test-Path $env:ANDROID_HOME)) { throw "ANDROID_HOME not set or missing" } else { $env:ANDROID_HOME } }
# cmdline-tools 23+ deprecates sdkmanager and prints WARNING lines first; keep only a version-looking line.
Check "sdkmanager"     { sdkmanager --version 2>&1 | Where-Object { $_ -match '\d+\.\d+' -and $_ -notmatch 'deprecated|binary can also|replacement|documentation' } | Select-Object -First 1 }
Check "adb"            { adb version }
Check "SDK platform"   { if (-not (Test-Path "$env:ANDROID_HOME\platforms\android-37.0")) { throw "platforms\android-37.0 missing" } else { "android-37.0 installed" } }
Check "build-tools"    { if (-not (Test-Path "$env:ANDROID_HOME\build-tools\37.0.0")) { throw "build-tools\37.0.0 missing" } else { "37.0.0 installed" } }
Check "gradle wrapper" { if (-not (Test-Path .\gradlew.bat)) { throw "gradlew.bat missing (generate the wrapper once)" } else { (.\gradlew.bat --version | Select-String "Gradle ").Line } }
if ($ok) { Write-Host "`nEnvironment OK" } else { Write-Host "`nEnvironment has problems"; exit 1 }
