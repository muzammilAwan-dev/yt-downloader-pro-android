# ROADMAP: YTDLP for Android

Date: 17 Nov 2026. Owner: Muzammil Awan. Status: proposal, awaiting owner approval. Revised 17 Nov 2026 against the live
repo (see §3.1/§3.2): the first draft used an older Android zip, so its baseline is corrected here. **No app feature code was changed.**

Goal: an Android twin of the owner's desktop app (`yt-downloader-pro`): paste or share a link, pick a format, download from YouTube and every popular platform yt-dlp supports, with Snaptube-like convenience and none of its ads, tracking or permission sprawl. Streaming is out of scope for now.

---

## 0. How an AI agent uses this file

1. Read `AGENTS.md`, `docs/STATE.md`, then **only the one phase section** you were told to run (plus its listed input files, at most 5).
2. Do not read other phases, `docs/PROGRESS.md` in full, or any decompiled third-party source (clean-room rule in `AGENTS.md`).
3. Tag every factual claim you write: `[Code]` (file path read this session), `[Docs]` (repo doc), `[Report]` (owner-supplied research), `[Web]` (URL + date), `[I]` (inference), `[?]` (unknown). Never state a build, test or device result you did not see.
4. Work in small steps: tests first (fake `Engine`), then `./gradlew testDebugUnitTest lintDebug`, then a conventional commit.
5. End every phase with a report: **host-verified / device-verified / deferred**, update `docs/STATE.md` (keep it under about 60 lines), then **stop and wait for owner approval**.
6. If this file contradicts the repo, the repo wins: log the conflict in `docs/RESEARCH-LOG.md` and ask the owner.

Phase spec template (copy into `docs/phases/<id>.md` before each phase):
`Goal / Inputs (max 5 files) / Do-not-touch / Steps / Tests / Exit gate (objective) / Owner-only checks / Stop point`.

---

## 1. Decisions already taken

| Id | Decision | Reason |
|---|---|---|
| DR-000 | **Do not fork LibreTube.** Keep `yt-downloader-pro-android` as the base. | The goal is a downloader, not a streaming client. LibreTube is YouTube-only, Views-based, NewPipe-extractor-based, with no yt-dlp or FFmpeg, minSdk 26. [Web: github.com/libre-tube/LibreTube README and `app/build.gradle.kts`, read 17 Nov 2026] Its upstream also rejects AI-generated PRs. Revisit only if streaming becomes a goal. |
| DR-001 | Native Kotlin + Compose, engine `youtubedl-android` behind the `Engine` interface, GPL-3.0. | [Docs: `docs/01-PLAN.md` §2] |
| DR-002 | Distribution: GitHub Releases, IzzyOnDroid, F-Droid. Not Google Play. | [Docs: `docs/01-PLAN.md` §2; `docs/07`] |
| DR-003 | No ads, analytics, accounts, remote code, cleartext HTTP. Permission budget: INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS, FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC. | [Docs: `AGENTS.md`] |
| DR-004 | Snaptube and 1DM+ are **behaviour references only**. No code, endpoints, assets or names. | [Docs: `AGENTS.md`, `docs/03`] |
| DR-005 | **Foreground service (`dataSync`) stays the execution owner** for long downloads. UIDT and WorkManager are not adopted. | Owner decision 17 Nov 2026. Keeps API 24-33 and 14+ on one tested path and adds no permission. [Code: `service/DownloadService.kt`, `docs/STATE.md`] |
| DR-006 | The display name is **YTDLP only**, with **no spaced expansion**. "You Tube" / "YouTube" never appears in the app name, icon or package id. | Owner decision 17 Nov 2026. Removes the trademark conflict instead of resolving it later. [Docs: `AGENTS.md`] |
| DR-007 | A **sandboxed in-app WebView is permitted for discovery only**, after the §2E threat model is written: HTTPS only, visible origin, no privileged JS bridge, no arbitrary intent launching, no DRM or key extraction. | Owner decision 17 Nov 2026. Still gated on the threat model before code. |
| DR-008 | **Engine stays APK-bundled only**; no runtime engine code download. Separately, the app will check **this project's GitHub Releases** on open, notify when a newer APK exists, and on the user's tap download it and hand it to the system installer. This is an APK update, not engine code loading. | Owner decision 17 Nov 2026 (OD-5). The self-update needs a permission decision (OD-8) because installing an APK is outside the current budget. |

## 2. Decisions the owner still has to take (each blocks the phase noted)

| Id | Question | Needed by | My recommendation |
|---|---|---|---|
| OD-3 | ABI policy. The build is arm64-only. **Owner deferred this to Phase 3** (17 Nov 2026). | 3 | Ship ABI splits (add armeabi-v7a) so 32-bit phones are not locked out. [Code: `app/build.gradle.kts` `abiFilters`] |
| OD-6 | Register with Google's developer verification? **Owner deferred ("decide later", 17 Nov 2026).** | 3 | See §4.4. Decide before the first public APK. |
| OD-7 | Two unrelated policies: (a) adult-site shortcuts (porn sites) and (b) saving **photo posts** from Instagram/Facebook/X (the desktop app does this). These three are ordinary social sites, not adult sites. | 2C | No promoted adult shortcuts. Photo posts out of scope unless a non-yt-dlp tool is approved. |
| OD-8 | May the permission budget add `REQUEST_INSTALL_PACKAGES` (plus a `FileProvider`) so the in-app updater in DR-008 can open the system installer for a downloaded APK? | 2G | Add it, scoped to the updater only; the installer still shows the system confirmation. Without it the updater can only open the release page in a browser. |

---

## 3. Starting position (evidence)

### 3.1 What was examined, and its limits
- First draft: uploaded zips of both repos read 17 Nov 2026 (Android then 64 Kotlin files, about 9,000 lines, 34 main + 30 test files; desktop 55 files).
- **Correction, same day:** the live working tree was reconciled against the zip (HEAD merge `33e14ad`). It now has **45 main + 40 test Kotlin files** and the delivered Phase 1 queue, so the zip was older than the working tree. §3.2 records the real state.
- Owner research ZIPs for Snaptube 7.67.0 and 1DM+ 20.0: only the written reports were read, **not** the decompiled code. Both are static analyses with no runtime verification. [Report]
- The pasted agent report (source stamp `56c15c4`: 595,245,874-byte 1080p MP4 saved, 7/7 self-test) matches the live tree: Room queue, `?si=` history records, private by default. No discrepancy remains after §3.2.
- I could not open the GitHub repos directly (automated access is blocked). Facts below come from the zips plus the reconciled working tree.

### 3.2 Android app, verified against the working tree (17 Nov 2026, HEAD merge `33e14ad`)
| Area | Finding | Evidence |
|---|---|---|
| Stack | Compose + Material 3, single activity. Coroutines, Compose, `youtubedl-android` 0.18.1 (library + ffmpeg), and **Room 2.8.5 with KSP 2.3.14 are delivered** (not just planned). DataStore/WorkManager are still only listed. | `app/build.gradle.kts`, `gradle/libs.versions.toml` |
| Engine boundary | `Engine` interface: `init`, `info`, `resolve`, `resolveForTask`, `download`, `cancel`. Resolve handles one video; `MediaInfo` says "Phase 0: no playlists". | `core/Engine.kt` |
| Download request | Fields: url, max height, output dir, process id, merge container, resolved snapshot, quality plan, audio plan. **No** cookies, trim, subtitles, playlist items, speed or compat-mode fields. | `core/Engine.kt` |
| Hard-coded options | `--no-playlist`, `-N 4` (`CONCURRENT_FRAGMENTS = 4`), `--windows-filenames`, `--no-mtime`, HTTPS-only source check. | `engine/DownloadExecutor.kt`, `engine/EngineExecution.kt` |
| Output verification | After exit code 0 it still requires printed evidence of the selected format ids, dimensions and file extension, else the task fails. | `engine/DownloadExecutor.kt` |
| Errors | 17-code taxonomy (`ErrorCode`) mirrors `contracts/errors.json`; each code has a stage and a retryable flag. | `core/Engine.kt`, `contracts/errors.json` |
| Durable queue | Room `TaskDatabase` v1 (schema exported), `TaskEntity` + `RoomTaskStore`, `TaskRepository` with a `TaskPhase` state machine, revision fencing, process-once recovery, publication-uncertainty state, and a source-privacy filter (a `?si=` link becomes a review record, not a stored URL). | `download/RoomTaskStore.kt`, `download/TaskRepository.kt` |
| Storage | `StorageBudget` pre-flight free-space check and an actual-byte check around publication. | `core/StorageBudget.kt` |
| Cancel | `Engine.cancel` is documented best-effort; real cancel cleanup is unverified on device. | `core/Engine.kt`, `docs/STATE.md` |
| Publishing | Copies the finished file into MediaStore `Download/YTDLP` with `IS_PENDING`; on exception it deletes the row. API 24-28 falls back to a narrow content-URI grant, not a gallery-visible folder. | `publish/MediaStorePublisher.kt` |
| Manifest | Permissions: INTERNET, FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC, POST_NOTIFICATIONS. **ACCESS_NETWORK_STATE is in the budget but not declared.** Only a `SEND text/plain` share filter. `allowBackup=false`, cleartext off. | `AndroidManifest.xml` |
| State holder | `HomeController` is a plain class, not an AndroidX ViewModel. Survival across configuration changes relies on `configChanges` plus `SharedSessionOwner`; the queue UI is `feature/home/TaskQueue.kt`. | `feature/home/HomeController.kt`, `feature/home/TaskQueue.kt` |
| Build | Release has `isMinifyEnabled = false`. arm64-only. minSdk 24, target/compile 37. versionName `0.1.0-phase1-preview`. | `app/build.gradle.kts` |
| Tests | 40 test files. Last recorded helper run (16 Nov, not re-run after the docs merge): 338 total, 337 passed, 1 Windows symlink skip, lint 0 errors / 7 warnings. | `docs/STATE.md`, `docs/PROGRESS.md` |
| Sites | All 9 sites in `contracts/sites.json` have `android_status: "unknown"`. | `contracts/sites.json` |

### 3.3 Device evidence (owner-reported)
- Passed: engine spike on an Infinix X682B (Android 10, arm64): 7/7 self-test; `299+140` 1080p merge saved; 314,296,147-byte save with no 403. [Docs: `docs/evidence/*`, `docs/STATE.md`]
- Passed later: `74ce7cf` saved a 1080×1920 `137+140` MP4 (7,737,267 bytes) with the resolve replay reused; `e37686c` saved WebM/Opus `251-drc` (24,965,341 bytes) and 720p `136+140` (108,448,544 bytes) with no recorded 403. [Docs: `docs/evidence/phase1-owner-feedback.md`]
- Latest pasted report (`56c15c4`): 595,245,874-byte 1080p MP4 saved; engine 211.3 s including merge; publication 5.9 s; fresh format lookups 40.6 / 37.1 / 34.5 s; self-test 7/7. [Report: pasted agent session]
- **Failed earlier:** `6bb477b` hit NETWORK_OFFLINE and four media 403s; cause unproven. The `f96194e` engine refresh is not a general 403 fix. [Docs: `docs/evidence/foreground-6bb477b.md`]
- **Unproven:** sound on new files, sustained background/lock, Cancel cleanup, process-death recovery, second quality preset, QHD/4K MKV playback, light theme, TalkBack, other share targets. [Docs: `docs/STATE.md`]

### 3.4 Desktop app parity target
Features: 360p-4K, MP3/FLAC/WAV/M4A, compatibility mode (H.264/AAC), timestamp trim, playlists with ranges like `1-5, 8`, multi-connection speed (4x/8x), speed limit, per-site toggles, cookie capture, auto-recovering queue, hardened option allowlist, daily engine update, 9 sites. [Code: desktop `readme.md`, `CommandValidator.cs`]

Contract drift (quick string comparison, Appendix A): the desktop validator allows 14 options missing from the Android contract, including `--sponsorblock-remove`, `--download-archive`, `--force-keyframes-at-cuts`, `--write-thumbnail`, `-R`, `-i`, and the `--sleep-*` family. Android lists 59 options against desktop's 44; the Android-only ones are mostly engine-internal (`--load-info-json`, `--cookies`, `--print`, ...). Each difference needs an owner decision, not an automatic copy.

---

## 4. External landscape (research)

### 4.1 YouTube is hardening; plan for degradation, not perfection
- yt-dlp now warns that YouTube extraction without a JavaScript runtime is deprecated and formats may go missing. [Web: yt-dlp issues #15197, #15897, #17619, 2026]
- YouTube is forcing SABR streaming for some web clients and binding GVS PO tokens to video IDs; formats then lack URLs. Issue #17666 (about Sep 2026) reports `mweb` and `web_embedded` becoming SABR-only in some sessions except format 18. [Web: github.com/yt-dlp/yt-dlp/issues/17666, #12482]
- Android wrapper users hit the same walls: Seal 1.13.1 reports "No supported JavaScript runtime", HTTP 403, and "Sign in to confirm you're not a bot" on yt-dlp 2026.01.25 and 2026.06.13. [Web: github.com/JunkFood02/Seal/issues/2413, #2543]
- Seal's last commit is dated 25 Apr 2026 (low-confidence aggregator). [Web: goodfirstissue.org] **Implication:** the engine wrapper is the product's single point of failure; keep the `Engine` boundary and a contingency plan (2F).
- Desktop lessons already paid for: forcing `web_safari`/`mweb` capped quality at 360p; cookie files lacking `SAPISID` / `__Secure-3PAPISID` are treated as logged out. [Docs: `docs/08`]
- **Strategic consequence:** YouTube will always be the shakiest site. Multi-site breadth (2C) is the resilience and the differentiator.

### 4.2 What Snaptube teaches (from the written reports, behaviour only) [Report; Docs: `docs/03`, `docs/15`, `docs/16`]
Adopt:
- Source tabs + prominent link field + persistent bottom navigation; per-item download arrow; a separate saved-files list with thumbnails, size, duration, search, filter, delete; contextual "how to start" cards per source; local clearable search history.
- Typed post-processing jobs (it has eight FFmpeg task kinds); separate task **status** (coarse), **failure reason** (user-facing) and **internal fault** (logs).
- Pre-flight disk check that produces a warning, not a failure; "waiting for dependency" as a first-class state.
- A locally derived extraction-health signal (it uses a server; we have none, so derive from recent outcomes).
- Format objects carrying expiry, so retries re-resolve instead of reusing stale URLs.

Reject: ad mediation, remote-updatable extractor JS, 34 permissions (install packages, all-files, overlay, usage stats), cleartext HTTP, cleaner/vault/booster, persistent-process tricks, clipboard monitoring in the background.

### 4.3 What 1DM+ teaches (static analysis, unexecuted) [Report: `download-profile-final.md`, `runtime-validation-matrix.md`]
- **No single signal proves success.** It separates durable admission, payload writes, accepted sync, per-part checkpoints, aggregate progress, processing and publication. Our evidence-based output check (`DownloadExecutor`) already follows this; extend it to publication.
- Three retry layers (part-local, deferred part, whole-download) stay separate; a counter increment is not proof of a new network attempt. Count and log each layer separately.
- 429/509 raises the delay floor linearly (10 s x clamp(retries, 1, 30)); it is **not** exponential backoff. We should use capped exponential with jitter [I] and never hammer a bot gate.
- Resume is validated by byte ranges, but the report found no ETag/If-Range checks: **same-entity resume is not established**. For us: yt-dlp `.part` continuation must be accepted only when format id and size still match; otherwise restart and say so.
- Its report flags selected TLS code that appears not to validate certificates, and an HTTPS-to-HTTP fallback. These are source-derived concerns, not verified vulnerabilities. We must never copy that pattern.
- Its proposed (not executed) test matrix is a good checklist: process kill between each stage, cancel during each stage, late callbacks after replacement, partial writes. We reuse the idea in 2B.

### 4.4 Android platform facts that change the plan
- `dataSync` foreground services get a time limit on API 35+ (6 h per 24 h, then `onTimeout` must stop the service). [Docs: `docs/06` §1, citing developer.android.com]
- **Android 16:** background jobs started from a foreground service must obey job quotas, including WorkManager and DownloadManager. UIDT jobs are exempt. [Web: developer.android.com/develop/background-work/services/fgs/changes]
- UIDT (Android 14+, `JobScheduler.setUserInitiated`) suits user-started, long, notified downloads; Google reports a 10%+ drop in offline-download failures for Maps after migrating. [Web: Android Developers Blog, Sep 2024] It requires a permission outside our budget, so it is not adopted (DR-005).
- **Developer verification:** enforcement began 30 Sep 2026 in Brazil, Indonesia, Singapore and Thailand; global rollout is planned for 2027. Developers outside Google Play register an account, verify identity (reported: ID and a one-time fee) and register the package name. Sideloading has an "advanced flow" with a waiting period; ADB installs are exempt. [Web: pinggy.io 24 Sep 2026; nihardaily.com; consumerrights.wiki. Secondary sources; verify on Google's own page before release]
- **Publisher disk cost:** `MediaStorePublisher` stream-copies the file, so publication needs free space equal to the file size on top of staging. During merge, parts, merged output and the published copy can coexist, so peak use may exceed 2x [I; measure in 2B].
- **Pending-row hazard:** if the process dies mid-copy, the `IS_PENDING=1` MediaStore row is not deleted by the catch block [I; verify]. A startup sweep is needed.

---

## 5. Architecture and coding practices (applied rules)

1. **Room is the single source of truth.** Every state transition is written first, then acted on; UI and notifications observe it. Stages are idempotent and re-runnable from persisted inputs. [Docs: `docs/02` §2]
2. **Three-level failure model:** `Stage` + `ErrorCode` (user-facing, drives the recovery button) + local log (internal fault). Add codes only through `contracts/errors.json` with tests.
3. **Typed jobs, never free-form commands:** resolve, download, merge, extract-audio, convert, trim, subtitle, publish. Each has its own validator, retry rule and test fixtures.
4. **Default-deny option allowlist:** every new yt-dlp flag enters `contracts/allowed-options.json` and the validator in the same commit, with a test. Output paths stay app-controlled.
5. **Evidence over exit codes:** a job succeeds only when printed or probed evidence matches the request (format id, size, extension, file exists, publication verified).
6. **Secrets never persist:** cookies live in a task-private file deleted at task end; the redactor covers cookies, auth headers, PO tokens and signed-URL queries; unit tests stay green. [Docs: `AGENTS.md`]
7. **Re-resolve, never reuse expired URLs.** A 403 on media triggers one bounded re-resolve, not blind retries.
8. **Compose hygiene:** unidirectional data flow, state hoisting, no business logic in composables, previews for each state, 48 dp touch targets, content descriptions, dark and light themes, large-text checks.
9. **Honest UI language:** "Retry from start" is not "Resume"; "unsupported" is not "untested"; a finished file says what was actually selected.
10. **Build discipline:** version catalog only; enable R8 for release with keep rules for the engine library before 1.0; baseline profile and macrobenchmark in Phase 3; CI runs tests and lint; **live-site tests are never a required check**.
11. **Fake engine first:** new logic is tested with `FakeEngine` and command-construction fixtures; the phone is for what automation cannot prove.
12. **One plan, one implementation per feature:** before adding a subsystem (queue, picker, storage, retry), check whether the working
    tree already has it; never build a second parallel version from an old snapshot. `origin/phase/R` did exactly that and produced
    seven merge conflicts plus duplicate `TaskEntity`/`TaskRepository`/`@Database` classes. Reconcile by diff review, then delete the loser.
13. **Stage vocabulary is a migration contract:** `TaskPhase` is persisted by name, so add values (`MERGING`, `POST_PROCESSING` before
    Phase 2D conversion/trim) only with a schema/migration note and tests. Model recovery as explicit resumable targets; anything
    uncertain after process death fails closed and asks the user, it never silently resumes or republishes.

---

## 6. Phases

Rough size is in AI sessions (one `/next` loop each) and is an inference [I], not a commitment.

### Phase R: Reconcile and tidy (host-only; 0-1 sessions left, mostly done 17 Nov 2026)
- **Goal:** make this roadmap's baseline true and leave a phase-spec scaffold.
- **Already done (17 Nov 2026):** §3.1/§3.2 corrected against the live tree; upstream merged (`33e14ad`); the stale `origin/phase/R`
  branch was tested and rejected (see §7). Room, revision fencing, recovery, storage preflight and publication safety are delivered.
- **Remaining steps:** (1) record a fresh `testDebugUnitTest lintDebug` count after any code change; (2) trim `docs/STATE.md` to about
  60 lines, moving history to `docs/PROGRESS.md`; (3) create `docs/phases/` with the §0 template; (4) produce the allowlist
  reconciliation table (Appendix A) with a recommendation per option; (5) fix the stale diagnostics header (it says runtimes are missing
  while later checks find them) if still present; (6) note that `TaskPhase` lacks `MERGING`/`POST_PROCESSING` before Phase 2D needs them.
- **Exit:** `docs/phases/R-report.md`; tests/lint counts quoted; no feature code.
- **Stop:** owner answers OD-3, OD-6, OD-7 and OD-8 as far as they block later phases. OD-1 to OD-5 are decided (DR-005 to DR-008).

### Phase 1: MVP acceptance close-out (owner-assisted; 2-3 sessions)
- **Goal:** turn "implemented" into "accepted" before adding features.
- **Device matrix** (owner runs; agent prepares the checklist and reads logs): sound on a new video and an original-audio file; a second quality preset with the correct dimensions; a long download (the earlier failing ~528 and ~873 MiB samples) while backgrounded and screen-locked; Cancel mid-download (no partial file, no orphan process, service gone); process kill mid-download (queue state sane, no duplicate save); low-space behaviour; share from Chrome, Instagram, Facebook, X, TikTok, Reddit apps; files visible in Gallery/Files; light theme and large text.
- **Fix only** defects the matrix finds, each with a regression test.
- **Exit:** `docs/evidence/phase1-acceptance.md` with pass / fail / untested per row, app version stamp, yt-dlp version, device.
- **Stop:** failures with unclear cause go to a research note, not a speculative fix.

### Phase 2A: Snaptube-style shell (3-4 sessions)
- **Goal:** the app feels like a downloader product, not a diagnostics tool.
- **Scope:**
  - Three destinations: **Home** (link field, foreground-only "paste link" chip, source-shortcut grid from `sites.json`, recent items), **Downloads** (active, queued, failed task cards: progress, size, format, contextual actions), **Library** (saved files with thumbnail, size, duration, search, filter, open, share, clearly scoped delete).
  - Source shortcuts open the site in the user's browser or app with a short "how to share to YTDLP" card. **No WebView in this phase.**
  - Share intake: validate, then canonicalise recognised public video links host by host (for example drop YouTube's `si`), keeping the source reviewable after restart. Do not strip unknown sites' parameters, and do not retain arbitrary queries blindly.
  - Move warnings and disclaimers into task details and contextual help; keep the protections.
  - Library reads own MediaStore entries; verify on a device what is visible after reinstall `[?]`.
- **Out:** browser, login, playlists, new post-processing.
- **Tests:** presenter/state tests, canonicalisation fixtures (tracking parameters, redirects, malformed links), screenshot set (dark, light, large text) for owner review.
- **Exit:** tests/lint green; screenshots approved by owner; no new permission.

### Phase 2B: Download manager hardening (4-5 sessions)
- **Goal:** a queue the user controls and can trust.
- **Scope:**
  - Explicit queue actions (start, reorder, cancel one or all); concurrency default 1, raised only after measurement; separate cap for post-processing.
  - Network and storage constraints: Wi-Fi only, with `ACCESS_NETWORK_STATE` added (already in the budget, missing from the manifest [Code]).
  - Bounded retry layers (yt-dlp request retries, whole-task retry with one re-resolve, user retry) counted separately; capped exponential backoff with jitter; no retry loops on bot gates or HTTP 429.
  - Space preflight that counts staging, merge outputs and the published copy; delete staging right after verified publication.
  - Startup sweep for orphaned `IS_PENDING` rows and staging directories.
  - Resume honesty: continue `.part` files only when format id and size match; otherwise restart and label it "Retry from start".
  - **Execution owner is fixed (DR-005):** keep the foreground service. Do not add UIDT or WorkManager unless a measured failure reopens OD-1.
  - Make `-N` a setting with a battery-aware default.
- **Tests:** fake-engine interruption and race tests (cancel during each stage, process death between stages, duplicate share, late callbacks); malformed partial state rejected; no duplicate save.
- **Measure and record:** extraction time, transfer speed, battery, peak disk use, on the Android 10 phone and one Android 14+ device.
- **Exit:** race tests green; measurement table; owner device checks for background, lock, cancel and kill added to the matrix.

### Phase 2C: Multi-site validation (3-4 sessions, owner supplies sample links)
- **Goal:** replace "unknown" with evidence for every platform the product claims.
- **Steps:**
  - For each of the 9 sites (YouTube, Instagram, Facebook, X, TikTok, Reddit, Twitch, Vimeo, SoundCloud): one sample per content type, a device run, the outcome, error codes, the yt-dlp version and the date, recorded in `contracts/sites.json` as verified / partial / failing / untested. Never commit personal URLs or account data.
  - Per-site hints driven by error codes (Vimeo needs login; Instagram and Facebook work better with login) [Docs: `docs/08`].
  - UI labels "Tested" versus "Best effort": any valid https link is attempted, but only tested sites are promoted.
  - YouTube degradation path: on `FORMATS_WITHHELD`, say so, show what is actually available, and offer the highest real option. Do not hard-code client lists or format ids; use what the resolve returns.
  - Candidate additions from Snaptube's catalogue behaviour (about 48 sites in its default list [Report]) are evaluated one at a time by evidence, never bulk-added.
- **Out:** login flows, browser.
- **Exit:** every claimed site has dated evidence; a non-blocking, owner-run canary checklist exists; README site table generated from `sites.json`.

### Phase 2D: Desktop-parity media tools (four sub-phases; each ends with a stop)
Each sub-phase needs: `DownloadRequest` fields, allowlist entries, validator tests, command-construction fixtures and a device check; and, where the task changes shape (convert, trim), a `TaskPhase` addition with a migration note and recovery test.
- **2D-1 Audio and compatibility (3 sessions):** MP3, M4A, FLAC, WAV conversion as a typed job (today only original audio exists [Code: `core/AudioFormats.kt`]); compatibility mode (H.264/AAC) as a preference that never silently substitutes an exact selection; speed limit setting.
- **2D-2 Playlists and batch (3-4 sessions):** extend `Engine` with bounded playlist discovery (entry cap), selection by ranges in the desktop syntax (`1-5, 8`), per-item outcomes, explicit batch confirmation. `--no-playlist` stays the default for single links.
- **2D-3 Trim and subtitles (3 sessions):** validated start/end ranges via `--download-sections`; say clearly when cuts are keyframe-approximate versus accurate (`--force-keyframes-at-cuts` is a desktop-only option today); subtitles with language selection, manual versus automatic labels, sidecar first, embedding where the bundled FFmpeg supports it.
- **2D-4 Save location and history (2 sessions):** optional user-picked folder through the Storage Access Framework with a persisted permission; skip-duplicate behaviour (desktop's `--download-archive`); metadata/thumbnail embedding stays deferred until capability-tested.
- **Exit per sub-phase:** unit/fixture tests green; unsupported combinations explained in the UI; device check for sound, A/V sync, subtitles, container playback.

### Phase 2E: Browser-assisted discovery and consented sessions (gated; 4-6 sessions)
- **Gate:** the written §2E threat model required by DR-007 (untrusted pages, no privileged JS bridge, no arbitrary intent launching, HTTPS only, no insecure TLS).
- **Scope:** in-app browser with source shortcuts, visible origin, back/forward, loading and error states; "Download this page" through the existing engine. Media-candidate detection (direct, HLS, DASH) only after the basics work; no DRM or key extraction.
- **Sessions:** explicit consent and a clear-session action; task-private cookie file deleted after the task; session change invalidates resolved state; cookies never in Room, history or logs. Desktop rules carry over: needed login cookies present, analytics/consent cookies excluded. [Docs: `docs/08`, `docs/16`]
- **Limits to state in the UI:** embedded Google sign-in may be rejected and a login does not remove bot checks `[?: confirm against Google and yt-dlp guidance at implementation time]`.
- **Exit:** controlled fixtures for redirects and cookies pass; real website sign-in is owner-operated and recorded as site-dependent.

### Phase 2F: Engine freshness and resilience (3-4 sessions)
- **Goal:** survive YouTube changes without runtime code downloads.
- **Scope:**
  - Written, scripted procedure for an APK-bundled refresh of yt-dlp and EJS with checksum verification (already done once for yt-dlp 2026.10.26 and EJS 0.8.0 [Docs: `docs/evidence/engine-refresh-2026.10.26.md`]); previous APK is the rollback.
  - Map yt-dlp warnings ("Signature solving failed", "No supported JavaScript runtime", SABR/PO-token messages) to `ENGINE_OUTDATED`, `JS_RUNTIME_MISSING`, `FORMATS_WITHHELD`; after repeated recent failures, prompt the user to update the app (health signal from local outcomes only).
  - Separate research spike on PO-token providers (WebView-based precedent exists in NewPipe PR #11955 [Docs: `docs/01-PLAN.md`]) with its own security gate; login is not a substitute.
  - Contingency for the wrapper dependency: track `youtubedl-android` releases, keep a vendoring or fork plan, keep the `Engine` boundary clean.
- **Not included:** runtime updater, remote plugins, client roulette.
- **Exit:** the refresh runbook succeeds from a clean checkout; error mapping covered by fixtures built from real warning text; documented limitation if a spike fails.

### Phase 2G: App self-update (gated by OD-8; 1-2 sessions)
- **Goal:** tell the user when a newer YTDLP APK exists and let them install it without opening a browser.
- **Scope:**
  - At app open, and at most once per day, query **this project's** GitHub Releases over HTTPS and compare `versionCode`/`versionName`.
    No analytics, no device identifiers, no account. A failed check is silent and never blocks the app.
  - Show a non-blocking notice with the new version and a release-notes link. Never auto-install and never nag on every launch.
  - On the user's tap: download the APK, verify its published SHA-256 and the expected signer, then hand it to the system installer
    through a `FileProvider` and `ACTION_VIEW`. Requires `REQUEST_INSTALL_PACKAGES` (OD-8); without it, open the release page instead.
  - Fail closed: hash mismatch, wrong signer or unknown version deletes the file and explains why; nothing is installed.
- **Not included:** runtime engine updates (DR-008), silent install, Play in-app updates, background update checks.
- **Tests:** fake releases endpoint, version comparison, hash mismatch, offline, permission denied, and a "no update" path.
- **Exit:** unit tests green; owner device check of download → verify → install on Android 10 and one Android 14+ device.

### Phase 3: Release (4-5 sessions)
- Accessibility (TalkBack, large text, contrast, touch targets), light/dark, localisation readiness.
- R8 on with keep rules; baseline profile and macrobenchmark; startup and battery numbers recorded.
- ABI splits (OD-3); APK size budget set from measurements.
- Signed release with published SHA-256, source correspondence, `THIRD_PARTY_NOTICES`, GPL notices.
- Name clearance is closed by DR-006 (YTDLP only, no expansion); confirm no "YouTube" text remains. Decision on developer verification and package registration (OD-6; the package id `io.github.muzammilawan.clipharbor` stays so installs update in place); distribution metadata for IzzyOnDroid and F-Droid; privacy statement and legal notice matching the desktop text.
- Final device matrix: Android 10 phone, one Android 14+ device, video and audio with sound, MP4/WebM/MKV playback, background/lock/cancel/kill/network loss, every shipped feature.
- **Exit:** the Definition of Done in §8. Deferring device checks never counts as passing.

### Phase 4: Optional, only after 1.0
Local media preview (Media3), tablet layouts, proven faster-transfer modes, native search, and, if the owner revisits it, streaming (revisit DR-000 then).

---

## 7. Risk register (ranked)

| # | Risk | Likelihood | Impact | Mitigation | Phase |
|---|---|---|---|---|---|
| 1 | YouTube hardening (SABR, PO tokens, JS runtime) limits formats or breaks extraction | High | Core | Multi-site breadth, honest degradation, bundled engine refresh, PO-token spike | 2C, 2F |
| 2 | Wrapper library or upstream stagnates | Medium | Engine stuck | `Engine` boundary, vendoring plan | 2F |
| 3 | Background/lock/kill behaviour unproven; earlier 403s unexplained | High | Lost downloads | Acceptance matrix, race tests, UIDT spike | 1, 2B |
| 4 | Disk exhaustion from stage + merge + publish copies | Medium | Failures, junk files | Preflight, immediate staging cleanup, startup sweep | 2B |
| 5 | Allowlist drift versus desktop; unsafe options added ad hoc | Medium | Security | Reconcile table, tests per flag | R, 2D |
| 6 | Developer verification limits installs outside Google Play | Medium | Reach | Early decision on registration | 3 |
| 7 | Legal and ToS exposure (downloading may violate site terms) | Certain, varies | Distribution | Legal notice, no endorsement claims, no DRM bypass, not on Play | 3 |
| 8 | Name and trademark conflicts | Medium | Rebrand | DR-006: YTDLP only, no expansion; confirm at release | 3 |
| 9 | AI context overload / drift | Medium | Wrong code | One phase per session, short STATE, evidence tags | all |
| 10 | arm64-only build excludes 32-bit phones | Medium | Reach | ABI splits | 3 |
| 11 | Parallel agent work re-implements a delivered subsystem from a stale snapshot (`phase/R` queue) | High while multiple agents run | Rework, duplicate classes, merge conflicts | Rule §5.12: diff before building, one plan of record, reconcile branches before merge | R, all |
| 12 | App self-update installs a tampered or wrong APK | Low | Severe | HTTPS, published SHA-256 and signer check, fail closed, system installer confirmation, no silent install | 2G |

## 8. Definition of done for 1.0

All Phase 1 to 3 gates passed with evidence; each shipped feature verified on at least two real devices (one Android 14 or newer); CI green; signed release with checksums; license report clean; README states the legal notice and known limitations; `sites.json` statuses are dated and truthful; no secret persists in Room, history or logs (tests prove it).

---

## Appendix A: allowlist comparison (string-level, needs human review)

Desktop validator allows but the Android contract lacks (14): `--continue`, `--download-archive`, `--force-keyframes-at-cuts`, `--ignore-errors`, `--max-sleep-interval`, `--no-overwrites`, `--restrict-filenames`, `--sleep-interval`, `--sleep-requests`, `--sleep-subtitles`, `--sponsorblock-remove`, `--write-thumbnail`, `-R`, `-i`.
Android contract only (engine-internal, mostly): `--batch-file`, `--cache-dir`, `--clean-info-json`, `--config-locations`, `--cookies`, `--downloader`, `--dump-json`, `--exec`, `--exec-before-download`, `--external-downloader-args`, `--extractor-args`, `--ffmpeg-location`, `--ignore-config`, `--js-runtimes`, `--load-info-json`, `--newline`, `--no-cache-dir`, `--no-clean-info-json`, `--no-clean-infojson`, `--no-simulate`, `--output`, `--paths`, `--plugin-dirs`, `--print`, `--remux-video`, `--user-agent`, `-P`, `-r`, `-v`.
Action in Phase R: check whether any listed Android-only entry sits in the user-facing allowlist rather than the `blocked_always` or `engine_internal_options` sections (for example `--exec` and `--plugin-dirs` should never be user-reachable).

**Checked 17 Nov 2026 [Code: `contracts/allowed-options.json` v3]:** the dangerous Android-only entries are already in `blocked_always`
(`--exec`, `--exec-before-download`, `--batch-file`, `--config-locations`, `--plugin-dirs`, `--load-info-json`, `--ffmpeg-location`,
`--cache-dir`, `--no-clean-info-json*`). `--load-info-json` is also app-set in `engine_internal_options`, which is the intended split.

**Recommendation for the 14 desktop-only options (owner decides, not an automatic copy):**
- Add to `engine_internal_options` only (app sets it, never an intent): `--continue`, `--no-overwrites`, `--restrict-filenames`, `-R`
  (alias of `--retries`), `--sleep-*` and `--max-sleep-interval` (rate limiting is engine policy, a user-supplied sleep value is a DoS knob).
- Add with a typed job in Phase 2D: `--download-archive` (2D-4 duplicate skip), `--force-keyframes-at-cuts` (2D-3, labelled approximate
  vs accurate), `--sponsorblock-remove` (2D setting, never free text), `--write-thumbnail` (2D-4, deferred until capability-tested).
- Do not add: `--ignore-errors` / `-i` (masks per-item failure; our evidence-over-exit-code rule depends on seeing the error).

## Appendix B: sources
- LibreTube repo and build file: github.com/libre-tube/LibreTube (read 17 Nov 2026).
- yt-dlp issues: #12482 (SABR), #15197, #15897, #17616, #17619, #17666 (2026).
- Seal issues #2413, #2543; Seal repository page.
- Android: developer.android.com/develop/background-work/services/fgs/changes; developer.android.com/about/versions/15/changes/datasync-migration; Android Developers Blog (UIDT, Sep 2024).
- Developer verification: pinggy.io (24 Sep 2026), nihardaily.com, consumerrights.wiki, f-droid.org open letter (24 Feb 2026).
- Owner material: both repo zips, Snaptube 7.67.0 reports, 1DM+ 20.0 reports, pasted agent report.

## Appendix C: not verified
Device behaviour of anything in §3.3 "Unproven"; Library visibility after reinstall; leftover `IS_PENDING` rows after process death; peak disk multiple during merge; UIDT behaviour with a Python subprocess; the exact developer-verification requirements for the owner's region; Snaptube and 1DM+ runtime behaviour (static analysis only). The repo state in §3.2 was verified on 17 Nov 2026 but must be re-checked (`git log -1`, test counts) before each phase.
