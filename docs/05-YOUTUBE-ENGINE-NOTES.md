# 05. YouTube engine notes

## 1. JavaScript runtime (EJS)

- yt-dlp 2025.11.12 introduced the External JavaScript system. YouTube support without a JS runtime is deprecated; formats may be
  missing and some requests can fail. yt-dlp pins an exact `yt-dlp-ejs` version per release and rejects others. [V, yt-dlp issue #15012]
- Supported runtimes: Deno (enabled by default), Node, QuickJS, QuickJS-ng, Bun. [V] Minimum QuickJS is 2023-12-9; all QuickJS-ng
  versions work; upstream QuickJS is recommended over QuickJS-ng for performance. [V, #15012 and EJS wiki]
- Bun support was announced deprecated on 21 May 2026 (issue #16766). [S, GIGAZINE]
- Enable QuickJS with `--js-runtimes quickjs` or `--js-runtimes quickjs:/path/to/qjs`. QuickJS cannot run scripts from stdin, so yt-dlp
  writes temp files for each EJS run. [V, EJS wiki]
- Symptom of a missing runtime in logs: a warning that no supported JavaScript runtime was found, then HTTP 403 on media. [V, Seal PR #2659]
- `youtubedl-android` 0.18.1 (16 Nov 2025) is reported to add QuickJS and pass it to yt-dlp. [V date; claim from PR text]

**Phase 0 must record:** the bundled Python version, the yt-dlp version, whether `yt-dlp-ejs` is present and version-matched, and the
exact `--js-runtimes` value that worked. Check whether the library's bundled Python is still supported by the yt-dlp you update to (not verified).

## 2. PO tokens (HD formats)

- A PO token is minted by BotGuard (web), DroidGuard (Android) or iOSGuard (iOS); a token from one platform cannot be used on another. [V, yt-dlp PO Token Guide]
- yt-dlp's recommended setup: a PO Token Provider plugin supplying a token for the `mweb` client's media requests; some HLS formats don't
  need the media-request token "at this time"; Premium accounts are exempt. [V, guide]
- Desktop experience: forcing `web_safari` and `mweb` without a provider capped downloads at 360p (v6.4.9); letting yt-dlp choose clients fixed it. [V, desktop release notes]
- NewPipe mints tokens with a WebView (`PoTokenWebView`). Snaptube has PO-token exception classes. [V for NewPipe PR; I for Snaptube]
- Providers exist as yt-dlp plugins: bgutil (HTTP server or script, Node/Deno), rustypipe-botguard (binary). [V] Whether any can run inside
  the embedded Python on Android is **unknown**.

**Plan:** MVP uses yt-dlp's own client choice and reports `FORMATS_WITHHELD` honestly. Keep a settings field for player clients
(desktop hotfix value was `default,tv`). Research a WebView-based PO-token provider only after Phase 2.

## 3. Cookies / login

Desktop: cookies captured only when the user enables "Bypass Age & Bot issue"; Vimeo always (cannot download anonymously); analytics and
consent cookies excluded; written to a temp file, deleted after the download, never persisted in history. Android: same rules (doc 02, section 6).

**15 Nov clarification:** the preceding sentence describes desktop behaviour and its assumptions, not a guarantee that
all Vimeo content needs login or that cookies eliminate every bot failure. The owner reports YouTube website login inside
Snaptube's browser, recommended by a red message after bot errors. Treat this as website-session behaviour, not necessarily
a third-party OAuth button. Adapt its high-level authentication-aware flow; exact session handoff is being traced from
the original REA reports. Current yt-dlp guidance warns of account bans/rotating cookies and says YouTube OAuth login no
longer works with yt-dlp. Cookies, PO tokens and EJS solve different problems.

App-owned cookie transfer is a feasibility proposal: AndroidX `CookieManagerCompat.getCookieInfo` exposes URL-scoped
attributes when `GET_COOKIE_INFO` is supported, unlike legacy `getCookie` header pairs. Google may reject embedded
website sign-in, and Custom Tabs cannot supply their cookies to our engine. No login implementation or device test yet;
see `16-BROWSER-AUTH-AND-MEDIA-DISCOVERY.md` and architecture §6 for validated task copies, cleanup and import fallback.

## 4. Engine updates

- `youtubedl-android` exposes `updateYoutubeDL` with STABLE or NIGHTLY channels. [V, README]
- Desktop default is nightly, with stable/nightly/master selectable and a daily check. [V]
- Android additions: verify a checksum of the downloaded engine before swapping; keep the previous version for rollback; show versions in Settings;
  never update during an active download.
- An update replaces the yt-dlp *zip/script*, which the library's bundled Python executes. Native binaries (ffmpeg, aria2c, QuickJS) ship inside
  the APK (doc 06).

## 5. Player clients seen in current yt-dlp (PyPI page, release 2026.10.26)

web, web_safari, web_embedded, web_music, web_creator, mweb, ios, visionos, android, android_vr, tv, tv_downgraded, tv_samsung, tv_simply. [V]
Which of them needs a PO token changes; read the PO Token Guide table on the day you implement the client setting.

## 6. Things YouTube does that break CI and testing

- Datacenter IPs can be blocked while a phone hotspot works (one yt-dlp issue reports exactly this). [V anecdote] So a CI "canary" against
  YouTube from GitHub-hosted runners will be flaky. Run canaries from a device or self-hosted runner and keep them out of required checks.
