---
description: Run the full verification (tests, lint, debug build) and report real results only
---
Run `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain` on Windows (`./gradlew ...` elsewhere). Do not change any file.

Report, in this shape and nothing more:
- Command and the final `BUILD SUCCESSFUL/FAILED in <time>` line, copied from the output.
- Test totals read from `app/build/test-results/testDebugUnitTest/*.xml` (tests, failures, errors, skipped). Say if the tasks were UP-TO-DATE and therefore reused earlier reports rather than executing again.
- Lint errors and warnings from `app/build/reports/lint-results-debug.xml`, and the names of any warning not listed as known in `docs/PROGRESS.md`.
- On failure: the first real error line and the file it points to. No guesses about the cause beyond what the output shows.

If Gradle cannot run in this environment, say so and give the exact command for the owner. Extra instructions: $ARGUMENTS
