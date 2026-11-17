# STATE: read this first (kept short and current)

Loaded automatically through `opencode.jsonc` `instructions`. Keep it SHORT. Long history lives in `docs/PROGRESS.md`
(append-only: `grep`/`tail` it, never read it whole) and `docs/RESEARCH-LOG.md` (look up by `R<number>`).
Update this file at the end of every step (`/handoff`). Facts here come from `docs/PROGRESS.md` and `docs/RESEARCH-LOG.md` (up to R80);
anything not re-run since is marked untested. Run `git log -5 --oneline` for the real HEAD.

## Where we are
- Product: YTDLP, an unofficial, free, GPL-3.0 Android front end for yt-dlp (package `io.github.muzammilawan.clipharbor`).
  Desktop sibling: `muzammilAwan-dev/yt-downloader-pro` (MIT), the parity target.
- Phase 0 (engine spike): **PASSED** 15 Nov 2026 on Infinix X682B (Android 10, API 29, arm64). Evidence: `docs/evidence/phase0.md`.
- **Phase 1 (MVP): approved implementation/host delivery complete; Android acceptance deferred, NOT PASSED.**
  Compact video/original-audio/exact picker, private resolve reuse/timings, Room queue/history, foreground ownership, recovery/storage,
  publication safety and full-session logs are host-tested. No Phase 2 work authorized. Report: `docs/evidence/phase1-delivery.md`.
- **Phase 2A (three-destination shell): implementation + host checks delivered 17 Nov; screenshots and device checks deferred, NOT PASSED.**
  Home (link, paste, 9 source shortcuts, recent saves, contextual help) / Downloads (queue card, live percentage) / Library
  (own MediaStore rows, search, kind filter, duration, open/share/confirmed delete); share links canonicalised per recognised host
  (unknown hosts and signed URLs untouched); `FULL_NAME` removed. No browser/login/playlist/new post-processing, no new permission.
  Report: `docs/evidence/phase2a-delivery.md`. **STOP for the owner's `next` before Phase 2B.**
- **17 Nov: upstream merged** (`origin/main` 7bde305, merge `33e14ad`): new owner roadmap `docs/ROADMAP.md` (phases R, 1, 2A-2F, 3, 4),
  DeepSeek build workflow `docs/DEEPSEEK_WORKFLOW.md` + `docs/DS_QUICKSTART.md`, `tools/ds_bridge.py`, `tools/ds/MASTER_PROMPT.txt`, `tools/ds.bat`.
  `docs/ROADMAP.md` §3.2 predates our Phase 1 delivery (it still calls Room/queue absent); its own Phase R says to correct that.
- `origin/phase/R` **NOT merged**: tested, 7 conflicts. It re-implements the queue from that stale snapshot (raw URL, autoincrement id,
  no revision fencing/recovery/privacy filter) and duplicates `TaskEntity`/`TaskRepository`/a second `@Database` in the same package.
  Only its `_ds/` gitignore hunk was kept. `main` stays ahead; nothing pushed.
- **17 Nov owner decisions (plan-level, no feature code):** foreground service stays (DR-005); name YTDLP only, no expansion (DR-006);
  sandboxed WebView allowed after a written threat model (DR-007); engine APK-bundled only, plus an app APK self-update check against
  this repo's Releases (DR-008) with `REQUEST_INSTALL_PACKAGES` scoped to the updater (DR-009); ABI and developer verification deferred;
  no promoted adult shortcuts. Photo posts need a Phase 2H spike because yt-dlp does not support images (R81).
  Implementation of Phases 2A-2H still needs the owner's `next`.
- **Current delivered APK (Phase 2A):** `dist/app-debug.apk`, full DEX stamp `91054d2`, 70,747,776 bytes,
  SHA-256 `B2D0CC9E6F7AE8E8304456FB6F220E93E62405E16448AD6051595270FB91AF9C`; build/copy/SHA256SUMS agree,
  V2 signer `CN=Android Debug`. Permissions unchanged (INTERNET, FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC,
  POST_NOTIFICATIONS + AndroidX receiver permission); min24 / target37. Device NOT_RUN.
- **Previous delivered APK (Phase 1):** full DEX stamp `56c15c4dc76a579c400e7b8a084dbb408eda299b`, 70,620,367 bytes.
  SHA-256 `71BE20C713B902B57E3FAA4DA9F904EEEEFB023175BC6D928CB27D8BE38ED233`; built/copy/SHA256SUMS agree, owner signer verified.
  yt-dlp 2026.10.26 / EJS0.8.0, library0.18.1; all 2 assets/notices, 7 native and 3 icon entries match f96194e. Manifest adds only
  Room's non-exported internal invalidation service (R80).
- Latest owner-tested artifact: **`e37686c`** — source-backed video + original audio. Owner log confirms WebM/Opus `251-drc`
  (24,965,341 bytes) and 720p `136+140-3` (1280×720, 108,448,544 bytes) saved without recorded 403. Sound/visibility unconfirmed.
  Four screenshots show 22 audio choices before video; resolve took 42,545/38,261 ms. Evidence: `docs/evidence/phase1-owner-feedback.md`.
- Owner approved full Phase 1 delivery in tested internal increments (16 Nov): compact picker, resolve performance, durable queue,
  errors/retry/storage and remaining MVP wiring, then verified stamped APK/report and STOP for `next`. Device tests deferred until
  the full project is built, never waived/passed. No new installation request now; Phase 2 and runtime-code download remain gated.
- Owner-approved lightweight phone-free helper: `python -I tools/phone-free.py` (Windows, existing stdlib/SDK/JDK/cached Gradle only).
  Fresh host checks, stamped/source/signature/assets checks and offline selectors passed; removes its own unchanged temp copy, retains local reports.
  No APK rebuild/replacement, phone connection, emulator, dependency install or device-verification claim. Usage/removal: `docs/13-LOCAL-BUILD-AND-INSTALL.md`.
- Previous owner-installed tester: **`f96194e`** (APK-only engine refresh), artifact stamp/asset/signature/copied SHA-256 verified.
  Owner log 16 Nov confirms Android 10 startup and one completed download; this is not a general 403 fix. Helper checks: BUILD SUCCESSFUL in 2m 3s, 251 pass / 1 skip;
  lint 0 errors / 7 known warnings; APK BUILD SUCCESSFUL in 32s. Tester hash/phone sequence: `docs/evidence/engine-refresh-2026.10.26.md`.
- Owner evidence for `74ce7cf` (R61): dark portrait standard picker with disabled QHD/4K; 1080×1920 `137+140` download,
  resolve replay reused, MP4 merge and 7,737,267-byte MediaStore save; later self-test 7/7. Only one quality download.
- Still unverified: second preset, new file playback with sound, QHD/4K MKV playback; earlier `ec146df` playback remains confirmed.
- `f96194e` owner session: yt-dlp 2026.10.26 / EJS 0.8.0 local solver / Python 3.12.11 / QuickJS 2025-04-26 / ffmpeg 7.1.1 ran.
  56 formats resolved; replay-selected `299+140` at 1920×1080, 292.01 MiB video + 7.58 MiB audio, MP4 merge and 314,296,147-byte save; no 403.
  Transfer continued during ~6.6 s Activity pause/stop; service destroyed only after publication. Self-test 6 pass / 1 no-URL skip.
  Open-file launch is recorded, not confirmed sound; failing 528/873 MiB samples and sustained background/lock still pending.
  Log has no full format inventory: possible 4K/1440p mismatch unproven; initial report header is stale, later runtime lines are authoritative.
- Foreground foundation: tested admission/progress/release and shared process-local session ownership with an independent scope.
  Tested Home timeout boundary: stop-service callback first, PAUSING through cleanup, then PAUSED(FGS_TIMEOUT); no automatic rescheduling.
  New task-only dataSync service wiring retains a session before admission, promotes before Engine work, and connects Android onTimeout.
  Task-scoped immutable notification Cancel retains service/session through cleanup; stale/repeated actions are no-ops, saving is fenced.
  Optional Android 13+ Enable notifications button never starts/resumes work or gates Download. Service loss cancels the actual task Job.
  `6bb477b` phone milestone **FAILED** (R69/R70): initial NETWORK_OFFLINE / two media 403s after backgrounding; two more media 403s
  with app continuously visible on owner-reported stable Wi-Fi. Each failure preceded service destruction; no recorded Cancel/timeout/restart.
  Four 403s near ~29 MiB fit three default HTTP chunks; cause/client unproven. Engine/replay/planner unchanged since working `74ce7cf`.
  Old-default-client 403 fixes exist upstream (R70); OWNER approved APK-bundled engine-only investigation with "ok go" (R72).
  R71 desktop example has explicit extraction bot gate/429; distinct from phone media 403s. Formats can resolve before media checks fail;
  exact phone cause remains unknown, and engine refresh is not guaranteed to fix sign-in/IP gates. No runtime updater/login work approved.
  Sustained background/lock, Cancel/newer APIs remain unverified; no durability/rescheduling. Evidence: `docs/evidence/foreground-6bb477b.md`.
- Full-session logging: fixed UI/lifecycle actions + available resolve/download diagnostics/progress/timings; emails/secrets redacted.
  Share snapshots the flushed startup-to-tap journal, not a rotating chunk; Clear/new process resets it. No silent truncation; R68 disk-space tradeoff.
  Owner supplied one complete file export (R69) and another session pasted in chat (R70); other native share targets remain unverified.
- R72 packaging investigation: official stable yt-dlp 2026.10.26 zipapp + EJS 0.8.0 verified against signed SHA-256; existing
  Python 3.12.11/QuickJS 2025-04-26 meet tagged requirements. Library remains 0.18.1; host-only offline smokes passed.
  Local installer foundation now has 22 offline tests: size/hash verification, synced staging, failure/interruption preservation,
  narrow cleanup/retry and cross-instance serialization. Official asset/notices now bundled unchanged; six offline payload tests pass.
  Stamped `f96194e` APK inspected: exact asset/full notices, startup class/commit field, seven native entries/binary manifest match `6bb477b`.
  R73 adapter wiring now uses Android Os.rename and one process-wide install-before-library-init gate; 18 startup tests pass.
  Failed/interrupted startup does not publish readiness; retry is allowed. A ready process never reinstalls over an active worker.
  Reports verified installed yt-dlp/EJS pin, not stale updater preferences; later verbose runtime versions remain observable.
  Owner-tested runtime/merge/save on one sample; old-file replacement without data clearing and broader reliability remain unconfirmed.
  Owner approved stale QuickJS/runtime-update documentation corrections; notice corrected. See `docs/evidence/engine-refresh-2026.10.26.md`.

## Final recorded checks — 17 Nov 2026 (Phase 2A)
`.\gradlew.bat testDebugUnitTest lintDebug --console=plain` → **BUILD SUCCESSFUL in 36s**;
**353 tests, 0 failures, 0 errors, 1 Windows symlink skip**; lint **0 errors / 7 known warnings** (count unchanged after
removing the new `UseKtx` warning). Stamped debug APK: DEX stamp `91054d2` present, `dist` copy and `SHA256SUMS.txt` agree.
These are host checks only: no Compose UI test dependency exists, so no screen behaviour, screenshot, gallery, thumbnail or
three-tab layout has been observed on a device.

## Final recorded checks — 16 Nov 2026
Fresh phone-free helper: **BUILD SUCCESSFUL in 3m 6s**, 338 total / 337 pass / 1 Windows symlink skip / 0 failures/errors;
lint 0 errors / 7 known warnings. Stamped offline APK: **BUILD SUCCESSFUL in 1m 45s**. 24 helper + 4 schema regressions and
96 standard-video / 9 original-audio / 20 actual Kotlin exact-video socket-blocked selectors passed; no media downloaded.
Report `C:\Users\Malku\AppData\Local\Temp\opencode\ytdlp-phone-free-o5y5fbwt\summary.md`: PASS_HOST_CHECKS_DEVICE_REQUIRED;
JSON device_verification=NOT_RUN, runtime_copy=REMOVED; directory contains only two reports. Source/signature/identity/budget/assets verified.
No live extraction speedup measured, no general 403 reliability/playback claim. No phone connection/emulator/tooling install/push.

### Verified final implementation increments (historical, superseded by fresh full check above)
Final evidence/task-scope hardening: **BUILD SUCCESSFUL in 1m 8s**, 338 total / 337 pass / 1 skip; lint 0 errors / 7 warnings.
4 added regressions pass: malformed later evidence clears IDs/dimensions, uncertain-save stage/share fences, recovered review guard,
and known URI retention through timeout. Service-loss regression now asserts paused uncertainty, not canceled/unsaved certainty.
Save/recovery fences: **BUILD SUCCESSFUL in 2m 17s**, 334 total / 333 pass / 1 skip; lint 0 errors / 7 warnings.
3 regressions pass: failed DONE write retains published URI, saving timeout requires explicit folder check, stale chooser cleanup cannot
cancel a newer revision. 24 helper regressions / 4 exported-schema tests and 20 socket-blocked exact selectors passed. No device result.
Publication mechanics: **BUILD SUCCESSFUL in 1m 36s**, 331 total / 330 pass / 1 skip; lint 0 errors / 7 warnings.
5 pending-commit/copy-failure/legacy collision/content-provider-root tests pass. API 24-28 uses narrow content-URI grants;
MediaStore success requires one committed row. Android provider/gallery execution unverified; no added permissions.
Earlier verified queue/storage/recovery/exact-picker/reuse increments and superseded tester/helper hashes live in `docs/PROGRESS.md`
(grep, never read whole). OpenCode fallback/runtime checks remain untested; old handoff checker lacked PyYAML (not installed).

## Phase 1 delivery checklist (STOP; unchecked device/policy gates remain deferred)
Owner cadence supersedes the previous phone-report/new-feature-area stop (R75). Verified internal commits; one final phase report.
Passing CLI checks is not phone verification. Existing phone checks remain deferred; do not claim overall reliability/playback.
- [x] Compact mode-specific picker: video first, at most 3 source-backed container/codec quick audio rows (+ current variant),
        explicit all-stream access and 340dp scrolling viewport. 3 new host tests pass; visual/a11y/device checks deferred.
- [x] Resolve performance foundation: bounded task-private same-link chooser reuse before consumption, explicit Refresh always re-extracts;
        init/subprocess/metadata-replay timing allowlist. No live extraction speedup measured; consumed snapshots never cached/replayed twice.
- [x] Owner-approved temporary/removable phone-free verifier: stdlib regressions, fresh offline tests/lint, APK/source/signing/payload
        checks and synthetic selectors; final delivered tester `56c15c4` passed default checks. No phone/device test performed.
- [x] Review and commit owner agent/config/docs edits plus supplied `74ce7cf` phone evidence; no new app feature (15 Nov 2026).
- [x] 0a. OWNER: install `74ce7cf`, send standard portrait picker screenshot and one planner-driven 1080p post-download log (R61).
- [ ] 0b. OWNER: try a second available preset, confirm selected dimensions and playback with sound; send its post-download log.
        Agents may continue unrelated Phase 1 steps, but must not claim this or high-resolution MKV playback passed.
- [x] 1a. Foreground admission boundary in Home (task-only lease, bounded progress, release through publication/terminal paths); fake-Engine tests green.
- [x] 1b. Shared session ownership outside Activity; independent scope, main-thread attachments, final-release replay/task cleanup.
- [x] 1c. Home timeout boundary: prompt stop callback, task-scoped cancellation, cleanup-fenced PAUSED(FGS_TIMEOUT), fresh confirmation to retry.
- [x] 1d. Task-only Android service admission/session attachment, basic progress/saving notification, `onTimeout` wiring; CLI checks green.
- [x] 1e. Notification Cancel action and contextual Android 13+ notification permission UI (not a launch gate); CLI checks green.
        Android 10 (API 29) remains the primary phone baseline; minSdk stays 24. Newer API checks do not raise the app's minimum.
- [x] Owner priority: full session Share log + action/deep-log coverage before more features; CLI checks green (R68), stamped APK built.
- [ ] 1f. Engine tester `f96194e` owner log confirms new runtime and one 1080p MP4 save without 403; old `6bb477b` failures remain historical.
        Repeat the earlier failing long video + working sample on same Wi-Fi while visible; confirm playback with sound/second preset.
        Brief background continuation is observed, not sustained background/lock or Cancel verification. Keep native runtime/planner/replay/logs.
- [x] Owner priority (16 Nov), first increment: hide unavailable video rows and label actual source resolution; CLI checks green, new UI phone-untested.
        This supersedes the older disabled-row design, not the standard typed planner. No exact-format picker/new tester in this increment.
- [x] Owner priority: separate source-backed original audio-only choices before Room, verified stamped tester `e37686c` ready (phone-untested).
        Supplied screenshots are behavior references only; do not copy code/assets. No browser/login/subtitle work.
        Owner now requests continuing through a verified stamped APK, not stopping after intermediate commits (16 Nov).
        Typed choices/plan, runner/evidence, Home/publication and separate UI CLI-tested; stamped APK inspected, exact artifact/phone sequence in evidence doc.
        Audio exact-ID selector emits no merge/transcode flags; codec/ID/dimension/extension evidence must agree. No MP3 conversions in this tester.
- [x] OWNER supplied e37686c picker/screenshots and complete post-result audio + second-video logs (R75). Audible playback/visibility,
        sustained background/Cancel/new APIs and earlier failing long videos remain deferred, not passed.
- [x] 2. Durable Room queue: platform-free revision-fenced task/store/transition/privacy/recovery foundation has 5 passing tests;
        Room 2.8.5/KSP 2.3.14 resolved/compiled; mapper/CAS tests and exported schema host SQL checks pass (R76/R77).
        Home/application wired with process-once recovery, staged/review/remove commands, new native attempt IDs, persist-before-work
        and cleanup-fenced terminal writes. Queue/history UI wired; interrupted publication requires separate explicit acceptance;
        Android Room runtime/process recovery device-deferred. At most 200 records, no secret headers retained in titles.
- [x] 3. Contract-checked error/retry UI; explicit fresh resolution after media 403 and safe failures, timeout wait, pre-flight
        free-space and actual-byte publication checks. Login/client/runtime/folder/raw-part recovery unavailable in Phase 1 and explained;
        no automatic connectivity resume/rescheduling or general 403 reliability claim.
- [x] 4. Advanced source-backed exact-video picker, typed selectors, strict ID/dimension/audio evidence, Home/history/fresh-retry wiring;
        20 socket-blocked engine selector cases pass. Separate exact-stream merges use MKV; no different-video fallback/transcode.
        Standard resolution mode remains default. Thumbnail embedding deliberately deferred; device playback unverified.
- [ ] 5. Broader engine update policy is excluded/gated for Phase 2; approved APK-only bundled freshness investigation is delivered (R72).
- [x] Integrated queue/history/progress/recovery UI and host recovery/privacy tests; stamped APK/source/signature/payload inspection and
        phase report delivered: `docs/evidence/phase1-delivery.md`. Android acceptance remains deferred, not waived/passed. STOP for `next`.

## Phase 2A checklist (STOP; screenshots/device gates deferred)
- [x] Share-link canonicalisation per recognised host; unknown hosts, signed URLs, malformed input unchanged; 6 tests.
- [x] Three destinations (Home / Downloads / Library) with one inset pass; diagnostics stays a local tool.
- [x] Library: own MediaStore rows (API 29+) / app folder (24-28), search, kind filter, size, duration, open/share/confirmed delete.
- [x] Downloads: queue card with live percentage for the one active download; format ids and requested ceiling; refusal to invent sizes.
- [x] Home: source-shortcut grid from `contracts/sites.json` (contract-synced test), "how to share" card, recent saves, contextual help.
- [x] Warnings/disclaimers moved into help dialogs and task details; protections and honesty text kept.
- [ ] OWNER: screenshot set (dark, light, large text) for approval — cannot be produced from host checks.
- [ ] OWNER/device: Library contents after reinstall, thumbnail (glyph fallback now), gallery visibility, share targets, large text/TalkBack.
- [x] Stamped APK + phase report: `docs/evidence/phase2a-delivery.md`. STOP for the owner's `next` before Phase 2B.

## Open owner checks (cannot be automated)
Gallery/Files visibility of `Download/YTDLP`; other native share targets; cancel cleanup on a real phone; light theme; large text/TalkBack; MKV playback.
Phase 2A: three-tab shell layout, Library search/filter/delete, Home recent saves, Downloads percentage.

## Risks to keep visible
- **Engine freshness:** owner-installed `f96194e` runs bundled 2026.10.26; previous `6bb477b` used 2025.11.12. YouTube
  extraction is the first thing to break. Updating yt-dlp at runtime downloads code, which conflicts with "no remote code execution"
  in `AGENTS.md`; R72 approves only a verified APK bundle, not runtime updates. One Android transfer passed; broader reliability remains open.
- Name: the display name is `YTDLP`; the spaced expansion contains "You Tube" while `AGENTS.md` forbids "YouTube" in the app name. Open.
- Browser/website-login and PO-token ideas are research only (`docs/16`); nothing is implemented. Do not start them unasked.
- Clean-room: coding agents must not read decompiled Snaptube source (the `rea` MCP server / REA folder); use the written notes in `docs/03` and `docs/16`.

## Environment (owner's PC, Windows, PowerShell 5.1)
JDK 17.0.21, Android SDK platform 37.0, build-tools 37.0.0, AGP 9.5.1, Gradle 9.6.0 (wrapper). Details: `docs/dev-setup.md`.
Build helper: `powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\build-debug.ps1`. If the Gradle daemon dies during lint, retry
with `GRADLE_OPTS=-Dorg.gradle.daemon=false` in that shell only (the last helper run needed it).
Models: primary `agentrouter/gpt-6.1-sol`; choose/confirm free fallbacks using `/models`; optional `build-free` profiles. See `opencode.jsonc`.
