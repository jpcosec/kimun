---
kind: suggestion
sender_project: deskops
created_at: 2026-06-14T00:00:03
status: open
---

# Add model registration query API

Deskops needs to check whether all expected models are registered in a store, to detect missing registrations during health checks and init.

## Scope

- `sldb models list --store <path>` — list all registered models with name and module path
- `sldb models check <model_ref> --store <path>` — exit 0 if registered, non-zero with "not found" message
- Python API: `list_registered_models(store_path) -> list[ModelRegistration]` and `is_model_registered(store_path, model_ref) -> bool`
- Support checking multiple models in one command: `sldb models check deskops.models.TaskDoc deskops.models.PillDoc --store <path>`

## Motivation

Deskops health recovery needs to verify model registration without parsing raw store JSON files.

## Existing pills

- `desk/pills/pill-001-sldb-vs-deskops-boundary.md`

## Done When

- `sldb models list` shows registered models with name and path
- `sldb models check` reports each model as registered or missing
- Python API supports both list and single-check queries
