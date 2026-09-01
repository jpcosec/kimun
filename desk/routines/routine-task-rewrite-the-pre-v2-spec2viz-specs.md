---
# routine-xxx
id: routine-task-rewrite-the-pre-v2-spec2viz-specs
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-rewrite-the-pre-v2-spec2viz-specs-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-rewrite-the-pre-v2-spec2viz-specs-execution-ready
- operator-task-rewrite-the-pre-v2-spec2viz-specs-activate
- checklist-task-rewrite-the-pre-v2-spec2viz-specs-testing-ready
- operator-task-rewrite-the-pre-v2-spec2viz-specs-ready-for-testing
- checklist-task-rewrite-the-pre-v2-spec2viz-specs-closeout-ready
- operator-task-rewrite-the-pre-v2-spec2viz-specs-close
# Edge identifiers composing the graph
edges:
- edge-task-rewrite-the-pre-v2-spec2viz-specs-execution-to-activate
- edge-task-rewrite-the-pre-v2-spec2viz-specs-activate-to-testing
- edge-task-rewrite-the-pre-v2-spec2viz-specs-testing-to-ready
- edge-task-rewrite-the-pre-v2-spec2viz-specs-ready-to-closeout
- edge-task-rewrite-the-pre-v2-spec2viz-specs-closeout-to-close
- edge-task-rewrite-the-pre-v2-spec2viz-specs-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
---

# Routine for Rewrite the pre-v2 spec2viz specs

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Rewrite the pre-v2 spec2viz specs.
