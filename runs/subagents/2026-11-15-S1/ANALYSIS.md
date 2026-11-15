# S1 — clases de divergencia del CST en Markdown libre (análisis previo a medir)

Base: `main` `a9c27007`. Autoridad: `docs/v2/04 §11.2`, `12 §1` (S1), `14 §13`
etapa S.2, `runs/research/2026-10-08/S2-report.md §Fase 2` y el estado de código
de `runs/subagents/2026-11-14-s1-cst-ast/` (`65ca4b5f` restaurado en `6930afa8`).

## De dónde sale el ~3 %

El número no viene del corpus ni del desk (1099/1099) sino de un muestreo de
**3000 documentos generados** en `66ff1e9c` (`S2-report.md §Fase 2`):

| clase (tipo nativo vs pod, primer bloque divergente) | n |
|---|---:|
| mismo tipo de bloque, contenido distinto (`:content-only`) | 94 |
| `[:list]` vs `[:code]` | 1 |
| `[:quote]` vs `[:code]` | 1 |
| errores/excepciones | 0 |

96/3000 ≈ 3,2 %. Causa declarada: el pod agrupa distinto listas anidadas y
fences indentados (`  ``` `` ` cierra un fence para tree-sitter-md y no para el
perfil; `indented_code_block` agrupa a través de líneas en blanco).

Ahí la vista era **pod-driven**: `blocks-of` + `lazy-continuation` adoptaban el
nodo del pod y reparaban clases sueltas (setext, indented code, frontmatter,
fence sin info), y lo no reparado cambiaba el AST.

## Qué hace el código actual (`65ca4b5f`, en `main`)

`reconcile-blocks` recorre las líneas con el segmentador del perfil y **solo
acepta** el bloque del pod cuando su span coincide exactamente con el del perfil
(`= i cur`, `= j' prof-end`) **y** su kind coincide. En cualquier otro caso usa el
bloque del perfil. Consecuencia, para documentos sin ERROR del pod:

- los `:lines` de cada bloque son los mismos en las dos ramas, y
- la conversión (`ast/from-blocks`) es la misma función,

así que el AST del pod es **idéntico por construcción**, no por paridad medida.
La divergencia de AST en Markdown libre es, hoy, 0; lo que queda del ~3 % son
**celdas reparadas** (el pod no puede aportar el bloque) y **fallbacks**
(`:has_error` / tipo sin contrapartida → `parse-native`).

Es decir: el ~3 % está cerrado *de resultado* y abierto *de método* — la vista
no es una vista del CST donde diverge, re-segmenta con el perfil. Eso es lo que
`04 §11.2` sigue describiendo como divergencia y lo que impide que la fase 3
(anclas por rangos/nodos del CST) se apoye en los spans del pod.

## Clases a reparar (una muestra a la vez)

Cada clase se identifica como una celda `[línea pod-kind perfil-kind span?]`
(ver `sample.clj`), no como una diferencia de AST:

| # | clase | regla del perfil | comportamiento del pod | reparación conservadora |
|---|---|---|---|---|
| A | lista ordenada `9.` → `10.` | `w` del primer marcador; `10.` sigue la lista | el nodo `list` corta al cambiar el ancho | recortar/absorber el span del pod al del perfil |
| B | continuación perezosa de cita (`> a` + `b`) | la cita cierra en la primera línea sin `>` | `block_quote` incluye la línea perezosa | partir el nodo en cita + resto re-segmentado (`lazy-continuation`) |
| C | fence con backtick en el info (`~~~ `x``) | regla 2 exige info sin backticks → párrafo | fence válido de CommonMark | re-segmentar el nodo con las reglas del perfil |
| D | fence indentado `  ``` ` | no cierra un fence abierto (regla de cierre `^ {0,3}`) | tree-sitter-md lo cierra | re-segmentar ese nodo (`[:list]/[:quote]` vs `[:code]`) |
| E | tabla de una línea + html/párrafo siguiente | `:table` y `:html` absorben hasta línea en blanco | `pipe_table`/`html_block` son de una línea | extender el span al del perfil, o re-segmentar |
| F | html inline al inicio de párrafo (`<span>x</span> y`) | regla 7 → `:html` | `paragraph` (CommonMark: html inline) | mapear a `:html` sobre el nodo `paragraph` (clase que rompía `plan-test/golden-outside-profile` en `5753bd3b`) |
| G | setext y `indented_code_block` | fuera del perfil: párrafo / thematic-break | heading / code | ya reparado (`blocks-of` en `66ff1e9c`) |
| H | html dentro de item de lista | — | recuperación con `ERROR`/`:has_error` | fallback a `parse-native` (no es divergencia; es cobertura) |

Contrato: A–F son **segmentación de bloques**; no tocan inline, render, ids ni
modelos. Ninguna depende de `D0` (no hay identidad por lugar ni anclas de nodo).
Cada reparación debe dejar `AST pod = AST native` en el caso y no cambiar el
resultado de ningún documento de corpus/desk (si lo cambia, cambia ids → fuera
de S1).

## Plan de cierre

1. Medir con `sample.clj` (una muestra por invocación, `memcap 3`): N documentos
   del alfabeto de `cst_ast_parity_test` y del `freeform_divergences.md`.
   Salida por documento: veredicto de AST y celdas `[pod vs perfil]`.
2. Clasificar las celdas con la tabla A–H y contar por clase.
3. Implementar solo las clases cuya reparación deja el AST idéntico sin tocar
   corpus/desk; por cada una, caso dirigido en `cst_ast_parity_test`.
4. Medir de nuevo; lo que no se repare sin cambiar el perfil se documenta con su
   razón (perfil cerrado ≠ CommonMark) y queda como cobertura del pod (`H`).
5. Actualizar `04 §11.2` y `12 §1` con el resultado medido (hoy dicen ~3 %).

> **Cerrado (2026-11-15).** Pasos 1–2 hechos: 500 y 3000 muestras, 0
> divergencias de AST; las clases A–I están medidas y son diferencias
> documentadas del perfil, no defectos. Ver `EVIDENCE.md`. Sin cambios en
> `src/` ni `test/`.
