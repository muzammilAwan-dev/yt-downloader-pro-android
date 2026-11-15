# 01. Updated plan (supersedes the earlier Claude Doc)

Date: 14 Nov 2026. Confidence tags: [V] verified from a source this session, [S] secondary source, [K] general knowledge not
re-verified, [I] my inference. Sources are in `10-VERIFICATION-LOG.md`.

## 1. What changed since the first plan

| Earlier statement | Now |
|---|---|
| "Unknown whether youtubedl-android supports a JS runtime" | `youtubedl-android` 0.18.1 (Maven Central, 16 Nov 2025) adds QuickJS support and passes the runtime to yt-dlp, per Seal PR #2659. [V for date, S for the QuickJS claim: it is the PR author's description]. Still must be proven on a device. |
| "Check the youtubedl-android license" | It is **GPL-3.0** (Maven Central POM). [V] So an app that links it is a GPL-3.0 work under the usual reading of the GPL. [K, not legal advice] |
| "Repo license: MIT like desktop" | Android repo should be **GPL-3.0**. Desktop stays MIT. |
| Prior art: Seal only | Add **YTDLnis** (GPL-3.0, MVVM + WorkManager, share-sheet card, intents API, cookies, cuts, scheduling). [V] |
| PO token: "design around it" | Public precedent exists: NewPipe generates PO tokens with a WebView (PR #11955); yt-dlp has a PO Token Provider plugin framework. [V] Treat as Phase 2+ research, not MVP. |

## 2. Decisions

1. **Separate repo**, `yt-downloader-pro-android`, GPL-3.0. Reasons: different toolchain, release cadence, signing key, F-Droid
   build-from-tag flow, contributor clarity. [I]
2. **Native Kotlin + Compose**, engine = `youtubedl-android` (library, ffmpeg, aria2c modules) behind an `Engine` interface.
3. **Distribution:** GitHub Releases + own F-Droid repo + IzzyOnDroid, then F-Droid main. Not Google Play. [S]
4. **No ads, no analytics, no remote code.** Local logs only, user-initiated share.
5. **Staged durable queue** (Room) as the core design; UI and notifications derived from it.

## 3. Scope

| Feature | Desktop today | Android |
|---|---|---|
| Entry | Chrome button + `ytdlp://` | Share target, paste, optional clipboard prompt when app is open |
| Formats | 360p-4K, MP3/FLAC/WAV/M4A | Same, picker built from `--dump-json` |
| Queue | IDM-style concurrent, hot-swap speed | Persisted queue, concurrency 1-3, pause/resume/retry |
| Speed | 4x/8x connections | `-N` fragments or aria2c, battery-aware default |
| Trim, playlists, subs | yes | Phase 2 |
| Login cookies | extension | Research app-owned website session -> validated task-private cookie adapter; explicit import fallback. No guaranteed embedded Google sign-in; doc 16 |
| Site toggles | `sites.js` | `contracts/sites.json` |
| Telemetry | none | none |

## 4. Phases and gates

| Phase | Goal | Exit criteria |
|---|---|---|
| 0 Spike (about 1 week) | Prove the engine on a phone | arm64 device: YouTube URL resolves with JS runtime, 1080p+audio downloads, merges, saved via MediaStore. Record yt-dlp version and formats list. **Go/no-go.** |
| 1 MVP (3-4 weeks) | Share to saved file | Share target, picker, Room queue, notification, errors, `onTimeout` handling |
| 2 Parity (about 3 weeks) | Match desktop | Playlists, trim, audio, subs, cookie login, site toggles, speed, engine update with checksum + rollback |
| 3 Release (about 3 weeks) | 1.0 | Baseline Profiles, a11y, Weblate strings, signing, F-Droid/Izzy metadata |
| 4 Later | Extras | Library view, Media3 preview, tablet layout, PO-token provider research |

Time estimates are rough guesses for one part-time developer. [I]

## 5. Size, speed, battery: what the data says

- `youtubedl-android` library AAR 0.16.0 was 39.1 MB on Maven. [V] YTDLnis's arm64-v8a APK was 36.1 MB on IzzyOnDroid
  (v1.7.9.2, Sept 2024; dated). [V] Expect an APK in the tens of MB. Ship arm64-first splits; set a budget after Phase 0.
- Snaptube's own APK is 34.9 MB with 28,898 classes and 36 native libs (export). [V] Size is not a differentiator.
- Defaults: 1-2 concurrent tasks, aria2c or `-N 4` opt-in, Wi-Fi-only as a setting. Measure battery in Phase 3. [I]

## 6. Risks (ranked)

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| YouTube extraction breaks on Android (JS runtime, PO token, Python version) | High | Core feature | Phase 0 gate; `Engine` interface; nightly engine updates; clear error UI |
| HD formats withheld (PO token/SABR) | High | 360p results | Let yt-dlp choose clients; distinguish cookies from PO tokens; honest messaging; Phase 2+ provider research (doc 16) |
| License mistakes | Medium | Forced relicensing | GPL-3.0 from day one; THIRD_PARTY_NOTICES; no copied code |
| Android developer verification limits installs | Medium | Distribution | Own signing key; decide on registration deliberately; multiple channels |
| FGS limits kill long downloads | Medium | Lost progress | Persisted queue + `onTimeout` pause/resume |
| Library maintainers stall | Medium | Engine stuck | `Engine` interface; fork plan |

## 7. Definition of done for 1.0

All Phase 2 features work on at least two real devices (one Android 15+); unit + instrumented tests green in CI; signed release with
published SHA-256; license report clean; README states legal notice and known limitations; Phase 0 evidence file committed
(`docs/evidence/phase0.md`).

## 8. Branding and positioning

Main identity is "the yt-dlp Android app / alternative", unofficial and powered by yt-dlp; Snaptube is a UX inspiration only. Naming rules, working names (unchecked), package-id
advice and the Snaptube-inspired UX table are in `11-BRANDING-AND-UX.md`.

## 9. Research-first rule for whoever builds this

Before writing code, the builder must re-check the moving parts (library versions, yt-dlp and EJS status, Android behaviour changes for the current API level, F-Droid and Google policy
pages) and log what it found in `docs/RESEARCH-LOG.md`. The checklist is in `CLAUDE_PROMPT.md` and `AGENT_PROMPT_OPENCODE.md`. Anything in this package marked [S], [I] or "not verified"
is a lead, not a fact.

## 10. Automated tests, per-phase APKs, deep logging

Every phase ships with automated tests (`./gradlew testDebugUnitTest lintDebug`, fake engine), a debug APK from the manual CI run, an in-app self-test and a shareable redacted log.
Manual testing is limited to what automation cannot prove. Details: `12-AUTOMATED-TESTS-AND-DEEP-LOGGING.md`.
