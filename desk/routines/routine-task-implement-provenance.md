---
# routine-xxx
id: routine-task-implement-provenance
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-implement-provenance-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-implement-provenance-execution-ready
- operator-task-implement-provenance-activate
- checklist-task-implement-provenance-testing-ready
- operator-task-implement-provenance-ready-for-testing
- checklist-task-implement-provenance-closeout-ready
- operator-task-implement-provenance-close
# Edge identifiers composing the graph
edges:
- edge-task-implement-provenance-execution-to-activate
- edge-task-implement-provenance-activate-to-testing
- edge-task-implement-provenance-testing-to-ready
- edge-task-implement-provenance-ready-to-closeout
- edge-task-implement-provenance-closeout-to-close
- edge-task-implement-provenance-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
---

# Routine for Implement Provenance

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Implement Provenance.
