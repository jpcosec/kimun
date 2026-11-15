# M1i — selectores C3-C5 sobre el CST

## Archivos
- `src/kimun/surface/models/selector_cst.cljc` (nuevo): `cst-step?` y `extract`. Lee `ts/parse-block` directamente.
- `src/kimun/surface/models/selector.cljc`: solo el despacho. Si algún paso es CST, esos pasos van a `selector-cst` y el resto corre por la vista de bloques sin cambios; si no hay ninguno, el camino es el de antes (se extrajo `extract-blocks` sin tocar su lógica).
- `test/kimun/surface/models/selector_cst_test.cljc` (nuevo): 5 pruebas, fixtures inline.
- No se tocó nada más. Anillos: `kimun.surface.models.*` puede requerir `kimun.host.treesitter` (ya lo hace `blocks.cljc`), no hizo falta relajar nada.

## Números (`runs/subagents/2026-11-15-M1i/`)
- `lint.txt`: ok, 109 archivos. `oracle.txt`: 9 filas, 0 diferencias.
- `directed.txt`: 18 namespaces, 135 pruebas, 1893 aserciones, 0 fallos, 0 `SKIPPED` (el pod estaba). Incluye corpus cli e index, typed_docs, docs_parity, golden, parity, blocks_parity, segments*, y todo `test/kimun/surface/models/`. Ninguna prueba existente se modificó.
- `mutation.txt`: M0 (sin mi `src`) 8 fallos en 4 pruebas; M1 (C4 sin profundidad) 1; M2 (C3 cuerpo truncado) 1; M3 (C5 con fila delimitadora) 3; M4 (sin comprobar el pod) 3. M5 (el despacho también pasa los pasos CST a la vista de bloques) da 0 fallos: no discrimina, se deja constancia.

## Qué prueba cada test
1. `a-two-level-list-keeps-its-hierarchy`: lista de 3 niveles da `[{:text "a" :depth 0} {a1 1} {a11 2} {b 0}]`; el handler `:list` de siempre da `["a\n- a1\n- a11" "b"]` (aplanado).
2. `a-section-is-found-wherever-it-sits`: C3 da el mismo cuerpo con `## Notes` al final o al principio, con una sección vecina de por medio; sección ausente deja el campo fuera.
3. `a-pipe-table-gives-rows-and-cells`: C5 da `[["k" "v"] ["x" "1"] ["y" "2 | 3"]]` (cabecera primero, `\|` desescapado, sin fila delimitadora) en dos órdenes de documento.
4. `a-cst-step-without-the-pod-fails-typed`: sin pod (`ts/available?` falso), C3, C4 y C5 lanzan `:docs/parser-unavailable`. Corre siempre.
5. `the-same-steps-agree-on-both-substrates`: pasos C1/C2 dan igual con pod y sin pod; mezclados con un paso C5 solo se añade su campo.
Las de pod usan `with-pod`: sin pod imprimen `SKIPPED (no tree-sitter pod): <nombre>`. No hay mecanismo de skip previo en el repo; es un `println`, no un reporte de clojure.test (la prueba cuenta como pasada sin aserciones). La prueba 6 del plan no se corrió.

## Elecciones que el coordinador debe preguntar al dueño (todas [inf])
1. **Forma del paso C3.** Tomé la del fixture `test/fixtures/models/spec16_examples.edn:47`: `{:key "notes" :want :find :where {:outer-tag "h2" :regex "^\\QNotes\\E$"}}` (regex como cadena, no `#"…"`; spec 16 l. 129 usa `:re #"…"`; plan 3.10). R3 usaba `:where {:type :tag :re}` (`/home/jp/.cache/spikes/R3/edn_select.clj`).
2. **Qué devuelve C3.** El cuerpo de la sección (texto desde el fin del encabezado hasta el fin de la sección, subsecciones incluidas, recortado) como cadena. R3 (`edn_select.clj`, `:find` + `:body`) capturaba el bloque encabezado y el cuerpo era otro paso; el spec no dice cuál.
3. **Forma del paso C4.** Del fixture l. 50-55: `{:key "items" :outer-type "list" :preserve-nesting true}` (no hay `:want`; spec l. 130 solo dice «substrato»). R3 usaba `:want :nested-list` sobre el AST neutro.
4. **Salida de C4.** Vector en preorden `[{:text :depth}]` (el de R3, `caps_edn.clj` `ast-items` y `blocktable.clj` `nested-items`). La jerarquía es recuperable por profundidad, pero no es un árbol anidado. Cubre listas ordenadas y con viñetas (nodo `list`); el texto es el del primer párrafo del ítem.
5. **Salida de C5.** Vector de filas, cada una vector de cadenas recortadas, con la cabecera primero (R3 `rows-of`). Las celdas son cadenas: «tipables» (spec l. 131) queda para que los campos las tipen; no hay spans (eso es C6/M1j).
6. **Alcance con `:where`.** C4 y C5 aceptan un `:where` opcional (mismo formato que C3) que acota la búsqueda a esa sección; sin él toman la primera lista/tabla del documento. R3 usaba un cursor secuencial («tabla tras heading»); lo cambié por `:where` porque C3 se declara independiente del orden. Inferencia mía.
7. **Nombre del campo** = `:key` (fixture), obligatorio en pasos CST (`:models/invalid` si falta). Campo ausente = clave fuera del resultado.
8. **Error sin pod**: `:docs/parser-unavailable`, el que ya usa `parse_contract.cljc:112`. El plan decía `:parser-unavailable` a secas (ese es el estado, `diagnostics.cljc`).
9. **Solo encabezados ATX** (`atx_heading`); setext no se detecta como sección en C3.
10. **Modo.** `:strict` y `:legacy-v1-template` no cambian nada para los pasos CST (nacen sin quirks).

## Límites y avisos
- `subgraph.cljc` (`check-step!`, l. ~40) exige `:outer-type` string y `:handler` keyword en cada paso del selector de un descriptor spec 16: un paso C3/C4/C5 válido para el spec (sin `:outer-type`/`:handler`) lo rechaza `validate!`. En mis pruebas los pasos llevan `:outer-type` pero ninguno `:handler`, así que tampoco pasarían ese `validate!` tal cual. No lo toqué (fuera de mis archivos): requiere un cambio en M1a/`subgraph.cljc` o una decisión sobre qué campos son obligatorios en pasos con `:want`.
- Sin pod, un selector con pasos CST falla entero (incluidos sus pasos C1/C2): no hay degradación parcial.
- No se probó contra descriptores reales ni se conectó con `extract`/`docs` (el despacho vive en `extract-selector`; `extract.cljc` no se tocó).
- Los pasos CST usan filas del CST (no bytes), con texto de hojas `inline`; un encabezado con énfasis o enlaces dentro daría el texto de la hoja `inline` que entrega el pod, no verificado con inline complejo.

## Propuesta de línea para la fila M1 de `docs/v2/12`
«M1i hecha: `selector/extract-selector` sirve C3 (`:want :find`, cuerpo de sección en cualquier posición), C4 (`:preserve-nesting`, lista en preorden con `:depth`) y C5 (`:want :table`, filas de celdas) desde el CST (`selector-cst`); sin pod, `:docs/parser-unavailable`; el resto de pasos y el default no cambian. Pendiente de decisión del dueño: formas de paso/salida (REPORT M1i, 1-6) y la validación de `subgraph` para pasos con `:want`.»
