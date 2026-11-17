# DeepSeek quickstart (step by step)

**The idea in 3 lines:** a small script packs your code into text files for DeepSeek, then puts DeepSeek's answer back into your project. You copy and paste. The script does the rest.

---

## Part 1: Steps

### Step 0: Check you have two tools
Open a terminal (on Windows, PowerShell) and run:
```
git --version
python --version
```
Both should print a version number. If `python` is not found, try `py --version`. If that works, type `py` instead of `python` in every command below. If something is missing, install it from git-scm.com or python.org.

### Step 1: Put the Android project on your computer
```
git clone https://github.com/muzammilAwan-dev/yt-downloader-pro-android.git
cd yt-downloader-pro-android
```
Every command below runs inside this folder (the "project folder"). If you already cloned it, just open a terminal inside that folder. A folder downloaded as a plain zip will not work, because it has no git history.

### Step 2: Add the toolkit
1. Download `ds-toolkit.zip`.
2. Extract it into the project folder ("extract here" or "merge").
3. Check that these files exist:
   - `tools/ds_bridge.py`
   - `tools/ds/MASTER_PROMPT.txt`
   - `docs/ROADMAP.md`

### Step 3: Save the toolkit in git
```
git checkout -b phase/R
git add tools docs
git commit -m "add ds bridge"
```
If git says "Please tell me who you are", run these, then repeat the commit:
```
git config user.name "Your Name"
git config user.email "you@example.com"
```

### Step 4: Create the test package
Run as **one line**:
```
python tools/ds_bridge.py bundle --name pilot --edit app/src/main/java/io/github/muzammilawan/clipharbor/AppInfo.kt --new docs/phases/pilot.md --task "Return AppInfo.kt UNCHANGED and create docs/phases/pilot.md with one line of text."
```
It creates the folder `_ds/pilot` and prints a list of files to upload.

### Step 5: Send it to DeepSeek
1. Open a **new** DeepSeek chat.
2. Upload every file listed in Step 4's output (they are inside `_ds/pilot`, except `PASTE_THIS_MESSAGE.txt`).
3. Open `_ds/pilot/PASTE_THIS_MESSAGE.txt` in any editor, select all, copy it, paste it into the DeepSeek message box, and send.
4. Wait for the full reply.

### Step 6: Bring the reply back
1. Click the **Copy** button under DeepSeek's reply. Do not select the text by hand.
2. Run:
```
python tools/ds_bridge.py apply --clip
```
This is a dry run. It shows what would change and writes nothing.

### Step 7: Read the result
- **Good:** `AppInfo.kt` shows `UNCHANGED` and `docs/phases/pilot.md` shows `NEW`. The copy is lossless, so you can start real work.
- **Bad:** `AppInfo.kt` shows `MODIFIED` or `BLOCKED`. Copy the whole output and show it to Claude before doing anything else.

### Step 8: First real batch = the intro batch (once, at the start)
This teaches DeepSeek your whole project. It writes no code.
```
python tools/ds_bridge.py bundle --all --name intro
```
It sends all the app code, contracts, build files and key docs (about 75,000 tokens, in 3 text files). Upload the listed files, paste `PASTE_THIS_MESSAGE.txt`, and read DeepSeek's summary. If it misunderstands something, tell Claude before continuing. You do **not** run `apply` for this batch.

Other sizes: `--all --scope tests` adds the test files; `--all --scope everything` sends every text file (about 280,000 tokens, usually too much).

### Step 9: Before each phase, get a phase brief from Claude
Ask Claude: "Make the phase brief for Phase <id>." Claude researches the latest library versions, Android behaviour and yt-dlp issues, and saves `docs/phases/<id>-brief.md` plus the exact `bundle` commands for each batch. `bundle --phase <id>` then includes the brief automatically as `PHASE_BRIEF.txt`.

### Step 10: Real work (the loop for every batch)
1. `bundle`: pack the files for this batch (Claude gives you the exact command).
2. New DeepSeek chat, upload the listed files, paste `PASTE_THIS_MESSAGE.txt`, send.
3. Copy the reply, then `python tools/ds_bridge.py apply --clip` (dry run, read the table).
4. If the table looks fine: `python tools/ds_bridge.py apply --clip --write`.
5. `python tools/ds_bridge.py verify` (runs the project's tests).
6. Green: `git add -A` then `git commit -m "describe the change"`. Red: `python tools/ds_bridge.py bundle --fix --phase <id>` and repeat from step 2.
7. Something went wrong with a write: `python tools/ds_bridge.py undo`.
8. When a whole phase is done: `python tools/ds_bridge.py review`, then upload `_ds/review.diff.txt` to Claude for review.

---

### Does DeepSeek research before writing code?
Partly, and on purpose. The research happens **before each phase**, done by Claude in the phase brief, because Claude can search the web and show sources. The master prompt tells DeepSeek: trust the uploaded files, the brief and the pinned versions over its memory; if its chat has a web-search option, use it to check any API it is unsure about and list what it checked under VERIFIED; if not, mark `TODO(verify)` instead of pretending. It cannot be forced to research, so Claude's review is the safety net. Turning on DeepSeek's search and thinking options for code batches is worth trying, if your chat shows them.

---

## Part 2: Simple explanations

### What does `apply --clip` do?
It takes DeepSeek's reply from your clipboard (`--clip` means "read my clipboard") and puts the code into your project files. Without it you would copy each file out of the reply by hand and paste it over the right file, which is slow and easy to get wrong.

In order, it:
1. Reads the reply.
2. Splits it into files using the `===FILE: path===` markers.
3. Checks each file (complete? placeholders? brackets balanced? allowed file and folder?).
4. Prints a table: `NEW`, `MODIFIED`, `UNCHANGED` or `BLOCKED`.

Without `--write` it never changes anything. With `--write` it first backs up the old files, then saves the new ones.

### How does it know which file the code belongs to?
It does not guess. DeepSeek writes the path into the reply. The master prompt makes it format every file like this:
```
===FILE: app/src/main/java/.../AppInfo.kt===
(the full code of that file)
===END FILE===
```
The script reads the `===FILE: ...===` line as the path and writes the code between the two markers into that exact path.

The paths come from you, not from DeepSeek's imagination: the uploaded files contain their real paths, and the paste message lists them under "EDIT FILES". Also, when you run `bundle`, the script saves that list. During `apply`, any file not on the list is `BLOCKED`, so a wrong or invented path cannot be written.

If DeepSeek ignores the format, `apply` says "no ===FILE: path=== blocks found" and changes nothing.

### What do the table words mean?
| Word | Meaning |
|---|---|
| `NEW` | A file that does not exist yet will be created |
| `MODIFIED` | An existing file will be replaced with the new version |
| `UNCHANGED` | The file is identical to what you already have |
| `BLOCKED` | Something is wrong; the file will not be written (the reason is printed under it) |

### Why `BLOCKED` happens
| Reason shown | What it means | What to do |
|---|---|---|
| no `===END FILE===` / no end-of-reply marker | DeepSeek's reply was cut off | Ask for fewer files per batch |
| placeholder-like line | The file contains `// ... rest of code` | Ask DeepSeek again for the complete file |
| syntax: unclosed `{` | Brackets do not match, probably cut off | Ask again, smaller batch |
| not in the current batch | DeepSeek returned a file you did not ask for | Ignore it |
| unsafe path / protected path / outside allowed folders | Path tries to leave the project or touch protected files | Ignore it |
| file shrank | The new file is much shorter than the old one | Ask again, or check by hand |

### What does `--all` do?
It adds many repo files in full instead of only the ones you name. With no `--edit` or `--new` it makes an intro batch, where DeepSeek only reads and summarises. The default scope is the app code, contracts, build files and key docs. Secrets and binary files are always skipped.

### What does `bundle` do?
It prepares one batch for DeepSeek. In `_ds/<name>/` it creates:
- The files DeepSeek must edit, as `.txt` (the chat only accepts text files).
- A file tree, so DeepSeek knows the project layout.
- A list of the other files' declarations, so DeepSeek does not invent functions.
- The part of the roadmap for this phase.
- `PASTE_THIS_MESSAGE.txt`: the rules plus this batch's task. You paste this into the chat.

It also keeps secrets (keystores, `local.properties`) out of the upload.

### What does `verify` do?
It runs the project's tests and lint, like pressing the test button in Android Studio, and saves a short list of errors in `_ds/build_errors.txt`. If something fails, `bundle --fix` sends only the broken files plus those errors back to DeepSeek.

### What does `undo` do?
Restores the files changed by the last `apply --write` from the backup in `.ds_backup/`, and removes files that apply created.

### What is the `_ds` folder?
The script's work folder (batches, logs, backups). It is added to `.gitignore`, so it never goes into git. You can delete it anytime.

### Why a new DeepSeek chat for each batch?
DeepSeek does not remember your project and can drift in long chats. Each batch brings the current code, so a fresh chat is more accurate.

### Why only a few files per batch?
If DeepSeek has to write too much at once, the reply gets cut off or it starts shortening files. Start with 3 files or fewer. If a reply is cut off, use smaller batches.

### Why make a git branch and commits?
A commit is a save point. If a batch breaks something, git can show exactly what changed and take it back. `apply --write` refuses to run if you have unsaved changes, so every batch starts from a clean save point.

### Words used here
| Word | Plain meaning |
|---|---|
| Terminal | The window where you type commands |
| Repo / project folder | The folder `yt-downloader-pro-android` |
| Clone | Download a project with its full git history |
| Branch | A separate line of work (`phase/R`) so your main code stays safe |
| Commit | A save point in git |
| Clipboard | What you last copied |
| Dry run | A test run that shows results but changes nothing |
| Batch | One small group of files sent to DeepSeek in one chat |
| Phase | One big step of the roadmap |

### If something goes wrong
- `python` not found: use `py` instead.
- `no ===FILE: path=== blocks found`: you copied the rendered text. Use the **Copy** button under the reply, or save the reply to a file and run `apply --in thatfile.txt`.
- Not sure what a message means: copy it and show it to Claude.
