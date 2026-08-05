# Feature: Decoupled AST and Field Instances

## Kind

feature

## Status

open

## Problem

In the current architecture, a field in a Pydantic model (e.g., `checklists: list[str]`) is tightly bound to a specific text extraction handler based on the marker (e.g., `⸢rev,list•checklists⸥`). This 1:1 mapping forces the AST representation to perfectly match the final field type, preventing complex or layered data extraction where one Markdown node might populate a rich, multi-property field.

## Desired Outcome

Decouple the AST node representation from the final Pydantic Field instance.
- **Separate Lifecycle**: An AST node (e.g., a Markdown task list item `- [x] Fix bug`) should be parsed into a rich intermediate AST instance (`TaskListItem(text="Fix bug", checked=True)`).
- **Mapping Layer**: The `sldb` extraction engine should then map this intermediate AST instance to the target Pydantic Field.
- **Meaningful Markers**: This allows markers to specify the *AST shape* rather than just the data type. For example, `⸢rev,ast_task_list•my_field⸥` tells the engine "parse this as a semantic task list, then hand the resulting AST instances to the field validator for `my_field`".

## Questions

- What is the common interface between an AST node instance and a Pydantic field validator?
- How do we handle bidirectional serialization (Field -> AST Instance -> Markdown String)?
- Does this require a major rewrite of the `DataExtractor` class?