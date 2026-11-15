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

The first-batch pictures establish visual appearance, not whether a screen is native, WebView or another renderer. They do not show
the account-login flow, a browser address bar, bot-error prompt, format picker, active queue or notification. No measured
contrast/touch-target, TalkBack or large-text validation was performed.

### Second screenshot batch (now received and reviewed)

After the owner reattached `C:\Users\Malku\Downloads\screenshots 2.zip`, the archive was accessible. All five 720×1640
PNGs were visually reviewed in filename-timestamp order. Archive SHA-256:
`7f235ee3ad8c0028bdfc9ee2e7ddc179765c9723919d9c6ee75d5a00bb1fca6e`.
Temporary copies remain outside the repository; no screenshots, account identity, comment authors/text, watch history,
playlist contents or media titles are retained in these notes or committed.

| Image / original filename | Visible UI | Original ClipHarbor lesson / evidence limit |
|---|---|---|
| 01 / `Screenshot_20261115-120537.png` | Player with progress/full-screen controls and loading indicator; media title, prominent Download button, headphone and overflow controls, comments preview and related-media rows; app navigation below | Keep the identified media and download action together. Loading playback and successful extraction/download are different states; no completed playback or transfer proved here. Headphone icon alone does not establish its action. |
| 02 / `Screenshot_20261115-120543.png` | Comments panel beneath the player, drag handle, reaction/reply affordances and comment-entry field with account avatar | Secondary content can use a dismissible panel without replacing the media context. Comments/reactions are not Phase 1 requirements; account avatar is weaker authentication evidence than image 05. |
| 03 / `Screenshot_20261115-120624.png` | Close/overflow browser-shell controls, thin loading/progress line, blank content area, website-style destinations above app navigation | Website and app navigation are visibly separate. Do not infer a failure, duration or eventual load result from this still; future browser design needs explicit loading/error states and a visible origin. |
| 04 / `Screenshot_20261115-120634.png` | Sites list with icon/name/URL rows and history-like, delete and add toolbar icons; one shortcut visibly uses an `intent:` scheme | Source shortcuts are navigation aids, not download-support guarantees. Toolbar icon effects were not exercised. Do not inherit arbitrary intent launching; HTTPS validation and explicit external-action policy remain required. |
| 05 / `Screenshot_20261115-120652.png` | YouTube-branded account page inside close/overflow shell, account avatar/name, Switch account and Google Account controls, history/playlist sections; website destinations above app navigation | Strong visual evidence of a signed-in website-style account state inside the app shell, consistent with the owner's website-session observation. It does not show credential entry, redirects, cookie transfer, token generation or post-login download success. Keep site account controls distinct from ClipHarbor settings. |

This batch adds visual evidence of the browser shell, nested website navigation and authenticated account-page state.
The exact renderer/cookie handoff is supported separately by the static REA trace in §7, not deduced from pixels. Neither
batch shows the red bot-error prompt, login-entry sequence, format picker, active queue or foreground notification.
No website login was performed by the agent, browser feature implemented or Phase 1 scope expanded.

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
downloads. This describes the **initial retained-summary coverage**; the original-source follow-up in §7 now establishes
WebView-cookie access during extraction and separate real token WebViews. Do not copy proprietary extractor code,
scripts, endpoints, assets or branding. No 1DM analysis is necessary
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
`explore` follow-up inspected that directory's original reports (results in §7). The earlier absence finding applied only to the
previous search scope and is no longer a reason to request another export before checking this supplied path.

The second archive was initially absent at its supplied Downloads path. **Resolved after the owner reattached it:** all
five images have now been visually reviewed, with distinct observations and limits recorded above. The earlier absence
was a search-time result, not a continuing blocker.

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
API has limitations. The supplied original REA path then enabled the stronger trace in §7; no new 1DM analysis is required.

## 7. Original REA follow-up: session access really reaches extraction

Completed by `explore` at the owner's explicit request, after receiving `C:\Users\Malku\Documents\REA`. Relevant
original topic reports and focused decompiled call sites were read; no target executed or new binary analysis performed.
The parent independently re-read the cookie helper, extraction/authentication and bot branches, browser-to-picker action,
HTTP cookie adapter and a real token-WebView constructor. Only paraphrased observations are recorded here.

Original provenance: `C:\Users\Malku\Documents\REA\export\00-BRIEF.md:1–8` reports Snaptube 7.67.0, versionCode
76761610, static JADX 1.5.6/Apktool 3.0.3 analysis. Source files below are relative to this exact owner-supplied root:
`C:\Users\Malku\Documents\REA\reversed\jadx\sources`.

| Observed boundary/behaviour | Original source references | Qualification |
|---|---|---|
| Actual browser and website sign-in | `com/snaptube/premium/fragment/VideoWebViewFragment.java:1085–1086,2055–2066`; `com/snaptube/premium/views/VideoEnabledWebView.java:103–111`; `com/snaptube/dataadapter/plugin/login/YTWebViewSignInPluginImpl.java:65–81,117–125,157–168` | Real WebView and website navigation; not proof of a third-party OAuth integration. Non-incognito browser setup enables third-party cookies; do not blindly inherit this setting. |
| Cookie/session changes and continuations | `com/snaptube/dataadapter/plugin/login/YTLoginUtils.java:28–60`; `o/hsc.java:45–61`; `com/snaptube/premium/fragment/youtube/YouTubeLoginFragment.java:270–310,325–328` | Cookie-marker heuristic, flush and session-change event 1050. Login success may be reported despite later account-metadata failure; event is not server validation. |
| Browser action -> cached media or page URL -> picker/extraction | `com/snaptube/premium/fragment/VideoWebViewFragment.java:1276–1318`; `com/snaptube/plugin/extension/nonlifecycle/root/ChooseFormatViewModel.java:184–201,249–266`; `o/yy3.java:101–128`; `com/snaptube/premium/extractor/BaseVideoUrlExtractor.java:100–126` | Context contains source/flags/extras; explicit cookie serialization into that context was not established. |
| Shared browser cookies actually used by extraction | `o/nk4.java:65–82`; `com/snaptube/extractor/pluginlib/youtube/Parser.java:760–799,911–943,965–968,1611–1653` | Helper reads `CookieManager`; page requests use eligible cookies, supporting clients use cookie-derived account authorization. Adapt through our credential/Engine boundary, not by copying the private algorithm. |
| WebView <-> HTTP cookie adapter | `com/snaptube/dataadapter/plugin/YouTubeCookieJar.java:90–127,178–194,205–216` | Browser cookies are used for handled requests and response cookies written back. Fixed-domain/path reconstruction loses attributes; do not copy it. |
| Selected format -> persisted task -> worker | `o/qrc.java:150–181,293–330`; `o/vm2.java:109–134,182–196`; `o/hrc.java:408–465,542–570`; `o/vsb.java:171–201,372–393,773–783,805–831`; `o/otc.java:206–218` | Worker chooses requested format or applicable fallback. Media-to-format mapping itself does not prove account headers on all media requests. |
| Headers reach HTTP transfer, media-URL cookie fallback | `o/rm2.java:374–390`; `com/wandoujia/download/rpc/InnerDownloadRequest.java:190–204`; `com/wandoujia/download/rpc/f.java:801–838,923–925`; `com/wandoujia/download/rpc/SingleThreadBlockDownloadTask.java:745–748,799–818`; `com/wandoujia/download/rpc/c.java:371–374` | Supplied headers and byte ranges reach requests; Cookie fallback queries the download URL. Some headers are persisted there; ClipHarbor must keep credentials out of history and audit redirects independently. |
| Bot/age/other login-required classifications | `com/snaptube/extractor/pluginlib/youtube/Parser.java:228–269,1547–1567,1672–1682`; `com/snaptube/extractor/pluginlib/common/ExtractException.java:23–27`; `o/fxa.java:37–59`; `o/oe3.java:135–149`; `o/grc.java:30–56` | Distinct codes 101/102/103 and normalized login-required marker. Already-logged-in login-like errors need not yield the same sign-in recommendation. |
| Login-state checks and cache invalidation | `com/snaptube/premium/youtube/LoginStateChecker.java:108–149,176–196,206–268`; `o/suc.java:22–29,65–68` | NOT_LOGIN / LOGIN_INVALID / LOGIN, short cache and session-change invalidation; some optimistic failure handling, not infallible validation. |
| Branch-specific picker continuation | `com/snaptube/plugin/extension/nonlifecycle/youtube/YoutubeAllFormatFragment.java:780–806,980–995,1010–1052,1177–1198`; `com/snaptube/plugin/extension/nonlifecycle/youtube/YoutubeContentFragment.java:930–974,990–1015,1138–1169,1176–1189`; `com/snaptube/premium/controller/b.java:147–159,203–235` | Expired-login signal resumes **selected-format submission**, not a verified re-extraction refresh. Ordinary login-required dialog has no equivalent auto-retry proven in this trace. |
| Failed-task recovery and display | `o/ura.java:143–144`; `o/g17.java:87–105,164–193,227–267,324–367`; `o/i29.java:119–149`; `o/dta.java:85–115,205–255`; APK resources: `C:\Users\Malku\Documents\REA\reversed\apktool\res\values\strings.xml:2173–2174`, `colors.xml:97` in the same directory | Expired-login recovery can invoke base retry, resetting task to PENDING. Generic red/sign-in wording supported; exact owner bot-message parser-to-display chain not reproduced. |
| Real, separate PO-token WebViews and client/API routing | `com/snaptube/extractor/pluginlib/youtube/Parser.java:838–843,1018–1025,1036–1063`; `com/snaptube/extractor/pluginlib/youtube/potoken/PoTokenWebView.java:190–248,292–317,394–438`; `com/snaptube/extractor/pluginlib/youtube/potoken/BaseJsPatchPoTokenWebView.java:257–295,395–445` | Token generation is not inferred solely from class names anymore. Purpose/client-specific path; account login does not prove token generation or account binding. |
| Token lifecycle, caches and bounded failures | `com/snaptube/extractor/pluginlib/youtube/potoken/a.java:64–153,160–181`; `com/snaptube/extractor/pluginlib/youtube/potoken/b.java:128–182,188–267`; `o/ec8.java:16–44`; `o/lsc.java:21–28,31–75` | Failure cap of three, generator reuse, differing lifetime/age checks. Cache hits precede later renewal logic; generator expiry is not proven per-entry token expiry. JADX-damaged renewal branches limit exact predicates. |

Original topic cross-checks: `export/40-extractor-core-part01.md:9919,12709,13236`,
`export/40-extractor-core-part02.md:10192,10298–10309`, `export/40-shell-routing-part01.md:13927`,
`export/40-search-sites-part02.md:12654,12685` under `C:\Users\Malku\Documents\REA`. Native search interfaces are also
present; the browser trace must not be generalized to every search surface.

### Original ClipHarbor adaptation (proposed, unimplemented)

1. Consented website session with explicit login/logout/account-change state. Treat cookie changes as a signal, not proof.
2. URL-scoped, task-private credentials passed through a controlled Engine adapter; validate feature support/attributes.
3. Cache resolved metadata against source, extraction configuration and **session generation**. Invalidate stale state
   after a relevant verified session change; this generation scheme is our design, not a copied competitor mechanism.
4. Persist user intent (source, desired format/quality, destination, state); keep cookies/auth headers/signed snapshots out
   of Room/history/logs. Apply credentials only to allowed destinations and strip them on disallowed redirects.
5. Distinguish login required, expired session, bot/age gate, expired media URL and token failure. Register an operation's
   continuation before login, preserve cancellation, prevent duplicate submission and allow only bounded retry.
6. Keep token-provider purpose, identifier/session binding, expiry, failure bounds and cleanup separate from login.
   No copy of proprietary JavaScript, authorization algorithm, private endpoints or unsafe bridge/security settings.
7. Test session changes, cancelled login, stale caches, duplicate events, redirect scoping, snapshot cleanup, token expiry
   and redaction with fixtures/fake Engine. Real website login is an owner-operated compatibility check, not required CI.

**Resolved:** browser cookies feed authenticated extraction; real token WebViews exist. **Still unverified:** reliable
Google sign-in/download behaviour in our app, full redirect safety, account-bound token invalidation, decompiler-damaged
renewal logic and universal/post-login bot immunity. No browser feature implemented or added to the Phase 1 gate.
