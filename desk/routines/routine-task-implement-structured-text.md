---
# routine-xxx
id: routine-task-implement-structured-text
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-implement-structured-text-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-implement-structured-text-execution-ready
- operator-task-implement-structured-text-activate
- checklist-task-implement-structured-text-testing-ready
- operator-task-implement-structured-text-ready-for-testing
- checklist-task-implement-structured-text-closeout-ready
- operator-task-implement-structured-text-close
# Edge identifiers composing the graph
edges:
- edge-task-implement-structured-text-execution-to-activate
- edge-task-implement-structured-text-activate-to-testing
- edge-task-implement-structured-text-testing-to-ready
- edge-task-implement-structured-text-ready-to-closeout
- edge-task-implement-structured-text-closeout-to-close
- edge-task-implement-structured-text-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
steps: []
---

# Routine for Implement Structured Text

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Implement Structured Text.
