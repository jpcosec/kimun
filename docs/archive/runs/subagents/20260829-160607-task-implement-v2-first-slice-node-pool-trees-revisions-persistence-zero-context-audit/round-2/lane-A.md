# Lane A — internal coherence (round 2)

Model: claude-haiku-4-5 · Explore (read-only) · fresh context · 2026-08-29

## Review

**Correct:** content hashing and canonical form (02 §2.1 = atom-canonical-content-and-node-hashing); edge types and evidence (02 §3.2 = atom-evidence-required-per-edge-type); tree identity, tree objects, Merkle (02 §3.1 = atom-tree-identity-tree-objects-and-heads); re-anchoring rules (02 §6.1 = atom-re-anchoring-rules-under-succession); TransactionPlan primitives (02 §5.1); ConflictSet and CAS (02 §5.2); lazy span materialization (02 §4.1); first-slice runtime choices (02 §8.1); identity by hash, succession by supersedes (02 §6).

- Note [high]: 02:232-233 vs 02:127,241 — `:replace` schema has no `:order`; sibling order enters the hash and rule 3 forbids gaps — Does the new node take the old node's `:order`, or is order recalculated?
- Note [high]: 02:231 vs 02:149-151 — binding evidence requires `:context <W_i>` but the plan example leaves evidence unexpanded — Is `:context` a node id, a tree id, a symbol or a name?
- Note [high]: 02:232 vs 02:142-143, 149-151 — edge id includes evidence with `:timestamp`, so the same logical edge created at different moments has different ids; `:remove-edge <edge-id>` has no way to name "the" edge — How does a plan author reliably specify which edge to remove?
- Note [high]: 02:304-308 vs 02:208-209 — `:replace = add-node + re-enlace + supersedes` does not say which edges get re-anchored; §6.1 only says ownership is rewritten by the transaction — Are edges with `:to = old` re-anchored per §6.1 inside the same transaction, or left pointing to old?
- Note [medium]: 02:46-48 vs 02:150-151 — `<W_i>` placeholder never mapped to a concrete EDN value.
- Note [medium]: 02:212 — validation rule 5 "capability" is undefined anywhere — role? permission set? how checked?
- Note [medium]: 02:196-201 vs 02:249 — Revision fields listed but the canonical structure hashed into the revision id is not specified; milestone 2 needs deterministic revision ids.
- Note [medium]: 02:208 vs 02:305 — `:supersedes` requires `{:actor}` evidence but `:replace` has no evidence field — is the plan's `:actor` used implicitly?
- Note [medium]: 02:72-76 vs 02:172-189 — spans are nodes without ownership parents; how are they persisted/recovered in milestone 3 if not rooted in any tree?
- Note [medium]: 02:368-369 — milestone 0 "suite de conformidad de hashing" names no properties or generators.
- Note [medium]: 01:57 vs 02 — "motor"/"engine" used interchangeably; can one edge carry both `:actor` and `:engine` or exactly one?
- Note [medium]: 02:359-360 vs 02:368 — "bb vs Node not decided in the first slice" vs "validated on Babashka"; milestone 3 file atomicity differs per host — is milestone 3 constrained to Babashka semantics?
- Note [low]: 02:51 vs 02:93 — no single canonical `Evidence` map schema.
- Note [low]: 02:90 — is an Edge a persisted object (in the log/CAS) or a derived view over tree objects?

## Verdict

Counts: high=4 medium=8 low=2. Milestone 0 can start (in-memory hashing and shapes); milestones 2–3 need the `:replace`, `:context`, `:remove-edge` determinism, re-anchoring scope, revision hashing, span reachability and file-atomicity answers.
