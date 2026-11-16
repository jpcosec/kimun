---
sldb:
  by: agent
  at: '2026-11-16T17:57:19Z'
  authority: generated
  via: save_document_payload
# board-xxx
id: board-001
# Affected workspace or domain
scope: desk
# List of task-xxx paths
tasks:
- desk/tasks/task-deskops-views-as-declare-generate-recipes.md
- desk/tasks/task-fichas-and-graph-queries-over-propositions.md
- desk/tasks/task-finish-wip-leer-readers-by-class.md
- desk/tasks/task-first-release-ci-run-pod-published-version-tagged.md
- desk/tasks/task-generate-leftovers-http-over-propositions-and-orphan-files.md
- desk/tasks/task-open-owner-decisions-that-hold-work.md
- desk/tasks/task-owner-decides-what-new-core-of-sldb-means.md
- desk/tasks/task-readers-for-every-class-of-the-deskops-desk.md
- desk/tasks/task-retire-the-previous-store-once-declarations-replace-it.md
- desk/tasks/task-tags-on-any-node-code-nodes-and-kimun-indexing-itself.md
- desk/tasks/task-toolchain-on-robocop2-and-suite-reproduced.md
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

- Deskops views as declare generate recipes [draft] - The Board and the view of one task of the deskops desk come out of declare generate.
- Fichas and graph queries over propositions [draft] - Terms with content and no anchor; graph.* queries the propositions; declare about accepts a proposition as term.
- Finish wip-leer readers by class [draft] - Branch wip/leer is rebased on origin/main, its tests and the whole suite pass, the readers have been run over a copy of the real deskops desk with the numbers recorded, and main is fast-forwarded to it and pushed.
- First release CI run pod published version tagged [draft] - An installable, tagged kimun.
- Generate leftovers HTTP over propositions and orphan files [draft] - HTTP serves the propositions; a generated file whose declaration disappeared is detected; typed docstring as materialization of a class.
- Open owner decisions that hold work [draft] - Each of the four rows of docs/v2/drafts/preguntas-abiertas.md carries the owner's answer, verbatim and dated, at the start of its second cell, prefixed by **Respondida (dueño, YYYY-MM-DD):** as the answered rows of that table already do; a question he declines is prefixed by **Sin decidir (dueño, YYYY-MM-DD):**.
- Owner decides what new core of sldb means [draft] - The owner's answer, in his own words, is appended as a dated section to docs/v2/drafts/sesion-2026-11-15.md (the file that holds his verbatim words), and docs/v2/drafts/diseno-leer.md §6 is rewritten from 'Mi lectura, sin confirmar' to what he said.
- Readers for every class of the deskops desk [draft] - Declarations under kimun/ of a copy of deskops that read every class of the desk into propositions.
- Retire the previous store once declarations replace it [draft] - Path tree, per-file fingerprint, revisions, log, plans, replay and include's identity-registry are gone, with their tests.
- Tags on any node, code nodes and kimun indexing itself [draft] - Several tags per node on several axes; code nodes; kimun's own code graph comes from kimun and replaces scripts/check_rings.clj.
- Toolchain on robocop2 and suite reproduced [draft] - On the machine robocop2 (ssh host 'robocop2', user robocop), in the clone ~/proyectos/kimun of https://github.com/jpcosec/kimun (branch main), 'bb test' runs with the tree-sitter pod and has no failures, with at least the 1081 tests of the baseline (runs/subagents/2026-11-16-find-genericos/coordinator/full-test.txt, commit e84869af).
