---
# routine-xxx
id: routine-task-mapear-arquitectura-real-de-sldb
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-mapear-arquitectura-real-de-sldb-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-mapear-arquitectura-real-de-sldb-execution-ready
- operator-task-mapear-arquitectura-real-de-sldb-activate
- checklist-task-mapear-arquitectura-real-de-sldb-testing-ready
- operator-task-mapear-arquitectura-real-de-sldb-ready-for-testing
- checklist-task-mapear-arquitectura-real-de-sldb-closeout-ready
- operator-task-mapear-arquitectura-real-de-sldb-close
# Edge identifiers composing the graph
edges:
- edge-task-mapear-arquitectura-real-de-sldb-execution-to-activate
- edge-task-mapear-arquitectura-real-de-sldb-activate-to-testing
- edge-task-mapear-arquitectura-real-de-sldb-testing-to-ready
- edge-task-mapear-arquitectura-real-de-sldb-ready-to-closeout
- edge-task-mapear-arquitectura-real-de-sldb-closeout-to-close
- edge-task-mapear-arquitectura-real-de-sldb-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
---

# Routine for Mapear arquitectura real de SLDB

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Mapear arquitectura real de SLDB.
