# Prompt for opencode (or any terminal coding agent, including free DeepSeek-class models)

Paste the block below as your first message in the repo root, after copying this package's files into the repo.
opencode reads `AGENTS.md` from the project root automatically (verified in opencode's docs); it does not auto-follow file references inside it, so this prompt tells the agent to read files explicitly.
Free models have smaller effective context and are less reliable on long tasks: work in **small steps**, keep a progress file, and verify with commands.

---

You are building an **unofficial Android version of yt-dlp** (free, open source, no ads, no telemetry; branded "yt-dlp for Android", inspired in ease-of-use by Snaptube but NOT a clone). Work only inside this repo.

## 00. Environment and repo setup (do this BEFORE everything else)

Read `docs/14-AGENT-SETUP-TASK.md` and do it: detect the OS/shell, report what is installed, research the official install steps and compatible versions, **print your install plan and wait for my single "yes"**, then install JDK,
Android command-line tools, platform-tools, SDK platform and build-tools into the folders described there (user-level, official sources only), create the local repo with `git init -b main` (no remote; I will connect GitHub later),
add the repo hygiene files and the Gradle wrapper, build a hello-world app with `assembleDebug`, write `docs/dev-setup.md`, and commit with Conventional Commits. Paste real command outputs into `docs/PROGRESS.md`. Then stop and report.
Do not start the research/Phase 0 steps below until I say "continue".

## 0. Setup (do this first, in order)

1. Read `AGENTS.md` (rules), then `docs/01-PLAN.md` and `docs/10-VERIFICATION-LOG.md`. Read the other `docs/*.md` only when the current task needs them (names are in `README.md`).
2. Create `docs/PROGRESS.md` with a checklist of the phases below. Update it after every task. If your context gets compacted, re-read `AGENTS.md` and `docs/PROGRESS.md` first.
3. **Research before coding.** Use web search/fetch tools if available. Record each finding in `docs/RESEARCH-LOG.md` (URL, date, takeaway). Research these, newest sources first:
   - `youtubedl-android` latest release, license, bundled Python/yt-dlp/QuickJS/ffmpeg/aria2c, and recent issues about YouTube/JS runtime/PO tokens;
   - yt-dlp latest release notes, EJS wiki, PO Token Guide;
   - Android behaviour changes for the newest API level (foreground-service types and time limits, notifications, storage);
   - latest stable versions of Android Gradle Plugin, Kotlin, Compose BOM, Room, WorkManager, DataStore, Coil, and the required JDK;
   - official Android architecture, Compose, WorkManager and Baseline Profile guidance; Kotlin coding conventions;
   - how Seal and YTDLnis (GPL-3.0) organise queue, options, cookies, updates (read to learn the rules; do not copy code).
   If you have no web access, say so, list what you could not check, and use `docs/` as is, flagged "unverified".
4. If research contradicts `docs/`, write the conflict and your recommendation in `docs/RESEARCH-LOG.md` and ask me.

## 1. Rules (also in AGENTS.md)

- Never claim success without running the command. After each change run the relevant check (`./gradlew testDebugUnitTest`, `./gradlew lintDebug`, or `./gradlew assembleDebug`) and paste the real result. If the sandbox cannot run Gradle, say so and give me the command.
- Never invent library versions or API signatures. Look them up or mark `TODO(verify)`.
- GPL-3.0 project. No code from Seal/YTDLnis/NewPipe/Snaptube. No analytics, ads, remote code, cleartext traffic. Permissions budget in `AGENTS.md`.
- One small task at a time: edit at most ~5 files per step, then build/test, then commit with a clear message.
- Do not touch `.github/workflows/android.yml` except to keep it consistent with real Gradle task/module names.

## 2. Phases (stop at each gate and wait for me)

**Phase 0 (gate):** minimal app proving on a real arm64 phone: resolve a YouTube URL with yt-dlp + JS runtime, download 1080p + audio, merge, save via MediaStore. Give me exact steps and a report template. Stop.
**Phase 1 (MVP):** share target/paste, format picker, Room staged queue, notification, error taxonomy (`contracts/errors.json`), `onTimeout` handling, unit tests.
**Phase 2 (parity):** playlists, trim, audio formats, subtitles, cookie sign-in, site shortcuts/toggles, speed settings, engine update with checksum + rollback.
**Phase 3 (release):** Baseline Profiles, accessibility, signing via env vars, fastlane metadata, security policy, changelog.

## Tests and diagnostics are part of every phase
Read `docs/12-AUTOMATED-TESTS-AND-DEEP-LOGGING.md`. Each phase must include automated tests (fake `Engine`, validator, error mapping, redactor) that pass with `./gradlew testDebugUnitTest lintDebug`,
plus the Diagnostics screen (self-test, rotating redacted file log, Share log). At each phase gate give me: the test output, a debug APK build instruction (run the Android CI workflow manually), and a
SHORT on-device checklist that covers only what automation cannot (real YouTube, battery, foreground-service timeout). I will send back the log file.
I build from the command line on my PC and install the APK by copying it to my phone (see `docs/13-LOCAL-BUILD-AND-INSTALL.md`); debug builds must also save a per-session log copy in the phone's Downloads folder. After creating the project, give me the exact `sdkmanager` packages to install and confirm the wrapper (`gradlew`) exists.

## 3. Branding

Follow `docs/11-BRANDING-AND-UX.md`. Propose three distinctive names, do not assume they are free, and wait for my choice before renaming the package.

Start now with Setup step 1.
