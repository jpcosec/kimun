---
id: task-002
domain: opsys/docs/ux
status: open
priority: p1
depends_on: []
created: "2026-05-24"
---

# Export the local opsys reporting and UX CLI testing guides into opsys

## Objective

Move the locally drafted generic `opsys` guides out of this repo and into the real `opsys` product surface without losing their portability or the design direction they are meant to support.

## Reference

- `docs/opsys/how_to_report/README.md`
- `docs/opsys/how_to_test_ux_cli/README.md`
- `desk/issues/ux-issue-cli-model-discovery-and-global-store.md`

## What To Fix

The guides now exist only as local drafts inside `sldb`.

That is useful for immediate work, but they should become canonical `opsys` product artifacts rather than remain stranded as local copies.

The intended downstream operating layer should eventually work directly over repo-local `desk/tasks`, but that handoff is not ready yet in the current ecosystem state.

## How To Do It

Create matching product surfaces in `opsys`, move the guides there, and adapt them to the target information architecture.

Until `opsys` can operate directly over `sldb`'s `desk/tasks`, handle the handoff manually and record readiness gaps in the downstream inbox instead of forcing workflow assumptions back into SLDB.

Preserve the current conceptual direction:

- a primitive iterable operational class
- routines as iterable instructions
- rituals as routines with hooks
- hooks as reusable conditional triggers
- checklists as iterable sets of conditionals/checks

The exported guides should fit that model rather than remain one-off prose docs.

## Validation

- confirm the target `opsys` location and naming
- copy or adapt both guides into `opsys`
- link the exported docs to the relevant operational primitives once they exist
- update this repo to reference the canonical `opsys` location
- confirm the current manual fallback and capture the downstream readiness gap explicitly

## Done When

The reporting guide and UX CLI testing guide live in `opsys` as canonical product artifacts, this repo stops being their long-term home, and any remaining direct-operation gap over `desk/tasks` is explicitly tracked downstream.
