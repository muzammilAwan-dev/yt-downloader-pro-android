# Tester 6bb477b: background and visible-app failures — 15 Nov 2026

Initial owner report: download/notification continued briefly after backgrounding and locking, then stopped after unlocking;
switching to another app without locking also stopped downloading. Follow-up report/log confirms two downloads also failed
with YTDLP continuously visible on reportedly stable Wi-Fi. **Backgrounding is not necessary for the media HTTP 403 failure;
sustained background/lock-screen success is still not verified.**

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
- At the first investigation (R69), transport/settings and a sustained visible-app control were unknown. The follow-up below
  answers Wi-Fi and demonstrates visible-app failure; it does not retrospectively explain the first NETWORK_OFFLINE attempt.

## Follow-up visible-app control (R70; pasted owner log, not an attachment)

- Owner reports Wi-Fi without disconnection and other apps retaining working internet. No battery/data-setting cause is established.
- Header identifies the same `6bb477b` / Infinix X682B / Android 10. Session start `2026-11-15T15:09:22.765Z`;
  footer `session_complete=true cutoff=2026-11-15T15:15:17.099Z`. This is a supplied complete-log claim, not an independently
  hashed file: no original attachment bytes/hash or line numbers are available for this paste.
- Both attempts are a different video from the first attachment, freshly resolved per task, selecting `299+140`, 1920×1080,
  with 873.48 MiB video. Each stops at the last displayed 3.4% before HTTP 403; no merge, publication or playback follows.

| Task | Resolve complete | Download tap / promotion | First HTTP 403 / mapped failure | Service destroyed |
| --- | --- | --- | --- | --- |
| `220ec6b9-bbf3-49e3-8dd1-606e5fda3a73` | 15:10:25.458 | 15:11:49.554 / 15:11:49.688 | 15:12:09.460 / 15:12:09.821 | 15:12:10.132 |
| `8e22191b-4004-4215-9bf2-1809559a95e2` | 15:12:59.769 | 15:13:46.191 / 15:13:46.274 | 15:14:05.556 / 15:14:05.978 | 15:14:06.322 |

No Activity pause/stop occurs during either download. The next pause is 15:15:03.778, already FAILED. No recorded Cancel,
timeout, Activity destruction or restart; both Engine failures again precede service destruction. Resolve-to-tap delays
are about 84 and 46 seconds, within the app's five-minute snapshot allowance; this is not proof of server URL validity.
Manually finding formats afresh did not prevent the second failure, so simply repeating that advice is not a demonstrated fix.

## Regression comparison and byte-boundary lead

- `git diff 74ce7cf 6bb477b` is empty for the engine directory, `ResolvedSnapshot.kt` (including `ReplayInfo`),
  `FormatPlanner.kt`, `VideoQualities.kt`, `Engine.kt` and `gradle/libs.versions.toml`.
  Download argv/replay policy/runner/cancellation adapter, planner and engine version did not change in that interval.
- `ec146df` → `74ce7cf` changed the typed quality selector/output name and selection validation in `DownloadExecutor`, not
  HTTP transport/chunk options or replay. Home later added admission/session/notification/action logging; source comparison
  has not identified a regression, but unchanged engine files do not rule out every interaction with those changes.
- Replay removes derived top-level selection/transport state, not nested catalog headers/options. No raw metadata was supplied
  to verify the selected formats' actual headers, client identity or signed-URL validity.
- Rounded progress estimates: 873.48 × 3.4% ≈ 29.70 MiB (both new attempts); 528.44 × 5.6% ≈ 29.59 MiB and
  528.44 × 5.4% ≈ 28.54 MiB (earlier 403s). These are estimates, not exact downloaded-byte counters.
- Official [2025.11.12 extractor](https://github.com/yt-dlp/yt-dlp/blob/2025.11.12/yt_dlp/extractor/youtube/_video.py#L3129)
  supplies per-format `http_chunk_size = 10 << 20` (10 MiB). Its
  [HTTP downloader](https://github.com/yt-dlp/yt-dlp/blob/2025.11.12/yt_dlp/downloader/http.py) uses sequential bounded Range
  requests randomized to 95–100% of that size, reconnects after a chunk, and does not retry HTTP 403 in its ordinary retry loop.
  Three such chunks total 28.5–30 MiB, consistent with these stops and the `establish_connection` traceback, not conclusive proof.
  `-N 4` controls fragment concurrency, not these direct HTTP range requests; changing it is not a justified fix here.
- Earlier success remains valid: `ec146df` playback with sound and `74ce7cf`'s 7,737,267-byte portrait MP4 save are confirmed.
  The latter finished below one 10 MiB chunk; success on that sample does not establish larger transfers. No failure is inferred
  for earlier unmeasured sizes or for every video using `299+140`.
- Later initialization/debug lines confirm QuickJS and ffmpeg 7.1.1; startup header `missing` values are pre-init snapshots,
  not evidence that the runtime/merger failed. A PO-token experiment message is not proof of the selected stream's token requirement.

## Upstream evidence and next decision

- Official [issue #15712](https://github.com/yt-dlp/yt-dlp/issues/15712) documents `android_sdkless` downloads starting then
  returning HTTP 403. Maintainer [confirmation](https://github.com/yt-dlp/yt-dlp/issues/15712#issuecomment-3819129364) says fixed
  in 2026.01.29; merged [PR #15726](https://github.com/yt-dlp/yt-dlp/pull/15726) changed default clients. Our 2025.11.12 defaults
  still include `android_sdkless`. This is a relevant engine/site failure mechanism, not a proven diagnosis of these redacted streams.
- Separately, [issue #17456](https://github.com/yt-dlp/yt-dlp/issues/17456#issuecomment-5354789281) records a later `android_vr`
  HTTP 403 problem fixed in 2026.08.19; it is not the same old default client. Avoid hard-coding an obsolete workaround.
- Rechecked [latest stable release](https://github.com/yt-dlp/yt-dlp/releases/tag/2026.10.26): 2026.10.26. Its extractor defaults
  are `tv_samsung`, `visionos`, `web`, not the old client set. Neither that release nor an Android packaging change was tested here.
- The R69 visible-60s/background-30s recommendation has been answered by failure before 60s with the app visible.
  Prioritize reliable visible-app media access before repeating lock-screen testing; do not prescribe power exemptions as a 403 fix.
- **Owner decision needed:** permit a narrow engine-freshness investigation for a pinned, verified APK-bundled payload, preserving
  Android 10/minSdk 24, existing engine interface/planner/icon and cancellation/replay tests. Check packaging, Python/EJS/QuickJS
  compatibility and offline integrity before a meaningful stamped tester; compare the failing long video and earlier successful
  sample on the same phone/Wi-Fi. A newer engine is a test candidate, not a promised fix. No app-side regression has been established.
  This does not authorize an in-app updater, runtime component downloads, cookies/login/PO-token providers or new permissions.

No engine update, retry workaround, power exemption, new permission or new APK was implemented for this investigation.
Notification Cancel/cleanup, sustained screen-off download and new-file playback with sound remain open.
