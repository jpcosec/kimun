---
sldb:
  by: agent
  at: '2026-11-16T23:42:09Z'
  authority: generated
  via: save_document_payload
# board-xxx
id: board-001
# Affected workspace or domain
scope: desk
# List of task-xxx paths
tasks:
- desk/tasks/task-finish-wip-leer-readers-by-class.md
- desk/tasks/task-open-owner-decisions-that-hold-work.md
- desk/tasks/task-owner-decides-what-new-core-of-sldb-means.md
# List of pill-xxx paths
pills: []
# List of ritual-xxx paths
rituals: []
# e.g., system:sldb, workspace:desk
tags:
- workspace:desk
---

# sldb-refactor-worktree Board

## Purpose

_Explain what this board routes and why it exists._

Legacy board (`docs/v2/12 §1`: `desk/tasks/Board.md` is not editable by the CLI). It routes the
seven tasks promoted from the deskops drawer; keep it in sync by hand until the desk migrates to a
modeled artifact. The planning-era pills and rituals are in `docs/archive/desk/`.

## Notes

_Add short operational notes about the current routed set._

- External oracle for canonical-bytes [closed] - independent canonical-bytes oracle (`scripts/canon_oracle.py`, `bb oracle`).
- Rewrite the pre-v2 spec2viz specs [closed] - pre-v2 `target-*.yml` retired to `docs/archive/architecture/spec2viz/`.
- Contention test for heads commit! [active] - CAS retry and ConflictSet under concurrent writers (`kimun.kernel.heads`).
- ULID monotonicity and clock adapter [active] - time behind the Clock port in `kimun.kernel.ports`.
- Unreachable objects report and GC groundwork [active] - reshaped by the stable store (`docs/v2/12 §1` (Kernel y store, puntos 1 y 6)).
- Node host parity for the v2 kernel [active] - deferred by decision 14 (drawer/UI); `kimun.host.*` if retaken.
- Test coverage and lint tooling for Babashka [active] - lint done (`bb lint`); coverage and JVM run pending.

## Task Details

_Generated from the task references above._

- Finish wip-leer readers by class [ready_for_testing] - Branch wip/leer is rebased on origin/main, its tests and the whole suite pass, the readers have been run over a copy of the real deskops desk with the numbers recorded, and main is fast-forwarded to it and pushed.
- Open owner decisions that hold work [draft] - Each of the four rows of docs/v2/drafts/preguntas-abiertas.md carries the owner's answer, verbatim and dated, at the start of its second cell, prefixed by **Respondida (dueño, YYYY-MM-DD):** as the answered rows of that table already do; a question he declines is prefixed by **Sin decidir (dueño, YYYY-MM-DD):**.
- Owner decides what new core of sldb means [draft] - The owner's answer, in his own words, is appended as a dated section to docs/v2/drafts/sesion-2026-11-15.md (the file that holds his verbatim words), and the paragraph of docs/v2/drafts/diseno-leer.md §6 that starts «Mi lectura, sin confirmar por el dueño» is replaced by what he said.
