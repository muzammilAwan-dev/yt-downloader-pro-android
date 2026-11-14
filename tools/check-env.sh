#!/usr/bin/env bash
# UNTESTED: verify the Android build environment. Usage: ./tools/check-env.sh
ok=0
chk() { local n="$1"; shift; if out=$("$@" 2>&1 | head -1); then printf '[ OK ] %-14s %s\n' "$n" "$out"; else printf '[FAIL] %-14s %s\n' "$n" "$out"; ok=1; fi; }
chk git git --version
chk java java -version
[ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME" ] && chk ANDROID_HOME echo "$ANDROID_HOME" || { echo "[FAIL] ANDROID_HOME not set or missing"; ok=1; }
chk sdkmanager sdkmanager --version
chk adb adb version
[ -x ./gradlew ] && chk "gradle wrapper" ./gradlew --version || { echo "[FAIL] gradlew missing (generate the wrapper once)"; ok=1; }
[ $ok -eq 0 ] && echo "Environment OK" || { echo "Environment has problems"; exit 1; }
