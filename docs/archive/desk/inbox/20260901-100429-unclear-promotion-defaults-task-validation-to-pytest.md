---
# unclear | suggestion
kind: unclear
# e.g., other_repo
sender_project: sldb-refactor-worktree
# e.g., target_repo
target_project: sldb-refactor-worktree
# ISO 8601 timestamp
created_at: '2026-09-01T10:04:29'
# open | closed
status: open
# project identity that acknowledged the note
acknowledged_by: ⸢rev•acknowledged_by⸥
# ISO 8601 timestamp, set when acknowledged
acknowledged_at: ⸢rev•acknowledged_at⸥
---

# Promotion defaults task validation to pytest

_Describe the incoming message with enough evidence to triage._

promote drawer-task-to-active-task writes a hardcoded 'pytest' Validation line into every promoted task, even in a repo whose gates are bb lint / bb test / bb oracle. All seven tasks promoted on 2026-09-01 had to be corrected by hand with deskops edit task <id> validation. Fix: derive the default from the repo registration or the desk config instead of assuming Python.
