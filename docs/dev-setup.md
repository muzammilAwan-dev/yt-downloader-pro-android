# Dev setup (this PC)

Everything below was executed on this machine on **2026-11-14**. Versions were not guessed: sources and dates are in
`docs/RESEARCH-LOG.md` (R1–R11). The toolchain proof run (`gradlew testDebugUnitTest lintDebug assembleDebug`) is pasted in
`docs/PROGRESS.md`.

## Installed toolchain

| Piece | Version | Location | How |
|---|---|---|---|
| Git | 2.55.0.windows.4 | `C:\Program Files\Git` (already present) | pre-existing |
| JDK | Eclipse Temurin **17.0.21+9** | `C:\Users\Malku\dev\tools\jdk-17` | official zip from api.adoptium.net, SHA-256 verified |
| Android command-line tools | **23.0.0** (build 16111833) | `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest` | official zip from dl.google.com, SHA-1 verified |
| Android platform-tools (`adb`) | **37.0.1** | `%LOCALAPPDATA%\Android\Sdk\platform-tools` | `android sdk install platform-tools` |
| SDK platform | **android-37.0** (Android 17, API 37) | `%LOCALAPPDATA%\Android\Sdk\platforms\android-37.0` | `android sdk install platforms/android-37.0` |
| Build-tools | **37.0.0** | `%LOCALAPPDATA%\Android\Sdk\build-tools\37.0.0` | `android sdk install build-tools/37.0.0` |
| Gradle (bootstrap only) | 9.6.0 | `C:\Users\Malku\dev\tools\gradle` | official zip, SHA-256 verified; used once to generate the wrapper |
| Gradle wrapper | 9.6.0 | repo `gradlew`, `gradlew.bat`, `gradle/wrapper/` | committed |

Project build stack (see `gradle/libs.versions.toml`): **AGP 9.5.1**, **Kotlin 2.2.10** (the KGP version AGP 9.5.1 bundles —
AGP 9 compiles Kotlin itself, so no `kotlin-android` plugin), Compose BOM **2026.09.00**, `compileSdk`/`targetSdk` **37**,
`minSdk` **24** (judgement call, revisit if a device report disagrees).

### Environment variables (user scope, no admin)

| Variable | Value |
|---|---|
| `JAVA_HOME` | `C:\Users\Malku\dev\tools\jdk-17` |
| `ANDROID_HOME` | `C:\Users\Malku\AppData\Local\Android\Sdk` |
| `ANDROID_SDK_ROOT` | same as `ANDROID_HOME` |
| `Path` (user) | appended `%JAVA_HOME%\bin`, `%ANDROID_HOME%\cmdline-tools\latest\bin`, `%ANDROID_HOME%\platform-tools` |

A shell that was already open before setup does not see these until it is restarted (Windows passes the environment block
to new processes from the parent shell). **Open a new terminal** before building.

## Commands

```powershell
# from the repo root, in a fresh terminal
.\tools\check-env.ps1          # environment sanity check
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
.\tools\build-debug.ps1        # same build, then copies the APK to .\dist\
```

Install on the phone (USB debugging on, phone unlocked):

```powershell
adb devices
adb install -r .\dist\<apk-name>.apk
```

Build output without `tools\build-debug.ps1` is `app\build\outputs\apk\debug\app-debug.apk`. `dist/` is git-ignored.

## Notes and gotchas

- **AGP 9 built-in Kotlin**: do not add `org.jetbrains.kotlin.android`; it fails with
  "Cannot add extension with name 'kotlin'". Use `kotlin { compilerOptions { } }` instead of `android.kotlinOptions { }`.
- **`sdkmanager` is deprecated** in command-line tools 23.0. It still works for `--list`, but it splits package names at
  `;` when installing. The replacement is the `android` CLI:
  `android sdk install platforms/android-37.0 build-tools/37.0.0`, `android sdk list`, `android sdk update`.
- **Licenses**: `sdkmanager --licenses` reports "no longer needed" in 23.0; licenses are accepted as packages install.
- **Platform package name** is `platforms;android-37.0` (there is no `platforms;android-37` in the repository index).
- **JDK 17 exactly** matches AGP 9.5's requirement. Newer JDKs (21/25) are accepted by the JDK range but 17.0.21 is what
  this machine is proven with.
- **No GitHub remote yet.** When ready: `git remote add origin <url>` then `git push -u origin main`.
- **Git identity** is local to this repo: `muzammilAwan-dev <231211902+muzammilAwan-dev@users.noreply.github.com>`,
  set with `git config user.name/user.email` (no `--global`). Change it with the same commands.

## Re-creating this environment on another PC

1. Install Git, then a JDK 17 (Temurin) at a user-writable path; set `JAVA_HOME` at user scope.
2. Download the Android command-line tools zip from `https://dl.google.com/android/repository/commandlinetools-win-<build>_latest.zip`
   (current build: 16111833), unpack so that `cmdline-tools\latest\bin\sdkmanager.bat` exists under
   `%LOCALAPPDATA%\Android\Sdk`, set `ANDROID_HOME`/`ANDROID_SDK_ROOT`.
3. `android sdk install platform-tools platforms/android-37.0 build-tools/37.0.0`.
4. Clone the repo and run `.\gradlew.bat assembleDebug` — the wrapper downloads Gradle 9.6.0 itself. No global Gradle needed.
5. Optionally verify checksums: JDK zip SHA-256 `98518ecf…81f84b`, Gradle zip SHA-256 `bbaeb2fe…a529a01` (full values in
   `docs/RESEARCH-LOG.md`).
