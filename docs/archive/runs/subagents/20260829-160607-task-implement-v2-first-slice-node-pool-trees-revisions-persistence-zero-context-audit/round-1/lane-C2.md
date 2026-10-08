# Lane C2 — legacy atoms l–z vs docs/v2 (round 1)

Model: claude-haiku-4-5 · type: Explore (read-only) · fresh context · 2026-08-29 · 110 atoms read

## Review

- Note [high]: desk/atoms/lisp-metalanguage.md:17 — "Lisp is an authored language surface around the Clojure kernel" — contradicts docs/v2/01:128 (Clojure is the Lisp; no separate metalanguage)
- Note [high]: desk/atoms/lisp-macro-language.md:16 — "The Lisp macro language is an authored input surface" — contradicts 01:128
- Note [high]: desk/atoms/lisp-schema-language.md:16 — "The Lisp schema language is an authored input surface" — contradicts 01:128
- Note [high]: desk/atoms/non-reversible-document-family.md:16 — "rather than a fully reversible render cycle" — contradicts 01:125-126 (per-node classification replaces the binary family split)
- Note [high]: desk/atoms/reversible-document-family.md:16 — "the kernel can guarantee canonical semantic round-trip" — contradicts 01:125-126
- Note [medium]: desk/atoms/ports-and-adapters.md:22 — "Tree-sitter, Rowan, redb, Cozo, Wasmtime, PyO3… are implementation details" — contradicts 01:123, 01:131
- Note [medium]: desk/atoms/markdown-emitter.md:22,42 — "closes the first safe round-trip slice" + depends_on decision-rowan-ast — contradicts 01:123, 02:91-92
- Note [medium]: desk/atoms/markdown-importer.md:42 — depends_on decision-rowan-ast — contradicts 01:123
- Note [medium]: desk/atoms/text-layer-vs-graph-layer.md:16-23 — "SLDB owns… while downstream graph systems own graph-native persistence" — contradicts 02:54-57 (S, M, G share one pool)
- Note [medium]: desk/atoms/node-reconciliation.md:16 — "matches external-source changes against canonical identity rules" — contradicts 01:65-67, 02:138-140 (identity is hash; succession is recorded)

## Table (non-neutral rows; all other 100 atoms l–z classified neutral)

| file | classification | quoted contradicting sentence | v2 source contradicted |
|---|---|---|---|
| lisp-metalanguage.md | rewrite | "Lisp is an authored language surface" | 01:128 |
| lisp-macro-language.md | rewrite | "The Lisp macro language is an authored input surface" | 01:128 |
| lisp-schema-language.md | rewrite | "The Lisp schema language is an authored input surface" | 01:128 |
| non-reversible-document-family.md | retire | "rather than a fully reversible render cycle" | 01:125-126 |
| reversible-document-family.md | retire | "guarantee canonical semantic round-trip" | 01:125-126 |
| ports-and-adapters.md | rewrite | "Rowan, redb, Cozo… PyO3… are implementation details" | 01:123, 01:131 |
| markdown-emitter.md | rewrite | depends_on decision-rowan-ast; "first safe round-trip slice" | 01:123 |
| markdown-importer.md | rewrite | depends_on decision-rowan-ast | 01:123 |
| node-reconciliation.md | rewrite | "matches external-source changes against canonical identity rules" | 01:65-67, 02:138-140 |
| text-layer-vs-graph-layer.md | rewrite | "downstream graph systems own graph-native persistence" | 02:54-57 |

## Verdict

Counts: retire=2 rewrite=8 neutral=100. Total atoms read=110.

## Triage note (main session)

Neutral candidates to re-check: phase-1-v1-replication (v1-parity sequencing), tracked-document-identity (document-centric identity), matrix-adapter (now a G index over the same pool, not an adapter), storage-backend, python-cli-orchestration-layer, transaction-plan (must match the EDN plan schema to be defined).
