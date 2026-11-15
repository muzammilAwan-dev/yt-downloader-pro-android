# Foreground tester 6bb477b: failed phone milestone — 15 Nov 2026

Owner report: download/notification continued briefly after backgrounding and locking, then stopped after unlocking;
switching to another app without locking also stopped downloading. **Sustained background/lock-screen success is not verified.**

## Evidence

- Owner attachment: `session-1794754259148-000000000001.txt` in the owner's Downloads folder; all 922 lines inspected.
- SHA-256: `DA4FCA46773D8B564265BB1405B9D4834C0C81D22FC85FEF6971FD0FDBFE7A2F`.
- Header: `gitSha=6bb477b`, Infinix X682B, Android 10 / API 29, arm64, notification permission granted.
- Startup: `2026-11-15T14:45:37.332Z`; Share action: `14:50:59.143Z`; footer:
  `session_complete=true cutoff=2026-11-15T14:50:59.148Z` (lines 7–8 and 920–922).
- One startup-to-Share export was received. This supports the tested export path, not every native share target or system-event coverage.
- Three resolved/download attempts, all `299+140`, 1920×1080, 528.44 MiB video; no second preset, merge, publication or playback evidence.
- No raw attachment copied into the repository; this summary omits the user's source URL, title and signed media queries.

## Timeline (UTC; line references refer to the attachment)

| Task | Foreground promotion | Activity stop | Actual engine failure | Service destroyed |
| --- | --- | --- | --- | --- |
| `941d5726-68d4-4633-9b90-2d19ea455e6e` | 14:46:39.443 (44) | 14:46:45.052 (66) | Broken pipe at 14:46:54.783, then network unreachable and DNS errors; `NETWORK_OFFLINE` at 14:46:55.490 (114–215) | 14:46:56.511 (377) |
| `f511979d-b3fd-400c-a10f-d4cbfc51dc46` | 14:47:33.463 (406) | 14:47:44.789 (458) | 5.6% then HTTP 403 at 14:47:51.717; `HTTP_403_ON_MEDIA` at 14:47:52.029 (477–529) | 14:47:52.245 (644) |
| `f0e8d933-f435-4f28-ba46-a112be2fe69c` | 14:49:38.410 (673) | 14:49:47.831 (724) | 5.4% then HTTP 403 at 14:49:55.605; `HTTP_403_ON_MEDIA` at 14:49:55.942 (745–797) | 14:49:56.100 (913) |

Progress continued after Activity stop in every attempt. All three returned to the same process/session in FAILED, not CANCELED/PAUSED.
There is no recorded notification/user Cancel, foreground timeout, Activity destroy, session stop or new process startup in this export.
The first attempt exhausted ten video retries in roughly 0.1 seconds, then audio retries also failed; rapid retries did not ride out the outage.

## Code reconciliation and limits

- `MainActivity.onPause/onStop` log lifecycle only; they do not cancel work or release the session.
- `SharedSessionOwner` supplies an independent scope; `ForegroundAdmission` retains an owner and admits Engine work after promotion.
- `HomeController` maps Engine failure to FAILED, then closes the foreground lease in `finally`.
  The queued lease release invokes `DownloadService.ServiceHost.stop`, removes the foreground notification, and stops the service.
  The observed failure-before-destruction ordering agrees with this terminal release path; notification disappearance is not itself the cause.
- The export does not record notification demotion, OS service importance, connectivity changes, Data Saver, idle mode or OEM restrictions.
  Absence of such events is not proof that Android never restricted connectivity. HTTP 403 establishes an HTTP refusal, not why it was refused.
- Wi-Fi/mobile transport, VPN, network/IP changes and power/data settings are unknown. No same-video sustained foreground control was supplied.
  Network transitions/restrictions and site/engine media-access rejection remain hypotheses; engine age alone does not diagnose this failure.

## Next controlled phone check (same APK; no settings change initially)

1. Report Wi-Fi/mobile, any VPN, and whether the connection changed; note Data Saver/background-data/battery restrictions.
2. Find formats afresh for the same video, choose 1080p, and keep YTDLP visible for 60 seconds of downloading.
3. If still running, switch to another app for 30 seconds on the same connection. If it fails earlier, share immediately instead.
4. Return and Share session log without Clear. Do not send cookies, tokens or signed URLs.

No engine update, retry workaround, power exemption, new permission or new APK was implemented for this investigation.
Notification Cancel/cleanup, sustained screen-off download and new-file playback with sound remain open.
