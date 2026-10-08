# Triage — round 3 (2026-08-29)

Totals: A high=5 medium=5 low=3 · B0 **ready** (2 medium, 1 low) · B1-3 high=4 medium=6 low=2 (M1, M2, M3 not ready).
Trend: A high 8 → 4 → 5, but round-3 highs are third-order precision issues surfaced by round-2 additions (one is a genuine contradiction I introduced: the §3 prose struct still listed `timestamp` in evidence). B0 reached ready. C lanes not run (nothing pending).

## Lane A → docs/v2/02

| finding | decision | artifact |
|---|---|---|
| §3 prose evidence lists timestamp (high) | prose struct now points to §3.2 Evidence, explicitly "sin timestamp" | 02 §3 |
| tree descriptor storage (high) | descriptor = CAS object; Revision gains `:trees` (tree-set object); **Revision has eight fields** | 02 §3.1, §5; atom-revision-id-edge-set-and-diff |
| edge object storage (high) | each edge stored at `objects/<edge-id>`; edge-set lists ids | 02 §3.2 |
| `:ref-hash` validation target (high) | plan-resolved state (base pool + nodes added by the plan) | 02 §5.1 check 4; atom-transactionplan… |
| capability format for tree-less ops (high) | `{:op x}` any tree, `{:op x :tree t}` one tree, `:all` | 02 §5.1 check 5; atom |
| literal shape (medium) | scalars only: NFC string, integer, boolean, nil, keyword; no floats | 02 §2.1 |
| canonical-bytes algorithm (medium) | pr-str with sorted map entries / set elements by `compare` of printed form, single spaces, UTF-8; allowed types enumerated; `sldb.kernel.canon` is the reference | 02 §2.1 |
| derived `:context` (medium) | optional | 02 §3.2 |
| reachability vs milestone 8 (medium) | computing reachability is part of `verify` (milestone 3); retention policy is 8; anchor states are 5 | 02 §3.1 |
| dirty set and non-ownership edges (medium) | only ownership changes mark dirty | 02 §3.1 |
| CAS phrasing (low) | single atomic heads.edn write; CAS logical per entry | 02 §3.1 |
| transitive succession (low) | re-anchor to direct successor; later replaces chain | 02 §6.1 |
| ULID (low) | defined inline | 02 §3.1 |
| B0 `:locator` example (medium) | `{:kind ... }` open map with `:kind` mandatory; fixture example | 02 §2.1 |

## Lane B1-3 → tasks

| finding | decision |
|---|---|
| M1 fixture shape abbreviated (high) | exact shape inlined in Done When |
| M1 dirty set lifecycle, "two states" | scope: dirty starts empty per apply, whole tree dirty on creation; reuse = base state → new state |
| M2 seven checks not named (high) | named in scope: base-cas, ids-exist, tree-integrity, evidence-ref-hash, capability, opaque-replace-only, pure-data |
| M2 expected shape shorthand (high) | eight fields inlined; tx-002 shape inlined |
| M2 re-anchoring scope contradiction | scope: full §5.1/§6.1 rules inside the transaction; out: heuristic reconciliation |
| M2 determinism | defined: same plan on same base state → same revision id and object set |
| M3 fixtures unnamed (high) | tx-001.edn and base plans of tx-002-conflict.edn |
| M3 close/reopen semantics | discard in-memory state + open in same process, plus one separate-bb-subprocess test |
| M3 replay inputs | log.edn + objects/ only (heads.edn deleted) |
| M3 generator | gen-plan-sequence from generators.cljc (shared file created in milestone 0) |
| B0 implementation path drift | synced; generators.cljc added to milestone 0 |

## Deviation from the ritual

The ritual caps the gate at 3 rounds. Rounds 1–3 still ended with high findings, all of which were fixable precision gaps rather than design decisions, so one confirmation round (4) is run for A and B1-3 only. If round 4 still reports highs that are not contradictions, they are recorded as accepted debt and the gate closes; this deviation is logged in desk/inbox as a suggestion to phrase the cap as "3 rounds after the last high".
