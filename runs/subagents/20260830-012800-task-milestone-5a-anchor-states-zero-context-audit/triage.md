# Zero-context audit gate — task-milestone-5a-anchor-states

Ritual: `desk/rituals/ritual-zero-context-audit-gate.md`. Three rounds, lanes A (internal
coherence of docs/v2 + epoch:v2 atoms) and B (executability against the A–M checklist) in
every round, lane C (contradiction sweep of the legacy knowledge base) in round 1.

| ronda | lanes | highs/mediums que sobrevivieron el triage |
|---|---|---|
| 1 | A, B, C | 9 — `§6.1` triggers, error contract, `:positions`, `:as-from`/`:as-to`, path order, `:successors`, `derived`, `§6.4` home, plus stale cross-references in `05` and `promises.md` |
| 2 | A, B | 4 — cycle semantics, `anchor/diff` `:changed` shape and naming, default `:base` with ≠1 parents, `:span` over an orphan leaf |
| 3 | A, B | 0 — one medium (`§5` did not mention the re-anchoring ops) and two lows, all corrected |

The gate rewrote `docs/v2/02 §5`, `§6.1`, `§6.2`, `§6.3`, `§6.4` and `§6.5` and the task's
implementation path. Round-by-round reports and triages under `round-1/`, `round-2/`,
`round-3/`.

**Verdict: gate closed, round 3 clean.** Implementation may start.
