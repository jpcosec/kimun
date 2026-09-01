---
# routine-xxx
id: routine-task-external-oracle-for-canonical-bytes
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-external-oracle-for-canonical-bytes-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-external-oracle-for-canonical-bytes-execution-ready
- operator-task-external-oracle-for-canonical-bytes-activate
- checklist-task-external-oracle-for-canonical-bytes-testing-ready
- operator-task-external-oracle-for-canonical-bytes-ready-for-testing
- checklist-task-external-oracle-for-canonical-bytes-closeout-ready
- operator-task-external-oracle-for-canonical-bytes-close
# Edge identifiers composing the graph
edges:
- edge-task-external-oracle-for-canonical-bytes-execution-to-activate
- edge-task-external-oracle-for-canonical-bytes-activate-to-testing
- edge-task-external-oracle-for-canonical-bytes-testing-to-ready
- edge-task-external-oracle-for-canonical-bytes-ready-to-closeout
- edge-task-external-oracle-for-canonical-bytes-closeout-to-close
- edge-task-external-oracle-for-canonical-bytes-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
---

# Routine for External oracle for canonical-bytes

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for External oracle for canonical-bytes.
