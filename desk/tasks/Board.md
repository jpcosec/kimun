---
# board-xxx
id: board-001
# Affected workspace or domain
scope: desk
# List of task-xxx paths
tasks: []
# List of pill-xxx paths
pills:
- desk/contexts/pills.md
- desk/contexts/pill-planning-contracts.md
# List of ritual-xxx paths
rituals:
- desk/rituals/execution.md
- desk/rituals/testing.md
- desk/rituals/closeout.md
# e.g., system:sldb, workspace:desk
tags:
- workspace:desk
- system:sldb
---

# sldb-refactor-worktree Board

## Purpose

_Explain what this board routes and why it exists._



## Notes

_Add short operational notes about the current routed set._

- Implement v2 milestone 3: file persistence and reload [draft] - A Store protocol with a Babashka file backend per docs/v2/02 section 8.1: objects/<hash> written as UTF-8 canonical EDN so that H(file) == name, log.edn append-only with one Transaction map per line, heads.edn and store.edn written atomically by rename; open = read descriptor and heads then replay (re-apply every logged plan and check each revision id matches the logged one); verify = recompute the hash of every object reachable from the heads and compare with its name. rebuild-indexes is out of scope until derived indexes exist (milestone 4+).

## Task Details

_Generated from the task references above._
