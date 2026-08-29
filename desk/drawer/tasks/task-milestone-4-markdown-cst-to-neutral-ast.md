# Milestone 4 Markdown CST to neutral AST

ID: task-milestone-4-markdown-cst-to-neutral-ast
Status: deferred
Priority: medium

## Goal

Triage and resolve the inbox message promoted from `desk/inbox/20260829-174022-suggestion-milestone-4-markdown-cst-to-neutral-ast.md`.

## Scope

First surface: Markdown -> CST (spec-conformant parser) -> neutral structural AST -> document tree + text leaves; inverse-correct emitter with property tests parse(render(A)) == A for canonical A; opaque regions for unknown HTML; addressability report per document (docs/v2/02 section 7, section 9 row 4).

## Source

- `desk/inbox/20260829-174022-suggestion-milestone-4-markdown-cst-to-neutral-ast.md`

## Done When

- The message is resolved, answered, or promoted into active work.
