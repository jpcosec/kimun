---
# routine-xxx
id: routine-task-implement-types
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-implement-types-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-implement-types-execution-ready
- operator-task-implement-types-activate
- checklist-task-implement-types-testing-ready
- operator-task-implement-types-ready-for-testing
- checklist-task-implement-types-closeout-ready
- operator-task-implement-types-close
# Edge identifiers composing the graph
edges:
- edge-task-implement-types-execution-to-activate
- edge-task-implement-types-activate-to-testing
- edge-task-implement-types-testing-to-ready
- edge-task-implement-types-ready-to-closeout
- edge-task-implement-types-closeout-to-close
- edge-task-implement-types-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
---

# Routine for Implement Types

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Implement Types.
