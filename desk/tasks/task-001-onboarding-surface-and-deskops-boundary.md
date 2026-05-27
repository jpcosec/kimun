---
id: task-001
domain: cli/docs/desk
status: done
priority: p1
depends_on: []
created: "2026-05-20"
---

# Close the onboarding surface and ground the deskops boundary

## Objective

Turn the recent SLDB onboarding and desk-oriented work into explicit desk artifacts so the repo has a task, pills, and a board entry that explain what was done and what architectural direction it established.

## Reference

- `README.md`
- `docs/faq.md`
- `src/sldb/cli/commands/help.py`
- `src/sldb/cli/commands/faq.py`
- `src/sldb/cli/commands/explore.py`
- `src/sldb/cli/commands/inbox.py`
- `docs/architecture/deskops-boundary-proposal.md`
- `docs/architecture/spec2viz/current-onboarding-surface.yml`
- `docs/architecture/spec2viz/proposed-deskops-components.yml`

## What To Fix

The repo had no first-class desk task or pills recording the reasoning behind the new onboarding/help surface and the `deskops` boundary proposal.

## How To Do It

Create a minimal repo-local desk model layer, write the board/task/pill docs with the current repo state, track them through SLDB, and rebuild the store so the desk becomes part of the queryable documentation surface.

## Validation

- `python -m sldb validate desk.models:DeskTaskDoc --input desk/tasks/task-001-onboarding-surface-and-deskops-boundary.md --pythonpath .`
- `python -m sldb validate desk.models:DeskPillDoc --input desk/pills/pill-001-sldb-vs-deskops-boundary.md --pythonpath .`
- `python -m sldb validate desk.models:DeskPillDoc --input desk/pills/pill-002-onboarding-surface-before-depth.md --pythonpath .`
- `python -m sldb validate desk.models:DeskBoardDoc --input desk/tasks/Board.md --pythonpath .`
- `python -m sldb stores update --store .sldb --pythonpath .`
- `pytest`

## Done When

The repo contains explicit desk artifacts for this delivery slice, those artifacts validate against local desk models, and they are tracked in the SLDB store.
