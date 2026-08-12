# Feature: Separation of AST and Template

## Kind

feature

## Status

open

## Problem

Currently, `sldb` relies heavily on strict template string matching (flat regex replacement via `⸢rev•field⸥` markers) to extract data. This tightly couples the extraction logic to the exact layout of the template. If a user modifies the Markdown structure in ways the regex doesn't expect, extraction can fail or corrupt data. It also prevents the system from semantically understanding native Markdown structures like task lists or headers.

## Desired Outcome

Redefine the core architecture so that `Document = AST + Template`. 
- **AST Parsing**: The system should parse the raw Markdown into a full Abstract Syntax Tree (AST) first.
- **Template as Schema**: The `__template__` string should act purely as a mapping schema, telling the extractor *which* AST nodes correspond to *which* Pydantic fields.
- **Rendering**: When rendering, the system combines the hydrated fields with the Template to generate the final Markdown, ensuring structural integrity while preserving the semantic richness of the AST.

## Questions

- How do we handle complex nested AST nodes that map to a single string field?
- Does `markdown-it-py` provide enough fidelity for two-way synchronization without losing human-added whitespace?
- What happens to the existing `⸢rev•⸥` markers in this paradigm? Do they become AST node attributes instead of raw text?