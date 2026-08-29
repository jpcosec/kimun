---
# pill-xxx
id: pill-guardrail-v2-implementation-gate
# e.g., language:python, library:pydantic
tags:
- workspace:desk
- system:sldb
---

# Guardrail: v2 implementation gate

## What

_Define the context or guardrail this pill carries._

For epoch v2 implementation tasks, the applicable execution ritual is desk/rituals/ritual-zero-context-audit-gate.md; the legacy execution/testing/closeout rituals are planning-era text and do not apply.

## Why

_Explain why this context matters for safe execution._

docs/v2 reoriented the repo from planning-only contracts to a Clojure kernel implementation; the old rituals forbid implementation and the lifecycle expects a fresh-context subagent review before execution.

## When

_Describe when an agent should apply this pill._

Whenever a task tagged for the v2 first slice sits on its -execution-ready node, and again after any change to docs/v2 or epoch:v2 atoms.

## Where

_Name the files, surfaces, or scope this pill applies to._

desk/tasks/task-implement-v2-*.md, docs/v2/, desk/atoms/atom-*.md with epoch:v2, runs/subagents/*-zero-context-audit/

## How

_Describe the correct way to apply this guidance._

Run the zero-context audit gate with cheap fresh-context lanes; fix atoms, docs and tasks through deskops; commit the runs/ evidence; then deskops advance task.

## How Not

_Describe the shortcut or failure mode to avoid._

Do not start src/ or test/ files before the last audit round is clean; do not give lanes chat context; do not hand-edit legacy rituals or Board.md (log gaps in desk/inbox instead).
