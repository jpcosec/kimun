# Feature: Executable Markdown Hooks

## Kind

feature

## Status

open

## Problem

Workflow orchestration tools built on `sldb` (like `deskops`) need to represent executable state transitions (e.g., checking if a test passes). Currently, `sldb` only stores static strings and lists. This forces downstream tools to invent complex, external primitive files to hold execution logic, breaking the locality of behavior.

## Desired Outcome

Introduce a mechanism to embed and execute hooks directly within `sldb` Markdown documents.
- **Syntax**: Support a syntax like `<!-- hook: execute_python, target: mymodule.MyClass -->` or structured codeblocks attached to specific AST nodes (like a checklist item).
- **Execution**: Provide a runtime utility in `sldb` that can parse these hooks, dynamically invoke the referenced Python classes, scripts, or system commands, and capture their return values (e.g., boolean success, or an exit code).
- **State Mutation**: Allow the hook's return value to directly influence the `sldb` extraction and rendering cycle (e.g., automatically ticking a Markdown checkbox if the linked test script returns `True`).

## Questions

- How do we sandbox the execution of arbitrary Python classes or bash scripts embedded in Markdown?
- Should the hook execution logic live in `sldb` core, or should `sldb` merely parse the hooks and leave execution to the consuming library (like `deskops`)?
- How are hook arguments passed and validated?