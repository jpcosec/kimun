---
# ritual-xxx
id: ritual-zero-context-audit-gate
# List of step-xxx paths
steps:
- 'Freeze the bundle: list every file a lane may read in runs/subagents/<ts>-<task>-zero-context-audit/brief.md
  and snapshot task.txt, next.txt, graph.txt, git-status.txt'
- 'Launch fresh-context read-only lanes on a cheap model with no chat context: A internal
  coherence of docs/v2 and epoch:v2 atoms; B executability of the task against the
  100% subagent-ready checklist A-M; C contradiction sweep of legacy atoms, contracts
  and features against docs/v2'
- Persist each lane report as lane-<X>.md with Note [high|medium|low] file:lines findings
- 'Triage in triage.md: every high/medium finding maps to a correction in an atom,
  a docs/v2 section, a task split, or a legacy retirement; design problems are returned
  to the user, not resolved by invention'
- Re-launch affected lanes fresh on the corrected bundle
- Stop when no high/medium remain or after 3 rounds; remaining lows are recorded as
  accepted debt
# e.g., layer:workflow, system:sldb
tags:
- workspace:desk
- system:sldb
---

# Zero-context audit gate

## Purpose

_Explain why this ritual exists._

Prove that a task bundle (task, atoms, docs, pill) is executable without improvisation and that the knowledge base does not contradict docs/v2, before any implementation. A weak model is used on purpose: if it cannot act from the bundle alone, the bundle is ambiguous.

## Trigger

_State when this ritual should start._

A task sits on its -execution-ready node and 'deskops next' lists the fresh-context subagent review as pending.

## Preconditions

_List the conditions that must hold before running the ritual._

- Task has bound atoms, explicit files and validation
- docs/v2 has no uncommitted changes
- A run directory under runs/subagents/ exists for the audit

## Validation

_List the checks that prove the ritual was performed correctly._

- runs/subagents/<ts>-<task>-zero-context-audit/ contains brief.md, lane-*.md, triage.md
- The last round has zero Note [high] and zero Note [medium]
- Lane B final verdict is ready with all checklist sections pass

## Failure Modes

_List common mistakes this ritual prevents._

- Using a strong model that fills gaps with inference
- Giving a lane the chat context or the triage of a previous round
- Editing the report instead of the bundle
- Findings without file:line citations
- Advancing the task before the last round is clean

## Completion

_Describe what completion looks like._

triage.md of the last round declares the gate satisfied and the task is advanced with deskops advance task.

## Step Details

_Generated from the step references above._
