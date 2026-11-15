# 10. Verification log

Date of research: 14 Nov 2026. "Verified" means I saw it on the cited page in this session (search snippet or fetched page). Search results are not the same as reading
every primary document; confidence is stated honestly.

## A. Verified facts

| # | Claim | Source | Confidence |
|---|---|---|---|
| 1 | yt-dlp 2025.11.12 added external JS runtime support; Deno default; Node, QuickJS, QuickJS-ng, Bun supported; QuickJS minimum 2023-12-9; yt-dlp pins an exact yt-dlp-ejs version | github.com/yt-dlp/yt-dlp/issues/15012; github.com/yt-dlp/yt-dlp/wiki/EJS | High |
| 2 | Missing runtime shows as a warning followed by HTTP 403 | github.com/JunkFood02/Seal/pull/2659 | Medium (PR text) |
| 3 | youtubedl-android 0.18.1 released 16 Nov 2025; adds QuickJS and passes the runtime to yt-dlp | central.sonatype.com (version/date); Seal PR #2659 (feature claim) | High date, Medium claim |
| 4 | youtubedl-android library is GPL-3.0 | central.sonatype.com POM; mvnrepository.com | High |
| 5 | library AAR 0.16.0 = 39.1 MB | mvnrepository.com | High |
| 6 | Seal: GPL-3.0, Kotlin/Compose, single activity, aria2c, Weblate, 29.6k stars, name-reuse prohibited | github.com/JunkFood02/Seal | High |
| 7 | YTDLnis: GPL-3.0; MVVM + WorkManager; share card; scheduling; cookies; cuts; incognito; Tasker intents TYPE/BACKGROUND; reproducible-build workflow; IzzyOnDroid v1.7.9.2 arm64 36.1 MB, MinVer 6.0, target 13 (Sept 2024) | github.com/deniscerri/ytdlnis; apt.izzysoft.de/packages/com.deniscerri.ytdl | High (listing is dated) |
| 8 | A PO token comes from BotGuard (web), DroidGuard (Android) or iOSGuard (iOS) and is not portable across platforms; recommended setup is a provider plugin for `mweb` | github.com/yt-dlp/yt-dlp/wiki/PO-Token-Guide | High |
| 9 | NewPipe PR #11955 adds PoTokenGenerator and a WebView implementation | github.com/TeamNewPipe/NewPipe/pull/11955 | High |
| 10 | Android 15 dataSync FGS: 6h per 24h, `onTimeout`, mediaProcessing type, adb test flags | developer.android.com/about/versions/15/behavior-changes-15 | High |
| 11 | F-Droid open letter against developer verification, 24 Feb 2026 | f-droid.org/2026/02/24/open-letter-opposing-developer-verification.html | High |
| 12 | opencode reads `AGENTS.md` from the project root (and a global one), supports `instructions` in `opencode.json` with paths/globs, does not auto-follow file references inside AGENTS.md, falls back to `CLAUDE.md` | opencode.ai/docs/rules | High |
| 13 | Desktop repo: Windows-only MV3 extension + .NET 8 WPF host, MIT, no telemetry; v6.4.9 notes on 360p cap and cookie allowlist | github.com/muzammilAwan-dev/yt-downloader-pro (README, releases) | High |
| 14 | Name "YT Downloader Pro" is used by other projects/products | search results (utkarsh-raut repo, ytdownloader.pro, commercial 10.x listing) | High |
| 15 | Snaptube export numbers (28,898 classes, 34 permissions, 36 native libs, 224/42 components, 188 hosts, 34.9 MB) and class observations in doc 03 | user's export.zip: 00-BRIEF.md, 02-report-*.md, 50-deep-dive.md | High (static analysis only) |
| 16 | yt-dlp player client names on PyPI 2026.10.26 page | pypi.org/project/yt-dlp | Medium (list from page text) |

## B. Secondary or weaker

| Claim | Source | Note |
|---|---|---|
| Bun support deprecated 21 May 2026 | GIGAZINE article citing yt-dlp issue #16766 | Read primary issue to confirm |
| Google Play prohibits apps facilitating unauthorized downloads; Seal/NewPipe distributed outside Play | androidheadlines.com (May 2026) and other press | Read Google's policy text |
| Developer verification: enforcement 30 Sept 2026 in four countries, wider in 2027; ~85% of F-Droid catalog signed by F-Droid | pinggy.io blog, Android Authority coverage | Press/blog level |
| A free default model in opencode is `opencode/deepseek-v4-flash-free` | third-party kit page on pypi.org (osdlc-kit) | Model names change; check opencode's current list |
| Datacenter IPs can be blocked while a hotspot works | yt-dlp issue #16747 snippet | Single anecdote |

## C. Not verified (treat as unknown)

1. That `youtubedl-android` + current yt-dlp returns 1080p on a real device today (Phase 0).
   *15 Nov 2026: the app side is built and tested; the device half is still open (docs/PROGRESS.md checklist).*
2. The bundled Python version in the library and whether the latest yt-dlp still supports it.
   *15 Nov 2026: **answered from the artifact** — CPython 3.12 (see E.3).*
3. Licenses of the FFmpeg, aria2c and QuickJS builds shipped with the library; whether F-Droid main accepts prebuilt binaries for this app.
   *15 Nov 2026: FFmpeg = GPL build with x264; QuickJS present as MIT `libqjs.so`; aria2c not shipped. F-Droid acceptance is still open (Phase 3).*
4. Whether any PO Token Provider plugin can run inside the embedded Python on Android.
5. Contents of the desktop repo's source files, `CommandValidator`'s true allowlist, and the mac/linux branch.
6. The decompiled Snaptube source beyond the classes named in doc 03; its runtime behaviour; its YouTube extraction method.
7. Current text of Google Play policy and Google's developer-verification page.
8. Exact current major versions of GitHub Actions used in the workflow (pinned to v4 majors from memory and examples; searches also showed newer majors such as checkout v6/v7, setup-java v5/v6 and
   setup-gradle v6 in some sources; Dependabot is configured to propose bumps). `gradle/actions` v6 is described as including a proprietary caching component; staying on v4 avoids it. Re-check before first run.
9. The `reactivecircus/android-emulator-runner@v2` inputs (written from memory).
10. Time estimates and the minSdk 24 recommendation (my judgement).
11. Trademark and name availability for any app name.

## D. Package self-check

Run `python3 tools/verify_handoff.py` (JSON validity, workflow structure, links, and a scan proving no decompiled Snaptube code is included).
It does **not** prove the workflow runs on GitHub; no Actions runner was available here.

## E. Phase 0 verification (15 Nov 2026, this machine unless stated)

| # | Claim | How checked | Confidence |
|---|---|---|---|
| E1 | `io.github.junkfood02.youtubedl-android:{library,ffmpeg}:0.18.1` is the latest published version and the POM license is GPL-3.0 | Maven Central `maven-metadata.xml` + POM | High |
| E2 | The artifacts carry native payloads for `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`; sizes 59.2 MB and 139.4 MB | downloaded both AARs and listed entries | High |
| E3 | Bundled CPython is **3.12**; bundled yt-dlp is **2025.11.12** with `yt_dlp_ejs` and solver JS included | extracted `res/raw/ytdlp` (shebang + zip), read `yt_dlp/version.py`, `yt_dlp_ejs/*`; listed `libpython.zip.so` | High |
| E4 | Upstream yt-dlp stable is **2026.10.26**; standalone `yt-dlp-ejs` is **0.8.0** | GitHub releases API | High |
| E5 | The ffmpeg payload is a **GPL build** (`libx264.so.164`) and ships no LICENSE/COPYING; QuickJS is `libqjs.so` | extracted `libffmpeg.zip.so` | High |
| E6 | PO-token requirements per client (`web`/`mweb`/`android`/`ios` = GVS token needed; `tv`/`web_embedded`/`android_vr` = not) | yt-dlp wiki, edited 12 Jul 2026 | High (state changes) |
| E7 | Android 17 has no all-app change that blocks this app; Android 18 will stop implicit URI grants | developer.android.com behaviour-changes page | High |
| E8 | `extractNativeLibs=true` is produced by `jniLibs.useLegacyPackaging = true` and legacy libs are compressed in the APK | `aapt2 dump xmltree` on the built APK + zip entry sizes | High (reproduced) |
| E9 | The APK requests only `INTERNET` plus the AndroidX-generated signature permission; cleartext is off | same manifest dump | High (reproduced) |
| E10 | 46 JVM tests and lint pass; APK builds | `gradlew testDebugUnitTest lintDebug assembleDebug`, `tools/build-debug.ps1` | High (reproduced) |
