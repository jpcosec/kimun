# A3 — informe

Estado: **terminada**. Reanudada desde el trabajo parcial del worktree
`/home/jp/proyectos/kimun-worktrees/A3` (rama `close/A3`), sin descartarlo.

## Qué cambió y por qué

Cierra las 2 brechas de paridad: aristas y export de grafo no veían los stores
enlazados. v1 las compone y califica por nombre de enlace
(`store/edge_index/federation.qualify`).

- `src/kimun/surface/edges.cljc` — `index` acepta `:linked`
  (`[{:name :store}]`, opened stores que pasa el llamador; la superficie sigue
  pura). Nuevos `qualify-node-id`, `qualify-part`, `store-parts`, `merge-parts`:
  documentos y secciones se califican (`sldb://document/annex:Table:t99`), el
  nodo store pasa a `sldb://store/<name>`, el nodo calificado lleva
  `semantics.store`; modelos, campos, tags y tipos de relación son un solo
  vocabulario y no cambian. Mismo id = un nodo (gana el local, el primero) y
  las aristas se acumulan.
- `src/kimun/cli/commands/edges.cljc` y `graph.cljc` — `linked-of` abre los
  stores enlazados con `kimun.cli.store/linked-stores {:transitive? true}` (con
  su guarda de ciclos) y los pasa bajo `:linked`. `--local` los deja fuera.
  Usan la opción `edges show|node|nodes` y todo `graph *`/`export`/`snapshot
  save`. `edges check` e `init|rebuild` quedan locales (alcance del brief).
- `src/kimun/surface/graph/store.cljc` — documenta `:linked`.
- Tests: los 2 deftest de brecha se reescriben como la promesa de v1 en
  `test/kimun/parity/closed_gaps_test.cljc` y se borran de
  `test/kimun/parity/known_gaps_test.cljc`.
- `docs/v2/tests/parity-gaps.edn` — fuera las 2 entradas.
- Docs al día: `docs/v2/06-superficies-y-cli.md`, `05-estado.md`,
  `12-ecosistema-y-pendientes.md`, `tests/parity-v1.md`.
- Extra (necesario para el marcador): `scripts/parity/coverage.py` también lee
  los corpus empaquetados `.jsonl.gz` (streaming). Sin esto, tras empaquetar
  los corpus ningún oráculo se reconocía y `--strict` fallaba. Commit aparte.

## Verificación

Todos los comandos bajo `memcap` (3 GB por defecto; ninguno lo superó, no hubo
que justificar más memoria).

`memcap bb lint`:
```
rings and docstrings ok: 96 files
```

`memcap bb oracle`:
```
9 rows, 0 mismatches
```

`memcap timeout 900 bb test`:
```
Ran 107 namespaces in 287 s
{:test 875, :pass 9443, :fail 0, :error 0, :namespaces 107}
```

Marcador estricto
`memcap python3 scripts/parity/coverage.py <v1> . --strict --allow-open-prefix selfdoc/ --allow-gaps`
(los 2 gaps que había antes → 0):
```
api/test_api_edges.py                                         13     12      0      0      1      0      0      0      0
api/test_graph_source_parity.py                               13     13      0      0      0      0      0      0      0
TOTAL                                                        590    517      1     14     55      1      0      1      1

strict: ok - 0 blocking, 0 errors, 2 unclassified under allowed prefixes, 1 gaps allowed
EXIT=0
```
Antes: 3 brechas / 515 portadas. Ahora: **1 brecha / 517 portadas** (2 brechas
menos), exit 0.

Commits (rama `close/A3`, sin push):
```
557af954 A3: aristas y grafo ven los stores enlazados (calificados por nombre de enlace)
6a9f6b91 parity: el marcador lee los corpus empaquetados (.jsonl.gz)
```

## Tests añadidos

En `test/kimun/parity/closed_gaps_test.cljc`, como la promesa de v1:
- `linked-store-documents-come-back-qualified` (v1
  `api/test_api_edges.py::test_linked_store_documents_come_back_qualified`):
  el nodo `sldb://document/annex:Table:t99` está, dice `semantics.store =
  "annex"`, el modelo tiene sus aristas `has_document` (local + enlazado) y
  `--local` los deja fuera.
- `graph-export-composes-linked-stores` (v1
  `api/test_graph_source_parity.py::test_snapshot_save_keeps_the_legacy_route_for_linked_stores_gap_G5`):
  `graph export`/`list`/`snapshot save` componen el store enlazado calificado;
  `--local` exporta solo el local.

## Desviaciones / pendiente

- Decisión recogida del brief: `edges check` no federa (queda local), aunque v1
  lo federaba; se anotó así en `06`. Si se quiere paridad exacta ahí, es un
  cambio pequeño y separable.
- Fuera de alcance y sin tocar: las superficies HTTP (`kimun.surface.http`) no
  consumen `:linked`; el brief pedía solo la CLI.
- Queda 1 brecha abierta en el marcador: `test_standalone.py::test_structured_nl_doc_requires_field_descriptions`
  (descripción obligatoria por campo), más el corpus y la abierta de selfdoc ya
  conocidos.
