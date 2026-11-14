# 14. Setup task for the local AI agent (environment + local git repo + folder structure)

For an agent that can run shell commands on the owner's PC (opencode or similar). A Claude **web** chat cannot do this; there, ask it to produce the same steps as a checklist for the owner.
Everything here is **UNTESTED**: the agent must verify every command, URL and version against official pages first and record them in `docs/RESEARCH-LOG.md`.

## 0. Safety rules for installing things on the owner's PC

1. Detect the OS and shell first (`ver`/`uname`, PowerShell vs bash). Assume Windows only if detected.
2. **Before installing anything, print the full plan** (what, from where, where it goes, whether admin is needed) and wait for the owner's explicit "yes". Then proceed without re-asking for each item.
3. Download only from official sources: developer.android.com (command-line tools, platform-tools), adoptium.net or the OS package manager (JDK 17), gradle.org (Gradle, only to generate the wrapper), git-scm.com (Git).
   Verify published checksums when the site provides them. Never pipe a script from the internet into a shell. Never disable antivirus or SmartScreen.
4. Prefer **user-level** installs (no admin). Use `winget`/`scoop`/`brew`/`apt` only if already present and the owner agrees. Set environment variables at **user** scope.
5. Never put secrets (signing keys, passwords, tokens) in the repo. Do not ask the owner for GitHub credentials; the remote is added later by the owner.
6. Do not touch anything outside the folders listed below, except PATH/user env vars that the owner approved.

## 1. Where things go (outside the repo vs inside)

Outside the repo (shared toolchain, never committed):
```
<home>\dev\tools\jdk-17\            JDK 17 (or the OS package manager's location)
<home>\dev\tools\gradle\            Gradle, only needed once to generate the wrapper (can be deleted after)
%LOCALAPPDATA%\Android\Sdk\         ANDROID_HOME (same default Android Studio uses, so it can adopt it later)
    cmdline-tools\latest\           Google command-line tools (sdkmanager, avdmanager)
    platform-tools\                 adb
    platforms\android-<API>\        the API level the project targets
    build-tools\<version>\          the version the project uses
<home>\dev\repos\<app-name>\        the repo (below)
```
Choose `<app-name>` as a placeholder now (e.g. `ytdlp-android`); renaming later is a package-id decision for the owner (`docs/11-BRANDING-AND-UX.md`).

Inside the repo (professional, conventional Android layout):
```
<repo>/
  app/                              application module (src/main, src/test, src/androidTest)
  core/  engine/  download/  feature/   add as modules only when the code needs them (docs/02)
  gradle/libs.versions.toml         version catalog (single place for dependency versions)
  gradle/wrapper/                   Gradle wrapper (committed)
  docs/                             plan, research log, progress, decisions
  docs/decisions/                   short ADRs: 0001-license-gpl3.md, 0002-engine-youtubedl-android.md, ...
  contracts/                        errors, allowed options, sites
  tools/                            build-debug.*, check-env.*, verify_handoff.py
  fastlane/metadata/android/en-US/  store metadata (F-Droid layout), added in Phase 3
  .github/                          workflows, dependabot
  dist/                             local APK output (git-ignored)
  .editorconfig  .gitignore  .gitattributes  LICENSE  README.md  CHANGELOG.md  SECURITY.md  THIRD_PARTY_NOTICES.md  AGENTS.md
  build.gradle.kts  settings.gradle.kts  gradle.properties  gradlew  gradlew.bat
```
Do not commit: `local.properties`, `*.jks`/`*.keystore`, `build/`, `.gradle/`, `.idea/`, `dist/`, log files.

## 2. Steps the agent performs (in order)

1. **Inspect** what is already installed: `git --version`, `java -version`, `echo $env:ANDROID_HOME`, `adb version`, `sdkmanager --version`, `gradle --version`. Report the table (present/missing/version).
2. **Research** current official instructions: Android command-line tools download page and `sdkmanager` usage; the JDK that the newest stable Android Gradle Plugin requires; the Gradle version compatible with that AGP;
   the newest stable API level and matching build-tools. Log URLs and versions in `docs/RESEARCH-LOG.md`.
3. **Print the install plan** (section 0.2) and wait for approval.
4. **Install/set up** missing pieces into the folders above: Git, JDK 17 (or the version AGP requires), Android command-line tools, platform-tools, the SDK platform + build-tools chosen in step 2, accept licenses
   (`sdkmanager --licenses`), set `ANDROID_HOME` and PATH at user scope. Open a **new** shell and re-check versions.
5. **Create the repo folder** `<home>\dev\repos\<app-name>`, copy the hand-off package contents into it, and run:
   `git init -b main`; ask the owner for the name/email to use **for this repo only** (`git config user.name/user.email` local); commit the package as `chore: import hand-off package`.
   No remote yet. Add a one-line note in `docs/dev-setup.md`: when ready, `git remote add origin <url>` then `git push -u origin main`.
6. **Add repo hygiene files** (the package already ships `.gitignore`, `.gitattributes`, `.editorconfig`; review them for the chosen tools): `LICENSE` (GPL-3.0 text from gnu.org), `README.md` (project name, what it is, build commands),
   `CHANGELOG.md` (Keep a Changelog), `SECURITY.md`, `THIRD_PARTY_NOTICES.md` (stub), `docs/decisions/0001-license-gpl3.md`.
7. **Generate the Gradle project + wrapper:** minimal Kotlin + Compose "hello" app in `app/` using the version catalog; run `gradle wrapper --gradle-version <chosen>` once; commit `gradlew`, `gradlew.bat`, `gradle/wrapper/*`. Verify `gradle/wrapper/gradle-wrapper.properties` points to an official distribution URL.
8. **Prove the toolchain**: run `tools/check-env.ps1` (or `.sh`), then `gradlew testDebugUnitTest lintDebug assembleDebug`. Paste the real outputs into `docs/PROGRESS.md`. Confirm `dist/` receives an APK via `tools/build-debug.*`.
9. **Write `docs/dev-setup.md`**: exact versions installed, install locations, env vars set, the one-line commands to build/test/install, and how to re-create the environment on another PC.
10. Commit in small conventional commits (`chore:`, `build:`, `docs:`). Tag `setup-complete`. Update `docs/PROGRESS.md`, then **stop and report** before Phase 0.

## 3. Git conventions

- Branches: `main` (always builds and passes tests); work on `feat/<topic>`, `fix/<topic>`; merge when `testDebugUnitTest lintDebug` is green.
- Commits: Conventional Commits (`feat:`, `fix:`, `docs:`, `test:`, `build:`, `chore:`), small and focused.
- Tags: `setup-complete`, `phase0-rc1`, `phase1-rc1` ... for test builds; `vX.Y.Z` for releases.

## 4. Done when

`tools/check-env` passes, `gradlew testDebugUnitTest lintDebug assembleDebug` succeeds and produces an APK in `dist/`, the repo has a clean `git status`, `docs/dev-setup.md` exists, and the owner can rebuild with one command.
