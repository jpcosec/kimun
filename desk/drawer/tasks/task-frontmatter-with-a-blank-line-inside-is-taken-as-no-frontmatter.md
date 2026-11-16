---
id: task-frontmatter-with-a-blank-line-inside-is-taken-as-no-frontmatter
status: deferred
summary: ''
tags:
- workspace:desk
- artifact:task
- source:inbox
current_node: ''
history: []
references: []
depends_on: []
pills: []
files: []
task_type: ''
inherits_from: []
inherit_acceptance_context: false
atoms: []
---

# Frontmatter with a blank line inside is taken as no frontmatter

## Rationale

_Explain why this task exists or the business driver behind it._

Promoted from the inbox note `desk/inbox/20261116-204318-suggestion-frontmatter-with-a-blank-line-inside-is-taken-as-no-frontmatter.md`.

## Goal

_Describe the concrete result this task must produce._

Triage and resolve the inbox message promoted from `desk/inbox/20261116-204318-suggestion-frontmatter-with-a-blank-line-inside-is-taken-as-no-frontmatter.md`.

## Scope

_State what is in scope and what is out of scope._

_Describe the incoming message with enough evidence to triage._

Rationale: found by the readers run of 2026-11-16 (runs/subagents/2026-11-16-lectores-desk/REPORT.md, limit 2) and reproduced by the coordinator with a two-file project. frontmatter-end in src/kimun/surface/markdown/cst.cljc returns nil when the YAML frontmatter has a blank line before its closing ---, so the reader view gets :front nil with no error; 44 reviews of the deskops desk (multi-line quoted scalars) were silently unread. Goal: a frontmatter with blank lines inside is read as frontmatter. Scope: cst.cljc and its tests; then remove fallback-front from the reviews reader and re-measure coverage. Done When: the 44 files give propositions without the fallback and the full suite passes. Task type when authored: task.code (blocked for closeout by the .py-only tests gate of deskops, see the inbox note of 2026-11-16).

## Implementation Path

_Outline the expected implementation route or affected surface._

To be defined when the task is promoted.

## Validation

_List the checks required before this task can close._

- 

## Done When

_Name the observable condition that makes the task complete._

The message is resolved, answered, or promoted into active work.
