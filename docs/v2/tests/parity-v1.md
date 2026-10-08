# Paridad con sldb v1 — libro y DAG (cerrado)

> Objetivo: `kimun` equivalente a sldb v1, medido contra los tests de v1
> (`hum-ecosystem/tools/sldb/tests`: 82 archivos, 590 funciones). Cada función de v1 queda
> en un estado del marcador: **portada** (test kimun que ejerce la misma promesa),
> **mapeada** (otro test kimun), **oráculo** (cubierta por el corpus diferencial grabado
> desde v1), **n/a** (mecanismo interno que v2 reemplaza; se nombra el test del kernel que
> sostiene la promesa), **brecha** o sin clasificar (**corpus**, **abierta**). El mapeo de
> comandos está en `docs/v2/06-superficies-y-cli.md`; las superficies retiradas, en ese
> mismo documento.

## Marcador (se regenera con `python3 scripts/parity/coverage.py <v1> .`)

Estado al 2026-10-08, sobre las 590 funciones de test de sldb v1:

| estado | funciones | qué significa |
|---|---|---|
| portada | 515 | un test de kimun la nombra y ejerce la misma promesa |
| mapeada | 1 | cubierta por un test de kimun con otro nombre (`parity-map.edn`) |
| oráculo | 14 | su tema es el motor y los corpus diferenciales la reproducen (`parity-oracle.edn`) |
| n/a | 55 | probaba un mecanismo que v2 no tiene por diseño; cada una con su razón y lo que sostiene la promesa hoy (`parity-na.edn`) |
| brecha | 3 | promesa de v1 que kimun **no** cumple todavía (`parity-gaps.edn`, fijadas en `test/kimun/parity/known_gaps_test.cljc`) |
| corpus | 1 | produjo casos del corpus sin declararse tema del motor: `selfdoc/test_preflight.py::test_before_image_guard_prevents_clobbering_concurrent_edits` |
| abierta | 1 | `selfdoc/test_python_scanner.py::test_kgdb_graph_check_rejects_missing_or_stale_source_nodes` |

Brechas abiertas (`parity-gaps.edn`): (1) aristas y export de grafo no ven los stores
enlazados (2 funciones); (2) no se exige descripción por campo del modelo (1 función).
Pendientes no cubiertos por el marcador: un documento `superseded` se lee bajo el contrato
nuevo, y `models field-add` de un campo opcional reasigna el último hueco de los documentos
existentes (`test/kimun/parity/known_gaps_test.cljc`).

## Cómo se mide

1. **Oráculo diferencial del motor**: un plugin de pytest corre la suite de v1 y graba cada
   llamada a extracción y render (clase de modelo → descriptor EDN, Markdown de entrada,
   payload, Markdown renderizado) en `test/fixtures/parity/engine/`. El motor de kimun
   tiene que reproducir cada caso. Son literalmente los mismos tests, en la frontera del motor.
2. **Tests portados**: para store, CLI, grafo, consultas y serve, cada test de v1 se
   reescribe contra la CLI/API de kimun con el vocabulario de `06-superficies-y-cli.md`.
3. **Corpus real**: `migrate --from-v1` sobre el `.sldb` de sldb (1.551 documentos) y
   comparación payload a payload.

## DAG

| id | tarea | depende de | estimado | paralelo | archivos v1 que cubre |
|---|---|---|---|---|---|
| T1 | oráculo + motor de modelos: frontmatter/tabla opacos en CST, `kimun.surface.models.{descriptor,marker,recipe,extract,render,validate}`, exportador Python de clases v1 | — | 120 min | tanda A | test_standalone, test_marker_invariants, test_section_body_extraction, test_validation_semantics, test_field_ownership, test_extraction_segments, test_document_segments, test_segment_structure, api/test_field_blocks, store/test_optional_field_hash_neutral, test_store_readme_doc, store/test_document_formats |
| A2 | `--where`: parser de la sintaxis v1 y de la forma EDN a AST cerrado, evaluador | — | 30 min | tanda A | store/test_where_empty_and_errors, store/test_queries_expanded |
| A3 | algoritmos de grafo puros: vecindario, camino, ciclos, orden, componentes, centralidad, similitud, lenguaje de consulta, io node-link | — | 45 min | tanda A | store/test_graph_{analysis,executor,io,language,neighborhood}, api/test_api_graph_analysis |
| A4 | enlaces: parser `[[…]]`, resolución, transclusión `![[…]]` | — | 30 min | tanda A | test_links (parte pura), test_composition, api/test_composition_ref_root |
| T1b | segmentos (spans de campo y literales, ownership por sección) y composiciones de modelo en el render | T1 | 60 min | con B1 | test_field_ownership, test_segment_structure, test_extraction_segments, test_document_segments, api/test_field_blocks, test_composition, api/test_composition_ref_root |
| B1 | modelos en el store y CLI: `models define\|list\|show\|check\|history`, `extract\|render\|validate` directos | T1 | 60 min | serie | api/test_api_models, api/test_model_lifecycle, api/test_api_model_drafts, store/test_models_io, store/test_contract_in_model_node, store/test_resolver |
| B2 | documentos con modelo: `docs create\|track\|update\|untrack\|delete\|show\|list`, `fields`, `sections` | B1 | 90 min | serie | api/test_api_creation, api/test_api_documents, api/test_api_stores, store/test_cli_store, test_cli_v2, store/test_section_rebuild, api/test_structural_noise, test_cli_reconciliation, store/test_update_fault_tolerance, store/test_hashing |
| C1 | índice derivado, `find`, direcciones, `--global`, `stores link` | B2, A2 | 60 min | serie (base de C2–C5) | test_address_surface, test_field_corpus, test_linked_structural, api/test_api_store_fingerprint |
| C2 | aristas, relaciones, predicates, `graph *`, export/snapshot | C1, A3 | 60 min | tanda C | api/test_api_edges, api/test_api_graph, api/test_graph_source_parity, api/test_declared_references, api/test_composition_edges, test_relation_models, test_predicates_cli, store/test_edge_anchor_targets, store/test_semantic_export |
| C3 | semántica: tags, DAG, equivalencias | C1 | 45 min | tanda C | api/test_api_semantic, store/test_semantic_dag_walk, store/test_semantic_rebuild |
| C4 | journal como vista del log: `journal show\|verify` | B2 | 30 min | tanda C | api/test_journal |
| C5 | `docs recover\|compose` en CLI | B2, A4 | 30 min | tanda C | test_links (parte CLI) |
| C6 | diagnósticos de `stores check` | C1 | 30 min | tanda C | store/test_diagnostics, store/test_catalog_reconcile |
| D1 | `serve` HTTP | C1–C3 | 90 min | serie | test_serve, test_serve_auth, test_serve_corpus_similar, test_serve_edges_docs, test_serve_stores_check |
| E1 | `migrate --from-v1` y corrida sobre el corpus real | B2 | 60 min | con D1 | — |
| E2 | selfdoc (adaptadores código → documentos) | B2 | 60 min | con D1 | selfdoc/* |
| K1 | **escala del kernel**: abrir sin reproducir el log, conjuntos de árboles y aristas con estructura compartida, acceso perezoso a objetos | E1 | 120 min | con D1b, E2 | — |
| E3 | cierre del libro: mapa test a test, n/a justificados | todo | 30 min | — | los n/a de abajo |

Regla de paralelismo: una tanda corre en paralelo solo si sus tareas crean namespaces y
tests nuevos sin tocar archivos compartidos; el cableado en `kimun.cli.main` y
`kimun.cli.errors` lo hace el integrador al fusionar. Con solape, en serie y la más corta primero.

## n/a previstos (mecanismos internos de v1)

`store/test_cache_identity`, `test_documents_shards`, `test_io_atomic_write`,
`test_legacy_layout_migration`, `test_merkle_cost`, `test_merkle_operation_cache`,
`test_merkle_shards`, `test_migration_hash_names` (×2), `test_runtime_cache_outside_store`,
`test_stale_shard_hashes`, `test_store_name_relpaths`, `test_runtime_cache`,
`test_clean_code_rules`: shards, cachés por capas, migraciones de layout y de nombres de
hash. En v2 no existen: los objetos son inmutables y direccionados por contenido. E3
nombra, para cada uno, el test del kernel que sostiene la promesa equivalente.

## Estado

| id | estado | evidencia |
|---|---|---|
| S2 primer corte | hecho 2026-10-06 | `e444d4d`, `test/kimun/cli/docs_test.cljc` |
| A4 enlaces | hecho 2026-10-06 | `13184d9`; `kimun.surface.links.{parser,resolver,recovery,composer}`; 8/8 de `test_links.py` a nivel de enlace, 13 tests |
| K1 escala del kernel | hecho 2026-10-06 | `ecafc4f`…`1f4a0134`; formato 2: abrir desde `heads.edn` y la Revision de cabeza sin replay, conjuntos por revisión como trie Merkle independiente de la historia (`kimun.kernel.mmap`), objetos perezosos, commit bajo lock exclusivo (`store/busy`, `--wait`), `stores upgrade` 1→2; store real: `docs show` 187,9 s → 0,38 s, `docs list` 180 s → 0,77 s, `fields set` → 0,38 s, migración completa 522 s → 71 s; abrir y commitear no crecen con la historia (`runs/bench/after-2026-10-06.txt`); diseño en `docs/v2/10-escala.md` |
| E3 cierre del marcador | hecho 2026-10-06 | `f1e0667`, `95b55d9`, `61e1bf8`; `scripts/parity/coverage.py --strict` con contexto de menciones, oráculo declarado, mapa, n/a y gaps; 109 promesas de v1 portadas en `test/kimun/parity/*`; datos en `parity-{na,map,oracle,gaps}.edn` |
| E2 adaptadores (selfdoc) | hecho 2026-10-06 | `515b4a2`, `4ba6c89`, `673fb18`; protocolo en `docs/v2/11-adaptadores.md`; adaptadores fuera de proceso `python-symbols`, `argparse-cli`, `click-cli`, `treesitter-symbols` (Python y JavaScript); grupo `adapters scan\|sync\|check\|list`; contra v1 en vivo: 2.288/2.288 símbolos, 93/93 comandos, 25/25 superficies con todos los campos propios iguales; tree-sitter y `ast` coinciden en 2.876 hechos sobre el fuente de v1 |
| D1b rutas HTTP | hecho 2026-10-06 | `87c73cf`, `072159c`; `kimun.surface.http.{routes,views}`: schema, graph, save por lotes con `expected`, docs create/track/untrack, document, segments, fields, sections, find, edges, graph, corpus, similar, models, lint, ast; de 61 funciones de v1: 55 portadas, 4 n/a, 2 ya cubiertas por el esqueleto |
| E1 migración | hecho 2026-10-06 | `dc770e6`, `548b9bf`, `2cfed85`; `kimun migrate --from-v1`; **store real de v1: 35/35 modelos, 1.551/1.551 documentos migrados (47 con `--force` por no hacer round-trip), 1.544 payloads y content-hash iguales a v1, 7 distintos (`TaskDoc.from_drawer`), 1.551 source-hash iguales, 1.551 rutas relativas**; informe en `runs/parity/migrate-2026-10-06.md` |
| C2 aristas, grafo, predicates | hecho 2026-10-06 | `6bdc107`…`0552d4e`; `kimun.surface.edges` (grafo del store como vista derivada, ids `sldb://…` de v1), `kimun.surface.graph.store`, grupos `edges`, `graph`, `predicates`; predicates y tipos de relación como nodos en el árbol `relations`, relación de autor = arista `:semantic` con contexto; diferencial: 40 documentos reales, 2.486 aristas de v1, 1.991 iguales, 541 diferencias todas explicadas; 52 funciones portadas, 4 n/a, 3 diferidas |
| C1 índice, direcciones, `find` | hecho 2026-10-06 | `ebbc7d8`…`ea07e95`; `kimun.surface.index.{build,cache,address,search,corpus}`, comandos `find`, `corpus rank\|refresh`, `rebuild-indexes`, `--store NAME`; 38 funciones portadas, 3 n/a, 3 diferidas |
| B2b campos y secciones | hecho 2026-10-06 | `c76a2d0`, `67762e0`, `ff7aead`, `12fb3be`; `kimun.surface.{fields,sections}`, grupos `fields` y `sections`, `kimun.cli.compose` (contexto de composición contra la raíz del proyecto), `kimun.cli.linked` (modelo de un store enlazado: `ALIAS:Model`), arreglo del timestamp a medianoche; de `test_cli_v2` 9 portadas, 2 parciales, 1 n/a; secciones 8; composición a nivel de store 3; paridad de documentos reales 54/57 |
| C6 diagnósticos | hecho 2026-10-06 | `ecb60a5`, `aca762b`; `kimun.surface.diagnostics` (categorías `ok reformatted edited missing contract-moved unextractable noise orphan corrupt`, cada una con su acción), `kimun.surface.models.noise`, `stores check\|health\|update\|reconcile`; un store inválido es el fallo `store/invalid` (exit 5); 32 funciones de v1 portadas, 4 n/a |
| C3 semántica | hecho 2026-10-06 | `5e14cb3`, `b6a2748`; `kimun.surface.semantic`, grupo `semantic`; tags = términos `:symbol/:term`, padre/equivalente = aristas `:semantic` con contexto `:fact/:context`; diferencial: 87 documentos de 23 modelos del store real, 87/87 tags iguales; 21 funciones portadas, 2 n/a, 7 diferidas |
| C5 `docs recover\|compose` | hecho 2026-10-06 | `239b507`; `kimun.cli.commands.links`; 5/5 funciones de CLI de v1 |
| T1b segmentos y composiciones | hecho 2026-10-06 | `bd2570e`, `1cf97b1`; composiciones en `render` con contexto; `kimun.surface.models.segments`; corpus del motor **2.384 pasan, 0 fallan, 24 excluidos, 0 pendientes**; corpus de segmentos nuevo: **1.588 pasan, 0 fallan, 7 excluidos**; 37 funciones portadas, 2 n/a |
| B2a documentos tipados | hecho 2026-10-06 | `195d21b`, `ec418b4`, `ec26eab` + arreglo del backfill; `kimun.surface.docs`; `docs create\|track\|update\|show\|status\|list\|untrack\|delete`, `models … --dry-run\|--backfill`, `stores check` con hallazgos de documentos, journal con `:operation`, `:address`, `:fields`; diferencial sobre 57 documentos reales de 23 modelos del `.sldb` de v1: 53 payloads iguales, 4 diferencias esperadas |
| B1 modelos en el store | hecho 2026-10-06 | `49bf972`, `1184f03`; `kimun.surface.models.{store,edit}`, `kimun.cli.commands.{models,direct}`; `models define\|list\|show\|history\|check\|field-add\|field-remove\|template`, `extract\|render\|validate` directos; de 58 funciones de v1: 36 portadas, 8 n/a, 14 diferidas a B2; goldens de 23 modelos de deskops y 6 de hum-scrapper |
| T1 oráculo + motor | hecho 2026-10-06 | `d0dff96`, `bf0aa64`, `bb214ef`; `kimun.surface.models.{marker,blocks,recipe,descriptor,extract,render,validate}` (666 líneas), `python/kimun/models.py`, `scripts/parity/record_engine.{py,sh}`; corpus grabado de la suite de v1 (1.221 tests): **2.408 casos, 2.373 pasan, 24 excluidos, 11 pendientes, 0 fallan**; render comparado byte a byte con v1 |
| D1 esqueleto de `serve` | hecho 2026-10-06 | `93b9107`; `kimun.surface.http` (rutas como datos, bearer en tiempo constante, CORS, exit→status, `command-route`) y `kimun serve`; `test_serve_auth.py` 5/5; el resto de rutas quedó portado en D1b; el checklist previo está en `docs/archive/v2/tests/parity-serve-checklist.md` |
| C4 journal (base) | hecho 2026-10-06 | `1623e8b`; `kimun.surface.journal` (`entries`, `verify`) y `journal show\|verify`: vista sobre `log.edn` y el DAG de revisiones, sin segundo almacén; 5 de 8 funciones de v1, 3 esperan campos tipados |
| federación (parte de C1) | hecho 2026-10-06 | `c880c51`; `stores link\|unlink\|links`, rutas relativas validadas al abrir, `linked-stores` con guarda de ciclos; 5 funciones de v1 portadas |
| A3 grafo | hecho 2026-10-06 | `4beff47`; `kimun.surface.graph.{model,traversal,analysis,query,io}`; 36 de 38 funciones de v1 (16+4+6+3+3+4), 2 de CLI diferidas a C2; pagerank, betweenness, grados, caminos, ciclos, componentes y capas reimplementados y contrastados con networkx 3.6.1 |
| A2 `--where` | hecho 2026-10-06 | `1f701ec`; `kimun.surface.index.where` (`parse`, `matches?`); 10/10 funciones de v1 que tocan `--where`, 15 tests |

## Diferido (pendiente, lo recoge la tarea indicada)

| de | qué falta | lo recoge |
|---|---|---|
| C2 | aristas y export de grafo no ven los stores enlazados (`test_linked_store_documents_come_back_qualified`, `…linked_stores_gap_G5`): ver `parity-gaps.edn` | K2 (pendiente) |
| K1 | **disco**: 94 MB de datos pero 364 MB en disco, porque 80.435 de 82.787 objetos pesan menos de 4 KB y cada uno ocupa un bloque; meta 150 MB no cumplida. Arreglo: empaquetar objetos (packfiles al estilo git) | K2 (pendiente) |
| K1 | `docs show` y `fields set` leen el descriptor de cada árbol para el índice por nombre (O(árboles)); constante requeriría un índice persistente en la Revision; no hay GC ni informe de objetos inalcanzables | K2 (pendiente) |

Todo lo demás que estaba diferido quedó portado o declarado n/a; lo mide
`scripts/parity/coverage.py` (marcador arriba).

## Desviaciones deliberadas respecto de v1 (acumuladas)

- `--where` no reconocido es `query/bad-where` con posición, no `False` silencioso (`06-superficies-y-cli.md`). Regex inválida se rechaza al parsear. `>=`/`<=` entre tipos distintos da falso en vez de `TypeError`. Dialecto de regex: Java.
- Adaptadores: `sync` deja de rastrear los documentos huérfanos que el adaptador posee (v1 solo los informaba); el drift de `check` es `adapters/missing` con `:reason "drift"` (exit 1, como v1); no se portaron la heurística de renombres, la proyección a kgdb ni la guarda de before-image. v1 ya no puede sincronizar sus propios símbolos: cinco pares getter/setter chocan en un mismo id; kimun lo informa como `adapters/collision`.
- Rutas HTTP: `/models/promote` y los borradores `.py.temp` se retiran (`POST /models/define {edn}`); `/lint` sirve los hallazgos de `stores check`; `/ast` es el documento con payload y secciones; `/models/detail` de un modelo desconocido es 404 (v1: 200 con `ok false`); las listas `unavailable` de `/schema` y `/graph` existen pero ningún estado del store las llena, porque un modelo es un nodo y no un módulo que pueda fallar al importarse. Claves que cambian de nombre: `id`→`name`, resultados bajo `data`.
- Índice y búsqueda: la búsqueda física también mira el texto del documento y el valor de los campos (v1: solo nombres y rutas); `--fuzzy` y el ranking por defecto usan Dice, no `difflib`; no se persisten vectores (`:score-fn` reemplaza al `Embedder`); una dirección que rompe la gramática es `query/bad-address` (exit 3), una bien formada que no nombra nada da vacío; `rebuild-indexes` con documentos que no extraen sale con 0 y `:complete false`.
- Aristas: `edges init|rebuild` son no-ops informados (el grafo se deriva de `(store, head)`); los 14 tipos builtin existen siempre; `edges check` y `predicates validate` salen con 3 (v1: 1); v1 inventaba secciones dentro del frontmatter YAML y kimun no (74 `has_section` y sus 421 `tagged_as` en la muestra real).
- Campos y secciones: `fields clean` no vacía una lista (en v1 es `--dedupe`/`--drop-empty`; vaciar es `fields clear`); `fields query` sin resultados es ok con lista vacía (v1 salía con 1); una ref de composición que sale de la raíz del proyecto resuelve a nada (v1 leía cualquier ruta); el modelo de un store enlazado se liga copiando su nodo descriptor al árbol `models` local, y los cambios posteriores del dueño no se propagan. Huecos conocidos del parseo de fechas YAML: colección en flujo de varias líneas y fecha sola en la línea siguiente a un `key:` vacío.
- Diagnósticos: `stores check` falla con `store/invalid` (exit 5; v1 salía con 1) cuando algo va más allá de `ok`/`reformatted`; el ruido ya rastreado se informa sin bloquear, el ruido que entra por una edición a mano sí bloquea; `stores update` nunca acepta una mutación de datos (v1 sí): eso es `docs update NAME`. `stores check` corre `store/verify` en cada llamada.
- Semántica: no hay `semantic_dag.yaml` ni rebuild; los padres implícitos por nombre con puntos se derivan al leer; las equivalencias valen en ambos sentidos; export v2 sin hashes de letras y con rutas relativas. Las fuentes de tags son solo `:semantics` del modelo sobre su linaje y las listas `tags`/`semantic_tags` del payload.
- Composiciones: la clave del descriptor es la de v1 (`source_field`, `model`, `template`, bajo `:py/compositions`), no `{:model :field :order}` de `07 §2`; una ref no resoluble renderiza vacío (v1 lanzaba `ImportError` para el modelo y caía al cwd para la ruta).
- Enlaces en CLI: un enlace sin resolver sale con 1 y el informe completo en el error, como afirman los tests de v1.
- Documentos: el payload se extrae del texto fuente guardado tal cual (signo `markdown/source`), no del render canónico del árbol, que escapa `_ * [ ]` y une saltos suaves; el árbol de bloques queda para estructura. `docs track` rechaza un archivo que no hace round-trip salvo `--force`; `docs delete` exige `--yes`; un archivo editado afuera es un warning de `stores check`; no hay outbox, el comando escribe el archivo tras el commit. La opción `{:doc}` no se agregó a `kimun.surface.markdown.plan`: `kimun.surface.docs` la pone en el AST.
- Modelos: redefinir el mismo contrato no es error (`:unchanged? true`, nada se escribe); la versión sube solo si el contrato cambia; un borrador es un `.edn` del usuario, no un archivo gestionado por el store; `extract` rechaza un `rev` vacío salvo `--allow-empty`. El template **no** se ingesta como documento `model/<Name>/template` (`07 §4`): duplicaría revisiones y aparecería en `docs list`. No hay template heredado: el exportador aplana la herencia.
- Motor: no usa el CST/AST de SLDB-MD; `kimun.surface.models.blocks` reimplementa la segmentación por bloques de markdown-it que usaba v1, porque la paridad byte a byte depende de sus tipos y rangos de línea. La receta es por bloque, no por `:path` como pide `07 §4`: **deuda de diseño** a saldar cuando el motor se monte sobre el árbol del kernel. Excluidos del corpus: 9 con `py•`/Jinja (`07 §8`) y 15 de `TaskDoc` de deskops, cuya clase omite un campo vacío con código Python propio. El descriptor admite `:exclude true` en un campo.
- Serve: todo cuerpo es el envelope (v1 enviaba el payload desnudo y `{ok:false,error:"…"}`); los status coinciden con v1. Ruta conocida con método equivocado es 405. CORS permite la cabecera `Authorization`. La variable es `KIMUN_SERVE_TOKEN`.
- Journal: no hay segundo almacén ni escritura en el commit; la cadena de hashes es el DAG de revisiones (`previous_hash` ⇒ `:parents`); no hay campo `operation`, sino el conteo de ops del plan; el filtro por actor es `--by`.
- Federación: `stores link NAME PATH` con ruta relativa al store; no existe catálogo global en el home (`~/.sldb` con rutas absolutas era el vicio nombrado en `06-superficies-y-cli.md`), así que `test_store_init_registers_project_in_global_catalog` y `test_get_store_context_readonly_falls_back_to_global` no se portan. Un destino ausente es un warning, no un fallo.
- Grafo: el orden es determinista por id donde v1 heredaba el de networkx (camino, orden topológico, ciclos); `paths-between` no repite un camino por cada relación paralela; `gt`/`lt` entre tipos no comparables dan falso.
- Enlaces: se informa `:ambiguous` cuando un mismo nivel de búsqueda da más de un candidato (v1 tomaba el primero). Los offsets son unidades de string de la plataforma.

