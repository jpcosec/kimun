---
id: routine-task-design-sldb-ast-query-primitives
status: active
entrypoint: checklist-task-design-sldb-ast-query-primitives-execution-ready
decomposition:
- checklist-task-design-sldb-ast-query-primitives-execution-ready
- operator-task-design-sldb-ast-query-primitives-activate
- checklist-task-design-sldb-ast-query-primitives-testing-ready
- operator-task-design-sldb-ast-query-primitives-ready-for-testing
- checklist-task-design-sldb-ast-query-primitives-closeout-ready
- operator-task-design-sldb-ast-query-primitives-close
edges:
- edge-task-design-sldb-ast-query-primitives-execution-to-activate
- edge-task-design-sldb-ast-query-primitives-activate-to-testing
- edge-task-design-sldb-ast-query-primitives-testing-to-ready
- edge-task-design-sldb-ast-query-primitives-ready-to-closeout
- edge-task-design-sldb-ast-query-primitives-closeout-to-close
- edge-task-design-sldb-ast-query-primitives-close-to-complete
terminal_nodes:
- complete
tags:
- workspace:desk
- primitive:routine
---

# Routine for Design SLDB AST query primitives

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Design SLDB AST query primitives.
