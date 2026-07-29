---
# routine-xxx
id: routine-task-implement-python
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-implement-python-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-implement-python-execution-ready
- operator-task-implement-python-activate
- checklist-task-implement-python-testing-ready
- operator-task-implement-python-ready-for-testing
- checklist-task-implement-python-closeout-ready
- operator-task-implement-python-close
# Edge identifiers composing the graph
edges:
- edge-task-implement-python-execution-to-activate
- edge-task-implement-python-activate-to-testing
- edge-task-implement-python-testing-to-ready
- edge-task-implement-python-ready-to-closeout
- edge-task-implement-python-closeout-to-close
- edge-task-implement-python-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
steps: []
---

# Routine for Implement Python

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Implement Python.
