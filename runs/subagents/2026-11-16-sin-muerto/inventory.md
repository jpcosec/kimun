# Inventario `sin-muerto` (2026-11-16)

Evidencia de "nadie lo llama": busqueda por nombre en todo `src/` (con alias resuelto, sin
docstrings ni comentarios) con scripts temporales en `/tmp/nodead-tools/` (`dead2/3/4.py`,
`unreq.py`; no se agregaron al proyecto). Despues de cada ronda de borrado se volvio a correr
hasta que no apareciera nada nuevo. Las pruebas citadas dejaron de ejercitar lo borrado.

## 1. Reconciliacion de anclas

| Borrado | Evidencia |
|---|---|
| `kernel/reconcile.cljc` entero (`orphans`, `proposals`, `accept-plan`, helpers privados) | sin llamadores en `src/`; solo `reconcile/dice` se usaba (corpus, search) |
| `anchor/endpoint-state`, `anchored-in`, `report`, `diff` | sin llamadores en `src/` (se usan `anchor/state`, `state-order`, `placements`) |
| `test/kernel/reconcile_test.cljc`, test de reconciliacion de `drift_test.cljc`, tests `anchored-in`/`report`/`diff` de `anchor_test.cljc` | ejercitaban solo lo borrado |
| `dice` (+ `trigrams`) | movido a `surface/index/corpus.cljc` (lo usan `corpus/lexical-score` y `search`); su prueba paso a `corpus_test.cljc` |

`anchor_test.cljc` conserva sus pruebas de estados y usa dos ayudantes locales que llaman a los
privados `endpoint-state*`/`context` (los estados siguen probados sin API publica).

## 2. Codigo muerto (sin llamadores en `src/`)

Funciones y ns borrados (con sus pruebas dedicadas):
- `graph/analysis`: `paths-between`, `acyclic?`, `must-be-acyclic`, `cycle-errors`, `topological-order`, `islands`.
- `graph/traversal`: `roots`, `children`, `parent`, `neighbors-via`, `edge-between`, `neighborhood`, `targets`, `sources`, `descendants`, `kind`, `bare` (y el require `str`).
- `kernel/route_tree`: `diff`, `diff-nodes` (+ 4 pruebas y 2 aserciones de dano).
- `kernel/tree`: `contains-node?`, `paths-of` (las pruebas usan `tree/nodes`/`tree/positions`; ayudante local `paths-of` en `tree_test`).
- `surface/docs`: `routed?`, `with-marks` (privada).
- `surface/index/address`: `list-address`, `description` (+ 4 pruebas/aserciones).
- `surface/index/corpus`: `split-field-id`, `cosine` (el ayudante de prueba `bow` calcula su coseno).
- `surface/semantic`: `index`. `surface/models/selector_cst`: `extract`.
- `surface/include`: `glob-match?` (prueba con ayudante local sobre `compile-glob`).
- `surface/links`: `resolve-link` (fachada; pruebas usan `links.resolver`).
- `surface/http`: `run-command`, `command-route` (alias); `http/views`: `bad-parameter`; `http/routes`: `also` (privada).
- `host/treesitter`: `parse-inline`, `parse-lang`, `query`.
- namespaces enteros: `surface/markdown/offsets` (+ `offsets_test`), `surface/markdown/report` (+ pruebas `coverage` y la asercion de reporte del golden).
- requires sin uso quitados: `babashka.fs`, `http.views` (http.cljc); `links.resolver` (links.cljc); `host/ports/store` (edges, predicates), `err` (semantic).

Se dejaron (solo las llaman pruebas) y por que: ver REPORT.md.

## 3. Codigo copiado unificado

- `store-ref` (10 copias: 5 de dos argumentos, 5 de mapa) -> `cli.store/store-ref` (dos aridades). `stores.cljc` conserva la suya (no tocar).
- `open` (6 copias) y `open-store` (models) y el cuerpo de `corpus/opened` -> `cli.store/open-for`.
- `require-arg!` (6 copias: docs, edges, predicates, sections, semantic, graph) -> `cli.args/require-arg!`.
- `usage!` (links, find, corpus, graph, edges, predicates, fields) -> `cli.args/usage!` (aridad 3 y 4); `fields/usage!` queda como adaptador de una linea porque su mensaje lleva prefijo.
- `write-file!` (docs, fields) -> `cli.store/write-file!`.
- `commit` (edges, predicates, identicos) -> `cli.commands.docs/commit-built`.
- `now-ts` (include, copia de `docs/timestamp`) -> `cdocs/timestamp`.
- ids `sldb://` (graph.edges y graph.io): una sola definicion en `graph.model` (`store-id`, `model-id`, `field-id`, `doc-id`, `section-id`, `tag-id`, `relation-type-id`, `anchor-id`).
- Pilas de Markdown: `norm`/`norm-pod-text` -> `host.treesitter/with-final-newline` (tambien lo usa `selector_cst`), `source-lines`/`pod-lines` -> `ts/source-lines`, `span`/`pod-span` -> `ts/block-span`, `leading-spaces` (cst, cst_ast) -> `cst/leading-spaces`.

## 4. Ciclos ocultos

- `markdown.ast <-> cst-ast`: las reglas CST->AST (`parse-native`, `from-blocks`) pasaron al ns nuevo `surface/markdown/profile`; `cst-ast` ya no requiere `ast`; `ast` requiere `cst-ast` de forma estatica.
- `cli.commands.include <-> cli.main`: `include build` llama `stores/init` (envuelto en `errors/wrap`) en vez de `requiring-resolve 'kimun.cli.main/run`.
