# ADR 0001 — License the Android app under GPL-3.0

- Status: accepted
- Date: 2026-11-14
- Deciders: project owner
- Related: `docs/07-LICENSE-AND-DISTRIBUTION.md`, `docs/10-VERIFICATION-LOG.md` (fact 4), `LICENSE`, `THIRD_PARTY_NOTICES.md`

## Context

The desktop project (`yt-downloader-pro`) is MIT. The Android app must run yt-dlp on the device. The practical option is
`youtubedl-android` (`com.github.yausername.youtubedl-android`), which packages yt-dlp, CPython, FFmpeg and aria2c as
Android artifacts.

The `youtubedl-android` POM on Maven Central declares **GPL-3.0** (checked 14 Nov 2026, `docs/RESEARCH-LOG.md` R-items and
`docs/10-VERIFICATION-LOG.md`). Linking a GPL-3.0 library into an app makes the combined work subject to the GPL when it
is distributed. This is the usual reading of the GPL, not legal advice.

## Decision

1. This repository is licensed **GPL-3.0-only**. The verbatim license text is in `LICENSE`.
2. MIT is not an option for this repository while it links `youtubedl-android`; the desktop repository stays MIT and
   separate.
3. Every shipped binary must be accompanied by the corresponding source (this repository plus the upstream components'
   sources, per `THIRD_PARTY_NOTICES.md`).
4. Contributions are accepted under GPL-3.0-only (inbound = outbound). No CLA.
5. Distribution channels are GitHub Releases, an own F-Droid repository, IzzyOnDroid, then F-Droid main. Google Play is
   deliberately not targeted.

## Consequences

- The app can be forked and redistributed, including commercially, as long as the GPL is honoured (source offer, notices,
  no additional restrictions).
- We must not add any dependency whose license is incompatible with GPL-3.0 distribution (for example proprietary SDKs,
  ad or analytics libraries).
- App-store metadata and the About screen must link to the source for the exact version installed.
- The GPL text is the FSF text fetched from the SPDX license-list-data mirror (`GPL-3.0-only.txt`) on 2026-11-14 because
  gnu.org was unreachable from the build machine; the content is the verbatim FSF license, SHA-256
  `FB981668C18A279E285FC4D83FBA1E836CC84DD4DAA73C9697D3CFD2D8ACA6E0`.

## Alternatives considered

| Option | Why rejected |
|---|---|
| MIT like the desktop app | Incompatible with linking a GPL-3.0 engine. |
| Apache-2.0 | Same incompatibility; also adds patent-clause complexity for no benefit. |
| Replace `youtubedl-android` with a private/closed engine | Not free, not auditable, and would break the project's premise. |
