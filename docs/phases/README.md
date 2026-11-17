# Phase specs

Copy this template into `docs/phases/<id>.md` **before** starting a phase, then fill it from the one phase section in
`docs/ROADMAP.md` and from `docs/STATE.md`. Keep it short; it is the contract for one `/next` loop.

```markdown
# Phase <id>: <name>

## Goal
One sentence, in user terms.

## Inputs (max 5 files)
- `AGENTS.md`, `docs/STATE.md`
- <the one ROADMAP phase section>
- <up to 2 source or contract files this phase actually needs>

## Do not touch
- <files/areas that belong to another phase>
- No new permission, no runtime code download, no cleartext HTTP, no analytics.

## Steps
1. <smallest testable increment>
2. <next increment>
3. ...

## Tests
- Unit/fixture tests with `FakeEngine`; no live-site test is a required check.
- `./gradlew testDebugUnitTest lintDebug`; quote the real BUILD and test/lint counts.

## Exit gate (objective)
- <what must be true to call the phase done; device vs host stated explicitly>

## Owner-only checks
- <what automation cannot prove, if anything>

## Stop point
- Stop here and wait for the owner before the next phase.
- Deferred device checks are never counted as passed.
```

Rules: one phase per owner request; commit each verified increment; update `docs/STATE.md` and append at most 10 lines
to `docs/PROGRESS.md`; never claim an unrun build, test or device result.
