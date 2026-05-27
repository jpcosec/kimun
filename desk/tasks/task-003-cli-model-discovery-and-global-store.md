---
id: task-003
domain: cli/store/ux
status: done
priority: p1
depends_on: []
created: "2026-05-26"
---

# Add a first-class model discovery surface and expose global store resolution

## Objective

Turn the CLI model-discovery gap into an explicit delivery task so users can answer "what models exist here?" without already knowing an exact model name or standing inside an initialized local workspace.

## Reference

- `src/sldb/cli/parser.py`
- `src/sldb/cli/commands/models.py`
- `src/sldb/cli/commands/help.py`
- `src/sldb/store/resolver.py`
- `docs/architecture/current-cli-tree.md`

## What To Fix

The current CLI exposes `sldb models show <model>` but not an obvious listing flow.

Store-resolved commands also fail early when no local `.sldb/` exists, which hides the distinction between local and global/shared store scope during first-use discovery.

## How To Do It

Add a first-class `sldb models list` surface or an equivalent discovery path that is visible from the top-level help and the `models` help.

Make the store-resolution path explain what store was searched, whether a global/shared store exists, and what command the user should run next when no local store is present.

Update the CLI tree and help/docs so the discovery path is documented where first-use users will actually look.

## Validation

- `python -m sldb models -h`
- `python -m sldb help`
- verify that a user can enumerate available models without knowing an exact model name
- verify that missing-local-store flows mention the resolved or expected store scope clearly
- `pytest`

## Done When

The CLI has an obvious model-discovery surface, the help output points to it, and missing-store flows no longer hide the local versus global/shared store distinction.
