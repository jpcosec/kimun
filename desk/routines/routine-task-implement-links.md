---
# routine-xxx
id: routine-task-implement-links
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-implement-links-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-implement-links-execution-ready
- operator-task-implement-links-activate
- checklist-task-implement-links-testing-ready
- operator-task-implement-links-ready-for-testing
- checklist-task-implement-links-closeout-ready
- operator-task-implement-links-close
# Edge identifiers composing the graph
edges:
- edge-task-implement-links-execution-to-activate
- edge-task-implement-links-activate-to-testing
- edge-task-implement-links-testing-to-ready
- edge-task-implement-links-ready-to-closeout
- edge-task-implement-links-closeout-to-close
- edge-task-implement-links-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
steps: []
---

# Routine for Implement Links

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Implement Links.
