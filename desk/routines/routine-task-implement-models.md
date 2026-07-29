---
# routine-xxx
id: routine-task-implement-models
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-implement-models-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-implement-models-execution-ready
- operator-task-implement-models-activate
- checklist-task-implement-models-testing-ready
- operator-task-implement-models-ready-for-testing
- checklist-task-implement-models-closeout-ready
- operator-task-implement-models-close
# Edge identifiers composing the graph
edges:
- edge-task-implement-models-execution-to-activate
- edge-task-implement-models-activate-to-testing
- edge-task-implement-models-testing-to-ready
- edge-task-implement-models-ready-to-closeout
- edge-task-implement-models-closeout-to-close
- edge-task-implement-models-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
---

# Routine for Implement Models

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Implement Models.
