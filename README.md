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
| Phase 1 — MVP (share target, picker, queue, notifications) | **Implementation host-delivered; Android acceptance deferred**: compact video/original-audio/exact picker, durable queue/history, foreground ownership, recovery/storage/publication and full-session logs. Verified stamped APK: `docs/evidence/phase1-delivery.md`. Not a device/reliability pass; STOP before Phase 2. |
| Phase 2 — parity (playlists, trim, audio, subs, cookies, updates) | not started |
| Phase 3 — release (baseline profiles, a11y, signing, F-Droid metadata) | not started |

Home offers available video + audio presets and separate original audio streams, with explicit Download confirmation,
MediaStore publication to `Download/YTDLP`, and Diagnostics/self-test/Share session log. No audio conversion or durable queue yet.
The owner-installed engine tester `f96194e` ran bundled yt-dlp 2026.10.26 / EJS 0.8.0 on an Infinix X682B (Android 10),
replay-selected `299+140`/1920×1080 and saved a 314,296,147-byte MP4 without 403. This is one sample, not a general 403 fix.
New original-audio/UI playback, second video preset, sustained background/lock and Cancel checks remain pending;
the new tester's exact artifact/phone sequence is in `docs/evidence/original-audio-tester.md`.
Older files remain in `Download/ClipHarbor`; the rename does not move or delete them. New debug logs use `Download/YTDLP-logs`.
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

Start with `AGENTS.md` (contribution rules), then `docs/STATE.md` (current state, short), then `docs/01-PLAN.md`.
`opencode.jsonc` and `.opencode/commands/` hold the shared AI-agent workflow (`/next`, `/verify`, `/review`, `/handoff`).

## Documentation index

| File | What |
|---|---|
| `docs/STATE.md` | current state and next steps (short, kept current) |
| `docs/PROGRESS.md` | append-only history with pasted command output; search it, do not read it whole |
| `docs/RESEARCH-LOG.md` | dated research findings `R1`, `R2`, ... with sources |
| `docs/01-PLAN.md` | plan, scope, phases, risks |
| `docs/02-ARCHITECTURE.md` | architecture and state machine |
| `docs/03-SNAPTUBE-REA-FINDINGS.md` | observations from the owner's static analysis of a competitor (ideas only, no code) |
| `docs/04-OPEN-SOURCE-REFERENCES.md` | open-source prior art and the owner's desktop app |
| `docs/05-YOUTUBE-ENGINE-NOTES.md` | yt-dlp, JS runtime, PO-token notes |
| `docs/06-ANDROID-PLATFORM-NOTES.md` | foreground services, storage, notifications |
| `docs/07-LICENSE-AND-DISTRIBUTION.md` | licensing and distribution channels |
| `docs/08-DESKTOP-CONTRACT.md` | what the desktop app does that Android must match |
| `docs/09-TEST-STRATEGY.md` | test strategy |
| `docs/10-VERIFICATION-LOG.md` | which facts are verified and how |
| `docs/11-BRANDING-AND-UX.md` | naming, identity, UX principles |
| `docs/12-AUTOMATED-TESTS-AND-DEEP-LOGGING.md` | tests, diagnostics screen, log format |
| `docs/13-LOCAL-BUILD-AND-INSTALL.md` | build on the PC, install on the phone |
| `docs/evidence/original-audio-tester.md` | available video + original audio tester verification and phone/log sequence |
| `docs/14-AGENT-SETUP-TASK.md` | the one-time environment setup (done 14 Nov 2026) |
| `docs/15-UI-UX-DESIGN-RESEARCH.md` | UI/UX research and screenshot checklist |
| `docs/16-BROWSER-AUTH-AND-MEDIA-DISCOVERY.md` | browser/login research (not implemented) |
| `docs/dev-setup.md` | exact toolchain versions and re-creation steps |
| `docs/AGENT-AUDIT-2026-11-15.md` | audit of the agent setup, with sources and open decisions |

## Contributing

- GPL-3.0-only; contributions are accepted under the same license.
- No code copied from Snaptube, Seal, YTDLnis or NewPipe — reading them to learn behaviour is fine.
- No ads, analytics, crash-upload SDKs, remote code execution or cleartext HTTP.
- Every feature lands with tests in the same commit; `./gradlew testDebugUnitTest lintDebug` must be green.

## License

GPL-3.0-only. See `LICENSE`, `THIRD_PARTY_NOTICES.md` and `docs/decisions/0001-license-gpl3.md`.
