---
# routine-xxx
id: routine-task-ejecutar-refactor-base-del-n-cleo-sldb
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-ejecutar-refactor-base-del-n-cleo-sldb-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-ejecutar-refactor-base-del-n-cleo-sldb-execution-ready
- operator-task-ejecutar-refactor-base-del-n-cleo-sldb-activate
- checklist-task-ejecutar-refactor-base-del-n-cleo-sldb-testing-ready
- operator-task-ejecutar-refactor-base-del-n-cleo-sldb-ready-for-testing
- checklist-task-ejecutar-refactor-base-del-n-cleo-sldb-closeout-ready
- operator-task-ejecutar-refactor-base-del-n-cleo-sldb-close
# Edge identifiers composing the graph
edges:
- edge-task-ejecutar-refactor-base-del-n-cleo-sldb-execution-to-activate
- edge-task-ejecutar-refactor-base-del-n-cleo-sldb-activate-to-testing
- edge-task-ejecutar-refactor-base-del-n-cleo-sldb-testing-to-ready
- edge-task-ejecutar-refactor-base-del-n-cleo-sldb-ready-to-closeout
- edge-task-ejecutar-refactor-base-del-n-cleo-sldb-closeout-to-close
- edge-task-ejecutar-refactor-base-del-n-cleo-sldb-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
---

# Routine for Ejecutar refactor base del núcleo SLDB

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Ejecutar refactor base del núcleo SLDB.
