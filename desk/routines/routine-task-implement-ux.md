---
# routine-xxx
id: routine-task-implement-ux
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-implement-ux-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-implement-ux-execution-ready
- operator-task-implement-ux-activate
- checklist-task-implement-ux-testing-ready
- operator-task-implement-ux-ready-for-testing
- checklist-task-implement-ux-closeout-ready
- operator-task-implement-ux-close
# Edge identifiers composing the graph
edges:
- edge-task-implement-ux-execution-to-activate
- edge-task-implement-ux-activate-to-testing
- edge-task-implement-ux-testing-to-ready
- edge-task-implement-ux-ready-to-closeout
- edge-task-implement-ux-closeout-to-close
- edge-task-implement-ux-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
---

# Routine for Implement Ux

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Implement Ux.
