---
# routine-xxx
id: routine-task-design-sldb-ast-query-primitives
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-design-sldb-ast-query-primitives-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-design-sldb-ast-query-primitives-execution-ready
- operator-task-design-sldb-ast-query-primitives-activate
- checklist-task-design-sldb-ast-query-primitives-testing-ready
- operator-task-design-sldb-ast-query-primitives-ready-for-testing
- checklist-task-design-sldb-ast-query-primitives-closeout-ready
- operator-task-design-sldb-ast-query-primitives-close
# Edge identifiers composing the graph
edges:
- edge-task-design-sldb-ast-query-primitives-execution-to-activate
- edge-task-design-sldb-ast-query-primitives-activate-to-testing
- edge-task-design-sldb-ast-query-primitives-testing-to-ready
- edge-task-design-sldb-ast-query-primitives-ready-to-closeout
- edge-task-design-sldb-ast-query-primitives-closeout-to-close
- edge-task-design-sldb-ast-query-primitives-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
---

# Routine for Design SLDB AST query primitives

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Design SLDB AST query primitives.
