#!/usr/bin/env python3
"""Self-check for the hand-off package. Run from the package root: python3 tools/verify_handoff.py
Checks: JSON validity, workflow structure, referenced files exist, README lists every doc, and that no decompiled
Snaptube source was included. It does NOT prove the workflow runs on GitHub (no Actions runner available)."""
import json, re, sys, pathlib
import yaml

root = pathlib.Path(__file__).resolve().parent.parent
errors, notes = [], []

def err(m): errors.append(m)

# 1. JSON
for f in sorted((root / "contracts").glob("*.json")):
    try:
        json.loads(f.read_text(encoding="utf-8")); notes.append(f"json ok: {f.name}")
    except Exception as e:
        err(f"invalid JSON {f}: {e}")

# 2. Workflow structure
wf = yaml.safe_load((root / ".github/workflows/android.yml").read_text(encoding="utf-8"))
on = wf.get("on", wf.get(True))
for t in ("push", "pull_request", "workflow_dispatch"):
    if t not in on: err(f"workflow missing trigger {t}")
jobs = wf["jobs"]
for j in ("unit-tests", "instrumented-tests", "build-artifact"):
    if j not in jobs: err(f"workflow missing job {j}")
build = jobs["build-artifact"]
if "workflow_dispatch" not in str(build.get("if", "")): err("build-artifact is not gated on workflow_dispatch")
ups = [(jn, s) for jn, j in jobs.items() for s in j.get("steps", []) if "upload-artifact" in str(s.get("uses", ""))]
for jn, s in ups:
    if jn != "build-artifact" and "failure()" not in str(s.get("if", "")):
        err(f"upload-artifact in {jn} is not failure-only: {s.get('name')}")
if not any(jn == "build-artifact" for jn, _ in ups): err("build-artifact job has no upload step")
if wf.get("permissions") != {"contents": "read"}: err("workflow permissions should be contents: read")
notes.append(f"workflow ok: jobs={list(jobs)} uploads={[(jn, s.get('name')) for jn, s in ups]}")
yaml.safe_load((root / ".github/dependabot.yml").read_text(encoding="utf-8")); notes.append("dependabot ok")

# 3. Referenced files exist (backticked paths)
will_create = {"docs/RESEARCH-LOG.md", "docs/PROGRESS.md", "docs/evidence/phase0.md", "THIRD_PARTY_NOTICES.md",
               "docs/development-standards.md", "docs/dev-setup.md", "docs/decisions/0001-license-gpl3.md", "SECURITY.md", "LICENSE"}
pat = re.compile(r"`((?:docs|contracts|tools|\.github)/[A-Za-z0-9_./-]+\.(?:md|json|yml|py))`")
for md in list(root.glob("*.md")) + list((root / "docs").glob("*.md")):
    for m in pat.findall(md.read_text(encoding="utf-8")):
        if m in will_create or "*" in m: continue
        if not (root / m).exists(): err(f"{md.name} references missing file {m}")

# 4. README lists every doc
readme = (root / "README.md").read_text(encoding="utf-8")
for d in sorted((root / "docs").glob("*.md")):
    if f"docs/{d.name}" not in readme: err(f"README does not list docs/{d.name}")
for p in ("CLAUDE_PROMPT.md", "AGENT_PROMPT_OPENCODE.md", "DEBUG_REFINE_PROMPT.md", "POLISH_UPGRADE_PROMPT.md", "AGENTS.md"):
    if not (root / p).exists(): err(f"missing {p}")

# 5. No decompiled Snaptube source
bad = [r"package com\.snaptube", r"JADX INFO", r"^import (android|java|o)\.", r"public (final )?class [A-Za-z]+ (extends|implements)"]
for f in root.rglob("*"):
    if f.is_file() and f.suffix in {".md", ".json", ".yml", ".py"} and f.name != "verify_handoff.py":
        t = f.read_text(encoding="utf-8")
        for b in bad:
            if re.search(b, t, re.M): err(f"possible decompiled code in {f.relative_to(root)} (pattern {b})")
notes.append("decompiled-code scan done")

print("\n".join(notes))
if errors:
    print("\nFAILED:"); print("\n".join(" - " + e for e in errors)); sys.exit(1)
print("\nALL CHECKS PASSED")
