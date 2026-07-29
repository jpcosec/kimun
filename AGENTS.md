# AGENTS

## Workspace mode

- This worktree is planning-only.
- `deskops` governs workflow through `desk/` artifacts and CLI state.
- `sldb` is the data/document layer.
- Do not claim implementation progress from this worktree.

## Mandatory recovery order

1. `AGENTS.md`
2. `README.md`
3. `docs/faq.md`
4. `desk/tasks/Board.md`
5. task-bound pills in `desk/contexts/`
6. `desk/rituals/phase.md`
7. `desk/rituals/execution.md`
8. `desk/rituals/testing.md`
9. `desk/rituals/closeout.md`
10. referenced `desk/atoms/`

## Planning-task rule

- Each planning task must produce exactly one named contract artifact.
- The contract artifact must cite governing atoms and target architecture docs.
- Validation is attestation against task scope and explicit non-goals.
- Closeout requires task metadata, board notes, and artifact paths to agree.
