# Prompt for Claude (web chat): optimize, upgrade and improve UI/UX of a working app

> Note (15 Nov 2026): the product is now **YTDLP** (formerly ClipHarbor); where the text below says "ClipHarbor" identity, use `docs/11-BRANDING-AND-UX.md`
> and `AGENTS.md`. Also attach `docs/STATE.md`.

Use this after a phase (or the whole app) works. For crashes or bugs use `DEBUG_REFINE_PROMPT.md` instead.
Start a new Claude chat, attach this package's `docs/` and `contracts/`, then paste everything below the divider.

## What to attach
- A zip of the repo **source only**: from the repo root run `git archive -o ../source.zip HEAD` (this leaves out `build/`, `.gradle/`, `dist/` and anything git-ignored). If the zip is too big for the chat, attach just the modules you want reviewed plus `settings.gradle.kts`, `gradle/libs.versions.toml` and the module `build.gradle.kts` files.
- `docs/PROGRESS.md` and `docs/RESEARCH-LOG.md`.
- Screenshots of every screen: light and dark, one small phone and (if you have it) a large one, plus an error state and an empty state. Claude can look at images.
- Latest shared diagnostic log and, if you have them, a startup time and APK size (`dist/*.apk` file size).

---

You are a senior Android engineer, performance specialist and product designer reviewing **an unofficial Android version of yt-dlp** (GPL-3.0, ad-free, no telemetry). The attached `docs/` is the spec;
`docs/10-VERIFICATION-LOG.md` says which facts are verified. Another AI wrote the code. Do not trust it or the docs blindly.

## Work in this order, one section per reply unless I say "all at once"

**1. Audit against the spec.** Compare the code to `docs/02`, `contracts/*.json`, the permissions budget, and the privacy rules (no cookies persisted, redacted logs, no cleartext, no telemetry, GPL-only code). List violations ranked by severity with file and line.

**2. Performance and size.** Look for: work on the main thread, unnecessary recomposition and unstable Compose parameters, blocking calls in the queue/worker, leaks (services, WebView, cursors), unbounded log growth, wake-lock misuse, battery drain in the foreground service,
APK size (R8/shrinking, ABI splits, unused resources and dependencies). Propose **measured** changes: say what to measure (Macrobenchmark, Android Studio profiler, `adb shell dumpsys`, APK Analyzer) and what number would prove it helped. Add Baseline Profiles. Do not claim a speedup you did not measure.

**3. Dependency and platform upgrades.** Look up the **current** stable versions (web search; cite sources) of AGP, Kotlin, Compose BOM, Room, WorkManager, DataStore, Coil, `youtubedl-android`, and the target API level behaviour changes. Give an upgrade plan with one change per commit,
the migration notes you found, the risk of each, and the test command that must be green after each. Never invent versions.

**4. UI/UX review (from my screenshots).** Review each screen: clarity of the primary action, one-handed reach, touch targets (at least 48 dp), contrast and dark theme, font scaling, empty/loading/error states with the recovery button from `contracts/errors.json`, progress feedback, TalkBack labels, language/strings in resources, tablet/landscape behaviour.
Give concrete Compose changes (complete composables), not just advice. Keep the identity "ClipHarbor" and the privacy-first tone from `docs/11-BRANDING-AND-UX.md`. Do not copy Snaptube, Seal or YTDLnis designs or assets.

**5. Tests and logging gaps.** List missing tests against `docs/09` and `docs/12`, and weak spots in the diagnostic log (anything a debugger would still need). Write the missing tests.

## Rules
- Be factual. You cannot run Gradle or my phone: mark every change "untested" and give the exact command or device step that verifies it. Never invent APIs or library versions; verify or mark `TODO(verify)`.
- Small, reviewable batches: at most about 5 files per batch; complete files for new files, unified diffs for edits. After each batch tell me what to run and what a pass looks like, then wait.
- Prioritise: correctness and security first, then YouTube-extraction reliability, then performance, then polish. No cosmetic churn without a reason.
- If you disagree with the spec, say so and cite the reason; do not silently override.
