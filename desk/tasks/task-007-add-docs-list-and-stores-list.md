---
id: task-007
domain: cli/store/ux
status: open
priority: p1
depends_on: []
created: "2026-05-27"
---

# Add first-class docs list and stores list discovery surfaces

## Objective

Extend the discovery improvements beyond `models list` so first-use and partial-knowledge users can also enumerate tracked documents and visible store scope without dropping into the AST or raw store files.

## Reference

- `src/sldb/cli/parser.py`
- `src/sldb/cli/commands/docs.py`
- `src/sldb/cli/commands/stores.py`
- `src/sldb/cli/commands/store.py`
- `src/sldb/cli/commands/help.py`
- `docs/architecture/current-cli-tree.md`

## What To Fix

The CLI now has `models list`, but the same discovery gap still exists for:

- tracked docs already registered in one store
- visible store scope such as local, global, and linked/federated stores

Today users can recover that information indirectly through AST or index inspection, but there is still no obvious first-class listing surface.

## How To Do It

Add:

- `sldb docs list`
- `sldb stores list`

Keep both surfaces shallow and operationally clear.

`docs list` should answer "what tracked docs exist here?" without forcing the user into graph inspection.

`stores list` should answer "what store scope is visible from here?" without creating ambiguity between local, global, and linked stores.

Update help/docs so the new discovery path appears in the first-use layer, not only in deep reference docs.

## Validation

- `python -m sldb docs -h`
- `python -m sldb stores -h`
- `python -m sldb help`
- verify that tracked docs can be enumerated without knowing an exact name
- verify that visible store scope is explained clearly from initialized and uninitialized locations
- `pytest`

## Done When

The CLI has obvious list surfaces for models, docs, and stores, and a first-use user can discover registered docs and visible store scope without falling back to AST or source inspection.
