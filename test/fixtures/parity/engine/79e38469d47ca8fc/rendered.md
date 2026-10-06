---
# unclear | suggestion
kind: suggestion
# e.g., other_repo
sender_project: deskops
# e.g., target_repo
# ISO 8601 timestamp
created_at: '2026-06-14T00:00:03'
# open | closed
status: open
# project identity that acknowledged the note
# ISO 8601 timestamp, set when acknowledged
---

# Add model registration query API

_Describe the incoming message with enough evidence to triage._

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