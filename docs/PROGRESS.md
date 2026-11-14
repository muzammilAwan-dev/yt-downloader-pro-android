# Progress log

Re-read `AGENTS.md` and this file after any context reset. Every claim below is either backed by pasted command output or marked
**untested**. Rules: small steps (~5 files), then build/test, then commit.

## Environment setup (docs/14-AGENT-SETUP-TASK.md) — in progress

- [x] 1. Inspect installed tools (output pasted below)
- [x] 2. Research official current versions (JDK / AGP / Gradle / SDK platform / build-tools) — see `docs/RESEARCH-LOG.md` R1–R11
- [x] 3. Printed install plan; owner approved ("yes, but keep the repo at Documents")
- [x] 4. Installed JDK 17.0.21+9, cmdline-tools 23.0.0 (16111833), platform-tools 37.0.1,
      platforms;android-37.0, build-tools;37.0.0; set `JAVA_HOME`/`ANDROID_HOME`/`ANDROID_SDK_ROOT`/PATH at user scope
- [x] 5. `git init -b main` in this folder (no remote), local identity, commit `chore: import hand-off package` (35 files)
- [x] 6. Repo hygiene files (LICENSE GPL-3.0, CHANGELOG, SECURITY, THIRD_PARTY_NOTICES, ADR 0001)
- [x] 7. Gradle project + wrapper, hello-world Compose app
- [x] 8. Proved toolchain: `tools/check-env.ps1`, `gradlew testDebugUnitTest lintDebug assembleDebug`, APK in `dist/`
- [x] 9. `docs/dev-setup.md`
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
| 14 Nov 2026 | Printed install plan, waiting for the owner's single "yes" | Approved with "keep the repo at Documents" |
| 14 Nov 2026 | Installed toolchain | See "Step 4 output" below |
| 14 Nov 2026 | Generated wrapper + first `assembleDebug` | `BUILD SUCCESSFUL in 6m 51s` — see below |

## Step 4 output (install, 14 Nov 2026)

Checksums verified before extraction:

```
jdk17.zip: SHA256 MATCH 98518ECF…81F84B
cmdline-tools.zip: SHA1 MATCH 57D04F2D…63E9D
gradle-9.6.0-bin.zip: SHA256 MATCH BBAEB2FE…A529A01
```

`java -version`:

```
openjdk version "17.0.21" 2026-10-20
```

Installed SDK packages (folders on disk):

```
platforms:   android-37.0
build-tools: 37.0.0
adb:         Android Debug Bridge version 1.0.41 / Version 37.0.1-15733141
```

Deviation: the SDK repository lists **command-line tools 23.0.0 (build 16111833)** as "latest", newer than the 15859902 the
download page links, so 23.0.0 was installed. In 23.0 `sdkmanager` is deprecated and splits `;`-separated names when
installing; `android sdk install platforms/android-37.0 build-tools/37.0.0` worked. Details in `docs/dev-setup.md`.

## Step 7-8 output (first build, 14 Nov 2026)

`gradlew.bat assembleDebug` tail:

```
> Task :app:compileDebugKotlin
> Task :app:packageDebug
> Task :app:createDebugApkListingFileRedirect
> Task :app:assembleDebug

BUILD SUCCESSFUL in 6m 51s
36 actionable tasks: 36 executed
```

This proves AGP 9.5.1 + built-in Kotlin + Compose BOM 2026.09.00 + compileSdk 37 work together on this machine.

## Step 8 output (gate, 14 Nov 2026)

```
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug

> Task :app:testDebugUnitTest
> Task :app:lintReportDebug
> Task :app:lintDebug

BUILD SUCCESSFUL in 23s
52 actionable tasks: 27 executed, 25 up-to-date
```

Unit tests (from `app/build/test-results/testDebugUnitTest/TEST-io.github.ytdlpforandroid.AppInfoTest.xml`):

```
tests=4 failures=0 errors=0 skipped=0
PASS displayNameUsesNoForbiddenBrand
PASS appStaysExplicitlyUnofficial
PASS displayNameIsNotEmpty
PASS forbiddenBrandCheckIsCaseInsensitiveAndCatchesRealNames
```

Lint (`lintDebug`) warnings: **2**, both deliberate version-availability notices:
`AndroidGradlePluginVersion` (Gradle 9.9.1 available; we pin 9.6.0 for AGP 9.5.1) and `NewerVersionAvailable`
(Compose compiler plugin 2.4.21 available; must match AGP's bundled Kotlin 2.2.10). Errors: 0.

`tools/check-env.ps1`:

```
[ OK ] git            git version 2.55.0.windows.4
[ OK ] java           openjdk version "17.0.21" 2026-10-20
[ OK ] JAVA_HOME      C:\Users\Malku\dev\tools\jdk-17
[ OK ] ANDROID_HOME   C:\Users\Malku\AppData\Local\Android\Sdk
[ OK ] sdkmanager     1.1.16919734 (Android CLI)
[ OK ] adb            Android Debug Bridge version 1.0.41
[ OK ] SDK platform   android-37.0 installed
[ OK ] build-tools    37.0.0 installed
[ OK ] gradle wrapper Gradle 9.6.0

Environment OK
```

`tools/build-debug.ps1` (tests + build + copy to `dist/`):

```
BUILD SUCCESSFUL in 2s
FEB196A0A83B5CD2C7A1A8948F4299FCCD9CD486C014C1EBEC0354365FAA1474  app-debug.apk
```

APK: `dist/app-debug.apk`, 12,090,786 bytes. `aapt2 dump badging`:

```
package: name='io.github.ytdlpforandroid' versionCode='1' versionName='0.0.1'
platformBuildVersionName='17' platformBuildVersionCode='37' compileSdkVersion='37'
minSdkVersion:'24' targetSdkVersion:'37'
application-label:'yt-dlp for Android'
launchable-activity: name='io.github.ytdlpforandroid.MainActivity'
```

Not yet installed on a phone: there was no device attached during setup. That happens with the Phase 0 checklist.
