# Markdown Round-Trip Contract

## Purpose and governing sources

This contract defines the strict requirements for reversibility between Markdown source documents and the SLDB Canonical AST.

Governing sources:
- `desk/atoms/markdown-importer.md`
- `desk/atoms/markdown-emitter.md`
- `desk/atoms/reversible-document-family.md`
- `desk/atoms/structurednldoc-contract.md`

## Reversibility Requirements

### 1. Byte-Equivalent Round-Trip
- For any document in the `reversible-document-family`, a `Render(Extract(Source))` operation must produce a file that is byte-for-byte identical to `Source`, assuming no structural changes were made to the AST.

### 2. Lossless Parsing
- White-space, comments, and idiosyncratic formatting must be preserved in the `CanonicalAST` (governed by the `decision-rowan-ast` atom and implemented via `pulldown-cmark` event preservation).

### 3. Metadata Preservation
- Frontmatter (YAML) and inline metadata (e.g., hidden comments or ID tags) must be treated as first-class AST nodes.

## Identity Handling

- **Fragment IDs**: If a Markdown section has an explicit ID (e.g., `<!-- id: xyz -->`), that ID must be preserved during extraction and re-rendered in the correct location.
- **Implicit Hashes**: For segments without explicit IDs, the system must generate stable hashes to enable change tracking.

## Phase 1 Parity Scope

- **Supported Syntax**: Full CommonMark specification + SLDB extensions (Frontmatter, hidden metadata, custom containers).
- **Transformation Floor**: Reversibility must be verified for the entire existing SLDB document corpus.

## Failure Conditions

A round-trip is considered failed if:
- Re-rendering changes the checksum of a file that underwent no structural modifications.
- Metadata is lost or re-ordered in a way that breaks external references.
- Structural relations (links/anchors) are corrupted during the cycle.
