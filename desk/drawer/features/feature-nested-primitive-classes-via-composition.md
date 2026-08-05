# Feature: Nested Primitive Classes via Composition

## Kind

feature

## Status

open

## Problem

Downstream applications often need to define complex, nested structures (like a workflow task containing a list of conditions and operators). Currently, `sldb` handles flat documents well, but expressing nested, typed objects inside a single Markdown file requires ugly workarounds or forcing the user to create dozens of relational files.

## Desired Outcome

Leverage and expand `sldb`'s existing `__compositions__` capabilities to support first-class nested classes within a single document.
- **Inline Objects**: Allow a field like `execution_checklist: list[ChecklistItem]` where `ChecklistItem` is itself an `sldb`-aware Pydantic model.
- **Nested Extraction**: When `sldb` parses a section of the document (e.g., a Markdown list), it should be able to recursively apply the schema of the nested class to extract properties (e.g., extracting the text, the checked state, and any embedded hooks into the `ChecklistItem` instance).
- **Reduced Boilerplate**: This allows consuming applications to collapse highly relational graphs (Tasks -> Routines -> Checklists -> Conditions) into a single, cohesive, nested Markdown document.

## Questions

- How do we serialize a deeply nested Pydantic model back into a readable, flat Markdown structure?
- How does the `template_extractor` parse nested marker grammar?
- Does this overlap with the "Decoupled AST" feature, and should they be implemented simultaneously?