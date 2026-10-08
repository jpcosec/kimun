# AGENTS

How an agent works in this repository.

## Reading order

1. `AGENTS.md` (this file)
2. `README.md`
3. `docs/v2/README.md` — the authority table and reading order for the direction docs
4. `docs/v2/05-estado.md` — current status and decisions
5. `docs/faq.md` — scope and what closes a task
6. `desk/tasks/Board.md` — active work (legacy format, not editable through the CLI)

Then the documents the task references.

## Layers and the desk

- `kimun` is the data/document layer: a content-addressed store plus the Markdown, models
  and query surfaces. `sldb` v1 and `kgdb` are frozen and superseded by it.
- The desk is a kimun store at `.kimun/`, never committed. Build it with
  `bin/kimun include build` (alias `bb desk`) from `kimun.include.edn` and
  `desk/models/*.edn`.
- Manage the desk with kimun commands (`docs`, `fields`, `find`, `journal`), not with the
  deskops CLI.
- Work is executed by herdr pi workers.

## Validation

- `bb lint` — dependency rings and docstrings.
- `bb test` — the suite (runs `lint` first).
- `bb oracle` — independent Python check of canonical bytes.
- Implementation progress may only be claimed with test evidence under `runs/subagents/`
  and a commit.

## Commits

- One task, one commit; the message says what changed and why.
- Add the trailer used across this repository:
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Closeout requires the passing validation output in the run directory.
