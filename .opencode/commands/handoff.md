---
description: Refresh docs/STATE.md and append a short progress entry for a new session
---
Prepare a clean hand-off. Change docs only.

1. Rewrite `docs/STATE.md` so it is true right now and stays under about 80 lines: current phase, what is done, the last real check result (copied from output you or the owner produced, never invented), unchecked next steps in order, open owner checks, risks, environment. Run `git log -5 --oneline` and `git status --short` and use the real HEAD and cleanliness.
2. Append at most 10 lines to `docs/PROGRESS.md` for this session. Do not paste full Gradle output there; three summary lines are enough. Raw evidence for a phase gate goes in `docs/evidence/`.
3. If research changed a fact, add a numbered entry to `docs/RESEARCH-LOG.md` (URL, date, takeaway, whether it changes the plan).
4. Commit as `docs: hand-off <date>`. Do not push.
5. Print the first message the owner should paste into a fresh session: it must say to read `AGENTS.md` and `docs/STATE.md`, then run `/next`.
