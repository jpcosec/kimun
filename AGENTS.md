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

## Resuming work (read this first)

The last design session (2026-10-08/09) changed the direction. Before touching code:

1. `docs/v2/drafts/sesion-2026-10-08-09.md` — what was decided and why, in order.
2. `docs/v2/drafts/preguntas-abiertas.md` — questions only the owner can answer. Do not implement
   anything that depends on an unanswered question (Q1–Q3 have no default).
3. `docs/v2/14-juego-del-lenguaje.md` — start with its "cómo leer" box: §18–§21 are the current
   principles (classes and instances, anchoring vs generation, cache / stable store / graph,
   tracked vs trackable, hash ≠ identity).
4. `docs/v2/12-ecosistema-y-pendientes.md §1` — the prioritised backlog.

Branches: `main` is the only line of work. `close/spec15` (G-layer draft, written before the owner's
correction in `drafts/capa-g-dudas.md`) and `close/A4` (packfiles, paused) are references, not
pending merges. Evidence of the session: `runs/research/2026-10-08/`.

## Workers and memory

- Run heavy work through herdr pi workers (`/home/jp/setup/herdr/coordination.sh`, provider
  `deepseek`, model `deepseek-flash`); briefs live in `~/.cache/coord-briefs/` and anything worth
  keeping is copied to `runs/`.
- Every `bb`/script call that may grow goes through `memcap` (`~/bin/memcap [GB] cmd…`, default
  3 GB). Two OOMs on 2026-10-08 took down the desktop; process large corpora one item at a time.

## Layers and the desk

- `kimun` is the data/document layer: a stable store (anchors and hashes of the environment;
  format 4 keeps document trees in a recomputable cache) plus the Markdown, models and query
  surfaces. The graph (models, G layer) is meant to become a separate package (`12 §1`). `sldb` v1 and `kgdb` are frozen and superseded by it.
- The desk is a kimun store at `.kimun/`, never committed. Build it with
  `bin/kimun include build` (alias `bb desk`) from `kimun.include.edn`. The desk models
  (`desk/models/*.edn`) were retired on 2026-10-09 (`docs/archive/desk/README.md`): the include
  declares no models or documents until spec 16 regenerates them (`12 §1`, Funciones/Modelos).
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
