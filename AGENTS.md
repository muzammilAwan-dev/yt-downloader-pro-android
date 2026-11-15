# AGENTS.md: rules for AI coding agents in this repo (OpenCode loads this file automatically)

YTDLP: an unofficial, free, GPL-3.0 Android front end for yt-dlp. No ads, no analytics, no accounts.
Current state: `docs/STATE.md` (loaded for you via `opencode.jsonc`). Plan: `docs/01-PLAN.md`. Doc index: `README.md`.
OpenCode does not follow file references inside this file: open detail docs yourself, and only the one the current step needs.

## How to work (one small step at a time)
1. Take the next unchecked item in `docs/STATE.md`. Say what you will change (about 5 files at most).
2. Tests first for logic (fake `Engine`), smallest change, then run `.\gradlew.bat testDebugUnitTest lintDebug --console=plain`
   (Windows) or `./gradlew ...` (Linux/macOS). Quote the real `BUILD` line and test/lint counts.
3. Commit (Conventional Commits). Update `docs/STATE.md`; append at most 10 lines to `docs/PROGRESS.md`. Stop and report.
Slash commands: `/next` does the loop, `/verify` runs the checks, `/review` asks the read-only reviewer, `/handoff` refreshes STATE.md.
Do not read `docs/PROGRESS.md` or `docs/RESEARCH-LOG.md` in full; `grep` them. After a context reset, re-read this file and `docs/STATE.md` only.

## Always
- Be factual. Never claim a build or test passed unless you ran it and saw the output. Mark unrun code "untested".
- Never invent versions or APIs. Look them up (newest sources first) or mark `TODO(verify)`. Log research in `docs/RESEARCH-LOG.md`.
- If research contradicts `docs/`, write the conflict and a recommendation in `docs/RESEARCH-LOG.md` and ask the owner.
- Stop at gates and wait for the owner. Current gate: the owner's phone report on the next stamped APK before starting a new
  major feature area (browser/login, playlists, engine update).

## Never
- Copy code from Snaptube, Seal, YTDLnis or NewPipe, or use those names or "YouTube" in the app name/icon/package id. Reading public GPL projects to
  learn behavior is fine; write original code.
- Clean-room rule: do not read decompiled or reverse-engineered third-party source (the owner's `REA` folder) in a coding session.
  Work from the written notes in `docs/03-*` and `docs/16-*`. If a note is not enough, ask the owner.
- Add ads, analytics, crash-upload SDKs, remote code execution, or cleartext HTTP.
- Persist cookies in task history or keep a cookie file after its task ends. Redact cookies, auth headers, PO tokens and signed-URL queries
  before anything is logged (the redactor has unit tests; keep them green).
- Request permissions beyond: INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS, FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC
  (WAKE_LOCK only if proven necessary).
- Push, force-push, reset --hard, or delete files outside the repo without the owner saying so.

## Identity (owner decision, 15 Nov 2026)
Display name **YTDLP**, with the owner's desktop icon (MIT notice bundled in `app/src/main/assets/licenses/`). Keep package id
`io.github.muzammilawan.clipharbor` so installs update in place; do not rename private cleanup prefixes. Owner asset reuse does not allow
copying competitor assets or implying yt-dlp/site endorsement. Name/trademark clearance is unverified; the spaced expansion "You Tube
Downloader Pro" conflicts with the no-"YouTube" rule above, so add no new uses of it until the owner resolves that (see `docs/STATE.md`).

## Stack and contracts
Kotlin, Jetpack Compose + Material 3, single activity. Planned: Room, DataStore, WorkManager + foreground service, kotlinx.serialization,
Coil (add each only when a step needs it, with the version checked). Engine (`youtubedl-android`) stays behind the `Engine` interface.
`contracts/errors.json` (error taxonomy), `contracts/allowed-options.json` (default-deny yt-dlp options), `contracts/sites.json`.

## Tests, logging, CI
- Every feature ships with tests in the same commit. A step is done only when `testDebugUnitTest lintDebug` is green.
  Never make a live-YouTube test a required CI check. Details: `docs/12-AUTOMATED-TESTS-AND-DEEP-LOGGING.md`.
- Diagnostics screen and rotating redacted `key=value` log stay working (self-test, share log, copy summary, clear). No network upload.
  Debug builds also copy the session log to `Download/YTDLP-logs/` through MediaStore (skip below API 29; no new permission).
- CI (`.github/workflows/android.yml`): module `app`; tasks `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleRelease`; signing env vars
  `ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Push/PR run tests only; a manual run builds the APK.
  Do not edit the workflow except to keep it consistent with real task and module names.
- The owner builds with `tools/build-debug.ps1`, copies the APK to the phone and installs it (`docs/13-LOCAL-BUILD-AND-INSTALL.md`).

## Models
Primary: `agentrouter/gpt-6.1-sol`. The provider and its API key live in the owner's user-level OpenCode config, never in the repo. Free fallbacks are in
`opencode.jsonc`. There is no automatic rate-limit fallback: select a free model with `/models` in the same session. The `build-free` and
`build-free-2` profiles are optional; verify the selected model (V2 keeps a session's model when changing agents). On a free model, keep steps even smaller.
