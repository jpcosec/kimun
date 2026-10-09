---
kind: unclear
sender_project: sldb-refactor-worktree
created_at: 2026-08-31T00:30:40
status: open
---

# board notes retain stale active lines for closed milestones 5b and 6

The Board.md Notes section still lists 'Milestone 5b drifted reconciliation [active]' and 'Milestone 6 stand-off below the paragraph [active]' although both tasks are closed (commits 80964a8/095ae90 for 5b; f4486bd/a85194c for 6). The tasks: field is correctly []. deskops cannot edit the legacy Board.md Notes prose, so the stale [active] lines cannot be cleared through the CLI. Fix: teach deskops to reconcile Board Notes on task closure, or migrate Board.md to a modeled artifact.
