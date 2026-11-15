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
| **yt-downloader-pro** (the owner's desktop app: `muzammilAwan-dev/yt-downloader-pro`) | MIT [V] | The reference implementation this app is the Android use case of: command allowlist validator, `-N 4` throttling bypass, compatibility mode, engine updater safety rules, progress/format parsing, settings defaults | **Study** now; **port with attribution** only on the owner's explicit request (MIT → GPL-3.0 is compatible). Details below. |

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

## Sibling project: `muzammilAwan-dev/yt-downloader-pro` (the owner's desktop app)

Read on 15 Nov 2026 at the owner's request, as the reference implementation whose Android use case this app is. MIT
licensed (`LICENSE` in that repo), C#/.NET 8 WPF + a Manifest V3 Chrome extension, 55 files, ~15 MB (mostly screenshots).

Why it matters: the hand-off package was written without seeing this code, and `contracts/allowed-options.json` literally
says "reconcile this file with `host/YTDLPHost/Services/CommandValidator.cs` before shipping". That file is now read, so the
allowlist can be reconciled instead of guessed.

### What is directly reusable as *rules* (not code)

| Their behaviour | File | Android use |
|---|---|---|
| Strict allowlist: value flags + boolean flags, every other token must be an `http(s)` link, path arguments must sit in an allowed dir with an allowed extension, reject control characters / escaped quotes / unbalanced quotes / `..` / UNC | `Services/CommandValidator.cs` | Seed for `contracts/allowed-options.json`; on Android the untrusted input is a share intent or deep link, not a `ytdlp://` payload. Same three rules transfer: default deny, links only, paths constrained. |
| `-N/--concurrent-fragments` (default 4) rather than an additional downloader | `MainViewModel.cs`, README | Adopt bounded parallelism for DASH/native-HLS fragments without an aria2c dependency. **Not a universal throttling bypass** or extra connections for every HTTP download; primary-source semantics clarified in research R27. Phase 1 default 4; measure performance/battery on-device. |
| Compatibility mode = force H.264/AAC | README, `YtDlpRunner.cs` | Already our default in `FormatPlanner`; theirs is a user switch. Keep ours default-on, expose a "best quality (any codec)" switch in Phase 1. |
| Detecting the silent fallback to progressive itag 18 with `[info] <id>: Downloading N format(s): <ids>` and warning the user (PO-token/SABR withholding) | `Services/YtDlpRunner.cs` | This is exactly what our spike lacks: the Phase 0 log cannot prove which format was chosen. Phase 1: parse that line, log `format_ids=`, and surface "HD withheld" when it is a single low muxed id. |
| Learning the merged filename from `[Merger] Merging formats into "<path>"` | same | Better than our timestamp-based `findOutputFiles()` heuristic; adopt in Phase 1. |
| Progress parsing: percent / speed / ETA / size, plus ffmpeg stats for section cuts and live streams | same | Our library already yields percent+ETA; keep the ffmpeg-stats regexes for Phase 2 trim. |
| Engine update: `--update-to <channel>`, version before/after, map raw output to a user sentence, network-error heuristics, retry in 15 min when downloads are running, never replace mid-download | `Services/EngineUpdater.cs` | Adopt the *safety rules* in our Phase 2 updater. Their update still trusts the downloaded asset, so our "verify SHA-256 + rollback" requirement stands. |
| Settings defaults: 3 concurrent downloads, unlimited speed, channel `nightly`, `YoutubePlayerClients` empty (emergency override only) | `Models/AppSettings.cs` | Defaults to mirror: 3 tasks, no speed limit, stable channel for release builds; do **not** ship a player-client override in the UI (log the value instead). |
| History as debounced JSON that survives crashes | `Services/HistoryManager.cs` | Room replaces the file, but the semantics (write-through on every state change, survive process death) carry over to Phase 1. |
| Cookie detection by name (`SAPISID`, `__Secure-3PAPISID`) | `YtDlpRunner.cs` | Pairs with our `LogRedactor` tests; Android login arrives via WebView in Phase 2. |
| Per-site on/off switch, 9 sites | `extension/sites.js` + README | Validate `contracts/sites.json` against their list (YouTube/Shorts, Instagram, Facebook, X, TikTok, Reddit, Twitch, Vimeo, SoundCloud). |

### What deliberately does **not** transfer

- The Manifest V3 extension (hover button, DOM scraping per site, cookie capture from Chrome). Android equivalent is the
  share sheet + a WebView login in Phase 2; site-specific page scraping is a maintenance trap we are not re-creating.
- Windows-only plumbing: `ytdlp://` protocol registration, single-instance mutex, tray icon, Inno Setup, WPF checkboxes.
- Their "photos from Instagram/Facebook/X" path: an image saver is a Phase 4 extra, not MVP.
- `deno` as the JS runtime: we already ship QuickJS through youtubedl-android, proven on the device.

### Licensing note

The owner explicitly authorized freely reusing their desktop code on 15 Nov 2026. MIT → GPL-3.0 is compatible in this
direction; if any file is copied or closely derived under its published license, record it in `THIRD_PARTY_NOTICES.md`
with the MIT notice. No files were ported in the logging/version increments. Their repo is not on the AGENTS.md
never-copy list; that prohibition still applies to the other named projects.

### Concrete adoption list (added to the Phase 1/2 backlog)

1. Phase 1: `-N 4` default, `[info] … format(s)` parsing, `[Merger]` filename parsing, 3-task concurrency, write-through
   history. *(also closes items 1–2 of `docs/evidence/phase0.md` §7)*
2. Phase 1/2: reconcile `contracts/allowed-options.json` with `CommandValidator.cs` (done for the comment; flags when the
   UI grows).
3. Phase 2: trim via `--download-sections` + ffmpeg stats, playlist ranges via `--playlist-items`, SponsorBlock as an
   opt-in that names the third-party service it contacts, WebView cookie login.
4. Phase 4: image-post saving; site toggles surfaced from `contracts/sites.json`.

## How to read a repo for ideas without copying

Read the module that owns the behaviour (download worker, options builder, cookie handling), write down the *rules* it enforces in your
own words (as `docs/03` does for Snaptube), then implement from the rules. The project default is **do not copy**. If the owner later decides to reuse GPL code, keep its copyright and
license header and record it in `THIRD_PARTY_NOTICES.md`; GPL-3.0-compatible reuse is possible because this app is GPL-3.0 (not legal advice).
