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

- External oracle for canonical-bytes [closed] - `scripts/canon_oracle.py`; `bb oracle` 9/9 canonical ids (`docs/v2/05 §2`).
- Rewrite the pre-v2 spec2viz specs [closed] - `docs/architecture/spec2viz/` declares only `v2-*.yml`.
- Contention test for heads commit! [active] - `test/kimun/kernel/hardening_test.cljc` already lands disjoint trees; extend to same-tree contention.
- ULID monotonicity and clock adapter [active] - reconsidered under `docs/v2/14 §21`.
- Unreachable objects report and GC groundwork [active] - report garbage candidates; ground it on `<store>/cache/`.
- Node host parity for the v2 kernel [active] - not active work (decision 14).
- Test coverage and lint tooling for Babashka [active] - cloverage/bb coverage plus a JVM run.
