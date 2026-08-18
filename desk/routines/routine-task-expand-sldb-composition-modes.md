---
# routine-xxx
id: routine-task-expand-sldb-composition-modes
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-expand-sldb-composition-modes-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-expand-sldb-composition-modes-execution-ready
- operator-task-expand-sldb-composition-modes-activate
- checklist-task-expand-sldb-composition-modes-testing-ready
- operator-task-expand-sldb-composition-modes-ready-for-testing
- checklist-task-expand-sldb-composition-modes-closeout-ready
- operator-task-expand-sldb-composition-modes-close
# Edge identifiers composing the graph
edges:
- edge-task-expand-sldb-composition-modes-execution-to-activate
- edge-task-expand-sldb-composition-modes-activate-to-testing
- edge-task-expand-sldb-composition-modes-testing-to-ready
- edge-task-expand-sldb-composition-modes-ready-to-closeout
- edge-task-expand-sldb-composition-modes-closeout-to-close
- edge-task-expand-sldb-composition-modes-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
---

# Routine for Expand SLDB composition modes

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Expand SLDB composition modes.
