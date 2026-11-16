# STATE: read this first (kept short and current)

Loaded automatically through `opencode.jsonc` `instructions`. Keep it SHORT. Long history lives in `docs/PROGRESS.md`
(append-only: `grep`/`tail` it, never read it whole) and `docs/RESEARCH-LOG.md` (look up by `R<number>`).
Update this file at the end of every step (`/handoff`). Facts here come from `docs/PROGRESS.md` and `docs/RESEARCH-LOG.md` (up to R78);
anything not re-run since is marked untested. Run `git log -5 --oneline` for the real HEAD.

## Where we are
- Product: YTDLP, an unofficial, free, GPL-3.0 Android front end for yt-dlp (package `io.github.muzammilawan.clipharbor`).
  Desktop sibling: `muzammilAwan-dev/yt-downloader-pro` (MIT), the parity target.
- Phase 0 (engine spike): **PASSED** 15 Nov 2026 on Infinix X682B (Android 10, API 29, arm64). Evidence: `docs/evidence/phase0.md`.
- **Phase 1 (MVP): in progress.** Done and unit-tested: logging/redaction, engine-version reporting, task-private replay
  (`--load-info-json`), download evidence, home controller, Material 3 home + paste/share, eight standard quality tiers with private
  typed selection plan, YTDLP branding and icon. Phone-verified earlier (`ec146df`): resolve, 1080p select, merge, MediaStore publish, playback with sound.
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

## Last recorded check — 16 Nov 2026
Exact-video foundation: **BUILD SUCCESSFUL in 2m 43s**, then exporter **BUILD SUCCESSFUL in 1m 18s**;
323 total / 322 pass / 1 skip; lint 0 errors / 7 warnings. 20 actual Kotlin selectors passed on socket-blocked pinned yt-dlp.
Typed source/selector/evidence/runner tests pass; exact source cannot fall back to different video. Home/UI wiring pending.
Contract recovery: **BUILD SUCCESSFUL in 2m 55s**, 317 total / 316 pass / 1 skip; lint 0 errors / 7 warnings.
Taxonomy/retry flags matched against contracts/errors.json; safe explicit fresh retries, 5-second timeout wait, no automatic reschedule.
Unsupported sign-in/client/runtime/folder/raw-part actions honestly explained. Destination-full-after-transfer and retry-capability tests pass.
Storage preflight: **BUILD SUCCESSFUL in 3m 59s**, 313 total / 312 pass / 1 skip; lint 0 errors / 7 warnings.
4 budget/unknown/overflow/estimate tests + fake-Engine low-space admission/retry test pass. Actual-byte publication recheck wired.
Conservative 3x video / 2x audio estimate + 64 MiB reserve, displayed free/budget/unknown warning; Android filesystem behavior device-unverified.
Queue/history UI: **BUILD SUCCESSFUL in 2m 59s**, 308 total / 307 pass / 1 skip; lint 0 errors / 7 warnings.
2 bounded/order/action-fence presentation tests pass; repeated wrapper BUILD SUCCESSFUL in 28s was up-to-date, not fresh.
Integrated staging/review/forget/open actions, 5 recent records plus all access, interrupted-save confirmation. UI/a11y/device unverified.
Home/Room integration: **BUILD SUCCESSFUL in 3m 8s**, 306 total / 305 pass / 1 skip; lint 0 errors / 7 warnings.
6 fake-Engine queue admission/retry/recovery/storage-failure/timeout/publication tests + bounded/redacted-record test pass.
Process-wide repository wired in Application; persist-before-resolve/download/publish/success; queue UI and recovery/storage UX still pending.
Room/KSP adapter: **BUILD SUCCESSFUL in 7m 14s**, 299 total / 298 pass / 1 skip; lint 0 errors / 7 warnings;
4 stdlib exported-DDL/DAO-SQL/transaction/backup tests pass. Generated v1 schema committed. Android database execution unverified.
Queue state foundation: **BUILD SUCCESSFUL in 2m 27s**, 296 total / 295 pass / 1 skip / 0 failures/errors; lint 0 errors / 7 warnings.
5 new store/state/privacy/recovery tests pass; first run caught numeric HTTP_403 error-name validation, fixed. Room and Home integration not yet implemented.
Current resolve-reuse/timing increment: **BUILD SUCCESSFUL in 2m 48s**, 291 total / 290 pass / 1 skip / 0 failures/errors;
lint 0 errors / 7 known warnings. 4 new fake-Engine regressions cover same-link reuse, explicit refresh, expiry/closed/consumed input,
and timing allowlist. No live extraction speedup measured; repeat extraction after download intentionally stays fresh. APK remains e37686c.
Historical tester/helper checks below:
Phone-free helper final default run: **BUILD SUCCESSFUL in 3m 44s**, 24 stdlib regressions and 96 video / 9 exact-audio selector cases passed.
Report `C:\Users\Malku\AppData\Local\Temp\opencode\ytdlp-phone-free-kvjj2y5b\summary.md`: PASS_HOST_CHECKS_DEVICE_REQUIRED;
JSON device_verification=NOT_RUN, runtime_copy=REMOVED; directory contains only the two reports. No installs or APK changes.
Earlier baseline-enabled helper: **BUILD SUCCESSFUL in 2m 39s**, full assets/native/manifest/icon identical to verified `f96194e`.
Required wrapper check also ran offline with stamped property: **BUILD SUCCESSFUL in 19s** (tests/lint up-to-date, not another fresh test run).
Prior stamped APK helper: **BUILD SUCCESSFUL in 2m 45s** (tests/lint), **BUILD SUCCESSFUL in 54s** (APK).
Fresh reports: 284 total / 283 passed / 1 Windows symlink skip / 0 failures/errors; lint 0 errors / 7 known warnings.
Five state/label safety tests passed before UI wiring, BUILD SUCCESSFUL in 36s; existing Home/audio/runner/replay/foreground/redaction tests green.
Offline WebM synthetic replay CLI emitted 251/NA/NA and 251/none/opus with socket connections blocked; no media downloaded.
Shell-only non-daemon setting; no persistent/project workaround. Existing fake-Engine Home, startup, installer, replay, foreground and redaction tests green.
Unchanged `dist/app-debug.apk`: actual DEX stamp `e37686c`, **70,276,358 bytes**, built/copied/SHA256SUMS agree:
`C923865E6A01F464358F2B30570FF5A25A84DD5275B7E5FDF8DEA245EAAD4FE6`.
Signature verifies and matches `f96194e`; exact engine/notices, seven native entries, manifest and three icon entries unchanged.
Video planner/dependencies/permissions unchanged; separate original audio UI/Home/save routing CLI-tested, phone-untested. 96 video selector cases
also passed on verified 2026.10.26 with sockets blocked; host harness only changed the historical checker's expected pin, not the repo tool.
OpenCode runtime/fallback checks remain untested; previous full handoff checker was blocked by absent PyYAML (not installed).
Tester `e37686c`: minSdk 24 / target 37 / arm64; same signing certificate/native entries/manifest as `f96194e`, no new permissions.

## Current Phase 1 exit checklist (continue within this approved phase)
Owner cadence supersedes the previous phone-report/new-feature-area stop (R75). Verified internal commits; one final phase report.
Passing CLI checks is not phone verification. Existing phone checks remain deferred; do not claim overall reliability/playback.
- [x] Compact mode-specific picker: video first, at most 3 source-backed container/codec quick audio rows (+ current variant),
        explicit all-stream access and 340dp scrolling viewport. 3 new host tests pass; visual/a11y/device checks deferred.
- [x] Resolve performance foundation: bounded task-private same-link chooser reuse before consumption, explicit Refresh always re-extracts;
        init/subprocess/metadata-replay timing allowlist. No live extraction speedup measured; consumed snapshots never cached/replayed twice.
- [x] Owner-approved temporary/removable phone-free verifier: stdlib regressions, fresh offline tests/lint, APK/source/signing/payload
        checks and synthetic selectors; default and optional-baseline runs passed. Current tester remains `e37686c`; no phone/device test performed.
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
- [ ] 4. Exact-video-format picker (original audio moved earlier and delivered above). Thumbnail embedding is deliberately deferred.
- [ ] 5. Broader engine update policy remains gated; approved APK-only freshness investigation is prioritized in 1f (R72).
- [ ] Integrated queue/history/progress/recovery UI and host recovery/privacy tests; stamped APK/source/signature/payload inspection and phase report.

## Open owner checks (cannot be automated)
Gallery/Files visibility of `Download/YTDLP`; other native share targets; cancel cleanup on a real phone; light theme; large text/TalkBack; MKV playback.

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
