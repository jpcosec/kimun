# Migrate SLDB desk context to deskops

ID: issue-migrate-sldb-desk-context-to-deskops
Status: open

## What

SLDB has repo-local `desk/` context and Python model files that duplicate or overlap with the deskops workflow surface.

## Why

`desk/` should be document data, while workflow implementation and reusable operational context should live in `deskops`. Keeping SLDB-specific desk context here creates drift and repeats the boundary problem fixed in deskops.

## Scope

- Review the remaining local desk context surfaces and `tools/sldb/desk/pills/`.
- Distill durable knowledge into deskops atoms or docs where it belongs.
- Remove or migrate SLDB `desk/` Python code such as `desk/models.py` if it is still active.
- Leave SLDB with only project docs that are truly owned by SLDB.

## Validation

- SLDB tests still pass.
- No required SLDB workflow context is lost.
- `deskops` remains the owner of workflow operations and desk document models.

## Tags

- system:sldb
- system:deskops
- topic:boundary
