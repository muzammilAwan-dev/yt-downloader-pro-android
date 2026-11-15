---
description: Read-only review of the latest change against the project rules
agent: reviewer
---
Review the most recent change: `git diff HEAD~1` (or `git diff` if the working tree has uncommitted edits). If I pass a revision range, use it: $ARGUMENTS

Check the Never rules in `AGENTS.md`, the permission budget, `contracts/errors.json`, `contracts/allowed-options.json`, redaction of cookies/auth headers/signed URLs, GPL-only provenance, and whether each new behavior has a test in the same commit. Return at most 5 findings, most severe first, each with file:line and a one-line fix. Write `none found` if there is nothing real.
