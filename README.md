# YTDLP — You Tube Downloader Pro

**Unofficial, free, open-source Android front end for [yt-dlp](https://github.com/yt-dlp/yt-dlp).** No ads, no analytics,
no accounts, no telemetry. Licensed GPL-3.0.

> Owner-selected desktop/Android name and icon (15 Nov 2026; `docs/11-BRANDING-AND-UX.md`). Formerly ClipHarbor;
> package id stays `io.github.muzammilawan.clipharbor` so existing installations update in place.

This project is **not affiliated with, endorsed by, or supported by** the yt-dlp maintainers. yt-dlp is a separate project
under the Unlicense; this app only drives it. Downloading may violate a site's terms of service or copyright law in your
country — you are responsible for what you download.

## Status

| Phase | State |
|---|---|
| Environment setup | done on the owner's PC (`docs/dev-setup.md`) |
| Phase 0 — engine spike on a real arm64 phone | **passed 15 Nov 2026** on an Infinix X682B (Android 10, arm64): 7/7 self-test, merged MP4 published via MediaStore. Evidence: `docs/evidence/phase0.md` |
| Phase 1 — MVP (share target, picker, queue, notifications) | **in progress**: engine replay and actual 1080p video/audio selection verified on the owner's phone; local tests/lint green. MVP UI/queue/service still pending (`docs/PROGRESS.md`) |
| Phase 2 — parity (playlists, trim, audio, subs, cookies, updates) | not started |
| Phase 3 — release (baseline profiles, a11y, signing, F-Droid metadata) | not started |

The current UI is still the diagnostics spike: it unpacks the yt-dlp engine, runs a 7-check self-test, and can download
1080p video + audio, merge them and publish the result to `Download/YTDLP`. The engine fixes were checked on a real arm64
phone (Infinix X682B, Android 10): self-test 7/7, task-private metadata replay, actual `299+140`/1080 selection and successful
MP4 publishing. Playback/audio/gallery confirmation and cancellation checks remain pending (`docs/PROGRESS.md`). Older
files remain in `Download/ClipHarbor`; the rename does not move or delete them. New debug logs use `Download/YTDLP-logs`.
The launcher icon is the owner's supplied desktop artwork, with its MIT notice included in the APK.

The product it is catching up with is the owner's Windows app,
[muzammilAwan-dev/yt-downloader-pro](https://github.com/muzammilAwan-dev/yt-downloader-pro) (MIT): its feature set and its
hardened-option rules are the parity target (`docs/04-OPEN-SOURCE-REFERENCES.md`).

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
UI/UX resource research and the screenshot checklist: `docs/15-UI-UX-DESIGN-RESEARCH.md`.

## Contributing

- GPL-3.0-only; contributions are accepted under the same license.
- No code copied from Snaptube, Seal, YTDLnis or NewPipe — reading them to learn behaviour is fine.
- No ads, analytics, crash-upload SDKs, remote code execution or cleartext HTTP.
- Every feature lands with tests in the same commit; `./gradlew testDebugUnitTest lintDebug` must be green.

## License

GPL-3.0-only. See `LICENSE`, `THIRD_PARTY_NOTICES.md` and `docs/decisions/0001-license-gpl3.md`.
