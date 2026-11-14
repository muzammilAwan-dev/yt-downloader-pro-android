# 09. Test strategy

What CI can prove and what it cannot.

## 1. Pyramid

| Level | What | Runs in CI |
|---|---|---|
| Unit (JVM) | State machine transitions, `DownloadError` mapping from engine output, option validator, cookie-file writer, config parser, format-picker logic, free-space check | `testDebugUnitTest` on every push/PR |
| Fake-engine integration (JVM/Robolectric) | Queue + worker with a fake `Engine`: success, 403 -> re-resolve, offline -> auto-resume, no space -> warning, cancel mid-stage, process death (re-create repository from DB) | same job |
| Room | In-memory DB tests for migrations (export schemas; `MigrationTestHelper` for instrumented) | JVM or emulator |
| WorkManager | `TestListenableWorkerBuilder` for the worker [K] | JVM |
| Instrumented (emulator) | Share intent -> bottom sheet; notification actions; MediaStore publish; SAF permission persistence | `instrumented-tests` job, only once `androidTest` sources exist |
| Device (manual) | Real engine: Phase 0 spike, 1080p merge, FGS timeout, low-storage, airplane mode toggle | **Not CI**; log results in `docs/evidence/` |

## 2. Tests that must exist before 1.0

1. Validator rejects every entry in `blocked_always` and any unknown option; accepts every option the UI can produce.
2. Each error code in `contracts/errors.json` has a test mapping a representative yt-dlp message to it.
3. Queue survives process death in every stage.
4. `onTimeout` pauses tasks and re-enqueue resumes them (simulate with the adb compat flags from doc 06).
5. Cookie temp file is deleted on success, failure and cancellation.
6. Task history never contains cookies (assert on the serialized row).
7. Release build has: no cleartext traffic, no extra permissions beyond the budget (a CI step diffing the merged manifest is a good guard).

## 3. What CI cannot prove

- That YouTube works today. GitHub-hosted runner IPs may be treated differently from a phone's (one yt-dlp issue reports an IP-specific block). [V anecdote]
  Keep any live-site canary out of required checks, run it from a device or self-hosted runner, and treat a red canary as "update the engine".
- Battery use, thermal behaviour, OEM background-kill behaviour. Test manually on at least one aggressive-OEM phone.

## 4. Workflow behaviour (see `.github/workflows/android.yml`)

- Push to `main` and pull requests: `unit-tests` then `instrumented-tests` (the emulator steps are skipped until androidTest sources exist). No APK is published. Docs-only changes are ignored by path filter.
  If you make these jobs *required* checks, note that path-filtered workflows that don't run can leave required checks pending; add an always-run summary job or drop the path filter.
- Manual run: same tests (unless `skip_tests`), then `build-artifact` builds debug or release, writes `SHA256SUMS.txt` and uploads the APK(s). Release needs the four signing secrets.
- On a test failure only, diagnostic reports are uploaded for 7 days (those are the only artifacts the non-manual paths ever create).

## 5. Static analysis

Add detekt and ktlint when the project exists; the workflow runs them automatically if the tasks are present. Add a dependency-license report task and a merged-manifest permission check.
