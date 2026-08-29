# Lane A — internal coherence (round 3)
Model: claude-haiku-4-5 · Explore · fresh context · 2026-08-29

## Review
Correct: node structure and classes (§2, §2.1); edge types and evidence (§3, §3.2); tree objects, dirty set (§3.1); stand-off (§4, §4.1); Revision seven fields (§5); plan schema, seven checks, :replace semantics (§5.1, §6.1); ConflictSet and rebase (§5.2); §8.1 runtime; invariants 1, 2, 4–7, 12–17 verifiable.
- Note [high]: 02 §3:97 vs §3.2:158-163 — §3 prose edge struct still lists `timestamp` inside evidence; §3.2 formal Evidence excludes it — which is authoritative?
- Note [high]: 02 §3.1:125 vs §5:221-229 — tree descriptor "guardado en la revisión" but Revision has no such field — where is it persisted and retrieved after restart?
- Note [high]: 02 §3.1:138, §5:223 — are Edge objects stored individually at objects/<edge-id> or inside the edge-set object?
- Note [high]: 02 §5.1:272 vs §3.2:179 — validation check 4: `:ref-hash` verified against which state (base pool or plan-resolved state including nodes added in the same plan)?
- Note [high]: 02 §5.1:293-295 — capability entries `{:op :tree}` cannot express :add-node/:add-edge, which have no :tree — format for tree-independent ops?
- Note [medium]: 02 §2.1:82-83 — "literal" value shape undefined (string/number/boolean/nil?) for :proposition args and :triple :object
- Note [medium]: 02 §2.1:65-68 — canonical-bytes: encoding, key collation, cross-host determinism not pinned; reference implementation?
- Note [medium]: 02 §3.2:175 vs §2:48 — derived edges have no :context; required, optional, or undefined?
- Note [medium]: 02 §3.1:147-149 vs §9 — reachability defined but scheduled for milestone 8; can 0–3 verify without it?
- Note [medium]: 02 §3.1:142-146 — do :add-edge/:remove-edge mark the dirty set?
- Note [low]: 02 §3.1:150 vs §8.1 — "CAS por árbol" vs single heads.edn written atomically
- Note [low]: 02 §6.1:368 — re-anchor to direct successor X' or transitive end X''?
- Note [low]: 02 §3.1:124 — ULID undefined
## Verdict
Counts: high=5 medium=5 low=3. Milestones 0–3 each blocked on one of the highs (evidence/literals; descriptor storage; edge storage, ref-hash target, capabilities; reachability scope).
