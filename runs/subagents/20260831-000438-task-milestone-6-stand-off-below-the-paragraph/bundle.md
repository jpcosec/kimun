### TASK
---
id: task-milestone-6-stand-off-below-the-paragraph
status: active
summary: ''
tags:
- workspace:desk
- artifact:task
- source:drawer
routine: routine-task-milestone-6-stand-off-below-the-paragraph
current_node: checklist-task-milestone-6-stand-off-below-the-paragraph-testing-ready
history:
- operator-task-milestone-6-stand-off-below-the-paragraph-activate
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

### PILL
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

### ATOM atom-stand-off-annotation-below-the-paragraph
---
id: atom-stand-off-annotation-below-the-paragraph
title: Stand-off annotation below the paragraph
five_wh_one_plus: how
tags:
- system:sldb
- epoch:v2
- domain:text-structure
provenance: docs/v2/02-sustrato-computacional.md
---

# Stand-off annotation below the paragraph

## Answer

Below the paragraph the markup tree stops being the single hierarchy: sentences, syntax and mentions overlap inline markup (an emphasis can span two sentences). The canonical leaf is therefore the paragraph's plain text with grapheme offsets, and everything else is a typed span layer over it: inline markup and Unicode UAX #29 grapheme, word and sentence boundaries (deterministic, derived on demand and cached by leaf hash), syntax and morphology (engine projections keyed by leaf hash, engine and version), mentions (bindings to symbols). An address down to a symbol exists as (leaf hash, offset) without a materialized node; the primitive anchor (leaf, range, hash) unifies sentence, phrase, mention, fact argument and opaque region.

### ATOM atom-stand-off-addresses-are-virtual-until-referenced
---
id: atom-stand-off-addresses-are-virtual-until-referenced
title: Stand-off addresses are virtual until referenced
five_wh_one_plus: how
tags:
- system:sldb
- epoch:v2
- domain:text-structure
provenance: docs/v2/02-sustrato-computacional.md
---

# Stand-off addresses are virtual until referenced

## Answer

An address is [leaf-id start end] in UAX #29 grapheme offsets over the leaf's NFC text and resolves without any node. A :span node {:leaf :range} is materialized only when an edge needs an endpoint there; being content-addressed, the same span requested twice is the same node. Deterministic layers (graphemes, words, sentences) are offset arrays cached by leaf id, not nodes. When the leaf changes id, spans over the old leaf follow the succession rules (superseded or drifted).

### ATOM atom-anchor-state-is-derived-and-computed-per-endpoint
---
id: atom-anchor-state-is-derived-and-computed-per-endpoint
title: Anchor state is derived and computed per endpoint
five_wh_one_plus: how
tags:
- system:sldb
- epoch:v2
- domain:anchors
provenance: docs/v2/02-sustrato-computacional.md
---

# Anchor state is derived and computed per endpoint

## Answer

An anchor is an active reference, binding, projection, semantic or derived edge in a revision; ownership is structure and supersedes is the succession record, so neither is an anchor. The state is computed per endpoint and then combined with the order intact < superseded < orphan, because the re-anchoring rules follow a successor at either end. An endpoint is superseded when at least one active supersedes edge targets it (two actors may name different successors, which makes the chain ambiguous rather than invalid), intact when it still resolves, and orphan otherwise. Resolving means occupying a position in some tree of the revision for ordinary signs, recursing to the leaf and checking the range against its grapheme count for spans, and merely existing in the CAS for symbols and facts, which is a first-slice concession the milestone 7 tightens to membership of a context tree. Every anchor query is derived — recomputed from revision plus pool, never persisted, and reproducible from the log.

### ATOM atom-inline-marks-live-on-the-block-text-leaves-stay-plain
---
id: atom-inline-marks-live-on-the-block-text-leaves-stay-plain
title: Inline marks live on the block, text leaves stay plain
five_wh_one_plus: how
tags:
- system:sldb
- epoch:v2
- domain:text-structure
provenance: docs/v2/04-superficie-markdown.md
---

# Inline marks live on the block, text leaves stay plain

## Answer

A heading or paragraph maps to a :sign/:block node whose :attrs carry :marks, a vector of [kind start end] (or [:link start end url]) spans in grapheme offsets over the block's single :sign/:text child, which holds the plain NFC text without markup. Spans must be nested or disjoint and sorted by [start end kind]; otherwise the paragraph degrades to an opaque node. This keeps text leaves shareable across documents (same text, same node) while the markup differs on the block, matching the one-symbol-many-signs principle.
