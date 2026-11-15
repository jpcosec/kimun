# S2a — anclas estructurales como evidencia (sin identidad)

## Qué se entregó

- `src/kimun/surface/markdown/anchors.cljc` (puro): `anchors`, `resolve-anchor`.
- `docs anchors NAME [--sections]` en `src/kimun/cli/commands/docs.cljc` (solo lectura).
- `test/kimun/surface/markdown/anchors_test.cljc` (5) y `test/kimun/cli/docs_anchors_test.cljc` (1).
- Docs: `06` (fila y párrafo), `12 §1` (fila `S2`, `S2a` implementado), `14 §13` (frase).

## Correspondencia capa de bloques → AST: 1:1 con una clase que NO lo es

Comprobado (script de sondeo, ambos parsers, misma salida) con: frontmatter + dos secciones de
nivel 1 + subsecciones con título repetido + párrafos + lista + fence + cita + tabla + html.
Criterio: mismo número de bloques y, por cada uno, que **el trozo de líneas del bloque, parseado
solo, dé exactamente un bloque con el digest del hijo del AST**.

| caso | native | treesitter |
|---|---|---|
| frontmatter, secciones anidadas, párrafos, lista, fence, cita, tabla, html | 1:1 | 1:1 |
| **cita con continuación perezosa** (`> cita` + línea sin `>`) | **no**: AST 4 hijos, capa 3 bloques | **no**: igual |

Por eso los rangos (`:lines`/`:bytes`) se emiten **por documento, solo si esa comprobación pasa**
(no es heurística: es la prueba de que el span es el bloque). Un documento con continuación
perezosa sale sin rangos y con todo lo demás (localizadores y huellas).

## Desvío de anillos

`kimun.surface.markdown.*` no puede requerir `kimun.surface.models.blocks` ni
`kimun.surface.parse-contract`. Por eso `anchors` es puro sobre el AST y recibe `:spans` y
`:parse-contract` del llamador (la CLI), que es quien comprueba el 1:1 y quien registra el
contrato. No se relajó ningún anillo.

## Pruebas

- `anchors_test` 5/5 (63 aserciones), `docs_anchors_test` 1/1 (34). Dirigidas totales con
  `docs_test`, `generic_docs_test`, `tour_test`, `contract_test`, `doc14_contract_test`:
  **38 tests / 568 aserciones, 0 fallos**.
- Mutación: con `docs.cljc` en stash y `anchors.cljc` apartado, ambas suites fallan por
  `FileNotFoundException` (exit 1); restaurado.
- `bb lint`: 105 ficheros.
