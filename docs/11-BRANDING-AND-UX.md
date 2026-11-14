# 11. Branding and UX

## 1. Positioning

**Main identity: "the yt-dlp Android app", an unofficial, free, open-source Android front-end (alternative) for yt-dlp.**
Snaptube is an *inspiration for ease of use* (paste/share, quick queue, site shortcuts), not a brand to imitate. Tagline pattern:
"yt-dlp for Android: simple, private, free." Keep "unofficial" and "powered by yt-dlp" visible in the About screen and README.

## 2. Naming rules

- Do not use "Snaptube", "Seal", "YTDLnis" or "YouTube" in the app name, icon or package id. Seal and YTDLnis explicitly forbid reuse of their names. [V]
- Do not imply the yt-dlp project endorses the app. Use "based on yt-dlp" wording, as Seal does. [V]
- The desktop name "YT Downloader Pro" is already crowded: other GitHub repos, a commercial "YT Downloader Pro 10.x" and ytdownloader.pro appeared in search results. [V]
  Pick a more distinctive name for the Android app, or at least a distinctive suffix.
- Working-name ideas (availability, trademarks, F-Droid and package-name collisions NOT checked): `YTDLP Droid`, `Droid DLP`, `Pocket DLP`, `DLP Pocket`.
  Check GitHub, F-Droid, IzzyOnDroid and a trademark search before you commit.
- Package id: reverse-domain you control; GitHub-based ids like `io.github.<handle>.<app>` are common (youtubedl-android publishes under `io.github.junkfood02`). [V] No hyphens allowed in
  package segments; use underscores or none. Decide once; changing it later forces users to reinstall.

## 3. UX inspired by Snaptube (from the export, no code)

What the export reports describe: a browser/search shell with "sites" bookmarks and speed-dial, an in-app web view and tabs, search across supported sources, link interception for
Facebook/Instagram-style URLs, and a clipboard monitor that offers to download when a link is copied (default catalogue of 48 sites with icons in `assets/full_sites.json`). [V, export reports]

| Snaptube-style idea | Our approach | Phase |
|---|---|---|
| Fast path from link to download | Share-sheet bottom card; paste field; "quick download" toggle (YTDLnis precedent) | 1 |
| Site shortcuts / speed-dial | Grid of the nine supported sites from `contracts/sites.json`, with **our own** icons, opening the site in a Custom Tab; user shares the link back | 2 |
| Clipboard offer | Only when the app is in the foreground: "Paste link" chip. No background clipboard service | 1 |
| Search across sources | Out of scope for MVP; yt-dlp supports `ytsearch:` prefixes, so an optional search box is cheap later | 4 |
| In-app browser with page sniffing | Not planned: it needs WebView interception and carries policy risk. Prefer sharing the page URL to yt-dlp | later, if ever |
| Queue / downloads tab | Persisted queue with pause/resume/retry, swipe to retry/delete (YTDLnis precedent) | 1 |
| Ads, cleaner, vault | Never | n/a |

## 4. Visual design

Material 3 with dynamic color (Seal precedent), dark theme first, large touch targets, one-handed layout (primary actions in the lower half), clear states per task stage with
the error taxonomy's recovery button. Check accessibility (TalkBack labels, contrast, font scaling) in Phase 3.
