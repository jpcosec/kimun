---
# routine-xxx
id: routine-task-implement-nodes
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-implement-nodes-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-implement-nodes-execution-ready
- operator-task-implement-nodes-activate
- checklist-task-implement-nodes-testing-ready
- operator-task-implement-nodes-ready-for-testing
- checklist-task-implement-nodes-closeout-ready
- operator-task-implement-nodes-close
# Edge identifiers composing the graph
edges:
- edge-task-implement-nodes-execution-to-activate
- edge-task-implement-nodes-activate-to-testing
- edge-task-implement-nodes-testing-to-ready
- edge-task-implement-nodes-ready-to-closeout
- edge-task-implement-nodes-closeout-to-close
- edge-task-implement-nodes-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
---

# Routine for Implement Nodes

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Implement Nodes.
