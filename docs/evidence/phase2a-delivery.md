# Phase 2A delivery: three-destination shell

Date: 17 Nov 2026. Scope from `docs/ROADMAP.md` §Phase 2A: Home / Downloads / Library shell, share-link
canonicalisation, contextual help. Out of scope and not attempted: browser/WebView, site login, playlists,
new post-processing, new permissions.

## What was built (increments, each committed separately)

| Commit | Increment |
| --- | --- |
| `395e930` | `LinkCanonicalizer`: host-specific tracking/share parameter removal; unknown hosts, signed URLs and malformed input returned unchanged. Wired into `MediaEntry.pasted`, so a shared link is stored in its canonical form. 6 new tests. |
| `797bff4` | Removed `AppInfo.FULL_NAME` ("You Tube Downloader Pro") and its two UI uses, per owner decision DR-006; display name is `YTDLP` only. |
| `c16b5bf` | `LibraryCatalog` (pure kind/filter/sort/size/duration rules) + `MediaStoreLibrary` (own `Download/YTDLP` MediaStore rows on API 29+, app-specific folder on 24-28, explicit single-item delete). 4 then 6 tests. |
| `60402ab` | `AppShell` (three destinations, one inset pass, diagnostics stays a local tool), `DownloadsScreen` (queue card), `LibraryScreen` (search, kind filter, open/share/delete with confirmation), `SiteCatalog` + `SourceShortcuts` (9 entries from `contracts/sites.json`, "how to share" help), Home rewired, warnings moved into "Help & current limits". |
| `91054d2` | Home "Recent saves" preview (3 newest), live download percentage on the active Downloads row, MediaStore duration on Library rows. |

## Verified checks (run, output seen)

- `.\gradlew.bat testDebugUnitTest lintDebug --console=plain` → **BUILD SUCCESSFUL in 36s**.
  JUnit XML totals: **353 tests, 0 failures, 0 errors, 1 skipped** (the known Windows symlink test).
  Lint: **7 issues, all Warning, 0 errors** (the same known set: Gradle/Kotlin/coroutines newer-version notices,
  ChromeOS x86_64, `UsableSpace`, monochrome icon).
- `.\tools\build-debug.ps1` produced `dist/app-debug.apk` and `dist/SHA256SUMS.txt` (the helper's own console output
  was lost to a buffered outer PowerShell call, but the artifacts and their hashes were produced and are verified below).
- APK inspection: full-DEX stamp `91054d2` present (`stamp_present: True`, 34,469,424 DEX bytes, 462 entries);
  `apksigner verify --print-certs` → V2 signer `CN=Android Debug`, SHA-256 `66b04ef4d990c10cd6d5b9126cfd3e3ceb214c390f63eb6c09595ff9aa391658`.
- `aapt2 dump permissions`: INTERNET, FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC, POST_NOTIFICATIONS plus the
  AndroidX-generated non-exported receiver permission. **No new permission.** `minSdkVersion 24`, `targetSdkVersion 37`, label `YTDLP`.

Artifact: `dist/app-debug.apk`, **70,747,776 bytes**, SHA-256
`B2D0CC9E6F7AE8E8304456FB6F220E93E62405E16448AD6051595270FB91AF9C` (matches `dist/SHA256SUMS.txt`).
The APK was stamped from source commit `91054d2`; the following commit `5c89a80` changes documentation only, so the
binary and this report stay in sync with no code difference.

## Tests added in this phase

- `LinkCanonicalizerTest` (6): YouTube `si`/`feature` removal while keeping playback parameters, social-host share
  parameters, unknown host and signed-URL pass-through, case/fragment normalisation, ports and valueless parameters.
- `LibraryCatalogTest` (6): kind mapping, name/kind filtering newest-first, blank query, newest slice, byte and duration
  formatting (locale-independent), negative sizes.
- `SiteCatalogTest` (2): shortcut ids match `contracts/sites.json` exactly and in order; every shortcut is an https home page.
- `SessionLogTest` now covers the 8 new fixed UI action names (no payload parameters).

## Honestly not done / not verified

- **No Compose UI tests.** The project has no Compose UI-test dependency (adding one is a build/dependency decision not
  taken in this phase), so the screens themselves are host-compiled but not behaviour-tested. All pure rules behind them are tested.
- **No device run.** Library contents after reinstall, thumbnail/duration values, gallery visibility, share targets, the
  three-tab layout, large text and TalkBack are **deferred, not passed**.
- **Thumbnails are a glyph fallback, not images.** Real MediaStore thumbnails were not implemented in this increment.
- **Per-task size is not shown.** `TaskRecord` stores no byte estimate, so the Downloads row shows phase, requested
  ceiling and format ids, plus the live percentage of the one active download. No invented sizes.
- **Durations only for API 29+ MediaStore rows**; legacy app-folder items show none.
- Home now reads the app's own folder on each resume (read-only, no device-wide media query); unmeasured on a phone.

## Owner actions still open

- Screenshot set for approval (dark, light, large text) — cannot be produced from host checks.
- Confirm on a phone: Library list/search/filter/open/share/delete, Home recent saves, Downloads live percentage,
  and that no saved file reappears or disappears after reinstall.

## STOP

Phase 2A implementation and host checks are complete; device/screenshot acceptance is deferred. Waiting for the
owner's `next` before any Phase 2B work.
