---
# board-xxx
id: board-001
# Affected workspace or domain
scope: desk
# List of task-xxx paths
tasks:
- desk/tasks/task-external-oracle-for-canonical-bytes.md
- desk/tasks/task-contention-test-for-heads-commit.md
- desk/tasks/task-ulid-monotonicity-and-clock-adapter.md
- desk/tasks/task-unreachable-objects-report-and-gc-groundwork.md
- desk/tasks/task-rewrite-the-pre-v2-spec2viz-specs.md
- desk/tasks/task-node-host-parity-for-the-v2-kernel.md
- desk/tasks/task-test-coverage-and-lint-tooling-for-babashka.md
- desk/tasks/task-toolchain-on-robocop2-and-suite-reproduced.md
- desk/tasks/task-finish-wip-leer-readers-by-class.md
- desk/tasks/task-readers-for-every-class-of-the-deskops-desk.md
- desk/tasks/task-compare-declare-about-with-the-sldb-edge-index-on-the-desk.md
- desk/tasks/task-cache-of-readings-by-git-blob-hash.md
- desk/tasks/task-deskops-views-as-declare-generate-recipes.md
- desk/tasks/task-owner-decides-what-new-core-of-sldb-means.md
- desk/tasks/task-design-kimun-as-the-core-of-sldb.md
- desk/tasks/task-retire-the-previous-store-once-declarations-replace-it.md
- desk/tasks/task-tags-on-any-node-code-nodes-and-kimun-indexing-itself.md
- desk/tasks/task-fichas-and-graph-queries-over-propositions.md
- desk/tasks/task-generate-leftovers-http-over-propositions-and-orphan-files.md
- desk/tasks/task-first-release-ci-run-pod-published-version-tagged.md
- desk/tasks/task-declared-debt-of-the-new-path.md
- desk/tasks/task-open-owner-decisions-that-hold-work.md
# List of pill-xxx paths
pills:
- desk/contexts/pill-guardrail-v2-implementation-gate.md
# List of ritual-xxx paths
rituals:
- desk/rituals/ritual-zero-context-audit-gate.md
# e.g., system:sldb, workspace:desk
tags:
- workspace:desk
- system:sldb
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

- Toolchain on robocop2 and suite reproduced [draft] - bb test with the tree-sitter pod gives the same numbers as the last chore(evidence) commit of main.
- Finish wip-leer readers by class [draft] - Branch wip/leer validated and fast-forwarded into main, with numbers measured on a copy of the real deskops desk.
- Readers for every class of the deskops desk [draft] - Declarations under kimun/ of a copy of deskops that read every class of the desk into propositions.
- Compare declare about with the sldb edge index on the desk [draft] - A measured comparison of the relations kimun reads from the desk against sldb's edge index.
- Cache of readings by git blob hash [draft] - Readings of an unchanged file are not recomputed, if the measurement of the first cut asks for it.
- Deskops views as declare generate recipes [draft] - The Board and the view of one task of the deskops desk come out of declare generate.
- Owner decides what new core of sldb means [draft] - The owner's answer, verbatim, in docs/v2/drafts/.
- Design kimun as the core of sldb [draft] - A design written by the coordinator, from the owner's answer and the measured terrain.
- Retire the previous store once declarations replace it [draft] - Path tree, per-file fingerprint, revisions, log, plans, replay and include's identity-registry are gone, with their tests.
- Tags on any node, code nodes and kimun indexing itself [draft] - Several tags per node on several axes; code nodes; kimun's own code graph comes from kimun and replaces scripts/check_rings.clj.
- Fichas and graph queries over propositions [draft] - Terms with content and no anchor; graph.* queries the propositions; declare about accepts a proposition as term.
- Generate leftovers HTTP over propositions and orphan files [draft] - HTTP serves the propositions; a generated file whose declaration disappeared is detected; typed docstring as materialization of a class.
- First release CI run pod published version tagged [draft] - An installable, tagged kimun.
- Declared debt of the new path [draft] - Each limit closed or explicitly kept.
- Open owner decisions that hold work [draft] - The owner's answer to each, recorded in docs/v2/drafts/preguntas-abiertas.md.
