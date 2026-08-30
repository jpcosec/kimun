# Triage — round 1

| # | finding | lane | resolution |
|---|---|---|---|
| 1 | §6.5's input is edge ids while its methods are about nodes; the two-orphan case is undefined | A | **§6.5**: the input is `reconcile/orphans`, `{node-id [edge-id …]}` over every orphan endpoint; both endpoints of a doubly-orphan anchor are reconciled independently; the edge ids ride along in `:edges`. |
| 2 | `:position` on a `:symbol`, `:fact` or `:span` — skip or error? | A | **§6.5**: a method that does not apply yields no candidate and never an error, said for all three cases. |
| 3 | `:markdown/root-changed` has no normative home and reads as a contradiction with "the root is never touched" | B | **04 §8**: the profile keeps the root invariant *and* the function verifies it; re-rooting would need a `:replace`, which records a succession the surface must not invent. |
| 4 | `:reconcile/unknown-node` has no normative home | B | **§6.5**: `accept-plan` checks the revision and both ends of the proposal. |
| 5 | the promise enumeration is implicit | B | Task implementation path now lists the rows to write. |
| 6 | "reusing `collect`" — called or copied, public or private, is `ast->plan` touched? | B | Task: `collect` stays private and is called; `ast->plan`, `store->ast`, `store->markdown` untouched; exactly two public functions are added. |
| 7 | `ast->update-plan`'s signature is inferred from prose | B | Task states it literally. |
| 8 | order of the proposals under `:all?` | B | **§6.5**: the same order; `:all?` changes only what is kept. |

Verified rather than asserted by lane A, and left alone: the Dice definition's totality,
determinism and portability; `accept-plan`'s compatibility with `apply-plan` and with the
`supersedes` trigger implemented in 5a; the "outside the pool" claim against invariant 18;
the expressibility of `markdown->update-plan` with the existing ops and `collect`; and the
agreement with §9 row 5b and 01 §8.

Round 2 re-runs A and B fresh on the corrected bundle.
