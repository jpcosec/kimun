---
id: task-milestone-5b-drifted-reconciliation
status: active
summary: Milestone 5b (docs/v2/02 section 9 row 5b, spec docs/v2/02 section 6.5 and
  docs/v2/04 section 8)- reconciliation of orphan anchors into proposals outside the
  pool by position, external fingerprint and Dice similarity over grapheme trigrams;
  acceptance as an ordinary supersedes transaction; and markdown->update-plan so a
  .md edited outside the kernel can be re-ingested without any replace op. Depends
  on task-milestone-5a-anchor-states, closed.
tags:
- workspace:desk
- artifact:task
- source:drawer
routine: routine-task-milestone-5b-drifted-reconciliation
current_node: checklist-task-milestone-5b-drifted-reconciliation-testing-ready
history:
- operator-task-milestone-5b-drifted-reconciliation-activate
references:
- desk/drawer/tasks/task-milestone-5b-drifted-reconciliation.md
depends_on:
- task-milestone-5a-anchor-states
pills:
- desk/contexts/pill-guardrail-v2-implementation-gate.md
files:
- docs/v2/02-sustrato-computacional.md
- docs/v2/04-superficie-markdown.md
- src/sldb/kernel/reconcile.cljc
- src/sldb/surface/markdown/plan.cljc
- test/sldb/kernel/reconcile_test.cljc
- test/sldb/surface/markdown/drift_test.cljc
- docs/v2/tests/promises.md
checklists:
- checklist-task-milestone-5b-drifted-reconciliation-execution-ready
- checklist-task-milestone-5b-drifted-reconciliation-testing-ready
- checklist-task-milestone-5b-drifted-reconciliation-closeout-ready
task_type: implementation
inherits_from: []
inherit_acceptance_context: false
atoms:
- atom-drifted-is-a-reconciled-orphan
- atom-evidence-edges-and-anchor-states
- atom-anchor-state-is-derived-and-computed-per-endpoint
- atom-external-fingerprint-form-and-who-computes-it
- atom-re-anchoring-rules-under-succession
- atom-identity-is-the-content-hash-succession-is-a-recorded-edge
- atom-decision-trees-are-trees-of-positions-git-like
- atom-sldb-md-profile-a-closed-canonical-markdown-subset
- atom-lossless-cst-and-canonical-render-for-markdown
closeout_evidence_verified: false
---

# Milestone 5b drifted reconciliation

## Rationale

_Explain why this task exists or the business driver behind it._

Milestone 5a can tell that an anchor lost its ground but not what replaced it. Without reconciliation an orphan is a dead end rather than a question the autopoietic loop can answer, and the roadmap exit criterion for row 5b — a .md edited outside the kernel detected and re-anchorable — cannot be demonstrated at all, because nothing can re-ingest a changed file into an existing tree.

## Goal

_Describe the concrete result this task must produce._

sldb.kernel.reconcile (ring 0) implementing docs/v2/02 section 6.5 — orphans, dice, proposals and accept-plan — and sldb.surface.markdown.plan/ast->update-plan plus markdown->update-plan (ring 2) implementing docs/v2/04 section 8, so that the exit criterion of roadmap row 5b holds end to end.

## Scope

_State what is in scope and what is out of scope._

In — the three reconciliation methods of section 6.5 with their confidences and precedence; the Dice coefficient over the multiset of grapheme trigrams with integer arithmetic and one final division; proposals ranked by descending confidence and ascending candidate id, best per orphan unless all are asked for; the obligatory :base when a revision has not exactly one parent; accept-plan as a plan the caller commits; markdown->update-plan emitting add-node, repeated detach of the root children and the ownership of the new AST, never a replace, and refusing a changed document root. Out — anything that writes to the store by itself, persisting a proposal, embeddings or any semantic provider, CommonMark beyond the profile, and every milestone from 6 on.

## Implementation Path

_Outline the expected implementation route or affected surface._

docs/v2/02 section 6.5 and docs/v2/04 section 8 settle every observable detail; nothing in them is to be re-decided. Then (1) src/sldb/kernel/reconcile.cljc, a new ring-0 namespace over sldb.kernel.anchor with dice, orphans, proposals and accept-plan exactly as section 6.5 shapes them, raising :reconcile/base-required, :reconcile/unknown-node and :anchor/unknown-revision; (2) src/sldb/surface/markdown/plan.cljc gains exactly two public functions - (ast->update-plan host store tree-id ast {:actor :timestamp :base}) and (markdown->update-plan host store tree-id text opts) = ast->update-plan after ast/parse - which CALL the existing private collect; collect stays private, and ast->plan, store->ast and store->markdown are not touched at all; (3) test/sldb/kernel/reconcile_test.cljc covering section 6.5 and test/sldb/surface/markdown/drift_test.cljc covering the end-to-end exit criterion of roadmap row 5b; (4) docs/v2/tests/promises.md gains one row per normative claim of section 6.5 - the three methods with their confidences, the Dice edge cases, the ordering and the best-per-orphan rule, the proposal living outside the pool, acceptance as a supersedes transaction with the actor as its only evidence, and the obligatory base - plus one row for the roadmap row 5b exit criterion and one for the docs/v2/04 section 8 addition.

## Validation

_List the checks required before this task can close._

- bb lint
- bb test
- bb oracle

## Done When

_Name the observable condition that makes the task complete._

bb lint, bb test and bb oracle green; every promise of docs/v2/02 section 6.5 and of the markdown->update-plan bullet has a named test in docs/v2/tests/promises.md; a Markdown document ingested, edited outside the kernel and re-ingested leaves its anchor orphan with no supersedes edge anywhere, reconciliation names the substitute by position with confidence 1.0, and committing accept-plan leaves the anchor superseded and re-anchored with no orphan left; runs/subagents evidence committed.
