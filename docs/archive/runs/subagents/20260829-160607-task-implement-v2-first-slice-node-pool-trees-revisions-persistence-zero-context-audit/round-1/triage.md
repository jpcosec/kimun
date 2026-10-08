# Triage — round 1 (2026-08-29)

Lanes: A (coherence), B (task executability), C1/C2 (legacy atoms), C3 (contracts, drawer, tasks, rituals, entry docs).
Totals reported: A high=8 medium=8 low=2 · B high=6 medium=4 low=2, verdict not ready, split=yes · C1 retire=2 rewrite=1 · C2 retire=2 rewrite=8 · C3 retire=6 files rewrite=3 files.
Main-session re-check of lenient "neutral" verdicts added 4 retirements (C1) — Haiku under-reports contradictions when the atom uses compatible vocabulary; the executability and coherence lanes were reliable.

## Lane A → docs/v2/02 additions + atoms

| finding | decision | artifact |
|---|---|---|
| TransactionPlan EDN schema undefined (high) | define schema, aliases, 7 validation checks, all-or-nothing | 02 §5.1; atom-transactionplan-edn-schema-and-validation; invariant 15 |
| canonical content per node class undefined (high) | canonical EDN bytes rule + per class/kind content table; class/kind in hash; provenance out | 02 §2.1; atom-canonical-content-and-node-hashing |
| sibling order in Merkle (high) | tree objects `{:node :children [[id hash]…]}` in order; order enters hash by construction | 02 §3.1; atom-tree-identity-tree-objects-and-heads; invariant 14 |
| ConflictSet undefined (high) | shape + detection + automatic rebase for disjoint trees; no semantic merge | 02 §5.2; atom-conflictset-on-failed-compare-and-swap |
| re-anchoring rules per edge type (high) | table: follow (reference, binding), review (semantic), revalidate (projection), invalidate (derived) | 02 §6.1; atom-re-anchoring-rules-under-succession |
| M node content shape (high) | `:term {:name :lang}`, `:proposition {:form [...]}`; data-only in 0–3 | 02 §2.1 |
| stand-off leaf form / span materialization (high) | leaf = `{:text}` NFC; address virtual; `:span` node only when referenced | 02 §4.1; atom-stand-off-addresses-are-virtual-until-referenced |
| W_i undefined (high) | G node kind `:context` + tree-index kind `:context`; one binding per W_i | 02 §2; atom-w-i-context-node-and-index |
| EffectPlan undefined (medium) | minimal shape only; out of first slice | 02 §5.3 |
| reverse index node→trees (medium) | derived, Datascript, rebuildable | 02 §3.1 |
| multi-context bindings (medium) | one edge per W_i | 02 §2, §3.2 |
| heads vs roots (medium) | heads `{tree-id → revision-id}` CAS per tree; document head = head of its :document tree | 02 §3.1; invariant 13 |
| opaque content form (medium) | `{:format :blob}`, blob in hash; drift = id change | 02 §2.1 |
| tree storage representation (medium) | tree objects in CAS = ownership edge set; adjacency derived | 02 §3.1 |
| projection validation criteria (medium) | evidence carries `:status`; validation itself is milestone 7 | 02 §3.2 |
| evidence per edge type (medium) | table of mandatory fields; edge id includes evidence | 02 §3.2; atom-evidence-required-per-edge-type |
| opaque replace primitive (low) | `:replace` whole; check 6 of §5.1 | 02 §5.1 |
| hito 4 criterion vs §7 (low) | criterion now says "para todo A canónico" | 02 §9 |

## Lane B → task bundle

| finding | decision | artifact |
|---|---|---|
| ritual contradiction (execution.md vs pill) | legacy rituals left intact by user decision; pill states the governing ritual; `deskops next` keeps listing execution.md (lifecycle spec in deskops repo) — accepted, documented in pill | pill-guardrail-v2-implementation-gate |
| `bb test` not runnable | Babashka 1.13.219 installed in ~/.local/bin; bb.edn `test` task discovers `test/**/*_test.cljc`; deps.edn for JVM parity; `bb test` exits 0 with no namespaces | bb.edn, deps.edn |
| "generative tests" undefined | test.check properties; verified bundled in bb | 02 §8.1; atom-first-slice-runtime-choices |
| "hand-described transaction" format | `test/fixtures/tx-<n>.edn` `{:plan :expected}` | 02 §8.1; milestone-2 task |
| `files:` empty | filled on all four tasks | tasks |
| ClojureScript test path | first slice validated on bb only; host parity is a later task | 02 §8.1 |
| hashing lib on bb/cljs | SHA-256 via host Hasher; BLAKE3 later | 02 §8.1 |
| backend milestone 3 | files only | 02 §8.1 |
| four milestones in one task | umbrella task converted into milestone 0 via `deskops edit task`; milestone 1, 2, 3 tasks added with `depends_on` chain and bound pill | 4 tasks |
| M/G boundary | data shapes only in 0–3 | 02 §2.1, §8.1 |
| empty summary / placeholder execution-ready checklist (low) | summaries filled; checklist conditions are deskops scaffolding — left | tasks |

## Lanes C → retirements and rewrites

Retired to `raw/source/atoms-retired/` and untracked (16): decision-rowan-ast, decision-rayon-parallelism, decision-pyo3-ffi, decision-rusqlite-store, decision-v1-parity-before-scope-expansion, decision-clojure-core-with-minimal-python, decision-canonical-ast-over-markdown, identity-stability-rules, lisp-metalanguage, lisp-macro-language, lisp-schema-language, reversible-document-family, non-reversible-document-family, node-reconciliation, text-layer-vs-graph-layer, phase-1-v1-replication. Wikilinks and spec2viz ids relinked to the superseding epoch:v2 atom (inbox note logged).
In-place one-line fixes: markdown-importer / markdown-emitter (`depends_on` rowan → atom-reversibility-scope-in-v2); ports-and-adapters (library list).
Retired to `raw/source/architecture-retired/`: markdown-roundtrip-contract, clojure-canonical-core-contract, phase-1-parity-contract, phase-1-macro-implementation-plan, phase-1-resolution-pack.
Superseded headers added: target-system-overview, python-clojure-ownership-and-ffi-contract, graph-store-contract.
Retired to `raw/source/drawer-retired/`: feature-clojure-kernel-foundation-first-slice, feature-lisp-control-and-data-surface-first-slice, feature-lisp-surface-expansion, feature-markdown-roundtrip-first-slice, feature-phase-1-closure-package, feature-post-phase-1-scope-expansion, track-first-slice-foundation.
Prose fixes: AGENTS.md (implementation claims require evidence), docs/faq.md (scope), docs/README.md (retirement instead of "will be regenerated").
Accepted debt: matrix-adapter (rewrite at milestone 7); markdown-importer/emitter wording (milestone 4); Board.md legacy pill list (not CLI-editable; logged).

## Design questions returned to the user

None blocking. Two decisions were taken by the main session and should be confirmed at some point: (1) `class`/`kind` enter the node hash (same text as :text vs :opaque are different nodes); (2) semantic edges are not auto-followed under succession.

## Round 2

Re-launch A (docs + 22 atoms), B0 (milestone 0), B1-3 (milestones 1–3), C1/C2 (189 legacy atoms), C3 (docs/architecture, drawer, tasks, rituals, entry docs).
