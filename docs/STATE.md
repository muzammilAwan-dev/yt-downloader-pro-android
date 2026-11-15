# STATE: read this first (kept short and current)

Loaded automatically through `opencode.jsonc` `instructions`. Keep it SHORT. Long history lives in `docs/PROGRESS.md`
(append-only: `grep`/`tail` it, never read it whole) and `docs/RESEARCH-LOG.md` (look up by `R<number>`).
Update this file at the end of every step (`/handoff`). Facts here come from `docs/PROGRESS.md` and `docs/RESEARCH-LOG.md` (up to R64);
anything not re-run since is marked untested. Run `git log -5 --oneline` for the real HEAD.

## Where we are
- Product: YTDLP, an unofficial, free, GPL-3.0 Android front end for yt-dlp (package `io.github.muzammilawan.clipharbor`).
  Desktop sibling: `muzammilAwan-dev/yt-downloader-pro` (MIT), the parity target.
- Phase 0 (engine spike): **PASSED** 15 Nov 2026 on Infinix X682B (Android 10, API 29, arm64). Evidence: `docs/evidence/phase0.md`.
- **Phase 1 (MVP): in progress.** Done and unit-tested: logging/redaction, engine-version reporting, task-private replay
  (`--load-info-json`), download evidence, home controller, Material 3 home + paste/share, eight standard quality tiers with private
  typed selection plan, YTDLP branding and icon. Phone-verified earlier (`ec146df`): resolve, 1080p select, merge, MediaStore publish, playback with sound.
- Latest handoff: **`74ce7cf`** (standard-quality tester build). Helper run executed the tests: BUILD SUCCESSFUL, 138 tests (137 pass, 1 skipped:
  Windows symlink), lint 0 errors / 7 known warnings, 96 offline selector checks PASS (yt-dlp 2025.11.12), `dist/app-debug.apk` stamped `74ce7cf`.
- Owner evidence for `74ce7cf` (R61): dark portrait standard picker with disabled QHD/4K; 1080×1920 `137+140` download,
  resolve replay reused, MP4 merge and 7,737,267-byte MediaStore save; later self-test 7/7. Only one quality download.
- Still unverified: second preset, new file playback with sound, QHD/4K MKV playback; earlier `ec146df` playback remains confirmed.
- Foreground foundation: tested admission/progress/release and shared process-local session ownership with an independent scope.
  Main is still the only real owner and uses ActivityOnly; final detach cancels. Service/notification/timeout wiring is absent. Keep the app open.

## Last recorded check — 15 Nov 2026
`.\gradlew.bat testDebugUnitTest lintDebug --console=plain`: **BUILD SUCCESSFUL in 30s**.
Tests executed: 154 total, 153 passed, 1 Windows symlink skip, 0 failures/errors; lint executed, 0 errors / 7 known warnings.
Seven new offline session tests (six use fake Engine): shared/reconnected owners, independent scope, final cleanup and failed factory.
Application/Activity/diagnostic wiring compiled and linted, not phone-tested; no foreground or process-death guarantee.
OpenCode runtime/fallback checks remain untested; previous full handoff checker was blocked by absent PyYAML (not installed).
Source base `018e905`; no APK rebuilt/copied this step, `dist/app-debug.apk` remains `74ce7cf`. Use Git for current HEAD.

## Next steps (do in order, one per `/next`)
- [x] Review and commit owner agent/config/docs edits plus supplied `74ce7cf` phone evidence; no new app feature (15 Nov 2026).
- [x] 0a. OWNER: install `74ce7cf`, send standard portrait picker screenshot and one planner-driven 1080p post-download log (R61).
- [ ] 0b. OWNER: try a second available preset, confirm selected dimensions and playback with sound; send its post-download log.
        Agents may continue unrelated Phase 1 steps, but must not claim this or high-resolution MKV playback passed.
- [x] 1a. Foreground admission boundary in Home (task-only lease, bounded progress, release through publication/terminal paths); fake-Engine tests green.
- [x] 1b. Shared session ownership outside Activity; independent scope, main-thread attachments, final-release replay/task cleanup.
- [ ] 1c. Android service attaches to the session; progress/cancel notification (manifest needs FOREGROUND_SERVICE,
        FOREGROUND_SERVICE_DATA_SYNC, POST_NOTIFICATIONS, inside the budget) and `onTimeout` handling. Main remains the only real owner.
- [ ] 2. Durable Room queue (staged tasks, survives process death); Home observes it. Add Room only with the version checked.
- [ ] 3. Error and retry UI driven by `contracts/errors.json`; pre-flight free-space check; re-resolve on 403.
- [ ] 4. Exact-format and audio-only picker. (Thumbnail embedding is deliberately deferred.)
- [ ] 5. Engine freshness spike. Owner decision needed first (see Risks).

## Open owner checks (cannot be automated)
Gallery/Files visibility of `Download/YTDLP`; native share sheet; cancel cleanup on a real phone; light theme; large text/TalkBack; MKV playback.

## Risks to keep visible
- **Engine age:** `youtubedl-android` 0.18.1 bundles yt-dlp 2025.11.12; upstream stable was 2026.10.26 (recorded in PROGRESS). YouTube
  extraction is the first thing to break. Updating yt-dlp at runtime downloads code, which conflicts with "no remote code execution"
  in `AGENTS.md`; this needs an explicit owner decision/ADR, not an agent's silent choice.
- Name: the display name is `YTDLP`; the spaced expansion contains "You Tube" while `AGENTS.md` forbids "YouTube" in the app name. Open.
- Browser/website-login and PO-token ideas are research only (`docs/16`); nothing is implemented. Do not start them unasked.
- Clean-room: coding agents must not read decompiled Snaptube source (the `rea` MCP server / REA folder); use the written notes in `docs/03` and `docs/16`.

## Environment (owner's PC, Windows, PowerShell 5.1)
JDK 17.0.21, Android SDK platform 37.0, build-tools 37.0.0, AGP 9.5.1, Gradle 9.6.0 (wrapper). Details: `docs/dev-setup.md`.
Build helper: `powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\build-debug.ps1`. If the Gradle daemon dies during lint, retry
with `GRADLE_OPTS=-Dorg.gradle.daemon=false` in that shell only (the last helper run needed it).
Models: primary `agentrouter/gpt-6.1-sol`; choose/confirm free fallbacks using `/models`; optional `build-free` profiles. See `opencode.jsonc`.
