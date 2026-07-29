---
# routine-xxx
id: routine-task-implement-ast
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-implement-ast-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-implement-ast-execution-ready
- operator-task-implement-ast-activate
- checklist-task-implement-ast-testing-ready
- operator-task-implement-ast-ready-for-testing
- checklist-task-implement-ast-closeout-ready
- operator-task-implement-ast-close
# Edge identifiers composing the graph
edges:
- edge-task-implement-ast-execution-to-activate
- edge-task-implement-ast-activate-to-testing
- edge-task-implement-ast-testing-to-ready
- edge-task-implement-ast-ready-to-closeout
- edge-task-implement-ast-closeout-to-close
- edge-task-implement-ast-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
---

# Routine for Implement Ast

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Implement Ast.
