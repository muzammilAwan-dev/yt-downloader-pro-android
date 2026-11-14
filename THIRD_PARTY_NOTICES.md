# Third-party notices

This project is licensed under GPL-3.0 (see `LICENSE`). It depends on third-party components; the list below is a
work-in-progress stub. Items marked `TODO(verify)` have **not** been confirmed by reading the component's own license
files yet (tracked as item C.3 in `docs/10-VERIFICATION-LOG.md`).

| Component | Used for | License | Status |
|---|---|---|---|
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | the download engine (invoked as a Python module) | Unlicense (public domain) | `TODO(verify)` against the bundled copy |
| [youtubedl-android](https://github.com/yausername/youtubedl-android) | Android packaging of yt-dlp + CPython, `library`/`ffmpeg`/`aria2c` modules | GPL-3.0 (Maven Central POM) | Verified for the library artifact; the exact version is pinned in `gradle/libs.versions.toml` |
| CPython | Python runtime bundled by youtubedl-android | PSF-2.0 | `TODO(verify)` which build ships in the library |
| FFmpeg (`ffmpeg` module) | merging video + audio, remuxing | LGPL/GPL depending on build flags | `TODO(verify)` — must confirm the build is LGPL-compatible or ship the corresponding source offer |
| aria2c (`aria2c` module) | optional multi-connection HTTP downloads | GPL-2.0-or-later | `TODO(verify)` version and build |
| QuickJS / QuickJS-ng | JavaScript runtime for yt-dlp's EJS support (youtubedl-android 0.18+) | MIT | `TODO(verify)` which build the library bundles |
| AndroidX / Jetpack Compose / Room / WorkManager / DataStore | UI, storage, background work | Apache-2.0 | `TODO(verify)` via the generated license report (Phase 3) |
| Kotlin standard library | language runtime | Apache-2.0 | `TODO(verify)` |

Also not bundled by this repository, but part of the product story: the desktop project
[yt-downloader-pro](https://github.com/muzammilAwan-dev/yt-downloader-pro) is MIT and separate; no source code is shared
between the two repositories.

## Rules for contributors

- Never copy code from Snaptube, Seal, YTDLnis or NewPipe. Reading them to learn behaviour is fine; copying is not.
- Any new dependency needs its license added here in the same pull request.
- A release is blocked until every `TODO(verify)` that applies to a shipped artifact is resolved (Phase 3 exit criterion).
