# Paridad con sldb v1 — libro y DAG (abierto 2026-10-06)

> Objetivo: `kimun` equivalente a sldb v1, medido contra los tests de v1
> (`hum-ecosystem/tools/sldb/tests`: 82 archivos, 590 funciones). Cada archivo de v1 queda
> en uno de tres estados: **portado** (test kimun que ejerce la misma promesa), **oráculo**
> (cubierto por el corpus diferencial grabado desde v1) o **n/a** (probaba un mecanismo
> interno de v1 que el diseño de v2 reemplaza; se nombra el test del kernel que sostiene la
> misma promesa). El mapeo de comandos es el de `docs/v2/06 §5`; las superficies retiradas,
> las de `06 §8`.

## Cómo se mide

1. **Oráculo diferencial del motor**: un plugin de pytest corre la suite de v1 y graba cada
   llamada a extracción y render (clase de modelo → descriptor EDN, Markdown de entrada,
   payload, Markdown renderizado) en `test/fixtures/parity/engine/`. El motor de kimun
   tiene que reproducir cada caso. Son literalmente los mismos tests, en la frontera del motor.
2. **Tests portados**: para store, CLI, grafo, consultas y serve, cada test de v1 se
   reescribe contra la CLI/API de kimun con el vocabulario de `06 §5`.
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
| D1 esqueleto de `serve` | hecho 2026-10-06 | `93b9107`; `kimun.surface.http` (rutas como datos, bearer en tiempo constante, CORS, exit→status, `command-route`) y `kimun serve`; `test_serve_auth.py` 5/5; el resto de rutas en `parity-serve-checklist.md` |
| C4 journal (base) | hecho 2026-10-06 | `1623e8b`; `kimun.surface.journal` (`entries`, `verify`) y `journal show\|verify`: vista sobre `log.edn` y el DAG de revisiones, sin segundo almacén; 5 de 8 funciones de v1, 3 esperan campos tipados |
| federación (parte de C1) | hecho 2026-10-06 | `c880c51`; `stores link\|unlink\|links`, rutas relativas validadas al abrir, `linked-stores` con guarda de ciclos; 5 funciones de v1 portadas |
| A3 grafo | hecho 2026-10-06 | `4beff47`; `kimun.surface.graph.{model,traversal,analysis,query,io}`; 36 de 38 funciones de v1 (16+4+6+3+3+4), 2 de CLI diferidas a C2; pagerank, betweenness, grados, caminos, ciclos, componentes y capas reimplementados y contrastados con networkx 3.6.1 |
| A2 `--where` | hecho 2026-10-06 | `1f701ec`; `kimun.surface.index.where` (`parse`, `matches?`); 10/10 funciones de v1 que tocan `--where`, 15 tests |

## Diferido por las tareas cerradas (lo recoge la tarea indicada)

| de | qué falta | lo recoge |
|---|---|---|
| A4 | `docs recover\|compose` en CLI: aceptar nombre de documento rastreado en vez de ruta, `--format yaml`, `-o -`; `--include-transclusions` se acepta y se ignora, como en v1 | C5 |
| A4 | **composiciones de modelo** (`test_composition.py` ×4, `api/test_composition_ref_root.py` ×3): un modelo declara `{nombre {source_field, model, template}}`; el slot `⸢render•nombre⸥` se expande cargando cada ref de `source_field`, extrayéndola con el modelo referido y rellenando `{campo}` en la plantilla de línea; refs inválidas o no resolubles se omiten en silencio; las refs relativas resuelven contra la raíz del proyecto (`ref_root`), nunca contra el cwd | B2 |
| A2 | `find` sobre store real con direcciones `st.{Model}`, `st.{Base+}`; exit 3 y stdout vacío ante `query/bad-where`; dirección inválida ⇒ lista vacía | C1 |
| T1 | segmentos y ownership por sección: `test_field_ownership` (14), `test_segment_structure` (6), `test_extraction_segments` (6), `test_document_segments` (3), `api/test_field_blocks` (2); trait `markdown`; tests unitarios del plegado YAML y de los carriers | T1b |
| T1 | 11 casos pendientes del corpus (`PENDING.edn`): render con composiciones de modelo (`Board`, `ComposedBoardDoc`, `ComposedRitualDoc`, `BoardDoc`) | T1b |
| C3 | secciones en el export semántico; `_extract_sections` de `store/test_semantic_rebuild.py` ×7 (texto sin encabezados, vacío, solo separadores y código, encabezado sin título ⇒ sin secciones; un encabezado ⇒ título, nivel, slug, path, `line_start`; anidados ⇒ paths `a`, `a/b`, `a/b/c`, `d`): cubrir con `kimun.surface.models.segments/sections` | B2b |
| C1 | `test_field_update_moves_the_model_hash_and_keeps_integrity`; estado y cuerpo HTTP de `/corpus/rank` y `/similar`; `find --format text` sin render propio | D1b, E3 |
| C2 | ids calificados entre stores enlazados (`test_linked_store_documents_come_back_qualified`, `…linked_stores_gap_G5`); `help edges` | E3 |
| E1 | **escala**: sobre el store migrado (1.586 revisiones, 36.718 objetos, 606 MB) abrir tarda ~200 s porque `open` reproduce el log y cada revisión reescribe los conjuntos completos de árboles y aristas | K1 |
| B2b | `find` como comando sobre campos y secciones (`test_find_field_shows_owning_section`, `test_find_section_where_predicates`): usar `kimun.surface.fields/rows` y `kimun.surface.sections/find-sections`; `fields query --global`, `sections find --global` | C1 |
| B2b | `ast show` (`test_ast_show_document_has_sections`, `test_ast_show_field_nodes_have_owning_section`): `ast` se retira en `06 §8`; la parte de secciones y ownership ya está cubierta | E3 (n/a) |
| C3 | la CLI de `graph` debe usar `kimun.surface.semantic/export->snapshot` (acepta contrato v1 y v2); direcciones `se.`/`gse.` entre stores | C2, C1 |
| T1b | cablear el contexto de composición (`:ref-text`, `:ref-model`, raíz del proyecto) en `docs create\|update\|show` y en el backfill; 3 funciones de `api/test_composition_ref_root.py` a nivel de store | B2b |
| C5 | registro de predicates del store en vez de los 7 por defecto fijos en `kimun.cli.commands.links/default-predicates` | C2 |
| B2a | ruido estructural (`api/test_structural_noise` ×8): rechazar al rastrear un documento con encabezados o claves de frontmatter que el template no declara, aceptar si un modelo hijo los declara `optrev`, y que `stores check` liste cada encabezado ruidoso ya rastreado; `stores check --format yaml`; informe de `check`/`health` que nombra el archivo fuente de un contrato movido (`test_contract_in_model_node` ×6); `docs show --format ast` | C6 |
| B2a | de `test_cli_v2`: `fields_update_append_and_clean`, `fields_update_can_clear_list_field`, `field_query_includes_owning_section`, `sections_show\|find\|fields_command`, `sections_show_identical_with_and_without_persisted_index`, `find_field_shows_owning_section` | B2b |
| B2a | `find_supports_physical_and_semantic`, `find_section_where_predicates`, `docs_create_accepts_linked_store_alias`, `docs_create_uses_model_from_linked_store_namespace` | C1 |
| B2a | `recover_links_only`, `recover_depth` | C5 |
| B2a | **bug del motor**: un timestamp de frontmatter a medianoche exacta (`created_at: 2026-06-14T00:00:00`) no vuelve como en v1 (`test/fixtures/parity/docs/EXPECTED-DIFFS.edn`) | T1b |
| B2a | rutas HTTP de documentos tipados y modelos; `docs create`/`update` con contexto de composición en el render | D1b |
| B1 | promover con documentos existentes: rechazo de campo requerido sin default (`lacks required field`), dry-run que lista los documentos a rellenar, backfill que los reescribe (`test_model_lifecycle` ×5); diagnóstico tras mover el contrato = estado `superseded` de la arista documento→modelo (`test_contract_in_model_node` ×8); `models list\|show` con conteo de documentos y estado de anclaje; journal con campo cambiado y rutas anidadas (×2) | B2 |
| C4 | entradas de `add_model`, `reindex_model`, `promote_model_draft`; en un guardado, `address` `Model:doc`, `previous_value`/`new_value` por campo (`test_add_model_leaves_an_entry`, `test_reindex_and_promote_leave_entries`, parte de campos de `test_save_entry_carries_before_and_after`): se agregan como claves extra de la entrada | B2 |
| federación | `--store NAME` resuelto por nombre de enlace desde un store anidado (`test_store_alias_is_resolved_from_an_ancestor_registry`); refresco de índices completo y con documento ausente (`test_update_store_indexes_*`) | C1 |
| A3 | `graph *` en CLI sobre store en disco (`test_the_cli_prints_each_analysis`, `…no_path_and_exits_nonzero`); constructor del índice de aristas, conversión snapshot⇄índice | C2 |
| A2 | tests de direcciones/glob/semántica sin `--where` en `store/test_queries_expanded.py` (7 funciones) | C1, C3 |

## Desviaciones deliberadas respecto de v1 (acumuladas)

- `--where` no reconocido es `query/bad-where` con posición, no `False` silencioso (`06 §5`). Regex inválida se rechaza al parsear. `>=`/`<=` entre tipos distintos da falso en vez de `TypeError`. Dialecto de regex: Java.
- Rutas HTTP: `/models/promote` y los borradores `.py.temp` se retiran (`POST /models/define {edn}`); `/lint` sirve los hallazgos de `stores check`; `/ast` es el documento con payload y secciones; `/models/detail` de un modelo desconocido es 404 (v1: 200 con `ok false`); las listas `unavailable` de `/schema` y `/graph` existen pero ningún estado del store las llena, porque un modelo es un nodo y no un módulo que pueda fallar al importarse. Claves que cambian de nombre: `id`→`name`, resultados bajo `data`.
- Índice y búsqueda: la búsqueda física también mira el texto del documento y el valor de los campos (v1: solo nombres y rutas); `--fuzzy` y el ranking por defecto usan Dice, no `difflib`; no se persisten vectores (`:score-fn` reemplaza al `Embedder`); una dirección que rompe la gramática es `query/bad-address` (exit 3), una bien formada que no nombra nada da vacío; `rebuild-indexes` con documentos que no extraen sale con 0 y `:complete false`.
- Aristas: `edges init|rebuild` son no-ops informados (el grafo se deriva de `(store, head)`); los 14 tipos builtin existen siempre; `edges check` y `predicates validate` salen con 3 (v1: 1); v1 inventaba secciones dentro del frontmatter YAML y kimun no (74 `has_section` y sus 421 `tagged_as` en la muestra real).
- Campos y secciones: `fields clean` no vacía una lista (en v1 es `--dedupe`/`--drop-empty`; vaciar es `fields clear`); `fields query` sin resultados es ok con lista vacía (v1 salía con 1); una ref de composición que sale de la raíz del proyecto resuelve a nada (v1 leía cualquier ruta); el modelo de un store enlazado se liga copiando su nodo descriptor al árbol `models` local, y los cambios posteriores del dueño no se propagan. Huecos conocidos del parseo de fechas YAML: colección en flujo de varias líneas y fecha sola en la línea siguiente a un `key:` vacío.
- Diagnósticos: `stores check` falla con `store/invalid` (exit 5; v1 salía con 1) cuando algo va más allá de `ok`/`reformatted`; el ruido ya rastreado se informa sin bloquear, el ruido que entra por una edición a mano sí bloquea; `stores update` nunca acepta una mutación de datos (v1 sí): eso es `docs update NAME`. `stores check` corre `store/verify` en cada llamada.
- Semántica: no hay `semantic_dag.yaml` ni rebuild; los padres implícitos por nombre con puntos se derivan al leer; las equivalencias valen en ambos sentidos; export v2 sin hashes de letras y con rutas relativas. Las fuentes de tags son solo `:semantics` del modelo sobre su linaje y las listas `tags`/`semantic_tags` del payload.
- Composiciones: la clave del descriptor es la de v1 (`source_field`, `model`, `template`, bajo `:py/compositions`), no `{:model :field :order}` de `07 §2`; una ref no resoluble renderiza vacío (v1 lanzaba `ImportError` para el modelo y caía al cwd para la ruta).
- Enlaces en CLI: un enlace sin resolver sale con 1 y el informe completo en el error, como afirman los tests de v1.
- Documentos: el payload se extrae del texto fuente guardado tal cual (signo `markdown/source`), no del render canónico del árbol, que escapa `_ * [ ]` y une saltos suaves; el árbol de bloques queda para estructura. `docs track` rechaza un archivo que no hace round-trip salvo `--force`; `docs delete` exige `--yes`; un archivo editado afuera es un warning de `stores check`; no hay outbox, el comando escribe el archivo tras el commit. La opción `{:doc}` no se agregó a `sldb.surface.markdown.plan`: `kimun.surface.docs` la pone en el AST.
- Modelos: redefinir el mismo contrato no es error (`:unchanged? true`, nada se escribe); la versión sube solo si el contrato cambia; un borrador es un `.edn` del usuario, no un archivo gestionado por el store; `extract` rechaza un `rev` vacío salvo `--allow-empty`. El template **no** se ingesta como documento `model/<Name>/template` (`07 §4`): duplicaría revisiones y aparecería en `docs list`. No hay template heredado: el exportador aplana la herencia.
- Motor: no usa el CST/AST de SLDB-MD; `kimun.surface.models.blocks` reimplementa la segmentación por bloques de markdown-it que usaba v1, porque la paridad byte a byte depende de sus tipos y rangos de línea. La receta es por bloque, no por `:path` como pide `07 §4`: **deuda de diseño** a saldar cuando el motor se monte sobre el árbol del kernel. Excluidos del corpus: 9 con `py•`/Jinja (`07 §8`) y 15 de `TaskDoc` de deskops, cuya clase omite un campo vacío con código Python propio. El descriptor admite `:exclude true` en un campo.
- Serve: todo cuerpo es el envelope (v1 enviaba el payload desnudo y `{ok:false,error:"…"}`); los status coinciden con v1. Ruta conocida con método equivocado es 405. CORS permite la cabecera `Authorization`. La variable es `KIMUN_SERVE_TOKEN`.
- Journal: no hay segundo almacén ni escritura en el commit; la cadena de hashes es el DAG de revisiones (`previous_hash` ⇒ `:parents`); no hay campo `operation`, sino el conteo de ops del plan; el filtro por actor es `--by`.
- Federación: `stores link NAME PATH` con ruta relativa al store; no existe catálogo global en el home (`~/.sldb` con rutas absolutas era el vicio nombrado en `06 §5`), así que `test_store_init_registers_project_in_global_catalog` y `test_get_store_context_readonly_falls_back_to_global` no se portan. Un destino ausente es un warning, no un fallo.
- Grafo: el orden es determinista por id donde v1 heredaba el de networkx (camino, orden topológico, ciclos); `paths-between` no repite un camino por cada relación paralela; `gt`/`lt` entre tipos no comparables dan falso.
- Enlaces: se informa `:ambiguous` cuando un mismo nivel de búsqueda da más de un candidato (v1 tomaba el primero). Los offsets son unidades de string de la plataforma.

