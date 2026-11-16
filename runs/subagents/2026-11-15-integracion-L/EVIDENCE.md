# Integración L — P1b y S2b-cp, apilados

| corte | evidencia propia |
|---|---|
| P1b — `edges` y la exportación de `semantic` al paquete del grafo | `runs/subagents/2026-11-15-P1b/` |
| S2b-cp — rangos de code points ↔ grafemas NFC (función pura) | `runs/subagents/2026-11-15-S2b-cp/` |

Sobre `c8ca2f25`, validados juntos una vez, con pod: `bb lint` ok; `bb test`: `{:test 1251, :pass 14905, :fail 0, :error 0, :namespaces 156}`;
`bb oracle` 9 filas, 0 diferencias; `include check` ok. Después de la corrida este commit
anota ambos en las filas `P1` y `S2` de `12 §1` y guarda el plan del que salieron
(`runs/research/2026-11-15/planes/plan-2.md`, de un agente de solo lectura; no es
autoritativo); sobre ese árbol corrieron las pruebas documentales.

El plan lista nueve preguntas nuevas para el dueño (sección C) que todavía **no** están en
`docs/v2/drafts/preguntas-abiertas.md`.
