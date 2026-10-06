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
| C4 | entradas de `add_model`, `reindex_model`, `promote_model_draft`; en un guardado, `address` `Model:doc`, `previous_value`/`new_value` por campo (`test_add_model_leaves_an_entry`, `test_reindex_and_promote_leave_entries`, parte de campos de `test_save_entry_carries_before_and_after`): se agregan como claves extra de la entrada | B2 |
| federación | `--store NAME` resuelto por nombre de enlace desde un store anidado (`test_store_alias_is_resolved_from_an_ancestor_registry`); refresco de índices completo y con documento ausente (`test_update_store_indexes_*`) | C1 |
| A3 | `graph *` en CLI sobre store en disco (`test_the_cli_prints_each_analysis`, `…no_path_and_exits_nonzero`); constructor del índice de aristas, conversión snapshot⇄índice | C2 |
| A2 | tests de direcciones/glob/semántica sin `--where` en `store/test_queries_expanded.py` (7 funciones) | C1, C3 |

## Desviaciones deliberadas respecto de v1 (acumuladas)

- `--where` no reconocido es `query/bad-where` con posición, no `False` silencioso (`06 §5`). Regex inválida se rechaza al parsear. `>=`/`<=` entre tipos distintos da falso en vez de `TypeError`. Dialecto de regex: Java.
- Motor: no usa el CST/AST de SLDB-MD; `kimun.surface.models.blocks` reimplementa la segmentación por bloques de markdown-it que usaba v1, porque la paridad byte a byte depende de sus tipos y rangos de línea. La receta es por bloque, no por `:path` como pide `07 §4`: **deuda de diseño** a saldar cuando el motor se monte sobre el árbol del kernel. Excluidos del corpus: 9 con `py•`/Jinja (`07 §8`) y 15 de `TaskDoc` de deskops, cuya clase omite un campo vacío con código Python propio. El descriptor admite `:exclude true` en un campo.
- Serve: todo cuerpo es el envelope (v1 enviaba el payload desnudo y `{ok:false,error:"…"}`); los status coinciden con v1. Ruta conocida con método equivocado es 405. CORS permite la cabecera `Authorization`. La variable es `KIMUN_SERVE_TOKEN`.
- Journal: no hay segundo almacén ni escritura en el commit; la cadena de hashes es el DAG de revisiones (`previous_hash` ⇒ `:parents`); no hay campo `operation`, sino el conteo de ops del plan; el filtro por actor es `--by`.
- Federación: `stores link NAME PATH` con ruta relativa al store; no existe catálogo global en el home (`~/.sldb` con rutas absolutas era el vicio nombrado en `06 §5`), así que `test_store_init_registers_project_in_global_catalog` y `test_get_store_context_readonly_falls_back_to_global` no se portan. Un destino ausente es un warning, no un fallo.
- Grafo: el orden es determinista por id donde v1 heredaba el de networkx (camino, orden topológico, ciclos); `paths-between` no repite un camino por cada relación paralela; `gt`/`lt` entre tipos no comparables dan falso.
- Enlaces: se informa `:ambiguous` cuando un mismo nivel de búsqueda da más de un candidato (v1 tomaba el primero). Los offsets son unidades de string de la plataforma.

