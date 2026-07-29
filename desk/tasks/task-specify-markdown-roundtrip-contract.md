---
id: task-specify-markdown-roundtrip-contract
status: closed
summary: Re-anchored to high-fidelity contract in docs/architecture/contracts/markdown-roundtrip-contract.md.
tags:
- workspace:desk
- artifact:task
- system:sldb
history: ["2026-07-29: closed with high-fidelity contract artifact."]
references:
- desk/atoms/markdown-importer.md
- desk/atoms/markdown-emitter.md
- desk/atoms/reversible-document-family.md
- desk/atoms/structurednldoc-contract.md
- desk/atoms/source-document-hash.md
- docs/architecture/spec2viz/target-components.yml
depends_on: []
pills:
- desk/contexts/pill-planning-contracts.md
files:
- docs/architecture/contracts/markdown-roundtrip-contract.md
checklists: []
---

# Specify Markdown Round-Trip Contract

## Rationale

Anchored in `reversible-document-family.md`. SLDB must guarantee that developer-authored Markdown is not degraded by the machine engine. Without strict byte-equivalent round-trip requirements, the system risks losing formatting, comments, or critical metadata during structural refactors.

## Goal

Define the absolute proof obligations for Markdown Import -> `CanonicalAST` -> Export reversibility. Ensure "Identity hash preservation" across the roundtrip.

## Scope

- **In-Scope**: 
  - Byte-equivalent round-trip for all `ReversibleDocs`.
  - Lossless parsing of `Frontmatter`, hidden comments, and ID tags.
  - Integration with `Blake3` source document hashing.
- **Out-of-Scope**: 
  - `Non-reversible document family` types (e.g., legacy binary artifacts).
  - External PDF/HTML materialization (Phase 2).

## Implementation Path

1. **Cycle Definition**: Map the `MarkdownImporter` -> `Rowan` -> `MarkdownEmitter` path.
2. **Metadata Rule**: Mandate that hidden `id:` tags must be treated as first-class AST nodes.
3. **Identity Checksum**: Require that `Source Document Hash` matches the re-rendered output checksum.
4. **Contract Materialization**: Write the specification in `docs/architecture/contracts/markdown-roundtrip-contract.md`.

## Validation

- Does the contract require byte-equivalent output for non-modifying cycles?
- Does it cover frontmatter and hidden ID tags?
- Does it align with the `AST -> Markdown` edge in `target-components.yml`?

## Done When

The high-fidelity `markdown-roundtrip-contract.md` is committed and approved.
