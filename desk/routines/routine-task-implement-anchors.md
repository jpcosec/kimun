---
# routine-xxx
id: routine-task-implement-anchors
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-implement-anchors-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-implement-anchors-execution-ready
- operator-task-implement-anchors-activate
- checklist-task-implement-anchors-testing-ready
- operator-task-implement-anchors-ready-for-testing
- checklist-task-implement-anchors-closeout-ready
- operator-task-implement-anchors-close
# Edge identifiers composing the graph
edges:
- edge-task-implement-anchors-execution-to-activate
- edge-task-implement-anchors-activate-to-testing
- edge-task-implement-anchors-testing-to-ready
- edge-task-implement-anchors-ready-to-closeout
- edge-task-implement-anchors-closeout-to-close
- edge-task-implement-anchors-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
---

# Routine for Implement Anchors

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Implement Anchors.
