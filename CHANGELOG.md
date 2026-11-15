# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html) once it reaches 1.0.0.

## [Unreleased]

### Added

- Hand-off documentation package (`docs/`, `contracts/`, `tools/`, `AGENTS.md`).
- Build environment notes and a local git repository (no remote yet).
- GPL-3.0 license text.
- Phase 0 engine spike: youtubedl-android 0.18.1 (yt-dlp + QuickJS + ffmpeg) behind an `Engine` interface.
- Diagnostics screen: 7-check self-test, 1080p video + audio download with merge, MediaStore publish to
  `Download/ClipHarbor`, Share log / Copy summary / Clear logs, verbose switch.
- Rotating, redacted diagnostic log in app storage plus a debug-only public copy in `Download/ClipHarbor-logs/`.
- JVM tests (fake engine, format planner, error mapping, redactor, log rotation, self-test) — 46 tests.

### Changed

- App renamed from the placeholder "yt-dlp for Android" to **ClipHarbor**; package id
  `io.github.muzammilawan.clipharbor` (owner's choice, docs/11 §2).
- APK is `arm64-v8a` only and uses legacy native-lib packaging (required by the engine).

<!--
Sections to use per release: Added, Changed, Deprecated, Removed, Fixed, Security.
Write entries in the imperative, group them by user-visible effect, and link the phase tag (e.g. phase0-rc1).
-->
