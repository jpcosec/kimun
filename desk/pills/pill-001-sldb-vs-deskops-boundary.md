---
id: pill-001-sldb-vs-deskops-boundary
tags:
- system:sldb
- system:deskops
- topic:boundary
- workspace:desk
---

# Keep SLDB generic and move workflow behavior toward deskops

## What

SLDB should own structured Markdown contracts, stores, validation, and generic navigation/query surfaces. Reusable desk workflow semantics should be owned by `deskops`.

## Why

If workflow routing, inbox lifecycle, pill semantics, and close-loop automation harden into SLDB, the document substrate becomes repo-specific and drifts from its reusable role.

## When

Apply this pill when a change touches local desk surfaces, inbox behavior, task lifecycle semantics, or cross-repo workflow coordination.

## Where

- `desk/`
- `.sldb/` desk workflow registrations
- `src/sldb/cli/commands/inbox.py`
- architecture docs that describe SLDB vs deskops ownership

## How

Keep SLDB text-first and generic. Use local desk docs only for SLDB-owned project state, and route reusable workflow behavior, models, and rituals toward `deskops`.

## How Not

Do not encode desk-specific lifecycle rules, inbox staging semantics, or operational orchestration as core SLDB behavior when the owning runtime should be `deskops`.
