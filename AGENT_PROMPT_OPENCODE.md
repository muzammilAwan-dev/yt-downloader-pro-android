# Starting an OpenCode session (replaces the 14 Nov setup-era prompt)

The environment setup, Phase 0 spike, naming decision and first Phase 1 steps are done. The old prompt in this file told a new
agent to redo setup and propose names; it is retired. What an agent needs now is already loaded for it:
`AGENTS.md` (rules, loaded automatically), `docs/STATE.md` (current state, loaded through `opencode.jsonc`).

## Start a session
1. Your `agentrouter` provider and key live in your user-level OpenCode config; this repo's `opencode.jsonc` only selects `agentrouter/gpt-6.1-sol` and two free fallbacks. In the repo root run `opencode models` once to confirm those ids, then run `opencode`.
2. First message, nothing else needed: `/next`. It does one small step (tests first, build, commit, update STATE.md) and stops.
3. Use `/verify` for a read-only test/lint/build report, `/review` for the read-only reviewer, `/handoff` before ending a long session.
4. Rate-limited or down? Select a free model with `/models` and continue in the same session. The `build-free` profiles are optional; verify the selected model because V2 retains a session's model when changing agents. No automatic rate-limit fallback is configured.
5. New chat, new account, new machine: paste "Read AGENTS.md and docs/STATE.md, then run /next". Nothing else is required.

## Research-first rule (kept, but scoped)
Before using a moving part (library version, yt-dlp/EJS behavior, Android API-level rule, Gradle/AGP pairing), check the newest source and log it in
`docs/RESEARCH-LOG.md` (URL, date, takeaway, whether it changes the plan). Do not re-research settled facts; `grep` the log by topic first.

## Phases and gates
Phase 1 (MVP) is in progress; Phases 2 and 3 are in `docs/01-PLAN.md`. A new feature area starts only after the owner's phone report on the latest stamped APK.
For the three claude.ai chat prompts (`CLAUDE_PROMPT.md`, `DEBUG_REFINE_PROMPT.md`, `POLISH_UPGRADE_PROMPT.md`) see the notes at the top of each file.
