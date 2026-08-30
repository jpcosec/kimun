---
id: task-milestone-5a-anchor-states
status: active
summary: Milestone 5a (docs/v2/02 section 9 row 5a, spec docs/v2/02 sections 6.1-6.4)-
  deterministic anchor states intact/superseded/orphan per endpoint and per edge,
  the six anchor queries, the supersedes trigger for re-anchoring, and the external
  fingerprint form. Split out of the drawer candidate task-milestone-5-anchor-states-and-drifted-reconciliation;
  the heuristic half is task-milestone-5b-drifted-reconciliation.
tags:
- workspace:desk
- artifact:task
routine: routine-task-milestone-5a-anchor-states
current_node: checklist-task-milestone-5a-anchor-states-testing-ready
history:
- operator-task-milestone-5a-anchor-states-activate
references: []
depends_on: []
pills:
- desk/contexts/pill-guardrail-v2-implementation-gate.md
files:
- docs/v2/02-sustrato-computacional.md
- src/sldb/kernel/anchor.cljc
- src/sldb/kernel/revision.cljc
- src/sldb/kernel/node.cljc
- test/sldb/kernel/anchor_test.cljc
- test/sldb/kernel/revision_test.cljc
- test/sldb/kernel/node_test.cljc
- test/sldb/kernel/generators.cljc
- test/fixtures/nodes.edn
- docs/v2/tests/promises.md
checklists:
- checklist-task-milestone-5a-anchor-states-execution-ready
- checklist-task-milestone-5a-anchor-states-testing-ready
- checklist-task-milestone-5a-anchor-states-closeout-ready
task_type: implementation
inherits_from: []
inherit_acceptance_context: false
atoms:
- atom-evidence-edges-and-anchor-states
- atom-anchor-state-is-derived-and-computed-per-endpoint
- atom-external-fingerprint-form-and-who-computes-it
- atom-re-anchoring-rules-under-succession
- atom-evidence-required-per-edge-type
- atom-identity-is-the-content-hash-succession-is-a-recorded-edge
- atom-decision-trees-are-trees-of-positions-git-like
- atom-stand-off-addresses-are-virtual-until-referenced
- atom-decision-pure-cljc-kernel-with-hosts-as-adapters
closeout_evidence_verified: false
---

# Milestone 5a anchor states

## Rationale

_Explain why this task exists or the business driver behind it._

The kernel records evidence on every cross-layer anchor but cannot answer what happened to it. Without deterministic anchor states there is no blame, no what-is-anchored-in-what, and no signal for the autopoietic loop; and milestone 5b reconciliation has nothing to refine.

## Goal

_Describe the concrete result this task must produce._

sldb.kernel.anchor (ring 0) implementing docs/v2/02 section 6.2 and 6.3 — endpoint-state, state, states, anchored-in, report and diff over (store, revision) — plus the supersedes trigger of section 6.1 in sldb.kernel.revision and the external fingerprint form of section 6.4 in sldb.kernel.node.

## Scope

_State what is in scope and what is out of scope._

In — deterministic states intact, superseded and orphan per endpoint and per edge; successor chains with ambiguity; resolution rules per class and kind including span recursion into its leaf and range; the six query functions with total ordering; add-edge of a new supersedes edge triggering the section 6.1 re-anchoring and recording superseded; validation of the external fingerprint shape against the store algorithm. Out — any heuristic, any proposal record, drifted, the reconcile namespace, markdown re-ingestion, derived indexes, persistence of anchor states.

## Implementation Path

_Outline the expected implementation route or affected surface._

Read docs/v2/02 sections 6.1 to 6.4 first; they settle every observable detail and nothing in them is to be re-decided. Then (1) src/sldb/kernel/anchor.cljc, a new ring-0 namespace that is a pure derived view over revision and tree, with the seven queries of section 6.3 (placements, endpoint-state, state, states, anchored-in, report, diff) plus the anchor-types set, and the two error types named there; (2) src/sldb/kernel/revision.cljc, where the non-ownership branch of add-edge-op gains one conditional - when the edge is newly added (never in the idempotent branch of section 3.2) AND its type is supersedes, conj the pair [(:to e) (:from e)] onto the working states superseded and then call the existing re-anchor with old = (:to e) and new = (:from e), on the working state that already contains the new edge. Because Clojure resolves vars at read time, the re-anchor definition has to be relocated in the file so that it appears before add-edge-op; that relocation is a pure move, its body does not change, and nothing else in revision.cljc changes; (3) src/sldb/kernel/node.cljc, where validate gains the external fingerprint check of section 6.4 - the value must match one algorithm name, a colon, and one or more lower-case hex digits, with the algorithm name equal to (name (ports/algorithm (ports/hasher host))), and a failure raises :node/invalid through the same fail helper as every other shape failure; (4) the frozen external row of test/fixtures/nodes.edn, the generator in test/sldb/kernel/generators.cljc and the external assertion in node_test all move to that form, which changes the frozen id of the external row - recompute it and let bb oracle re-verify it independently in Python; (5) test/sldb/kernel/anchor_test.cljc, one falsifiable test per promise of sections 6.1 to 6.4; (6) the promise rows in docs/v2/tests/promises.md under a new section 6 heading, including invariant 18 and the replacement of the stale task reference on the :ref-hash row.

## Validation

_List the checks required before this task can close._

- bb lint
- bb test
- bb oracle

## Done When

_Name the observable condition that makes the task complete._

bb lint, bb test and bb oracle are green; every promise of docs/v2/02 section 6.2 to 6.4 has a named test in docs/v2/tests/promises.md; detaching an anchored node leaves its anchor orphan and adding supersedes leaves it superseded and re-anchored, both proven by test; runs/subagents evidence committed.
