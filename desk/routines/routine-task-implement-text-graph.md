---
# routine-xxx
id: routine-task-implement-text-graph
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-implement-text-graph-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-implement-text-graph-execution-ready
- operator-task-implement-text-graph-activate
- checklist-task-implement-text-graph-testing-ready
- operator-task-implement-text-graph-ready-for-testing
- checklist-task-implement-text-graph-closeout-ready
- operator-task-implement-text-graph-close
# Edge identifiers composing the graph
edges:
- edge-task-implement-text-graph-execution-to-activate
- edge-task-implement-text-graph-activate-to-testing
- edge-task-implement-text-graph-testing-to-ready
- edge-task-implement-text-graph-ready-to-closeout
- edge-task-implement-text-graph-closeout-to-close
- edge-task-implement-text-graph-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
---

# Routine for Implement Text Graph

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Implement Text Graph.
