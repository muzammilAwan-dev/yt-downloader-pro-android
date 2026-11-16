# Available video + original audio tester — 16 Nov 2026

## Scope

Owner requested continuing through a meaningful stamped APK, not stopping at intermediate commits.
**Verified stamped tester `e37686c` was installed by the owner.** Supplied logs confirm original WebM audio and a
720p MP4 save; screenshots confirm the overlong picker. Audible playback remains unconfirmed. Updated evidence
and phase-level cadence: `phase1-owner-feedback.md`. Historical artifact/install sequence below is not a new request.

- Only source-backed video rows are shown; labels use actual reported short-edge resolution, including portrait/nonstandard sizes.
- Separate **Audio only** and **Video + audio** sections share one exclusive selection and an explicit Download confirmation.
- Original audio streams only: M4A/AAC, WebM/Opus or another supported original container if actually reported.
  Format IDs and reported approximate sizes distinguish streams; unknown sizes stay unknown. No invented bitrate choices,
  MP3 conversion, audio extraction from a muxed video, thumbnail embedding, login/cookies or subtitle work.
- Task-private exact audio ID selector, original extension, explicit audio codec evidence and no video dimensions are required;
  no fallback to another ID/video or merge/transcode flags. WebM audio uses **audio/webm**, not video/webm.
- Existing foreground ownership, replay, Cancel/timeout/saving fences and startup-to-Share redacted logging remain in use.
  Notifications are optional and media-neutral. No Room queue/process-death recovery, new permissions or runtime updater.
- APK keeps verified **yt-dlp 2026.10.26 / EJS 0.8.0**, Android library/ffmpeg 0.18.1, native runtimes, package and owner icon.
  Android 10 is the primary phone baseline; minSdk remains 24. New UI/audio playback are **phone-untested**.

## Verification

- Implementation HEAD: **`e37686c863ca65118e664fc62f15502d67219703`**. Only handoff documentation was uncommitted
  during the build; app source, dependencies, assets and manifest were committed. Later doc commits do not change this stamp.
- Ran `powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\build-debug.ps1` **without SkipTests**,
  using shell-only `GRADLE_OPTS=-Dorg.gradle.daemon=false`. No persistent/project workaround.
  Checks: **BUILD SUCCESSFUL in 2m 45s**; APK: **BUILD SUCCESSFUL in 54s**.
- Fresh XML reports: **284 total / 283 passed / 1 Windows symlink skip / 0 failures/errors**;
  lint **0 errors / 7 known warnings**. Earlier full integration checks: BUILD SUCCESSFUL in 3m 14s.
- `dist/app-debug.apk`: **70,276,358 bytes**, SHA-256
  **`C923865E6A01F464358F2B30570FF5A25A84DD5275B7E5FDF8DEA245EAAD4FE6`**.
  Built APK, copied APK and `dist/SHA256SUMS.txt` agree.
- Parsed the actual APK's own BuildConfig DEX static field: **`GIT_SHA="e37686c"`**; package
  `io.github.muzammilawan.clipharbor`, versionCode **2** / **0.1.0-phase1-preview**. Own audio/runner/startup/Home/Activity classes present.
- Embedded engine exactly matches source and `f96194e`: **3,073,072 bytes**, SHA-256
  `e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3`; actual zip contains yt-dlp 2026.10.26,
  EJS 0.8.0 and both local solver scripts. All bundled full license/notice assets match source and the previous tester byte-for-byte.
- Preserved/hash-checked `f96194e` in approved temporary storage; compared both actual APKs.
  **All seven native entries, binary AndroidManifest.xml and three icon entries are byte-identical**.
  The owner icon and native runtimes were not rebuilt/replaced by this feature.
- `apksigner verify --verbose --print-certs` verifies v2 signing; signer SHA-256
  **`66b04ef4d990c10cd6d5b9126cfd3e3ceb214c390f63eb6c09595ff9aa391658`**, matching `f96194e`/`6bb477b`.
  `aapt2` reports **YTDLP**, **minSdk 24 / target 37 / arm64-v8a / debuggable**.
  Permissions remain INTERNET, FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC, POST_NOTIFICATIONS and the
  pre-existing AndroidX app-local signature permission. No new permission or battery exemption.
- Offline host checks: original-audio runner/evidence/Home tests and exact-selector smokes; bundled CLI synthetic replay
  emitted `251/NA/NA` selection and `251/none/opus` codec evidence with socket connections blocked and media skipped.
  Fresh JVM-generated **96 video selector cases passed** on verified 2026.10.26 with socket connections blocked.
  The historical checker expects 2025.11.12; only the temporary host harness changed that expected pin, not the repo tool/planner.
- Verification-harness correction: initial baseline self-check looked for EJS version in `__init__.py`; it imports `_version.py`.
  Corrected the verifier to check actual `_version.py`; baseline and new APK checks passed. No packaged code/asset changed.

**Not phone proof:** no Compose instrumentation, Android installation/native execution/audio playback or sustained background/Cancel
test ran in this step. Existing owner `f96194e` video/runtime evidence remains historical; no general 403 fix is claimed.

## Owner phone sequence

1. Copy the new `dist/app-debug.apk` to the phone and install **over `f96194e` without uninstalling/clearing data**.
   Confirm **`gitSha=e37686c`** in Diagnostics → Copy summary/log, and yt-dlp 2026.10.26 / EJS 0.8.0 after startup.
2. Find formats on a short known-working source while visible on the same Wi-Fi. Send picker screenshot(s) showing
   **Audio only** and **Video + audio**; missing video tiers must be absent, not disabled placeholders or fake resolutions.
   A source with no eligible original audio should say so, not invent converted choices.
3. Choose **M4A · AAC** if offered. Video radios must clear and the button must say **Download audio only**.
   Confirm Download; after saving, open the file, verify it plays sound and appears in Downloads/YTDLP. Send the complete
   **Share session log** export after the result, even on failure. Audio success must not claim video dimensions.
4. Refresh formats and try **WebM · Opus** if offered; confirm original `.webm`, audio playback and saved-file visibility.
   Share the post-result session log. A player's inability to open it is a separate playback compatibility result.
5. Refresh/select a second available **video** preset on the known-working sample. Audio radios must clear and the button
   must say **Download video + audio**. Confirm selected dimensions, picture **and sound**, then share the complete log.
6. Retry the earlier failing long video while continuously visible, at the same available preset. Record success/error and
   share its complete session log. The previous 292 MiB video save on `f96194e` did not retest the 528/873 MiB failures.
7. Do not clear logs between an attempt and Share. Background/lock/notification Cancel checks wait until visible transfers
   are reliable. High-resolution MKV sound/playback remains a separate open check; no general 403 fix is claimed.

Useful new log fields: `reportedMaxResolution`, `visibleResolutions`, `audioChoices`, `mediaMode=audio|video`, selected IDs,
`CLIPHARBOR_AUDIO` evidence and publication `mime`/`bytes`. None is a complete format inventory or a media-file probe.
