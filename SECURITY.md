# Security policy

## Scope

This is an unofficial, free, open-source Android front end for [yt-dlp](https://github.com/yt-dlp/yt-dlp), licensed GPL-3.0.
It has no backend, no accounts, no ads, no analytics and no crash upload. Logs stay on the device unless the user
explicitly shares them.

In scope:

- the app itself (this repository), including its Gradle build and CI workflow;
- handling of cookies, PO tokens and signed URLs (must never be written to logs unredacted);
- the option allowlist in `contracts/allowed-options.json` (default-deny) and any path where user input reaches the
  yt-dlp command line;
- downloads written through MediaStore.

Out of scope:

- vulnerabilities in yt-dlp itself, in `youtubedl-android`, FFmpeg, aria2c, QuickJS or CPython — report those upstream;
- sites whose terms of service prohibit downloading. This project does not grant any right to content;
- devices the owner has already rooted or tampered with.

## Reporting a vulnerability

Please **do not** open a public issue for a security problem. Use GitHub's private "Report a vulnerability" flow on this
repository once a remote exists, or contact the maintainer directly. Include: app version, Android version, device,
reproduction steps, and the redacted section of the app log (Diagnostics → Share log).

Expect an acknowledgement within 7 days. There is no bug bounty.

## Supported versions

Only the latest release and the current `main` branch receive fixes. Test/debug APKs from phase tags are not supported.
