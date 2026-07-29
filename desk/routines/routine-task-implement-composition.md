---
# routine-xxx
id: routine-task-implement-composition
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-implement-composition-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-implement-composition-execution-ready
- operator-task-implement-composition-activate
- checklist-task-implement-composition-testing-ready
- operator-task-implement-composition-ready-for-testing
- checklist-task-implement-composition-closeout-ready
- operator-task-implement-composition-close
# Edge identifiers composing the graph
edges:
- edge-task-implement-composition-execution-to-activate
- edge-task-implement-composition-activate-to-testing
- edge-task-implement-composition-testing-to-ready
- edge-task-implement-composition-ready-to-closeout
- edge-task-implement-composition-closeout-to-close
- edge-task-implement-composition-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
---

# Routine for Implement Composition

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Implement Composition.
