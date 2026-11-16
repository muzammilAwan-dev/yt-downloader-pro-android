# Phase 1 MVP delivery — 16 Nov 2026

**Status: approved Phase 1 implementation and host delivery complete; Android acceptance deferred, NOT PASSED.**
Stop here for the owner's `next`; do not begin Phase 2. This is a debug tester, not a release-readiness claim.

## Delivered scope

- Paste/share review without automatic work; compact video-first picker, source-resolution labels, separate original audio,
  explicit all-stream access and advanced exact video. Standard resolutions remain the default.
- Same-link, unconsumed task-private chooser reuse; explicit Refresh re-extracts. Initialization/subprocess/replay timings
  remain observable. No live extraction speedup has been measured; the owner's earlier 38–43 second resolves are not claimed fixed.
- Process-wide Room queue/history, at most 200 records, revision-fenced writes and conservative interrupted-task recovery.
  Stage, review fresh formats, forget record and open previous saves; one explicitly confirmed download at a time.
- Task-only foreground ownership, progress, Cancel before saving and cleanup-fenced Android timeout handling.
  Optional notification permission never admits or gates a download.
- Contract-checked recovery messages and safe fresh retries; no automatic connectivity resume or rescheduling.
  Storage headroom estimate and actual-byte publication recheck; unknown sizes stay unknown, estimates are not guarantees.
- MediaStore pending-row copy/commit checks; API 24–28 narrow content-URI viewer grants and collision-safe app-specific saves.
  Interrupted publication requires checking Downloads and explicit acceptance before retry; no automatic duplicate save.
- Full-session local redacted logs, diagnostics/self-test/share/copy/clear, bundled engine freshness and original branding preserved.

## Artifact and host checks

| Item | Verified result |
|---|---|
| APK | `dist/app-debug.apk` — **70,620,367 bytes** |
| Actual own BuildConfig DEX stamp | `56c15c4dc76a579c400e7b8a084dbb408eda299b` |
| SHA-256 (built/copy/checksum agree) | `71BE20C713B902B57E3FAA4DA9F904EEEEFB023175BC6D928CB27D8BE38ED233` |
| APK assembly | **BUILD SUCCESSFUL in 1m 45s**; offline `assembleDebug -PgitSha=<full commit>` |
| Fresh full host check | **BUILD SUCCESSFUL in 3m 6s**; cached Gradle `testDebugUnitTest lintDebug --offline --rerun-tasks --console=plain` with matching stamp |
| Android JVM suite | **338 total / 337 passed / 1 Windows symlink skip / 0 failures/errors** |
| Lint | **0 errors / 7 known warnings** |
| Stdlib | **24 helper regressions + 4 exported Room schema/SQL regressions passed** |
| Pinned-engine synthetic selectors | **96 standard video + 9 original audio + 20 actual Kotlin exact-video selectors passed**, sockets blocked; no media downloaded |
| Identity | YTDLP / `io.github.muzammilawan.clipharbor`, minSdk **24**, target **37**, arm64, versionCode **2** / `0.1.0-phase1-preview` |
| Engine | Verified bundled yt-dlp **2026.10.26**, EJS **0.8.0**; library stays **0.18.1**, no runtime updater |

Signature verified with the same owner debug signer as the previous tester:
`66b04ef4d990c10cd6d5b9126cfd3e3ceb214c390f63eb6c09595ff9aa391658`.
All **2 assets** (engine/full notices), **7 arm64 native entries**, and **3 launcher/icon entries** match the verified
`f96194e` baseline byte-for-byte. The binary manifest intentionally differs: SDK `aapt2 dump xmltree` shows only
Room's `androidx.room.MultiInstanceInvalidationService` (`exported=false`, `directBootAware=true`) plus line-number shifts.
The old helper's optional identical-manifest baseline mode was **not** used or weakened. No new requested permissions:
INTERNET, FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC and POST_NOTIFICATIONS, plus the pre-existing AndroidX
signature-only local receiver permission. Backup remains disabled and cleartext traffic prohibited.

Final helper reports:
`C:\Users\Malku\AppData\Local\Temp\opencode\ytdlp-phone-free-o5y5fbwt\summary.md` and `summary.json`.
Status **PASS_HOST_CHECKS_DEVICE_REQUIRED**, device verification **NOT_RUN**, unchanged temporary runner **REMOVED**;
that directory contains only the two reports. The fresh exact-video fixture was generated during the full check and
then passed `python -I tools/check_exact_video_selectors.py`; the four schema checks were rerun afterward.
Known lint warnings: dependency/tool freshness notices (4), arm64-only ChromeOS coverage, conservative free-space probe
guidance and missing monochrome launcher layer. No dependency/tooling update was attempted for these warnings.

## Limits and deferred gates

This is Phase 1 implementation delivery under the owner's phone-free approval, **not a passed Android acceptance gate**.
No phone connection, emulator, media download or new tooling installation is used for host verification.

- Latest actual owner-tested APK remains `e37686c`; its audio and 720p saves are recorded, not audible playback/visibility proof.
- New APK startup/in-place installation, Android Room/process recovery, sustained background/lock, real notification Cancel,
  newer API timeouts, Files/gallery visibility, light theme, large text/TalkBack and original-audio/exact-video/MKV playback are deferred.
- Earlier failing long samples and broader 403 reliability remain unresolved. Fresh extraction and engine freshness are not guarantees.
- API 24–28 saves are app-specific and are removed on uninstall. Forget record deletes neither saved files nor retained private parts.
- No concurrent/automatic queue scheduler, byte-range pause/resume, MP3 conversion, thumbnail embedding, folder picker or raw-part saving.
- Browser/login/cookies, playlists, trim/subtitles, site toggles, broader engine-update policy and runtime code downloads are not delivered.
  Phase 2 requires the owner's `next`; runtime code downloading remains prohibited.

## Optional future device acceptance (deferred, not a new installation request)

When the owner chooses to test the full project, use `docs/13-LOCAL-BUILD-AND-INSTALL.md` and identify logs by the artifact stamp.
Check paste/share and both picker modes; queue stage/restart/review without auto work; actual dimensions and audible playback;
foreground/background/Cancel and saved-file visibility; explicit interrupted-save review; complete post-result session-log export.
Android 10 remains the primary phone baseline. A compatible newer device is needed for newer-API timeout checks.
