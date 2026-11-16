# P1b — `edges` y el export de `semantic` al paquete del grafo

Base `c8ca2f25`, worktree `/tmp/kimun-p1b-wt` (sin commit, como se pidió).

## Verificación previa de los `:require` (grep sobre src/ test/ scripts/)
- `kimun.surface.edges` lo requerían solo `cli/commands/{edges,links,predicates}` y `graph/store.cljc` (más pruebas). Se sostiene la afirmación del plan.
- Export de `semantic` (`export`, `export->snapshot`, `contract-version`): solo `cli/commands/semantic`, `graph/store` y pruebas. El resto de `semantic` (`model-tags`, `tags-of`, `dag`, `document-tags`…) lo requieren `index/{build,search,address}`, `http/views`, `cli/commands/sections`: se queda en la base.

## Cambios
- `git mv src/kimun/surface/edges.cljc → src/kimun/graph/edges.cljc` (ns `kimun.graph.edges`; mismos nombres de función).
  Se conservaron a propósito los tres literales persistidos/emitidos `"kimun.surface.edges"` (`:engines` del plan, facet `generated_from`): cambiarlos cambiaría los bytes/ids de salida.
- Nuevo `src/kimun/graph/export.cljc` (`kimun.graph.export`): `contract-version`, `export`, `export->snapshot`, cuerpo idéntico; llama a `sem/dag`, `sem/document-tags`, `sem/model-tags`. Se quitó de `kimun.surface.semantic` ese tramo, `contract-version` y el require de `kimun.graph.io`.
- `:require`/alias: `graph/store.cljc` (edges + `sexport`), `cli/commands/{edges,links,predicates,graph}` (solo docstrings/alias de edges), `cli/commands/semantic.cljc` (`sexport/export`).
- `scripts/check_rings.clj`: `pending-graph-crossings` pasa de `{"kimun.surface.semantic" #{"kimun.graph.io"}}` a `{}`. Ningún anillo relajado, ningún cruce nuevo.
- Pruebas movidas con `git mv`: `test/kimun/surface/edges_test.cljc` y `edges_parity_test.cljc` → `test/kimun/graph/` (ns renombrados, contenido igual salvo el require). El deftest `export-snapshot-reads-both-contract-versions` salió de `surface/semantic_test.cljc` a `test/kimun/graph/export_test.cljc` (cuerpo igual, solo `sexport/`). `cli/semantic_test`, `cli/graph_test`, `cli/predicates_test`, `parity/closed_gaps_test` (comentario): solo alias/ns.
- `docs/v2/03 §1`: qué se movió, la lista vacía, y qué queda en superficie y por qué (`models.*` y DAG de tags: los requiere la base; `models.*` además choca con el anillo por `kimun.host.`). No se tocó `docs/v2/12`.
- No tocados: `surface/docs.cljc`, `cli/commands/docs.cljc`, `surface/models/*`, `parse_contract`, kernel.

## Pruebas nuevas (2, en `test/kimun/lint_test.cljc`; reemplazan la prueba de cruce pendiente, que ya no tiene objeto)
1. `no-base-namespace-requires-the-graph-package-any-more`: `surface.semantic`→`graph.io`, `surface.semantic`→`graph.export`, `surface.docs`→`graph.edges` fallan con «the base may not require the graph package»; y `surface.semantic` sin cruce pasa (no exige entrada pendiente).
2. `the-moved-namespaces-read-the-base-from-inside-the-package`: `graph.export`→`surface.semantic` y `graph.edges`→`surface.models.store` pasan.
Mutación real (`mutation.txt`): un `:require [kimun.graph.edges]` temporal en `surface/index/search.cljc` hace fallar `bb lint` (exit 1); una entrada pendiente sobrante para `semantic` también falla («no longer needed»); restaurado idéntico (diff) y lint verde. No hice la comprobación con `git checkout src/` de la regla 5 de las reglas del ejecutor para las pruebas nuevas (las de lint se mutan sobre el script/fuente como arriba, no sobre un cambio de `src/`).

## Resultados
- `bb lint`: `rings and docstrings ok: 111 files`.
- Dirigidas (`directed.txt`), 3 tandas, 0 fallos 0 errores: A 80 tests/1117 aserciones (graph edges, edges-parity, export, semantic, graph store/io/query/traversal/analysis, lint); B 68/958 (cli edges, graph, predicates, semantic, links, parity edges, closed-gaps, spec16-readers); C 126/922 (HTTP completo, http-test, index address/build/search, cli sections).
- `bb oracle`: 9 rows, 0 mismatches.
- "Mismas respuestas": lo respaldan las pruebas de CLI/paridad existentes pasando sin tocar sus aserciones; no corrí un diff byte a byte contra un binario previo.

## Límites / notas
- No corrí `bb test` completo (lo hace el coordinador). Otras pruebas que usan `semantic`/`edges` (cli docs, index cache/where/corpus, etc.) no se corrieron: no requieren los namespaces movidos (grep), pero no lo verifiqué ejecutándolas.
- `docs/v2/14 §19` no se editó (no lo pedía el encargo).

## Línea propuesta para la fila `P1` de `12 §1`
`P1` — hecho en parte: `kimun.graph.{model,io,query,traversal,analysis,store}` (P1) y `kimun.graph.{edges,export}` (P1b); `pending-graph-crossings` vacío. Queda `kimun.surface.models.*` y el DAG de tags de `semantic` en la base (Q1, ver `03 §1`).
