# 08. Desktop contract (behaviour to keep consistent)

Source: `muzammilAwan-dev/yt-downloader-pro` main branch README and release notes v6.4.0 / v6.4.9, read 14 Nov 2026. The source files themselves were not visible.
The mac/linux branch was not visible.

## Product

Windows-only: Manifest V3 Chrome extension + .NET 8 WPF host (`YTDLPHost`). MIT license, version 6.4.x, no telemetry. Repo layout: `extension/`, `host/`
(`Models`, `Services`: AppLogger, CommandValidator, HistoryManager, ProtocolHandler, SingleInstanceManager, TrayIconService, YtDlpRunner; `ViewModels`), `tools/`.

## Behaviours worth mirroring on Android

| Desktop behaviour | Android equivalent |
|---|---|
| Hand-off through `ytdlp://` with Base64 payload, command string + cookies + user agent | Share intent -> validated option set; cookies via temp file |
| `CommandValidator`: only allowlisted yt-dlp options, every other arg must be http(s), output must be a media extension in a normal folder, blocks `..`, network shares, system/startup/engine folders | `OptionValidator` + `contracts/allowed-options.json`; output only inside app-private dir |
| Single-instance via named pipe doorbell | Not needed (Android single activity); use `launchMode=singleTask` for the share receiver if needed |
| Queue with hot-swappable speed/thread settings; debounced JSON queue state survives crashes | Room-backed queue; settings changes apply to the next stage |
| Multi-connection 4x default, up to 8x | `-N`/aria2c, default lower for battery |
| Quality 360p to 4K; audio MP3/FLAC/WAV/M4A | Same list |
| Compatibility mode: force H.264/AAC | Same toggle |
| Timestamp cropper | `--download-sections` |
| Playlist ranges like `1-5, 8` | `--playlist-items` |
| Site toggles for YouTube, Instagram, Facebook, X, TikTok, Reddit, Twitch, Vimeo, SoundCloud | `contracts/sites.json` |
| Cookies only when "Bypass Age & Bot issue" is on (Vimeo always); analytics/consent cookies excluded; temp file deleted after | Same rule |
| Engine auto-update on launch and daily; channel stable/nightly/master; manual "Update engine" | Same, plus checksum and rollback |
| Settings override `YoutubePlayerClients` (hotfix `default,tv`) | Same field |
| Honest "only 360p offered" message on finished item, with log lines for PO Token/SABR | `FORMATS_WITHHELD` error |
| Log export shortcut (Ctrl+Shift+Y on the extension) | "Share log" button |
| Legal notice | Same text |

## Lessons the desktop releases already paid for

- v6.4.9: forcing `web_safari` + `mweb` caused a 360p cap because those clients need PO tokens the app can't supply. Letting yt-dlp choose clients fixed it.
- v6.4.9: login cookies were treated as signed-out because SAPISID and `__Secure-3PAPISID` were missing from the allowlist.
- v6.4.9: yt-dlp output must be decoded with the right code page (garbled characters in logs); on Android use UTF-8 explicitly.
- v6.4.0: host fetches yt-dlp, ffmpeg and deno on first launch. Android bundles the equivalents (QuickJS instead of Deno).

## Reconcile before coding

Open `host/YTDLPHost/Services/CommandValidator.cs` and `sites.js` in the desktop repo and diff them against `contracts/allowed-options.json` and `contracts/sites.json`.
