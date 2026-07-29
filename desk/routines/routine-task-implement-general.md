---
# routine-xxx
id: routine-task-implement-general
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-implement-general-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-implement-general-execution-ready
- operator-task-implement-general-activate
- checklist-task-implement-general-testing-ready
- operator-task-implement-general-ready-for-testing
- checklist-task-implement-general-closeout-ready
- operator-task-implement-general-close
# Edge identifiers composing the graph
edges:
- edge-task-implement-general-execution-to-activate
- edge-task-implement-general-activate-to-testing
- edge-task-implement-general-testing-to-ready
- edge-task-implement-general-ready-to-closeout
- edge-task-implement-general-closeout-to-close
- edge-task-implement-general-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
---

# Routine for Implement General

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Implement General.
