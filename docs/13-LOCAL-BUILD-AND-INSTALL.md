# 13. Build the APK on your PC from the command line, install it on your phone, read the log

Goal: the Android equivalent of `dotnet build`. Commands below assume Windows PowerShell (macOS/Linux use `./gradlew`). The scripts and commands in this file are
originally came from untested reference notes. Windows helper/Gradle builds are now verified on the owner's PC (see
`docs/dev-setup.md` and `docs/PROGRESS.md`); other-host and phone commands still require their own verification.

## 1. One-time setup (no Android Studio needed for building)

1. **JDK 17** (Temurin or similar). Check: `java -version`.
2. **Android command-line tools** (Google's "command line tools only" download). Put them at e.g. `C:\Android\cmdline-tools\latest\`.
3. Set `ANDROID_HOME` to `C:\Android` and add `C:\Android\cmdline-tools\latest\bin` and `C:\Android\platform-tools` to `PATH`.
4. Install what the build needs and accept licenses:
   `sdkmanager --licenses` then `sdkmanager "platform-tools" "platforms;android-<API the project targets>" "build-tools;<version the project uses>"`
   (the AI must tell you the exact API and build-tools versions after it creates the project; do not guess them).
5. **Gradle wrapper:** the project must contain `gradlew`, `gradlew.bat` and `gradle/wrapper/`. A text-only AI agent cannot create the wrapper jar, so generate it once with
   `gradle wrapper` (install Gradle once via winget/scoop/choco) or by creating the project in Android Studio once. After that, only `gradlew` is used and it downloads the right Gradle itself.
   *Easier alternative:* install Android Studio just to get the SDK, JDK and wrapper; you can still build only from the terminal afterwards.

## 2. Build commands (the `dotnet` mapping)

| dotnet | Android/Gradle |
|---|---|
| `dotnet test` | `.\gradlew.bat testDebugUnitTest lintDebug` |
| `dotnet build` | `.\gradlew.bat assembleDebug` |
| output in `bin\Debug` | `app\build\outputs\apk\debug\*.apk` (default location; verify) |
| `dotnet publish -c Release` | `.\gradlew.bat assembleRelease` (needs signing env vars, doc 07) |

Debug APKs are signed automatically with a local debug key, so they install normally. Always build the same way: test, then build, then copy to `dist\`. Helper scripts: `tools/build-debug.ps1`
(Windows) and `tools/build-debug.sh` (macOS/Linux). On this PC script execution is disabled; use the already-tested,
process-scoped invocation `powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\build-debug.ps1` instead of
changing a persistent execution policy. Identify each phone log by `gitSha`, not the version name alone. The first UI
tester retains `0.1.0-phase1-preview` / versionCode 2; identify delivery by its source stamp, not version name alone.
Phase 1 implementation is host-delivered; Android acceptance remains deferred, not passed.

### Optional phone-free verification (Windows, no installation)

From the repo root, using the **existing Python 3.11+**, JDK, SDK build-tools and cached Gradle/dependencies:

```powershell
python -I tools/phone-free.py
```

This small stdlib helper does not connect to your phone, use an emulator, install packages, download an engine,
assemble an APK or replace `dist/app-debug.apk`. It runs from an unchanged temporary copy under
`%LOCALAPPDATA%\Temp\opencode\ytdlp-phone-free-*` and removes **only that copy** afterward. It keeps local
`summary.json` / `summary.md` reports there and prints their path; it adds no service, scheduled job or project dependency.
Normal Gradle outputs/cache and build-tool scratch files remain in their usual locations.

- Checks built/copied/SHA256SUMS agreement (including PowerShell's UTF-16 checksum file), the actual **own BuildConfig DEX stamp**,
  stamp ancestry and unchanged tracked app/build/contracts inputs; rejects untracked app/build inputs. Later docs/tool commits are allowed.
- Uses existing SDK tools to verify signing against the documented local debug certificate, identity/SDK/ABI/permissions;
  compares the complete packaged engine/notices against source and verifies the engine/EJS pin and seven arm64 native entries.
- Runs helper regressions, then fresh `testDebugUnitTest lintDebug --offline --rerun-tasks --console=plain -PgitSha=<APK stamp>`
  using the **already-extracted cached Gradle distribution directly**, so even the wrapper cannot initiate a download.
  The non-daemon setting is child-process-only; missing cached tools/dependencies cause failure, not installation.
- Requires fresh unit/lint/video-fixture reports, runs synthetic video and exact original-audio selectors against the verified
  bundled engine with socket connections blocked, then rechecks the APK hash and source stamp. No site/media extraction is performed.
- Optional: `python -I tools/phone-free.py --baseline "<previously verified APK path>"` additionally requires identical
  assets, native files, binary manifest and icon entries. An intentional change needs review, not silently relaxed checks.
  `--apk` selects an existing candidate (its neighboring SHA256SUMS and the module's built APK must still agree);
  `--certificate` sets an explicitly trusted lowercase SHA-256 signer for another owner's build.

Exit 0 / `PASS_HOST_CHECKS_DEVICE_REQUIRED` means **host checks only**. Exit 1 / `FAILED_HOST_CHECKS` means a host check,
report or temporary-copy cleanup failed. Reports contain fixed failure guidance, counts and hashes, not raw command output/secrets.
Changed temporary copies are preserved, not deleted. Run `python -I tools/test_phone_free.py` for the stdlib regressions alone.
Verified on this PC with Python 3.14.6; no other-host support is claimed. The optional helper is removable later by deleting only
`tools/phone-free.py`, `tools/test_phone_free.py`, this usage section and the specific generated report folders you no longer need;
do not delete the shared temp root, Gradle/SDK installation or other project outputs.

**The owner still needs to install and test the phone APK:** picker interaction, Android engine/runtime execution,
M4A/WebM audible playback and file visibility, another video preset/picture/sound, the earlier failing long sample and complete
Share session logs. Background/lock/notification Cancel/newer APIs are separate device checks; a green host report proves none of these.
No phone-PC connection is required: transfer/install manually as below. Current ready tester/sequence:
`docs/evidence/phase1-delivery.md` (`56c15c4`, not a new APK from this helper). The latest actual owner-tested APK is still `e37686c`.

## 3. Get it onto the phone and install

1. Copy the APK from `dist\` to the phone: USB cable (File Transfer), Google Drive, Telegram "Saved Messages", or `adb install -r dist\<file>.apk` (fastest; needs USB debugging on).
2. On the phone, open the APK from Files/Downloads. Android will ask to allow "Install unknown apps" for that app (Files/Chrome). Allow it, install, open.
3. Updating: install the new APK over the old one. This works only if both were signed with the same key (debug builds from the same PC use the same debug key; a CI-built debug APK uses a different one, so uninstall first when switching).

## 4. Where the log goes (so you can just grab a file)

Tester (debug) builds write two copies:
1. **Private rotating log** in app storage (always, as in doc 12).
2. **Public copy in the phone's `Downloads/YTDLP-logs/`** as one text file per app session, e.g. `ytdlp-log-20261115-140322.txt`, written through MediaStore (no storage permission on Android 10+ [K]).
   Keep only the last 5 sessions; write a new file per session instead of overwriting (MediaStore creates duplicate names such as `log (1).txt` when you re-insert the same name).
   On Android 7-9 (minSdk 24 means these exist) a public write needs a storage permission that the permissions budget forbids: there, skip the public copy and rely on **Share log**.
   *Implemented in Phase 0 as `log/MediaStoreLogMirror.kt` (5 sessions). After the owner rename, new logs use
   `Download/YTDLP-logs`; older files in `Download/ClipHarbor-logs` are not migrated or deleted.*
3. The same file is available from **Home -> Diagnostics & local logs -> Share log** (share sheet), and **Run self-test** writes its result block into the current session file.

Public-folder trade-off: files in Downloads are not private. Redaction (doc 12 section 4) still applies, and the public copy is **debug/tester builds only**; release builds log to the private folder and export
only when you tap Share log.

To get the file to the AI: open Downloads on the phone, attach the newest `ytdlp-log-*.txt` or the newest **Share log**
`app-YYYYMMDD-HHmmss.log` export to the chat (or copy it to your PC), together with the debug prompt.
Fallback with a cable: `adb pull /sdcard/Download/YTDLP-logs/ .` [K] or `adb logcat -d > logcat.txt`.

## 5. Daily loop

```
edit/ask AI -> .\tools\build-debug.ps1 -> adb install -r dist\*.apk (or copy to phone) -> open app -> Run self-test / use feature -> grab log from Downloads -> give to AI
```

## 6. Phase 1 preview phone checks

- Install the newly handed-off, stamped APK over the previous local debug build; confirm the stamp in Copy summary/log.
- Open Home, paste one HTTPS link and tap **Find formats**. Choose an available standard video+audio preset
  (144/240/360/480/720/1080/1440/2160p), then tap **Download video + audio**. Missing tiers are hidden, not disabled
  placeholders or fake promises. Labels use the reported source resolution; 1440 is QHD, 2160 is 4K. The cap uses
  the shorter edge, including portrait video. No upscaling.
- In the original-audio tester, **Audio only** is a separate section with reported streams, not converted MP3 choices.
  Selecting it clears video selection and changes the button to **Download audio only**; selecting video clears audio.
  Verify original M4A/AAC and WebM/Opus if offered, playback with sound, file visibility and post-result session logs.
  Detailed artifact/phone sequence: `docs/evidence/original-audio-tester.md`. Its UI/audio playback are not phone-verified yet.
- Try a lower preset and a higher available one on the same source; compare the selected dimensions in completion/logs.
  Nonstandard source sizes are shown honestly; lower bounded fallbacks are possible. High-resolution merges use MKV,
  lower merges prefer MP4/MKV, and filenames retain the true extension with an `up-to-…p` ceiling tag.
- For the foreground milestone APK, start a download, press the phone's Home button or switch apps, and check the
  ongoing notification. Tap it during the active download: Home must reconnect to the same attempt, not start another.
  Briefly lock/unlock the screen too. Finish a background download and check the saved file in Files/Downloads.
- Start another attempt and tap **Cancel** in its notification before saving begins. Return to Home; check the
  canceled result, notification removal and absence of a new saved file. Retry only through fresh formats + confirmation.
  Saving is a commit boundary: the notification Cancel action disappears then; an old action must not interrupt it.
- Android 10–12 must not request notification runtime permission. On Android 13+, **Enable notifications** is optional
  in the quality picker. Denying/dismissing must not start or block a download; allow it only by choice, then tap Download.
  If app/channel notifications are disabled in Android settings, Home must say so; use Home's Cancel while downloading.
- Background execution, notification delivery and cleanup remain phone-unverified until these checks are reported.
  Android 15+ timeout testing needs a separate compatible device; the Android 10 phone cannot exercise `onTimeout`.
- After saving on Android 10+, tap **Open saved file**; confirm picture/sound and find it under Downloads/YTDLP.
- Share one text link from another app to YTDLP. It must only prefill for review, not begin extraction or a download.
- If convenient, send Home/picker screenshots (redact personal URLs/titles), light/dark or larger-text observations,
  and the newest exported log. Native share/UI/engine/cancel/open-file checks are not implied by JVM tests or compilation.
- For the full-session logging build, open the app, paste/find formats, change quality, download/cancel/open a file as desired,
  then Diagnostics -> **Share session log**. One redacted `.txt` should contain startup, your action sequence and engine/stage details
  through that Share tap, including background/reopen events. The on-screen preview is only a tail; it is not the export.
  Clear logs deliberately starts a new timeline. Do not clear between reproducing a bug and sharing its log.

Owner confirmed first-preview `ec146df` playback with sound and supplied dark Home/picker/progress/saved screenshots.
Second-preset playback/dimensions and MKV playback still need their own phone checks. The final Phase 1 tester adds host-tested
durable queue/process-recovery wiring and advanced exact-video selection; Android execution is not implied by those checks.
Thumbnail embedding, audio conversion and automatic queue scheduling remain unavailable. Original audio-only and exact-video
selection are CLI-tested; Android playback and radio interaction still require the owner's deferred report.
