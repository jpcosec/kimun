# AGENTS

## Workspace mode

- This worktree holds v2 direction and, once tasks are promoted, first-slice implementation.
- `deskops` governs workflow through `desk/` artifacts and CLI state.
- `sldb` is the data/document layer.
- Implementation progress may only be claimed with test evidence under `runs/subagents/` and a commit.

## Mandatory recovery order

1. `AGENTS.md`
2. `README.md`
3. `docs/v2/05-estado.md` (status, decisions, next step)
4. `docs/v2/01-orden-filosofico.md`
5. `docs/v2/02-sustrato-computacional.md`
6. `docs/faq.md`
7. `desk/tasks/Board.md`
8. task-bound pills in `desk/contexts/`
9. `desk/rituals/phase.md`
10. `desk/rituals/execution.md`
11. `desk/rituals/testing.md`
12. `desk/rituals/closeout.md`
13. referenced `desk/atoms/`

## Implementation-task rule (epoch v2)

- An implementation task enters execution only after the zero-context audit gate (`desk/rituals/ritual-zero-context-audit-gate.md`) is clean; evidence lives under `runs/subagents/`.
- Its `files:`, validation (`bb test`) and Done When must let a blind executor start without questions.
- Closeout requires passing validation output in the run directory and a commit.

## Planning-task rule (legacy, planning-era tasks only)

- Each planning task must produce exactly one named contract artifact.
- The contract artifact must cite governing atoms and target architecture docs.
- Validation is attestation against task scope and explicit non-goals.
- Closeout requires task metadata, board notes, and artifact paths to agree.
