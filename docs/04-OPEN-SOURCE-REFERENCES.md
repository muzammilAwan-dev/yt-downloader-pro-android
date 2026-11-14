# 04. Open-source references

"Reuse" column: **Study** = read the repo, write your own code. **Depend** = use as a library under its license. Nothing here
was copied into this package. Licenses marked [V] were seen on the project's own page or Maven Central this session; [K] means
general knowledge to re-check.

| Project | License | What to learn | Reuse |
|---|---|---|---|
| **youtubedl-android** (yausername; published as `io.github.junkfood02.youtubedl-android`) | GPL-3.0 [V] | Embeds yt-dlp + Python; separate ffmpeg and aria2c modules; `getInfo` (= `--dump-json`), process IDs for cancel, `updateYoutubeDL` with STABLE/NIGHTLY channel; needs `extractNativeLibs="true"`; scoped-storage caveats; 0.18.1 (16 Nov 2025) adds QuickJS per Seal PR #2659 | **Depend** (makes the app GPL-3.0) |
| **Seal** (JunkFood02) | GPL-3.0 [V] | Kotlin, Compose, Material 3, single activity, composable destinations, custom command templates, aria2c, metadata/thumbnail embedding, Weblate translations; name may not be reused | **Study** |
| **YTDLnis** (deniscerri) | GPL-3.0 [V] | MVVM + WorkManager; share-menu bottom card; queue + scheduling by date/time; playlist item editing; cookies; cuts by timestamp/chapter; incognito mode; backup/restore; Tasker intents (TYPE audio/video/command, BACKGROUND true/false); reproducible builds via GitHub workflow; F-Droid + IzzyOnDroid; name may not be reused | **Study** |
| **dvd** (yausername) | not checked | Minimal reference app for youtubedl-android | **Study** |
| **NewPipe** (TeamNewPipe) | GPL-3.0 [K] | PR #11955 adds `PoTokenGenerator` base class and `PoTokenWebView` (only implementation); designed so non-WebView generators can be added | **Study** |
| **yt-dlp** | Unlicense [K] | Engine; EJS (JS runtime), PO Token Guide, client list | **Depend** (via library) |
| **yt-dlp-ejs** | Unlicense, bundles MIT and ISC [V] | The JS challenge solver yt-dlp pins to each release | via yt-dlp |
| **bgutil-ytdlp-pot-provider** (Brainicism) | not checked | PO Token Provider plugin: HTTP server or script mode; featured on the yt-dlp PO Token Guide and described there as maintained by a yt-dlp maintainer | **Study** (feasibility on Android unknown) |
| **rustypipe-botguard** + `yt-dlp-get-pot-rustypipe` | not checked | Standalone BotGuard binary and plugin for web-based clients | **Study** |
| **ytdlp-ejs (Rust port)** | not checked | EJS solver with QuickJS in a binary under 5 MB (v0.1.1, Jan 2026) | **Study** (experimental) |
| **Read You**, **Music You** | not checked | UI sources Seal credits | **Study** for Compose/M3 patterns |
| **F-Droid / IzzyOnDroid** | n/a | Distribution; IzzyOnDroid shows permission lists, library counts and malware check per APK | process |

## Features worth borrowing as *ideas* (priority order)

1. Share-sheet bottom card, no full app launch (YTDLnis).
2. "Quick download" toggle that skips the card (YTDLnis).
3. Playlist items editable individually (YTDLnis).
4. Scheduling downloads by date/time (YTDLnis). Phase 4.
5. Incognito mode: no history, no logs (YTDLnis). Cheap to add; fits the privacy story.
6. Backup/restore of settings (YTDLnis, Seal templates).
7. Cuts by chapter as well as timestamp (YTDLnis marks it experimental; so does yt-dlp).
8. Command templates for power users (Seal). Keep behind the allowlist or an "advanced, unsafe" switch.
9. Metadata + thumbnail embedding for audio (Seal).
10. Reproducible-build workflow so the APK signature proves origin (YTDLnis). Matches the developer-verification landscape.

## How to read a repo for ideas without copying

Read the module that owns the behaviour (download worker, options builder, cookie handling), write down the *rules* it enforces in your
own words (as `docs/03` does for Snaptube), then implement from the rules. The project default is **do not copy**. If the owner later decides to reuse GPL code, keep its copyright and
license header and record it in `THIRD_PARTY_NOTICES.md`; GPL-3.0-compatible reuse is possible because this app is GPL-3.0 (not legal advice).
