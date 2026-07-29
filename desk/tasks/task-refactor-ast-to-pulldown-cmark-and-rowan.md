---
id: task-refactor-ast-to-pulldown-cmark-and-rowan
status: draft
summary: 'Technical Debt: Move markdown parsing from Python markdown-it to Rust pulldown-cmark + rowan'
tags:
- workspace:desk
- artifact:task
routine: routine-task-implement-rust-core
current_node: execution
history: []
references:
- desk/atoms/decision-rowan-ast.md
- desk/atoms/markdown-importer.md
depends_on: []
pills: []
files: []
checklists: []
---

# Refactor AST to pulldown-cmark and rowan

## Rationale

The initial MVP bypassed the architectural design by implementing parsing via `markdown-it-py` in Python. We must respect `decision-rowan-ast.md` to guarantee lossless reversible parsing in the Rust core.

## Goal

- Remove `markdown-it-py` dependency.
- Integrate `pulldown-cmark` in `sldb-core` to read markdown bytes.
- Fully implement `rowan` AST types that capture whitespace and trivia.

## Scope

- Rust Core `sldb-core/src/ast`
- FFI bindings for the AST.

## Done When

Lossless generation of the document AST is performed natively in Rust.
