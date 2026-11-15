# 11. Branding and UX

## 1. Positioning

**Main identity: "YTDLP" (You Tube Downloader Pro), an unofficial, free, open-source Android front end for yt-dlp.**
Snaptube is an *inspiration for ease of use* (paste/share, quick queue, site shortcuts), not a brand to imitate. Tagline:
"Simple, private, free." Keep "unofficial" and "powered by yt-dlp" visible in the app and README.

Latest owner decision (15 Nov 2026): **YTDLP (You Tube Downloader Pro)**, matching the owner's desktop brand, with their
supplied `host/YTDLPHost/Assets/icon.ico`. This supersedes the earlier ClipHarbor name and no-"yt" preference. Retain
package id `io.github.muzammilawan.clipharbor` for update continuity; private cleanup keys and theme names need not change.
Do not imply yt-dlp or any supported website endorses the app. Name/trademark availability has **not** been cleared;
check GitHub, F-Droid, IzzyOnDroid and trademarks before a public release. Owner choice is not a clearance claim.

## 2. Naming rules

- Do not use "Snaptube", "Seal", "YTDLnis" or "YouTube" in the app name, icon or package id. Seal and YTDLnis explicitly forbid reuse of their names. [V]
- Do not imply the yt-dlp project endorses the app. Use "based on yt-dlp" wording, as Seal does. [V]
- The desktop name "YT Downloader Pro" is already crowded: other GitHub repos, a commercial "YT Downloader Pro 10.x" and ytdownloader.pro appeared in search results. [V]
  The owner now explicitly chooses their desktop brand anyway; this crowding concern remains a public-release check.
- Working-name ideas (availability, trademarks, F-Droid and package-name collisions NOT checked): `YTDLP Droid`, `Droid DLP`, `Pocket DLP`, `DLP Pocket`.
  **Historical: first superseded by `ClipHarbor`, then by the owner's current `YTDLP` decision.**
  Availability/trademark review remains required before public release, not claimed by this rename.
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
| In-app browser with media discovery | Owner-requested feasibility research: optional non-DRM discovery alongside yt-dlp; direct website login and safe session transfer need separate proof. Not added to MVP. See doc 16 | post-MVP research; implementation scope undecided |
| Queue / downloads tab | Persisted queue with pause/resume/retry, swipe to retry/delete (YTDLnis precedent) | 1 |
| Ads, cleaner, vault | Never | n/a |

## 4. Visual design

Material 3 with dynamic color (Seal precedent), dark theme first, large touch targets, one-handed layout (primary actions in the lower half), clear states per task stage with
the error taxonomy's recovery button. Check accessibility (TalkBack labels, contrast, font scaling) with each UI increment;
Phase 3 is the final release audit, not the first check. Owner screenshots were reviewed on 15 Nov 2026; visual
observations and their limits are recorded in `16-BROWSER-AUTH-AND-MEDIA-DISCOVERY.md` §1. Use original YTDLP layouts,
tokens, assets and navigation labels, not a traced replica.

### Owner-supplied icon

- Use the owner's desktop ICO's largest 256x256 PNG frame unchanged for Android (not competitor artwork).
- API 24-25 uses a bitmap fallback; API 26+ uses the same image in an adaptive wrapper. The launcher controls the outer
  mask, so it may be circular/squircle rather than the desktop rounded square. The red play/download symbol fits the
  central safe zone. Final OEM appearance and themed/monochrome treatment remain a device/release audit item.
- Desktop MIT notice is included in `app/src/main/assets/licenses/ytdlp-desktop-icon-MIT.txt` and credited in
  `THIRD_PARTY_NOTICES.md`. No competitor assets or desktop source code copied.
- New downloads/logs use `Download/YTDLP` and `Download/YTDLP-logs`; existing ClipHarbor files are left untouched.
