# APK-bundled engine refresh investigation — 15 Nov 2026 (R72)

## Scope and result

Owner approved this investigation with **"ok go"** after the R70/R71 recommendation. Only a pinned, verified
APK-bundled engine candidate is approved: no runtime updater/code downloads, remote EJS components, cookies/login,
new permissions, battery exemption or unrelated feature work. Android 10 stays the phone baseline; minSdk stays 24.

**Packaging candidate identified, not integrated or phone-tested.** Keep Android library/ffmpeg **0.18.1** and its
existing native runtime; replace only its Python zipapp with official stable **yt-dlp 2026.10.26**, which includes
**EJS 0.8.0**. This is a controlled media-access test, not a proven fix for the phone's 403 or desktop's bot gate.

## Sources and integrity

- [Current library Maven metadata](https://repo.maven.apache.org/maven2/io/github/junkfood02/youtubedl-android/library/maven-metadata.xml)
  still reports latest/release **0.18.1**. The current upstream README uses the same coordinate/version; no dependency
  bump on that coordinate provides a fresher engine. This does not establish absence of every possible fork/artifact.
- [Official latest stable API](https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest) returned **2026.10.26**.
  Select its platform-independent `yt-dlp` zipimport asset, not a Linux glibc/musl executable or Windows EXE.
- Downloaded [the pinned asset](https://github.com/yt-dlp/yt-dlp/releases/download/2026.10.26/yt-dlp),
  `SHA2-256SUMS`, `SHA2-256SUMS.sig`, and the [tagged public key](https://github.com/yt-dlp/yt-dlp/blob/2026.10.26/public.key)
  into `C:\Users\Malku\AppData\Local\Temp\opencode\engine-2026.10.26\`; none is added to the app in this step.
- Asset size: **3,073,072 bytes**. Local SHA-256 matches both the release API digest and signed checksum entry:
  **`e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3`**.
- Git's bundled GPG, with an isolated temporary keyring, returned **Good signature / VALIDSIG**, exit 0, for
  `SHA2-256SUMS.sig` against `SHA2-256SUMS`. Signing-key fingerprint:
  **`AC0CBBE6848D6A873464AF4E57CF65933B5A7581`**. Trust is **undefined**: the public key is obtained from the same
  official repository, not independently authenticated out-of-band. First GPG attempt failed due to MSYS path syntax;
  correcting paths to `/c/Users/...` succeeded without changing the owner's keyring or installing software.

## Compatibility findings

- [Tagged Python metadata](https://github.com/yt-dlp/yt-dlp/blob/2026.10.26/pyproject.toml) requires **Python >=3.11**.
  Existing phone Python **3.12.11** meets this requirement. All **1,046** `.py` files in the downloaded zipapp parsed
  with Python's `ast.parse(feature_version=(3,12))`; this checks grammar, not Android import/runtime compatibility.
- Zip inspection found **1,051 entries**, lazy extractors, EJS **0.8.0** and both local minified solver scripts;
  no `.so`/`.pyd`/`.dll`/`.exe` entries. No new native package or on-device pip install is indicated by these findings.
  Solver script SHA3-512 values match the zipapp's own pinned vendor hashes (additional internal consistency check).
- [Tagged QuickJS runtime code](https://github.com/yt-dlp/yt-dlp/blob/2026.10.26/yt_dlp/utils/_jsruntime.py) requires
  QuickJS **2023-12-09** or newer; [provider code](https://github.com/yt-dlp/yt-dlp/blob/2026.10.26/yt_dlp/extractor/youtube/jsc/_builtin/quickjs.py)
  recommends **2025-04-26** or newer for performance. The phone's **2025-04-26** meets both. QuickJS still accepts
  an explicit executable path; its provider invokes `--script` on a task-temporary JS file and removes that file.
- [Tagged ffmpeg discovery](https://github.com/yt-dlp/yt-dlp/blob/2026.10.26/yt_dlp/postprocessor/ffmpeg.py)
  recognizes `libffmpeg.so` and discovers sibling `libffprobe.so`; the Android library's existing path remains usable
  according to source and a host path-discovery check. Native merge/playback has **not** been rerun.
- [EJS source selection](https://github.com/yt-dlp/yt-dlp/blob/2026.10.26/yt_dlp/extractor/youtube/jsc/_builtin/ejs.py)
  loads the bundled package first. GitHub script downloads require explicitly enabling `ejs:github`; tagged options
  default to no remote components. The integration must keep this default-deny policy and never call the updater.

## Offline host checks (not Android tests)

Ran the verified asset with the owner's **Windows CPython 3.14**, not the Android runtime:

- `python -I -X utf8 <asset> --ignore-config --no-plugin-dirs --no-remote-components --no-update --version`
  exited 0 and printed **2026.10.26**.
- Imported EJS from that zipapp: version **0.8.0**, resource loader returned byte-identical local core/lib scripts.
  Initial smoke assertion compared UTF-8 byte sizes to character counts; correcting that test-harness assumption passed.
- Created empty temporary `libffmpeg.so`/`libffprobe.so` placeholders: `FFmpegPostProcessor` selected both exact paths.
  No native binary was executed by this path-discovery check.
- Synthetic private info JSON with formats `137`/`140`: CLI `--load-info-json --list-formats` exited 0 and listed
  `137` at **1920x1080** and audio-only `140`. Socket connection functions were blocked for that CLI check;
  no live site/media request was made. This is only a replay format-list smoke, not download/evidence compatibility.

## Required smallest integration

The published [0.18.1 sources JAR](https://repo.maven.apache.org/maven2/io/github/junkfood02/youtubedl-android/library/0.18.1/library-0.18.1-sources.jar)
was inspected, including `YoutubeDL.kt` and `YoutubeDLUpdater.kt`. Installation uses
`noBackupFilesDir/youtubedl-android/yt-dlp/yt-dlp`; `init_ytdlp` copies `R.raw.ytdlp` **only if that file is absent**.
The public path constants are `baseName`, `ytdlpDirName`, `ytdlpBin`. An APK resource change alone leaves the old engine
on an existing install. `version(context)` reads updater preferences, not the binary, so it can be null/stale after a
local replacement; version reporting must identify the successfully installed pin and confirm runtime verbose output.

Next increment should:

1. Bundle the unmodified verified asset with a pinned digest/version and the applicable license texts. Choose an
   explicit app-owned resource/asset; verify the APK's actual bytes, rather than assume a library resource overlay.
2. Test a local-only installer first: clean install; replace old existing engine; matching hash no-op; corrupted bundle,
   interrupted copy and replacement failure preserve the old file but fail initialization; retry; concurrent init.
   Stage/flush/hash a sibling temporary file before atomic replacement. Do not recursively clear engine/native directories.
3. Install once under a **process-wide gate before library init/any engine execution**, preventing replacement while an
   engine task runs. Integrate only at adapter initialization; keep the runner, planner, replay and cancellation unchanged.
4. Keep complete session logging and report the actual candidate version. Rerun offline tests/lint, inspect packaged
   digest/native packaging/permissions and build a stamped APK. No fresh installer or Android compatibility is tested yet.
5. Owner then installs over `6bb477b` without clearing data, confirms new runtime versions, downloads the failing long video
   and earlier working sample on the same Wi-Fi with app visible, and shares each complete log plus playback/dimensions.
   Only after reliable visible transfers retry background/lock/Cancel. If 403 persists, isolate replay from direct execution;
   do not silently add authentication, client overrides, PO-token plugins or runtime updating.

Licenses: [yt-dlp](https://github.com/yt-dlp/yt-dlp/blob/2026.10.26/LICENSE) and
[EJS 0.8.0](https://github.com/yt-dlp/ejs/blob/0.8.0/LICENSE) are Unlicense; EJS bundles
**Meriyah 6.1.4 (ISC)** and **Astring 1.9.0 (MIT)** per its tagged `package.json` and the tagged yt-dlp
`THIRD_PARTY_LICENSES.txt` texts. Include those notices with the new asset, not the unrelated full PyInstaller dependency list.
Existing native source-offer/license release obligations remain open.
