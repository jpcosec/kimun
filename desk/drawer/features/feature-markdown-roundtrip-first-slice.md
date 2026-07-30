---
id: feature-markdown-roundtrip-first-slice
status: proposed
summary: First implementation macrotask for Markdown import, canonicalization, and byte-level roundtrip materialization over the first reversible family.
tags:
- workspace:desk
- artifact:feature
- system:sldb
history:
- "2026-07-29: drafted as first-slice macrotask planning feature."
references:
- also_core.md
- core_README.md
- diagramas_core.md
- interfaces.md
- plan_core.md
- desk/atoms/markdown-text-surface.md
- desk/atoms/markdown-importer.md
- desk/atoms/markdown-emitter.md
- desk/atoms/reversible-document-family.md
- desk/atoms/canonical-ast.md
- docs/architecture/target-system-overview.md
- docs/architecture/spec2viz/target-components.yml
- docs/architecture/spec2viz/target-runtime.yml
- docs/architecture/spec2viz/target-anchoring.yml
depends_on:
- feature-rust-kernel-foundation-first-slice
- feature-lisp-control-and-data-surface-first-slice
---

# Markdown roundtrip first slice

## Goal

Define the first implementation lane for Markdown import, canonicalization, and byte-level roundtrip materialization for the initial reversible family.

## Includes

- Markdown as authored input surface
- parser/import path into kernel-owned canonical state
- renderer/materializer path back to Markdown
- byte-level roundtrip acceptance for the first reversible family
- anchor and selector behavior required by the first slice

## Excludes

- additional document families
- HTML/JSON parity obligations
- semantic retrieval or embeddings

## Must stay true

- authored Markdown is input, not authority
- emitted Markdown is materialized output from canonical state
- the first acceptance bar is byte-level roundtrip behavior for the initial Markdown family

## Needs design or grounding before promotion

- exact initial reversible Markdown family boundary
- exact normalization rules allowed before byte-level proof would fail
- exact anchor payload and selector behavior exercised by roundtrip fixtures
- exact fixture corpus shape for conformance proof

## Exit shape

A promotable implementation task set exists for the initial Markdown reversible family without scope creep into later surfaces.
