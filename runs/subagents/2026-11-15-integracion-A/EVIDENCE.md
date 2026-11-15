# Integración A — validación completa de tres cortes apilados

Tres cortes hechos por ejecutores en paralelo (cada uno con sus dirigidas, lint y mutación en
su propio directorio de evidencia) se apilaron sobre `ad3b2541` como tres commits y se validaron
**juntos**, una vez, en el árbol final:

| commit | corte | evidencia propia |
|---|---|---|
| `4e79f3be` | M1a — forma, jerarquía y composición del descriptor spec 16 | `runs/subagents/2026-11-15-M1a/` |
| `d20618d6` | V1 — `/stores/sense` y `/stores/health` por HTTP | `runs/subagents/2026-11-15-http-sense/` |
| `d63c0456` | R1 — archivos de release deterministas | `runs/subagents/2026-11-15-release-determinism/` |

| paso | archivo | resultado |
|---|---|---|
| `bb lint` | `lint.txt` | ok |
| dirigidas (5 namespaces) | `directed.txt` | 23 tests, 556 aserciones, 0 fallos |
| `bb test` | `full-test.txt`, `full-exit.txt` | 1155 tests, 12697 passes, 0 fallos, 0 errores, 140 namespaces, pod disponible |
| `bb oracle` | `oracle.txt` | 9 filas, 0 diferencias |
| `bin/kimun include check` | `include-check.txt` | ok |

La suite completa **no** se corrió sobre cada commit por separado: lo verificado es el árbol
con los tres. Un conflicto en la fila «Operación y vista» de `12 §1` (V1 y R1 la editan) se
resolvió a mano conservando los dos avances.

Planes de los que salieron estos cortes: `runs/research/2026-11-15/planes/` (`M1-plan.md`,
`frentes-plan.md`), escritos por agentes de solo lectura; no son autoritativos.
