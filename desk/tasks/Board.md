---
# board-xxx
id: board-001
# Affected workspace or domain
scope: desk
# List of task-xxx paths
tasks:
- desk/tasks/task-harden-the-first-slice-test-suite-against-every-spec-promise.md
- desk/tasks/task-define-and-enforce-code-standards-and-dependency-rings.md
- desk/tasks/task-document-the-v2-kernel-with-spec2viz-diagrams.md
# List of pill-xxx paths
pills:
- desk/contexts/pills.md
- desk/contexts/pill-planning-contracts.md
# List of ritual-xxx paths
rituals:
- desk/rituals/execution.md
- desk/rituals/testing.md
- desk/rituals/closeout.md
# e.g., system:sldb, workspace:desk
tags:
- workspace:desk
- system:sldb
---

# sldb-refactor-worktree Board

## Purpose

_Explain what this board routes and why it exists._



## Notes

_Add short operational notes about the current routed set._

- Harden the first-slice test suite against every spec promise [draft] - A traceability table docs/v2/tests/promises.md mapping every normative statement of docs/v2/02 sections 2.1, 3.1, 3.2, 4.1, 5, 5.1, 5.2, 6.1, 8.1 and invariants 1-17 to the test that proves it, with the gaps found by fresh-context tester lanes closed by new tests (negative cases, contention, cross-implementation oracle for canonical-bytes).
- Define and enforce code standards and dependency rings [draft] - docs/v2/03-estandares-de-codigo.md defining rings (0 kernel: only clojure.* and kernel; 1 host adapters: implement kernel ports, only place with reader conditionals; 2 surfaces; 3 tests), docstrings on every public var, pure kernel with explicit host argument, namespaced error types, test standards; kernel ports (protocols) in sldb.kernel.ports with host implementations; a bb task that fails on ring violations or missing docstrings and runs before tests.

## Task Details

_Generated from the task references above._

- Harden the first-slice test suite against every spec promise [draft] - A traceability table docs/v2/tests/promises.md mapping every normative statement of docs/v2/02 sections 2.1, 3.1, 3.2, 4.1, 5, 5.1, 5.2, 6.1, 8.1 and invariants 1-17 to the test that proves it, with the gaps found by fresh-context tester lanes closed by new tests (negative cases, contention, cross-implementation oracle for canonical-bytes).
- Define and enforce code standards and dependency rings [draft] - docs/v2/03-estandares-de-codigo.md defining rings (0 kernel: only clojure.* and kernel; 1 host adapters: implement kernel ports, only place with reader conditionals; 2 surfaces; 3 tests), docstrings on every public var, pure kernel with explicit host argument, namespaced error types, test standards; kernel ports (protocols) in sldb.kernel.ports with host implementations; a bb task that fails on ring violations or missing docstrings and runs before tests.
- Document the v2 kernel with spec2viz diagrams [draft] - spec2viz YAML specs for the v2 kernel (rings and namespaces, CAS object model, transaction flow, store layout, succession/re-anchoring) rendered and cataloged, referenced from docs/v2/README.md.
