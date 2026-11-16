# REPORT sin-muerto (2026-11-16)

Worktree `/tmp/kimun-nodead-wt` (sin commit; hay `git rm` e `git add -N` en el indice). Detalle por cosa: `inventory.md`.

## Numeros
- 59 archivos, +564 / -1601 lineas: neto -1037 (src -648 en 43 archivos, test -389 en 15). Contiene el ns nuevo `surface/markdown/profile.cljc` (ver ciclos).
- Borrados 4 ns enteros: `kernel/reconcile`, `surface/markdown/offsets`, `surface/markdown/report` y (de tests) `reconcile_test`, `offsets_test`.
- `bb lint` verde (101 archivos). `bb oracle` 9 filas, 0 diferencias.
- Pruebas dirigidas, 0 fallos 0 errores: kernel+graph (241 pruebas, 1909 aserciones), surface markdown/http/index (156 / 1265), los CLI pedidos + `lint` (168 / 1816), y ademas surface/models, surface/*, host y cli stores/contract/spec16/cold-cache/diagnostics (323 / 3607). Salidas en `directed.txt`. No corri `bb test` completo.
- No hay `mutation.txt`: es un corte de borrado y unificacion; las pruebas tocadas son las que dejaron de ejercitar codigo borrado, y se agrego solo `dice` en `corpus_test`.

## Borrado
1. Reconciliacion: `kernel/reconcile` entero, y `anchor/endpoint-state|anchored-in|report|diff`; `drift_test` queda con su prueba de update-plan. `dice` se movio a `surface/index/corpus` (lo usan corpus y search) y su prueba tambien.
2. Codigo muerto sin llamadores en `src/` (lista completa en inventory.md): 6 de `graph/analysis`, 11 de `graph/traversal`, `route-tree/diff`, `tree/contains-node?` y `paths-of`, `routed?`, `with-marks`, `list-address`, `split-field-id`, `cosine`, `semantic/index`, `selector-cst/extract`, `include/glob-match?`, `links/resolve-link`, `http/run-command` y `command-route`, `bad-parameter`, `routes/also`, 3 envoltorios del pod, y requires sin uso. Las pruebas dedicadas se fueron con ellos; en las demas se reemplazo la llamada (`tree/nodes`, ayudantes locales, `layers` en vez de `topological-order`).

## Unificado
`store-ref` (10 copias) -> `cli.store/store-ref`; `open`/`open-store` -> `cli.store/open-for`; `require-arg!` (6) y `usage!` (7) -> `cli.args`; `write-file!` -> `cli.store`; `commit` (edges = predicates) -> `docs/commit-built`; `now-ts` de include -> `docs/timestamp`; ids `sldb://` de `graph.edges` y `graph.io` -> `graph.model`; en Markdown `norm`/`source-lines`/`span` (cst_ast y models.blocks, y la normalizacion de newline de `selector_cst`) -> `host.treesitter/with-final-newline|source-lines|block-span`, `leading-spaces` -> `cst/leading-spaces`.
Diferencias que importan: `fields/usage!` queda como adaptador de una linea (su mensaje lleva prefijo `` `fields sub` ``); `selector_cst` mantiene su `split` con `-1` (difiere de `source-lines`). `timestamp` no era copia: es una sola funcion en `cli.commands.docs` (la usa `stores.cljc`).

## Ciclos y dependencias ascendentes
- `ast <-> cst-ast`: resuelto. Las reglas CST->AST pasaron al ns nuevo `surface.markdown.profile` (`parse`, `from-blocks`); `cst-ast` ya no requiere `ast`; `ast` requiere `cst-ast` con `:require` estatico (solo :clj) y `ast/parse-native` queda como delegacion de una linea (lo usan las pruebas de paridad). Sin cambio de comportamiento.
- `include <-> main`: resuelto. `include build` llama `stores/init` envuelto en `errors/wrap` (mismo envelope que antes) en vez de `requiring-resolve 'kimun.cli.main/run`; ya no queda ninguno de los dos en el grafo oculto.
- Dependencias que suben (H3): NO resueltas, exigen decidir frontera. `surface.http` usa `cli.errors/wrap` y `cli.out/failure`; `http.routes` y `http.views` usan `cli.out/ok|failure` (el envelope es el contrato compartido de la CLI); `http.views` abre stores con `cli.store/open!|resolve-store` y `surface.index.cache` usa `cli.store/linked-stores`. Ademas `http.views:27` sigue con `requiring-resolve 'kimun.cli.main/run` como `:run` por defecto (ciclo oculto surface->cli.main que el pedido no listaba). Mover `cli.out` y `cli.store` a una capa baja es el rediseño de H3; no lo forcé.

## Lo que NO toque (y por que)
- Los archivos vedados (`stores.cljc`, `main.cljc`, `errors.cljc`, `host/git`, `surface/changes`, selector/extract/subgraph/compile/parse_contract, parity, README/AGENTS/docs). En ellos quedan: `store-ref` propio en `stores.cljc`; `leading-spaces` y `id-of` duplicados (`surface/docs`, `surface/semantic`, `graph/edges`; `models/selector.cljc`); `template->spec16`, `compose-projections`.
- Accesores solo de pruebas que se quedan porque borrarlos obliga a reescribir muchas aserciones: `tree/hash-at` (13), `tree/nodes`, `route-tree/from-entries` (7 en 5 archivos), `fs-store/cached-ids` (5, prueba de cache), `anchor/states` (15), `graph.io/semantic-export->graph`, `graph.model/from-triples` y `add-node` (constructores de grafos de prueba), `revision/tree-count` y `edge-count`, `index.build/fingerprint`, `heartbeat/outcomes`, `mmap/empty-leaf`, `markdown.cst/text` (invariante 8), `segments/field-lines` (9). `store/replay` no es muerto (lo usa `replay-open`).
- `models/capture/capture-selector` (y su prueba de 91 lineas) no tiene llamadores en `src/`, pero es la capacidad C6 de spec 16 y vive junto a selector/subgraph que otro ejecutor edita: lo dejo para ese corte.
- Tampoco toque `.kimun/` reales ni `/home/jp/proyectos/kimun`; scripts de busqueda en `/tmp/nodead-tools/` (fuera del proyecto). Sin procesos en segundo plano.
