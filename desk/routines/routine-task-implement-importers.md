---
# routine-xxx
id: routine-task-implement-importers
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-implement-importers-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-implement-importers-execution-ready
- operator-task-implement-importers-activate
- checklist-task-implement-importers-testing-ready
- operator-task-implement-importers-ready-for-testing
- checklist-task-implement-importers-closeout-ready
- operator-task-implement-importers-close
# Edge identifiers composing the graph
edges:
- edge-task-implement-importers-execution-to-activate
- edge-task-implement-importers-activate-to-testing
- edge-task-implement-importers-testing-to-ready
- edge-task-implement-importers-ready-to-closeout
- edge-task-implement-importers-closeout-to-close
- edge-task-implement-importers-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
---

# Routine for Implement Importers

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Implement Importers.
