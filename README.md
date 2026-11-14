# yt-dlp for Android

**Unofficial, free, open-source Android front end for [yt-dlp](https://github.com/yt-dlp/yt-dlp).** No ads, no analytics,
no accounts, no telemetry. Licensed GPL-3.0.

> Working name. The final app name and package id are not decided yet (`docs/11-BRANDING-AND-UX.md`).

This project is **not affiliated with, endorsed by, or supported by** the yt-dlp maintainers. yt-dlp is a separate project
under the Unlicense; this app only drives it. Downloading may violate a site's terms of service or copyright law in your
country — you are responsible for what you download.

## Status

| Phase | State |
|---|---|
| Environment setup | done on the owner's PC (`docs/dev-setup.md`) |
| Phase 0 — engine spike on a real arm64 phone | **next**; see `docs/01-PLAN.md` |
| Phase 1 — MVP (share target, picker, queue, notifications) | not started |
| Phase 2 — parity (playlists, trim, audio, subs, cookies, updates) | not started |
| Phase 3 — release (baseline profiles, a11y, signing, F-Droid metadata) | not started |

The current app is a hello-world Compose screen used to prove the toolchain. It has no download functionality yet.

## Build and test

Requirements: JDK 17, Android SDK with `platforms;android-37.0` and `build-tools;37.0.0`, and `ANDROID_HOME` set. Exact
versions, install locations and re-creation steps: `docs/dev-setup.md`.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug   # tests + lint (must be green before every commit)
.\gradlew.bat assembleDebug                 # APK: app\build\outputs\apk\debug\app-debug.apk
.\tools\build-debug.ps1                     # tests + build + copy APK and SHA-256 into dist\
.\tools\check-env.ps1                       # verify JDK/SDK/adb/wrapper
```

Install on a phone: `adb install -r dist\<file>.apk`, or copy the APK to the phone and open it.

## Stack

Kotlin, Jetpack Compose + Material 3, single activity, Room, DataStore, WorkManager + foreground service,
kotlinx.serialization, Coil. The engine (`youtubedl-android`) sits behind an `Engine` interface so it can be faked in tests
and replaced later.

## Repository layout

| Path | What |
|---|---|
| `app/` | the Android application module |
| `gradle/libs.versions.toml` | single place for every dependency version |
| `contracts/` | `errors.json` (error taxonomy), `allowed-options.json` (default-deny yt-dlp options), `sites.json` |
| `docs/` | plan, architecture, research log, progress, verification log, decisions (ADRs) |
| `tools/` | build/check helpers (PowerShell; `.sh` variants for other machines) |
| `dist/` | local APK output (git-ignored) |

Start with `AGENTS.md` (contribution rules), then `docs/01-PLAN.md`, then `docs/PROGRESS.md` for the current state.

## Contributing

- GPL-3.0-only; contributions are accepted under the same license.
- No code copied from Snaptube, Seal, YTDLnis or NewPipe — reading them to learn behaviour is fine.
- No ads, analytics, crash-upload SDKs, remote code execution or cleartext HTTP.
- Every feature lands with tests in the same commit; `./gradlew testDebugUnitTest lintDebug` must be green.

## License

GPL-3.0-only. See `LICENSE`, `THIRD_PARTY_NOTICES.md` and `docs/decisions/0001-license-gpl3.md`.
