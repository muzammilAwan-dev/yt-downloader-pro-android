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
- [x] 10. Conventional commits, tag `setup-complete`, stop and report

**Setup gate reached 14 Nov 2026** — waiting for the owner to say "continue" before Phase 0 research/coding.

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

- [x] **Phase 0 (gate)** — spike: YouTube URL resolves with yt-dlp + JS runtime, 1080p video + audio downloaded, merged, saved via
      MediaStore on a real arm64 phone. Deliverable: exact steps + report template. **PASSED 15 Nov 2026** on an Infinix X682B
      (Android 10, API 29, arm64): `SELFTEST RESULT: PASS (7/7 checks)`, merged MP4 2,505,222 B, published to
      `content://media/external/downloads/18406`. Evidence: `docs/evidence/phase0.md`; log excerpt and findings below.
- [ ] **Phase 1 (MVP)** — share target/paste, format picker, Room staged queue, notification, error taxonomy
      (`contracts/errors.json`), `onTimeout` handling, unit tests.
      *Backlog carried in from Phase 0 + the desktop reference (`docs/evidence/phase0.md` §7, `docs/04` sibling section):
      reuse the resolve via `--load-info-json` instead of extracting twice, `-N 4` concurrent fragments, parse
      `[info] … Downloading N format(s)` and `[Merger] Merging formats into "…"`, 3 concurrent tasks, idempotent redaction,
      distinguishable log names, foreground service.*
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
| 15 Nov 2026 | Phase 0 research (engine artifact, yt-dlp/EJS/PO tokens, Android 17, Jetpack versions) | `docs/RESEARCH-LOG.md` R14–R21 |
| 15 Nov 2026 | Renamed app to ClipHarbor, package `io.github.muzammilawan.clipharbor` (owner's choice) | 46 tests green, commit `refactor: rename…` |
| 15 Nov 2026 | Added youtubedl-android 0.18.1 + ffmpeg, arm64-only ABI, legacy native-lib packaging | `assembleDebug` green, APK 67,007,639 B (pre-code) |
| 15 Nov 2026 | Implemented engine spike: diagnostics self-test, 1080p download+merge, MediaStore publish, redacted rotating log, Share log | `testDebugUnitTest lintDebug assembleDebug` green — outputs below |
| 15 Nov 2026 | Built installable APK with `tools/build-debug.ps1` | `dist/app-debug.apk`, SHA-256 below |
| 15 Nov 2026 | Owner ran the spike on an Infinix X682B (Android 10, API 29): self-test 7/7, 1080p-class Shorts resolved, merged MP4 2,505,222 B published via MediaStore | **Phase 0 gate: PASS** (evidence below and in `docs/evidence/phase0.md`) |
| 15 Nov 2026 | Read the owner's desktop repo (`muzammilAwan-dev/yt-downloader-pro`, MIT) as the reference implementation; reconciled `contracts/allowed-options.json` v2 with its real validator and mapped features to phases | `docs/04-OPEN-SOURCE-REFERENCES.md` (sibling-project section), `docs/RESEARCH-LOG.md` R24 |

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

> The outputs in this setup section are kept verbatim from 14 Nov 2026, i.e. **before** the 15 Nov rename: the package id
> `io.github.ytdlpforandroid`, the label "yt-dlp for Android" and the `AppInfoTest` path are historical, not current.

## Phase 0 — engine spike (15 Nov 2026)

Name decided by the owner: **ClipHarbor**, package `io.github.muzammilawan.clipharbor` (docs/11 §2). The app name, label,
package id and theme were renamed from the placeholder in one commit.

### What exists now (app side — nothing installed on a phone yet)

| Area | What | Where |
|---|---|---|
| Engine boundary | `Engine` + models, contract error codes (`contracts/errors.json`), error mapping from yt-dlp output, yt-dlp `-v` parser, format planner (H.264+AAC capped at 1080p) | `app/src/main/java/.../core/` |
| Real engine | only class that touches youtubedl-android: init (Python/QuickJS/ffmpeg unpack), `-v --dump-json` resolve, download with `--merge-output-format mp4` + `--print after_move:filepath`, cancel | `.../engine/YoutubeDlEngine.kt` |
| Diagnostics | 7 checks (engine unpack, JS runtime, ffmpeg, app-private storage, MediaStore insert, network, resolve of the pasted URL) → `SELFTEST RESULT:` line; screen has Run self-test, Download 1080p + merge, Share log, Copy summary, Clear logs, Verbose switch | `.../diagnostics/` |
| Logging | rotating private log `files/logs/app-YYYYMMDD*.log` (≤5 × 2 MB), redaction of cookies/auth/PO tokens/signed-URL queries, debug-only public copy `Download/ClipHarbor-logs/`, FileProvider share; uncaught-exception hook is still Phase 1 | `.../log/` |
| Publish | copy to `Download/ClipHarbor` through MediaStore with `IS_PENDING`; API 24–28 falls back to app-specific external storage (no storage permission allowed) | `.../publish/MediaStorePublisher.kt` |
| Tests | 8 suites / 46 JVM tests with a scriptable fake `Engine` plus fake storage/network probes; no live-YouTube test in CI | `app/src/test/java/...` |

### Command output (this machine, 15 Nov 2026)

```
> .\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain

BUILD SUCCESSFUL in 21s
54 actionable tasks: 24 executed, 30 up-to-date

test suites=8 tests=46 failures=0
lint: 0 errors, 6 warnings
```

The 6 lint warnings, all deliberate or informational:

1. `AndroidGradlePluginVersion` — Gradle 9.9.1 exists; 9.6.0 is the AGP 9.5.1 pairing.
2. `NewerVersionAvailable` — Compose compiler plugin 2.4.21; must match AGP's bundled Kotlin 2.2.10.
3./4. `NewerVersionAvailable` — coroutines 1.11.0; pinned 1.9.0 because that is the version already on the
   classpath (Kotlin 2.2.10 metadata compatibility).
5. `ChromeOsAbiSupport` — APK ships `arm64-v8a` only (deliberate: 67 MB instead of ~200 MB).
6. `UsableSpace` — free-space number is a rough hint in the header, not an allocation decision (Phase 1 adds the
   pre-flight check).

A rerun of `gradlew testDebugUnitTest lintDebug assembleDebug` after the three device-path fixes produced the same
counts (`test suites=8 tests=46 failures=0`, `lint: 0 errors, 6 warnings`), and then:

```
> powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\build-debug.ps1
E1A8C9F5780FD4F5E9A647B2B779521E0E0866FEDB0AFA15809ECEE2F088325A  app-debug.apk
dist/app-debug.apk = 67,693,493 bytes
```

That APK carries `BuildConfig.GIT_SHA = "8cd9487"` (commit `docs: record Phase 0 engine facts…`, which contains every
code change listed in this section) and `VERSION_NAME = "0.1.0-phase0"`; verified in
`app/build/generated/source/buildConfig/debug/…/BuildConfig.java` after the build. The one docs commit that follows only
records these numbers, so the APK bits still correspond to `8cd9487`.

Manifest/packaging audit of that APK (`aapt2 dump xmltree`):

```
package="io.github.muzammilawan.clipharbor"  minSdkVersion=24  targetSdkVersion=37
extractNativeLibs=true      <- required: the engine reads libpython.zip.so/libffmpeg.zip.so by path
usesCleartextTraffic=false
uses-permission: android.permission.INTERNET
uses-permission: io.github.muzammilawan.clipharbor.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
                (signature-level, generated by AndroidX for local broadcasts; not requested by us)
native libs: lib/arm64-v8a/{libqjs.so, libpython.so, libpython.zip.so, libffmpeg.so, libffmpeg.zip.so, libffprobe.so}
```

Byte budget: yt-dlp zipapp 3.2 MB + Python payload 13.6 MB + ffmpeg payload 34 MB + QuickJS 0.9 MB.

### Limits of this build (expected, not bugs)

- **No foreground service/notification yet** (Phase 1): keep ClipHarbor in the foreground while the test downloads.
- **No PO-token provider** (Phase 2 decision, docs/RESEARCH-LOG.md R17). If YouTube withholds HD, the self-test shows
  `withheld=true` or a warning count and the download falls back; that result is exactly what Phase 0 must capture.
- **Engine is 11 months behind upstream**: the Maven artifact bundles yt-dlp **2025.11.12**, upstream stable is
  **2026.10.26**. The report records the actual version; extraction failures caused only by age are expected.
- **Public log copy needs Android 10+**; on 7–9 use Share log (docs/13 §4).
- UI strings are hardcoded English (i18n is Phase 3); only the app label is a resource.
- The engine initialises when the screen opens, so the first launch spends a few seconds unpacking; the log records
  `event=engine_init ms=…`.

### On-device result (owner's run, 15 Nov 2026) — gate **PASS**

Device and build as reported by the app itself: `device=INFINIX MOBILITY LIMITED Infinix X682B android=10 api=29
abi=arm64-v8a locale=en-US freeSpaceMB=11194`, `gitSha=8cd9487` (the audited APK). Logs supplied:
`app-20261115.log` (self-test only, `resolve` skipped because no URL was entered → `PASS (6/7 checks)`) and two snapshots
of the download session. Full detail in `docs/evidence/phase0.md` §5.

```
2026-11-15T05:06:02.780Z I stage=APP event=engine_init ms=8297
check=engine-init status=PASS detail="library=0.18.1 ytdlp=(2025.11.12) python=3.12.11 ejs=? jsRuntime=quickjs-2025-04-26 ffmpeg=7.1.1"
check=resolve status=PASS detail="formats=31 videoOnly=18 audioOnly=8 muxed=1 maxHeight=1920 maxHeightMuxed=640 hdUnavailable=false withheld=false warnings=0"
SELFTEST RESULT: PASS (7/7 checks)
2026-11-15T05:07:27.685Z I task=t153727 stage=MERGING event=ok file=…[j6EUktS4XYo].mp4 bytes=2505222 ms=19622
2026-11-15T05:07:28.007Z I task=t153727 stage=PUBLISHING event=ok uri=content://media/external/downloads/18406 mime=video/mp4
```

Verdict: **GO.** Resolve with QuickJS, 1080p-class formats offered with no token block, separate video (1.73 MiB) +
audio (660.71 KiB) downloaded, merged to a 2,505,222-byte MP4, published through MediaStore on API 29 (Android 10).
Outstanding human check: that the file plays with sound and shows in the gallery — the size arithmetic already proves the
audio stream is inside the container.

Engine versions measured on the device: yt-dlp 2025.11.12, CPython **3.12.11**, QuickJS `quickjs-2025-04-26`, ffmpeg
**7.1.1**. First-run unpack: **8.3 s**.

Findings handed to Phase 1 (`docs/evidence/phase0.md` §7): the URL is extracted twice (~18 s wasted per item), the
selected format id/height is not logged, `ejs=?` and `ytdlp=(2025.11.12)` need fixing in the report, the URL is redacted
twice (`?<redacted><redacted>`), session log names are indistinguishable, and the foreground service is still missing.

### On-device checklist (owner) — kept for regression runs

1. Copy `dist\app-debug.apk` to the phone and install it (allow "install unknown apps" for Files). The package id is
   new, so a previously installed placeholder APK is a separate app — uninstall it if it is in the way.
2. Open **ClipHarbor**. Wait for the status "Engine ready." (first run unpacks the Python/ffmpeg payloads).
3. Paste the URL of a **public video that offers 1080p** into the URL field.
4. Tap **Run self-test**. Expect `SELFTEST RESULT: PASS (7/7 checks)`; any `FAIL` line names the failed check.
5. Tap **Download 1080p + merge**. Expect "Saved: content://media/external/downloads/…".
6. Open Files → Downloads → **ClipHarbor**: the file plays with audio; the player's info shows 1920×1080 (or the
   highest offered; note the actual number).
7. Get the log: it is already in Downloads → **ClipHarbor-logs** → `clipharbor-log-YYYYMMDD-HHMMSS.txt` (debug build),
   or use **Share log**. Copy the whole file to the PC.
8. Fill in the template below and send it with the log. Read it before sharing: the redactor strips cookies, auth
   headers, PO tokens and signed-URL queries, but you are the last check.

### Report template (copy/paste and fill)

```
app=ClipHarbor version=0.1.0-phase0 build=debug
device=<manufacturer model> android=<version> api=<n> abi=arm64-v8a
engine: library=<..> ytdlp=<..> python=<..> ejs=<..> jsRuntime=<..> ffmpeg=<..>
SELFTEST RESULT: PASS|FAIL (n/m checks[, skipped=k])
failed checks=<ids and details, or "none">
resolve: title=<..> duration=<..>s formats=<n> maxHeight=<n> withheld=<true|false>
download: saved=<uri or error code> file=<name> sizeMB=<n> container=<mp4|webm> elapsed=<mm:ss>
player: resolution shown=<..> audio=<yes|no> visible in gallery=<yes|no>
log file=<clipharbor-log-....txt, attached=yes|no>
oddities=<thermal warnings, notification missing, UI stall, anything else>
```
