# STATE: read this first (kept short and current)

Loaded automatically through `opencode.jsonc` `instructions`. Keep it SHORT. Long history lives in `docs/PROGRESS.md`
(append-only: `grep`/`tail` it, never read it whole) and `docs/RESEARCH-LOG.md` (look up by `R<number>`).
Update this file at the end of every step (`/handoff`). Facts here come from `docs/PROGRESS.md` and `docs/RESEARCH-LOG.md` (up to R73);
anything not re-run since is marked untested. Run `git log -5 --oneline` for the real HEAD.

## Where we are
- Product: YTDLP, an unofficial, free, GPL-3.0 Android front end for yt-dlp (package `io.github.muzammilawan.clipharbor`).
  Desktop sibling: `muzammilAwan-dev/yt-downloader-pro` (MIT), the parity target.
- Phase 0 (engine spike): **PASSED** 15 Nov 2026 on Infinix X682B (Android 10, API 29, arm64). Evidence: `docs/evidence/phase0.md`.
- **Phase 1 (MVP): in progress.** Done and unit-tested: logging/redaction, engine-version reporting, task-private replay
  (`--load-info-json`), download evidence, home controller, Material 3 home + paste/share, eight standard quality tiers with private
  typed selection plan, YTDLP branding and icon. Phone-verified earlier (`ec146df`): resolve, 1080p select, merge, MediaStore publish, playback with sound.
- Latest/owner-installed tester: **`f96194e`** (APK-only engine refresh), artifact stamp/asset/signature/copied SHA-256 verified.
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
`.\gradlew.bat testDebugUnitTest lintDebug --console=plain`: **BUILD SUCCESSFUL in 3m 21s** (original-audio Home/publication).
Fresh reports: 277 total / 276 passed / 1 Windows symlink skip / 0 failures/errors; lint 0 errors / 7 known warnings.
Nine Home tests first ran 8 fail / 1 pass, **BUILD FAILED in 1m 46s**; then 9/9 pass in 1m 6s. Full run caught log field order
regression (BUILD FAILED in 38s); old formats/ms prefix restored, full tests/lint green above.
Shell-only non-daemon setting; no persistent/project workaround. Existing fake-Engine Home, startup, installer, replay, foreground and redaction tests green.
No new APK: `dist/app-debug.apk` remains stamped `f96194e`, 70,342,176 bytes; SHA-256 rechecked unchanged
`9CECFB27BF7C0F645DD03CAA5ACDACFD54B9E5D499904DBFCD0AA50DF9F8F495`. Prior helper checks BUILD SUCCESSFUL in 2m 3s; APK in 32s.
Video runner/planner/icon/dependencies/permissions unchanged; Home audio/save routing CLI-tested; Compose wiring next, phone-untested.
OpenCode runtime/fallback checks remain untested; previous full handoff checker was blocked by absent PyYAML (not installed).
Tester `f96194e`: minSdk 24 / target 37 / arm64; same signing certificate/native entries/manifest as `6bb477b`, no new permissions.

## Next steps (do in order, one per `/next`)
Owner cadence (R65): push verified increments; owner now runs engine tester `f96194e`; keep `dist` unchanged until the next meaningful tester.
Passing CLI checks is not phone verification. Existing phone checks and the major-feature-area gate remain open.
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
- [ ] Next owner priority: separate source-backed audio-only choices (before Room). MP3 conversion options must be labelled separately from source formats.
        Supplied screenshots are behavior references only; do not copy code/assets. No browser/login/subtitle work.
        Owner now requests continuing through a verified stamped APK, not stopping after intermediate commits (16 Nov).
        Typed original-audio choices/plan, runner/evidence and Home/publication CLI-tested; next UI wiring, stamped tester.
        Audio exact-ID selector emits no merge/transcode flags; codec/ID/dimension/extension evidence must agree. No MP3 conversions in this tester.
- [ ] 2. Durable Room queue (staged tasks, survives process death); Home observes it. Add Room only with the version checked.
- [ ] 3. Error and retry UI driven by `contracts/errors.json`; pre-flight free-space check; re-resolve on 403.
- [ ] 4. Exact-format and audio-only picker. (Thumbnail embedding is deliberately deferred.)
- [ ] 5. Broader engine update policy remains gated; approved APK-only freshness investigation is prioritized in 1f (R72).

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
