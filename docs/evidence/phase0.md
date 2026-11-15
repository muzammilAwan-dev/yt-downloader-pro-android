# Phase 0 evidence — engine spike

Per `docs/01-PLAN.md` §4, Phase 0 is the go/no-go gate: prove on a real arm64 phone that a link resolves through
yt-dlp + a JS runtime, that 1080p video + audio download, merge, and land in `Download/` through MediaStore.

Two halves:

1. **App side (done, this machine).** Built, tested and audited here; every number below is pasted output.
2. **Device side (open).** Requires the owner's phone. Nothing in this file claims the on-device half has happened.

## 1. Build identity

| Item | Value |
|---|---|
| App | ClipHarbor `0.1.0-phase0`, package `io.github.muzammilawan.clipharbor` |
| Built from commit | `8cd9487` (`BuildConfig.GIT_SHA`, read back from the generated BuildConfig after the build) |
| APK | `dist/app-debug.apk`, 67,693,493 bytes |
| SHA-256 | `E1A8C9F5780FD4F5E9A647B2B779521E0E0866FEDB0AFA15809ECEE2F088325A` |
| minSdk / target / compileSdk | 24 / 37 / 37 |
| ABIs in the APK | `arm64-v8a` only (deliberate: 67 MB vs ~200 MB) |
| Permissions in the APK | `INTERNET` + AndroidX signature-level `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` |
| Cleartext | `usesCleartextTraffic=false` |
| Native packaging | `extractNativeLibs=true` (engine reads `libpython.zip.so` / `libffmpeg.zip.so` by path) |

## 2. Engine facts (read out of the artifacts, not from the README)

| Component | Version / form | Source |
|---|---|---|
| youtubedl-android `library` + `ffmpeg` | 0.18.1 (Maven Central) | POM, `maven-metadata.xml` |
| yt-dlp | **2025.11.12** (git `335653be82d5…`, channel stable), packaged as a Python zipapp in `res/raw/ytdlp` | `yt_dlp/version.py` inside the zipapp |
| yt-dlp-ejs + solver JS | vendored inside the zipapp (`yt_dlp_ejs/yt/solver/core.min.js`, `lib.min.js`) | zipapp listing |
| CPython | **3.12** (`usr/lib/python3.12/`, `libpython3.12.so.1.0`) | unzipped `libpython.zip.so` |
| JS runtime | QuickJS, `libqjs.so` (0.9 MB arm64), passed by the library as `--js-runtimes quickjs:<dir>/libqjs.so` | AAR entries + `YoutubeDL.kt` |
| ffmpeg | **GPL build** (payload contains `libx264.so.164`), `--ffmpeg-location …/libffmpeg.so` | unzipped `libffmpeg.zip.so` |

Consequences recorded elsewhere: upstream yt-dlp stable is **2026.10.26** (so the artifact is ~11 months old, and the
library's updater has no checksum check → Phase 2 work), and the GPL ffmpeg build makes a source offer mandatory before
release (`THIRD_PARTY_NOTICES.md`, Phase 3).

## 3. What the app actually does (one download, in order)

1. `YoutubeDL.init(context)` + `FFmpeg.init(context)` unpack the Python and ffmpeg payloads into app storage (first run
   only; timed and logged as `event=engine_init ms=…`).
2. Resolve: `-v --dump-json --no-playlist` → last JSON line parsed into `MediaInfo`; `[debug]` header parsed for
   yt-dlp / Python / EJS / JS-runtime / ffmpeg versions.
3. Format plan: `bv*[height<=1080][vcodec^=avc1]+ba[acodec^=mp4a]/bv*[height<=1080]+ba/b[height<=1080]/b`
   (device-friendly H.264+AAC first, any codec next, muxed last) — `core/FormatPlanner.kt`.
4. Download: `--merge-output-format mp4/mkv --progress --newline --windows-filenames --no-mtime --no-playlist
   --print after_move:filepath --no-simulate -P <staging> -o "%(title).150B [%(id)s].%(ext)s"`.
5. Publish: `MediaStore.Downloads` + `RELATIVE_PATH=Download/ClipHarbor` with `IS_PENDING` on API 29+; app-specific
   external dir on 24–28 (no storage permission is allowed by the budget). Staging dir is deleted afterwards.
6. Every stage writes `task=… stage=… event=…` lines to the rotating private log; the fail path appends the last lines of
   yt-dlp output. Cookies, auth headers, PO tokens and signed-URL queries are redacted.

## 4. Automated evidence (this machine, 15 Nov 2026)

```
> .\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain

BUILD SUCCESSFUL in 21s
test suites=8 tests=46 failures=0
lint: 0 errors, 6 warnings
```

(Re-run after the last three code fixes: same suite/test counts and the same 6 warnings.)

- 8 test suites, fake `Engine` + fake storage/network probes; no live-YouTube call in CI.
- Lint: 0 errors, 6 deliberate/informational warnings (Gradle 9.9.1 exists; Compose plugin 2.4.21 exists; coroutines
  1.11.0 exists ×2; ChromeOS x86_64 ABI missing because the APK is arm64-only; `UsableSpace` hint).

## 5. Device evidence — to be filled from the owner's report

| Check | Expected | Owner's result |
|---|---|---|
| Install and first launch unpacks the engine | status "Engine ready.", `ms=` in the log | pending |
| `SELFTEST RESULT` | `PASS (7/7 checks)` | pending |
| `jsRuntime=` line | a QuickJS entry, not `missing` | pending |
| `ffmpeg=` line | a version, not `missing` | pending |
| resolve | `maxHeight=1080`, `withheld=false` | pending |
| download | `Saved … to Downloads/ClipHarbor`, `container=mp4` or `mkv` | pending |
| playback | 1920×1080 (or highest offered) **with audio** | pending |
| visibility | file appears in Files/gallery under `Downloads/ClipHarbor` | pending |
| log copy | `Download/ClipHarbor-logs/clipharbor-log-*.txt` exists and uploads/copies out | pending |

Report template to paste into this section (also in `docs/PROGRESS.md`):

```
app=ClipHarbor version=0.1.0-phase0 build=debug
device=<manufacturer model> android=<version> api=<n> abi=arm64-v8a
engine: library=<..> ytdlp=<..> python=<..> ejs=<..> jsRuntime=<..> ffmpeg=<..>
SELFTEST RESULT: PASS|FAIL (n/m checks[, skipped=k])
failed checks=<ids and details, or "none">
resolve: title=<..> duration=<..>s formats=<n> maxHeight=<n> withheld=<true|false>
download: saved=<uri or error code> file=<name> sizeMB=<n> container=<mp4|webm|mkv> elapsed=<mm:ss>
player: resolution shown=<..> audio=<yes|no> visible in gallery=<yes|no>
log file=<clipharbor-log-....txt, attached=yes|no>
oddities=<thermal warnings, notification missing, UI stall, anything else>
```

## 6. Exit decision

- **Go** if the self-test passes on the device and the file plays with audio at 1080p (or the highest format YouTube
  offers, with the withholding recorded in the report).
- **Conditional go** if HD is withheld: that is a PO-token/enforcement fact (docs/RESEARCH-LOG.md R17), not an engine
  bug; Phase 1 can proceed and Phase 2 owns the mitigation.
- **No-go** if QuickJS fails to start, ffmpeg cannot merge, or MediaStore publish fails — those block every later phase.
