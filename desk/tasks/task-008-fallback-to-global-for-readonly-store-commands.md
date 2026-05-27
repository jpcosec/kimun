---
id: task-008
domain: cli/store/ux
status: open
priority: p1
depends_on: []
created: "2026-05-27"
---

# Fall back to global store for read-only commands from uninitialized locations

## Objective

Make SLDB more usable from uninitialized folders by automatically falling back to the global store for read-only store commands, while still requiring explicit local initialization for commands that create or mutate store-backed state.

## Reference

- `src/sldb/cli/utils.py`
- `src/sldb/store/resolver.py`
- `src/sldb/cli/commands/models.py`
- `src/sldb/cli/commands/docs.py`
- `src/sldb/cli/commands/store.py`
- `src/sldb/cli/commands/help.py`

## What To Fix

Today, store-based commands run from an uninitialized location fail even when a global `~/.sldb/` exists.

That is too strict for read-only discovery flows such as listing or inspection, but still correct for commands that create or mutate tracked state.

## How To Do It

Define two store-resolution modes:

- read-only store commands can fall back from missing local `.sldb/` to global `~/.sldb/`
- write/create/mutate commands must still fail and require explicit local initialization

When the CLI falls back to the global store for a read-only command, print a warning that makes the scope switch explicit.

When the command would create or mutate local state, fail with a clear message explaining that the repo needs `sldb stores init --path .` first.

## Validation

- verify that read-only commands such as `models list`, future `docs list`, and future `stores list` fall back to global scope with a warning
- verify that create/mutate commands still fail from uninitialized locations
- verify that the warning and error messages explain local versus global scope clearly
- `pytest`

## Done When

From an uninitialized folder, read-only store discovery commands use the global store with an explicit warning, while create/mutate commands fail with a clear local-initialization requirement.
