---
id: task-milestone-6-stand-off-below-the-paragraph
status: ready_for_testing
summary: ''
tags:
- workspace:desk
- artifact:task
- source:drawer
routine: routine-task-milestone-6-stand-off-below-the-paragraph
current_node: checklist-task-milestone-6-stand-off-below-the-paragraph-closeout-ready
history:
- operator-task-milestone-6-stand-off-below-the-paragraph-activate
- operator-task-milestone-6-stand-off-below-the-paragraph-ready-for-testing
references:
- desk/drawer/tasks/task-milestone-6-stand-off-below-the-paragraph.md
depends_on: []
pills:
- desk/contexts/pill-guardrail-v2-implementation-gate.md
files:
- src/sldb/kernel/ports.cljc
- src/sldb/host/text.cljc
- src/sldb/kernel/standoff.cljc
- test/sldb/kernel/standoff_test.cljc
checklists:
- checklist-task-milestone-6-stand-off-below-the-paragraph-execution-ready
- checklist-task-milestone-6-stand-off-below-the-paragraph-testing-ready
- checklist-task-milestone-6-stand-off-below-the-paragraph-closeout-ready
task_type: ''
inherits_from: []
inherit_acceptance_context: false
atoms:
- desk/atoms/atom-stand-off-annotation-below-the-paragraph.md
- desk/atoms/atom-stand-off-addresses-are-virtual-until-referenced.md
- desk/atoms/atom-anchor-state-is-derived-and-computed-per-endpoint.md
- desk/atoms/atom-inline-marks-live-on-the-block-text-leaves-stay-plain.md
closeout_evidence_verified: false
---

# Milestone 6 stand-off below the paragraph

## Rationale

_Explain why this task exists or the business driver behind it._

Not provided.

## Goal

_Describe the concrete result this task must produce._

Implement milestone 6 stand-off below the paragraph (docs/v2/02 sections 4 and 4.1). Add deterministic UAX 29 word and sentence segmentation to the TextSegmenter port and its host adapter, derived on demand and cached by leaf id, not materialized as graph nodes. Add a virtual stand-off address API of the form leaf-id plus start plus end, in grapheme offsets, that resolves against a leaf NFC text without materializing any node. The span node kind and its per-endpoint anchor-state validation (section 6.2) already exist and stay as-is. Exit criterion from 02 section 9 row 6 is an address down to the grapheme with no materialized nodes.

## Scope

_State what is in scope and what is out of scope._

In scope. One, extend the TextSegmenter protocol in src/sldb/kernel/ports.cljc with words and sentences returning vectors of grapheme-offset ranges start end over a string. Two, implement both in the host adapter src/sldb/host/text.cljc using java.text.BreakIterator on JVM and Intl.Segmenter on cljs, with offsets counted in UAX 29 grapheme clusters not UTF-16 code units, reusing the existing grapheme counting already present in host/text.cljc and ports/graphemes. Three, a new namespace src/sldb/kernel/standoff.cljc with a pure derivation of the grapheme, word and sentence layers as offset arrays, memoized by the leaf node id. Note that in this kernel a node id is its content hash, so leaf-id and leaf-hash denote the same value and the cache key is the leaf node id. Plus a virtual address resolver that takes store, leaf-id, start, end and returns the addressed text or an err when the leaf does not resolve or end exceeds the leaf grapheme count, never creating a node. Out of scope. The span node kind (src/sldb/kernel/node.cljc lines 19 to 23) and its per-endpoint anchor-state validation (src/sldb/kernel/anchor.cljc resolves fn, lines 68 to 79), which already exist and are verified. Syntax and mention layers, which are later milestones. Any change to canonical bytes, revision or store.

## Implementation Path

_Outline the expected implementation route or affected surface._

Start from the existing span node validation in src/sldb/kernel/anchor.cljc which already counts leaf graphemes via ports/graphemes. Mirror that pattern. Add words and sentences to the TextSegmenter protocol and to host/text.cljc next to the existing graphemes fn. Build src/sldb/kernel/standoff.cljc that only depends on kernel/ports, kernel/revision and kernel/err (respect the dependency rings, bb lint enforces no outward require and docstrings on public vars). Derive layers from a leaf NFC text, memoize by leaf id, and expose a resolver over a store. Write test/sldb/kernel/standoff_test.cljc following the style of anchor_test.cljc and reconcile_test.cljc.

## Validation

_List the checks required before this task can close._

- bb lint
- bb test
- bb oracle

## Done When

_Name the observable condition that makes the task complete._

The spec exit criterion of 02 section 9 row 6, an address down to the grapheme with no materialized nodes, holds. bb lint, bb test and bb oracle are all green with new tests in test/sldb/kernel/standoff_test.cljc that prove word and sentence offsets are grapheme-based and deterministic, layers are memoized by the leaf node id, and a virtual address resolves to the correct substring with no node materialized while an out-of-range or unresolved leaf returns a typed err. Evidence lives under runs/subagents and the task closes with deskops closeout commit.
