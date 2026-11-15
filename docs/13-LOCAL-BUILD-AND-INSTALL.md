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
preview now uses `0.1.0-phase1-preview` / versionCode 2; it is not the full completed Phase 1.

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
  (144/240/360/480/720/1080/1440/2160p), then tap **Download video + audio**. Missing tiers must be disabled, not fake
  promises. 1440 is QHD; 2160 is 4K. The cap uses the shorter edge, including portrait video. No upscaling.
- Try a lower preset and a higher available one on the same source; compare the selected dimensions in completion/logs.
  Nonstandard source sizes are shown honestly; lower bounded fallbacks are possible. High-resolution merges use MKV,
  lower merges prefer MP4/MKV, and filenames retain the true extension with an `up-to-…p` ceiling tag.
- Keep the app open: this preview is activity-lifetime only, without durable queue or foreground notifications.
- After saving on Android 10+, tap **Open saved file**; confirm picture/sound and find it under Downloads/YTDLP.
- Share one text link from another app to YTDLP. It must only prefill for review, not begin extraction or a download.
- If convenient, send Home/picker screenshots (redact personal URLs/titles), light/dark or larger-text observations,
  and the newest exported log. Native share/UI/engine/cancel/open-file checks are not implied by JVM tests or compilation.

Owner confirmed first-preview `ec146df` playback with sound and supplied dark Home/picker/progress/saved screenshots.
The new standard-presets UI/native transfer and MKV playback still need their own phone check. This remains an
activity-lifetime preview, not finished Phase 1. Thumbnail embedding and audio-only/exact-format selection are not included.
