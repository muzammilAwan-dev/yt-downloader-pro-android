# 16. Browser authentication and media discovery

Research checked 15 Nov 2026 at the owner's request. **Research/proposal only:** no browser, login, cookie import or new
extractor implemented; no competitor app executed and no new binary analysis performed. Use original code and assets.

## 1. Evidence and its limits

### Owner's screenshots

Reviewed all nine original 720×1640 PNGs from `Screenshots.zip`, ordered by their filename timestamps. Archive SHA-256:
`626a4558b1db1ceb7e18a75a9633fcdae4aa1552a28cf63ab4252dabcb22daf0`. Originals and temporary image copies are not committed.
Do not retain the owner's personal search/feed/download contents in design notes.

| Image | Visible UI | Useful interaction principle |
|---|---|---|
| 01 (`114050`) | Dark home, top source tabs, prominent search/link field, three bottom destinations | Clear entry action and persistent navigation; avoid the large empty branded area in our compact downloader |
| 02 (`114058`) | YouTube-labelled feed, large thumbnails, duration labels, per-item download arrows | Offer a direct download action next to identified media |
| 03 (`114101`) | Music sections, compact artwork/title/metadata rows, download arrows | Compact media rows; audio and video may need different metadata presentation |
| 04 (`114104`) | Source-specific help cards with "How to start" actions | Contextual share/paste instructions, not an unsupported-site promise |
| 05 (`114107`) | Subscription-labelled feed, avatar strip, media cards and download arrows | Owner values browsing integrated with downloading; subscription syncing is not Phase 1 scope |
| 06 (`114113`) | Downloaded-file list with thumbnails, duration/size, overflow menus, search/filter/delete | Separate active queue from saved files; label actions and deletion scope clearly |
| 07 (`114119`) | Grouped settings, download/notification/theme entries, unrelated cleaner/vault tools | Group core settings; reject cleaner/booster/vault features |
| 08 (`114146`) | Search results with source/category tabs, mixed media rows and download arrows | Keep media selection close to its download action |
| 09 (`114152`) | Search field, recent/hot search chips and source shortcuts | Local, explicitly clearable history and source shortcuts; no remote trends needed for MVP |

The pictures establish visual appearance, not whether a screen is native, WebView or another renderer. They do not show
the account-login flow, a browser address bar, bot-error prompt, format picker, active queue or notification. No measured
contrast/touch-target, TalkBack or large-text validation was performed.

**Owner-reported runtime observation (15 Nov):** YouTube and its search run as a website in Snaptube's internal browser;
the owner can sign into YouTube there. After a bot-related download failure, a red message suggests signing into the
YouTube account. This is relevant firsthand evidence, not a claim independently reproduced by this agent. It refers to
**website session login**, not necessarily a third-party "Sign in with Google" OAuth integration. The owner's desktop
project likewise supplies cookies for some age/bot failures; that does not prove immunity from all bot checks.

### Existing REA notes

`docs/03-SNAPTUBE-REA-FINDINGS.md` and `docs/11-BRANDING-AND-UX.md` already report a browser/search shell, a plugin-shaped
extractor registry and remote extractor JavaScript, media URLs with headers/expiry, TS combining and M4S remux jobs.
`BadWebViewException` / `PoTokenException` names **suggest**, but do not prove, a WebView PO-token path.

Taken together, these findings are consistent with browser-assisted and custom extraction. They **do not establish**
whether yt-dlp is absent, the exact cookies-to-download handoff, a working 2026 token implementation, or universal/bot-free
downloads. Do not copy proprietary extractor code, scripts, endpoints, assets or branding. No 1DM analysis is necessary
to formulate the public-platform feasibility tests below; request additional evidence only for a concrete unresolved gap.

### `explore` source trace (completed, coverage-limited)

The owner-requested trace recovered these retained references:

- `03-SNAPTUBE-REA-FINDINGS.md` T5: distinct login-required, unauthorized and sign-in-expired extraction outcomes.
- T7: `EXTRAS_REQUIRED` exists; interpreting the extras specifically as cookies is still an inference.
- T6: format URL, headers and expiry fields exist; it does not establish cookie provenance or actual refresh branches.
- T1–T2: typed post-processing and bounded scheduling; T10: token-related exception names, not the generator's callers.
- `11-BRANDING-AND-UX.md` §3: browser/search/web-view/tabs reported; renderer/provider identity is not established.
- `10-VERIFICATION-LOG.md` evidence 15 identifies `50-deep-dive.md` in the original export.

The original export/topics/decompiled call sites were **not found within the searched scope**: the workspace recursively,
the approved `AppData/Local/Temp/opencode` directories, `Downloads/export.zip`, and direct Downloads children matching
`*export*.zip`. This is not proof the owner deleted it or that it exists nowhere else. A read-only REA `binary_session`
check also reported `open=false` and zero retained evidence records; no target was opened/closed or newly analyzed.

For an exact source trace, obtain the original export/path and inspect the unread topic reports plus narrowly relevant
call sites: browser/cookie-store ownership; login/session detection; download action -> extraction; cookies/headers at
both extraction and transfer; redirects; bot prompt -> login -> retry; and PO-token callers separately. The existing
architecture evidence and owner's runtime observation remain useful without that trace.

Evidence-backed flow to adapt, with the missing link explicit:

```text
Internal browsing/search -> optional website login             (owner observed)
                         -> session-to-extractor handoff        (not yet traced)
                         -> formats with URLs/headers/expiry    (reported static evidence)
                         -> download + typed merge/remux        (reported static evidence)
Authentication/bot failure -> suggest website login             (owner observed)
```

**Subsequent owner path correction:** the original REA is at `C:\Users\Malku\Documents\REA`; the owner-requested
`explore` follow-up is now inspecting that directory's original reports. The earlier absence finding applied only to the
previous search scope and is no longer a reason to request another export before checking this supplied path.

The owner also supplied `C:\Users\Malku\Downloads\screenshots 2.zip`. Its exact path was not present when read; a
case-insensitive direct-Downloads screenshot-ZIP search also returned no entries. **Second screenshot batch not reviewed**;
do not infer its contents from the first batch or count it as visual evidence.

## 2. yt-dlp is broader than social sites

[Supported sites](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md) and the
[FAQ](https://github.com/yt-dlp/yt-dlp/wiki/FAQ#can-you-add-support-for-this-anime-video-site-or-site-which-shows-movies-for-free)
explain that embed extraction and the generic extractor can handle sites outside the named list. Attempting extraction
is the reliable support check; a catalogue entry is neither a guarantee nor the engine's complete capabilities.

The [generic extractor source](https://github.com/yt-dlp/yt-dlp/blob/master/yt_dlp/extractor/generic.py) handles direct media
and includes HLS (`m3u8`) and DASH (`mpd`) extraction paths. A browser can help discover resources produced only after
JavaScript/player interaction, but the existing yt-dlp/FFmpeg pipeline may still download and merge them. **Browser
discovery and the download engine are separate concerns**; do not add a second heavy downloader before evidence requires it.

## 3. Login, cookies, PO tokens and bot checks are different

- **Website cookies:** authenticate an account/session and may resolve some login/bot failures. yt-dlp's
  [YouTube guidance](https://github.com/yt-dlp/yt-dlp/wiki/Extractors#exporting-youtube-cookies) warns of temporary/permanent
  account bans and frequent cookie rotation. Account use should be opt-in and only when needed; do not promise "no bot errors".
- **OAuth:** current [yt-dlp guidance](https://github.com/yt-dlp/yt-dlp/wiki/Extractors#logging-in-with-oauth) says YouTube
  OAuth login no longer works with yt-dlp; cookies are the suggested alternative. OAuth tokens are not browser cookies.
- **PO tokens:** the [PO Token Guide](https://github.com/yt-dlp/yt-dlp/wiki/Po-Token-Guide) describes attestation for GVS,
  player and subtitle requests. Requirements vary by client. Most relevant tokens are video-bound; platform/session
  bindings and expiry matter. Signing into a regular account does not supply every required token. The guide's Premium
  exemption applies to GVS PO tokens, not a general guarantee of authentication, download support or bot immunity.
- **EJS:** player JavaScript/signature solving is another subsystem, not interchangeable with cookies or PO tokens.
- **Rate/IP/session failures:** authentication can still fail or trigger limits. Use bounded retry/backoff and honest
  recovery messages; do not retry-loop challenges or encourage increasing concurrency as an authentication fix.

The guide recommends provider plugins, including a browser-based provider. Listing a public plugin is not proof it runs
inside our Android payload; dependencies, licenses and the no-remote-code rule need separate review. Raw wiki Markdown
URLs attempted for the PO guide returned 404; the linked GitHub wiki page was successfully read instead.

## 4. Website login is not automatically an OAuth flow

[Google's OAuth browser policy](https://developers.google.com/identity/protocols/oauth2/policies#browsers) prohibits
developer-controlled embedded agents **for Google OAuth authorization requests**. This must not be misquoted as proof that
every direct YouTube website login is OAuth, or that the owner's observation cannot occur. Direct website sign-in acceptance
in a particular WebView remains a separate compatibility/security question; it has not been tested in ClipHarbor.

The owner-requested `general` follow-up also found broader
[Google Account Help](https://support.google.com/accounts/answer/7675428?co=GENIE.Platform%3DAndroid&hl=en): Google may stop
sign-ins in browsers embedded in another application. That is direct website-sign-in guidance, not merely the OAuth rule.
An observed working login is useful evidence, but not a guaranteed, supported Google login path for every embedded browser.

| Environment | What it provides | What it does not automatically provide |
|---|---|---|
| App-owned WebView | App-controlled browsing, its own cookie store and potential media observation; cookie attributes via AndroidX when supported | Guaranteed Google website login support, Chrome's account session, complete network capture, or a universally supported cookie exporter |
| Custom Tab / external browser | Browser-provided browsing and its existing session, where supported | WebView cookie sharing, unrestricted cookie reading by ClipHarbor, or a general media-request interception API |
| User-selected cookie import (proposed fallback) | Explicitly supplied Netscape cookie data for a task | Automatic extraction from another Android app's private browser database; guaranteed fresh/accepted session |

[Custom Tabs documentation](https://developer.chrome.com/docs/android/custom-tabs/overview) explicitly contrasts browser
state sharing with WebView isolation. Do not claim that opening a logged-in Custom Tab authenticates yt-dlp.

[CookieManager.getCookie](https://developer.android.com/reference/android/webkit/CookieManager#getCookie(java.lang.String))
returns a `Cookie` request-header string (`name=value; ...`), **not** a full Netscape cookie jar with domain/path/expiry
attributes. Partitioned cookies also have context constraints. Converting that header into a global `.youtube.com` cookie
file by guessing scope is not a verified design. Website login and safe, correct cookie transfer both need proof.

**Important additional API:** [CookieManagerCompat.getCookieInfo](https://developer.android.com/reference/androidx/webkit/CookieManagerCompat),
added in AndroidX WebKit **1.6.0**, returns the cookies for a URL with their set attributes in `Set-Cookie` format. It
requires `WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)`; unsupported calls throw. Thus, incomplete
metadata from the legacy API is **not a universal Android limitation**. An app-owned, consented website session ->
URL-scoped attribute snapshot -> temporary yt-dlp cookie-file adapter is technically plausible when the feature and
session work. Validate host-only/domain/path defaults, expiry, duplicate names, HttpOnly and partitioned-cookie semantics;
do not broaden scope or flatten partitioned state. No WebKit dependency added or device feature support checked yet.

The [Chromium Custom Tabs security FAQ](https://chromium.googlesource.com/chromium/src/+/HEAD/docs/security/custom-tabs-faq.md)
explicitly excludes cookies, arbitrary script injection and network interception from embedding-app access. It also
warns that WebView is not intended as a general browser framework. A browser/discovery spike therefore needs a security
review, not simply a browser toolbar around an unrestricted WebView.

## 5. Optional browser-assisted media discovery (proposal, not MVP expansion)

```text
Share/paste page URL -> yt-dlp site/embed/generic extraction -> common picker/queue/save flow
                                 |
                      unsupported/dynamic page
                                 v
           opt-in browsing -> user plays authorized media -> detected candidates
                                 |
                    validated non-DRM media/manifest
                                 v
                     same picker/queue/download engine
```

Candidate handling should be bounded and task-private: validate HTTPS URLs and redirects, MIME/manifest contents rather
than filename suffix alone; deduplicate segments; identify audio/video variants; preserve only necessary origin-scoped
headers and consented session data. Never replay all captured headers or forward credentials to arbitrary redirected hosts.
Keep signed URLs, cookies and tokens out of Room/history/logs; clean task snapshots and cookie files on all terminal paths
and after process death. If a task loses its authorized session, ask for re-authentication instead of persisting secrets
indefinitely. A detected resource still needs validation and the user's explicit download action.

Platform limits:

- [shouldInterceptRequest](https://developer.android.com/reference/android/webkit/WebViewClient#shouldInterceptRequest(android.webkit.WebView,%20android.webkit.WebResourceRequest))
  is a request callback, not a complete response/network recorder. It omits `blob:`/`javascript:` requests and observes the
  initial resource URL, not all redirected URLs; runs off the UI thread. `WebResourceRequest` exposes URL/method/headers,
  but not a request-body API. Service-worker, worker and cross-origin paths require separate coverage tests.
- [Media Source Extensions](https://developer.mozilla.org/en-US/docs/Web/API/Media_Source_Extensions_API) assemble media
  chunks in browser-managed buffers. A `blob:` player source is not a portable downloadable file URL. Underlying manifest
  or chunk requests might be discoverable; that is not guaranteed by one callback.
- [Encrypted Media Extensions](https://developer.mozilla.org/en-US/docs/Web/API/Encrypted_Media_Extensions_API) support
  licensed DRM playback. Being playable does not make media exportable. No license/key extraction or DRM bypass is proposed.
- HTTPS-only, existing permissions budget, Safe Browsing, normal TLS validation and no privileged bridge on arbitrary
  pages. Android documents [native-bridge risks](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges).
  Do not bypass rejected login with user-agent spoofing or disable certificate checks to improve coverage.
- Before implementation, explicitly review how sandboxed website JavaScript fits the repo's no-remote-code policy.
  Downloaded extractor plugins/scripts or a privileged page-to-engine bridge remain forbidden; research is not permission
  to relax that rule.

## 6. Recommended next decision and proof

Keep Phase 1's approved share/paste, picker, durable queue, notification and diagnostics work moving. Keep the engine
boundary open to future discovery sources. Reconsider the earlier blanket "browser not planned" position as **optional
post-MVP research**, not a silently added Phase 1 requirement or a promised Google login implementation.

Future spike, after scope approval:

1. Test original HTTPS fixtures: direct MP4, HLS, DASH with separate audio, JavaScript-created player, redirect, extensionless
   media, unsupported/DRM-marked content; verify candidate detection and common download path. No DRM keys needed.
2. Test cookie scope, expiry, redirects, cancellation/process-death cleanup, redaction and no credential leakage using fake
   accounts/fixtures; check `GET_COOKIE_INFO` support and reject unsupported/ambiguous exports; log counts/stages only.
   Tests remain offline/fake-engine in required CI.
3. Owner manually tests any real website login on the device; no agent-entered passwords or captured account screenshots.
   Record renderer/WebView version, whether sign-in is accepted, authenticated page state, download outcome and cleanup—not
   cookies, tokens or personal account details. Compare anonymous vs explicitly authorized session on a small lawful sample.
4. If Google rejects embedded sign-in, respect the rejection; use browser browsing/share and investigate explicit cookie
   import instead. A browser login alone is not evidence the yt-dlp session is authenticated.
5. Browser and PO-token providers are separate spikes. Approve each only after Android feasibility/security evidence;
   do not copy a competitor implementation or claim all playable Internet videos can be downloaded.

`general` completed the direct-website-login research; the cookie-attribute API, broader Google sign-in guidance and
Chromium isolation findings above were independently re-read on their primary pages. Research only; no login attempt.

`explore` completed the retained-evidence trace and documented the missing original-source scope in §1. The engineering
goal is to adapt the working app's high-level browser/session/extraction/download flow, not dismiss it because a public
API has limitations, and not claim its exact internals were already proven. The original export/path is the next evidence
needed for that specific handoff, not a reason to demand 1DM analysis or halt unrelated Phase 1 work.
