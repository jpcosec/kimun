# Workspace FAQ

## What is this worktree for?

Direction (docs/v2), knowledge base (desk/atoms) and implementation of `knowledge`: the SLDB v2 kernel plus the product surface (CLI, models, indexes, evaluator, packaging) built on it along the "pista S" milestones (docs/v2/05 §Pista S).

## What is intentionally out of scope here?

- implementation before the zero-context audit gate is clean
- v1 parity as a 1:1 port. The parity **floor** (store core, direct extract/render/validate, find/--where/addresses/--global, links/transclusion/predicates, serve, graph export/query) is in scope through the pista S milestones (docs/v2/06); faq/explore/inbox/ast/lint/legacy are dropped
- M/G engines, embeddings and UI (kernel milestones 7–9 run in parallel; Node parity stays in the drawer)

## What closes an implementation task here?

- the zero-context audit gate for its bundle is clean (last round has no high/medium findings)
- `bb test` passes and its output is saved under `runs/subagents/<run>/validation.log`
- the task's `files:` exist and a commit closes it

## What closed a planning task here? (legacy)

- one contract doc exists
- the doc cites governing atoms/diagrams
- the task `files:` list includes the output doc
- (planning-era tasks were planning-only; all such tasks are closed)

## How should deskops be interpreted here?

- `knowledge` (this repo, formerly `sldb` v2) = data/document layer; `sldb` v1 and `kgdb` are frozen and superseded by it
- `deskops` = workflow harness
