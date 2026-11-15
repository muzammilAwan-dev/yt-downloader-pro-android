# 12. Automated tests, per-phase APKs and deep logging

Extends `09-TEST-STRATEGY.md`. Goal: minimise manual testing, and when a manual test is unavoidable, make the result machine-readable so an AI can debug from one file.

## 1. The `dotnet test` equivalent

| .NET habit | Android/Gradle equivalent |
|---|---|
| `dotnet test` | `./gradlew testDebugUnitTest` (JVM unit tests, no phone needed) |
| Mock a service | Fake `Engine` implementation (see `02-ARCHITECTURE.md`) so queue, state machine, retry, error mapping and validator are tested without YouTube |
| Integration tests against a DB | Room in-memory tests; Robolectric for Android-dependent code on the JVM [K] |
| CI on every push | `.github/workflows/android.yml` already runs unit tests, lint and (when `androidTest` exists) emulator tests |

Rule for the builder: **every feature ships with tests in the same commit**, and the phase is not "done" until `./gradlew testDebugUnitTest lintDebug` is green and the output is pasted into `docs/PROGRESS.md`.

## 2. What automation can and cannot cover

| Covered by CI | Needs the phone (cannot be automated here) |
|---|---|
| State machine, retries, re-resolve on 403, no-space warning, cancel, process-death recovery (fake engine) | Real yt-dlp + JS runtime against YouTube |
| Option validator and error mapping from sample yt-dlp output text | Real PO-token / format-withheld behaviour |
| Cookie temp file lifecycle, no cookies in history | Battery, thermal, OEM background kills |
| Manifest permission budget, cleartext off | Foreground-service timeout on a real Android 15 device |
| Log redaction (see section 4) | Real MediaStore/SAF behaviour on your OS skin |

Two caveats: (a) GitHub runner IPs may be treated differently from a phone by YouTube (one yt-dlp issue reports an IP-specific block), so never make a live-YouTube test a required CI check. (b) The workflow's emulator job uses an x86_64 image; whether `youtubedl-android` ships x86_64 native binaries is **not verified**, so engine-dependent tests must use the fake engine there.

## 3. The built-in self-test (replaces most manual testing)

Add a **Diagnostics screen** from Phase 0 onward with a "Run self-test" button. It runs these checks on the phone, logs each as PASS/FAIL with details, and produces one shareable report:

1. Engine unpack OK; library version; Python version; yt-dlp version; `yt-dlp-ejs` present and version-matched.
2. JS runtime found (QuickJS) and its version; ffmpeg and aria2c present and executable.
3. Storage: write + read + delete in app-private dir; MediaStore insert test; free space.
4. Notifications permission; foreground-service start (dataSync) works.
5. Network reachable; DNS OK.
6. Resolve test: `--dump-json` for a URL the owner pastes (default: none, no hard-coded URL); records formats list, max height, whether HD formats were withheld.
7. Optional download test of a small file with progress and merge (ffmpeg) to prove the full path.
8. Summary line: `SELFTEST RESULT: PASS|FAIL (n/m checks)`.

The Phase 0 spike is essentially this screen. The "report template" the AI asks you to fill in becomes: tap the button, share the log.

## 4. Deep logging requirements

**Where:** app-private `files/logs/app-YYYYMMDD-HHmmss.log` for the bounded preview/history (max 5 files, 2 MB each).
Owner-requested full-session logging (R68) additionally keeps `files/logs/session/active.txt` from the current process startup through all
recorded actions, including background work/reopens. It has **no 2 MB cap** so rotation cannot silently remove the beginning; long sessions
use more disk space. The journal resets on a new process or explicit **Clear logs**. Not world-readable. No network upload, ever.
**Tester (debug) builds also write a public copy** to `Download/YTDLP-logs/` (one file per session, last 5 kept) so you can grab it without any menu; see `13-LOCAL-BUILD-AND-INSTALL.md` section 4 for how and the Android 7-9 limitation.
**How the owner gets it:** Diagnostics -> **Share session log** captures the Share action and exact flushed-byte cutoff synchronously,
then prepares one immutable `.txt` on IO and opens the Android share sheet via `FileProvider`. All recorded session events up to that tap are
included, not only the newest rotating chunk or visible tail. Subsequent events do not change that file; keep the last 5 private exports.
Explicit Clear removes private history/exports and starts a `session_scope=since_clear` timeline; previous public copies/already-shared files
are not withdrawn. Incomplete journals/export failures produce a clear error rather than silently sharing a partial log.
**Copy summary** remains a short report, not the full log. Opening a chooser is not proof that the owner sent the file.
**Switch:** `Verbose logging` setting, **ON by default in debug and "tester" builds, OFF in release**, with a clear "Clear logs" button. Incognito mode disables file logging.

**Header (first lines of every shared file):**
```
== DIAGNOSTIC REPORT ==
app=<name> versionName=<..> versionCode=<..> gitSha=<..> buildType=<debug|release> phase=<0..>
device=<manufacturer model> android=<release> api=<n> abi=<arm64-v8a> locale=<..> freeSpaceMB=<..>
engine: library=<ver> python=<ver> ytdlp=<ver> ejs=<ver|missing> jsRuntime=<quickjs ver|missing> ffmpeg=<ver|missing> aria2c=<ver|missing>
settings: concurrency=<n> speed=<mode> clients=<value|default> updateChannel=<stable|nightly> wifiOnly=<bool> incognito=<bool>
permissions: notifications=<granted|denied> safTree=<set|unset>
```

**Line format (greppable, one event per line, key=value):**
```
2026-11-14T14:03:22.481Z I task=17 stage=RESOLVING event=start url=https://youtube.com/watch?v=<id> opts=<hash>
2026-11-14T14:03:25.002Z I task=17 stage=RESOLVING event=ok formats=24 maxHeight=1080 withheld=false ms=2521
2026-11-14T14:04:10.330Z W task=17 stage=DOWNLOADING event=http_403 action=RE_RESOLVE attempt=1
2026-11-14T14:04:41.900Z E task=17 stage=MERGING event=fail code=MERGE_FAILED exit=1 ffmpegTail="<last 20 lines>"
```
Levels: V/D/I/W/E. Always include `task`, `stage`, `event`. One correlation id (`task`) ties every line of a download together.

**Must log:** app start and engine init (with timings); every stage transition; every engine call with duration and exit code; the **last N (e.g. 200) lines of yt-dlp `-v` output** for any failed task (keep success tasks to a short summary to limit size); every error code from `contracts/errors.json`; foreground-service start/stop/`onTimeout`; connectivity changes; free-space checks; update/rollback events; unhandled exceptions with stack trace (install an uncaught-exception handler that writes the trace, then rethrows); ANR-ish slow main-thread warnings in debug builds (StrictMode).

Implemented action coverage: link edit/paste/share receipt, find formats, quality selection/Download/Cancel/retry, pending link, saved-file open,
diagnostics/back/self-test/summary/clear/verbose/permission controls, Activity create/resume/pause/stop/destroy/new intent. Action entries contain
fixed names, phase and numeric/boolean choices, **not typed or clipboard text**. Home forwards the available last-200 resolve debug lines with
the correct stage, engine download lines, changed integer progress, success timings/exit and publication details; errors keep their redacted tails.
This is app diagnostics, not every Android/system event or raw info JSON. Crash-handler, StrictMode/ANR and connectivity-change monitors above
remain requirements, not implemented/proven coverage. Native chooser/phone paths need device verification.

**Must NOT log (redact before writing, unit-tested):** cookie values or cookie file contents; `Authorization`, `Cookie`, `Set-Cookie` headers; PO tokens; signed media URL query strings (log host + path only; replace the query with `?<redacted>`); the owner's account names/emails; clipboard contents other than the URL being processed. Full page URLs are logged by default **only in tester/debug builds**; release builds log video ID/host only unless the owner turns on "include URLs".

**Tests required:** redactor unit tests with realistic samples (cookie lines, signed URLs, tokens); rotation test; "log never contains a cookie value" test run against a full fake download; header builder test.

## 5. Per-phase APK for you to install

- Tag each phase candidate `phase0-rc1`, `phase1-rc1` ... and run **Actions -> Android CI -> Run workflow**; the job runs the tests first, then uploads the APK + `SHA256SUMS.txt` (debug builds are fine for testers; release needs signing secrets).
- Tester builds are `debug` with verbose logging on. Install, tap **Run self-test** (Phase 0) or follow the phase checklist, then **Share log** and give the file to the AI.
- Each phase ends with a short on-device checklist in `docs/PROGRESS.md` listing only what automation cannot cover (section 2, right column), so manual work stays small.

## 6. How the AI uses the log

Give the debug prompt (`DEBUG_REFINE_PROMPT.md`) the shared `.txt` file unedited. Because every line has `task`/`stage`/`event` and the header carries versions, the AI can: find the first `W`/`E` line, read that task's full timeline, check engine/JS-runtime/ffmpeg versions in the header, and compare against the error taxonomy, without asking you questions.
