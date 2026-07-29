---
# routine-xxx
id: routine-task-implement-graphs
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-implement-graphs-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-implement-graphs-execution-ready
- operator-task-implement-graphs-activate
- checklist-task-implement-graphs-testing-ready
- operator-task-implement-graphs-ready-for-testing
- checklist-task-implement-graphs-closeout-ready
- operator-task-implement-graphs-close
# Edge identifiers composing the graph
edges:
- edge-task-implement-graphs-execution-to-activate
- edge-task-implement-graphs-activate-to-testing
- edge-task-implement-graphs-testing-to-ready
- edge-task-implement-graphs-ready-to-closeout
- edge-task-implement-graphs-closeout-to-close
- edge-task-implement-graphs-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
---

# Routine for Implement Graphs

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Implement Graphs.
