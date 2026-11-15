#!/usr/bin/env bash
# UNTESTED helper: run tests, build the debug APK, copy it to dist/ with a SHA-256.
# Usage (repo root): ./tools/build-debug.sh [--skip-tests]
set -euo pipefail
GIT_SHA="$(git rev-parse --short HEAD 2>/dev/null || echo unknown)"
if [ "${1:-}" != "--skip-tests" ]; then ./gradlew testDebugUnitTest lintDebug "-PgitSha=$GIT_SHA" --stacktrace; fi
./gradlew assembleDebug "-PgitSha=$GIT_SHA" --stacktrace
mkdir -p dist
cp app/build/outputs/apk/debug/*.apk dist/
(cd dist && sha256sum ./*.apk | tee SHA256SUMS.txt)
echo "Done. Install with: adb install -r dist/<file>.apk  (or copy dist/*.apk to the phone)"
