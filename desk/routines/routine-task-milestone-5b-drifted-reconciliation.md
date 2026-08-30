---
# routine-xxx
id: routine-task-milestone-5b-drifted-reconciliation
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-milestone-5b-drifted-reconciliation-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-milestone-5b-drifted-reconciliation-execution-ready
- operator-task-milestone-5b-drifted-reconciliation-activate
- checklist-task-milestone-5b-drifted-reconciliation-testing-ready
- operator-task-milestone-5b-drifted-reconciliation-ready-for-testing
- checklist-task-milestone-5b-drifted-reconciliation-closeout-ready
- operator-task-milestone-5b-drifted-reconciliation-close
# Edge identifiers composing the graph
edges:
- edge-task-milestone-5b-drifted-reconciliation-execution-to-activate
- edge-task-milestone-5b-drifted-reconciliation-activate-to-testing
- edge-task-milestone-5b-drifted-reconciliation-testing-to-ready
- edge-task-milestone-5b-drifted-reconciliation-ready-to-closeout
- edge-task-milestone-5b-drifted-reconciliation-closeout-to-close
- edge-task-milestone-5b-drifted-reconciliation-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
---

# Routine for Milestone 5b drifted reconciliation

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Milestone 5b drifted reconciliation.
