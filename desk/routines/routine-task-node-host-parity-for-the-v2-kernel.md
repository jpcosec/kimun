---
# routine-xxx
id: routine-task-node-host-parity-for-the-v2-kernel
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-node-host-parity-for-the-v2-kernel-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-node-host-parity-for-the-v2-kernel-execution-ready
- operator-task-node-host-parity-for-the-v2-kernel-activate
- checklist-task-node-host-parity-for-the-v2-kernel-testing-ready
- operator-task-node-host-parity-for-the-v2-kernel-ready-for-testing
- checklist-task-node-host-parity-for-the-v2-kernel-closeout-ready
- operator-task-node-host-parity-for-the-v2-kernel-close
# Edge identifiers composing the graph
edges:
- edge-task-node-host-parity-for-the-v2-kernel-execution-to-activate
- edge-task-node-host-parity-for-the-v2-kernel-activate-to-testing
- edge-task-node-host-parity-for-the-v2-kernel-testing-to-ready
- edge-task-node-host-parity-for-the-v2-kernel-ready-to-closeout
- edge-task-node-host-parity-for-the-v2-kernel-closeout-to-close
- edge-task-node-host-parity-for-the-v2-kernel-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
---

# Routine for Node host parity for the v2 kernel

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Node host parity for the v2 kernel.
