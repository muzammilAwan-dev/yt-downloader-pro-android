# Agent setup audit (15 Nov 2026)

Scope: the hand-off zip of `yt-downloader-pro-android` as the owner supplied it, read in full for agent configuration, plan docs,
build files, CI and tooling. I could not run Gradle, an emulator, OpenCode or GitHub Actions in this audit, so nothing below
claims a build or test result. The live GitHub page rate-limited my fetch, so the zip is the only repo state I read.

## Update for the second zip (15 Nov 2026, 17:00)

The newer zip has byte-identical code, contracts and config to the first; the agent only added the `74ce7cf` standard-quality tester handoff to `docs/PROGRESS.md`
(41 lines) and `R60` to `docs/RESEARCH-LOG.md`. It ran without my setup (no `opencode.jsonc`, old `AGENTS.md`), which is why the ordered step "refresh `dist/`" in
`docs/STATE.md` is now marked done and the owner's phone test of `74ce7cf` is the new item 0.

## What was wrong or missing, and what changed

### Live repository review (15 Nov 2026)

The audit below describes the supplied zip, not runtime verification of this setup. Official V2 migration documentation
confirms supported legacy config remains compatible; no wholesale migration was requested. Reviewed project JSONC has
no stored credentials. Narrowed broad shell grants and made the reviewer default-deny (including MCP/delegation), with
local reads and exact Git inspection commands only. Corrected `/handoff` wording: history is append-only, never trimmed.
Official V2 agents/models docs say an already selected session model survives agent changes: use `/models` to confirm a
fallback, not Tab alone. Free model IDs/privacy terms are listed in the official Console docs; no inference was tested.
`opencode` is not on this shell's PATH; live config reload/permission enforcement and fallback calls remain untested.
`python tools/verify_handoff.py` could not run because PyYAML is absent; no dependency was installed and no pass claimed.
Phone `74ce7cf` evidence confirms a portrait 1080p MP4 download/merge/MediaStore save; second-quality/MKV checks remain open.
Required Gradle check: `BUILD SUCCESSFUL in 24s`; cached reports show 138 tests (137 passed/one skipped), lint 0 errors/7
known warnings. Tests and lint analysis were FROM-CACHE, not freshly executed. Static JSONC/command/doc-index checks passed.

| # | Finding | Why it matters | Change |
|---|---|---|---|
| 1 | **No OpenCode config at all.** Only `AGENTS.md` and a prompt file. | No pinned model, no fallback story, and default permissions. In OpenCode 2 the base policy allows everything, including outside the project folder. | New `opencode.jsonc`: primary model, `build-free` / `build-free-2` fallback agents, read-only `reviewer`, permission rules, file-watcher ignores. |
| 2 | **`.gitignore` ignored all of `.opencode/`.** | Project agents, commands and skills would never be committed, so the workflow is lost on a new PC or account. | Ignore only local state (`node_modules`, lockfiles). |
| 3 | **Context bloat from the re-read rule.** `AGENTS.md` said to re-read `docs/PROGRESS.md` (72,528 bytes, about 18k tokens) and `RESEARCH-LOG.md` (45,258 bytes, about 11k) after every reset. | Pays about 30k tokens each reset and buries the state in raw Gradle output. Research shows extra context-file instructions raise cost by up to 19-20% for 3-4% better success at best, and agents obey them even when unnecessary. | New `docs/STATE.md` (about 70 lines) loaded through `instructions`; PROGRESS and RESEARCH-LOG become grep-only; new entries capped at 10 lines. |
| 4 | **Stale, contradictory agent instructions.** `AGENTS.md` still ordered the one-time environment setup and "stop after Phase 0" (both done). `AGENT_PROMPT_OPENCODE.md` still said ClipHarbor, "propose three names", "do setup first", "free DeepSeek-class models". | A new agent given that prompt would try to redo setup and rename the app. | `AGENTS.md` rewritten (current, shorter, with the working loop); the prompt file replaced by a short session-start guide; notes added to the three claude.ai prompts. |
| 5 | **README lost its docs index**, so `python tools/verify_handoff.py` fails (13 docs unlisted) and the old prompt's "names are in README.md" was false. | The repo's own self-check was red. | Docs index restored (re-run below). |
| 6 | **Dependabot would open PRs that break pinned versions.** `libs.versions.toml` says Kotlin must equal AGP's bundled version and coroutines is pinned for metadata compatibility. | Noise and broken builds from automatic bumps. | `ignore` for `org.jetbrains.kotlin.*` and `kotlinx-coroutines-*`; AndroidX grouped. |
| 7 | **No automatic model fallback exists in OpenCode.** | "gpt-6-sol with DeepSeek fallback" cannot be a one-line config. | Manual fallback that keeps the session: Tab to `build-free`, or `/models`. See options below. |

## Open decisions for the owner (I did not decide these)

1. **Engine age is the biggest product risk, not the agent setup.** `youtubedl-android` 0.18.1 bundles yt-dlp 2025.11.12; upstream stable
   2026.10.26 exists (your own PROGRESS records both). YouTube extraction is what breaks first. The Phase 2 plan to update the engine at runtime
   downloads code, which conflicts with the "no remote code execution" rule in `AGENTS.md`. Decide: allow a checksum-verified, user-triggered
   engine update (and record it as an ADR), or ship engine updates only as new app releases. I could not check whether a newer
   `youtubedl-android` than 0.18.1 exists (Maven Central page returned no data); check before deciding.
2. **Name.** `AGENTS.md` forbids "YouTube" in the app name, but the chosen identity expands to "You Tube Downloader Pro". Trademark
   clearance is already marked unverified. Safest is to keep `YTDLP` only in user-facing text and store listings. Not legal advice.
3. **Clean-room boundary for the Snaptube reverse engineering.** `docs/03` and `docs/16` cite decompiled class names and your local
   `Documents\REA` path, and PROGRESS shows an `explore` agent reading decompiled source inside a coding session. For a GPL project that must stay
   free of copied code, keep decompiled source out of coding sessions and let agents work from the written notes. I added `external_directory: ask`
   and an `AGENTS.md` rule. Consider removing personal paths and device details from committed docs if the repo is public.
4. **aria2c.** The plan lists aria2c as an opt-in speed option. A summary of yt-dlp 2026.06.09 says it removed aria2c support for HLS and DASH
   (secondary source; verify in the yt-dlp changelog) before building around it.

## Model setup (updated 15 Nov 2026, 17:00)

- **Primary:** `agentrouter/gpt-6.1-sol`, through your AgentRouter provider (an OpenAI-compatible gateway defined in your user-level OpenCode config).
  `gpt-6.1-sol` is a real, current model id (OpenCode's own docs and OpenRouter list it). I cannot see AgentRouter's catalog or limits, so I did not verify them.
- **Free fallbacks (no API key needed):** OpenCode Zen's free tier. From the Zen docs, `deepseek-v4.1-flash-free` and `glm-5.3-flash-free` are $0 "for a limited time",
  and their provider keeps zero retention and does not train on your data. The other free models log or train on prompts, or are country-restricted, so I did not
  pick them. Pick them with Tab (`build-free`, `build-free-2`) or `/models`. Caveats: free models are rate-limited per IP, may be slow or disappear, and I could
  not confirm how reliably they call tools; run `/verify` once on a free model before trusting it with edits.
  An OpenRouter `deepseek/deepseek-v4-flash-0731:free` also exists, but its page did not state tool-calling support or free-tier data terms.
- **No automatic fallback exists in OpenCode** (open request anomalyco/opencode#7602, candidate PR #26292). Options, best first for a solo developer:
  1. *Manual, same session* (shipped): Tab to a `build-free` agent, or `/models`. Zero dependencies.
  2. *Plugin* such as `opencode-runtime-fallback` (per-agent `fallback_models`). It is a V1-era plugin; I could not confirm it runs on OpenCode 2.
  3. *Gateway* (LiteLLM-style router) exposing one model route with ordered targets. Works for API-key providers only.
- **Config format.** OpenCode 2 renamed keys (`agent`->`agents`, `permission` array, `plugin`->`plugins`). The current schema still accepts the old keys
  (marked deprecated) and OpenCode 2 normalizes them in memory. Your own provider/MCP config uses the old shape too, so the project file does the same.
  Mixing shapes inside one nested entry is warned against.

## Your user-level config (reviewed from the snippet you pasted)

- **API key:** it is written inline. Use `"apiKey": "{env:AGENTROUTER_API_KEY}"` and set that Windows user environment variable; OpenCode substitutes `{env:NAME}`.
  Keep that file out of every repo. If a real key was ever pasted into a chat or commit, rotate it. This repo's `.gitignore` now also ignores a stray project-level `opencode.json`.
- **`rea` MCP server (decompiled Snaptube source and apktool/jadx tools) is enabled for every session.** That puts decompiled third-party code within reach of the
  agent that writes your GPL code, and every enabled MCP server also adds its tool definitions to the context of every session. Suggested: set `"enabled": false` on `rea`
  in your user-level config and flip it to `true` only for a deliberate analysis session; write what you learn as prose notes in `docs/03` or `docs/16`, and let the
  coding sessions read only those notes. I did not try to gate `rea` from the project file: a project entry replaces a whole server definition rather than patching it,
  and I could not verify a per-agent switch.
- **`browse` MCP:** fine for research. Disable it when you are only coding, for the same context-cost reason.
- `@ai-sdk/openai` talks to the OpenAI Responses API. If AgentRouter ever rejects requests, try `@ai-sdk/openai-compatible` for that provider (not tested here).

## Superpowers skills

Not enabled by default. The brainstorming/TDD/plan skills are good on a strong model, but they add a lot of process text per session and fight the small-step
`/next` loop on the Flash model. Opt-in snippet is commented at the bottom of `opencode.jsonc`. Its install path changed (a `plugin` entry, not symlinks); some Windows
installs of git-backed plugins fail, and OpenCode 2 uses a new plugin API, so check compatibility first.

## Road to a Snaptube-like app (inspiration only, original code)

Your docs already map this well. What is missing is order. Do next: stamped APK refresh, foreground service and notification, durable Room queue, error/retry UI
(these four are what make it *feel* like a downloader app), then the engine-freshness decision above. In-app browse/search/site shortcuts and website login (doc 16)
are Phase 2 research; starting them before the queue and service exist would add risk with no user-visible download improvement.

## Smaller items left as they are

CI actions are pinned to v4; setup-java and setup-gradle have newer majors, and Dependabot's weekly github-actions run will propose them (not changed, because I cannot run CI here).
Release builds have `isMinifyEnabled = false` (Phase 3). The theme is still named `Theme.ClipHarbor` (cosmetic; renaming touches the manifest and styles).
`THIRD_PARTY_NOTICES.md` still has `TODO(verify)` rows (a Phase 3 release blocker, already stated there).

## How to use this

1. Copy the changed files into the repo root and commit them. The live-review notes above supersede conflicting zip-era guidance below.
2. Run `opencode models` once to confirm `agentrouter/gpt-6.1-sol` and the two `opencode/...-free` ids appear; fix a line in `opencode.jsonc` if one does not.
3. Run `opencode`, then `/next`. Use `/models` to select and confirm a free fallback when the primary is unavailable.

## Sources

- OpenCode config schema: https://opencode.ai/config.json (all keys above checked present)
- OpenCode V1 to V2 migration: https://opencode.ai/docs/migrate-v1/
- OpenCode permissions (V2): https://opencode.ai/v2/docs/permissions/
- OpenCode agents: https://opencode.ai/docs/agents/
- OpenCode context management and AGENTS.md behavior: https://datalakehousehub.com/blog/2026-03-context-management-opencode/
- AGENTS.md size and cost evidence (ETH Zurich study, arXiv 2602.11988, as summarized): https://codex.danielvaughan.com/2026/03/27/agents-md-bloat-problem/
- Model fallback status: https://github.com/anomalyco/opencode/issues/7602
- Superpowers for OpenCode install: https://cdn.jsdelivr.net/npm/@shodocan/superpowers@6.0.3/docs/README.opencode.md (a mirror of the upstream doc) and https://www.mintlify.com/obra/superpowers/installation/opencode (older symlink method)
- GPT-6 Sol: https://www.nocode.mba/ai-release-tracker/releases/gpt-6-sol
- DeepSeek V4.1 Flash: https://pi.dev/models/opencode/deepseek-v4-1-flash and https://deepseek.com/en/news/deepseek-v4-1-flash/
- yt-dlp release list (2026.10.26 newest): https://newreleases.io/project/github/yt-dlp/yt-dlp/release/2026.06.09
