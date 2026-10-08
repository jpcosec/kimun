# Workspace FAQ

## What is this repository for?

Direction (`docs/v2/`), the knowledge base (`desk/atoms/`) and the implementation of
`kimun`: the SLDB v2 kernel plus the product surface (CLI, models, indexes, packaging).
Current status is in `docs/v2/05-estado.md`; pending items are in
`docs/v2/12-ecosistema-y-pendientes.md`.

## What is intentionally out of scope?

- The anchored evaluator (S4) and derived writes (S5).
- Embeddings, M/G semantics as engines, and UI (Node parity stays in the drawer).
- v1 parity as a 1:1 port. Parity with sldb v1 is closed and measured in
  `docs/v2/tests/parity-v1.md`; decision 18 keeps v1 in v1 and lets kimun stores be
  regenerated (`docs/v2/05-estado.md §3`).

## How does an agent work and close a task?

See `AGENTS.md`. Implementation progress requires test evidence under `runs/subagents/` and
a commit.

## How should "desk" and deskops be interpreted here?

- `kimun` is the data/document layer; `sldb` v1 and `kgdb` are frozen and superseded by it.
- The desk is a kimun store at `.kimun/`, built by `bin/kimun include build` (alias
  `bb desk`) from `kimun.include.edn` and `desk/models/*.edn`, and managed with kimun
  commands. The deskops CLI is not used in this repository.
