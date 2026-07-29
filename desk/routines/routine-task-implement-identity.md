---
# routine-xxx
id: routine-task-implement-identity
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-implement-identity-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-implement-identity-execution-ready
- operator-task-implement-identity-activate
- checklist-task-implement-identity-testing-ready
- operator-task-implement-identity-ready-for-testing
- checklist-task-implement-identity-closeout-ready
- operator-task-implement-identity-close
# Edge identifiers composing the graph
edges:
- edge-task-implement-identity-execution-to-activate
- edge-task-implement-identity-activate-to-testing
- edge-task-implement-identity-testing-to-ready
- edge-task-implement-identity-ready-to-closeout
- edge-task-implement-identity-closeout-to-close
- edge-task-implement-identity-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
steps: []
---

# Routine for Implement Identity

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Implement Identity.
