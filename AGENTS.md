# AGENTS.md: rules for AI coding agents in this repo (opencode reads this file automatically)

Project: unofficial, free, open-source Android app for yt-dlp (GPL-3.0). Plan and facts: `docs/01-PLAN.md`, `docs/10-VERIFICATION-LOG.md`. Detail docs are listed in `README.md`; read them on demand.

## Always
- Be factual. Do not claim a build/test passed unless you ran it and saw the output. Mark unrun code "untested". Never invent versions or APIs; look up or mark `TODO(verify)`.
- Log research in `docs/RESEARCH-LOG.md`; log progress in `docs/PROGRESS.md`. Re-read both after any context reset.
- Work in small steps (about 5 files), then build/test, then commit.

## Never
- Copy code from Snaptube, Seal, YTDLnis or NewPipe; use the names "Snaptube", "Seal", "YTDLnis" or "YouTube" in app name/icon/package id.
- Add ads, analytics, crash-upload SDKs, remote code execution, or cleartext HTTP.
- Persist cookies in task history or keep a cookie file after its task ends.
- Request permissions beyond: INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS, FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC (WAKE_LOCK only if proven necessary).

## Stack
Kotlin, Jetpack Compose + Material 3, single activity, Room, DataStore, WorkManager + foreground service, kotlinx.serialization, Coil. Engine behind an `Engine` interface (youtubedl-android).

## Contracts
`contracts/errors.json` (error taxonomy), `contracts/allowed-options.json` (default-deny yt-dlp options), `contracts/sites.json` (site catalog).

## CI contract
Module `app`; tasks `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleRelease`; signing env vars `ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.
Push/PR run tests only; manual workflow run builds and uploads the APK.

## Tests and logging (required from Phase 0; details in docs/12-AUTOMATED-TESTS-AND-DEEP-LOGGING.md)
- Every feature ships with tests in the same commit; a phase is done only when `./gradlew testDebugUnitTest lintDebug` is green (paste the output into docs/PROGRESS.md).
- Use a fake `Engine` for logic tests. Never make a live-YouTube test a required CI check.
- Build a Diagnostics screen: "Run self-test", "Share log", "Copy summary", "Clear logs". Rotating file log in app-private storage, greppable `key=value` lines with `task`/`stage`/`event`,
  header with app/device/engine versions, last ~200 lines of yt-dlp `-v` output for failed tasks. Verbose ON in debug/tester builds. No network upload.
- Redact cookies, auth headers, PO tokens and signed-URL query strings before writing; unit-test the redactor.

## Local build and log location (docs/13-LOCAL-BUILD-AND-INSTALL.md)
- Owner builds from the command line (`gradlew assembleDebug`), copies the APK to the phone, and installs it. Keep `tools/build-debug.*` working.
- Debug/tester builds write a per-session log copy to `Downloads/<app-name>-logs/` via MediaStore (skip on API < 29; no new permissions). Release builds do not.
- After creating the project, tell the owner the exact SDK platform and build-tools versions to install with `sdkmanager`. Do not guess them.

## Setup task (docs/14-AGENT-SETUP-TASK.md)
- Before Phase 0 (and before research-driven coding), set up the environment and a LOCAL git repo exactly as docs/14 says: print the install plan, wait for one explicit "yes", install only from official sources at user level,
  no GitHub remote (the owner adds it later), prove the toolchain with `tools/check-env.*` and a hello-world `assembleDebug`, then stop and report.

## Gates
Stop after Phase 0 and wait for the owner's device report before starting Phase 1.
