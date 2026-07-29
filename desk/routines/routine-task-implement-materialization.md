---
# routine-xxx
id: routine-task-implement-materialization
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-implement-materialization-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-implement-materialization-execution-ready
- operator-task-implement-materialization-activate
- checklist-task-implement-materialization-testing-ready
- operator-task-implement-materialization-ready-for-testing
- checklist-task-implement-materialization-closeout-ready
- operator-task-implement-materialization-close
# Edge identifiers composing the graph
edges:
- edge-task-implement-materialization-execution-to-activate
- edge-task-implement-materialization-activate-to-testing
- edge-task-implement-materialization-testing-to-ready
- edge-task-implement-materialization-ready-to-closeout
- edge-task-implement-materialization-closeout-to-close
- edge-task-implement-materialization-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
---

# Routine for Implement Materialization

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Implement Materialization.
