# AGENTS

## Workspace mode

- This worktree holds v2 direction and, once tasks are promoted, first-slice implementation.
- `deskops` governs workflow through `desk/` artifacts and CLI state.
- `sldb` is the data/document layer.
- Do not claim implementation progress from this worktree.

## Mandatory recovery order

1. `AGENTS.md`
2. `README.md`
3. `docs/v2/01-orden-filosofico.md`
4. `docs/v2/02-sustrato-computacional.md`
5. `docs/faq.md`
6. `desk/tasks/Board.md`
7. task-bound pills in `desk/contexts/`
8. `desk/rituals/phase.md`
9. `desk/rituals/execution.md`
10. `desk/rituals/testing.md`
11. `desk/rituals/closeout.md`
12. referenced `desk/atoms/`

## Planning-task rule

- Each planning task must produce exactly one named contract artifact.
- The contract artifact must cite governing atoms and target architecture docs.
- Validation is attestation against task scope and explicit non-goals.
- Closeout requires task metadata, board notes, and artifact paths to agree.
