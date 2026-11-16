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

## 2026-11-15 — Phase 0 engine research

| # | Topic | Source (checked 15 Nov 2026) | Takeaway | Confidence |
|---|---|---|---|---|
| R14 | youtubedl-android 0.18.1 artifact contents | Maven Central POM + downloaded both AARs and listed their entries (`library` 59,213,110 B, `ffmpeg` 139,371,444 B) | Both AARs carry native payloads for **all four ABIs** (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`): `libqjs.so` (QuickJS, 0.6–0.9 MB), `libpython.so` + `libpython.zip.so` (Python payload, 12–14 MB), and `libffmpeg.so` + `libffprobe.so` + `libffmpeg.zip.so` (29–37 MB). Restricting `abiFilters` to `arm64-v8a` keeps the phone APK near 50 MB instead of ~200 MB. All four ABIs existing means an x86_64 emulator *could* run the engine, but CI keeps the fake engine (docs/12 §2). | High (reproduced) |
| R15 | yt-dlp version inside 0.18.1 | extracted `res/raw/ytdlp` (3,170,726 B: `#!/usr/bin/env python3` + zip starting at byte 23) and read `yt_dlp/version.py` | Bundled engine is **yt-dlp 2025.11.12** (git `335653be82d5…`, channel stable). Latest stable on GitHub on the check date is **2026.10.26** → the Maven artifact is ~11 months behind. An engine update path is mandatory, and 0.18.1's `YoutubeDLUpdater` downloads the GitHub release asset and installs it **without a checksum or signature check** — Phase 2 must add verification + rollback before we expose it. | High (reproduced) |
| R16 | yt-dlp-ejs / JS solver scripts | same zipapp: `yt_dlp_ejs/__init__.py`, `_version.py`, `yt_dlp_ejs/yt/solver/{core,lib}.min.js`; `yt_dlp/extractor/youtube/jsc/_builtin/ejs.py` | yt-dlp-ejs **is vendored inside the artifact** and the solver JavaScript ships locally, so the default JSC provider needs no remote component download (which our no-remote-code rule would forbid anyway). The standalone `yt-dlp-ejs` release is 0.8.0 (17 Mar 2026). The **exact vendored EJS version cannot be read** from `_version.py` without importing it (setuptools-scm writes it at build time) → read it at runtime from `-v` output and record it in the on-device report. | High for presence, Medium for the version string |
| R17 | PO-token enforcement | yt-dlp wiki `PO-Token-Guide` (last edited 12 Jul 2026) + `Extractors#youtube` | GVS PO tokens are currently required for `web`, `mweb`, `web_music`, `web_creator`, `tv_simply`, `android`, `ios`; **not** required for `tv`, `web_embedded`, `android_vr`. Tokens are video-ID-bound, so one token per video; manual `po_token` is discouraged and a provider plugin (bgutil / getpot-wpc) is recommended. Those are Python plugins (+ a Node/Deno sidecar), i.e. remote/extra code we may not ship casually. Consequence for Phase 0: prove the default client order yields **1080p with audio** and record `withheld=`/403 behaviour; a PO-token strategy is a Phase 2 decision, not a Phase 0 blocker. | High for the constraint, **open** for our mitigation |
| R18 | Android 17 (API 37) all-app behaviour changes | developer.android.com/about/versions/17/behavior-changes-all | Nothing blocks the spike: per-app memory limits (we hold no big heaps; watch `MemoryLimiter:AnonSwap` if the app dies), `usesCleartextTraffic` deprecation (we stay cleartext-off), Keystore key cap 50k (irrelevant), implicit URI-grant restriction **starting Android 18** (so pass `FLAG_GRANT_READ_URI_PERMISSION` explicitly when sharing the log — done in this phase), IME-restore-after-rotation (set `windowSoftInputMode` if a text field needs it later), background-audio hardening (no playback). | High |
| R19 | Jetpack versions for Phase 1 | dl.google.com / Maven Central `maven-metadata.xml`, 15 Nov 2026 | Latest stable: `room-runtime` **2.8.5**, `work-runtime` **2.12.0**, `datastore-preferences` **1.3.0**, `paging-runtime` **3.5.3**; `io.coil-kt.coil3:coil-compose` **3.6.3**. Not added in Phase 0 (the spike stays dependency-light); re-check and pin when Phase 1 starts. | High for versions, Medium for AGP 9.5/compileSdk 37 compatibility until built |
| R20 | Legacy native-lib packaging is required | youtubedl-android README ("set `android:extractNativeLibs="true"`") + AAR contents (`lib*.zip.so` read by path from `nativeLibraryDir`) | The payload is a zip disguised as a `.so`, opened by **file path**; AGP 7+ defaults `jniLibs.useLegacyPackaging` to **false**, which leaves the libs uncompressed inside the APK and breaks those file reads. Set `packaging { jniLibs { useLegacyPackaging = true } }` and prove it in the built APK (`aapt2 dump xmltree … AndroidManifest.xml | findstr extractNativeLibs`). | High for the mechanism, proved by the build below |
| R21 | Engine call pipeline (what we can rely on) | `YoutubeDL.kt` on master, 0.18.1 line | `execute()` always appends `--js-runtimes quickjs:<nativeLibraryDir>/libqjs.so`, `--ffmpeg-location <nativeLibraryDir>/libffmpeg.so` and `--no-cache-dir`; `getInfo()` = `--dump-json` + `execute` + Jackson mapping to `VideoInfo`/`VideoFormat`; progress callback is `(Float, Long, String) -> Unit` (percent, eta seconds, raw line); `version(context)` reads the installed yt-dlp version; `updateYoutubeDL(context, STABLE/NIGHTLY/MASTER)`. The library has `minSdk 24` and drags in appcompat, jackson-databind, commons-io. | High |
| R22 | Corrections found by inspecting the native payloads | unzipped `jni/arm64-v8a/libpython.zip.so` and `libffmpeg.zip.so` from `library`/`ffmpeg` 0.18.1 | **CPython is 3.12**, not the 3.8 the README still says (`usr/lib/python3.12/`, `libpython3.12.so.1.0`). The ffmpeg payload contains **`libx264.so.164`**, i.e. a **GPL ffmpeg build** (fine for our GPL-3.0 app, but a source offer is mandatory). Neither payload ships a LICENSE/COPYING file, and `libx264.so`/`libav*.so` entries are 14–23-byte **symlinks** stored as text. Consequence: `THIRD_PARTY_NOTICES.md` must carry PSF + MIT + GPL source-offer items before release. | High (reproduced) |
| R23 | First on-device run (owner's phone) | app's own diagnostic log, 15 Nov 2026, Infinix X682B / Android 10 / API 29 / arm64 | Runtime versions confirmed **on the device**: CPython **3.12.11**, QuickJS **`quickjs-2025-04-26`**, ffmpeg **7.1.1**, yt-dlp **2025.11.12** (the library's `version()` returns it as `(2025.11.12)`, parentheses included). First-run unpack **8.3 s**. A Shorts URL resolved to **31 formats** with `maxHeight=1920`, `withheld=false`, 0 warnings → **no PO-token block for that video**. Two streams (1.73 MiB video + 660.71 KiB audio) merged into one 2,505,222-byte MP4 and published to `content://media/external/downloads/18406` on API 29. Timing: resolve 17.6 s, then the download stage spent another ~17.9 s before its first byte — **the URL is extracted twice** because `download()` re-runs `execute()` on the URL. EJS version is still not reported by `-v` (`ejs=?`). | High (device, single sample) |
| R24 | The owner's desktop project as the reference implementation | github.com/muzammilAwan-dev/yt-downloader-pro (README, LICENSE, `Services/CommandValidator.cs`, `Services/YtDlpRunner.cs`, `Services/EngineUpdater.cs`, `Models/AppSettings.cs`, `Models/DownloadTask.cs`, `Services/HistoryManager.cs`, file tree), read 15 Nov 2026 | **MIT**, 55 files, C#/.NET 8 WPF host + Manifest V3 extension, 9 sites. Reusable *rules* (not code): (a) strict allowlist with separate value/bool flag sets; every bare token must be `http(s)`; `-o` / `--download-archive` paths constrained by directory + extension; rejects control characters, escaped quotes, unbalanced quotes, `..`, UNC; (b) `-N/--concurrent-fragments` default **4** as the throttling bypass instead of aria2c; (c) compatibility mode = force H.264/AAC; (d) format-fallback detection via `[info] <id>: Downloading N format(s): <ids>` (the only trace when YouTube withholds adaptive formats and yt-dlp silently picks itag 18) and merged filename from `[Merger] Merging formats into "…"`; (e) engine update via `--update-to <channel>` with version before/after, user-facing sentences and a **15-minute busy retry** while downloads run — but **no checksum**, so our Phase 2 SHA-256 + rollback requirement stands; (f) settings defaults: 3 concurrent downloads, unlimited speed, channel nightly, empty `YoutubePlayerClients` emergency override. Not transferable: MV3 extension/DOM scraping, `ytdlp://` protocol, tray/single-instance, deno (we have QuickJS). Full mapping in `docs/04-OPEN-SOURCE-REFERENCES.md`. | High (source read) |

## 2026-11-15 — Phase 1 research (primary sources rechecked)

| # | Topic | Source (checked 15 Nov 2026) | Takeaway | Confidence |
|---|---|---|---|---|
| R25 | Actual EJS verbose-header spelling | [yt-dlp 2025.11.12 `YoutubeDL.py`](https://github.com/yt-dlp/yt-dlp/blob/2025.11.12/yt_dlp/YoutubeDL.py#L4114), [`dependencies/__init__.py`](https://github.com/yt-dlp/yt-dlp/blob/2025.11.12/yt_dlp/dependencies/__init__.py), [`compat_utils.py`](https://github.com/yt-dlp/yt-dlp/blob/2025.11.12/yt_dlp/compat/compat_utils.py) | The `Optional libraries` header formats imported modules as `name-version`; EJS imports as `yt_dlp_ejs`, so the parser must accept `yt_dlp_ejs-<version>`, not just `yt-dlp-ejs <version>`. If no version is reported, use `ejs=unknown`; do not infer presence/absence or hardcode a version. `JS runtimes: none (disabled)` and runtime `(unknown)` / `(unsupported)` entries must not pass the runtime check. Corrects the overly narrow parser and R23's interpretation of the missing EJS version. | High (source inspected; parser unit-tested, new device run pending) |
| R26 | Resolve reuse with `--load-info-json` | [current README](https://github.com/yt-dlp/yt-dlp#filesystem-options), [2025.11.12 `download_with_info_file`](https://github.com/yt-dlp/yt-dlp/blob/2025.11.12/yt_dlp/YoutubeDL.py#L3667), [CLI dispatch](https://github.com/yt-dlp/yt-dlp/blob/2025.11.12/yt_dlp/__init__.py) | The CLI ignores URL arguments when `--load-info-json` is supplied. `download_with_info_file` sanitizes the JSON and runs `process_ie_result`, so the happy path can reuse the full resolved format list with a new selector. It can fall back to `webpage_url` on certain errors: **not a guarantee of exactly one extraction under all conditions**. Signed URLs/headers in the snapshot must stay task-private and out of history/logs, and the temporary JSON must be cleaned up. Implementation/device verification pending. | High for source behavior, implementation not yet verified |
| R27 | Current engine release and fragment concurrency semantics | [GitHub latest-release API](https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest), [current README download options](https://github.com/yt-dlp/yt-dlp#download-options) | The API returned stable **2026.10.26**, published `2026-10-26T22:25:26Z`; the Android artifact remains bundled at 2025.11.12. `-N` controls parallel DASH / native-HLS **fragments**, default 1. Our adopted value 4 is a bounded parallelism setting, **not a universal throttle bypass** and does not add connections to every HTTP download. Device throughput/battery effects remain unmeasured. | High (primary sources checked) |
| R28 | Native Android design guidance and reusable design skills | Owner-requested `general` research: [Material 3 Compose](https://developer.android.com/develop/ui/compose/designsystems/material3), [Google adaptive skill](https://github.com/android/skills/blob/main/jetpack-compose/adaptive/SKILL.md), [Compose accessibility](https://developer.android.com/develop/ui/compose/accessibility/key-steps), community Material 3 and Anthropic frontend-design instructions; full links in `docs/15-UI-UX-DESIGN-RESEARCH.md` | Official Android/Material guidance is the foundation; Google's adaptive skill is native but assumes Navigation 3 / some experimental APIs. Community Material 3 skill is a planning candidate; frontend-design is web-oriented and only an optional critique aid. None is proven universally "best". No skills installed, scripts executed or UI implemented. Review/pin instructions and dependencies before use. Owner will provide screenshots for interaction inspiration, never competitor code/assets/branding. | High for reviewed resource scope; effectiveness and security not comprehensively evaluated |

## 2026-11-15 — screenshot, browser and authentication research

| # | Topic | Source (checked 15 Nov 2026) | Takeaway | Confidence |
|---|---|---|---|---|
| R29 | Screenshot review and owner's website-login observation | Owner's `Screenshots.zip`, all nine 720×1640 PNGs visually reviewed; existing `docs/03-SNAPTUBE-REA-FINDINGS.md`; detailed image mapping in `docs/16-BROWSER-AUTH-AND-MEDIA-DISCOVERY.md` | Dark home/search/source tabs, direct media download actions, compact downloaded-file list and grouped settings are visible. Login, browser address bar, bot prompt and picker are not pictured. Owner separately reports YouTube website login inside the browser and a red sign-in recommendation after bot errors. Treat that as firsthand owner-reported behaviour, not an independently reproduced implementation. Static REA suggests custom/browser-assisted extraction; exact cookies/token path and yt-dlp presence/absence remain unproved. No new binary analysis, screenshots/assets/code committed or browser implemented. | High for visual observations; owner-reported for runtime flow; inference only for implementation |
| R30 | Generic and manifest extraction beyond social sites | [Supported sites](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md), [FAQ](https://github.com/yt-dlp/yt-dlp/wiki/FAQ), [generic extractor source](https://github.com/yt-dlp/yt-dlp/blob/master/yt_dlp/extractor/generic.py) | yt-dlp's embed/generic paths support more than its named catalogue; generic source includes HLS and DASH handling. Browser discovery can complement extraction while reusing the download engine. A site list is not a guarantee or exhaustive engine-support boundary; Android coverage remains untested beyond the Phase 0 sample. | High for upstream capability; app/browser integration untested |
| R31 | Account cookies, OAuth and PO-token distinctions | [YouTube cookie guidance](https://github.com/yt-dlp/yt-dlp/wiki/Extractors#exporting-youtube-cookies), [OAuth guidance](https://github.com/yt-dlp/yt-dlp/wiki/Extractors#logging-in-with-oauth), [PO Token Guide](https://github.com/yt-dlp/yt-dlp/wiki/Po-Token-Guide) | Cookies may help some login/bot errors; upstream warns of account bans and cookie rotation. OAuth login no longer works with yt-dlp. PO-token requirements are client/request dependent; tokens can be video/session/platform-bound and expire. Regular login is not a universal PO-token or bot fix; Premium's GVS exemption is narrower than bot immunity. Raw wiki PO-guide URLs returned 404; actual GitHub wiki successfully read. | High for current upstream guidance; Android provider/session handoff unresolved |
| R32 | Direct website sign-in vs embedded OAuth and browser isolation | [Google OAuth browser policy](https://developers.google.com/identity/protocols/oauth2/policies#browsers), [Custom Tabs overview](https://developer.chrome.com/docs/android/custom-tabs/overview), [CookieManager](https://developer.android.com/reference/android/webkit/CookieManager#getCookie(java.lang.String)) | OAuth policy prohibits developer-controlled embedded agents for OAuth authorization, **not proof all direct YouTube website sign-in is the same flow**. Website sign-in acceptance needs testing. Custom Tabs share browser state, not app WebView state or unrestricted cookie access. Legacy `getCookie` returns a header, not full domain/path/expiry metadata; do not guess a cookie jar's scope. Prior blanket WebView-login proposal corrected; additional attribute API and broader Google sign-in guidance verified in R34. | High for API/policy scope; direct website-login compatibility and cookie transfer untested |
| R33 | Browser media discovery limits and security | [WebViewClient](https://developer.android.com/reference/android/webkit/WebViewClient), [WebResourceRequest](https://developer.android.com/reference/android/webkit/WebResourceRequest), [MSE](https://developer.mozilla.org/en-US/docs/Web/API/Media_Source_Extensions_API), [EME](https://developer.mozilla.org/en-US/docs/Web/API/Encrypted_Media_Extensions_API), [Android native-bridge risks](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges) | Interception omits blob/javascript and redirected resource URLs; request callback is not full response capture and has no request-body getter. MSE buffers are not direct media URLs; EME/DRM playback is not exportability. Optional non-DRM browser discovery requires HTTPS validation, scoped session transfer, secret cleanup and original safe code. No permission/remote-code policy relaxed; no 1DM REA required to define a fixture spike. | High for documented limits; proposed discovery/security design unimplemented |
| R34 | `general` follow-up: website-login acceptance and attribute-capable cookie snapshot | Owner-requested research, rechecked on [Google Account Help](https://support.google.com/accounts/answer/7675428?co=GENIE.Platform%3DAndroid&hl=en), [CookieManagerCompat](https://developer.android.com/reference/androidx/webkit/CookieManagerCompat), [Chromium Custom Tabs security FAQ](https://chromium.googlesource.com/chromium/src/+/HEAD/docs/security/custom-tabs-faq.md) | Google may stop account sign-ins in embedded browsers, independently of the narrower OAuth rule. `CookieManagerCompat.getCookieInfo` (WebKit 1.6.0+) returns URL-scoped cookie attributes in Set-Cookie format, gated by `GET_COOKIE_INFO`; missing metadata is not a universal Android constraint. A validated WebView session-to-task adapter is plausible when login and API support work. Chromium explicitly withholds cookies/script injection/network interception from Custom Tab embedders and cautions against WebView as a general browser framework. No dependency installed, feature support checked, login tested or adapter implemented. | High for primary API/security documentation; runtime compatibility untested |
| R35 | `explore` trace of retained REA evidence and original-source coverage | Owner-requested workspace/temp/targeted Downloads search; `03-SNAPTUBE-REA-FINDINGS.md` T1–T2/T5–T7/T10, `11-BRANDING-AND-UX.md` §3, `10-VERIFICATION-LOG.md` evidence 15 (`50-deep-dive.md`); read-only REA `binary_session` | Retained summaries support browser/search, authentication-aware failure outcomes, URL/header/expiry formats and separate typed transfer/post-processing. Owner supplies website-login/bot-prompt runtime evidence. The exact browser-cookie-extractor-transfer handoff is not retained. Original export/topics absent within searched scope; REA reports no open target and zero retained evidence. Request original export/path for focused source trace, not another app's REA. Absence in this scope does not invalidate the working-app observation or establish absence everywhere. | High for retained-document/search/session results; original source not reverified |
| R36 | Original REA session-to-extraction/download trace after owner supplied the path | `C:\Users\Malku\Documents\REA\export\00-BRIEF.md:1–8`, original `40-*` reports and focused JADX call sites; detailed exact paths/line ranges in doc 16 §7. Owner-requested `explore` trace; parent re-read key cookie helper/parser/bot/browser/HTTP/token-WebView evidence | Real browser website login/session signals; parser obtains shared WebView cookies and attaches eligible authentication to extraction. Selected-format submission persists a task; headers reach transfer and Cookie fallback queries the media URL. Expired-login continuations are branch-specific: picker resumes submission; failed-task path can requeue. Separate actual PO-token WebViews/providers have bounded failures/cache/lifecycle logic, with decompiler renewal ambiguity. Login is not proven to generate/invalidate account-bound tokens, guarantee bot immunity or broadcast credentials to every CDN. Initial missing-source/WebView-inference limitations superseded where traced; no competitor code copied, app executed or new binary analysis performed. | High for specific static branches; runtime success/security completeness unverified |
| R37 | Reattached second screenshot batch: browser/account state and media actions | Owner's `C:\Users\Malku\Downloads\screenshots 2.zip`; all five 720×1640 PNGs visually reviewed; SHA-256 and original-filename mapping in doc 16 §1 | Player with direct Download action, comments panel, loading browser shell, Sites shortcuts and signed-in website-style account page inside app chrome are visible. Supports the owner's website-session observation, not third-party OAuth-button inference. Exact renderer/session handoff relies on separate REA evidence; no login-entry/bot prompt, post-login transfer or token-generation proof. Original lessons: separate app/site navigation and task state, visible origin, clear loading/errors, validated shortcuts. Initial missing-archive result is superseded. No images, account/feed/history details, code/assets retained in repo; no browser/login implementation or agent-operated sign-in. | High for visible states; runtime authentication/download compatibility untested |

## Phase 1 implementation follow-up

- **15 Nov 2026:** final output detection now has an offline-tested parser foundation using explicit, app-owned
  `--print` markers and canonical task-directory confinement. A merger line or recently modified file is not final-output
  proof. Selection parsing distinguishes the actual reported format IDs/height from the initial resolve catalogue;
  later fallback selection clears stale height. Wiring into the engine is the next increment; primary-source verification
  of pinned print/load-info semantics completed as the owner-authorized `general` task (R38 below). No live engine/device
  result claimed. Local tests/lint/build succeeded, with one host-restricted symlink test skipped (progress Step 3a).

| # | Topic | Primary source (15 Nov 2026) | Takeaway | Confidence |
|---|---|---|---|---|
| R38 | Pinned engine replay, print timing and cancellation | Owner-authorized `general` read-only check: [Android library 0.18.1 execute](https://github.com/yausername/youtubedl-android/blob/d725d5c9a18c3a99a13ee0308bf78275dc310760/library/src/main/java/com/yausername/youtubedl_android/YoutubeDL.kt#L124-L261), sibling Request/StreamProcessExtractor/StreamGobbler files; [yt-dlp 2025.11.12](https://github.com/yt-dlp/yt-dlp/blob/5977782142ca7e41240f07202cc9b8dcc087b401/yt_dlp/YoutubeDL.py#L3667-L3716) and pinned output/options/move postprocessor | Blocking execute; callbacks normally stdout-only, exceptions often replace nonzero responses. Empty URL-list constructor works for replay; no shell quoting needed for argv values. Full catalog + enabled cleaning reapplies current selector, but disabled cleaning/old requested_formats can retain old merge members. Clear derived top-level selection/transport/paths before replay; preserve catalog transport data. before_dl sees selection; after_move supplies final filepath, which still requires success/file/confinement checks. Interruption/public cancellation do not prove complete child cleanup; use generated internal keys and never publish after cancellation. No source-artifact binary comparison, dependency/skill installation or live runtime test. | High for pinned source; runtime/process-tree guarantees unverified |
| R39 | Parent replay/cancellation recheck and implementation limits | Parent re-read pinned [Android execute source](https://github.com/yausername/youtubedl-android/blob/d725d5c9a18c3a99a13ee0308bf78275dc310760/library/src/main/java/com/yausername/youtubedl_android/YoutubeDL.kt), [yt-dlp format-field source](https://github.com/yt-dlp/yt-dlp/blob/5977782142ca7e41240f07202cc9b8dcc087b401/yt_dlp/YoutubeDL.py), and [options](https://github.com/yt-dlp/yt-dlp/blob/5977782142ca7e41240f07202cc9b8dcc087b401/yt_dlp/options.py) lines 424/1510 | Confirmed stdout-only default callbacks, merged-output exception limitation, shell helper's arbitrary-key/PID mismatch, and explicit ignore-config/clean-info-json flags. Expanded replay policy to clear top-level request bodies, HLS key/query data and format transport flags while retaining per-format data. Adapter now uses task-bound single-use inputs, source HTTPS validation, interruptible jobs and explicit final/selection markers. No live speed gain, replay success, Google login or full descendant cleanup measured; metadata reuse may still fall back to upstream re-extraction after errors. | High for inspected source/offline policy tests; native runtime untested |
| R40 | Latest phone evidence belongs to the old APK | Owner attachments `app-20261115.log`, `app-20261115 2.log`, `app-20261115 3.log`, read 15 Nov 2026; clean-source tester build via existing helper | All three are snapshots of one session stamped `8cd9487`. Old build logs successful video/audio download, MP4 publish and self-test 7/7; catalog height is not downloaded height. EJS remains unreported, not proven absent. The 18.367 s pre-progress wait is consistent with old repeated extraction, not a measured new-build comparison. Refreshed `dist/app-debug.apk` from `5889eee`; generated BuildConfig/packaged stamp and identical source/dist SHA-256 checked. 90 JVM tests passed, one host-restricted symlink case skipped; lint/build succeeded. No current phone replay/cancellation, playback/audio or gallery confirmation yet. Personal media/account details not retained. | High for supplied logs/local build; latest native behavior still unverified |

## 15 Nov 2026 — current phone evidence and owner asset

- **R41:** Owner's `app-20261115-091521*.log` attachments identify `5889eee`, show private metadata replay, actual
  `299+140`/1080 selection, explicit final-file evidence, MP4 publish and 7/7 passing checks. EJS is now reported as
  0.3.1 (previous unknown is resolved for this run). This proves a successful sample, not universal extraction, native
  cancellation/descendant cleanup or owner playback. First progress at ~5.632 s is not a controlled comparison to the
  different earlier video. Engine age warning is retained; no updater change. Personal media details not copied.
- **R42:** Owner explicitly selected **YTDLP (You Tube Downloader Pro)** and supplied
  `E:\Github Projects\yt-downloader-pro\host\YTDLPHost\Assets\icon.ico`. Read their local desktop `LICENSE`: MIT,
  copyright (c) 2026 Muhammad Muzammil. ICO inventory has seven sizes from 16 to 256 px, largest frame PNG-compressed.
  Reuse only this owner-selected asset with license/attribution; do not replace it with a competitor asset or imply
  upstream endorsement. Android package id remains stable; icon rendering and public-name availability unverified.
- **R43:** Read Google's [adaptive icon guidance](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive)
  on 15 Nov 2026: bitmap layers and inline foreground/background drawables are supported; 108dp layers have a central
  66dp safe zone, with launcher-controlled masks. Preserved the supplied desktop tile rather than inventing a replacement;
  legacy bitmap/adaptive wrapper share its exact 256px PNG frame (SHA-256
  `0221f46a21d1d739eaf4d68d5364f127049b14201d28764f4d444e4d7178e5ea`). Previewed extracted artwork locally; primary opaque
  red symbol bounds scale to x=31.22..76.36/y=26.16..81.42dp within the safe zone. Original tile includes its own rounded
  outline; outer appearance is launcher-dependent and needs a phone check. License shipped with the APK; no new image
  library installed or Android dependency added. No name/trademark clearance claimed.

## 15 Nov 2026 — launcher visible-bounds correction

- **R44:** Owner `69f343f` logs again show successful replay, actual 1080 video/audio and MP4 publish. Initial self-test
  was 6 passes/1 skip without URL, not a failed resolve. Latest launcher screenshot shows visual cropping/oversizing.
  Earlier resource test checked source symbol bounds, not AdaptiveIconDrawable's displayed viewport; that coverage was
  insufficient. No media/account details retained and no phone playback/folder confirmation inferred.
- **R45:** Read Android 10 [AdaptiveIconDrawable.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/android10-release/graphics/java/android/graphics/drawable/AdaptiveIconDrawable.java)
  lines 100–101, 243–244, 309–330: layers expand each visible edge by 25%, giving 1.5x size and 2/3 viewport scale.
  [InsetDrawable.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/android10-release/graphics/java/android/graphics/drawable/InsetDrawable.java)
  lines 193–221 confirms XML fraction support. One-sixth inset per expanded-layer edge (16.67%) restores full desktop
  image proportions without density-dependent dp padding or a new artwork asset. Added a visible-size regression;
  actual OEM launcher rendering remains unverified. Primary source facts correct R43's incomplete rendering check.
  Locally generated/read labelled before/after mask simulations (rounded square/circle/squircle) using existing Pillow;
  no package installed, source PNG modified or simulation presented as native rendering evidence.

## 15 Nov 2026 — Phase 1 entry boundary

- **R46:** Owner's latest launcher/app screenshots confirm corrected visible icon proportions and `b83d33c` running.
  This resolves the owner-device visual check only, not playback/audio, file-browser visibility or universal OEM rendering.
- **R47:** Reviewed current `MainActivity`, `Engine`, `DownloadExecutor`, `FormatPlanner`, manifest and fake-engine tests.
  Existing `DownloadRequest.maxHeight` is a ceiling, not exact selected formats. The planner's unbounded final `/b`
  could violate a user-selected cap; removed it and added regressions. First picker exposes only reported video+audio
  ceilings, explicitly labels fallbacks and keeps actual output evidence separate. Strict bounded HTTPS validation is
  shared by paste/share entry; only a URL is accepted, never arbitrary intent options. No new upstream API inferred.
- **R48:** Rechecked Google's [receiving shared content guide](https://developer.android.com/training/sharing/receive):
  `ACTION_SEND` + `DEFAULT` + `text/plain` declares a text share target; validate action/type/payload and let the user
  confirm/edit before use. This app can handle it directly in its single activity without adding Compose Navigation.
  No `SEND_MULTIPLE`, binary streams, deep-link commands or external automation accepted in this increment.
- **R49:** Controller design preserves existing task-bound, single-use replay and verified output evidence rather than
  storing extraction JSON in screen state/history. A chooser older than five minutes is re-resolved, with renewed user
  confirmation. Stream callback percentages are not aggregate video/audio/merge progress. Publication is a commit boundary;
  no cancel button is offered once saving begins. Activity destruction/process death and full native cleanup remain outside
  durable guarantees until the queue/service increment; this is explicitly a foreground-screen tester flow.
- **R50:** Owner requested `@explore` design alignment review. Read-only specialist compared new HomeScreen/controller
  against docs 15/16: Material, original review/quality/save states, separate pending shares and honest login limits are
  reflected. No external design skill installed/loaded. Review noted missing heading/focus handling and unverified render/
  accessibility/contrast, basic column rather than reviewed wireframes/tokens, and absent audio-only/queue/library.
  Added headings/keyboard dismissal; no claim of TalkBack, large-font, light/dark rendering or end-to-end phone success.
- **R51:** Static teardown review found that reader callbacks needed explicit task/closed guards: cancellation of a
  coroutine alone does not prove library reader threads stopped. Added volatile ownership/close visibility, callback
  gates and fake late-callback tests; activity observers suppress post-close writes. No complete descendant/native
  process-cleanup guarantee inferred. Build/report metadata explicitly marks the new UI as a Phase 1 preview.
- **R52:** Standard stamped helper's reused Gradle daemon disappeared during lint; root cause unverified. Retried with
  process-local `GRADLE_OPTS` daemon=false (existing value preserved), no source/cache/dependency/SDK/global setting changes.
  Fresh-daemon lint/build succeeded. Verified generated/DEX `ec146df` + preview version + HomeScreen, identical source/dist
  APK SHA-256, compiled share target and unchanged adaptive padding. 120 passed/one existing host symlink skip in XML;
  helper retry's test task was UP-TO-DATE, not another execution. Native UI/share/download/accessibility remain untested.

## 15 Nov 2026 — Standard quality presets

- **R53:** Reviewed all five owner screenshots and the attached `ec146df` self-test log. Screens show dark Home,
  raw-height chooser, stream progress and saved selection; owner explicitly confirms audio playback. Log confirms
  7/7 self-tests, 33 formats and catalogue maxHeight 1440, but contains no download/publish events. Do not infer download
  timing or replay from it. Personal media titles, source URLs and screenshot copies are not committed.
- **R54:** Rechecked pinned [yt-dlp 2025.11.12 sorting](https://raw.githubusercontent.com/yt-dlp/yt-dlp/2025.11.12/yt_dlp/utils/_utils.py)
  and [selector/filter implementation](https://raw.githubusercontent.com/yt-dlp/yt-dlp/2025.11.12/yt_dlp/YoutubeDL.py):
  `res` uses the shorter positive dimension; `-S res:N` alone is a preference, not a hard ceiling. Quoted `format_id~=`
  regex filters are supported. App-owned, safe-ID whitelists derived from both known dimensions can cap portrait and
  landscape video without trusting a resolution name or introducing unrestricted fallback. Highest reported resolution
  is tried before codec compatibility/lower capped fallbacks. Native offline selector verification remains pending.
- **R55:** Rechecked owner MIT desktop `host/YTDLPHost/Services/YtDlpRunner.cs` and supplied command. Adopt familiar
  standard presets, bounded fragment concurrency and ceiling-aware filenames in original Kotlin. Keep bundled Android
  FFmpeg/QuickJS, task replay and MediaStore publication, not desktop Windows paths/Deno. `mp4/mkv` describes merge
  containers, not conversion; preserve `%(ext)s`. `--restrict-filenames` forces ASCII; keep existing Windows-safe Unicode
  naming rather than needlessly stripping titles. Thumbnail embedding adds network/post-processing/dependency behavior;
  defer it explicitly until separately Android-tested. No competitor code/assets copied or external design skill loaded.

- **R56:** Owner clarified that full architectural/code-behavior study is welcome, with original implementation rather
  than source copying. Re-read doc 03's picker/extraction/task/transfer boundaries and typed processing/state lessons;
  rechecked desktop MainViewModel height/quality parsing. Current work adopts task-private typed plans and explicit stages;
  durable queue/service architecture remains planned, not delivered by this quality increment. Added duplicate-ID rejection
  (an ID with contradictory dimensions cannot substantiate a cap) and a 512-candidate bound. Preparing 96 synthetic
  JVM-generated selector cases for the already extracted, version-checked pinned package; no installs/live-media CI.
- **R57:** Rechecked owner `extension/popup.html`/`popup.js`: standard dropdown defaults to 1080; command builder uses
  MKV merges at 1440/2160, lower-tier MP4, opt-in thumbnail and quality suffixes. Adapt those rules without copying:
  disable unavailable source tiers, strict portrait-aware caps, true extension, Unicode-safe filenames and no custom commands.
  First optional native matrix run caught Opus preference in generic `ba`; added explicit AAC preference across best/lower
  video fallbacks, retaining Opus when AAC is absent. Test fixture JSON compile assumption was corrected to JDK XML without
  adding the not-yet-installed serialization dependency. Final generated native matrix verification pending.
- **R58:** Executed corrected JVM checks (133 pass/one host skip, lint 0 errors/7 known warnings), then the optional native
  checker against the existing pinned 2025.11.12 package. All 96 Kotlin-generated selectors passed orientation, codec,
  audio, muxed and bounded/no-fallback cases. This is offline selector evidence, not FFmpeg/live-site/Android execution.
- **R59:** Integrated typed plans into Home rather than exposing raw frame heights/format commands. UI receives only
  standard choices and dimension summaries; task-private plans/IDs/replay remain controller-owned. QHD/4K merges use MKV,
  lower merges retain MP4/MKV. Completion/logs use both selected dimensions and short-edge resolution, not a catalogue
  maximum or a falsely labelled portrait frame height. New controller fake tests cover portrait, MKV, unknown dimensions
  and unsupported catalogue. New Compose/native phone flow remains unverified; old owner audio evidence is not extended.
- **R60:** Standard-quality `74ce7cf` helper succeeded with process-local fresh-daemon settings: 137 passing executed
  stamped tests/one host skip, lint 0 errors/7 known warnings, debug APK built. Re-ran all 96 generated offline native
  selectors. Verified source/dist SHA-256 equality, DEX/BuildConfig stamp and quality labels, exact source/artwork bytes
  in APK, compiled share/cleartext/native extraction settings and unchanged adaptive inset. Phone standard-picker/native
  download and high-resolution MKV playback remain unverified. No native/runtime/icon/dependency/permission update.

- **R61 (15 Nov 2026):** Owner supplied `74ce7cf` phone log/picker screenshot: 1080×1920 selection `137+140`,
  task-private replay reused, MP4 audio/video merge, 7,737,267-byte MediaStore publish, later self-test 7/7. Only one quality
  download; no new playback/second-quality/MKV/accessibility claim. Runtime output confirms yt-dlp 2025.11.12 and bundled
  Python/QuickJS/FFmpeg despite early header placeholders. No media titles/URLs or raw personal log committed.
- **R62 (15 Nov 2026):** Checked https://opencode.ai/docs/migrate-v1, /docs/permissions, /docs/agents, /docs/models and
  /docs/zen: supported legacy config retained; narrowed broad shell grants and default-denied reviewer tools. V2 docs
  conflict with zip-audit Tab-only fallback advice: session model selection persists; use `/models` to explicitly confirm
  the free model (owner approved this correction; same manual-fallback plan, no plugin). Official docs list both chosen free IDs and privacy terms;
  inference/reload/enforcement untested (`opencode` absent from PATH). Handoff script blocked by missing PyYAML; not installed.

- **R63 (15 Nov 2026):** Rechecked https://developer.android.com/develop/background-work/services/fgs/launch and
  /fgs/timeout: service start and successful foreground promotion are separate; API 35+ dataSync timeout requires prompt
  stopSelf, not waiting for native cleanup. Current Home/private replay is Activity-owned, so this small increment adds
  a typed, task-only admission/progress/release boundary with fake-Engine tests; Android service/session ownership,
  notification permission/cancel and onTimeout wiring remain next. No new permission/dependency or background guarantee.
  Also checked current CoroutineStart.UNDISPATCHED API docs and pinned 1.9.0 source:
  https://raw.githubusercontent.com/Kotlin/kotlinx.coroutines/1.9.0/kotlinx-coroutines-core/common/src/CoroutineStart.kt.
  Immediate entry before suspension can establish cleanup even for a canceled scope; ensureActive must precede admission.
  The real engine already switches download work to Dispatchers.IO; no dependency bump is needed.

- **R64 (15 Nov 2026):** Checked https://developer.android.com/reference/android/app/Application: custom Application
  uses application android:name; application-context-backed wiring is process-local, not durable queue storage.
  Checked current https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-supervisor-job.html
  and pinned https://raw.githubusercontent.com/Kotlin/kotlinx.coroutines/1.9.0/kotlinx-coroutines-core/common/src/Supervisor.kt:
  SupervisorJob(parent = null) is available; replace the caller Job in the session context, do not inherit Activity cancellation.
  Added same-thread attachment ownership and final-release cancellation; shared Home/diagnostics/publication retain no Activity.
  Engine/logger are application-owned; logger remains flushed/open across sessions so old cleanup cannot close a newer writer.
  Main is still the only real attachment; fake reconnect requires another owner. Service/notification/timeout wiring and phone behavior untested.

- **R65 (15 Nov 2026):** Rechecked https://developer.android.com/develop/background-work/services/fgs/timeout:
  API 35+ dataSync onTimeout must call stopSelf within seconds; it must not await native/task cleanup.
  Preparing Home's timeout boundary before the Android adapter: stop-service callback first, task-scoped cancellation,
  PAUSING until worker/replay cleanup, then PAUSED(FGS_TIMEOUT); stale callbacks must not cancel a newer task.
  Contract recovery stays PAUSE_AND_RESCHEDULE, but Room/WorkManager do not exist yet: explicitly log rescheduled=false
  and require fresh resolution + user confirmation for another attempt. No automatic resume or durability claim.
  Owner now authorizes pushing verified increments and defers installation until a meaningful phone-testing milestone;
  old phone checks remain unverified and the major-feature-area gate stays in place. No new permission/dependency.

- **R66 (15 Nov 2026):** Checked current Android docs: https://developer.android.com/develop/background-work/services/fgs/launch,
  https://developer.android.com/develop/background-work/services/fgs/service-types#data-sync and
  https://developer.android.com/develop/ui/views/notifications/notification-permission: dataSync covers downloads/local processing,
  requires FOREGROUND_SERVICE + FOREGROUND_SERVICE_DATA_SYNC, has no runtime prerequisite; POST_NOTIFICATIONS is not a launch gate.
  Android 13+ denied/unrequested permission hides drawer notices (Task Manager still shows FGS); prompt/control UI is a following increment.
  Owner clarified Android 10+ support: API 29 remains the primary phone baseline; minSdk stays 24, not 33. ServiceCompat guards pre-29 types,
  channels are API 26+, notification permission checks API 33+, onTimeout(int,int) API 35+; these are conditional paths, not a new minimum.
  Checked https://developer.android.com/reference/androidx/core/app/ServiceCompat and https://developer.android.com/reference/android/app/Service:
  promote in onStartCommand, stopForeground(REMOVE) + stopSelfResult latest delivered start, START_NOT_STICKY; stale hosts cannot demote newer work.
  Checked https://developer.android.com/develop/ui/views/notifications/build-notification: low-importance channel, generic ongoing progress,
  immutable explicit Activity PendingIntent, no URL/title/cookie data. Existing AndroidX APIs only; owner's launcher icon remains unchanged.
  Checked https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/with-timeout-or-null.html:
  bounded await uses a child coroutine and has cancellation/resource races; capture actual task Job outside it, await Unit, retain session
  through promotion/cancel/timeout and enqueue release after Home's cleanup. Coalesce worker updates; failed starts log only fixed safe errors.
  Android service/notification behavior is compiled/offline-tested only, not device evidence; no durability, automatic rescheduling or wake lock.

- **R67 (15 Nov 2026):** Rechecked https://developer.android.com/develop/ui/views/notifications/notification-permission and
  https://developer.android.com/training/permissions/requesting: Android 13+ RequestPermission via Activity Result; ask explicitly in context,
  accept denial/dismissal, re-read permission/app/channel state and never use the callback to start/resume a download. Android 10–12 has no prompt.
  Picker shows an optional Enable notifications button + inline purpose/denial explanation; existing direct Download tap stays independent.
  Checked https://developer.android.com/develop/ui/views/notifications/build-notification#Actions and
  https://developer.android.com/develop/background-work/background-tasks/broadcasts: immutable explicit broadcast PendingIntent to private receiver,
  per-task action identity (extras do not identify PendingIntents), no implicit filter/boot trigger/metadata. Receiver just calls process-local cancel,
  does not attach a new session, launch a service or await worker cleanup; stale/repeated/cold actions are no-ops. No new permission/dependency.
  Cancellation invokes native best-effort cancel with actual-Job cancellation in finally, retains service/session through cleanup; notification
  shows canceling and drops its Cancel action. Publication immediately closes cancellation admission, even before a queued saving notice renders.
  Timeout after user cancellation still stops service but cannot replace it with FGS_TIMEOUT; timeout-first rejects user Cancel and keeps the pause.
  Native exceptions/notification action inputs are never logged; fixed event/error strings only. Owner's icon, engine and minSdk 24 unchanged.
  CLI/fake-Engine evidence is not Android lifecycle/permission/notification proof. Next phone milestone remains Android 10 background/cancel/sound;
  API 33+ permission and API 35+ timeout need separate devices. No process-death/durable recovery or wake-lock claim.

- **R68 (15 Nov 2026, owner logging priority):** Owner requested all app-action/deep logs from opening the app through the Share tap before more features.
  Code audit: MainActivity shared `newestLogFile()` only; rotation could lose startup, UI controls had gaps, Home dropped resolve debugTail.
  Keep bounded rolling history but add a full private current-process journal + five immutable share exports; no active-session size cap, an explicit
  storage tradeoff for the owner's complete-timeline request. Share records its action and flushed-byte cutoff before async IO; no later writes in it.
  Clear resets coverage and private exports; a new process resets the journal. Write/export failures must not masquerade as a complete log.
  Fixed UI/lifecycle action names carry phase/quality/flags only, not text/clipboard/title; forward available resolve/download diagnostics and timings.
  Tests also exposed absent email redaction; add it alongside the existing cookie/auth/PO-token/signed-query defenses. No raw info JSON/secret files.
  No new dependency/permission/engine-option/identity change; public MediaStore mirror remains debug-only and restarts after Clear.
  These are recorded app diagnostics, not exhaustive Android events; crash/ANR/connectivity monitors remain unimplemented. Native share remains phone-untested.

- **R69 (15 Nov 2026, failed foreground phone milestone):** Owner's complete `6bb477b` export has three attempts; see
  `docs/evidence/foreground-6bb477b.md` for attachment hash, line references and UTC ordering. First: network unreachable/DNS failure;
  next two: media HTTP 403 at 5.6%/5.4%. Every Engine failure precedes service destruction; progress continues after Activity stop.
  No recorded Cancel/timeout/destroy/restart; code audit agrees with Home terminal lease release removing the notification, not UI cancellation.
  Rechecked https://developer.android.com/training/monitoring-device-state/doze-standby (idle network restrictions; FGS excludes App Standby)
  and https://developer.android.com/develop/connectivity/network-ops/data-saver (metered background restrictions and status APIs).
  https://github.com/yt-dlp/yt-dlp/wiki/FAQ documents that media access may depend on the same IP/headers; a change is a hypothesis, not observed.
  Current log has no connectivity/power-policy monitoring or same-video sustained foreground control. Screen-on failure does not establish Doze.
  Recommend same APK/video/1080p visible 60s then background 30s on unchanged connection; ask transport/VPN/settings, Share without Clear.
  No app change/engine update/power exemption; successful foreground milestone stays open. One startup-to-Share export received, not all share paths.

- **R70 (15 Nov 2026, visible-app HTTP 403 control and regression comparison):** Owner reports stable Wi-Fi/other apps working;
  pasted `6bb477b` session has two fresh `299+140` / 1920×1080 / 873.48 MiB attempts, each failing at 3.4% while Activity stays
  resumed. Failures precede service destruction; no Cancel/timeout/restart. Sanitized UTC table/limits in
  `docs/evidence/foreground-6bb477b.md`; paste has no independently hashed original file. Earlier successful downloads/playback stand.
  **Supersedes R69's missing foreground control and visible-60s/background-30s recommendation:** backgrounding is not required
  for media 403. Wi-Fi is owner-reported; this does not explain the earlier distinct NETWORK_OFFLINE/DNS failure or prove an IP stayed fixed.
  Source diffs `74ce7cf` → `6bb477b`: unchanged engine directory, replay, core Engine/planner/qualities and dependency versions.
  `ec146df` → `74ce7cf` changed DownloadExecutor's selector/output/evidence validation, not HTTP options/replay. Home admission/logging
  changes remain; no app regression established or every interaction ruled out. Catalog transport fields are not stripped by replay.
  Official tagged 2025.11.12 `_video.py` supplies 10 MiB HTTP chunks; `downloader/http.py` randomizes each to 95–100%, sends sequential
  bounded Range requests and treats HTTP 403 as fatal (ordinary retries will not handle it). Four rounded byte estimates ~28.54–29.70 MiB
  fit three chunks (28.5–30 MiB); traceback is connection establishment. No exact byte/request/header capture, so not a proven chunk cause.
  Sources: https://github.com/yt-dlp/yt-dlp/blob/2025.11.12/yt_dlp/extractor/youtube/_video.py and sibling `downloader/http.py`.
  `-N 4` is fragment concurrency, not direct HTTP chunk concurrency; no evidence justifies changing it. `74ce7cf`'s 7,737,267-byte save
  was below one chunk, compatible with these larger-transfer failures; no claim about every earlier sample's size. Fresh manual resolve
  also failed, so stale metadata/re-resolve alone is not demonstrated as the cause/fix. QuickJS/ffmpeg debug lines confirm runtime presence.
  **Beyond an age warning:** https://github.com/yt-dlp/yt-dlp/issues/15712 documents old `android_sdkless` streams starting then 403;
  maintainer comment 3819129364 says fixed in 2026.01.29, merged PR https://github.com/yt-dlp/yt-dlp/pull/15726. 2025.11.12 still uses
  that default client; the owner's redacted log does not prove it supplied selected URLs or that a PO token is required.
  Separate later `android_vr` issue https://github.com/yt-dlp/yt-dlp/issues/17456 (maintainer comment 5354789281) fixed in 2026.08.19;
  not evidence to hard-code that older workaround. GitHub API recheck: latest stable 2026.10.26; tagged `_video.py` defaults
  `tv_samsung`, `visionos`, `web`. Upstream fixes are facts; same-phone success with them is untested.
  **Recommendation/decision:** prioritize a narrowly owner-approved pinned APK-bundled engine-freshness investigation before new
  features or further background testing. Verify integrity/packaging/Python/EJS/QuickJS compatibility, keep Android 10/minSdk 24 and
  existing planner/replay/cancellation. Gate remains closed pending approval; no runtime updater/remote components/login/PO-token work.
  No app/test/engine/permission/APK change. No battery-setting cause or power exemption justified. Original current APK stays `6bb477b`.

- **R71 (15 Nov 2026, owner's desktop bot-check example):** Owner pasted a desktop log dated 14 Nov: webpage HTTP 429,
  followed by explicit "Sign in to confirm you're not a bot", exit 1 during extraction; no successful format catalog/media transfer.
  It is a different device/video/session; exact engine version and shared public IP are not established by this excerpt.
  Rechecked https://github.com/yt-dlp/yt-dlp/wiki/PO-Token-Guide: distinguishes Player (fetch format URLs), GVS (media bytes),
  and subtitle checks; affected media URLs may return HTTP 403 without a required client PO token. Format discovery or initial
  bytes therefore do not rule out later client/anti-bot enforcement. Phone logs establish media refusal, not an explicit sign-in
  gate or its exact cause. The wiki's July 2026 client table is not treated as newer than R70's later tagged fixes.
  Updating the engine is not guaranteed to fix a sign-in/IP gate; do not conflate the desktop's explicit gate with phone 403s.
  No engine/login/cookie/PO-token change approved or implemented; R70's narrow refresh recommendation remains a test candidate.

- **R72 (15 Nov 2026, approved APK-only engine packaging investigation):** Owner said "ok go" to R70's narrow
  pinned-bundle investigation; supersedes R70/R71's historical approval-pending language, not the no-runtime-code rule.
  Maven metadata still latest/release library 0.18.1; official stable API still 2026.10.26. Downloaded official platform-independent
  `yt-dlp` to approved temp only: 3,073,072 bytes, SHA-256 `e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3`.
  Matches API digest/signed SHA2-256SUMS; isolated Git GPG returned Good signature/VALIDSIG, exit 0, fingerprint
  `AC0CBBE6848D6A873464AF4E57CF65933B5A7581`. Key from tagged upstream repository, trust undefined, no independent identity proof.
  Zipapp vendors EJS 0.8.0/local solvers and lazy extractors, no native extensions; solver hashes match its pinned vendor table.
  Python >=3.11 / QuickJS >=2023-12-09 (recommended >=2025-04-26) match existing phone 3.12.11/2025-04-26 by version;
  native Android behavior remains untested. Host CPython 3.14 version/EJS-resource/ffmpeg-path/replay-format-list smokes passed;
  1,046 Python files parsed with 3.12 grammar. Not a live download, Android-runtime or anti-bot success check.
  Published library sources JAR confirms engine copy only if absent: resource-only APK upgrade leaves old installed zipapp.
  Public path constants allow local pre-init replacement; updater version prefs can be stale. Recommend tests-first atomic,
  hash-pinned process-wide installation before init; preserve native runtime/adapter runner/planner/replay/cancellation/logs.
  Candidate licenses read: Unlicense yt-dlp/EJS, bundled Meriyah 6.1.4 ISC/Astring 1.9.0 MIT notices required in integration.
  **Docs conflict:** R15/R24's historical runtime-updater plans are not authorized by this approval; APK-only supersedes them.
  `THIRD_PARTY_NOTICES.md` still says QuickJS 2021-03-27, contrary to R23/R25/owner logs and current requirement checks;
  recommend correcting that stale notice alongside integration (owner confirmation requested), not downgrading/bumping native code.
  Sources, exact boundaries and next installer/device test plan: `docs/evidence/engine-refresh-2026.10.26.md`.
  No app/dependency/permission/test/APK change in this investigation; verified asset is a test candidate, not a proven 403 fix.

- **R73 (16 Nov 2026, APK-only pre-init activation APIs):** Re-read published 0.18.1 `YoutubeDL.kt`/updater sources JAR (R72):
  private destination uses public `baseName`, `ytdlpDirName`, `ytdlpBin`; engine copies only if absent and `init` runs no engine command.
  `version(context)` reads updater preferences, so installed hash pin must replace that reporting source; later verbose versions remain observable.
  [Android Os.rename reference](https://developer.android.com/reference/android/system/Os#rename(java.lang.String,%20java.lang.String))
  confirms `rename(String, String)` added API 21, throwing `ErrnoException`; usable at unchanged minSdk 24/Android 10 baseline.
  [rename(2)](https://man7.org/linux/man-pages/man2/rename.2.html) documents atomic existing-target replacement and target preservation on failure.
  Use synced, hash-verified sibling staging in app-private storage; never delete old target first or use a non-atomic fallback.
  Shared startup gate serializes install + library init, publishes readiness only on full success, and never reinstalls after ready in this process.
  New process rechecks the pin; no runtime downloads/updater preference writes/native-runtime changes. Android atomic/runtime/download behavior untested;
  file sync + rename is not a verified abrupt-power-loss durability guarantee. R72 scope/phone test plan remains unchanged.

- **R74 (16 Nov 2026, original audio selector/evidence and MIME):** The verified bundled 2026.10.26 zipapp passed nine
  host-only exact `ba[format_id='…']` selector/missing-ID/muxed-rejection cases with socket connections blocked (previous increment).
  Its synthetic replay CLI `--load-info-json --skip-download --no-simulate` emitted selected ID with NA/NA dimensions and
  the explicit before_dl vcodec=none/acodec marker; no extraction/media request or real playback was tested.
  [WebM Project container guidelines](https://www.webmproject.org/docs/container/) (last modified 2026-05-07, re-read today)
  specify audio-only MIME **audio/webm** and Vorbis/Opus audio. Original WebM audio must not inherit the video/webm publisher default.
  Offer only reported original streams with separate video/audio selection; no synthetic MP3 bitrate or conversion options.
  Owner's screenshots are behavior references only; no third-party code/assets were read/copied. Android audio playback remains unverified.

## Still unverified (carried forward)

- Whether AGP 9.5.1 accepts `compileSdk = 37` with `build-tools;37.0.0` — **proved**: `assembleDebug` succeeded and `aapt2` reports
  `compileSdkVersion='37'` (docs/PROGRESS.md).
- Whether the Compose compiler plugin at 2.2.10 coexists with AGP 9.5 built-in Kotlin — **proved** by the same build (R13).
- **Phase 0, answered on-device 15 Nov 2026** (R23): QuickJS does start (Android 10 / API 29, arm64), the default client order
  returned 1080p-class adaptive formats **with audio** and no withholding for the tested Shorts link, first-run unpack took 8.3 s,
  and the MediaStore publish landed in `Download/ClipHarbor` (`content://media/external/downloads/18406`). Still open: whether the
  earlier file's gallery visibility and behaviour on other videos/sites. Owner confirmed latest `ec146df` download playback
  with sound (R53); new `74ce7cf` standard-plan transfers/MKV playback need separate phone verification.
