#!/usr/bin/env python3
"""ds_bridge.py - move code between this repo and a text-only chat UI (DeepSeek web).

Commands (run from anywhere; the repo root is the parent of the folder holding this script):
  tree                      write _ds/TREE.txt (file tree with line counts)
  bundle --edit F... [...]  build one batch: .txt uploads + a paste-ready message in _ds/<name>/
  apply  [--clip|--in F]    parse the model's reply and write the files (dry run unless --write)
  verify                    run gradle tests + lint, keep a trimmed error list for the next fix batch
  review                    write _ds/review.diff (git diff + new files) to send to a reviewer model
  undo                      restore the files changed by the last `apply --write`

Only the Python standard library is used. Windows, macOS and Linux.
"""
from __future__ import annotations

import argparse
import ast
import datetime as dt
import difflib
import fnmatch
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# ---------------------------------------------------------------- configuration
TEXT_EXT = {".kt", ".kts", ".java", ".xml", ".json", ".jsonc", ".md", ".toml", ".properties",
            ".txt", ".yml", ".yaml", ".pro", ".ps1", ".sh", ".py", ".gradle", ".bat", ".cfg"}
EXCLUDE_DIRS = {".git", ".gradle", "build", "dist", "_ds", ".ds_backup", ".idea", ".kotlin",
                "__pycache__", "node_modules", ".opencode"}
# Never leave the machine: secrets and clean-room material (decompiled third-party code).
NEVER_NAMES = {"keystore.properties", "local.properties", "opencode.jsonc", ".env"}
NEVER_SUFFIX = (".jks", ".keystore", ".apk", ".aab", ".zip", ".png", ".jpg", ".jpeg", ".webp",
                ".ico", ".so", ".jar", ".class", ".pdf")
CLEAN_ROOM_PARTS = {"decompiled", "reversed", "jadx", "apktool", "smali", "rea"}
SECRET_RES = [
    re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
    re.compile(r"\bAKIA[0-9A-Z]{16}\b"),
    re.compile(r"\bsk-[A-Za-z0-9_\-]{20,}"),
    re.compile(r"\bghp_[A-Za-z0-9]{30,}"),
    re.compile(r"(?i)\b(store|key)?password\s*[=:]\s*['\"]?[A-Za-z0-9_\-!@#$%^&*]{8,}"),
    re.compile(r"(?i)\b(api[_-]?key|secret|token)\s*[=:]\s*['\"][A-Za-z0-9_\-]{16,}['\"]"),
    re.compile(r"(?i)\bcookie:\s*\S{30,}"),
]
# Where apply may write. Anything else is blocked.
WRITE_ROOTS = ("app/src/", "contracts/", "docs/", "tools/")
BUILD_FILES = ("app/build.gradle.kts", "gradle/libs.versions.toml", "app/proguard-rules.pro",
               "build.gradle.kts", "settings.gradle.kts", "gradle.properties")
WRITE_DENY = (".git/", ".github/", ".opencode/", "gradle/wrapper/", "gradlew", "gradlew.bat",
              "AGENTS.md", "LICENSE", "local.properties", "keystore.properties", "opencode.jsonc")

START_RE = re.compile(r"^\s*===\s*FILE:\s*(.+?)\s*===\s*$")
END_RE = re.compile(r"^\s*===\s*END FILE\s*===\s*$")
SENTINEL_RE = re.compile(r"^\s*===\s*END OF RESPONSE\s*===\s*$")
FENCE_RE = re.compile(r"^\s*```[\w+\-.]*\s*$")
COMMENT_START = ("//", "#", "/*", "*", "<!--")
PLACEHOLDER_LINE_RES = [
    re.compile(r"^\s*(//|#|/\*+|\*|<!--)\s*\.{3}"),
    re.compile(r"^\s*\.{3}\s*$"),
]
PLACEHOLDER_WORDS = re.compile(
    r"(?i)\b(rest of (the )?(file|code|class|function|implementation)|remaining (code|methods|functions)|"
    r"existing code (here|remains)|code (unchanged|omitted)|truncated|same as (before|above)|"
    r"unchanged (code|section))\b")


# ---------------------------------------------------------------- small helpers
def out(msg: str = "") -> None:
    print(msg)


def die(msg: str, code: int = 1) -> None:
    print("ERROR: " + msg, file=sys.stderr)
    sys.exit(code)


def work_dir() -> Path:
    p = ROOT / "_ds"
    p.mkdir(exist_ok=True)
    return p


def est_tokens(text: str) -> int:
    return int(len(text) / 3.5)


def rel(p: Path) -> str:
    return p.resolve().relative_to(ROOT).as_posix()


def read_text(p: Path) -> str:
    return p.read_bytes().decode("utf-8-sig", errors="replace").replace("\r\n", "\n").replace("\r", "\n")


def ensure_gitignore() -> None:
    gi = ROOT / ".gitignore"
    want = ["_ds/", ".ds_backup/"]
    have = gi.read_text(encoding="utf-8", errors="replace").splitlines() if gi.exists() else []
    missing = [w for w in want if w not in have]
    if missing:
        with gi.open("a", encoding="utf-8", newline="\n") as f:
            if have and have[-1] != "":
                f.write("\n")
            f.write("# ds_bridge working folders\n" + "\n".join(missing) + "\n")
        out("Added to .gitignore: " + ", ".join(missing))


def git(*args: str) -> subprocess.CompletedProcess | None:
    try:
        return subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True,
                              encoding="utf-8", errors="replace")
    except FileNotFoundError:
        return None


def iter_files():
    for dirpath, dirnames, filenames in os.walk(ROOT):
        dirnames[:] = sorted(d for d in dirnames if d not in EXCLUDE_DIRS)
        for f in sorted(filenames):
            yield Path(dirpath) / f


def is_clean_room_path(r: str) -> bool:
    return any(part.lower() in CLEAN_ROOM_PARTS for part in r.split("/"))


def bundlable(r: str) -> tuple[bool, str]:
    p = Path(r)
    if p.name in NEVER_NAMES or p.name.startswith(".env"):
        return False, "secret/config file"
    if p.suffix.lower() in NEVER_SUFFIX:
        return False, "binary"
    if p.suffix.lower() not in TEXT_EXT and p.name not in {"gradlew", ".gitignore", ".editorconfig", ".gitattributes"}:
        return False, "not a text type"
    if is_clean_room_path(r):
        return False, "clean-room (decompiled/third-party analysis)"
    return True, ""


CODE_PREFIXES = ("app/src/main/", "contracts/")
CODE_FILES = ("app/build.gradle.kts", "build.gradle.kts", "settings.gradle.kts", "gradle/libs.versions.toml",
              "gradle.properties", "app/proguard-rules.pro", "AGENTS.md", "README.md", "docs/STATE.md",
              "docs/01-PLAN.md", "docs/02-ARCHITECTURE.md")
SCOPE_HELP = "code = app/src/main + contracts + build files + key docs; tests = code + tests; everything = all text files"


def collect_all(scope: str) -> list[str]:
    picked = []
    for p in iter_files():
        r = rel(p)
        if not bundlable(r)[0] or r.startswith("app/src/main/assets/"):
            continue
        if scope == "everything":
            picked.append(r)
            continue
        in_code = r.startswith(CODE_PREFIXES) or r in CODE_FILES
        in_tests = scope == "tests" and r.startswith(("app/src/test/", "app/src/androidTest/"))
        if in_code or in_tests:
            picked.append(r)
    return picked


def secret_hit(text: str) -> str | None:
    for rx in SECRET_RES:
        m = rx.search(text)
        if m:
            return rx.pattern[:40]
    return None


def resolve_patterns(patterns: list[str]) -> list[str]:
    found: list[str] = []
    for pat in patterns:
        pat = pat.replace("\\", "/").strip().lstrip("./")
        if any(ch in pat for ch in "*?["):
            hits = sorted(rel(p) for p in ROOT.glob(pat) if p.is_file())
            if not hits:
                die(f"pattern matched nothing: {pat}")
            found.extend(hits)
        else:
            found.append(pat)
    seen, uniq = set(), []
    for f in found:
        if f not in seen:
            seen.add(f)
            uniq.append(f)
    return uniq


# ---------------------------------------------------------------- tree
def render_tree(files: list[str]) -> str:
    tree: dict = {}
    for f in files:
        node = tree
        parts = f.split("/")
        for part in parts[:-1]:
            node = node.setdefault(part + "/", {})
        node[parts[-1]] = None
    lines: list[str] = []

    def walk(node: dict, prefix: str, base: str) -> None:
        names = sorted(node, key=lambda n: (not n.endswith("/"), n.lower()))
        for i, name in enumerate(names):
            last = i == len(names) - 1
            branch = "`-- " if last else "|-- "
            if node[name] is None:
                p = ROOT / base / name
                info = ""
                if p.suffix.lower() in TEXT_EXT:
                    try:
                        info = f"  ({read_text(p).count(chr(10)) + 1} lines)"
                    except OSError:
                        pass
                lines.append(prefix + branch + name + info)
            else:
                lines.append(prefix + branch + name)
                walk(node[name], prefix + ("    " if last else "|   "), base + name)

    walk(tree, "", "")
    return "\n".join(lines) + "\n"


def cmd_tree(args) -> None:
    files = [rel(p) for p in iter_files()]
    text = ROOT.name + "/\n" + render_tree(files)
    (work_dir() / "TREE.txt").write_text(text, encoding="utf-8")
    out(f"Wrote _ds/TREE.txt ({len(files)} files, ~{est_tokens(text)} tokens)")


# ---------------------------------------------------------------- digest (heuristic signatures)
DECL_RE = re.compile(
    r"^(?:(?:public|internal|private|protected|override|suspend|data|sealed|enum|abstract|open|inline|"
    r"const|lateinit|operator|infix|annotation|value|companion|expect|actual)\s+)*"
    r"(?:fun\s+interface|class|interface|object|fun|val|var|typealias)\b")


def _cut_signature(sig: str) -> str:
    depth = 0
    for i, ch in enumerate(sig):
        if ch in "([<":
            depth += 1
        elif ch in ")]>":
            depth = max(0, depth - 1)
        elif depth == 0 and (sig.startswith(" {", i) or sig.startswith(" = ", i) or sig.startswith(" by ", i)):
            return sig[:i].rstrip()
    return sig.rstrip(" {")


def kotlin_digest(text: str) -> list[str]:
    lines = text.split("\n")
    result: list[str] = []
    i = 0
    while i < len(lines):
        raw = lines[i]
        indent = len(raw) - len(raw.lstrip(" "))
        s = raw.strip()
        if indent <= 4 and s and not s.startswith(("//", "*", "/*", "@")) and DECL_RE.match(s) \
                and not s.startswith("private "):
            sig = s
            depth = sig.count("(") - sig.count(")")
            j = i
            while depth > 0 and j + 1 < len(lines) and j - i < 14:
                j += 1
                nxt = lines[j].strip()
                sig += " " + nxt
                depth += nxt.count("(") - nxt.count(")")
            cap = 700 if re.search(r"\bclass\b", sig[:60]) else 260
            result.append(("  " * (indent // 4)) + _cut_signature(sig)[:cap])
            i = j
        i += 1
    return result


def build_digest(paths: list[str]) -> str:
    chunks = []
    for r in paths:
        if not r.endswith(".kt"):
            continue
        try:
            sigs = kotlin_digest(read_text(ROOT / r))
        except OSError:
            continue
        if sigs:
            chunks.append(f"// {r}\n" + "\n".join(sigs))
    return "\n\n".join(chunks) + "\n"


# ---------------------------------------------------------------- bundle
def file_block(r: str) -> str:
    p = ROOT / r
    body = read_text(p).rstrip("\n")
    return f"===FILE: {r}===\n{body}\n===END FILE===\n"


def split_parts(blocks: list[str], header: str, max_chars: int) -> list[str]:
    parts, cur = [], header
    for b in blocks:
        if len(cur) + len(b) > max_chars and cur != header:
            parts.append(cur)
            cur = header
        cur += "\n" + b
    parts.append(cur)
    return parts


def write_group(folder: Path, base: str, blocks: list[str], header: str, max_chars: int) -> list[str]:
    names = []
    parts = split_parts(blocks, header, max_chars)
    for n, content in enumerate(parts, 1):
        name = f"{base}.txt" if len(parts) == 1 else f"{base}_part{n}.txt"
        (folder / name).write_text(content, encoding="utf-8", newline="\n")
        names.append(name)
    return names


def extract_roadmap(phase: str | None, roadmap: Path) -> str:
    text = roadmap.read_text(encoding="utf-8", errors="replace").split("\n")
    heads = [(i, ln) for i, ln in enumerate(text) if ln.startswith("## ") or ln.startswith("### ")]

    def section(prefixes: tuple[str, ...]) -> list[str]:
        for idx, (i, ln) in enumerate(heads):
            if any(ln.startswith(p) for p in prefixes):
                end = heads[idx + 1][0] if idx + 1 < len(heads) else len(text)
                return text[i:end]
        return []

    picked = section(("## 0.",)) + section(("## 5.",))
    if phase:
        sec = section((f"### Phase {phase}:", f"### Phase {phase} ", f"### Phase {phase}\n"))
        if not sec:
            die(f"no '### Phase {phase}' heading in {roadmap}")
        picked += [""] + sec
    return "\n".join(picked) + "\n"


def pinned_versions() -> str:
    toml = ROOT / "gradle" / "libs.versions.toml"
    if not toml.exists():
        return "(gradle/libs.versions.toml not found)"
    lines, on = [], False
    for ln in read_text(toml).split("\n"):
        if ln.strip().startswith("["):
            on = ln.strip() == "[versions]"
            continue
        if on and ln.strip() and not ln.strip().startswith("#"):
            lines.append(ln.strip())
    return "\n".join(lines)


def error_files_from_log(text: str) -> list[str]:
    found = []
    for m in re.finditer(r"((?:app|contracts)[/\\]src[/\\][^\s:()]+?\.(?:kt|java|xml))", text.replace("\\", "/")):
        p = m.group(1)
        if p not in found and (ROOT / p).exists():
            found.append(p)
    # Paths like file:///C:/.../app/src/main/... are covered by the search above.
    return found


def cmd_bundle(args) -> None:
    ensure_gitignore()
    edit = resolve_patterns(args.edit or [])
    new = resolve_patterns(args.new or [])
    refs = resolve_patterns(args.ref or [])
    errors_text = ""
    if args.fix:
        ep = work_dir() / "build_errors.txt"
        if not ep.exists():
            die("no _ds/build_errors.txt - run `verify` first")
        errors_text = ep.read_text(encoding="utf-8", errors="replace")
        for f in error_files_from_log(errors_text):
            if f not in edit:
                edit.append(f)
    intro = not edit and not new
    if intro and not args.all:
        die("nothing to do: give --edit and/or --new (or --fix), or --all for an intro batch")
    existing_new = [f for f in new if (ROOT / f).exists()]
    if existing_new:
        die("--new paths already exist (use --edit): " + ", ".join(existing_new))
    missing = [f for f in edit if not (ROOT / f).exists()]
    if missing:
        die("--edit paths do not exist (use --new): " + ", ".join(missing))

    problems = []
    for f in edit + refs:
        ok, why = bundlable(f)
        if not ok:
            problems.append(f"{f}: {why}")
    if problems:
        die("refusing to bundle:\n  " + "\n  ".join(problems))
    for f in edit + refs:
        hit = None if "/src/test/" in f else secret_hit(read_text(ROOT / f))
        if hit and not args.no_secret_scan:
            die(f"{f}: looks like it contains a secret (pattern {hit}). Fix it or pass --no-secret-scan.")
    dup = set(edit) & set(refs)
    refs = [r for r in refs if r not in dup]
    skipped_secret: list[str] = []
    if args.all:
        for f in collect_all(args.scope):
            if f in edit or f in new or f in refs:
                continue
            if "/src/test/" not in f and not args.no_secret_scan and secret_hit(read_text(ROOT / f)):
                skipped_secret.append(f)
                continue
            refs.append(f)
        if skipped_secret:
            out("Skipped (looks like a secret): " + ", ".join(skipped_secret))

    name = args.name or dt.datetime.now().strftime("batch_%Y%m%d_%H%M%S")
    folder = work_dir() / name
    if folder.exists():
        shutil.rmtree(folder)
    folder.mkdir(parents=True)

    header_note = ("# Repository files as text. Real paths are in the ===FILE: path=== markers "
                   "(uploaded as .txt only because the chat accepts text files).")
    uploads: list[str] = []
    uploads += write_group(folder, "EDIT_FILES", [file_block(f) for f in edit],
                           header_note + " These are the CURRENT contents of the files you must return in full.",
                           args.max_chars) if edit else []
    if refs:
        uploads += write_group(folder, "REFERENCE_FILES", [file_block(f) for f in refs],
                               header_note + " READ-ONLY reference; do not return these.", args.max_chars)
    all_files = [rel(p) for p in iter_files()]
    tree_text = ROOT.name + "/\n" + render_tree(all_files)
    (folder / "TREE.txt").write_text(tree_text, encoding="utf-8", newline="\n")
    uploads.append("TREE.txt")
    if not args.no_digest:
        digest_paths = [f for f in all_files if f.startswith("app/src/main/") and f.endswith(".kt")
                        and f not in edit and f not in refs]
        dg = ("# Heuristic list of declarations in repo files NOT included in full (nested members may be missing).\n"
              "# Use these signatures; do not guess others.\n\n") + build_digest(digest_paths)
        if digest_paths and "// " in dg:
            (folder / "API_DIGEST.txt").write_text(dg, encoding="utf-8", newline="\n")
            uploads.append("API_DIGEST.txt")
    roadmap = Path(args.roadmap) if args.roadmap else (ROOT / "docs" / "ROADMAP.md")
    if args.phase:
        if not roadmap.exists():
            die(f"roadmap not found: {roadmap} (use --roadmap PATH)")
        (folder / "ROADMAP_EXCERPT.txt").write_text(extract_roadmap(args.phase, roadmap), encoding="utf-8",
                                                     newline="\n")
        uploads.append("ROADMAP_EXCERPT.txt")
    brief = Path(args.brief) if args.brief else (ROOT / "docs" / "phases" / f"{args.phase}-brief.md" if args.phase else None)
    if brief and brief.exists():
        (folder / "PHASE_BRIEF.txt").write_text(brief.read_text(encoding="utf-8", errors="replace"),
                                                 encoding="utf-8", newline="\n")
        uploads.append("PHASE_BRIEF.txt")
    elif args.brief:
        die(f"brief not found: {brief}")
    if errors_text:
        (folder / "BUILD_ERRORS.txt").write_text(errors_text, encoding="utf-8", newline="\n")
        uploads.append("BUILD_ERRORS.txt")

    # message
    master = ""
    if not args.no_master:
        mp = Path(__file__).resolve().parent / "ds" / "MASTER_PROMPT.txt"
        if not mp.exists():
            mp = Path(__file__).resolve().parent / "MASTER_PROMPT.txt"
        if mp.exists():
            master = mp.read_text(encoding="utf-8") + "\n\n"
        else:
            out("WARNING: MASTER_PROMPT.txt not found next to the script; message has only the batch section.")
    if intro:
        lines = ["=== THIS BATCH: INTRO (no code) ===",
                 "This batch OVERRIDES the OUTPUT PROTOCOL: do NOT write ===FILE blocks.",
                 "TASK: read every uploaded file, then reply in plain text, at most 60 lines:",
                 "  1. What the app does and its current state.",
                 "  2. Architecture in at most 10 lines.",
                 "  3. The 10 most important files and why.",
                 "  4. Inconsistencies or risks you notice, each citing a file path. If something is not in the "
                 "uploads, write 'not in uploads' - do not guess.",
                 "  5. Questions you need answered before coding.",
                 "", "UPLOADED FILES IN THIS CHAT: " + ", ".join(uploads),
                 "", "PINNED VERSIONS (gradle/libs.versions.toml [versions]):", pinned_versions(),
                 "", "End with ===END OF RESPONSE===.", "Begin."]
    else:
        lines = ["=== THIS BATCH ===",
                 f"PHASE: {args.phase or '(not specified)'}" + (f"    BATCH: {args.batch}" if args.batch else ""),
                 f"TASK: {args.task or '(see ROADMAP_EXCERPT.txt for the phase goal)'}",
                 "", "EDIT FILES (return each one complete, in this order):"]
        lines += [f"  - {f}" for f in edit] or ["  (none)"]
        lines += ["", "NEW FILES (create each one complete):"]
        lines += [f"  - {f}" for f in new] or ["  (none)"]
        lines += ["", "REFERENCE FILES (read-only, do not return):"]
        lines += [f"  - {f}" for f in refs] or ["  (none)"]
        lines += ["", "UPLOADED FILES IN THIS CHAT: " + ", ".join(uploads)]
        lines += ["", "PINNED VERSIONS (gradle/libs.versions.toml [versions]):", pinned_versions()]
        if "PHASE_BRIEF.txt" in uploads:
            lines += ["", "PHASE_BRIEF.txt holds researched facts and pitfalls for this phase: trust it over your memory."]
        if errors_text:
            lines += ["", "BUILD ERRORS TO FIX: see BUILD_ERRORS.txt. Fix only what the errors show."]
        if args.accept:
            lines += ["", "ACCEPTANCE CRITERIA:"] + [f"  - {a}" for a in args.accept]
        lines += ["", f"MAX FILES IN THIS RESPONSE: {args.max_files}. If they do not all fit, return fewer COMPLETE files "
                      "and list the rest as REMAINING in NOTES.",
                  "Follow the OUTPUT PROTOCOL exactly and end with ===END OF RESPONSE===.", "Begin."]
    message = master + "\n".join(lines) + "\n"
    (folder / "PASTE_THIS_MESSAGE.txt").write_text(message, encoding="utf-8", newline="\n")

    state = {"edit": edit, "new": new, "name": name, "time": dt.datetime.now().isoformat(timespec="seconds"),
             "max_files": args.max_files}
    (work_dir() / "last_batch.json").write_text(json.dumps(state, indent=2), encoding="utf-8")

    total = sum(len((folder / u).read_text(encoding="utf-8")) for u in uploads)
    out(f"Batch folder: {folder}")
    out("Upload these files to the chat:")
    for u in uploads:
        out(f"  - {u}  (~{est_tokens((folder / u).read_text(encoding='utf-8'))} tokens)")
    out(f"Paste this as the message: _ds/{name}/PASTE_THIS_MESSAGE.txt (~{est_tokens(message)} tokens)")
    out(f"Total upload ~{est_tokens('x' * total)} tokens; files to upload: {len(uploads)} (chat limit reported: 50).")
    if len(edit) + len(new) > args.max_files:
        out(f"NOTE: {len(edit) + len(new)} files requested but MAX FILES is {args.max_files}; "
            "the model may return only part and list REMAINING.")


# ---------------------------------------------------------------- apply
def get_clipboard() -> str:
    try:
        import tkinter
        r = tkinter.Tk()
        r.withdraw()
        try:
            return r.clipboard_get()
        finally:
            r.destroy()
    except Exception:
        pass
    cmds = [["powershell", "-NoProfile", "-Command", "Get-Clipboard -Raw"], ["pbpaste"],
            ["xclip", "-selection", "clipboard", "-o"], ["wl-paste"]]
    for c in cmds:
        try:
            res = subprocess.run(c, capture_output=True, text=True, encoding="utf-8", errors="replace")
            if res.returncode == 0 and res.stdout:
                return res.stdout
        except FileNotFoundError:
            continue
    die("could not read the clipboard; save the reply to a file and use --in FILE")
    return ""


def clean_path(raw: str) -> str:
    p = raw.strip().strip("`'\"*").replace("\\_", "_").replace("\\", "/")
    while p.startswith("./"):
        p = p[2:]
    return p


def strip_fence(lines: list[str]) -> list[str]:
    while lines and not lines[0].strip():
        lines = lines[1:]
    while lines and not lines[-1].strip():
        lines = lines[:-1]
    if len(lines) >= 2 and FENCE_RE.match(lines[0]) and lines[-1].strip() == "```":
        lines = lines[1:-1]
    return lines


def parse_response(text: str):
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    blocks, notes = [], []
    cur = None
    sentinel = False
    for line in text.split("\n"):
        if SENTINEL_RE.match(line):
            sentinel = True
            if cur is not None:
                cur["terminated"] = False
                blocks.append(cur)
                cur = None
            continue
        m = START_RE.match(line)
        if m:
            if cur is not None:
                cur["terminated"] = False
                blocks.append(cur)
            cur = {"path": clean_path(m.group(1)), "lines": [], "terminated": False}
            continue
        if cur is not None:
            if END_RE.match(line):
                cur["terminated"] = True
                blocks.append(cur)
                cur = None
            else:
                cur["lines"].append(line)
        else:
            notes.append(line)
    if cur is not None:
        cur["terminated"] = False
        blocks.append(cur)
    for b in blocks:
        b["lines"] = strip_fence(b["lines"])
        b["content"] = "\n".join(b["lines"]) + "\n"
    return blocks, notes, sentinel


def check_balance(text: str) -> str | None:
    stack, i, n, line = [], 0, len(text), 1
    pairs = {")": "(", "]": "[", "}": "{"}
    while i < n:
        c = text[i]
        if c == "\n":
            line += 1
            i += 1
        elif text.startswith("//", i):
            j = text.find("\n", i)
            i = n if j < 0 else j
        elif text.startswith("/*", i):
            depth = 1
            i += 2
            while i < n and depth:
                if text.startswith("/*", i):
                    depth += 1
                    i += 2
                elif text.startswith("*/", i):
                    depth -= 1
                    i += 2
                else:
                    if text[i] == "\n":
                        line += 1
                    i += 1
        elif text.startswith('"""', i):
            j = text.find('"""', i + 3)
            if j < 0:
                return f"unterminated raw string from line {line}"
            while j + 3 < n and text[j + 3] == '"':
                j += 1
            line += text.count("\n", i, j)
            i = j + 3
        elif c == '"':
            i += 1
            while i < n and text[i] != '"':
                if text[i] == "\\":
                    i += 1
                if i < n and text[i] == "\n":
                    return f"unterminated string at line {line}"
                i += 1
            i += 1
        elif c == "'":
            if i + 1 < n and text[i + 1] == "\\":
                j = text.find("'", i + 2)
                i = n if j < 0 else j + 1
            elif i + 2 < n and text[i + 2] == "'":
                i += 3
            else:
                i += 1
        elif c in "([{":
            stack.append((c, line))
            i += 1
        elif c in ")]}":
            if not stack or stack[-1][0] != pairs[c]:
                return f"unmatched '{c}' at line {line}"
            stack.pop()
            i += 1
        else:
            i += 1
    if stack:
        return f"unclosed '{stack[-1][0]}' opened at line {stack[-1][1]}"
    return None


def syntax_issue(path: str, content: str) -> str | None:
    ext = Path(path).suffix.lower()
    try:
        if ext == ".json":
            json.loads(content)
        elif ext == ".py":
            ast.parse(content)
        elif ext == ".xml":
            import xml.etree.ElementTree as ET
            ET.fromstring(content.encode("utf-8"))
        elif ext == ".toml":
            try:
                import tomllib
                tomllib.loads(content)
            except ImportError:
                pass
        elif ext in (".kt", ".kts", ".java"):
            return check_balance(content)
    except Exception as e:  # noqa: BLE001
        return f"{type(e).__name__}: {str(e)[:120]}"
    return None


def placeholder_issue(content: str) -> str | None:
    for n, ln in enumerate(content.split("\n"), 1):
        if any(rx.search(ln) for rx in PLACEHOLDER_LINE_RES):
            return f"placeholder-like line {n}: {ln.strip()[:70]}"
        if ln.strip().startswith(COMMENT_START) and PLACEHOLDER_WORDS.search(ln):
            return f"placeholder-like comment line {n}: {ln.strip()[:70]}"
    return None


def path_issue(p: str, allow_build: bool, allow_globs: list[str]) -> str | None:
    if not p or p.startswith("/") or re.match(r"^[A-Za-z]:", p) or ".." in p.split("/"):
        return "unsafe path"
    if any(p == d or p.startswith(d) for d in WRITE_DENY):
        return "protected path"
    if Path(p).suffix.lower() not in TEXT_EXT:
        return "file type not allowed"
    if any(fnmatch.fnmatch(p, g) for g in allow_globs):
        return None
    if p in BUILD_FILES:
        return None if allow_build else "build file (use --allow-build-files after checking versions)"
    if not any(p.startswith(r) for r in WRITE_ROOTS):
        return "outside allowed folders (app/src, contracts, docs, tools)"
    return None


def newline_style(p: Path) -> str:
    try:
        raw = p.read_bytes()
    except OSError:
        return "\n"
    return "\r\n" if raw.count(b"\r\n") > raw.count(b"\n") / 2 and b"\r\n" in raw else "\n"


def count_delta(old: str, new: str) -> tuple[int, int]:
    add = rem = 0
    for ln in difflib.ndiff(old.split("\n"), new.split("\n")):
        if ln.startswith("+ "):
            add += 1
        elif ln.startswith("- "):
            rem += 1
    return add, rem


def cmd_apply(args) -> None:
    if args.clip:
        text = get_clipboard()
    elif args.infile:
        text = Path(args.infile).read_text(encoding="utf-8", errors="replace")
    elif not sys.stdin.isatty():
        text = sys.stdin.read()
    else:
        die("give --clip, --in FILE, or pipe the reply on stdin")
    blocks, notes, sentinel = parse_response(text)
    if not blocks:
        die("no ===FILE: path=== blocks found in the reply")

    batch = {}
    bp = work_dir() / "last_batch.json"
    if bp.exists() and not args.ignore_batch:
        batch = json.loads(bp.read_text(encoding="utf-8"))
    allowed = set(batch.get("edit", [])) | set(batch.get("new", [])) if batch else None
    allow_globs = args.allow_path or []

    plan, seen = [], set()
    for b in blocks:
        p, content = b["path"], b["content"]
        issues, warns = [], []
        pi = path_issue(p, args.allow_build_files, allow_globs)
        if pi:
            issues.append(pi)
        if p in seen:
            issues.append("duplicate block for this path")
        seen.add(p)
        if allowed is not None and p not in allowed and not args.allow_extra:
            issues.append("not in the current batch (use --allow-extra to accept)")
        if not b["terminated"]:
            issues.append("block has no ===END FILE=== (reply was probably cut off)")
        if not content.strip():
            issues.append("empty content")
        ph = placeholder_issue(content)
        if ph:
            issues.append(ph)
        sx = syntax_issue(p, content)
        if sx:
            issues.append("syntax: " + sx)
        target = ROOT / p
        status, add, rem = "NEW", content.count("\n"), 0
        if target.exists() and not pi:
            old = read_text(target)
            if old == content:
                status, add, rem = "UNCHANGED", 0, 0
            else:
                status = "MODIFIED"
                add, rem = count_delta(old, content)
                old_lines = old.count("\n")
                if old_lines >= 40 and content.count("\n") < 0.6 * old_lines and not args.force_shrink:
                    issues.append(f"file shrank from {old_lines} to {content.count(chr(10))} lines "
                                  "(truncation?) - use --force-shrink if intended")
        if args.skip_checks:
            issues = [i for i in issues if i.split(":")[0] in ("unsafe path", "protected path")
                      or i.startswith(("file type", "outside", "build file", "duplicate"))]
        plan.append({"path": p, "content": content, "status": "BLOCKED" if issues else status,
                     "raw_status": status, "issues": issues, "add": add, "rem": rem})

    out(f"Parsed {len(blocks)} file block(s); end-of-reply marker: {'yes' if sentinel else 'NO'}")
    for e in plan:
        extra = "" if e["status"] in ("BLOCKED", "UNCHANGED") else f"  +{e['add']} -{e['rem']}"
        out(f"  [{e['status']:9}] {e['path']}{extra}")
        for i in e["issues"]:
            out(f"      ! {i}")
    note_lines = [ln for ln in notes if ln.strip()][:40]
    if note_lines:
        out("\nModel notes/report (outside file blocks):")
        for ln in note_lines:
            out("  | " + ln[:200])
    blocked = [e for e in plan if e["status"] == "BLOCKED"]
    if not sentinel:
        out("\nWARNING: the reply has no ===END OF RESPONSE=== line - it may be incomplete.")
    if args.write and not sentinel and not args.skip_checks:
        out("Refusing to write: reply not confirmed complete (add --skip-checks if you verified it yourself).")
        sys.exit(2)
    if not args.write:
        out("\nDry run only. Re-run with --write to apply the non-blocked files.")
        sys.exit(1 if blocked else 0)

    st = git("status", "--porcelain")
    if st is None:
        out("WARNING: git not found; relying on .ds_backup only.")
    elif [l for l in st.stdout.splitlines() if l.strip() and not l.strip().endswith(".gitignore")] \
            and not args.allow_dirty:
        die("working tree has uncommitted changes. Commit or stash first so git can show and undo this batch "
            "(or pass --allow-dirty).")
    todo = [e for e in plan if e["status"] in ("NEW", "MODIFIED")]
    if blocked and not args.partial:
        die(f"{len(blocked)} file(s) blocked - nothing written. Fix the reply or use --partial to write the rest.")
    stamp = dt.datetime.now().strftime("%Y%m%d_%H%M%S")
    bdir = ROOT / ".ds_backup" / stamp
    record = []
    for e in todo:
        target = ROOT / e["path"]
        existed = target.exists()
        nl = newline_style(target) if existed else "\n"
        if existed:
            dest = bdir / e["path"]
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(target, dest)
        target.parent.mkdir(parents=True, exist_ok=True)
        tmp = target.with_name(target.name + ".ds_tmp")
        tmp.write_bytes(e["content"].replace("\n", nl).encode("utf-8"))
        os.replace(tmp, target)
        record.append({"path": e["path"], "existed": existed})
    (work_dir() / "last_apply.json").write_text(json.dumps({"backup": str(bdir), "files": record}, indent=2),
                                                 encoding="utf-8")
    ensure_gitignore()
    out(f"\nWrote {len(todo)} file(s). Backup: {bdir}")
    if batch:
        done = {e["path"] for e in todo} | {e["path"] for e in plan if e["status"] == "UNCHANGED"}
        remaining = [f for f in batch.get("edit", []) + batch.get("new", []) if f not in done]
        if remaining:
            out("Not returned yet (put these in the next batch):")
            for r in remaining:
                out("  - " + r)
            new_set = set(batch.get("new", []))
            e_args = " ".join(f"{r}" for r in remaining if r not in new_set)
            n_args = " ".join(f"{r}" for r in remaining if r in new_set)
            cmdline = "python tools/ds_bridge.py bundle"
            if e_args:
                cmdline += f" --edit {e_args}"
            if n_args:
                cmdline += f" --new {n_args}"
            out("Suggested: " + cmdline)
    g = git("diff", "--stat")
    if g is not None and g.stdout.strip():
        out("\n" + g.stdout.rstrip())
    out("\nNext: python tools/ds_bridge.py verify")


def cmd_undo(args) -> None:
    lp = work_dir() / "last_apply.json"
    if not lp.exists():
        die("nothing to undo (no _ds/last_apply.json)")
    rec = json.loads(lp.read_text(encoding="utf-8"))
    bdir = Path(rec["backup"])
    for f in rec["files"]:
        target = ROOT / f["path"]
        if f["existed"]:
            shutil.copy2(bdir / f["path"], target)
            out("restored " + f["path"])
        elif target.exists():
            target.unlink()
            out("removed   " + f["path"])
    lp.unlink()
    out("Undo complete.")


# ---------------------------------------------------------------- verify / review
def trim_errors(log: str, limit: int) -> str:
    keep, lines = [], log.split("\n")
    for i, ln in enumerate(lines):
        s = ln.strip()
        if (s.startswith("e: ") or "error:" in s.lower() or " FAILED" in s or s.startswith("BUILD ")
                or s.startswith("* What went wrong") or "Unresolved reference" in s
                or re.search(r"\d+ tests completed", s)):
            keep.append(s[:300])
            if s.startswith("* What went wrong"):
                keep += [x.strip()[:300] for x in lines[i + 1:i + 4] if x.strip()]
    seen, uniq = set(), []
    for k in keep:
        if k not in seen:
            seen.add(k)
            uniq.append(k)
    return "\n".join(uniq[:limit]) + "\n"


def cmd_verify(args) -> None:
    gradle = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    if not gradle.exists():
        die(f"{gradle.name} not found in {ROOT}")
    tasks = args.tasks or ["testDebugUnitTest", "lintDebug"]
    out("Running: " + " ".join([gradle.name, *tasks, "--console=plain"]) + "  (this can take minutes)")
    lines = []
    proc = subprocess.Popen([str(gradle), *tasks, "--console=plain"], cwd=ROOT, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace")
    assert proc.stdout is not None
    for ln in proc.stdout:
        lines.append(ln.rstrip("\n"))
        if args.verbose:
            print(ln, end="")
    code = proc.wait()
    log = "\n".join(lines)
    (work_dir() / "build.log").write_text(log, encoding="utf-8")
    trimmed = trim_errors(log, args.max_lines)
    (work_dir() / "build_errors.txt").write_text(trimmed, encoding="utf-8")
    out("\n".join(l for l in lines[-6:] if l.strip()))
    if code == 0:
        out("\nGRADLE OK. Commit this batch, then run the next one.")
    else:
        out(f"\nGRADLE FAILED (exit {code}). Trimmed errors: _ds/build_errors.txt")
        out("Next: python tools/ds_bridge.py bundle --fix --phase <id>   (re-sends the failing files with the errors)")
    sys.exit(code)


def cmd_review(args) -> None:
    parts = []
    base = args.since
    d = git("diff", "--no-color", *( [base] if base else ["HEAD"] ))
    if d is None:
        die("git not found")
    parts.append(d.stdout)
    un = git("ls-files", "--others", "--exclude-standard")
    for f in (un.stdout.split("\n") if un else []):
        f = f.strip()
        if not f or not bundlable(f)[0]:
            continue
        parts.append(f"\n===NEW FILE: {f}===\n{read_text(ROOT / f)}\n===END NEW FILE===\n")
    text = "".join(parts)
    if not text.strip():
        die("no changes to review")
    hit = secret_hit(text)
    if hit and not args.no_secret_scan:
        die(f"diff looks like it contains a secret (pattern {hit}); fix or pass --no-secret-scan")
    (work_dir() / "review.diff").write_text(text, encoding="utf-8", newline="\n")
    (work_dir() / "review.diff.txt").write_text(text, encoding="utf-8", newline="\n")
    out(f"Wrote _ds/review.diff.txt (~{est_tokens(text)} tokens). Upload it to the reviewer with the review prompt "
        "from WORKFLOW.md.")


# ---------------------------------------------------------------- main
def main(argv=None) -> None:
    global ROOT
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:  # noqa: BLE001
        pass
    ap = argparse.ArgumentParser(prog="ds_bridge", description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--root", help="repo root (default: parent of this script's folder)")
    sub = ap.add_subparsers(dest="cmd", required=True)

    sub.add_parser("tree", help="write _ds/TREE.txt").set_defaults(fn=cmd_tree)

    b = sub.add_parser("bundle", help="build upload files + paste-ready message for one batch")
    b.add_argument("--edit", nargs="*", help="existing files the model must return in full (globs ok)")
    b.add_argument("--new", nargs="*", help="new files the model must create")
    b.add_argument("--ref", nargs="*", help="read-only context files sent in full")
    b.add_argument("--phase", help="roadmap phase id, e.g. R, 1, 2A, 2B (adds ROADMAP_EXCERPT.txt)")
    b.add_argument("--batch", help="label such as 2/5")
    b.add_argument("--task", help="one or two sentences: what this batch must achieve")
    b.add_argument("--accept", nargs="*", help="acceptance criteria lines")
    b.add_argument("--name", help="folder name under _ds/")
    b.add_argument("--roadmap", help="path to ROADMAP.md (default docs/ROADMAP.md)")
    b.add_argument("--max-files", type=int, default=4, help="max files the model may return per reply (default 4)")
    b.add_argument("--max-chars", type=int, default=120000, help="split upload files above this size")
    b.add_argument("--all", action="store_true", help="also send many repo files in full (intro batch if no --edit/--new)")
    b.add_argument("--scope", choices=["code", "tests", "everything"], default="code", help="with --all: " + SCOPE_HELP)
    b.add_argument("--brief", help="phase brief file (default docs/phases/<phase>-brief.md if it exists)")
    b.add_argument("--fix", action="store_true", help="use _ds/build_errors.txt: add the failing files to --edit")
    b.add_argument("--no-master", action="store_true", help="omit the master prompt (already in this chat)")
    b.add_argument("--no-digest", action="store_true")
    b.add_argument("--no-secret-scan", action="store_true")
    b.set_defaults(fn=cmd_bundle)

    a = sub.add_parser("apply", help="parse the model's reply and write files (dry run unless --write)")
    a.add_argument("--clip", action="store_true", help="read the reply from the clipboard")
    a.add_argument("--in", dest="infile", help="read the reply from a file")
    a.add_argument("--write", action="store_true", help="actually write files")
    a.add_argument("--partial", action="store_true", help="write non-blocked files even if some are blocked")
    a.add_argument("--allow-extra", action="store_true", help="accept files that are not in the current batch")
    a.add_argument("--allow-build-files", action="store_true", help="allow Gradle/version-catalog edits")
    a.add_argument("--allow-path", action="append", help="extra glob that may be written")
    a.add_argument("--allow-dirty", action="store_true")
    a.add_argument("--force-shrink", action="store_true")
    a.add_argument("--ignore-batch", action="store_true")
    a.add_argument("--skip-checks", action="store_true", help="skip syntax/placeholder/end-marker checks")
    a.set_defaults(fn=cmd_apply)

    sub.add_parser("undo", help="restore files from the last apply --write").set_defaults(fn=cmd_undo)

    v = sub.add_parser("verify", help="run gradle tests + lint and save trimmed errors")
    v.add_argument("--tasks", nargs="*")
    v.add_argument("--max-lines", type=int, default=80)
    v.add_argument("--verbose", action="store_true")
    v.set_defaults(fn=cmd_verify)

    r = sub.add_parser("review", help="write the diff for a reviewer model")
    r.add_argument("--since", help="git ref to diff against (default HEAD)")
    r.add_argument("--no-secret-scan", action="store_true")
    r.set_defaults(fn=cmd_review)

    args = ap.parse_args(argv)
    if args.root:
        ROOT = Path(args.root).resolve()
    args.fn(args)


if __name__ == "__main__":
    main()
