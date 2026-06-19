---
id: routine-task-tighten-semantic-export-provenance-contract
status: active
entrypoint: checklist-task-tighten-semantic-export-provenance-contract-execution-ready
decomposition:
- checklist-task-tighten-semantic-export-provenance-contract-execution-ready
- operator-task-tighten-semantic-export-provenance-contract-activate
- checklist-task-tighten-semantic-export-provenance-contract-testing-ready
- operator-task-tighten-semantic-export-provenance-contract-ready-for-testing
- checklist-task-tighten-semantic-export-provenance-contract-closeout-ready
- operator-task-tighten-semantic-export-provenance-contract-close
edges:
- edge-task-tighten-semantic-export-provenance-contract-execution-to-activate
- edge-task-tighten-semantic-export-provenance-contract-activate-to-testing
- edge-task-tighten-semantic-export-provenance-contract-testing-to-ready
- edge-task-tighten-semantic-export-provenance-contract-ready-to-closeout
- edge-task-tighten-semantic-export-provenance-contract-closeout-to-close
- edge-task-tighten-semantic-export-provenance-contract-close-to-complete
terminal_nodes:
- complete
tags:
- workspace:desk
- primitive:routine
---

# Routine for Tighten semantic export provenance contract

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Tighten semantic export provenance contract.
