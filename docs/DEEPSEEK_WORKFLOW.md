# DeepSeek builds, Claude plans and polishes

Roles: **Claude** plans (ROADMAP, batch cuts, version checks), reviews and polishes. **DeepSeek** writes the code in small batches. **You** run four commands per batch. `tools/ds_bridge.py` does the file shuffling and refuses unsafe replies.

```
bundle  ->  upload + paste to DeepSeek  ->  copy reply  ->  apply  ->  verify  ->  commit  ->  (next batch)
                                                              |           |
                                                         blocked?     errors? -> bundle --fix
phase done -> review -> Claude reviews the diff -> fix batch -> commit
```

## 1. One-time setup (about 10 minutes)

1. Copy into the repo: `tools/ds_bridge.py`, `tools/ds/MASTER_PROMPT.txt`, `tools/ds.bat`, `docs/ROADMAP.md`, this file.
2. On a branch: `git checkout -b phase/R`, then `git add tools docs && git commit -m "chore: add ds bridge"`.
   `apply --write` refuses a dirty tree (so git can show and undo each batch). A changed `.gitignore` is ignored by that check.
3. Python 3.9+ is the only requirement. On Windows you can write `tools\ds` instead of `python tools\ds_bridge.py`.

## 2. Pilot (do this once, before real work)

This proves the copy-paste round trip is lossless in DeepSeek's web UI, which I could not test.

1. `python tools/ds_bridge.py bundle --name pilot --edit app/src/main/java/io/github/muzammilawan/clipharbor/AppInfo.kt --new docs/phases/pilot.md --task "Return AppInfo.kt UNCHANGED and create docs/phases/pilot.md with one line of text."`
2. Open a new DeepSeek chat, upload every file listed, paste `_ds/pilot/PASTE_THIS_MESSAGE.txt`, send.
3. Copy the reply with the message's **Copy** button (raw text, not a selection of the rendered text).
4. `python tools/ds_bridge.py apply --clip`. Expected: `AppInfo.kt` is `UNCHANGED` and the new file is `NEW`. If `AppInfo.kt` shows `MODIFIED`, the UI altered characters. Stop and tell Claude what changed.

## 2b. Intro batch and phase briefs
- **Intro (once):** `python tools/ds_bridge.py bundle --all --name intro`. Sends the code, contracts, build files and key docs in full (about 75k tokens; `--scope tests` adds tests, `--scope everything` sends all text, about 280k). No `--edit`, so DeepSeek only summarises. Do not `apply` it.
- **Phase brief (before each phase):** Claude researches current versions, Android behaviour and yt-dlp issues, saves `docs/phases/<id>-brief.md` and the batch commands. `bundle --phase <id>` adds it as `PHASE_BRIEF.txt` (or pass `--brief PATH`).
- The master prompt makes DeepSeek rank sources: uploaded files, then the brief and pinned versions, then its memory. It must use web search if available and list what it checked (`VERIFIED`), otherwise mark `TODO(verify)`.

## 3. The batch loop

```
python tools/ds_bridge.py bundle --phase 2B --batch 1/4 --edit <files> [--new <files>] [--ref <files>] \
        --task "<1-2 sentences>" --accept "<criterion>" "<criterion>"
```
- New DeepSeek chat per batch. Upload the listed files, paste `PASTE_THIS_MESSAGE.txt`, send.
- Copy the reply, then `python tools/ds_bridge.py apply --clip` (dry run). Read the table.
- If everything is `NEW`, `MODIFIED` or `UNCHANGED`: `apply --clip --write`.
- `python tools/ds_bridge.py verify` (gradle tests and lint). Green: `git add -A && git commit -m "feat(...): ..."`.
- Red: `python tools/ds_bridge.py bundle --fix --phase 2B --no-master` re-sends only the failing files plus the trimmed errors. Use `--no-master` only when you stay in the same chat; otherwise keep the master prompt.
- `apply` prints which files were not returned and a ready `bundle` command for the next batch.
- Wrong result: `python tools/ds_bridge.py undo`, or `git restore .`.

### How to cut batches
- Start with **3 files or fewer, and under about 600 lines of output**. If a reply has no `===END OF RESPONSE===` or a block lacks `===END FILE===`, it was cut off: halve the next batch. If replies are complete and fast, raise `--max-files`.
- Order by dependency: contracts and models, then engine and persistence, then controllers, then UI, with tests in their own batch or alongside the logic they cover.
- Send other files the model must call as `--ref` (full code) or let `API_DIGEST.txt` cover them. Never ask it to edit a file you did not send in full.
- Gradle and version-catalog edits are blocked unless you pass `--allow-build-files`. Claude checks library versions first, and you put them in the batch `--task`.

## 4. What the script enforces

| Guard | Effect |
|---|---|
| Path allowlist (`app/src`, `contracts`, `docs`, `tools`) | Anything else is blocked (workflows, wrapper, `AGENTS.md`, LICENSE, secrets) |
| Current-batch list | A file you did not request is blocked (`--allow-extra` to override) |
| End markers | A missing `===END FILE===` or `===END OF RESPONSE===` means "cut off": blocked or refused |
| Placeholder scan | Lines such as `// ... rest of file` are blocked |
| Syntax sanity | JSON, XML, TOML, Python parse; Kotlin/Java bracket balance (not a compiler) |
| Shrink check | A file of 40+ lines that loses over 40% of them is blocked (`--force-shrink`) |
| Clean tree + backup | `.ds_backup/<time>/` copies and `undo`; line endings of existing files preserved |
| Upload filter | Secrets, keystores, `local.properties`, binaries and clean-room folders are never bundled; secret-pattern scan on everything sent |

Tested by me on the uploaded repo snapshot (Linux): tree, bundle, digest, balance check over all 64 Kotlin files, apply, undo, error trimming and a lossless round trip. **Not tested:** Windows, the clipboard reader, a real gradle run, DeepSeek's UI.

## 5. Phase review with Claude

When a phase's batches are all committed:
`python tools/ds_bridge.py review` writes `_ds/review.diff.txt`. Upload it to Claude together with the phase section of `ROADMAP.md` and send:

> Review this diff against the exit gate of Phase <X>. Find: bugs, crashes, race conditions, lifecycle and cancellation errors, security or privacy problems (secrets, logging, path handling), missing tests, violations of AGENTS.md. For each finding give file, line, severity and a minimal fix. Do not rewrite working code. Then list UI/UX improvements separately.

Fixes from Claude go through the same `bundle` and `apply` loop, or Claude returns files in the same `===FILE:===` format.

## 6. Limits to keep in mind

- DeepSeek web chat reportedly accepts 50 files per session and 100 MB per file (secondary source, Sep 2026). The script uses a handful of files per batch and splits uploads above 120,000 characters.
- The API models are listed with 1M-token context windows, but that was on a third-party host. **The web UI's context and output limits are unverified**, so calibrate with the batch-size rule above.
- DeepSeek has no memory of your repo between chats and may hallucinate newer library APIs. That is why every batch carries current code, pinned versions and a signature digest.
- Everything you upload goes to a third-party service. The script keeps secrets and logs out, but check any file you add by hand.
- Do not upload decompiled Snaptube/1DM+ material. The upload filter blocks folders named like it, but the rule is yours to keep.

## 7. Troubleshooting

| Symptom | Fix |
|---|---|
| `no ===FILE: path=== blocks found` | You copied rendered text. Use the Copy button, or save the reply to a file and use `--in` |
| `not in the current batch` | Model returned an extra file. Ignore it, or `--allow-extra` if you want it |
| `syntax: unclosed '{'` | Truncated or damaged file. Ask for that file again in a smaller batch |
| Everything `MODIFIED` with whitespace-only diffs | Line-ending mix; run `git diff -w --stat` and ask Claude |
| gradle prints nothing useful | Open `_ds/build.log` |
