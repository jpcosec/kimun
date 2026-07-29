---
# routine-xxx
id: routine-task-implement-workflows
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-implement-workflows-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-implement-workflows-execution-ready
- operator-task-implement-workflows-activate
- checklist-task-implement-workflows-testing-ready
- operator-task-implement-workflows-ready-for-testing
- checklist-task-implement-workflows-closeout-ready
- operator-task-implement-workflows-close
# Edge identifiers composing the graph
edges:
- edge-task-implement-workflows-execution-to-activate
- edge-task-implement-workflows-activate-to-testing
- edge-task-implement-workflows-testing-to-ready
- edge-task-implement-workflows-ready-to-closeout
- edge-task-implement-workflows-closeout-to-close
- edge-task-implement-workflows-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
steps: []
---

# Routine for Implement Workflows

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Implement Workflows.
