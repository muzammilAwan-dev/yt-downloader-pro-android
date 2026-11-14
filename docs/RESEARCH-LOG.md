# Research log

One row per finding: URL, date checked, takeaway, confidence. Primary sources first. Anything not seen on a primary page is
marked as a lead. The owner asked for research *before* coding; this file is part of the deliverable.

## 2026-11-14 — environment setup research (docs/14 step 2)

| # | Topic | Source (checked 14 Nov 2026) | Takeaway | Confidence |
|---|---|---|---|---|
| R1 | Android Gradle Plugin latest stable | developer.android.com/build/releases/agp-9-5-0-release-notes; dl.google.com Maven metadata for `com.android.tools.build:gradle` | Latest **stable** is **AGP 9.5.1** (9.5.0 notes page, last updated 2026-11-05; metadata also lists 9.6.0-alpha03/alpha02/alpha01 = preview only). AGP 9.5 supports **max API level 37**. | High |
| R2 | AGP 9.5 toolchain requirements | same release-notes page | Minimum **Gradle 9.6.0** (default 9.6.0), **SDK Build Tools 36.0.0** (default 36.0.0), **JDK 17** (default 17), NDK default 28.2.13676358 (not used by us). | High |
| R3 | AGP 9 runtime Kotlin dependency | `gradle-9.5.1.pom` fetched from dl.google.com | AGP 9.5.1 declares `org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10` (runtime) and KSP `2.2.10-2.0.2`. | High |
| R4 | AGP 9 built-in Kotlin | developer.android.com/build/releases/agp-9-0-0-release-notes; developer.android.com/build/migrate-to-built-in-kotlin | AGP 9 **enables built-in Kotlin by default**. Do **not** apply `org.jetbrains.kotlin.android` (it fails: "Cannot add extension with name 'kotlin'"). `android.kotlinOptions {}` must become `kotlin { compilerOptions {} }`. The Compose compiler Gradle plugin (`org.jetbrains.kotlin.plugin.compose`) is still the documented way to enable Compose, applied with the **same version as Kotlin**, plus `android.buildFeatures.compose = true`. | High for the rules, Medium for the exact Compose-plugin interaction with built-in Kotlin (no explicit example on the page; will prove with the hello-world build) |
| R5 | Newest stable Android API level | apilevels.com; developer.android.com/about/versions/17/setup-sdk; android-developers.googleblog.com/2026/06/Android-17.html | **Android 17 = API 37**, stable since 16 Jun 2026. Setup page: `compileSdk = 37`, `targetSdk = 37`, build-tools 37.x.x. Google Play requires `targetSdk 36+` for new apps/updates since 31 Aug 2026 (irrelevant for our channels, still a good default). | High |
| R6 | SDK packages available (exact names/checksums) | dl.google.com/android/repository/repository2-3.xml | `platforms;android-37.0` (rev 2, `platform-37.0_r02.zip`, 67,281,901 B, SHA-1 ed8ebf7f8822a4de5686d427f237d2fa30ff7410); also 37.1/37.2 exist. `build-tools;37.0.0` (`build-tools_r37_windows.zip`, 61,077,164 B, SHA-1 e9c97e26b5b5678002e9d3fed632c841ae62d99f). `platform-tools` rev 37.0.3 (`platform-tools_r37.0.3-win.zip`, 8,188,595 B, SHA-1 b3fde5239ad89229d5a9178375b81c9887a24172). **No package named `platforms;android-37` exists in that index**, only `platforms;android-37.0` — confirm with `sdkmanager --list` before installing. | High |
| R7 | Android command-line tools download | developer.android.com/tools/releases/cmdline-tools; developer.android.com/studio (page HTML) | Latest stable package: **`commandlinetools-win-15859902_latest.zip`** (155,643,188 B) from `https://dl.google.com/android/repository/`. Must be unpacked to `cmdline-tools\latest\` for `sdkmanager` to work. | High |
| R8 | JDK 17 download | api.adoptium.net/v3/assets/latest/17/hotspot (Eclipse Adoptium API) | **Temurin 17.0.21+9** (`OpenJDK17U-jdk_x64_windows_hotspot_17.0.21_9.zip`, 191,117,380 B, SHA-256 `98518ecf554465057ee5509462631deeee374bc368c12a411d225cd29381f84b`), published 2026-11-03. | High |
| R9 | Gradle distribution + checksum | services.gradle.org/distributions | `gradle-9.6.0-bin.zip`, SHA-256 `bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01` (from `...zip.sha256`). Gradle 9.9.1 (11 Nov 2026) is newer, but 9.6.0 is the version AGP 9.5.1 declares as both minimum and default, so 9.6.0 is the compatible choice. | High |
| R10 | Jetpack Compose BOM | dl.google.com Maven metadata `androidx.compose:compose-bom`; developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler | Latest BOM = **2026.09.00** (docs say "always use the latest BOM: 2026.09.00"). Compose **1.12.0+ requires `compileSdk 37` and AGP 9+** (our plan complies). Compose core modules in that BOM are 1.12.x. | High |
| R11 | Kotlin version to declare | kotlinlang.org/docs/releases; github.com/JetBrains/kotlin/releases | Latest stable Kotlin line is **2.4** (2.4.21, 8 Oct 2026). Two options: (a) keep AGP's bundled KGP 2.2.10 and apply the Compose compiler plugin at 2.2.10, or (b) add `classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")` in the root `buildscript` (documented under "Upgrade to a higher KGP version") and use the Compose compiler plugin at 2.4.21. Plan: try (b), fall back to (a) if the build objects; record whichever wins in docs/dev-setup.md. | High for versions, Medium for which one builds first try |
| R12 | `sdkmanager` deprecated in command-line tools 23.0 | observed on this machine: `sdkmanager` prints "The SDK Manager CLI tool (sdkmanager) is deprecated. Android CLI will be used instead", reports version `1.1.16919734 (Android CLI)`, and **splits `platforms;android-37.0` into four arguments** ("Package platforms not found…"). The bundled `android.exe` replaces it: `android sdk install <package>`, `android sdk list`, `android sdk update`, `android sdk remove`, with package names written with `/` (`platforms/android-37.0`, `build-tools/37.0.0`). `sdkmanager --licenses` answers "The --licenses option is no longer needed". | High (reproduced) |
| R13 | Outcome of R11 | the build was attempted with option (a): AGP 9.5.1 built-in Kotlin at 2.2.10 + Compose compiler plugin 2.2.10 + Compose BOM 2026.09.00 + compileSdk 37 → `assembleDebug` **BUILD SUCCESSFUL in 6m 51s** (docs/PROGRESS.md). No `kotlin-android` plugin, no `kotlinOptions`. Upgrading KGP to 2.4.21 is therefore not required for setup; revisit only if a dependency demands newer Kotlin. | High |


### Install locations (from docs/14, unchanged)

```
C:\Users\Malku\dev\tools\jdk-17\          Temurin 17.0.21+9 (zip, no installer, no admin)
C:\Users\Malku\dev\tools\gradle\          Gradle 9.6.0 (only to generate the wrapper)
C:\Users\Malku\AppData\Local\Android\Sdk\ ANDROID_HOME
    cmdline-tools\latest\                 build 15859902
    platform-tools\                       r37.0.3 (adb)
    platforms\android-37.0                SDK platform for compileSdk 37
    build-tools\37.0.0                    build-tools
```

### Deviations from docs/14 to be approved by the owner

1. **Repo location.** docs/14 §1 suggests `<home>\dev\repos\<app-name>`; the hand-off package is already in
   `C:\Users\Malku\Documents\yt-downloader-pro-android` and the owner's prompt says "work only inside this repo". Plan: keep the
   existing folder and `git init` there. No files are moved.
2. **`platforms;android-37.0` vs `android-37`.** Verified package name is `android-37.0`; will confirm with `sdkmanager --list`.
3. **Gradle 9.6.0 instead of the newest 9.9.1**, for the AGP-documented pairing (R9).

## Still unverified (carried forward)

- Whether the Compose compiler plugin 2.4.21 coexists with AGP 9.5 built-in Kotlin on the first try (proved or disproved by the
  hello-world `assembleDebug`).
- Whether AGP 9.5.1 accepts `compileSdk = 37` with `build-tools;37.0.0` (its default is 36.0.0; we may pin 37.0.0 or leave the
  default — decided by the build output).
- Phase 0 engine facts (youtubedl-android 0.18.x behaviour, JS runtime, PO tokens) — not researched yet, by design: the owner's
  prompt says setup first, then research, and to wait for "continue".
