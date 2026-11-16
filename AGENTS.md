# AGENTS

How an agent works in this repository.

## Reading order

1. `AGENTS.md` (this file)
2. `README.md`
3. `docs/v2/00-nucleo.md` — the core of kimun in one page: what it is and must not stop being
4. `docs/v2/drafts/sesion-2026-11-15.md` — the owner's words (verbatim) that changed the frame;
   `docs/v2/drafts/diseno-declaraciones.md`, `diseno-evaluar.md` and `diseno-generar.md` — the
   coordinator's design drafts (declare, evaluate, generate): his choices, not owner decisions;
   `docs/v2/drafts/plan-producto.md` — the plan the owner approved on 2026-11-16, by stages, with
   its progress at the end
5. `docs/v2/README.md` — authority table and reading order (the drafts above are the
   current direction even though they are drafts)
6. `docs/v2/05-estado.md` — status and decisions
7. `docs/faq.md` — scope and what closes a task
8. `docs/v2/12-ecosistema-y-pendientes.md §1` — the backlog as a dependency graph; the working
   order is the plan's, and the rows are read through it. There are no priorities: pick work
   whose dependencies are met.
9. `desk/tasks/Board.md` — the board. The pending work is there as `deskops` tasks since
   2026-11-16 (`deskops list tasks`); the thirteen tasks older than that date are in a format
   `deskops` no longer loads and were not migrated

Then the documents the task references. Always cite files by path when reporting to the owner.

## Direction in one paragraph

kimun sees, synthesizes and generates; it asks nothing of the sources, because reality lives
outside it. Declarations are Clojure that runs. Up to the file level git does the monitoring;
inside a file only what is anchored matters. As of 2026-11-16 this exists, with no store:
`declare read|add|anchors|terms|about|members` over the `*.clj` under `kimun/`, the sensor that
asks git (`stores changed|seen`; `sense|health|heartbeat` with `:scope`), and `kimun mcp`.
Generating from the propositions (`declare generate`, `docs/v2/drafts/diseno-generar.md`) entered
on 2026-11-16; reading sources by class (`docs/v2/drafts/diseno-leer.md`) is unfinished, on branch
`wip/leer`; code nodes, the replacement of the old store and the `G` layer do not exist.
**The owner's criterion of done (2026-11-16): kimun is the new core of sldb and ingests an
advanced project such as deskops.** Start from `docs/v2/drafts/traspaso-2026-11-17.md`.
Do not implement anything that depends on an open
decision (`docs/v2/drafts/preguntas-abiertas.md`; today `D-G`, the truth axis of the G layer,
among others). Invariants no work may break: `bb lint` and `bb test` green, `bb oracle`,
`bin/kimun include check` clean. **Parity with sldb v1 is suspended** (owner, 2026-11-15): a
cut that changes behaviour on purpose retires or adjusts the parity test and says so. **Redoing
git is explicitly deferred**: do not keep building the file-level Merkle store or revisions as a
goal of its own (packs, the unreachable inventory, the journal and `docs status` were deleted on
2026-11-16).

Branches: `main` is the only line of work. Close a branch as soon as it is merged or abandoned,
after copying anything worth keeping to `runs/`. Evidence of past sessions is in `runs/research/`.

## Workers and memory

- Run heavy work through herdr pi workers (`/home/jp/setup/herdr/coordination.sh`, provider
  `deepseek`, model `deepseek-flash`); briefs live in `~/.cache/coord-briefs/` and anything worth
  keeping is copied to `runs/`.
- Every `bb`/script call that may grow goes through `memcap` (`~/bin/memcap [GB] cmd…`, default
  3 GB). Two OOMs on 2026-10-08 took down the desktop; process large corpora one item at a time.
- **Several executors in parallel (one worktree each):** only one `bb` at a time on the whole
  machine, so every `bb` or `bin/kimun` call is
  `flock /tmp/kimun-bb.lock env KIMUN_POD=<main checkout>/pods/treesitter/target/release/pod-kimun-treesitter ~/bin/memcap 3 bb …`
  (`KIMUN_POD` points a worktree at the built pod, which lives only in the main checkout).
  Executors run targeted tests, never the whole `bb test`; the coordinator does.
- **A Babashka evaluator of our own must pin its namespace.** Babashka is `sci` inside, and
  `sci/eval-form` evaluates in the process's current namespace: a test can pass alone and fail
  in the suite (`runs/subagents/2026-11-16-evaluar/REPORT.md`, third round). Run the new
  namespaces together with about twenty others before reporting.
- **Do not delete with wildcards in temporary directories.** Make them with `mktemp -d` and
  delete the exact files you created.
- **An executor delivers its report once**, at the end, and stops; no second copy by another channel.
- **Never `git stash`** when worktrees exist: the stash is shared by all of them and two
  executors overwrite each other's changes. To compare against the old code, save a patch and a
  copy of `src/`, `git checkout -- src/`, run, and re-apply.

## Layers and the desk

- `kimun` is the data/document layer: a stable store (anchors and hashes of the environment;
  document trees live in a recomputable cache) plus the Markdown, models and query surfaces.
  The new path (declarations, `declare *`, `mcp`) does not use a store (only `declare anchors` reads the pointer `.kimun/seen.edn` if one is there).
  The graph (models, G layer) is meant to become a separate package (`12 §1`). `sldb` v1 and
  `kgdb` are frozen and superseded by it.
- The desk is a kimun store at `.kimun/`, never committed. Build it with
  `bin/kimun include build` (alias `bb desk`) from `kimun.include.edn`. The include declares no
  models or documents today (`docs/archive/desk/README.md`). The documents SLDB used to
  generate become materializations from models (`docs/v2/drafts/sesion-2026-11-15.md §4`), and
  the desk's source documents are ordinary documents, as in any project that does not use kimun.
- Tasks are created, advanced and closed with the `deskops` CLI (2026-11-16; before that date
  this file said not to use it). A task is dispatchable only with an accepted cold-context review
  (`deskops review preflight <task>`, then `deskops review dispose`; `deskops review status <task>`
  shows it). On 2026-11-16 four of the fifteen tasks had one; the other eleven are rejected drafts. To read the desk, kimun commands (`docs`, `fields`, `find`) also work.
- Work is executed by herdr pi workers.

## Validation

- `bb lint` — dependency rings and docstrings.
- `bb test` — the suite (runs `lint` first).
- `bb oracle` — independent Python check of canonical bytes (needs Python 3).
- Implementation progress may only be claimed with test evidence under `runs/subagents/`
  and a commit.

## Commits

- One task, one commit; the message says what changed and why.
- Add the trailer used across this repository:
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Closeout requires the passing validation output in the run directory.
