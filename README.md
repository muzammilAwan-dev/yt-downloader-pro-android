# yt-downloader-pro-android: hand-off package

Everything a fresh Claude chat needs to start building the Android app, plus a CI workflow.
Prepared 14 Nov 2026 from: the REA export of Snaptube 7.67.0, the desktop repo
`muzammilAwan-dev/yt-downloader-pro` (main branch README), and web research.

## How to use it

1. Create the new GitHub repo (license: **GPL-3.0**, see `docs/07-LICENSE-AND-DISTRIBUTION.md`; pick a distinctive name, see `docs/11-BRANDING-AND-UX.md`).
2. Copy this package's contents into the repo root (`docs/`, `contracts/`, `.github/`, `AGENTS.md`, `tools/`), so CI and agent rules are there from the first commit.
3. **For opencode, the agent first does the environment setup and local git repo itself (`docs/14-AGENT-SETUP-TASK.md`), asking you once before installing anything; the GitHub remote is added later by you.**
   **Claude web chat:** attach the zip and paste the part of `CLAUDE_PROMPT.md` below its divider line.
   **opencode / DeepSeek free agent:** open the repo in opencode and paste the block in `AGENT_PROMPT_OPENCODE.md`.
4. Both prompts make the AI read the plan, **research the latest versions and practices first** (logged in `docs/RESEARCH-LOG.md`), then build phase by phase.
5. Do **Phase 0** (a spike on a real phone) before anything else. The AI cannot run your phone; you report the result.
6. **Recommended order:** opencode builds Phase 0 and Phase 1 in your repo (it can run Gradle on your machine); a Claude web chat with `DEBUG_REFINE_PROMPT.md` reviews, debugs and refines after each phase gate, using real logs.
7. **Which prompt when:** `AGENT_PROMPT_OPENCODE.md` = first build (opencode); `DEBUG_REFINE_PROMPT.md` = Claude fixes bugs after each phase; `POLISH_UPGRADE_PROMPT.md` = Claude optimizes, upgrades and improves UI/UX; `CLAUDE_PROMPT.md` = only if Claude builds instead of opencode.
8. To get an APK from CI: GitHub -> Actions -> "Android CI" -> Run workflow (choose debug or release). Pushes and PRs only run tests.

## Contents

| Path | What it is |
|---|---|
| `CLAUDE_PROMPT.md` | Prompt for a Claude web chat (research first, then build) |
| `POLISH_UPGRADE_PROMPT.md` | Prompt for a Claude web chat to optimize, upgrade dependencies and improve UI/UX of a working app |
| `DEBUG_REFINE_PROMPT.md` | Prompt for a Claude web chat to debug and refine code an agent wrote |
| `AGENT_PROMPT_OPENCODE.md` | Prompt for opencode / free DeepSeek-class terminal agents |
| `AGENTS.md` | Short rules file opencode reads automatically from the repo root |
| `docs/01-PLAN.md` | Updated, consolidated plan (supersedes the earlier Claude Doc) |
| `docs/02-ARCHITECTURE.md` | Modules, state machine, data model, reference snippets (untested) |
| `docs/03-SNAPTUBE-REA-FINDINGS.md` | What the Snaptube export shows; keep / adapt / reject; code-level tricks |
| `docs/04-OPEN-SOURCE-REFERENCES.md` | Related open-source projects, licenses, what to study |
| `docs/05-YOUTUBE-ENGINE-NOTES.md` | JS runtime, PO tokens, clients, cookies, engine updates |
| `docs/06-ANDROID-PLATFORM-NOTES.md` | Foreground-service limits, storage, permissions, binaries |
| `docs/07-LICENSE-AND-DISTRIBUTION.md` | GPL consequence, channels, developer verification |
| `docs/08-DESKTOP-CONTRACT.md` | Behaviour to keep consistent with the desktop app |
| `docs/09-TEST-STRATEGY.md` | What to test, how, and what CI can and cannot prove |
| `docs/11-BRANDING-AND-UX.md` | Positioning as "yt-dlp for Android", naming rules, Snaptube-inspired UX |
| `docs/12-AUTOMATED-TESTS-AND-DEEP-LOGGING.md` | `dotnet test` equivalent, in-app self-test, per-phase APKs, deep log format and redaction rules |
| `docs/13-LOCAL-BUILD-AND-INSTALL.md` | Command-line APK build on your PC (`dotnet build` equivalent), install on phone, where the log file lands |
| `docs/14-AGENT-SETUP-TASK.md` | Task for the local agent: install tools, create the local git repo, folder structure, prove the toolchain |
| `docs/10-VERIFICATION-LOG.md` | Every factual claim with source and confidence, plus unverified items |
| `contracts/*.json` | Error taxonomy, site catalog, proposed yt-dlp option allowlist |
| `.github/workflows/android.yml` | Tests on push/PR; **manual run also builds and uploads the APK** |
| `.github/dependabot.yml` | Weekly bumps for Gradle deps and Actions |
| `tools/build-debug.ps1`, `tools/build-debug.sh` | Untested helpers: test + build debug APK + copy to `dist/` |
| `tools/check-env.ps1`, `tools/check-env.sh` | Untested environment checkers (JDK, SDK, adb, wrapper) |
| `.gitignore`, `.gitattributes`, `.editorconfig` | Repo hygiene files for the Android repo |
| `tools/verify_handoff.py` | Self-check for this package (JSON, YAML, links) |

## What is deliberately NOT included

- **No Snaptube source code.** The export's own `SHARE.md` warns it is decompiled proprietary code.
  This package contains only my own written observations of it.
- **No code copied from Seal, YTDLnis or other GPL projects.** Study them at the links in `docs/04`.
- **No compiled or tested Android project.** There was no Android SDK or network in the sandbox that produced
  this package, so nothing here claims "builds" or "tests pass". Snippets are marked UNTESTED.

## Honest limits of the research

See section "Not verified" in `docs/10-VERIFICATION-LOG.md`. The biggest: nobody has yet proven on a real device that
`youtubedl-android` + current yt-dlp returns 1080p from YouTube. That is Phase 0.
