---
# routine-xxx
id: routine-task-track-atom-work
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-track-atom-work-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-track-atom-work-execution-ready
- operator-task-track-atom-work-activate
- checklist-task-track-atom-work-testing-ready
- operator-task-track-atom-work-ready-for-testing
- checklist-task-track-atom-work-closeout-ready
- operator-task-track-atom-work-close
# Edge identifiers composing the graph
edges:
- edge-task-track-atom-work-execution-to-activate
- edge-task-track-atom-work-activate-to-testing
- edge-task-track-atom-work-testing-to-ready
- edge-task-track-atom-work-ready-to-closeout
- edge-task-track-atom-work-closeout-to-close
- edge-task-track-atom-work-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
---

# Routine for Track Atom Work

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Track Atom Work.
