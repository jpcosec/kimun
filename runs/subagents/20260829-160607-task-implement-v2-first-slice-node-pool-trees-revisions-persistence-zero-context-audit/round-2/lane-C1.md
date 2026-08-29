# Lane C1 — legacy atoms a–k (round 2)

Model: claude-haiku-4-5 · Explore (read-only) · fresh context · 2026-08-29 · 87 atoms read

## Review
- Note [medium]: desk/atoms/document.md:16 — "A document is the stable canonical unit whose evolving states are recorded as immutable revisions" — contradicts docs/v2/01:19-22 (the pool is the unit; a document is one index form)
- Note [medium]: desk/atoms/decision-blake3-hashing.md:14 — "`blake3` is used for cryptographic hashing of nodes and documents" — contradicts docs/v2/02:366 (SHA-256 in milestones 0–3, BLAKE3 later)

## Table
| file | classification | quoted contradicting sentence | v2 source contradicted |
|---|---|---|---|
| document.md | rewrite | "A document is the stable canonical unit" | 01:19-22 |
| decision-blake3-hashing.md | rewrite | "`blake3` is used for cryptographic hashing" | 02:366 |
| all other 85 atoms a–k | neutral | - | - |

## Verdict
Counts: retire=0 rewrite=2 neutral=85. Total atoms read=87.
