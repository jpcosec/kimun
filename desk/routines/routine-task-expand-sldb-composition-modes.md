---
id: routine-task-expand-sldb-composition-modes
status: active
entrypoint: checklist-task-expand-sldb-composition-modes-execution-ready
decomposition:
- checklist-task-expand-sldb-composition-modes-execution-ready
- operator-task-expand-sldb-composition-modes-activate
- checklist-task-expand-sldb-composition-modes-testing-ready
- operator-task-expand-sldb-composition-modes-ready-for-testing
- checklist-task-expand-sldb-composition-modes-closeout-ready
- operator-task-expand-sldb-composition-modes-close
edges:
- edge-task-expand-sldb-composition-modes-execution-to-activate
- edge-task-expand-sldb-composition-modes-activate-to-testing
- edge-task-expand-sldb-composition-modes-testing-to-ready
- edge-task-expand-sldb-composition-modes-ready-to-closeout
- edge-task-expand-sldb-composition-modes-closeout-to-close
- edge-task-expand-sldb-composition-modes-close-to-complete
terminal_nodes:
- complete
tags:
- workspace:desk
- primitive:routine
---

# Routine for Expand SLDB composition modes

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Expand SLDB composition modes.
