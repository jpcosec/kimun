# Paridad con sldb v1 — DAG y estado (histórico, archivado 2026-10-09)

> Movido desde `docs/v2/tests/parity-v1.md` (auditoría L2, 2026-10-09): el DAG T1–E3 está todo
> hecho y el estado ya no guía. La traza viva (marcador, cómo se mide, n/a, diferido y
> desviaciones) sigue en `docs/v2/tests/parity-v1.md`.

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
| C2 | aristas, relaciones, predicates, `graph *`, export/snapshot; federación de stores enlazados (A3) | C1, A3 | 60 min | tanda C | api/test_api_edges, api/test_api_graph, api/test_graph_source_parity, api/test_declared_references, api/test_composition_edges, test_relation_models, test_predicates_cli, store/test_edge_anchor_targets, store/test_semantic_export |
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


## Estado

| id | estado | evidencia |
|---|---|---|
| S2 primer corte | hecho 2026-10-06 | `e444d4d`, `test/kimun/cli/docs_test.cljc` |
| A4 enlaces | hecho 2026-10-06 | `13184d9`; `kimun.surface.links.{parser,resolver,recovery,composer}`; 8/8 de `test_links.py` a nivel de enlace, 13 tests |
| K1 escala del kernel | hecho 2026-10-06 | `ecafc4f`…`1f4a0134`; formato 2: abrir desde `heads.edn` y la Revision de cabeza sin replay, conjuntos por revisión como trie Merkle independiente de la historia (`kimun.kernel.mmap`), objetos perezosos, commit bajo lock exclusivo (`store/busy`, `--wait`), `stores upgrade` 1→2; store real: `docs show` 187,9 s → 0,37 s, `docs list` 180 s → 0,77 s, `fields set` → 0,37 s, migración completa 522 s → 71 s; abrir y commitear no crecen con la historia (`runs/bench/after-2026-10-06.txt`); diseño en `docs/v2/10-escala.md` |
| E3 cierre del marcador | hecho 2026-10-06 | `f1e0667`, `95b55d9`, `61e1bf8`; `scripts/parity/coverage.py --strict` con contexto de menciones, oráculo declarado, mapa, n/a y gaps; 109 promesas de v1 portadas en `test/kimun/parity/*`; datos en `parity-{na,map,oracle,gaps}.edn` |
| E2 adaptadores (selfdoc) | hecho 2026-10-06 | `515b4a2`, `4ba6c89`, `673fb18`; protocolo en `docs/v2/11-adaptadores.md`; adaptadores fuera de proceso `python-symbols`, `argparse-cli`, `click-cli`, `treesitter-symbols` (Python y JavaScript); grupo `adapters scan\|sync\|check\|list`; contra v1 en vivo: 2.288/2.288 símbolos, 93/93 comandos, 25/25 superficies con todos los campos propios iguales; tree-sitter y `ast` coinciden en 2.876 hechos sobre el fuente de v1 |
| D1b rutas HTTP | hecho 2026-10-06 | `87c73cf`, `072159c`; `kimun.surface.http.{routes,views}`: schema, graph, save por lotes con `expected`, docs create/track/untrack, document, segments, fields, sections, find, edges, graph, corpus, similar, models, lint, ast; de 61 funciones de v1: 55 portadas, 4 n/a, 2 ya cubiertas por el esqueleto |
| E1 migración | hecho 2026-10-06 | `dc770e6`, `548b9bf`, `2cfed85`; `kimun migrate --from-v1`; **store real de v1: 35/35 modelos, 1.551/1.551 documentos migrados (47 con `--force` por no hacer round-trip), 1.544 payloads y content-hash iguales a v1, 7 distintos (`TaskDoc.from_drawer`), 1.551 source-hash iguales, 1.551 rutas relativas**; informe en `runs/parity/migrate-2026-10-06.md` |
| C2 aristas, grafo, predicates | hecho 2026-10-06 | `6bdc107`…`0552d4e`; `kimun.surface.edges` (grafo del store como vista derivada, ids `sldb://…` de v1), `kimun.surface.graph.store`, grupos `edges`, `graph`, `predicates`; predicates y tipos de relación como nodos en el árbol `relations`, relación de autor = arista `:semantic` con contexto; diferencial: 40 documentos reales, 2.486 aristas de v1, 1.991 iguales, 541 diferencias todas explicadas; 52 funciones portadas, 4 n/a, 3 diferidas |
| A3 federación de aristas y grafo | hecho 2026-10-08 | `kimun.surface.edges` deriva también las stores enlazadas alcanzables (`:linked`, abiertas por el llamador con `kimun.cli.store/linked-stores` y su guarda de ciclos) y las califica por nombre de enlace (v1 `federation.qualify`): `sldb://document/annex:Table:t99`, `semantics.store`, aristas de documento; `--local` las deja fuera; usado por `edges show\|node\|nodes` y `graph *`/`export`/`snapshot save`; cierra las 2 brechas |
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

