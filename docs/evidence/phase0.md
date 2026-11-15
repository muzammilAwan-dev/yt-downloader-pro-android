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

## 5. Device evidence (owner's run, 15 Nov 2026)

Device as reported by the app's own header: `device=INFINIX MOBILITY LIMITED Infinix X682B android=10 api=29
abi=arm64-v8a locale=en-US freeSpaceMB=11194`, APK `gitSha=8cd9487` (the build audited in §1).

| Check | Expected | Result (from `app-20261115*.log`) |
|---|---|---|
| Install and first launch unpacks the engine | status "Engine ready.", `ms=` in the log | **PASS** — `event=engine_init ms=8297` |
| `SELFTEST RESULT` | `PASS (7/7 checks)` | **PASS** — `SELFTEST RESULT: PASS (7/7 checks)` (the earlier run was 6/7 with `resolve` skipped because no URL was entered) |
| `jsRuntime=` line | a QuickJS entry, not `missing` | **PASS** — `quickjs-2025-04-26` |
| `ffmpeg=` line | a version, not `missing` | **PASS** — `ffmpeg=7.1.1` |
| resolve | HD offered, `withheld=false` | **PASS** — `formats=31 videoOnly=18 audioOnly=8 muxed=1 maxHeight=1920 maxHeightMuxed=640 hdUnavailable=false withheld=false warnings=0`, 17,556 ms |
| download | merged file, published | **PASS** — video 1.73 MiB + audio 660.71 KiB → merged `…[j6EUktS4XYo].mp4` = **2,505,222 bytes**, 19,622 ms |
| publish | `Downloads/ClipHarbor` via MediaStore | **PASS** — `uri=content://media/external/downloads/18406 mime=video/mp4` on API 29 |
| playback | plays **with audio** at the offered HD size | **owner's word needed** — not visible in the log; the size arithmetic (1,814,000 + 660,710 ≈ 2,474,710 B → 2,505,222 B container) shows both streams are in the file |
| visibility | appears in Files/gallery under `Downloads/ClipHarbor` | **owner's word needed** |
| log copy | log can be shared out and read on the PC | **PASS** — three files supplied: one 6/7 session (no URL) and two snapshots of the download session |

Engine versions measured on the device (from the second self-test, which runs after a successful resolve):

```
check=engine-init status=PASS detail="library=0.18.1 ytdlp=(2025.11.12) python=3.12.11 ejs=? jsRuntime=quickjs-2025-04-26 ffmpeg=7.1.1"
```

So the artifact-derived facts in §2 hold on the phone: CPython **3.12.11**, QuickJS present, ffmpeg 7.1.1, yt-dlp
2025.11.12. Two gaps in the report itself: `ejs=?` (yt-dlp's `-v` header did not print a separate EJS version line) and
`ytdlp=(2025.11.12)` (the library's `version()` returns the value with parentheses, and our parser did not override it).

### What the run also shows

- **No PO-token block on this video**: 1080p-class formats were offered and downloaded with zero warnings, so the default
  client order worked without a token provider (not a general guarantee — see docs/RESEARCH-LOG.md R17).
- **The URL is extracted twice**: resolve took 17.6 s and the download stage spent another ~17.9 s before the first byte
  (05:07:08.063 start → 05:07:25.915 first progress line), because `download()` calls `execute()` on the URL again
  instead of reusing the resolved info (Phase 1: `--load-info-json` or a single process).
- **Log hygiene**: the shared copies are hard to tell apart (same `app-20261115*.log` name, one snapshot without the
  report header) and one URL line reads `?<redacted><redacted>` because the URL is redacted twice (once by the caller,
  once by the log writer). Phase 1 items.

### Report template (kept for later phases)

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

**GO** (15 Nov 2026). The device run satisfies every measurable exit criterion in `docs/01-PLAN.md` §4: the link resolved
with QuickJS present, HD video + separate audio downloaded, ffmpeg merged them into one MP4, and MediaStore published it
on API 29. Two human checks (does it play with sound, does the gallery see it) remain to be confirmed by the owner; the
file size arithmetic already shows both streams inside the merged file.

- **Conditional go** would have applied if HD had been withheld: that is a PO-token/enforcement fact
  (docs/RESEARCH-LOG.md R17), not an engine bug.
- **No-go** would have applied if QuickJS had not started, ffmpeg could not merge, or the MediaStore publish failed —
  none of that happened.

## 7. Phase 1 backlog opened by this run

1. Reuse the resolve result for the download (`--load-info-json` / one process) — saves ~18 s per item.
2. Log the selected `format_id`/height/fps and the `[debug]` header of the resolve, so "1080p" is provable from the log.
3. Report EJS: parse whatever yt-dlp prints, or state `unknown` explicitly; normalise the library's `(2025.11.12)`.
4. Make `LogRedactor` idempotent and stop pre-redacting at call sites (fixes the doubled `<redacted>`).
5. Distinguishable session log names and a header written into every new file.
6. Foreground service + notification (the spike must stay in the foreground today).
