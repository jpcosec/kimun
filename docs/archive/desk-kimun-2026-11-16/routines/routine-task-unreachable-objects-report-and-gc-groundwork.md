---
# routine-xxx
id: routine-task-unreachable-objects-report-and-gc-groundwork
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-unreachable-objects-report-and-gc-groundwork-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-unreachable-objects-report-and-gc-groundwork-execution-ready
- operator-task-unreachable-objects-report-and-gc-groundwork-activate
- checklist-task-unreachable-objects-report-and-gc-groundwork-testing-ready
- operator-task-unreachable-objects-report-and-gc-groundwork-ready-for-testing
- checklist-task-unreachable-objects-report-and-gc-groundwork-closeout-ready
- operator-task-unreachable-objects-report-and-gc-groundwork-close
# Edge identifiers composing the graph
edges:
- edge-task-unreachable-objects-report-and-gc-groundwork-execution-to-activate
- edge-task-unreachable-objects-report-and-gc-groundwork-activate-to-testing
- edge-task-unreachable-objects-report-and-gc-groundwork-testing-to-ready
- edge-task-unreachable-objects-report-and-gc-groundwork-ready-to-closeout
- edge-task-unreachable-objects-report-and-gc-groundwork-closeout-to-close
- edge-task-unreachable-objects-report-and-gc-groundwork-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
---

# Routine for Unreachable objects report and GC groundwork

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Unreachable objects report and GC groundwork.
