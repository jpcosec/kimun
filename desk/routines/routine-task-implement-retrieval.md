---
# routine-xxx
id: routine-task-implement-retrieval
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-implement-retrieval-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-implement-retrieval-execution-ready
- operator-task-implement-retrieval-activate
- checklist-task-implement-retrieval-testing-ready
- operator-task-implement-retrieval-ready-for-testing
- checklist-task-implement-retrieval-closeout-ready
- operator-task-implement-retrieval-close
# Edge identifiers composing the graph
edges:
- edge-task-implement-retrieval-execution-to-activate
- edge-task-implement-retrieval-activate-to-testing
- edge-task-implement-retrieval-testing-to-ready
- edge-task-implement-retrieval-ready-to-closeout
- edge-task-implement-retrieval-closeout-to-close
- edge-task-implement-retrieval-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
---

# Routine for Implement Retrieval

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Implement Retrieval.
