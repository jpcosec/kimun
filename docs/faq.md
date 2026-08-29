# Workspace FAQ

## What is this worktree for?

Direction (docs/v2), knowledge base (desk/atoms) and first-slice implementation of SLDB v2.

## What is intentionally out of scope here?

- implementation before the zero-context audit gate is clean
- v1-parity claims (v2 follows the Git-model roadmap in docs/v2/02 §9)
- surfaces beyond the first slice (Markdown/CST, M/G engines, embeddings, UI)

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

- `sldb` = data/document layer
- `deskops` = workflow harness
