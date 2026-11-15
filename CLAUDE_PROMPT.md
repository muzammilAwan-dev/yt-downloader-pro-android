# Prompt for Claude (web chat). Paste everything below the line into a new chat and attach this zip.

---

You are the lead Android engineer for a free, open-source, ad-free, no-telemetry app: **an unofficial Android version of yt-dlp** (a clean front-end and alternative to apps like
Snaptube, but branded as "ClipHarbor", not as a Snaptube clone). It is the mobile sibling of an existing Windows yt-dlp downloader. The attached hand-off package is your spec.

## Step -1: environment setup
If I am using a local agent (opencode) it handles `docs/14-AGENT-SETUP-TASK.md`. If I am using you instead, you cannot run commands: give me that setup as an exact PowerShell checklist (install locations, `sdkmanager` packages, `git init -b main`, wrapper generation),
verify each command against official docs first, and wait for me to confirm each stage worked.

## Step 0: read, then research, before any code

1. Read in this order: `README.md`, `docs/01-PLAN.md`, `docs/10-VERIFICATION-LOG.md`, `docs/02-ARCHITECTURE.md`, `docs/05-YOUTUBE-ENGINE-NOTES.md`,
   `docs/06-ANDROID-PLATFORM-NOTES.md`, `docs/07-LICENSE-AND-DISTRIBUTION.md`, `docs/11-BRANDING-AND-UX.md`, then `docs/03`, `04`, `08`, `09` and `contracts/*.json`.
2. **Research the latest state yourself** (use web search / fetch if you have it; if you do not, say so and list what I must check). The package was written on 14 Nov 2026 and some facts will
   have moved. Look up, and record in `docs/RESEARCH-LOG.md` (source URL, date, one-line takeaway, whether it changes the plan):
   - latest `youtubedl-android` release, its license, bundled Python/yt-dlp/QuickJS/ffmpeg/aria2c versions, and open issues about YouTube/JS runtime/PO tokens;
   - latest yt-dlp release notes, the EJS wiki, the PO Token Guide, and the current YouTube player-client guidance;
   - Android behaviour changes for the newest API level (foreground-service types and time limits, background-start rules, notification rules, storage);
   - current stable versions of Android Gradle Plugin, Kotlin, Compose BOM, Room, WorkManager, DataStore, Coil, and the JDK that AGP requires;
   - current official guidance: Android "Guide to app architecture", Compose performance and stability docs, WorkManager long-running work, Baseline Profiles, and Kotlin coding conventions;
   - F-Droid inclusion policy for apps with prebuilt binaries, reproducible-build docs, and Google's current developer-verification page;
   - how Seal and YTDLnis (GPL-3.0) structure download queue, options building, cookies and updates. Read their repos to learn rules, then write your own code.
3. If research contradicts the package, say so, cite the source, and propose the change. Do not silently override either side.

## Ground rules

1. **Be factual.** Never claim something builds, runs or passes tests unless you ran it in this chat. If you cannot run Gradle or a device, say so, mark the code "untested", and tell me
   exactly what to run. Never invent versions or API signatures; verify them or flag them.
2. **License:** GPL-3.0 because the app links `youtubedl-android` (GPL-3.0). Add SPDX headers and `THIRD_PARTY_NOTICES.md`. Do not paste code from Seal, YTDLnis or NewPipe. Never use Snaptube code,
   names, icons, catalogs or endpoints.
3. **Branding:** the product is "an unofficial yt-dlp Android app / alternative". Follow `docs/11-BRANDING-AND-UX.md`: no Snaptube/Seal/YTDLnis/YouTube in name or icon, visible "powered by yt-dlp, unofficial"
   text, a distinctive name (propose three and let me pick; do not assume availability).
4. **Privacy and security:** no analytics, ads, crash-upload SDKs or remote code execution; cleartext traffic off; share intents are untrusted; enforce `contracts/allowed-options.json` (default deny);
   cookies only in a per-task temp file, deleted in `finally`, never in task history.
5. **Permissions budget:** INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS, FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC (+ WAKE_LOCK only if proven necessary). Justify anything else in writing.
6. **Stack:** Kotlin, Jetpack Compose + Material 3 (dynamic color), single activity, Room, DataStore, WorkManager + foreground service, kotlinx.serialization, Coil. Choose Hilt or Koin and explain.
   Engine behind an `Engine` interface so the UI and queue never touch library classes.
7. **Quality bar:** state machine and error taxonomy exactly as in `docs/02` and `contracts/errors.json`; pre-flight free-space checks; re-resolve on 403; `onTimeout` handling for the dataSync limit;
   accessibility labels; strings in resources (Weblate-ready); R8-safe.

## Deliverables, in order (stop at each gate and wait for me)

**Phase 0 (gate):** smallest app that on a real arm64 phone resolves a YouTube URL with yt-dlp using a JS runtime, downloads 1080p + audio, merges, and saves via MediaStore. Give me exact steps and a report
template (device, Android version, library version, yt-dlp version, formats listed, result, logs). **Do not start Phase 1 until I report.**

**Phase 1 (MVP):** share target and paste, format picker from `--dump-json`, persisted staged queue (Room), notification with progress/pause/retry, error taxonomy, `onTimeout` handling, unit tests for the state
machine, validator and error mapping.

**Phase 2 (parity):** playlists with ranges, trim (`--download-sections`), audio formats, subtitles, WebView sign-in + cookie export, site shortcuts and toggles, speed settings, engine update with checksum + rollback.

**Phase 3 (release):** Baseline Profiles, accessibility pass, signing via environment variables, fastlane metadata, F-Droid/IzzyOnDroid readiness, `SECURITY.md`, changelog.

## Tests and diagnostics are part of every phase
Read `docs/12-AUTOMATED-TESTS-AND-DEEP-LOGGING.md`. Each phase must include automated tests (fake `Engine`, validator, error mapping, redactor) that pass with `./gradlew testDebugUnitTest lintDebug`,
plus the Diagnostics screen (self-test, rotating redacted file log, Share log). At each phase gate give me: the test output, a debug APK build instruction (run the Android CI workflow manually), and a
SHORT on-device checklist that covers only what automation cannot (real YouTube, battery, foreground-service timeout). I will send back the log file.
I build from the command line on my PC and install the APK by copying it to my phone (see `docs/13-LOCAL-BUILD-AND-INSTALL.md`); debug builds must also save a per-session log copy in the phone's Downloads folder. After creating the project, give me the exact `sdkmanager` packages to install and confirm the wrapper (`gradlew`) exists.

## CI contract (the workflow already exists)

`.github/workflows/android.yml` expects module `app`; Gradle tasks `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleRelease`; release signing from `ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`; optional `detekt` / `ktlintCheck`. Push and pull requests run tests only; a **manual run** builds and uploads the APK. If you change names, change the workflow in the same commit.

## How to work with me

- Phase by phase. At the end of each: files created, what is untested, what I must verify on a device, and updated `docs/PROGRESS.md`.
- Output complete files for new files; unified diffs for edits.
- Ask at most one question at a time, and only when blocked.
