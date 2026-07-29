---
# routine-xxx
id: routine-task-implement-search
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-implement-search-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-implement-search-execution-ready
- operator-task-implement-search-activate
- checklist-task-implement-search-testing-ready
- operator-task-implement-search-ready-for-testing
- checklist-task-implement-search-closeout-ready
- operator-task-implement-search-close
# Edge identifiers composing the graph
edges:
- edge-task-implement-search-execution-to-activate
- edge-task-implement-search-activate-to-testing
- edge-task-implement-search-testing-to-ready
- edge-task-implement-search-ready-to-closeout
- edge-task-implement-search-closeout-to-close
- edge-task-implement-search-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
steps: []
---

# Routine for Implement Search

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Implement Search.
