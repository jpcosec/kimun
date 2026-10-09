# SPEC15-R1 — Reporte de la revisión del `15-capa-g.md` tras el gate

Rama `close/spec15`. Se corrigieron todos los hallazgos y ambigüedades de `GATE15-A`
(coherencia) y `GATE15-B` (ejecutabilidad). `bb lint` y `bb test` verdes
(`runs/subagents/SPEC15-R1-2026-10-09/`). El 14 no se tocó.

## Tabla hallazgo → cambio

### Carril A (coherencia)

| # | qué cambió (sección y frase) |
|---|---|
| A1 | `15 §1` (forma `L_i`): quitado `:properties` de `:relations`; dos viñetas nuevas («`:relations` no lleva `:properties`… una sola fuente: los hechos `(property R p) @ W_i`»). `vocabulary.edn` y `context.edn` sin `:properties`. `facts.edn` define los hechos de propiedad (`:fact/f2`, `:fact/f2b`, `:fact/f4`). |
| A2 | `15 §5`, resolución (c): «un hecho sobre el **anchor del documento**… El anchor se nombra por un término (`:symbol/:term`, p. ej. `external:<path>`) que designa el `:sign/:external`; `R`/`A`/`B` siguen siendo ids de término». |
| A3 | `15 §2` (ruteo) + `15 §11` (fila `02 §3.2`): la arista de ruteo es `:semantic` con `:relation`/`:context-target`/`:phi`, declarada como revisión de `02 §3.2`. |
| A4 | `15 §12`: fila Q3 eliminada; nota «Q3 … eliminada: la decide `14 §12` decisión 27». `15 §4` ya no la cita como pregunta. |
| A5 | `15 §3` (§ «La clave de la procedencia es `:source-hash`… `sha-256:<hex>`»), `15 §6` header (`:source-hash`), `15 §7` (`"source_hash": "sha-256:…"` + mapeo de nombre al `:source-hash` EDN), fixtures. |
| A6 | `15 §1` y `§2`: enum único `:document|:section|:paragraph|:corpus|:transversal`. |
| A7 | `15 §1` añade `{:name "literals" :root "literal"}` a `:taxonomies`; `vocabulary.edn` idem; `terms` incluye la raíz `literal`. |
| A8 | `15 §2`: `self` ceñido a «la autodescripción de kimun: el store y sus modelos, decisión 34»; la lista concreta se marca ampliación → nueva §12 Q8. |
| A9 | `15 §6` párrafo de anchors de evaluación (P8: `:sign/:opaque`, `derived`) fuera de alcance; `15 §11` «No toca» añade P8, P14 (decisión 36) y P15 (decisión 38). |
| A10 | `15 §1` + `15 §11` fila `02 §2.1`: «nombres propuestos, revisión de `02 §2.1`». |
| A11 | Creado `test/fixtures/g/source.md` (cinco párrafos). |
| A12 | `context.edn`: descriptor `{:tree :kind :name :root}` (`02 §3.1`), sin `:children` ni `:properties`; el hecho de propiedad queda en `facts.edn`. |
| A13 | `facts.edn` define `:fact/f2b` y las aristas `:edge/p2`, `:edge/p2b` (todas con alias). |
| A14 | `context.edn` declara `:ctx/doc` → `:node`. |
| A15 | `vocabulary.edn`: ejes corregidos a raíces declaradas (`comparte`/`es` → `["thing" "thing"]`, `property` → `["relation" "thing"]`); `terms` de los ejes declarados en `facts.edn`. |
| A16 | `facts.edn`: cinco hojas y **una proyección por hecho con su span propio**; cada span cubre sujeto y predicado. |
| A17 | `conflict.edn`: `:conflict/id nil` con comentario de placeholder explícito. |

### Carril B (ejecutabilidad)

| # | qué cambió |
|---|---|
| H1 | `15 §11` fila nueva «kernel (código)»: `kimun.kernel.node/shapes`, `kimun.kernel.edge/statuses`, puerto `Backend` (`kimun.kernel.store`), `kimun.kernel.anchor/resolves?`. |
| H2 | `15 §1` (invariante del plan: `:add-node :fact/:triple` sin `:projection` en el mismo plan ⇒ `:plan/rejected`) + fila en `§10` (`g/fact-without-projection-is-rejected`). |
| H3 | `15 §8`: prohibición explícita de matrices densas si el `W_i` es `:corpus`; evaluación solo por pares candidatos acotados por relación; cota `Σ_r \|A_r\|·\|B_r\|`. |
| H4 | Nueva sección `10 §11` «Capa G en disco» (forma de `facts.log.edn`, `index/graph/`, `conflicts/`); `15 §4`/`§5`/`§8` citan `10 §11`; `10` añadido a la tabla «Revisa» de `15 §11` y quitado de «No toca». |
| H5 | `15 §4`: `:upstream {:pending n}` → aviso por `:warnings` del envelope de `06 §1` (tipo `facts/pending-upstream`, `:data {:pending n}`); no se toca el envelope. |
| H6 | `15 §10`: añadidas filas para formas anidadas, propiedad-como-hecho, ejes, enum, source-hash, ruteo sin strings, transversal, tiers/status, `unsinnig`, política de promoción, `:rejected`, tres resoluciones, span semiabierto, despacho por perfil y exits. |
| H7 | `15 §10`: la promesa `p×p` se reformula como cota medible (`g/candidate-pairs-are-bounded-per-relation`). |
| H8 | `15 §7`: «aditiva pero no automática; hay que despachar por perfil tanto `validate` como el plan (`plan-documents`)»; fila en `§11` (fila `11`). |
| H9 | `15 §3` y `§11`: `:tier` **obligatorio** en `projection`. |
| H10 | `15 §4`: idempotencia **sobre nodos y aristas**; las revisiones no lo son (timestamp en su id, inv. 16). |

### Ambigüedades

| ambigüedad | lectura fijada |
|---|---|
| `:axis` | `15 §1`: cita la **raíz** de una taxonomía declarada en `:taxonomies`; no nombres de taxonomía, sub-paths ni strings. |
| `:range [a b]` | `15 §6`: **semiabierto** (`fin` exclusivo, `0 ≤ inicio ≤ fin ≤ n`). |
| unidad del `span` del protocolo | `15 §6`/`§7`: protocolo en **code points** (`"unit": "codepoints"`); interno en **grafemas de la hoja**; son formas distintas. |
| `:tiers` por relación vs orden global | `15 §1`: `:tiers` es el subconjunto admisible; el orden global solo ordena los admisibles. |
| `equivalent` vs promoción | `15 §2`: la arista de ruteo es la evidencia; `:promotions` es la política que promueve, por transacción registrada. |
| A1 `observed`/`confirmed` en dos ejes | `15 §3`: `:status` = estado de decisión; `:tier` = nivel epistémico; ejes independientes (`14 §5`). |
| A3 `:status` de la arista | `15 §3`: la arista lleva el estado de decisión; el sentido es el índice derivado `S` (`02 §3.2` revisa). |
| A4 hecho sin proyección | `15 §1`: el plan lo rechaza; `02 §2.1` describe la forma, no la admisión al pool. |

## Test de fixtures

`test/kimun/g/fixtures_test.cljc` (ring 4): los cinco EDN parsean con `clojure.edn` y se
comprueban campo a campo (descriptor `02 §3.1`, ejes, `:fact/:triple` con `:object` de
término, una proyección por hecho con span propio, `:tier` obligatorio, `:source-hash`
`sha-256:`, alias de conflicto/decisión existentes, `:conflict/id` placeholder). Pasa:
5 tests, 297 aserciones.

## Preguntas al dueño (final)

Q1, Q2, Q4, Q5, Q6, Q7 (con sus defaults, sin cambios) y **Q8** nueva (lista de términos del
transversal `self`, ampliación de decisión 34; default (a): `store`, `model`, `context`,
`fact`, `edge`, `sign`, `tree`). **Q3 eliminada** (decidida por `14 §12` decisión 27).

## Lo que NO corregí y por qué

- **`14`**: no se tocó ninguna de sus filas. Ningún hallazgo exigió contradecirlo; Q3
  eliminada es coherencia con su decisión 27, no cambio.
- **Código del kernel** (`node/shapes`, `edge/statuses`, puerto `Backend`, `anchor/resolves?`,
  despacho por perfil en `adapters`): son cambios de implementación que la spec registra en
  `§11`; no entran en una tarea de spec.
- **`README.md`**: la fila de `14` dice «decisiones 21–32» aunque el 14 tiene 21–39; es una
  inexactitud previa, ajena a los dos informes del gate. No la toqué.
- **`tests/promises.md`**: las filas nuevas de `15 §10` quedan `deferred` hasta la etapa A/C
  (no hay tests `g/*` todavía); el único test escrito es el de fixtures.
- **`06`**: no se añade a «Revisa» porque el aviso de pendientes se resolvió por `:warnings`
  (opción de H5), sin cambiar el envelope.
