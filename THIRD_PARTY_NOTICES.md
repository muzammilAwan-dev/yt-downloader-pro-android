# Third-party notices

This project is licensed under GPL-3.0 (see `LICENSE`). It depends on third-party components; the list below is a
work-in-progress stub. Items marked `TODO(verify)` have **not** been confirmed by reading the component's own license
files yet (tracked as item C.3 in `docs/10-VERIFICATION-LOG.md`).

| Component | Used for | License | Status |
|---|---|---|---|
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | the download engine (Python zipapp bundled inside youtubedl-android) | Unlicense (public domain) | Verified: the zipapp in 0.18.1 is **yt-dlp 2025.11.12** and carries `yt_dlp_ejs`; upstream LICENSE read 15 Nov 2026 |
| [youtubedl-android](https://github.com/yausername/youtubedl-android) | Android packaging of yt-dlp + CPython, `library`/`ffmpeg` modules | GPL-3.0 (Maven Central POM) | Verified for `library` and `ffmpeg` **0.18.1** (pinned in `gradle/libs.versions.toml`) |
| CPython | Python runtime bundled by youtubedl-android | PSF-2.0 | **Version verified from the artifact: CPython 3.12** (`usr/lib/python3.12/`, `libpython3.12.so`). No license text ships inside the payload → include the PSF license in the release package (Phase 3) |
| FFmpeg (`ffmpeg` module) | merging video + audio, remuxing | **GPL build** — the payload contains `libx264.so.164` | Compatible with this app's GPL-3.0, but a **source offer for the exact ffmpeg build is required before release** (Phase 3). The payload has no LICENSE/COPYING file |
| aria2c (`aria2c` module) | optional multi-connection HTTP downloads | GPL-2.0-or-later | **Not shipped**: the `aria2c` artifact is deliberately not a dependency |
| QuickJS / QuickJS-ng | JavaScript runtime for yt-dlp's EJS support (`jni/*/libqjs.so`) | MIT | Present in `library` 0.18.1 as `libqjs.so`, report line `quickjs-2021-03-27` (unversioned build); include the MIT text in the release package (Phase 3) |
| AndroidX / Jetpack Compose / Room / WorkManager / DataStore | UI, storage, background work | Apache-2.0 | `TODO(verify)` via the generated license report (Phase 3) |
| Kotlin standard library | language runtime | Apache-2.0 | `TODO(verify)` |

Also not bundled by this repository, but part of the product story: the desktop project
[yt-downloader-pro](https://github.com/muzammilAwan-dev/yt-downloader-pro) is MIT and separate; no source code is shared
between the two repositories.

## Rules for contributors

- Never copy code from Snaptube, Seal, YTDLnis or NewPipe. Reading them to learn behaviour is fine; copying is not.
- Any new dependency needs its license added here in the same pull request.
- A release is blocked until every `TODO(verify)` that applies to a shipped artifact is resolved (Phase 3 exit criterion).
