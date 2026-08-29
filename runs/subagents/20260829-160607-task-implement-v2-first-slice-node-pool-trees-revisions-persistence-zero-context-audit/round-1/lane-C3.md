# Lane C3 — contracts, drawer, tasks, rituals, entry docs vs docs/v2 (round 1)

Model: claude-haiku-4-5 · type: Explore (read-only) · fresh context · 2026-08-29 · 30 files read

## Review

- Note [high]: docs/architecture/target-system-overview.md:13 — "render round-trips for reversible document families must stay exact" — contradicts docs/v2/01:121-124
- Note [high]: docs/architecture/target-system-overview.md:43-49 — "AST -> render -> AST -> render. The rendered text must remain equal across the cycle." — contradicts docs/v2/01:92, 01:98-104
- Note [high]: docs/architecture/contracts/markdown-roundtrip-contract.md:16 — "Render(Extract(Source)) operation must produce a file that is byte-for-byte identical to Source" — contradicts docs/v2/01:92, 02:238
- Note [high]: docs/architecture/contracts/clojure-canonical-core-contract.md:17-18 — "Canonical AST (Rowan) — Green Tree Architecture: Support for full-fidelity, lossless parsing" — contradicts docs/v2/01:131 (CST + neutral structural AST, not Rowan)
- Note [high]: docs/architecture/contracts/clojure-canonical-core-contract.md:35 — "Phase 1 Requirement: Markdown round-trip parity is mandatory for reversible document families." — contradicts docs/v2/01:92
- Note [high]: docs/architecture/contracts/phase-1-parity-contract.md:44 — "Source Document -> AST -> Target Document round-trip must be byte-equivalent for non-semantic changes." — contradicts docs/v2/01:92, 02:9
- Note [high]: desk/rituals/execution.md:7 — "keep the task planning-only and avoid implementation claims" — blocks an implementation task if followed literally (mitigated by pill-guardrail-v2-implementation-gate.md:16)
- Note [high]: desk/rituals/testing.md:8 — "verify no implementation progress is claimed" — same
- Note [medium]: docs/architecture/phase-1-macro-implementation-plan.md (entire) — "Phase 1 = v1 parity before scope expansion", "reversible families must preserve exact render equality" — contradicts docs/v2/01:133, 02:205-224
- Note [medium]: docs/architecture/phase-1-resolution-pack.md:36-44 — "first real proof slice must be Markdown -> Canonical AST -> Markdown ... with exact render equality" — contradicts docs/v2/01:92
- Note [medium]: docs/architecture/contracts/python-clojure-ownership-and-ffi-contract.md:30 — "FFI Boundary (PyO3)" — contradicts docs/v2/02:193
- Note [medium]: docs/architecture/contracts/clojure-canonical-core-contract.md:33 — "via `pulldown-cmark` event mapping to `Rowan` nodes" — contradicts docs/v2/02:165
- Note [medium]: docs/architecture/contracts/graph-store-contract.md:14,35 — references decision-rusqlite-store, "redb is the default embedded backend" — contradicts docs/v2/02:198 (no backend dependency)
- Note [medium]: docs/README.md:3-4 — says contracts "will be regenerated from epoch:v2 atoms" but no regeneration has occurred; contracts remain contradictory
- Note [low]: docs/architecture/contracts/atom-ontology-map.md — references decision-rowan-ast, decision-rusqlite-store, decision-pyo3-ffi

## Table

| file | classification | quoted contradicting sentence | v2 source contradicted |
|---|---|---|---|
| docs/architecture/target-system-overview.md | rewrite | "render round-trips ... must stay exact" (13); "rendered text must remain equal across the cycle" (43-49) | 01:92, 01:121-124 |
| docs/architecture/contracts/markdown-roundtrip-contract.md | retire | "byte-for-byte identical to Source" (16) | 01:92 |
| docs/architecture/contracts/clojure-canonical-core-contract.md | retire | "Canonical AST (Rowan) — Green Tree" (17-18); "round-trip parity is mandatory" (35); "pulldown-cmark ... Rowan" (33) | 01:131, 01:92, 02:165 |
| docs/architecture/contracts/phase-1-parity-contract.md | retire | "round-trip must be byte-equivalent" (44) | 01:92, 02:205-224 |
| docs/architecture/phase-1-macro-implementation-plan.md | retire | "Phase 1 = v1 parity before scope expansion"; "exact render equality" (11, 17) | 01:133, 02:205-224 |
| docs/architecture/phase-1-resolution-pack.md | retire | "exact render equality" (36-44) | 01:92 |
| docs/architecture/contracts/python-clojure-ownership-and-ffi-contract.md | rewrite | "FFI Boundary (PyO3)" (30) | 02:193 |
| docs/architecture/contracts/graph-store-contract.md | rewrite | "redb is the default embedded backend" (14, 35) | 02:198 |
| desk/rituals/execution.md | neutral (planning-era; superseded by pill for v2 tasks) | "keep the task planning-only" (7) | — |
| desk/rituals/testing.md | neutral (planning-era) | "verify no implementation progress is claimed" (8) | — |
| desk/rituals/closeout.md | neutral (planning-era scaffolding) | - | - |
| docs/architecture/tag-domain-map.md, atom-provenance-map.md | neutral | - | - |
| desk/drawer/README.md, features/*, tracks/* | neutral (deferred, sequenced after first slice) | - | - |
| desk/tasks/Board.md, task-implement-v2-first-slice-… | neutral | - | - |
| desk/contexts/pill-guardrail-v2-implementation-gate.md | neutral | - | - |
| docs/README.md, README.md, AGENTS.md, docs/faq.md | neutral (but see Answers 2) | - | - |

## Answers

1. **Duplicates/competitors of the first-slice task**: no direct duplicate. `feature-clojure-kernel-foundation-first-slice` covers the same scope (kernel, persistence, transaction validation) as a higher-level planning artifact; `feature-lisp-control-and-data-surface-first-slice` and `feature-markdown-roundtrip-first-slice` are sequenced after it; `track-first-slice-foundation` groups them.
2. **Planning-only / stale statements**: AGENTS.md:8 "Do not claim implementation progress from this worktree"; docs/faq.md:9 "runtime implementation" is "intentionally out of scope here"; desk/rituals/execution.md:7. No stale promotion queue found by the lane.
3. **Blocking ritual lines**: desk/rituals/execution.md:7 and desk/rituals/testing.md:8 block implementation tasks if followed literally; mitigated by the pill.

## Verdict

Counts: retire=8 (lane's count; 6 distinct files) rewrite=5 (3 distinct files) neutral=17. Files read=30.
Blocker status for milestones 0-3: retired/rewritten contracts do not affect the first-slice scope; they block milestone 4 (Markdown surface) if not corrected; `feature-markdown-roundtrip-first-slice` still references byte-level round-trip.

## Triage note (main session)

- The lane classified `feature-clojure-kernel-foundation-first-slice` as neutral while acknowledging identical scope; checklist §L (no two surfaces claiming the same scope) requires resolving this — retire or mark superseded.
- The lane classified `desk/tasks/Board.md` as "references v2 implementation task" — to be verified; Board.md is legacy and may still list the old promotion queue.
- AGENTS.md:8 and docs/faq.md:9 are direct fixes in prose docs (not modeled artifacts).
