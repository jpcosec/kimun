---
id: task-migrate-desk-context-to-deskops
domain: desk/boundary
status: blocked
priority: p2
depends_on: []
created: "2026-06-11"
---

# Migrate reusable desk context toward deskops

## Objective

Audit the local SLDB `desk/` surface and move or remove workflow context that belongs to `deskops`, while keeping SLDB-owned project context available in this repo.

## Reference

- Source issue: `desk/issues/issue-migrate-sldb-desk-context-to-deskops.md`
- Context pill: `desk/pills/pill-001-sldb-vs-deskops-boundary.md`
- Cross-store support: SLDB can resolve linked store aliases and use models from linked store namespaces for generic document writes.
- Local governance: `desk/STANDARDS.md`
- Local spec: `desk/SPEC.md`
- Local workflow policy: `desk/METHODOLOGY.md`

## What To Fix

Separate three kinds of material currently under `desk/`:

- SLDB-owned project state that should remain local.
- Reusable workflow semantics that should be owned by `deskops`.
- Legacy or duplicate context that can be removed after the owning source is confirmed.

The local `desk/models.py` definitions for workflow documents are part of the migration target. `DeskTaskDoc`, `DeskPillDoc`, `DeskBoardDoc`, `InboxNoteDoc`, and related desk workflow models should not remain SLDB-owned if `deskops` is the canonical owner of the desk surface semantics.

Treat inbox semantics as cross-project request routing: projects, agents, or teams use inboxes to ask other projects for things. Do not model inbox as the local staging area for unclear notes or task candidates; that role belongs to drawers.

Do not move generic SLDB infrastructure or product docs out of this repo.

## How To Do It

Review `desk/contexts/`, `desk/pills/`, and `desk/models.py` against the boundary pill.

For each candidate, decide whether it is local project state, reusable workflow behavior, or redundant legacy context.

Check whether `deskops` already provides canonical importable models for the local desk documents. If it does, update SLDB's store/model registration to reference those models instead of `desk.models:*`. If it does not, migrate or create the canonical models in `deskops` first, then update this repo to consume them.

Audit any local docs or models that describe `inbox` as an unclear-note/task-candidate staging area. Either migrate that behavior to drawers or update the wording to match the `deskops` inbox contract.

Coordinate any actual migration with the `deskops` repo surface before deleting local material.

Keep changes small and preserve this repo's ability to understand its active work from `desk/tasks/Board.md`.

## Validation

- SLDB tests still pass.
- Required local desk context is still available from this repo.
- Any migrated reusable workflow context has a clear owner in `deskops`.
- `.sldb` model registrations for desk workflow documents point at the canonical `deskops` models, or the task records why that cannot happen yet.
- The board and affected desk docs are updated after material changes.

## Current Blocker

- `deskops` exists at `/home/jp/proyectos/hum-ecosystem/tools/deskops`, but its worktree is heavily dirty with many modified and untracked workflow model/materialization files. Those changes appear to be active user or other-agent work and should not be overwritten here.
- Importable `deskops.models:TaskDoc`, `BoardDoc`, and `PillDoc` exist, but their schemas/templates are not compatible with the current SLDB local desk documents (`DeskTaskDoc`, `DeskBoardDoc`, `DeskPillDoc`). Directly rewriting SLDB's `.sldb` registrations would break validation for existing local documents.
- `deskops.models:InboxNoteDoc` currently still represents unclear/suggestion notes, so it does not yet encode the desired cross-project request-routing inbox contract.

## Partial Boundary Cleanup

- Updated SLDB local desk wording so local unclear points and task candidates belong to `drawers`, not `inbox`.
- Reserved `inbox` wording for `deskops`-owned cross-project request routing.
- Left local `desk/models.py` workflow shims in place until canonical compatible `deskops` models are available and the store can import them safely.

## Done When

- Reusable desk workflow context is no longer duplicated as SLDB-owned behavior.
- SLDB no longer owns canonical desk workflow models in `desk/models.py`, unless a deliberately scoped local shim remains with documented justification.
- SLDB retains only local project state and SLDB-specific context.
- Any removed local context has either been migrated, superseded, or proven redundant.
