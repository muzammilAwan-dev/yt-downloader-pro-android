# 03. Snaptube REA findings (observations only, no source code)

Initial coverage: user's `export.zip` (Snaptube Premium 7.67.0, versionCode 76761610, minSdk 21, targetSdk 35). I read `00-BRIEF.md`,
`SHARE.md`, `02-report-*`, and the deep-dive classes `FfmpegTaskScheduler`, `TaskInfo`, `TaskError`, `ExtractError`, `Format`,
`AvailabilityChecker` (plus the deep-dive heading list). I did **not** read the 38 MB of decompiled app source or the `40-*` topic
files. Static analysis only; nothing was executed. Class names under package `o` are R8-renamed and not meaningful.

**Coverage expanded 15 Nov 2026:** the owner supplied `C:\Users\Malku\Documents\REA`. The owner-requested `explore`
agent read relevant original reports and focused decompiled call sites; the parent rechecked core cookie/extraction,
browser/picker and token-WebView evidence. Findings below in §5 and doc 16 §7 supersede the initial coverage gaps where
explicitly stated. No competitor code copied, target executed or new binary analysis performed.

Legal: `SHARE.md` warns this is decompiled third-party proprietary code. Use the ideas, not the code, names, icons, catalogs or endpoints.

## 1. Key numbers (from the export)

| Item | Value |
|---|---|
| Classes / resources / native libs | 28,898 / 3,968 / 36 |
| Declared permissions | 34 |
| Components | 224 total; 42 exported |
| Distinct network hosts | 188 |
| APK size | 34,895,518 bytes |
| UI toolkit | XML Views + Lottie, no Jetpack Compose |
| Ads | Pangle, Mintegral, InMobi, Vungle, Huawei Ads, PubNative; IAB consent |
| Analytics | Sensors Analytics, in-house APM, Firebase |

## 2. Keep / adapt / reject

| Snaptube idea | Verdict | Our version |
|---|---|---|
| Durable task record with explicit stages; progress via one message hub | **Keep** | Room table is the single source of truth; UI + notification observe it |
| Named failure reasons so the UI can offer a specific recovery | **Keep** | `contracts/errors.json` |
| Plugin-shaped extractor registry driven by a JSON catalogue | **Adapt** | yt-dlp already is the extractor registry; keep a small `sites.json` for toggles and status text only |
| Remotely updatable extractor JS | **Reject** | Policy and security risk; the export's own notes say avoid it |
| Compiled defaults + remote config overlay | **Adapt** | Data-only overlay (doc 02 section 5) |
| One storage-status model that drives UI | **Keep** | `StorageStatus` |
| Thin per-feature entry activities (shortcuts, notifications deep-link in) | **Adapt** | Share receiver only, plus optional intent API |
| Icon variants through `activity-alias` | **Adapt (low priority)** | Optional |
| Small JNI surface over mature native libs | **Keep** | Only FFmpeg/aria2c/QuickJS via the library; `UnsatisfiedLinkError` handled |
| Vault, cleaner, booster | **Reject** | Out of scope; the export flags booster-style claims that kill background processes as a hazard |
| Ad mediation, multi-network | **Reject** | Free, ad-free product |
| `QUERY_ALL_PACKAGES`, `REQUEST_INSTALL_PACKAGES`, `SYSTEM_ALERT_WINDOW`, `MANAGE_EXTERNAL_STORAGE`, cleartext HTTP | **Reject** | Permissions budget in `CLAUDE_PROMPT.md` |
| `persistent=true`, `largeHeap=true` | **Reject** | Not needed; the export notes persistent is not honoured for ordinary apps |

## 3. Code-level patterns found in the deep-dive classes (my paraphrase)

**T1. Eight FFmpeg task kinds.** The scheduler distinguishes: transcode to MP3, WebM mux, extract MP3, combine HD (video+audio),
M4A to MP3, combine images, combine TS segments, remux M4S. Lesson: model post-processing as *typed jobs* with their own inputs,
not a free-form command, so each can be validated, retried and reported. We need at least: merge, extract-audio, remux, convert.

**T2. Pending map + running map, bounded.** Two ordered maps (waiting, running); a dispatcher starts the next waiting job only if the
running count is below a configured limit; completion triggers the next dispatch. Lesson: a small explicit scheduler beats ad-hoc
coroutines; mirror it with WorkManager constraints plus a DB-driven "next task" query.

**T3. Pre-flight disk-space check per ffmpeg job.** Before starting, it computes the space the job needs; if insufficient it sets a
WARNING status (not FAILED) and moves on to the next job. On failure, it appends available/needed space to the error text. Lesson:
cheap, user-visible, and avoids misleading "failed" states.

**T4. Eight scheduler statuses including WAITING_FOR_CODEC_PLUGIN.** The ffmpeg stage waits for a plugin to be installed and resumes
when it appears. Lesson: model "waiting on a dependency" as a first-class status. For us: WAITING_FOR_ENGINE (first-run engine unpack or
update in progress).

**T5. Task status vs. fail reason are separate.** `TaskStatus` is coarse (RUNNING, PENDING, ERROR, PAUSED, FINISH, DELETED, WARNING);
`FailReason` is a numeric enum grouped by stage: about ten extraction reasons (timeout, unplayable, login required, live offline,
unauthorized, media unavailable, sign-in expired, unknown...), then download reasons (timeout, no space, HTTP 403/491/492/498/501),
then convert. A separate `TaskError` enum (about 40 entries) covers internal faults (no permission, no space, md5 mismatch, mux failed,
plugin faults). Lesson: three levels (status, user-facing reason, internal fault) keep UI simple and logs precise. Ours: Stage +
`DownloadError` + local log.

**T6. Format model carries expiry.** Each format object stores download URL, headers map, MIME, codec, codec standard, quality, size,
dimensions, `expireTime`, `timeToLive`, `availableAt`, a "playable" flag and a first-second offset. Lesson: keep freshness and
necessary headers together with the task-private chosen format; do not persist cookies/auth headers/signed URLs in history
or logs. Re-resolving on expiry/403 is our proposed recovery, **not a branch verified by this summary**. A headers-capable
model does not itself prove where account cookies come from or how they are passed to extraction and media transfer.

**T7. ExtractError is tiny and generic.** OK, EXTRAS_REQUIRED, IO_EXCEPTION, NO_VIDEO, NOT_SUPPORTED, UNHANDLED_EXCEPTION, OTHER.
EXTRAS_REQUIRED hints at "needs more input" (login/cookies/consent) as its own outcome. Ours maps these to LOGIN_REQUIRED etc.

**T8. Availability checker with capped exponential backoff.** A background checker polls a first-party endpoint (with the network's
country code) to learn whether site extraction is currently available; backoff is capped at 512 seconds; callers can wait on a latch
with a timeout or force a check. Lesson: a "health of extraction" signal is useful. We have no server, so derive health locally from
recent task outcomes (e.g. 3 consecutive ENGINE_OUTDATED -> prompt to update) and optionally from the repo's data-only config.

**T9. Per-stage timing/size metrics.** Each ffmpeg stage logs start, success or failure with duration and output size. Ours: keep the
same in a *local* log the user can share manually.

**T10. PO-token exception classes exist** in a `youtube/potoken/exception` package (`BadWebViewException`, `PoTokenException`). [I] This
suggests Snaptube generates PO tokens through a WebView, as NewPipe publicly does (PR #11955). Not verified by reading that code.
**Superseded by §5:** actual separate token-generating WebViews and provider routing were inspected on 15 Nov.

**T11. WorkManager is in the stack.** The application class implements the WorkManager configuration provider, so periodic and deferred
work goes through WorkManager even though the main downloads use their own scheduler. Ours: WorkManager for everything.

**T12. Foreground-service types declared:** dataSync, mediaPlayback, specialUse. Ours: dataSync (+ mediaProcessing only if conversion
needs more than the dataSync budget; see doc 06).

## 4. Limits of the initial export read

- How Snaptube's extractors beat YouTube in 2026 (the remote JS, `libvideo_v1.so`, obfuscated `o.*` classes are not in the chat files).
- Whether its PO-token path is WebView-based (inference only).
- Anything about runtime behaviour: no dynamic analysis was done.

The WebView question was answered by the expanded static trace below. Runtime success in ClipHarbor, exact renewal
branches obscured by decompiler errors, and complete redirect/security behaviour remain unverified.

## 5. Expanded browser/session/extraction trace (15 Nov 2026)

Source root: `C:\Users\Malku\Documents\REA\reversed\jadx\sources`. Original export brief identifies version 7.67.0
and static JADX/Apktool provenance (`export/00-BRIEF.md:1–8`); this is report metadata, not a newly hashed/executed APK.
Detailed source-line references and qualification are in `16-BROWSER-AUTH-AND-MEDIA-DISCOVERY.md` §7, research R36.

- **Real website browser/login:** `VideoEnabledWebView` is constructed; website sign-in and browser navigation observe
  cookie-marker changes, flush cookies and emit a session-change event. The login-marker test is heuristic, not proof
  that the server accepts every subsequent request. Native search interfaces also exist; not every search UI is proven
  to be the website renderer.
- **Authentication reaches extraction:** the parser reads the shared WebView cookie store, attaches eligible cookies
  to page extraction and uses cookie-derived account authorization for supporting API clients. This is stronger evidence
  than the original format-header inference. The extraction-context adapter was **not** shown explicitly serializing
  cookies: downstream session access supplies authentication. A bidirectional WebView/HTTP cookie adapter also exists.
- **Picker -> queue -> transfer:** cached metadata or source URL feeds extraction/picking, selected-format submission
  creates/persists a task, and a worker selects media information/format before transfer. Supplied headers reach HTTP
  transfer; if absent, a Cookie lookup uses the **media request URL**. This does not prove account credentials go to every
  CDN or that redirects are safely scoped. Header persistence in that app must **not** be inherited by ClipHarbor.
- **Bot/login classification and recovery:** bot, age and other login-required outcomes are distinguished. Expired-login
  picker continuations resume **submission**, not a proven extraction refresh; a failed-task expired-login path requeues
  after the session-change signal. Ordinary login-recommendation branches are not proven to auto-retry. The generic red
  failure/sign-in wording is supported; the owner's exact bot-message display chain was not reproduced end-to-end.
- **PO tokens are separate:** two principal providers create separate token WebViews; routing depends on client/API,
  with caches, lifecycle handling and bounded generation failures. Cookie login is not proven to generate/invalidate
  tokens or bind every token to the account. Generator lifetime is not equivalent to verified per-token cache expiry;
  decompiler-damaged renewal branches prevent exact claims.

Adapt boundaries and state transitions: consented session -> task-private credential context -> existing Engine -> picker
and durable intent -> scoped transfer -> bounded authentication recovery. Use original implementation/tests; do not copy
private authorization/token algorithms, remote JavaScript, unsafe bridges, disabled security checks or broad permissions.
