# Lane C — contradiction sweep of the knowledge base (round 1)
Model: claude-haiku-4-5, fresh context, read-only.

## Findings

Note [high] docs/v2/05-estado.md:84-85 — the drawer list still names
`task-milestone-5-anchor-states-and-drifted-reconciliation` and enumerates the anchor
states as "estados `intact/superseded/drifted/orphan`", i.e. four parallel deterministic
states. The new §6.2 makes only three deterministic (`intact | superseded | orphan`) and
`drifted` a reconciliation refinement of `orphan` (§6.5). 05 is the entry point a new
reader consults first. Needs correction.

Note [medium] docs/v2/tests/promises.md:44 — deferred promise ":ref-hash como detector de
mutación" points at the now-split task name. Needs correction to
`task-milestone-5a-anchor-states`.

Note [medium] docs/v2/tests/promises.md:105 — the invariants row covers 1–17; invariant 18
(anchor state is derived; a proposal is not an object until accepted) has no row. Needs
addition.

Note [medium] desk/tasks/task-milestone-5a-anchor-states.md:7 — the summary cites the
drawer candidate it was split from. Historical provenance, not a normative claim; kept
deliberately (the drawer file was removed by the split and the reference records why).

Note [low] docs/v2/05-estado.md:93 — accepted-debt line "bytes de `:fingerprint` por
`:kind` externo (hito 5)"; the form is 5a (§6.4) and the per-kind byte recipe is the
emitting engine's contract. Imprecise, in a debt section, not authoritative.

## Verdict

Two high/medium contradictions live in `docs/v2/05` and `docs/v2/tests/promises.md`, both
stale cross-references produced by the split itself. No contradiction found in `raw/`
(correctly frozen), in the legacy atoms, or in the drawer material. The specification
itself (02, 04) is internally consistent and correctly cross-referenced.
