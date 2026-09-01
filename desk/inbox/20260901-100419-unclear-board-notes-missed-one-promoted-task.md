---
# unclear | suggestion
kind: unclear
# e.g., other_repo
sender_project: sldb-refactor-worktree
# e.g., target_repo
target_project: sldb-refactor-worktree
# ISO 8601 timestamp
created_at: '2026-09-01T10:04:19'
# open | closed
status: open
# project identity that acknowledged the note
acknowledged_by: ⸢rev•acknowledged_by⸥
# ISO 8601 timestamp, set when acknowledged
acknowledged_at: ⸢rev•acknowledged_at⸥
---

# Board Notes missed one promoted task

_Describe the incoming message with enough evidence to triage._

promote drawer-task-to-active-task appended 'Test coverage and lint tooling for Babashka' to Board.md ## Task Details but not to ## Notes; the other six promotions of the same batch landed in both sections, so Notes and Task Details now disagree by one task. Also: the same batch DID clear the stale [active] Notes lines for closed milestones 5b/6, so Notes is regenerated on promotion after all - see 20260831-003040.
