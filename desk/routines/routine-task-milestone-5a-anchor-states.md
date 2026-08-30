---
# routine-xxx
id: routine-task-milestone-5a-anchor-states
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-milestone-5a-anchor-states-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-milestone-5a-anchor-states-execution-ready
- operator-task-milestone-5a-anchor-states-activate
- checklist-task-milestone-5a-anchor-states-testing-ready
- operator-task-milestone-5a-anchor-states-ready-for-testing
- checklist-task-milestone-5a-anchor-states-closeout-ready
- operator-task-milestone-5a-anchor-states-close
# Edge identifiers composing the graph
edges:
- edge-task-milestone-5a-anchor-states-execution-to-activate
- edge-task-milestone-5a-anchor-states-activate-to-testing
- edge-task-milestone-5a-anchor-states-testing-to-ready
- edge-task-milestone-5a-anchor-states-ready-to-closeout
- edge-task-milestone-5a-anchor-states-closeout-to-close
- edge-task-milestone-5a-anchor-states-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
---

# Routine for Milestone 5a anchor states

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Milestone 5a anchor states.
