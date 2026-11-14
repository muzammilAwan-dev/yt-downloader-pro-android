# Progress log

Re-read `AGENTS.md` and this file after any context reset. Every claim below is either backed by pasted command output or marked
**untested**. Rules: small steps (~5 files), then build/test, then commit.

## Environment setup (docs/14-AGENT-SETUP-TASK.md) — in progress

- [x] 1. Inspect installed tools (output pasted below)
- [x] 2. Research official current versions (JDK / AGP / Gradle / SDK platform / build-tools) — see `docs/RESEARCH-LOG.md` R1–R11
- [ ] 3. Print install plan and get one explicit "yes"
- [ ] 4. Install JDK, Android command-line tools, platform-tools, SDK platform, build-tools; accept licenses; set env vars
- [ ] 5. Create repo, `git init -b main`, commit hand-off package
- [ ] 6. Repo hygiene files (LICENSE GPL-3.0, CHANGELOG, SECURITY, THIRD_PARTY_NOTICES, ADR 0001)
- [ ] 7. Gradle project + wrapper, hello-world Compose app
- [ ] 8. Prove toolchain: `tools/check-env.ps1`, `gradlew testDebugUnitTest lintDebug assembleDebug`, APK in `dist/`
- [ ] 9. `docs/dev-setup.md`
- [ ] 10. Conventional commits, tag `setup-complete`, stop and report

### Step 1 output (14 Nov 2026)

```
OS   : Microsoft Windows NT 10.0.26100.0
Arch : AMD64
PSVersion=5.1.26100.9818
ANDROID_HOME=
ANDROID_SDK_ROOT=
JAVA_HOME=
git => C:\Program Files\Git\cmd\git.exe
java => MISSING
javac => MISSING
adb => MISSING
sdkmanager => MISSING
gradle => MISSING
winget => MISSING
python => C:\Users\Malku\AppData\Local\Programs\Python\Python314\python.exe
git: git version 2.55.0.windows.4
C:\Users\Malku\AppData\Local\Android\Sdk missing
C:\Users\Malku\dev\tools missing
C:\Users\Malku\dev\repos missing
```

## Phases (gates: stop and wait for the owner)

- [ ] **Phase 0 (gate)** — spike: YouTube URL resolves with yt-dlp + JS runtime, 1080p video + audio downloaded, merged, saved via
      MediaStore on a real arm64 phone. Deliverable: exact steps + report template. **STOP for owner's device report.**
- [ ] **Phase 1 (MVP)** — share target/paste, format picker, Room staged queue, notification, error taxonomy
      (`contracts/errors.json`), `onTimeout` handling, unit tests.
- [ ] **Phase 2 (parity)** — playlists, trim, audio formats, subtitles, cookie sign-in, site shortcuts/toggles, speed settings,
      engine update with checksum + rollback.
- [ ] **Phase 3 (release)** — Baseline Profiles, accessibility, signing via env vars, fastlane metadata, security policy, changelog.

Every phase must ship with automated tests (fake `Engine`, validator, error mapping, redactor) that pass under
`./gradlew testDebugUnitTest lintDebug`, plus the Diagnostics screen (self-test, rotating redacted log, Share log).

## Step log

| Date | Step | Result |
|---|---|---|
| 14 Nov 2026 | Read `AGENTS.md`, `docs/01-PLAN.md`, `docs/10-VERIFICATION-LOG.md`, `docs/14-AGENT-SETUP-TASK.md` | Done |
| 14 Nov 2026 | Inspected toolchain | JDK/SDK/Gradle/winget missing; Git 2.55.0, Python 3.14 present |
| 14 Nov 2026 | Researched versions (AGP 9.5.1, Gradle 9.6.0, JDK 17.0.21, API 37, build-tools 37.0.0, Compose BOM 2026.09.00) and verified all download URLs/checksums | Written to `docs/RESEARCH-LOG.md` R1–R11 |
| 14 Nov 2026 | Printed install plan, waiting for the owner's single "yes" | **blocked on approval** |
