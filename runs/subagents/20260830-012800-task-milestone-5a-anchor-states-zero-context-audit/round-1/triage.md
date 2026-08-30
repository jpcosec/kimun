# Triage — round 1

Every high/medium finding maps to a correction, a rejection with a reason, or a task change.
No design problem was resolved by invention; the two that changed the design (path order,
`derived` precedence) are consequences the lanes exposed, not new decisions.

| # | finding | lane | resolution |
|---|---|---|---|
| 1 | `add-edge` re-anchoring trigger unspecified (order, old/new, idempotent branch, pair shape) | A, B | **docs/v2/02 §6.1**: new paragraph fixing all four. `:from` is the successor, `:to` the replaced node; the pair is `[old new]`; re-anchoring runs after the edge joins the active set; the idempotent branch never re-anchors. |
| 2 | error contract not attached to queries | A, B | **§6.3**: errors are raised as kernel `ex-info` (docs/v2/03 §2) with the named `:type` and stated data; one line per query. |
| 3 | `:positions` order and completeness | B | **§6.3**: every position of the node in every tree of the revision, trees by id ascending, paths in document (pre-)order. |
| 4 | `:as-from`/`:as-to` shape, order, self-loop | A, B | **§6.3**: both are `anchor/state` maps ordered by edge id; an edge whose two endpoints are the same node appears in both lists. |
| 5 | "orden lexicográfico de vectores" is not Clojure's `compare` (length-first) | B | **§6.3**: paths are never sorted — they come out in document order; only ids are sorted, by ascending string order. Real defect in the text, corrected. |
| 6 | `:successors` dedup / empty | B | **§6.3**: deduplicated, ascending, `[]` when there are none. |
| 7 | `derived` remark reads like a rule | B | **§6.2**: demoted to a remark; the worst-of-two rule is the only rule. |
| 8 | fingerprint check has no home, error type or algorithm rule | A, B | **§6.4**: checked in `node/validate` so `node/make` raises `:node/invalid`; `alg` must equal the store algorithm. |
| 9 | frozen external fixture will not validate any more | A | Accepted consequence: `test/fixtures/nodes.edn`, `test/sldb/kernel/generators.cljc` and `node_test` are updated and the new frozen id is re-verified by the independent Python oracle (`bb oracle`). Added to the task's files. |
| 10 | `05-estado.md` names the unsplit task and four deterministic states | C | Corrected when the milestone closes (05 is updated at each closeout); the state list is corrected now. |
| 11 | `promises.md` names the unsplit task; invariant 18 has no row | C | Corrected during implementation, with the rest of the promise rows. |
| 12 | spec fixes indexing/caching/laziness | B | **Rejected**: no observable effect on the specified results. §6.3 says so explicitly instead. |
| 13 | (own finding, not raised by any lane) `reconcile` needs the position index, which §6.3 did not expose | — | **§6.3**: `anchor/placements` added as a public query. |
| 14 | (own finding) a `:to`/`:from` id absent from the CAS has no stated state | — | **§6.2**: an endpoint that is not in the pool is `orphan`; `add-edge` makes it unreachable, but `replay` must not depend on that. |

Round 2 re-runs lanes A and B fresh on the corrected bundle. Lane C is not re-run: its
findings are cross-references outside the specification, all scheduled.
