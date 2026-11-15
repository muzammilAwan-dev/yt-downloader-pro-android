# Progress log

**Append-only history. Do not read this file in full** (about 19k tokens): current state and next steps are in `docs/STATE.md`;
after a context reset re-read `AGENTS.md` and `docs/STATE.md` only, and `grep` or `tail` this file for a specific step.
Every claim below is either backed by pasted command output or marked **untested**. New entries: at most 10 lines, with a
3-line check summary instead of full Gradle output (raw evidence for a phase gate goes in `docs/evidence/`).
Rules: small steps (~5 files), then build/test, then commit.

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
      **Started with the owner's approval on 15 Nov 2026.** Phase 0 regression fixes are the first increments.
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

## Phase 1 — incremental implementation (15 Nov 2026)

### Step 1: logging regressions

- Removed pre-redaction from `MainActivity`; both disk sinks still redact independently.
- Made URL redaction idempotent, including already-redacted token markers followed by other query parameters.
  Credentials, fragments, signed query parameters and HttpOnly Netscape cookie values are removed.
- Private logs now use `app-YYYYMMDD-HHmmss[-N].log`, carry a redacted header after every rotation, and are shareable
  before the first event. UTF-8 byte accounting and safe truncation enforce the per-file byte cap.
- Regression tests cover repeated redaction, header rotation, timestamp collisions and multi-byte truncation.

Verified command output (whole working tree, including pending engine-version regression tests):

```
> .\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain --no-daemon

BUILD SUCCESSFUL in 1m 31s
54 actionable tasks: 16 executed, 38 up-to-date

XML reports: suites=8 tests=57 failures=0 errors=0 skipped=0
lint XML: errors=0 warnings=6
```

An earlier invocation was interrupted by the user and is **not** counted as a completed verification run. The 6 lint
warnings remain the Phase 0 informational warnings. Device regression testing of this increment is **not run**;
`dist/app-debug.apk` still contains the previously delivered Phase 0 build.

### Step 2: factual engine-version reporting

- Normalize the library's parenthesized yt-dlp version at the engine boundary and in the verbose parser.
- Parse EJS's actual upstream `Optional libraries: ... yt_dlp_ejs-<version>` spelling, as well as alternate explicit
  version headers. A missing version is reported as `ejs=unknown`, not presumed bundled or missing.
- Reject disabled, unavailable or explicitly unsupported JS-runtime header entries in the runtime self-test check.
- Primary sources and limitations are recorded in `docs/RESEARCH-LOG.md` R25–R27.

```
> .\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain --no-daemon

BUILD SUCCESSFUL in 1m 24s
54 actionable tasks: 16 executed, 38 up-to-date

XML reports: suites=8 tests=59 failures=0 errors=0 skipped=0
lint XML: errors=0 warnings=6
```

The debug APK built locally; **no new device run or `dist/` handoff build** was performed. Phase 1 remains in progress:
resolve reuse, selected-format/output-path evidence, Room queue, foreground service, share target, picker and error UI
are still outstanding. `--load-info-json` behavior has been researched, not yet implemented; upstream may re-extract
on errors, so an unconditional "exactly one extraction" claim would be incorrect.

### UI/UX side quest (research only)

The owner explicitly requested `general` to research design skills while implementation continues. Findings and the
screenshot checklist are in `docs/15-UI-UX-DESIGN-RESEARCH.md`, research row R28. Official Material 3 / Compose accessibility
guidance comes first; adaptive and community skills have documented limitations. Nothing was installed or redesigned.
Snaptube screenshots will be used for interaction inspiration only, with an original ClipHarbor identity.

Code increments committed: `30f6666` (logging), `2e86113` (engine-version reporting). Documentation-only follow-up does
not change the last tested application code; tests/lint/APK output is recorded above.

### Browser/login and screenshot follow-up (research only)

- Visually reviewed all nine owner PNGs; image mapping and original-design principles are in
  `docs/16-BROWSER-AUTH-AND-MEDIA-DISCOVERY.md`. No images, feed/account details, competitor code or assets committed.
- Re-read existing static REA notes; no new binary/dynamic analysis performed. Exact extraction/cookie/PO-token runtime
  remains unproved; owner-reported website login and the red bot-error sign-in recommendation are recorded separately.
- Verified upstream generic/HLS/DASH extraction, cookie/account risks, OAuth vs direct website-login scope, Custom Tabs
  isolation, cookie-header export limitations, WebView interception, MSE/DRM and bridge risks (research R29–R33).
- Reconsidered the blanket browser rejection as optional post-MVP feasibility research; **no browser/login added to Phase 1**,
  no security/permissions/remote-code rule relaxed. Corrected the architecture's assumed WebView cookie-export path.
- `general` completed the owner-requested direct website-login follow-up: broader Google embedded-sign-in guidance,
  feature-gated AndroidX cookie attributes and explicit Custom Tabs isolation rechecked on primary sources (R34).
  A validated WebView session-to-task adapter is plausible, not a guaranteed supported Google sign-in path.
- `explore` completed the owner-requested retained REA trace. Browser/search, auth-aware outcomes, URL/header/expiry models
  and typed jobs support the high-level architecture; the exact session handoff needs the original export/path, not found
  within workspace/approved temp/targeted Downloads scope. Read-only REA status reports no open target or retained evidence.
  No new device/browser-login test or APK handoff performed. Research proposals are **unimplemented/untested**.

Documentation increment validation (application sources unchanged):

```
> .\gradlew.bat testDebugUnitTest lintDebug --console=plain --no-daemon

BUILD SUCCESSFUL in 18s
36 actionable tasks: 1 executed, 35 up-to-date
```

Gradle reused the existing unit-test/lint results; this is **not a fresh execution of 59 tests** or browser validation.
No `assembleDebug` performed for this research-only increment and no new APK delivered.

### Owner steering and repository connection

- The owner supplied the original REA directory, `C:\Users\Malku\Documents\REA`. `explore` is now checking its original
  reports; the previous missing-export result only covered the earlier search scope. Exact session dataflow still pending.
- The second screenshot archive's supplied Downloads path is currently absent; direct case-insensitive screenshot-ZIP
  lookup also returned no files. The second batch has **not** been visually reviewed.
- The owner explicitly authorized connecting their new GitHub repository. `general` added `origin` at
  `https://github.com/muzammilAwan-dev/yt-downloader-pro-android.git`; `git remote get-url origin` matched, and noninteractive
  `git ls-remote origin` returned exit 0 with no refs (empty remote). Local branch `main`; initially nothing pushed, no remote
  history merged and no credentials/global configuration changed. The owner subsequently authorized pushing code so far;
  the push has not yet been run. This supersedes setup's no-remote instruction for the authorized connection/push.
- Reconciled plan/engine notes with website-session research and corrected T6's ambiguous header-persistence lesson.

```
> .\gradlew.bat testDebugUnitTest lintDebug --console=plain --no-daemon

BUILD SUCCESSFUL in 23s
36 actionable tasks: 1 executed, 35 up-to-date
```

Again, test/lint tasks reused their existing reports (59 tests, 0 failures/errors/skipped; lint 0 errors/6 warnings).
This verifies the unchanged application tree remains buildable, **not** a browser feature or fresh device test.

First authorized push completed: non-force `git push -u origin main` succeeded, and `git ls-remote origin refs/heads/main`
matched `f6206241cee8e387675ebf66b4489ba5cb580aac`. Upstream is `origin/main`; working tree was clean and synchronized.
Only `main` was pushed (no tags/other branches). GitHub Actions results were not checked. Original REA report follow-up
is still running; no new Android code, APK or browser-login validation accompanied the push.

### Original REA follow-up completed (15 Nov 2026)

- `explore` traced the original reports and focused source under the owner's `Documents/REA` path; parent rechecked core
  evidence. Doc 16 §7 and research R36 preserve source-line references without copying proprietary code/assets/endpoints.
- **Confirmed statically:** actual browser website-session handling; extractor reads shared WebView cookies and uses
  eligible authentication; selected-format task submission and header-capable transfer; branch-specific expired-login
  continuations; separate real token-generating WebViews. This replaces the initial missing-source/token-inference gaps.
- **Corrections/limits:** picker login continuation resumes submission, not a verified extraction refresh; ordinary login
  recommendations are not proven automatic retry. Session markers/checks are heuristic. No complete redirect audit,
  account-bound token invalidation, reliable renewal predicates or runtime bot immunity established.
- Original adaptation: controlled task-credential boundary, session-generation-aware resolve cache, durable user intent,
  scoped transfer, bounded/cancellable login recovery, independent token-provider lifecycle. No implementation added.
- Second screenshot batch still unavailable at its supplied path. No new binary analysis, live login test or APK handoff.

```
> .\gradlew.bat testDebugUnitTest lintDebug --console=plain --no-daemon

BUILD SUCCESSFUL in 18s
36 actionable tasks: 1 executed, 35 up-to-date
```

Application sources are unchanged; test/lint reports were reused. This is not new browser/login feature verification.

### Reattached second screenshot batch reviewed (15 Nov 2026)

- Previous research commit `3ff7ff6` was pushed non-force; verified remote `main` at
  `3ff7ff6977f6e35aeaf4026f6e69d89ef009bff1`. At the start of this increment the working tree was clean and tracked
  `origin/main`. GitHub Actions results have not been checked.
- Owner reattached `Downloads/screenshots 2.zip`; the previously unavailable archive is now accessible. Safely extracted
  five PNGs under the approved temporary directory and visually reviewed all five (720×1640 each). Hash and original
  filename mapping are in doc 16 §1, research R37. All fourteen images across both batches have now been reviewed.
- New visual evidence: player/download controls, comments panel, loading browser shell, Sites list and signed-in
  website-style account page inside app chrome. Supports the owner's website-session point; no credential-entry flow,
  bot-error prompt, post-login transfer or PO-token generation shown. Static REA evidence remains separately qualified.
- No images, account names, comments, personal history/playlist details or competitor code/assets committed. Original
  design notes distinguish app/site navigation, origin visibility, loading/error states and validated source shortcuts.
- Documentation only: no Android code, dependency/permission changes, browser implementation, agent-operated login,
  new device test or APK handoff. Phase 1 remains incomplete; next engineering increment is task-private resolve reuse
  and selected-format/output-path evidence with tests. The outstanding owner playback/gallery check remains open.

```
> .\gradlew.bat testDebugUnitTest lintDebug --console=plain --no-daemon

BUILD SUCCESSFUL in 15s
36 actionable tasks: 1 executed, 35 up-to-date
```

Unit-test and lint analysis/report tasks were UP-TO-DATE; this reused existing results, not a fresh execution of tests or
browser authentication verification. No `assembleDebug` or new APK performed for this documentation-only increment.

### Step 3a: explicit download-evidence foundation

- Added a platform-free parser for app-owned selected-format/final-path markers and standard yt-dlp selected-format lines.
  Final paths must be absolute, canonical, inside the current staging directory, nonempty regular media files.
- No timestamp/directory-scanning fallback: unrelated files, merger-only evidence, partial files and metadata are rejected.
  Actual height remains unknown when unreported; a later selection clears the earlier height.
- Added outcome fields and offline regression fixtures. Parser is not yet wired into the Android engine; that follows
  after pinned upstream API/print/load-info verification. No dependency, permission or browser changes.
- Verification pending for this foundation increment; no new device run or APK handoff.

Initial verification: `testDebugUnitTest` executed and `assembleDebug` completed, but the overall command **FAILED in
1m 58s**: lint reported 3 `NewApi` errors for `File.toPath`/`Path.startsWith` on minSdk 24, plus the existing 6 warnings.
Replaced NIO path checks with canonical-parent walking; no minSdk bump, desugaring dependency or lint suppression.

```
> .\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain --no-daemon

BUILD SUCCESSFUL in 1m 23s
54 actionable tasks: 15 executed, 39 up-to-date

XML reports: suites=9 tests=69 failures=0 errors=0 skipped=1
lint XML: errors=0 warnings=6
```

Unit tests executed. One symlink-escape test is skipped because this Windows host cannot create a symlink without extra
privileges; canonical sibling/traversal confinement tests ran. No privilege escalation requested. Debug APK built locally;
`dist/app-debug.apk` remains the previous Phase 0 handoff, and real engine marker output is not yet device-tested.

### Step 3b: private, single-use replay-input foundation

- Added a bounded in-memory snapshot with exact source/task binding, five-minute monotonic age limit and single use.
  Raw JSON is not exposed in data-class strings/history. A temporary file exists only while the blocking worker uses it;
  success, failure, cancellation and interruption release it. Startup cleanup only removes owned regular snapshots.
- Replay field policy removes old merge/download selections, derived paths and top-level transport/format fields;
  the full format catalog and its transport data are retained. Missing catalogs require a new resolve.
- Added offline lifecycle, scope, stale-input, failure and sanitization-policy fixtures; engine/outcome types can carry
  the opaque input. Android wiring and interruption-aware execution are still the next increment, not implemented yet.
- Validation pending; no device login/download test, dependency/permission changes or APK handoff.

```
> .\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain --no-daemon

BUILD SUCCESSFUL in 1m 58s
54 actionable tasks: 16 executed, 38 up-to-date

XML reports: suites=10 tests=80 failures=0 errors=0 skipped=1
lint XML: errors=0 warnings=6
```

Tests executed, including 11 new snapshot/policy cases. The existing host-restricted symlink case remains skipped.
This verifies the foundation, not Android replay integration, live authentication or process-tree termination.

### Step 3c: offline-testable blocking download orchestration

- Added an original process-runner seam and interruptible coroutine execution; caller task IDs never reach the library's
  shell-based process helper. Cancellation targets the registered job and uses generated internal keys for best-effort
  cleanup. This is not a guarantee of complete descendant-process termination in library 0.18.1.
- Download orchestration supplies a fresh selector, `-N 4`, enabled replay cleaning, two explicit print markers and an
  empty URL list for task-private replay. It verifies final-file evidence and captures reported IDs/height.
- Retains a bounded redacted 200-line tail even when the library throws without an exit code. Callback exceptions cannot
  kill its reader thread or report task success; unrelated lines do not reuse stale progress percentages.
- Offline runner fixtures cover replay/cleanup, failure/redaction, callback errors, stale progress, HTTPS-only inputs and
  cancellation. Android engine/MainActivity wiring follows next. Verification pending; no new device test or APK handoff.

```
> .\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain --no-daemon

BUILD SUCCESSFUL in 1m 49s
54 actionable tasks: 18 executed, 36 up-to-date

XML reports: suites=11 tests=88 failures=0 errors=0 skipped=1
lint XML: errors=0 warnings=6
```

Tests executed, including eight new orchestration cases and real-thread interruption against a synthetic blocking runner.
The host-restricted symlink case remains skipped. This does not execute native yt-dlp or prove Android child cleanup.

### Step 3d: Android replay/evidence wiring

- Diagnostics now resolves for its task and passes the opaque input to download; self-test-only resolves do not create
  replay inputs. Android extraction clears derived selection/output/transport fields while retaining the full catalog.
- Wired the library behind the tested runner: resolve keeps stderr separate, download deliberately merges stderr for
  reliable failure tails. Both use ignore-config, HTTPS-only source validation, interruptible jobs and internal UUID keys.
- Replay files live under app-private no-backup storage and are cleaned once per process before workers start. Activity
  recreation does not delete active inputs. Handles are closed on all diagnostic/adapter terminal paths.
- Removed timestamp-based output guessing. Diagnostics logs actual selected IDs/height, requested height, resolve-reuse
  state and fragment concurrency. Low reported height is a warning, not inferred proof of withholding/bot immunity.
- Verification pending; native replay/print behavior remains untested on the phone. No new permission/dependency/browser
  feature, foreground service, picker, queue or `dist/` APK handoff yet. Phase 1 remains in progress.

```
> .\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain --no-daemon

BUILD SUCCESSFUL in 2m 17s
54 actionable tasks: 16 executed, 38 up-to-date

XML reports: suites=11 tests=89 failures=0 errors=0 skipped=1
lint XML: errors=0 warnings=6
```

Tests executed; the new compatibility case confirms fake/alternative engines may resolve a task without supplying replay
input. The one Windows symlink case remains skipped. Android code compiles; no native yt-dlp replay/device test performed.

### Step 3e: final replay/cancellation safety pass

- Expanded clearing of stale top-level transport fields (including request bodies, HLS key/query state and DRM flags),
  retaining the selected catalog's own fields. Rechecked the pinned format/option source; research R39.
- Failed temporary-input deletion is surfaced without replacing the primary cancellation/error. Oversized rejected input
  bytes are cleared. Added a cleanup-failure regression and a runner that ignores interruption but must not return success
  after task cancellation. Real descendant-process cleanup remains best-effort and unverified.
- Reconciled internal options/HTTPS-only source policy in contract v3; disabled replay cleaning is explicitly blocked for
  untrusted input. General intent-option parsing is still not shipped; these flags are set only by trusted engine code.
- Final validation pending. This increment finishes the recommended engine groundwork, not the Phase 1 MVP. Next: validated
  paste/share, selectable formats, durable Room queue, foreground notification/service and error/retry UI.

```
> .\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain --no-daemon

BUILD SUCCESSFUL in 1m 48s
54 actionable tasks: 16 executed, 38 up-to-date

XML reports: suites=11 tests=91 failures=0 errors=0 skipped=1
lint XML: errors=0 warnings=6

Contract JSON valid; replay flags internal-only, disabled cleaning blocked
```

Tests executed: 90 passed, one Windows symlink-capability case skipped. JSON parse/internal-flag checks and
`git diff --check` succeeded. No native/device replay, speed/battery measurement or complete process-tree cleanup proof.

Implementation commits before this final pass: `9b30c66` evidence, `633cb43` private inputs, `e892d21` orchestration,
`162b273` Android wiring. A local debug APK built, but no new install/handoff/tag/manual CI build was performed;
`dist/app-debug.apk` still refers to Phase 0. Do not install that old file expecting these fixes.

Future device regression checklist, when a new candidate APK is handed off:
1. Resolve/download a small public HTTPS sample; confirm `resolveReuse=true` and `reusedResolve=true` in the log. These
   mean a replay input was supplied, not a promise that upstream never re-extracted after an error.
2. Confirm `selected_formats ids=... actualHeight=...` agrees with the player's media information (height may be unknown
   for audio). Verify the published file plays with sound and is visible in Files/gallery.
3. Exercise cancel/retry in the future queue UI and check logs/task-private input cleanup without sharing credentials.
    Diagnostics still has no foreground service: keep the app foregrounded until the queue/service increment lands.

### Latest phone log review and refreshed tester handoff (15 Nov 2026)

- Reviewed the owner's three attached `app-20261115*.log` files. They are progressively longer snapshots of one session,
  not three independent runs. Every header reports `gitSha=8cd9487`: the previous Phase 0 handoff. This does **not** validate
  the replay, selected-format reporting, cleanup or cancellation changes through `5889eee`.
- The old-build task resolved in 17,185 ms, reported 33 catalog formats and catalog maximum height 2560, then downloaded
  video/audio streams, produced a 1,543,397-byte MP4 and logged successful MediaStore publishing. Actual selected IDs/height
  are unreported, so neither 1080p nor 2560p output is established by this log.
- Approximately 18.367 s elapsed between download start and first progress, with 20,598 ms reported for download/merge.
  This is consistent with the old repeated-extraction path, not proof of where every millisecond was spent or a speed
  comparison against the new adapter.
- Self-test recorded seven `status=PASS` checks and `failures=0`. Later runtime evidence identifies Python 3.12.11,
  QuickJS `quickjs-2025-04-26`, ffmpeg 7.1.1 and yt-dlp `(2025.11.12)`; EJS remains unknown. Startup placeholders precede
  initialization and do not establish runtime failure. Old double-redaction and incomplete summary punctuation are not
  evidence that the current logging fixes failed. No source URLs, media titles or account details copied into this record.
- Playback with sound and gallery visibility still need the owner's confirmation. Current native replay/selection/final-file
  checks remain pending; Phase 1 is still in progress.

The direct PowerShell helper invocation was blocked by this PC's existing script policy. Used the already documented
process-scoped invocation instead; no persistent execution-policy or environment changes:

```
> powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\build-debug.ps1

testDebugUnitTest lintDebug:
BUILD SUCCESSFUL in 1m 48s
36 actionable tasks: 15 executed, 21 up-to-date

assembleDebug:
BUILD SUCCESSFUL in 8s
38 actionable tasks: 3 executed, 35 up-to-date

XML reports: suites=11 tests=91 failures=0 errors=0 skipped=1
lint XML: errors=0 warnings=6
APK bytes=67693493
APK SHA256=95528855198DBE2F0632218B8C82D14749570840D15614D907C6FF4E692B3A4B
APK DEX contains expected gitSha=5889eee
Built and dist APK hashes match
```

Tests executed: 90 passed and the existing Windows symlink-capability case skipped. Generated `BuildConfig.java` confirms
`GIT_SHA="5889eee"`; the packaged DEX contains that stamp. Built from clean source at `5889eee`, with no implementation
changes during this handoff. `dist/app-debug.apk` is now refreshed and contains the engine fixes (supersedes the earlier
old-artifact warnings). Version name/phase header remain `0.1.0-phase0`/0 until the Phase 1 UI increment; identify this
candidate by **gitSha**, not version name. The following documentation commit only records the evidence.

Owner handoff: copy the newly refreshed `dist/app-debug.apk` to the phone and install over the old local debug build.
Keep the app foregrounded, download a small public HTTPS sample, run self-test and share the newest session log. Expect
`gitSha=5889eee`, `resolveReuse=true`, `selected_formats ids=... actualHeight=... reusedResolve=true` and successful publish;
report playback/audio/gallery results. Do not call native regression passed until the new phone evidence is received.

### Current-build phone evidence and owner rename (15 Nov 2026)

- Reviewed the three new `app-20261115-091521*.log` snapshots of one session, stamped `5889eee` on the same Android 10
  arm64 phone. Successful task-private replay is evidenced by `--load-info-json`, `resolveReuse=true` and
  `reusedResolve=true`; no extractor re-fetch lines are visible in this successful download's captured output.
- Actual selection marker and summary agree: `299+140`, height 1080 (video plus audio). The explicit after-move marker
  preceded verified output and a 17,201,445-byte MP4 was published successfully. This validates the successful native
  replay/selection/final-file path for this sample, not cancellation, error recovery or every supported site.
- Resolve took 19,943 ms; first download progress arrived about 5.632 s after download start; download/merge reported
  17,798 ms. The earlier sample was different, so these are observations, not a controlled speed comparison.
- Self-test passed 7/7 with zero failures; runtime reports yt-dlp 2025.11.12, Python 3.12.11, EJS **0.3.1**, QuickJS
  `quickjs-2025-04-26` and ffmpeg 7.1.1. Signed media URL queries remain redacted. The engine's age warning is real;
  no update, warning suppression or remote-component enablement is included in this branding change.
- Playback with sound/gallery visibility still needs the owner. The summary's missing closing parenthesis remains a
  cosmetic issue, not a failed check. Media titles/source URLs/account details are not retained in this record.
- Owner superseded ClipHarbor with **YTDLP (You Tube Downloader Pro)** and supplied their desktop `icon.ico` for reuse.
  Keep package id `io.github.muzammilawan.clipharbor` so the app updates in place. Unofficial attribution stays visible;
  no name/trademark availability or upstream endorsement claimed. Source icon conversion follows as a small increment.

Branding step 1: updated launcher label, app metadata, diagnostics heading and share/copy strings. New output-name
constants are tested; MediaStore wiring follows immediately. Existing private keys/process markers/package/theme names
remain stable implementation identifiers. Verification pending; no new APK handoff yet.

```
> .\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain --no-daemon

BUILD SUCCESSFUL in 1m 19s
54 actionable tasks: 24 executed, 1 from cache, 29 up-to-date
XML reports: suites=11 tests=92 failures=0 errors=0 skipped=1
lint XML: errors=0 warnings=6
```

Tests executed: 91 passed; the existing Windows symlink case skipped. Rename compiles, but launcher/phone appearance
has not been tested. The `dist/` handoff is still `5889eee` until all branding increments are packaged.

Branding step 2: publisher, storage probe and public log mirror now use the central YTDLP output-name constants.
New files go to `Download/YTDLP` and debug session logs to `Download/YTDLP-logs`; old ClipHarbor files are not moved,
deleted or included in new-folder log pruning. Private replay filename prefixes stay unchanged for orphan cleanup
compatibility. Added two JVM contract checks for the wired constants; real Android MediaStore rename is untested.
Verification pending; no new permission, dependency or storage migration.

```
> .\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain --no-daemon

BUILD SUCCESSFUL in 1m 43s
54 actionable tasks: 16 executed, 38 up-to-date
XML reports: suites=12 tests=94 failures=0 errors=0 skipped=1
lint XML: errors=0 warnings=6
```

Tests executed: 93 passed, one existing Windows symlink case skipped. Storage branding constants are tested and the
Android wiring compiles; no new phone MediaStore run performed. The owner requested this build continue in the background;
results above were recorded only after its completion notification.

Branding step 3: reuse the owner's supplied ICO's largest PNG frame losslessly (256x256, unchanged image bytes),
replacing the placeholder download arrow. Legacy API 24-25 bitmap and API 26+ adaptive wrapper use the same artwork;
the Android launcher determines the outer mask. The primary red symbol fits the inner 66dp safe zone at 108dp layer scale.
Packaged the desktop MIT copyright/permission notice and added four offline label/artwork/layer/license resource checks.
No monochrome themed layer added; OEM launcher appearance remains untested. Verification pending.

```
> .\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain --no-daemon

BUILD SUCCESSFUL in 2m 4s
54 actionable tasks: 26 executed, 28 up-to-date
XML reports: suites=13 tests=98 failures=0 errors=0 skipped=1
lint XML: errors=0 warnings=7
APK contains exact desktop MIT notice
Owner MIT copyright and full license retained in packaged asset
```

Tests executed: 97 passed, one existing Windows symlink case skipped. New seventh lint warning is
`MonochromeLauncherIcon`: normal adaptive icon works but no distinct themed monochrome layer is provided yet. No
warning suppression, dependency upgrade or claim of final launcher appearance. Exact asset/license resource checks ran;
the full MIT text was compared against the local owner LICENSE and against the built APK's packaged asset.

Branding documentation: updated README, identity/UX guide, local handoff/log-folder guide and AGENTS identity note.
Preserved historical ClipHarbor evidence and other-app naming restrictions; owner's current YTDLP decision supersedes
only the prior display-name/no-"yt" preference. These documentation changes were present during the successful build
above and introduce no executable code. A clean-source stamped `dist/` handoff is the final step.

### YTDLP tester APK handoff (15 Nov 2026)

Built from clean source at `69f343f` with the existing helper in the background. Recorded results after completion:

```
> powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\build-debug.ps1

testDebugUnitTest lintDebug:
BUILD SUCCESSFUL in 42s
36 actionable tasks: 15 executed, 21 up-to-date

assembleDebug:
BUILD SUCCESSFUL in 4s
38 actionable tasks: 3 executed, 35 up-to-date

XML reports: suites=13 tests=98 failures=0 errors=0 skipped=1
lint XML: errors=0 warnings=7
APK bytes=67694988
APK SHA256=38A612D6D49C8043BA34486EB37A0CE57204912CEC57AEB3C5008EDD0362E810
APK DEX contains expected gitSha=69f343f
Packaged icon MIT notice matches source
Built and dist APK hashes match

aapt2 dump badging:
package: name='io.github.muzammilawan.clipharbor' versionCode='1' versionName='0.1.0-phase0'
application-label:'YTDLP'
application: label='YTDLP' icon='res/drawable-anydpi-v26/ic_launcher.xml'
native-code: 'arm64-v8a'
```

Tests executed: 97 passed, one Windows symlink-capability case skipped. Generated BuildConfig and packaged DEX agree on
`gitSha=69f343f`; packaged label/adaptive icon confirmed by SDK aapt2. `dist/app-debug.apk` is now the renamed tester
candidate, superseding the previous `5889eee` handoff. The full desktop MIT notice was checked inside the APK. Implementation
and branding docs were pushed non-force and remote HEAD verified at `69f343f1e0b492644b85e2a583bb89556fefee7c`; the following
documentation-only commit records this handoff without changing APK code.

Install this refreshed APK over the old local debug build. Check YTDLP launcher name/icon, new downloads/log folders,
and playback/audio/gallery results; no automatic device install was attempted. Engine success is already evidenced on
`5889eee`, but native branding/MediaStore folder appearance and cancellation remain untested. Phase 1 picker, validated
share/paste, durable queue and foreground notification/service remain unfinished.

### Launcher crop correction and current phone logs (15 Nov 2026)

- New owner `app-20261115-094415*.log` snapshots identify `69f343f` and `app=YTDLP`. Self-test has six passes, zero failures
  and a skipped resolve because no URL was entered; this is not 7/7 or a missing-runtime failure. The subsequent download
  reports private replay, actual `299+140`/1080 selection, explicit final output and successful 2,706,320-byte MP4 publish.
  Resolve took 21,132 ms; download/merge 7,349 ms. Runtime versions match the earlier run, including EJS 0.3.1. No source
  URLs/media titles/account details retained. These logs do not expose the MediaStore folder name or prove playback.
- Owner launcher screenshot shows oversized/cropped desktop artwork. Root cause: full bitmap filled AdaptiveIconDrawable's
  expanded layers, 1.5x the visible viewport. Earlier source-pixel safe-zone tests passed but did not test visible scale.
- Corrected only the adaptive wrapper: 16.67% inset per edge offsets that expansion while retaining the exact original
  PNG and legacy icon. New regression models bounds at six launcher sizes and rejects the old missing-padding wrapper.
  OEM mask still controls the outside shape; a circle cannot preserve every corner of a desktop rounded-square tile.
  Screenshot/rendering on the phone is pending, not claimed fixed on-device.
- UI/UX timing clarified: **Phase 1**, which is already authorized/in progress, delivers the first real paste/share,
  format-picker, queue/progress/error UI. Diagnostics is temporary. Phase 3 is the final visual/accessibility/release
  polish, not a reason to defer usable UI. Next implementation work after this small regression is Phase 1 entry/picker;
  durable queue/foreground notifications follow. No guaranteed calendar delivery date or finished-MVP claim.
- Verification pending; no engine update, new permission/dependency, source artwork redraw or additional UI scope.

Previewed before/after approximate rounded-square, circle and squircle masks generated from the actual resource and
XML inset in the approved local temp folder. Padded variants preserve the desktop symbol's relative size; unpadded
variants visibly match the oversized tendency in the owner's screenshot. These are labelled simulations, not Android
screenshots or proof of the owner's OEM launcher result. No preview/account/phone screenshot asset committed.

```
> .\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain --no-daemon

BUILD SUCCESSFUL in 1m 18s
54 actionable tasks: 19 executed, 6 from cache, 29 up-to-date
XML reports: suites=13 tests=99 failures=0 errors=0 skipped=1
lint XML: errors=0 warnings=7
```

Unit tests executed: 98 passed and the existing Windows symlink-capability case skipped. New adaptive-padding
regression passed at six launcher sizes; original image hash/label/license checks remain green. Lint retains the known
missing monochrome layer warning and six prior warnings. Debug APK built locally, but `dist/` still contains `69f343f`
until the clean-source handoff helper runs. Native launcher correction has not yet been confirmed by the owner.

Icon-padding tester handoff built from clean `b83d33c`; background completion received and verified:

```
> powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\build-debug.ps1

testDebugUnitTest lintDebug:
BUILD SUCCESSFUL in 1m 37s
36 actionable tasks: 15 executed, 21 up-to-date

assembleDebug:
BUILD SUCCESSFUL in 16s
38 actionable tasks: 3 executed, 35 up-to-date

XML reports: suites=13 tests=99 failures=0 errors=0 skipped=1
lint XML: errors=0 warnings=7
APK bytes=67694988
APK SHA256=0B5CA680D540484AFC978E8E09124A66A00B06B8F767F8F11BF3A69E9CF85DBC
APK DEX contains expected gitSha=b83d33c
Built and dist APK hashes match
```

Tests executed: 98 passed/one Windows symlink-capability case skipped. Generated BuildConfig matches the packaged stamp;
SDK `aapt2 dump xmltree` confirms the packaged adaptive foreground includes an `inset` with normalized fraction 0.1667,
then the original bitmap. `dist/app-debug.apk` is refreshed and supersedes the cropped `69f343f` candidate. Source fix
was pushed non-force; remote SHA verified as `b83d33c7840d9a09e3cbc70479e9d8a10655f9c8`. The next documentation-only commit
records the handoff without changing the APK.

Owner: install over the current local debug build and send a launcher screenshot to validate this OEM's result. No need
to repeat the video/self-test solely for this icon-wrapper change. Native appearance remains pending. First real Phase 1
entry/picker UI is the next feature increment; no new gate requiring a repeated download test was introduced.

### Phase 1 entry/picker increment (15 Nov 2026)

Owner's latest two screenshots confirm corrected launcher proportions on the Infinix and running `b83d33c`.
This supersedes the pending icon-only visual check above. No personal screen details retained; playback with sound
and Files/gallery visibility remain unconfirmed. The displayed screen is still the temporary Diagnostics spike.

Step 1: added a bounded platform-free HTTPS paste/share validator. Shared titles are accepted only alongside exactly
one web link; options/other extras never become commands. Input's object representation hides the URL. Added reported
video+audio quality ceilings and conservative default selection; this increment is not an exact-itag/audio-only picker.
Removed the engine planner's final unbounded `b` fallback so a chosen height cannot silently fall back above its ceiling.
Offline regressions added; verification pending. No dependency/permission/engine upgrade or browser/login implementation.

First check failed: `106 tests completed, 1 failed, 1 skipped; BUILD FAILED in 1m 31s`. The pre-existing selector
test still expected the intentionally removed unbounded `/b` branch. Updated that assertion to require the bounded
ending; the first background compiler had already read its old version. Full tests/lint/debug build rerun pending.

Rerun executed:

```
> .\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain --no-daemon
BUILD SUCCESSFUL in 1m 8s
54 actionable tasks: 8 executed, 46 up-to-date
XML reports: suites=14 tests=106 failures=0 errors=0 skipped=1
lint XML: errors=0 warnings=7
```

105 passed/one existing Windows symlink case skipped. Entry/picker foundation and bounded fallback regressions green.
No new UI wired yet and `dist/app-debug.apk` remains the icon-only `b83d33c` build; not a Phase 1 tester handoff.

Step 2: implemented an original offline-testable one-task home controller with explicit review/resolve/choose/download/
publish states. Private replay remains bound to one task and closes on edit, dismissal, failure, cancellation and activity
close. Expired chooser refreshes formats and requires confirmation again. New shares during work wait separately rather
than replacing active input; duplicate taps are guarded. Completion reports actual selected IDs/height, not catalogue
maxima. Publishing is a commit boundary with no user Cancel action. Native cleanup is still best-effort; failed/canceled
staging files are not deleted while descendant ownership is uncertain. Fake-engine regressions added; verification pending.
This controller is activity-lifetime, not a durable queue or foreground service. UI wiring is the next small increment.

```
> .\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain --no-daemon
BUILD SUCCESSFUL in 1m 12s
54 actionable tasks: 18 executed, 36 up-to-date
XML reports: suites=15 tests=116 failures=0 errors=0 skipped=1
lint XML: errors=0 warnings=7
```

115 passed/one existing Windows symlink case skipped. Ten new fake-engine controller cases passed, including explicit
confirmation, expiration, cancellation/no publish, deferred shares, duplicate taps and actual-height reporting.
Still no new UI in `dist/`; no native/controller device run claimed.

Step 3: wired an original Material 3 home screen into the single activity. Owner artwork/red-accent light/dark themes,
bounded-width scrolling/safe/keyboard insets, review/paste action, reported video+audio quality chooser, stream progress,
honest errors, cancel-before-save and content-URI saved-file opening are implemented. Diagnostics is separate and retains
self-test/share/copy/clear logs. Self-test and download controls are mutually gated. Removed the temporary direct-1080
diagnostics path rather than keeping two download owners. `ACTION_SEND` text/plain routes to the existing singleTask
activity, validates payload and only prefills; all other extras/actions/types are ignored. No new permission/dependency.
API 24–28 location is explicitly app-specific; saved-file opening is currently offered for MediaStore content URIs only.

At the owner's `@explore` request, ran a read-only static design-alignment check. It confirmed researched Material/state/
interaction principles are used, but no external design skill was installed/loaded. Addressed heading semantics and IME
dismissal in this increment; picker/error focus announcements, contrast, large text, TalkBack, keyboard, rendered themes
and adaptive-window checks remain unverified. This is a basic first usable preview, not final visual polish or the reviewed
wireframe/token process. No competitor assets/code copied. Offline share resource checks added; full verification pending.

Initial UI check succeeded in 1m 44s. After tightening direct callback/self-test gating, reran all checks:

```
> .\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain --no-daemon
BUILD SUCCESSFUL in 1m 39s
54 actionable tasks: 15 executed, 39 up-to-date
XML reports: suites=16 tests=118 failures=0 errors=0 skipped=1
lint XML: errors=0 warnings=9
```

117 passed/one existing Windows symlink case skipped. Two new style warnings (optional modifier ordering and use of
String.toUri) corrected; final rerun pending. These are JVM/resource tests and successful Compose compilation, not
rendered Compose/share-sheet/real-engine phone verification. No fresh stamped `dist/` handoff yet.

Final step-3 rerun after the style fixes:

```
> .\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain --no-daemon
BUILD SUCCESSFUL in 2m 6s
54 actionable tasks: 16 executed, 38 up-to-date
XML reports: suites=16 tests=118 failures=0 errors=0 skipped=1
lint XML: errors=0 warnings=7
```

117 passed/one existing Windows symlink case skipped; lint is back to the seven known warnings. Home/picker and
separate Diagnostics compile; share-target resource contracts pass. Native UI/share/download/open-file and rendered
accessibility tests remain unrun. Next small step labels the build as a Phase 1 preview, hardens late-callback teardown,
then refreshes `dist/` from clean committed source. No complete Phase 1/queue/service/release claim.

Step 4: versionCode 2 / `0.1.0-phase1-preview`, diagnostic `phase=1-preview` (package unchanged). Added compiled metadata
regression. Hardened reader-thread callback ownership so a canceled task cannot update the next task or write lines after
controller close; activity teardown suppresses observer writes before closing its session logger. Added late-progress/
close/no-publish regressions and made fake re-resolves return fresh task-bound inputs. This does not prove full native
process-tree cleanup. Final verification pending; no engine/SDK/dependency update or permission addition.

Step-4 verification executed:

```
> .\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain --no-daemon
BUILD SUCCESSFUL in 2m 7s
54 actionable tasks: 24 executed, 30 up-to-date
XML reports: suites=16 tests=121 failures=0 errors=0 skipped=1
lint XML: errors=0 warnings=7
```

120 passed/one existing Windows symlink case skipped. New preview metadata and late-callback/close tests passed.
Phone rendering/share/download/playback/accessibility still unrun. `dist/` is not refreshed until the stamped clean-source
helper completes; no incorrect icon-only APK presented as containing the new UI.

### First Phase 1 UI tester handoff — `ec146df` (15 Nov 2026)

Clean source `ec146df670bab77e092d383a04ff06ade12ada11` pushed non-force and remote SHA verified. First standard helper
attempt failed with `DaemonDisappearedException` during lint after running the stamped unit-test task. Cause is unknown;
no failing test or app compile error was reported. Do not count that whole helper attempt as successful.

Retried without changing app/source/SDK/dependencies or deleting caches: appended `-Dorg.gradle.daemon=false` to
`GRADLE_OPTS` only inside the build shell, preserving any existing value; the helper's child PowerShell inherited it.
Gradle confirmed a fresh single-use daemon for each invocation. No persistent environment/config change made.

```
> $env:GRADLE_OPTS = "$env:GRADLE_OPTS -Dorg.gradle.daemon=false"
> powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\build-debug.ps1

testDebugUnitTest lintDebug:
BUILD SUCCESSFUL in 1m 44s
36 actionable tasks: 3 executed, 33 up-to-date

assembleDebug:
BUILD SUCCESSFUL in 29s
38 actionable tasks: 3 executed, 35 up-to-date

XML reports: suites=16 tests=121 failures=0 errors=0 skipped=1
lint XML: errors=0 warnings=7
APK bytes=67278887
APK SHA256=2F3C7407897E6ACA0B6E9107F03546E1C2B2DE7535B923BCE3A2D15C69D85E1B
APK DEX contains gitSha=ec146df, preview version and HomeScreenKt
Built and dist APK hashes match
```

120 passing tests/one existing Windows symlink case skipped. On the successful helper retry, unit tests were UP-TO-DATE,
not executed a second time; validated XML reports are retained from executed stamped/source checks. Generated BuildConfig
and packaged DEX agree: versionCode 2, `0.1.0-phase1-preview`, `gitSha=ec146df`.
SDK `aapt2` confirms packaged SEND/DEFAULT/text/plain and singleTask MainActivity, stable package, cleartext=false and
extracted engine libraries. The padded adaptive wrapper remains packaged with normalized inset 0.1667. App-declared
permission remains INTERNET; prior AndroidX-generated local signature permission/debug tooling components remain.

`dist/app-debug.apk` is now the first usable Phase 1 UI preview, replacing the icon-only `b83d33c` handoff. Install over
the previous local debug APK, review/paste/share a link, Find formats, choose a video+audio height ceiling, download with
the app open, then check saved-file picture/sound and Downloads/YTDLP visibility. Send Home/picker screenshots and latest
log if convenient; details/checklist in doc 13 §6. The next documentation-only commit does not change this APK/stamp.

Still incomplete: exact-format/audio-only picker, durable Room queue, foreground notifications/service and full progress/
error recovery. This is not finished Phase 1 or a release. New native UI/share/engine/cancel/open-file and light/dark,
large-text/TalkBack/window-layout checks have not been run. Earlier owner phone engine/icon evidence remains valid only
for those earlier builds; playback/audio/file-browser confirmation and complete native descendant cleanup remain open.

### Standard quality presets — owner request (15 Nov 2026)

Reviewed the five latest phone screenshots: dark Home, raw-height chooser, stream progress and saved-file state on
`ec146df`. Owner confirms playback **with sound**; that owner-device check is now resolved for this download. The short
attached log establishes 7/7 self-tests, not download timings/replay/publish. Gallery/file-browser visibility, native share,
native cancellation cleanup, light theme, large text/TalkBack/adaptive-window verification remain outstanding.

Step 1 implements offline quality planning: 144/240/360/480/720/1080/1440/2160p, QHD vs 4K labels, both-dimension short-edge
ceilings and visible disabled missing tiers (no duplicate aliases for the same low stream). Nonstandard source dimensions
are shown honestly. Unknown dimensions/unsafe IDs/video without audio are not advertised. Task-private ID whitelists
enforce caps; resolution-first H.264/AAC preference and lower bounded fallback preserve audio. Added seven JVM regressions.
Quality-aware filenames explicitly say `up-to` and retain the true output extension. Existing diagnostic height planner
remains compatible for now; Home/engine integration and native offline selector checks are pending. Untested until run.

Step-1 verification:
```
> .\gradlew.bat testDebugUnitTest lintDebug --console=plain --no-daemon
BUILD SUCCESSFUL in 1m 36s
36 actionable tasks: 14 executed, 1 from cache, 21 up-to-date
XML reports: suites=17 tests=128 failures=0 errors=0 skipped=1
lint XML: errors=0 warnings=7
```
127 passed/one existing host symlink skip. Also ran a no-network synthetic selector smoke check using the previously
extracted pinned yt-dlp 2025.11.12 package: escaped-dot ID regex accepted, 1080×1920 VP9 selected over lower 720 H.264,
1440×2560 excluded, AAC joined. Native Android and a full generated-selector matrix remain untested. No updated APK yet.

Step 2: engine accepts the app-owned typed plan, applies its selector/quality filename, and reports selected width **and**
height. Legacy two-column selection evidence remains readable; later fallback clears both dimensions. Standard-plan
success requires known dimensions within the short-edge ceiling and a selected candidate ID; contradictory/missing
evidence cannot become publishable success. Kept `-N 4`, `mp4/mkv`, Unicode-safe filenames and no-playlist. No thumbnail
embedding/transcoding or forced `.mp4`. Four new JVM tests cover command wiring, evidence and fail-closed selection.
Verification pending; UI is not wired to the new planner yet.

Step-2 verification:
```
> .\gradlew.bat testDebugUnitTest lintDebug --console=plain --no-daemon
BUILD SUCCESSFUL in 1m 36s
36 actionable tasks: 13 executed, 23 up-to-date
XML reports: suites=17 tests=132 failures=0 errors=0 skipped=1
lint XML: errors=0 warnings=7
```
131 passed/one existing host symlink skip. These are fake-runner/evidence checks, not a phone download. The optional
no-network native selector checker is being prepared separately and is not counted as verified in this step.

Step 3: following owner clarification, architectural inspiration is welcome; keep original implementation, existing Engine
boundary and typed task/private selection state. Re-read doc 03 and desktop MainViewModel; no competitor code copied.
Reject duplicate format IDs/oversized candidate lists to prevent ambiguous caps. Added JVM-generated 96-case native selector
matrix and optional `tools/check-quality-selectors.py` against an **existing** version-checked package. It sorts/selects
synthetic dictionaries only, never invokes extraction/download or installs a dependency. Covers all eight tiers in both
orientations, H.264/AAC preference, higher other-codec selection, Opus audio, muxed-only, bounded lower/no fallback.
JVM and native verification pending. No UI/APK handoff yet.

First step-3 check failed at test compilation: attempted kotlinx.serialization JSON fixture output, but that planned
dependency is not currently installed in the Phase 0/preview module. Corrected the test/tool to use JDK Properties XML,
avoiding a new dependency or invented available API. No test pass claimed for that failed invocation. Rerun pending.

Corrected JVM rerun succeeded (59s, 133 pass/one host skip; lint 0 errors/7 known warnings). First native matrix run exposed
that generic `ba` preferred Opus when AAC also existed, contrary to the matrix's AAC compatibility expectation. Added
explicit AAC preference for non-H.264 best video and capped lower fallbacks (Opus remains supported when AAC is absent).
Fixed the nullable parent-directory compiler warning; full rerun/native check pending. This is why strings-only planner
assertions are not sufficient to establish native selection behavior.

Step-3 final verification:
```
> .\gradlew.bat testDebugUnitTest lintDebug --console=plain --no-daemon
BUILD SUCCESSFUL in 1m 56s
36 actionable tasks: 12 executed, 24 up-to-date
XML reports: suites=17 tests=134 failures=0 errors=0 skipped=1
lint XML: errors=0 warnings=7
> python tools/check-quality-selectors.py --package <existing verified pinned zip> --fixtures app/build/reports/quality-selectors.xml
PASS: 96 JVM-generated selectors, yt-dlp 2025.11.12; offline only
```
133 passed/one existing host skip; no new lint/compiler warning. All 96 actual Kotlin-generated selectors passed pinned
yt-dlp sorting/selection with synthetic formats, audio preservation and strict caps. This does not test Android native
execution, FFmpeg merging or live extraction. Optional tool uses the existing extracted package; no package installed.

Step 4: Home now shows all eight standard tiers with source-derived availability, disabled absent tiers and honest best
reported dimensions. Default prefers available 1080 or the highest lower tier. Selection passes a private typed plan into
the engine; plans/IDs/replay never enter UI history. QHD/4K merges use MKV following the owner's desktop rule; lower merges
keep MP4/MKV fallback without conversion. Completion/logging distinguish selected width/height/short-edge resolution from
the requested preset. Added fake-controller portrait/MKV/unknown-dimension tests and updated fixture widths; existing share,
expiry, duplicate-tap and teardown tests retained. Compose rendering/phone download are untested until owner checks;
full JVM/lint/debug-build verification pending. No permission, dependency, icon or native runtime change.

Step-4 executed verification:
```
> .\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain --no-daemon
BUILD SUCCESSFUL in 2m 15s
54 actionable tasks: 16 executed, 38 up-to-date
XML reports: suites=17 tests=138 failures=0 errors=0 skipped=1
lint XML: errors=0 warnings=7
> python tools/check-quality-selectors.py --package <existing verified pinned zip> --fixtures app/build/reports/quality-selectors.xml
PASS: 96 JVM-generated selectors, yt-dlp 2025.11.12; offline only
```
137 passed/one existing Windows symlink skip, seven known lint warnings, debug build successful. New Home/controller
contracts and retained share/replay/expiry/callback tests pass. No new native phone, rendered Compose/accessibility,
MKV-playback or live extraction claim. Next: committed-source stamped helper refresh of `dist/app-debug.apk`, not the
unstamped intermediate build. Keep the app open while downloading; durable queue/service/notifications remain unfinished.

### Standard-quality tester handoff — `74ce7cf` (15 Nov 2026)

Clean source `74ce7cfe0896c9bf110c000c0b38967d12c84fdf` pushed non-force; remote main SHA matched. Ran the standard
helper with process-local `GRADLE_OPTS` daemon=false (existing value preserved), no persistent configuration/cache/
SDK/dependency changes. Both invocations confirmed fresh single-use daemons. This helper attempt succeeded first time.

```
> $env:GRADLE_OPTS = "$env:GRADLE_OPTS -Dorg.gradle.daemon=false"
> powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\build-debug.ps1
testDebugUnitTest lintDebug:
BUILD SUCCESSFUL in 1m 54s
36 actionable tasks: 15 executed, 21 up-to-date
assembleDebug:
BUILD SUCCESSFUL in 30s
38 actionable tasks: 3 executed, 35 up-to-date
XML reports: suites=17 tests=138 failures=0 errors=0 skipped=1
lint XML: errors=0 warnings=7
APK bytes=67327136
APK SHA256=AEC0EBBFF740CDDEB14EC473C7B3F8B80C8588E13D7702321E7B21F2247BBE64
APK DEX contains gitSha=74ce7cf, preview version, standard quality labels and HomeScreenKt
Packaged owner icon bytes equal unchanged source artwork
Built and dist APK hashes match
PASS: 96 JVM-generated selectors, yt-dlp 2025.11.12; offline only
```

Stamped unit tests executed in this helper, not merely UP-TO-DATE. 137 passed/one existing Windows symlink case skipped.
Generated BuildConfig: versionCode 2, `0.1.0-phase1-preview`, `gitSha=74ce7cf`, stable package. `aapt2` verifies compiled
SEND/text/plain/singleTask, cleartext=false, extracted native libraries, and unchanged normalized adaptive inset 0.1667.
App-declared permission is still INTERNET; prior AndroidX signature permission/debug tooling components remain.

`dist/app-debug.apk` now contains the standard picker/planner, replacing `ec146df`. Install over the current debug build;
verify `gitSha=74ce7cf` in summary/log, try two available standard presets (including portrait media where convenient),
check selected dimensions/audio and send a picker screenshot plus a **post-download** session log. High-resolution MKV
playback still needs owner verification. Available tiers are based on reported formats; 1440 is QHD, 2160 is 4K, no
upscaling/above-cap fallback. Owner's exact artwork and working native engine configuration are preserved.

Limits remain explicit: native standard-plan/UI/merge/transfer and new MKV playback are phone-untested; no universal
site/bot/login/decoder promise. Thumbnail embedding is separately deferred; Unicode-safe filename restrictions retained
instead of ASCII-only restriction. No audio-only/exact-format picker, durable queue/foreground notifications or full
native cancellation guarantee yet. Keep the app open. Following documentation-only commit does not change APK/stamp.

### Owner workflow review + phone evidence — 15 Nov 2026
Reviewed owner agent/config/docs/Dependabot edits; narrowed shell grants/default-denied reviewer, corrected handoff and owner-approved /models guidance.
R61: `74ce7cf` portrait picker and 1080×1920 audio/video MP4 merge/MediaStore save confirmed; only one quality download, self-test 7/7.
> .\gradlew.bat testDebugUnitTest lintDebug --console=plain — BUILD SUCCESSFUL in 24s
Tests FROM-CACHE: 138 total, 137 passed, 1 skipped, 0 failures/errors (not freshly executed).
Lint: 0 errors / 7 known warnings; analysis FROM-CACHE, report generated.
Static JSONC/command/contract/doc-index checks passed; full handoff checker blocked by missing PyYAML, not installed.
Untested: OpenCode runtime/reload/permissions/free inference, second-preset/new-file playback and MKV/accessibility checks; APK/icon/engine unchanged.
One reviewed docs/config change set committed; owner explicitly authorized a non-force push. Next: foreground service/progress/cancel notification.
