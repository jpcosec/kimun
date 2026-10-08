# Milestone 5b — reconciliation of drifted anchors

`bb lint` ok (24 files) · `bb test` 122 tests / 526 assertions, 0 failures ·
`bb oracle` 9/9 ids, 0 mismatches.

## What was built

- **`src/sldb/kernel/reconcile.cljc`** (new, ring 0), `docs/v2/02 §6.5`: `dice` over the
  multiset of grapheme trigrams of the NFC text (integer numerator and denominator, one
  IEEE-754 division, no rounding — identical on any host); `orphans`, the index
  `{node-id [edge-id …]}` over every orphan endpoint of every active anchor; `proposals`
  with the three methods `:position`, `:fingerprint` and `:sample`, ranked by descending
  confidence and ascending candidate id, best per orphan unless `:all?`; and `accept-plan`,
  which returns the plan and applies nothing.
- **`src/sldb/surface/markdown/plan.cljc`**, `docs/v2/04 §8`: `ast->update-plan` and
  `markdown->update-plan` re-ingest a file edited outside the kernel into a tree that
  already exists — `:add-node`, one `:detach [0]` per current root child, and the ownership
  of the new AST. No `:replace`, therefore no `supersedes`: the surface sees new text and
  does not know what replaced what, which is the definition of an external change. It
  verifies the document root instead of re-rooting (`:markdown/root-changed`). `ast->plan`,
  `store->ast` and `store->markdown` are untouched and `collect` is still private.
- **`test/sldb/kernel/reconcile_test.cljc`** (7 tests) and
  **`test/sldb/surface/markdown/drift_test.cljc`** (2 tests, the roadmap exit criterion).

## What the tests found

`dice` did not normalize its input, so a decomposed and a precomposed spelling of the same
text scored 0.5 instead of 1.0. §6.5 says "del texto en NFC" and the function now does it,
through the `TextNormalizer` port. In practice node content is already normalized by
`node/make`; the bug was only reachable through the public `dice`, which is exactly what
the test exercised.

## Exit criterion of §9 row 5b, end to end

`drift_test/external-edit-orphans-the-anchor` ingests a document, anchors a `reference` on
one of its text leaves, edits the file outside the kernel, re-ingests it and asserts, in
order: no `supersedes` edge exists anywhere; the untouched leaf is still placed and
`intact`; the replaced leaf and its anchor are `orphan`; `reconcile/proposals` names the
substitute by `:position` with confidence `1.0` and carries the affected edge; and
committing `accept-plan` leaves the anchor `superseded`, the re-anchored reference `intact`
on the new leaf, and no orphan left in the revision.
