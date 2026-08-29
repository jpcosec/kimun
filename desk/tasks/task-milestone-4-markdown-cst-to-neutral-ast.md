---
id: task-milestone-4-markdown-cst-to-neutral-ast
status: active
summary: 'Milestone 4 (docs/v2/02 section 9 row 4, spec docs/v2/04): Markdown text
  -> lossless CST -> neutral AST -> document tree + text leaves in the pool, canonical
  render back, opaque regions, addressability report.'
tags:
- workspace:desk
- artifact:task
- source:drawer
routine: routine-task-milestone-4-markdown-cst-to-neutral-ast
current_node: checklist-task-milestone-4-markdown-cst-to-neutral-ast-execution-ready
history: []
references:
- desk/drawer/tasks/task-milestone-4-markdown-cst-to-neutral-ast.md
depends_on: []
pills:
- desk/contexts/pill-guardrail-v2-implementation-gate.md
files:
- docs/v2/04-superficie-markdown.md
- src/sldb/kernel/ports.cljc
- src/sldb/host/text.cljc
- src/sldb/surface/markdown/cst.cljc
- src/sldb/surface/markdown/inline.cljc
- src/sldb/surface/markdown/ast.cljc
- src/sldb/surface/markdown/render.cljc
- src/sldb/surface/markdown/plan.cljc
- src/sldb/surface/markdown/report.cljc
- test/sldb/surface/markdown/cst_test.cljc
- test/sldb/surface/markdown/roundtrip_test.cljc
- test/sldb/surface/markdown/plan_test.cljc
- test/sldb/kernel/generators.cljc
- test/fixtures/markdown/profile.md
- test/fixtures/markdown/profile.ast.edn
- test/fixtures/markdown/outside-profile.md
- test/fixtures/markdown/outside-profile.report.edn
- docs/v2/tests/promises.md
checklists:
- checklist-task-milestone-4-markdown-cst-to-neutral-ast-execution-ready
- checklist-task-milestone-4-markdown-cst-to-neutral-ast-testing-ready
- checklist-task-milestone-4-markdown-cst-to-neutral-ast-closeout-ready
task_type: implementation
inherits_from: []
inherit_acceptance_context: false
atoms:
- atom-sldb-md-profile-a-closed-canonical-markdown-subset
- atom-inline-marks-live-on-the-block-text-leaves-stay-plain
- atom-lossless-cst-and-canonical-render-for-markdown
- atom-addressability-report-per-document
- atom-addressability-class-per-node
- atom-canonical-content-and-node-hashing
- atom-transactionplan-edn-schema-and-validation
- atom-decision-pure-cljc-kernel-with-hosts-as-adapters
---

# Milestone 4 Markdown CST to neutral AST

## Rationale

_Explain why this task exists or the business driver behind it._

The kernel has no input surface: content can only be created by hand-written EDN plans. The first surface is Markdown under a closed canonical profile so that parse(render(A)) == A is provable and the tool can state exactly which regions of a document are operable.

## Goal

_Describe the concrete result this task must produce._

sldb.surface.markdown.{cst,inline,ast,render,plan,report} (ring 2) implementing docs/v2/04 sections 2-7: total lossless CST, profile parser to the neutral AST with grapheme-offset marks, canonical renderer with escapes, ast->plan and store->ast mapping per section 4, opaque regions per section 6, addressability report per section 7; plus the TextSegmenter port (graphemes) in sldb.kernel.ports implemented in sldb.host.text.

## Scope

_State what is in scope and what is out of scope._

In: the SLDB-MD profile of docs/v2/04 (headings, paragraphs with nested/disjoint emphasis/strong/code/link marks, fenced code, quotes, lists, breaks), HTML blocks and out-of-profile paragraphs as opaque nodes, CST with source lines, canonical render, plan generation and AST reconstruction through the kernel store, report, fixtures and property tests. Out: full CommonMark conformance, tables, footnotes, images, reference links, tree-sitter, Node parity, fine-grained editing plans (a document is re-created or replaced whole).

## Implementation Path

_Outline the expected implementation route or affected surface._

src/sldb/kernel/ports.cljc (TextSegmenter), src/sldb/host/text.cljc (BreakIterator graphemes), src/sldb/surface/markdown/cst.cljc, inline.cljc, ast.cljc, render.cljc, plan.cljc, report.cljc, test/sldb/surface/markdown/*_test.cljc, test/sldb/kernel/generators.cljc (gen-ast), test/fixtures/markdown/{profile.md,profile.ast.edn,outside-profile.md,outside-profile.report.edn}

## Validation

_List the checks required before this task can close._

- bb lint
- bb test
- bb oracle

## Done When

_Name the observable condition that makes the task complete._

bb lint, bb test and bb oracle pass with: (a) property over arbitrary strings (including CRLF, tabs, trailing blank lines): (cst/text (cst/parse s)) == s (invariant 8); (b) property over gen-ast (headings 1-6, paragraphs with nested and disjoint marks and text containing every escapable character, code with inner backticks, nested lists, quotes, breaks, opaque blocks): (parse (render A)) == A and (render (parse (render A))) == (render A) (invariant 9); (c) golden fixtures: profile.md parses to profile.ast.edn and renders back to profile.md byte-for-byte; outside-profile.md yields the frozen report and its opaque regions re-emit verbatim; (d) markdown->plan applied on an empty store then store->ast equals parse(s), and store->markdown equals render(parse(s)); text leaves shared between two documents with the same paragraph have one node id; (e) promises.md extended with the section 04 promises.
