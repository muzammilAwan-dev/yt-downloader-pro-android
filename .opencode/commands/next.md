---
description: Do the next small step from docs/STATE.md, verify it, commit it, then stop
---
Do exactly ONE step. `docs/STATE.md` and `AGENTS.md` are already loaded; do not re-read `docs/PROGRESS.md` or `docs/RESEARCH-LOG.md` in full (use grep).

1. Take the first unchecked item under "Next steps" in `docs/STATE.md` (or the task in: $ARGUMENTS). In two lines, say what you will change and which files (about 5 at most). Read only the detail docs that step needs.
2. If the step adds logic, write the test first, against the fake `Engine`. Never add a live-YouTube test to CI.
3. Make the smallest change that does the job. Check every library version and API before using it; mark anything you cannot check `TODO(verify)`.
4. Run `.\gradlew.bat testDebugUnitTest lintDebug --console=plain` on Windows (`./gradlew ...` elsewhere). Quote the final `BUILD` line plus the test and lint counts. If it fails, fix the root cause once; if it still fails, stop and report. Never describe unrun work as passing.
5. Commit with a Conventional Commit message. Do not push.
6. Update `docs/STATE.md` (tick the item, update "Last recorded check") and append at most 10 lines to `docs/PROGRESS.md`: what changed, the 3-line check summary, what is still untested.
7. Stop. Report in five lines or fewer: what changed, check result, what is untested, what the owner must test on the phone (if anything), the next step.
