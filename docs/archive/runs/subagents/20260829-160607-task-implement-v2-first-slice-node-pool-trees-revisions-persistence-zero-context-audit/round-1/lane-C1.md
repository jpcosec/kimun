# Lane C1 — legacy atoms a–k vs docs/v2 (round 1)

Model: claude-haiku-4-5 · type: Explore (read-only) · fresh context · 2026-08-29 · 94 atoms read

## Review

- Note [high]: desk/atoms/decision-rowan-ast.md:14 — "`rowan` (lossless syntax trees) is chosen" to satisfy reversible parsing — contradicts docs/v2/01-orden-filosofico.md:123-124 (Rowan/lossless trees are no longer a kernel requirement; replaced by the §5 guarantee)
- Note [high]: desk/atoms/decision-rayon-parallelism.md:14 — "`rayon` enables data-parallelism during the Importer phase" — contradicts docs/v2/02-sustrato-computacional.md:193 "Rust: cero en el repo"; rayon is a Rust library
- Note [medium]: desk/atoms/decision-pyo3-ffi.md:16 — "An embedded FFI such as PyO3 may exist for Python clients" — contradicts docs/v2/02-sustrato-computacional.md:193 and 01-orden-filosofico.md:130-131 (no Rust in repo; PyO3 is Rust FFI)

## Table

| file | classification | quoted contradicting sentence | v2 source contradicted |
|---|---|---|---|
| decision-rowan-ast.md | retire | "`rowan` (lossless syntax trees) is chosen. It retains all bytes…guaranteeing that rendering a non-mutated AST yields the exact original text" | 01:123-124 |
| decision-rayon-parallelism.md | retire | "`rayon` enables data-parallelism during the Importer phase" | 02:193 |
| decision-pyo3-ffi.md | rewrite | "An embedded FFI such as PyO3 may exist for Python clients" | 02:193, 01:130-131 |
| all other 91 atoms a–k | neutral | - | - |

Neutral per lane: addressability-layer, agent-provider, alias, anchor-comment, anchor-sample, append-only-event-log, artifact, ast-anchor, ast-as-the-debugging-surface, ast-command-group, ast-locator, ast-persistence, authorship-state, backup-export, cache, canonical-address, canonical-ast, canonical-existence, canonical-identity, canonicalizer, capability-model, cli-invocation-contract, cli-workflow-surface, clojure-code-linting, clojure-core, clojure-patterns, clojure-testing, code-linting, compatibility-surface, compose-rendered-document-view, concept-binding, conformance-suite, content-addressed-store, corruption-state, create-document-workflow, decision-blake3-hashing, decision-canonical-ast-over-markdown, decision-clojure-core-with-minimal-python, decision-graph-store-over-yaml-indexes, decision-links-and-anchors-are-canonical, decision-relation-ast-extensibility, decision-rusqlite-store, decision-search-index-library, decision-v1-parity-before-scope-expansion, degraded-mode, dependency-edge, dependency-index, derived-address, derived-edge, derived-index, direct-mode, docs-command-group, document-head, document-materializer, document, document-path, document-source, document-tracker, dom-locator, draft-first-model-edits, effect-outbox, effect-plan, embeddings, emitter-compiler, event-bus, external-anchor, external-anchor-resolver, failure-model, faq-command-group, field-and-section-navigation, field-binding, field-descriptions-are-required-contract, field-index, field-path, fields-command-group, find-command-group, fragment-id, garbage-collection, git-orchestration-in-python, golden-fixture, graph-projection, graph-store, hash-field, help-command-group, highlighting-engine, hook-binding, hook-runtime, how-to-get-data-out-of-sldb, identity-stability-rules, immutable-append-only-database, importer-translator.

## Verdict

Counts: retire=2 rewrite=1 neutral=91. Total atoms read=94.

## Triage note (main session)

The lane was lenient. "Neutral" candidates to be re-checked by the main session during triage because their titles name things v2 rejects or re-sequences: decision-rusqlite-store (Rust), decision-v1-parity-before-scope-expansion (v1-parity-first sequencing vs Git-model roadmap), decision-clojure-core-with-minimal-python (Python role), decision-canonical-ast-over-markdown (document-centric), canonical-identity / identity-stability-rules (identity model), document-head, graph-store.
