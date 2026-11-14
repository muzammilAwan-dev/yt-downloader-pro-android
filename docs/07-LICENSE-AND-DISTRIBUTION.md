# 07. License and distribution

Not legal advice. Re-check with a lawyer or the FSF FAQ before 1.0.

## 1. License decision

- `youtubedl-android` (library, ffmpeg and aria2c modules, common) is **GPL-3.0** on Maven Central. [V]
- An app that links a GPL-3.0 library is, under the usual reading of the GPL, distributed under GPL-3.0 as a whole. [K]
- Seal and YTDLnis, both built on this library, are GPL-3.0. [V]
- **Recommendation:** Android repo = GPL-3.0 (`SPDX-License-Identifier: GPL-3.0-only` or `-or-later`; pick one deliberately). Desktop repo stays MIT.
  If you want MIT on Android, you would have to avoid this library and run yt-dlp, Python, ffmpeg and a JS runtime yourself, which is much more work.
- Add `THIRD_PARTY_NOTICES.md` generated from the dependency graph in CI. Check the licenses of the FFmpeg build (LGPL vs GPL depends on build flags),
  aria2c (GPL-2.0-or-later with a linking exception in some builds [K]) and QuickJS (MIT [K]). None of these were verified here.
- Naming: Seal and YTDLnis forbid reuse of their names for downloader apps. Do not use "Snaptube", "Seal" or "YTDLnis" anywhere.

## 2. Content and platform policy

- Google Play's policies reportedly prohibit apps that facilitate unauthorized downloading of streaming content; Seal and NewPipe are distributed outside Play. [S, press and
  community posts; I did not read Google's policy text]
- Keep the desktop legal notice: personal archiving and royalty-free content, no DRM circumvention, respect each site's terms, not affiliated with YouTube/Google/Meta/X/TikTok/Reddit.
- Do not add anything that bypasses DRM or paid content. The desktop README states private/paid/DRM content is unsupported; keep that true.

## 3. Channels

| Channel | Notes |
|---|---|
| GitHub Releases | Signed APKs + SHA-256 (the workflow writes `SHA256SUMS.txt`) |
| Own F-Droid repo | Hosted from GitHub Pages or releases; lets users update with your key |
| IzzyOnDroid | YTDLnis ships there; it lists permissions, library counts and a malware check per APK [V] |
| F-Droid main | Builds from source and re-signs with F-Droid's key unless reproducible builds are set up; Seal is listed [V]; bundled prebuilt binaries may need review (unknown) |
| Google Play | Not planned |

## 4. Android developer verification

- Google plans to require apps to be registered by identity-verified developers to install on certified devices. F-Droid's open letter (24 Feb 2026) opposes it and says
  an "advanced flow" for experienced users had not been shown. [V]
- A September 2026 article reports enforcement starting 30 September 2026 in Brazil, Indonesia, Singapore and Thailand, with a wider rollout planned for 2027, and notes that
  roughly 85% of F-Droid's catalog is signed with F-Droid's key (a forum estimate). [S]
- Consequence for you: sign with your **own long-lived key**, publish reproducible-build instructions (YTDLnis's workflow is a model [V]), and decide deliberately whether to
  register. F-Droid advises against registering; that is a personal and ethical choice. Read Google's current developer page before deciding; this package does not.

## 5. Release checklist

1. `LICENSE` (GPL-3.0), `THIRD_PARTY_NOTICES.md`, README legal notice.
2. Signed release APK per ABI, `SHA256SUMS.txt`, changelog (Keep a Changelog format, as Seal does).
3. fastlane metadata directory (F-Droid layout: `fastlane/metadata/android/en-US/`).
4. Weblate project for strings (both Seal and YTDLnis use Hosted Weblate [V]).
5. `SECURITY.md` with a reporting address.
6. Tag `vX.Y.Z`; manual workflow run builds the release artifact; attach to the GitHub release.
