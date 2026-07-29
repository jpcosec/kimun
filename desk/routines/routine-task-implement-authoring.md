---
# routine-xxx
id: routine-task-implement-authoring
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-implement-authoring-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-implement-authoring-execution-ready
- operator-task-implement-authoring-activate
- checklist-task-implement-authoring-testing-ready
- operator-task-implement-authoring-ready-for-testing
- checklist-task-implement-authoring-closeout-ready
- operator-task-implement-authoring-close
# Edge identifiers composing the graph
edges:
- edge-task-implement-authoring-execution-to-activate
- edge-task-implement-authoring-activate-to-testing
- edge-task-implement-authoring-testing-to-ready
- edge-task-implement-authoring-ready-to-closeout
- edge-task-implement-authoring-closeout-to-close
- edge-task-implement-authoring-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
steps: []
---

# Routine for Implement Authoring

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Implement Authoring.
