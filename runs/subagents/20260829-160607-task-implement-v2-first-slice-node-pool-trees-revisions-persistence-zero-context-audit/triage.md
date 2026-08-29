# Zero-context audit gate — final triage (2026-08-29)

Task bundle: milestone 0 (task-implement-v2-first-slice-node-pool-trees-revisions-persistence), milestones 1–3 (task-implement-v2-milestone-{1,2,3}-…). Knowledge base: docs/v2 + 24 epoch:v2 atoms + 191 legacy atoms + docs/architecture + drawer + tasks + rituals + entry docs.
Lanes: Haiku 4.5, Explore (read-only), fresh context, no chat context. Five rounds, 20 lane runs. Per-round reports and triage under round-1 … round-5.

## Trajectory

| round | A (docs) high/med | B (tasks) | C (KB) |
|---|---|---|---|
| 1 | 8 / 8 | not ready (one umbrella task) | 20 retire/rewrite across atoms, contracts, drawer |
| 2 | 4 / 8 | B0 not ready; M1 partial, M2 not ready, M3 partial | 3 rewrite (retired) |
| 3 | 5 / 5 | B0 **ready**; M1–M3 not ready | — |
| 4 | 1 / 8 | M1, M2, M3 **ready** (0/0) | — |
| 5 | **0 / 2** | — | — |

## Gate status: **CLOSED — satisfied**

- Task lanes: all four tasks ready with zero high/medium (B0 round 3, B1-3 round 4).
- Doc lane: round 5 has zero high; the two mediums were answered in 02 §5.1 (NFC normalized, not rejected; `:remove-edge` may reference an `:as` alias from the same plan) before closing.
- KB lanes: 19 legacy atoms, 5 architecture docs and 7 drawer items retired to raw/source/*-retired/ with relinks; no contradiction left in rounds 2 sweeps; graph missing targets reduced from 15 to 1 (pre-existing `.yml` extraction gap).

## Accepted debt (low)

- "transclusion" undefined (milestone 4 concept); `:fingerprint` bytes per external `:kind` (milestone 5); docs/v2/02 listed under `files:` as a reference; M2 Done When shows one example check while Scope names all seven; M3 Done When does not enumerate store.edn fields.
- Legacy rituals (execution/testing/closeout) remain planning-era by user decision; `deskops next` keeps listing execution.md and pill-planning-contracts via the legacy Board (not CLI-editable); the governing ritual for v2 tasks is stated in pill-guardrail-v2-implementation-gate.
- Ritual cap: 5 rounds were run instead of 3; suggestion logged in desk/inbox to phrase the cap as "3 rounds after the last high".

## Design decisions taken by the main session during triage (to confirm with the user at leisure)

1. `class`/`kind` enter the node hash (same text as `:text` vs `:opaque` are different nodes).
2. Semantic edges are not auto-followed under succession; reference/binding are.
3. Timestamp is not part of an edge; the same assertion by the same origin is one edge (idempotent); different origins are two evidences.
4. Tree ids are nominal ULIDs; descriptors are CAS objects referenced by the revision tree-set; Revision has eight hashed fields.
5. First slice: SHA-256, files-only backend, Babashka-only validation, M/G as data shapes.

## User confirmation (2026-08-29, after milestone 0 closeout)

All five design decisions above were reviewed and **confirmed by the user** as stated: (1) class/kind enter the node hash; (2) reference/binding follow the successor, semantic/projection stay superseded, derived recompute; (3) timestamp outside the edge id, same origin ⇒ one edge, different origins ⇒ two evidences; (4) nominal ULID tree ids with descriptors in the CAS, eight-field Revision; (5) first-slice choices (SHA-256, files-only backend, Babashka-only validation, M/G as data shapes). No changes requested. Milestone 1 may start.
