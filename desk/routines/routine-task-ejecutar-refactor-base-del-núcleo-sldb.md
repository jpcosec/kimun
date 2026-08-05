---
# routine-xxx
id: routine-task-ejecutar-refactor-base-del-núcleo-sldb
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-ejecutar-refactor-base-del-núcleo-sldb-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-ejecutar-refactor-base-del-núcleo-sldb-execution-ready
- operator-task-ejecutar-refactor-base-del-núcleo-sldb-activate
- checklist-task-ejecutar-refactor-base-del-núcleo-sldb-testing-ready
- operator-task-ejecutar-refactor-base-del-núcleo-sldb-ready-for-testing
- checklist-task-ejecutar-refactor-base-del-núcleo-sldb-closeout-ready
- operator-task-ejecutar-refactor-base-del-núcleo-sldb-close
# Edge identifiers composing the graph
edges:
- edge-task-ejecutar-refactor-base-del-núcleo-sldb-execution-to-activate
- edge-task-ejecutar-refactor-base-del-núcleo-sldb-activate-to-testing
- edge-task-ejecutar-refactor-base-del-núcleo-sldb-testing-to-ready
- edge-task-ejecutar-refactor-base-del-núcleo-sldb-ready-to-closeout
- edge-task-ejecutar-refactor-base-del-núcleo-sldb-closeout-to-close
- edge-task-ejecutar-refactor-base-del-núcleo-sldb-close-to-complete
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
