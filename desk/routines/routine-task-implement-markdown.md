---
# routine-xxx
id: routine-task-implement-markdown
# active | archived
status: active
# Initial node identifier
entrypoint: checklist-task-implement-markdown-execution-ready
# Ordered or grouped primitive identifiers
decomposition:
- checklist-task-implement-markdown-execution-ready
- operator-task-implement-markdown-activate
- checklist-task-implement-markdown-testing-ready
- operator-task-implement-markdown-ready-for-testing
- checklist-task-implement-markdown-closeout-ready
- operator-task-implement-markdown-close
# Edge identifiers composing the graph
edges:
- edge-task-implement-markdown-execution-to-activate
- edge-task-implement-markdown-activate-to-testing
- edge-task-implement-markdown-testing-to-ready
- edge-task-implement-markdown-ready-to-closeout
- edge-task-implement-markdown-closeout-to-close
- edge-task-implement-markdown-close-to-complete
# Terminal node identifiers
terminal_nodes:
- complete
# e.g., system:deskops
tags:
- workspace:desk
- primitive:routine
---

# Routine for Implement Markdown

## Summary

_Summarize what this routine does and how its nodes fit together._

Actionable routine for Implement Markdown.
