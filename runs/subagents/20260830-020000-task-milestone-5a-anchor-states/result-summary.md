# Milestone 5a — anchor states

`bb lint` ok (23 files) · `bb test` 113 tests / 469 assertions, 0 failures ·
`bb oracle` 9/9 ids, 0 mismatches.

## What was built

- **`src/sldb/kernel/anchor.cljc`** (new, ring 0): the seven derived queries of
  `docs/v2/02 §6.3` — `placements`, `endpoint-state`, `state`, `states`, `anchored-in`,
  `report`, `diff` — plus `anchor-types` and `state-order`. Pure over `(store, revision)`,
  nothing persisted (invariant 18). States per §6.2: `superseded` when an active
  `supersedes` targets the endpoint, `intact` when it resolves, `orphan` otherwise; the
  edge takes the worst of its two endpoints. `resolves?` recurses through `:span` into its
  leaf and checks the range against the leaf's grapheme count.
- **`src/sldb/kernel/revision.cljc`**: `add-edge-op` gained the second re-anchoring
  trigger of §6.1 — a *newly added* `supersedes` edge records `[old new]` and runs
  `re-anchor` with the edge already active; the idempotent branch never re-anchors.
  `re-anchor` was moved above `add-edge-op` (a pure relocation, body unchanged).
- **`src/sldb/kernel/node.cljc`**: `fingerprint-ok?` and the §6.4 check inside `validate`
  — an external `:fingerprint` must be `<alg>:<hex>` with the store's algorithm.
- **`test/fixtures/nodes.edn`**, `generators.cljc`, `node_test.cljc`: moved to that form.
  The external row's frozen id changed to
  `b44fd9099808bc7729937b5b0597b2ae6ee98bfd37857418eab6319d422d6890`, **independently
  recomputed by `scripts/canon_oracle.py`** (Python, no Clojure involved).
- **`test/sldb/kernel/anchor_test.cljc`** (new): 13 tests covering every promise of
  §6.1–§6.4, listed in `docs/v2/tests/promises.md`.

## What the tests found

Writing the re-anchoring test surfaced a real consequence of §6.2 rather than a bug: if a
`supersedes` names a successor that is **not placed in any tree**, the re-anchored edge is
`orphan`, not `intact`. That is correct — the successor has no ground — and the test was
rewritten to the shape an accepted reconciliation actually has: the successor is already
in the tree (an external edit put it there) and the actor then records the succession.

## Exit criteria of §9 row 5a

- detaching the anchored node leaves the anchor `orphan` — `anchor_test/detaching-the-referent-orphans-the-anchor`
- registering `supersedes` leaves it `superseded` and re-anchors per §6.1 —
  `anchor_test/adding-a-supersedes-edge-re-anchors-and-records-the-pair`
- every query is derived and reproducible — `anchor_test/queries-are-pure-derived-and-deterministic`
