# Tester lane 2 — plan / revision / heads / store / fs-store (docs/v2/02 §§5–6.1, §8.1, §10)

Model: claude-haiku-4-5 · Explore (read-only) · fresh context · 2026-08-29

## Promises (66) — OK=42 WEAK=17 UNTESTED=7
OK (selection): eight-field Revision and id (tx-001); timestamp format; six ops; all-or-nothing; pure EDN; CAS on heads; same-parent conflict + disjoint rebase; schema; the seven checks each rejecting; :replace keeps order, supersedes with plan actor, reference/binding followed; capabilities variants; ConflictSet schema; diff shape; objects/<id> with H(file)==id; heads.edn atomic; open replays and checks ids; verify; determinism and chaining properties; invariants 3, 6, 7, 12, 15, 17.
WEAK: NFC dedup across normalizations (determinism only); :move marks dirty / error names first op; subtree inheritance on replace (leaf-only test); :superseded-target and :removed-target kinds (fixture suggests, not exercised); diff mismatch-only descent; log line format; store.edn descriptor fields; replay mismatch path; invariants 1, 2, 13, 16 partially.
UNTESTED: replace of a node living in several trees; :remove-edge already removed by head; semantic/projection/derived re-anchoring rows; chained replace X''→X'→X; node in multiple trees (invariant 5 at revision level).

## Missing negative and boundary cases
Duplicate alias; alias before declaration; :remove-edge of an ownership edge; :replace of the root; :move to own descendant; empty :ops; tree-scoped capability on the wrong tree in a revision; concurrent same-edge add + rebase edge-set merge; corrupt log line; partial heads.edn write; missing object file; two real processes; store.edn with another hash algorithm; heads map for multiple touched trees; commit! retry limit.

## Top 5 by risk
1. :superseded-target / :removed-target never exercised · 2. replace in one of several trees · 3. chained replace · 4. semantic/projection/derived rows · 5. store failure modes and real concurrency.
