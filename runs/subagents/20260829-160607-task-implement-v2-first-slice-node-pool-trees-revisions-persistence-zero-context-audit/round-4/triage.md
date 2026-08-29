# Triage — round 4 (2026-08-29)

Totals: A high=1 (2 listed) medium=8 low=4 · B1-3 **all three ready**, high=0 medium=0 low=3 · B0 ready since round 3.
Task gate: **clean** (four tasks ready, no high/medium).
Doc gate: A still lists precision items; none is a contradiction between sections any more (round 4 "Correct" list covers every formal section).

## Lane A → docs/v2/02

| finding | decision | artifact |
|---|---|---|
| ownership `:add-edge` carries `:tree` vs "no tree" (high) | ops carrying `:tree` (new-tree, replace, move, ownership add/remove-edge) authorized per tree; add-node and non-ownership edges tree-less | 02 §5.1 check 5; atom-transactionplan… |
| binding uniqueness (high) | holds by construction for one origin (timestamp-free edge id; re-adding an existing edge id is a no-op); different origins = two evidences, deliberate | 02 §3.2 |
| drifted confidence (medium) | proposal record outside the pool, milestone 5 | 02 §6.1 |
| `:engines {}` (medium) | engines of this transaction only; no inheritance | 02 §5 |
| `:sample`/`:fingerprint` (medium) | 200 NFC graphemes; H of referenced bytes | 02 §2.1 |
| float string format (medium) | floats/inst/uuid rejected by canonical-bytes; caller pre-converts | 02 §2.1 |
| rule 6 scope (medium) | edges to/from opaque nodes unrestricted | 02 §5.1 |
| W_i self-contained (medium) | one-line definition added | 02 §2 |
| reachability formalization (medium) | transitive closure from heads; acyclic because ids are hashes | 02 §3.1 |
| span materialization timing (medium) | spans are ordinary nodes added by the plan; never implicit | 02 §4.1 |
| timestamp precision (medium) | `YYYY-MM-DDTHH:MM:SS.mmmZ`, UTC, 24 chars | 02 §5 |
| touched tree in check 1; rebase edge-set (low) | defined: ownership changes or creation; edge-set union on rebase | 02 §5.1, §5.2 |
| "escalares" wording (low) | "valores EDN atómicos" | 02 §2.1 |
| transclusion (low) | accepted debt (milestone 4 concept) | — |

## Lane B1-3 lows (accepted)

docs/v2/02 listed under `files:` as reference; Done When of M2 shows one example check while Scope names all seven; M3 Done When does not list store.edn fields. None affects executability.

## Round 5

One final A-only confirmation lane on the corrected doc, with an explicit instruction not to inflate later-milestone concerns above low. Regardless of its outcome, the gate closes after it: any remaining medium is recorded as accepted debt with its answer, because four consecutive rounds have produced no cross-section contradiction since round 3 and the task lanes are clean.
