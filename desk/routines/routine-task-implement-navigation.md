---
# routine-xxx
id: routine-task-implement-navigation
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-implement-navigation-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-implement-navigation-execution-ready
- operator-task-implement-navigation-activate
- checklist-task-implement-navigation-testing-ready
- operator-task-implement-navigation-ready-for-testing
- checklist-task-implement-navigation-closeout-ready
- operator-task-implement-navigation-close
# Edge identifiers composing the graph
edges:
- edge-task-implement-navigation-execution-to-activate
- edge-task-implement-navigation-activate-to-testing
- edge-task-implement-navigation-testing-to-ready
- edge-task-implement-navigation-ready-to-closeout
- edge-task-implement-navigation-closeout-to-close
- edge-task-implement-navigation-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
---

# Routine for Implement Navigation

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Implement Navigation.
