# Third-party notices

This project is licensed under GPL-3.0 (see `LICENSE`). It depends on third-party components; the list below is a
work-in-progress stub. Items marked `TODO(verify)` have **not** been confirmed by reading the component's own license
files yet (tracked as item C.3 in `docs/10-VERIFICATION-LOG.md`).

| Component | Used for | License | Status |
|---|---|---|---|
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | the download engine (Python zipapp) | Unlicense (public domain) | Verified: official **2026.10.26** candidate ships unchanged at `app/src/main/assets/engine/yt-dlp-2026.10.26`, with signed SHA-256 verified (R72). Startup activation follows separately; `library` 0.18.1 still includes its original **2025.11.12** payload. Upstream LICENSE read 15 Nov 2026 |
| [yt-dlp-ejs](https://github.com/yt-dlp/ejs) | locally bundled JavaScript solver scripts | Unlicense (public domain) | **EJS 0.8.0** included in the candidate zipapp under `yt_dlp_ejs/yt/solver/`; both `core.min.js` and `lib.min.js` retain the upstream Unlicense headers |
| Meriyah 6.1.4 | EJS JavaScript parser | ISC | Complete copyright and ISC notice preserved inside the candidate zipapp's `yt_dlp_ejs/yt/solver/lib.min.js` header; offline packaging test verifies the full text |
| Astring 1.9.0 | EJS JavaScript code generator | MIT | Complete copyright and MIT notice preserved inside the same `lib.min.js` header; offline packaging test verifies the full text |
| [youtubedl-android](https://github.com/yausername/youtubedl-android) | Android packaging of yt-dlp + CPython, `library`/`ffmpeg` modules | GPL-3.0 (Maven Central POM) | Verified for `library` and `ffmpeg` **0.18.1** (pinned in `gradle/libs.versions.toml`) |
| CPython | Python runtime bundled by youtubedl-android | PSF-2.0 | **Version verified from the artifact: CPython 3.12** (`usr/lib/python3.12/`, `libpython3.12.so`). No license text ships inside the payload → include the PSF license in the release package (Phase 3) |
| FFmpeg (`ffmpeg` module) | merging video + audio, remuxing | **GPL build** — the payload contains `libx264.so.164` | Compatible with this app's GPL-3.0, but a **source offer for the exact ffmpeg build is required before release** (Phase 3). The payload has no LICENSE/COPYING file |
| aria2c (`aria2c` module) | optional multi-connection HTTP downloads | GPL-2.0-or-later | **Not shipped**: the `aria2c` artifact is deliberately not a dependency |
| QuickJS / QuickJS-ng | JavaScript runtime for yt-dlp's EJS support (`jni/*/libqjs.so`) | MIT | Present in `library` 0.18.1 as `libqjs.so`; owner's Android 10 runtime reports **QuickJS 2025-04-26** (R23/R25/R72), not the stale 2021-03-27 value previously recorded here. Include the MIT text in the release package (Phase 3) |
| AndroidX / Jetpack Compose / Room / WorkManager / DataStore | UI, storage, background work | Apache-2.0 | `TODO(verify)` via the generated license report (Phase 3) |
| Kotlin standard library | language runtime | Apache-2.0 | `TODO(verify)` |
| Owner's [yt-downloader-pro](https://github.com/muzammilAwan-dev/yt-downloader-pro) desktop icon | YTDLP Android launcher artwork | MIT, copyright (c) 2026 Muhammad Muzammil | Owner authorized reuse 15 Nov 2026; local LICENSE read. Original ICO's 256x256 PNG frame reused unchanged; full notice shipped in `app/src/main/assets/licenses/ytdlp-desktop-icon-MIT.txt` |

Also not bundled by this repository, but part of the product story: the desktop project
[yt-downloader-pro](https://github.com/muzammilAwan-dev/yt-downloader-pro) is MIT and separate; no source code is shared
between the two repositories. The owner's supplied launcher artwork is now shared with explicit permission and the MIT
notice above; no desktop source-code port is included in this branding change.

The approved engine-freshness spike is **APK-bundled only** (R72). Historical runtime-updater plans are not authorization
to download code on the phone. Candidate yt-dlp 2026.10.26/EJS 0.8.0 is now bundled, unchanged, with the notices above
embedded in its local solver scripts. It is not activated yet; Android runtime/download compatibility is untested.
Existing native release obligations remain open.

## Rules for contributors

- Never copy code from Snaptube, Seal, YTDLnis or NewPipe. Reading them to learn behaviour is fine; copying is not.
- Any new dependency needs its license added here in the same pull request.
- A release is blocked until every `TODO(verify)` that applies to a shipped artifact is resolved (Phase 3 exit criterion).
