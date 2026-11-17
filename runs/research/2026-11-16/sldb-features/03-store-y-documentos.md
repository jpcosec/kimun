# sldb: el store y las operaciones sobre documentos (reporte 03)

Fuente leída: `/home/jp/proyectos/SLDB_1/sldb` (solo lectura). Rutas `src/...` son relativas a `src/sldb/`. Marcas: [V] verificado en el archivo citado; [I] inferencia mía. Fechas "reciente" = commits del 2026-11-01 en adelante (git log); "oct" = 2026-10-15 a 10-31.

No abrí: `store/graph`, `store/edge_index/*` (salvo `cache_keys.py` y `composed_disk.py`), `api/model_registry`, `api/rules`, `cli/serve`, ni el contenido de los tests (asigno test por nombre de archivo, [I]).

## 1. Para qué sirve esta área

Es el almacén de sldb y las operaciones que escriben documentos Markdown estructurados. Un "store" (`.sldb/`) lleva un registro de modelos Python (clases pydantic con un `__template__`), de documentos Markdown trackeados bajo cada modelo, y un árbol de hashes tipo Merkle (texto, campos, roster, contrato) para saber qué se movió. Encima hay índices derivados (tags semánticos, secciones, aristas) en una caché hermana (`.sldb_cache/`), una declaración fuente (`store.yaml`) desde la que se reconstruye todo, escrituras atómicas con rollback (transacciones, lotes), procedencia firmada en el frontmatter (`sldb:`), políticas de hallazgos (`stores check`), y federación entre stores por links. [V: `docs/architecture/derived-store-boundary.md`, `store/layout.py`, `store/hashing.py`]

## 2. Layout en disco

Todo bajo la raíz del proyecto (padre de `.sldb/`). La caché es hermana del directorio del store, no hija: `cache_dir = store.parent / ".sldb_cache"` [V `store/layout.py:19`]. Formato YAML salvo indicación. Escritura de YAML: `StoreIOUtils._atomic_write` (tmp + fsync + `os.replace`) [V `store/io/utils.py`]; sólo se reescribe un shard si su contenido difiere (`_save_shard`, `_save_if_changed`) [V `store/io/shards.py`, `store/io/__init__.py`].

### 2.1 `.sldb/` (versionado entero en git desde 2026-11-04)

| Ruta | Contenido | Quién escribe |
|---|---|---|
| `.sldb/store.yaml` | La declaración (fuente). Ver sección 3. | humano; `save_declaration` (declare --write, `declare_tracked`, `forget_tracked`, `declare_model`) |
| `.sldb/core/store_index.yaml` | `StoreIndex`: `stores` (links `{name, path}`), `models` (`ModelEntry`: name, model_ref, path, models_index, version, canonical, family, semantics, origin), `predicates` (`{name, axis, description}`), `store_hash` (raíz Merkle; alias de lectura `hash_a`) | `save_store_index` en `cascade_store_hash`, link/unlink, apply_predicates, add/inherit/remove model |
| `.sldb/core/models/<Model>.yaml` | `ModelsIndex`: name, model_ref, path (fuente de la clase), documents_index, sections_index, `model_hash` (alias `hash_b`), `roster_hash`, `contract_hash`, `documents_count`, version, canonical, family, semantics, `base_models` | `save_models_index` en cada write de documento y en alta de modelo |
| `.sldb/core/documents/<Model>/<doc>.yaml` | Una hoja por documento (`DocumentEntry`): name, path (relativo a raíz), `source_hash` (alias `hash_c`), `content_hash` (alias `hash_d`), `field_hashes` (hash por campo), `semantic_tags`, `provenance` (bloque `sldb` indexado, o null) | `save_document_shard` |
| `.sldb/.config/` | `layout.config_dir` lo define pero nadie lo usa [V grep: sólo `layout.py:28`] | nadie (muerto) |

Rutas que los índices guardan son strings relativos a la raíz (`models_index`, `documents_index`). `sections_index` conserva un valor "legado" `.sldb/runtime/sections/<Model>.yaml` que es sólo una clave: los shards reales viven en `.sldb_cache/sections/` [V `layout.py:sections_index_relpath`, `io/sections_index.py`].

### 2.2 `.sldb_cache/` (ignorado por git; regenerable)

| Ruta | Contenido | Quién escribe |
|---|---|---|
| `.sldb_cache/locks/store.lock` | archivo de lock (`fcntl.lockf`, sin contenido) | `StoreIOLock` |
| `.sldb_cache/semantic/<Model>/<doc>.yaml` | `SemanticDocumentRecord` {model, path, tags, source_hash} | `semantic_doc_contribution.sync_doc_shard` |
| `.sldb_cache/sections/<Model>/<doc>.yaml` | `DocSections` {doc_name, sections[`SectionContextRecord`: path, title, breadcrumbs, about, semantic_tags, slug, level, line_start, line_end], source_hash, sections_version} | `section_doc_contribution` |
| `.sldb_cache/edges.yaml` | contribución del store al índice de aristas (nodo store, aristas del DAG semántico) | `edge_rebuild._save_store_shard` |
| `.sldb_cache/edges/<Model>.yaml` | contribución de un modelo (`ModelEdges`: nodo modelo, campos, extends, `projected_fields`) | `edge_sync.process_model_edges` |
| `.sldb_cache/edges/<Model>/<doc>.yaml` | `DocEdges`: nodes, edges, source_hash, content_hash, contract_hash, edges_version, semantic_tags, `source_stat` [mtime_ns,size] | `edge_doc_contribution.sync_doc_shard` |
| `.sldb_cache/semantic_dag.yaml` | `SemanticDAG`: nodos con padres, `equivalences`. **Incluye lo que el autor declara a mano** (`stores semantic-map`, `add_semantic_parent`), pese a vivir en la caché ignorada [V `layout.py:semantic_dag_path`, `store/semantic_parents.py`, `cli/commands/store_semantic_map.py`] | `save_semantic_dag` |
| `.sldb_cache/rules/<content_hash>.json`, `rules/dirty.json` | caché de resultados de RuleDocs y su dirty set (no profundicé) | `api/rules/rule_cache.py` |
| `.sldb_cache/composed/<16hex>.json` | índice de aristas compuesto persistido entre procesos, con clave de estado de shards y contratos | `edge_index/composed_disk.py` |
| `.sldb_cache/signals.jsonl` | log append-only de señales del servidor MCP | `mcp/signals/log.py` |
| `.sldb_cache/graphs/`, `.sldb_cache/corpus/` | grafos generados (`selfdoc python-sync`) e índice del corpus del servidor (`serve/corpus_routes.py`) [V doc; `corpus` verificado en código] | selfdoc / serve |
| `.sldb_cache/semantic_index.yaml` | archivo único legado de antes de los shards; se borra al migrar [V `io/semantic_index.py`] | nadie hoy |

### 2.3 Cachés fuera del repo (user cache)

Bajo `$XDG_CACHE_HOME/sldb` (o `~/.cache/sldb`), un archivo por store con clave `sha256(ruta resuelta del store)` [V `store/user_cache.py`]:
- `runtime-cache-extracted/<h>.json`: payloads extraídos por documento (clave: path, source_hash, mtime, size, contract hash, store, modelo). Se reescribe entero en `flush`. [V `store/runtime_cache_disk.py`]
- `runtime-cache-built/<h>.json`: `built_cache`, clave por modelo (`model_hash|version|semantics`) por clase de resultado (`semantic_shards`, `sections_shards`, `edges_shards`). [V `store/built_cache.py`]
- `content-scans/<kind>/<h>.json`: resultados por documento de `rewrite-loss` y `structural-noise`, clave = sha(texto)|contract hash|fingerprint del código lector (tamaño+mtime de todos los .py de core/runtime/models)|SCAN_VERSION. [V `api/stores/scan_cache.py`]

### 2.4 Otros
- Global: `~/.sldb` (store catálogo: lista de proyectos bajo HOME), `register_project_store`. [V `store/catalog.py`, `store/resolver.py`]
- `.gitignore` de la raíz: línea `.sldb_cache/`. [V `api/stores/gitignore_lines.py`]
- Legado: store con `store_index.yaml`, `semantic_*.yaml`, `models/`, `documents/` en la raíz de `.sldb/`; `.sldb/runtime/` (hasta 2026-11-04), `.sldb/runtime/journal/` (retirado). [V `store/migration_legacy_layout.py`, `api/stores/migrate_layout.py`]

## 3. Claves de `store.yaml`

Modelo `StoreDeclaration` [V `api/stores/store_declaration.py:24-52`]. Todas las listas/dicts por defecto vacíos; `folders`, `seeds`, `project_graph`, `tag_models`, `tag_namespaces` no se vuelcan al YAML cuando están vacías (`OPTIONAL_KEYS`).

| Clave | Significado | Ejemplo |
|---|---|---|
| `version` (int, 1) | versión del formato; no se valida nada con ella [I: no vi consumidor] | `version: 1` |
| `links` | lista `{name, path}` de stores enlazados; path relativo a la raíz. El alias sirve para `alias:Doc` y `alias:Model`. | `- {name: deskops, path: ../deskops/.sldb}` |
| `models` | `{name, model_ref}` + `path` (fuente local) **o** `origin` (alias del link dueño; modelo heredado) + `canonical: true` opcional. `apply_models` registra primero locales, luego heredados. | `- {name: TaskDoc, model_ref: "deskops.models:TaskDoc", origin: deskops}` |
| `predicates` | nombre -> `{axis, description}`. `apply_predicates` deja los predicados del store exactamente iguales a estos. Default de `stores init`: 7 predicados (is_solved_by, implements, mathematically_proves, explains_failure_of, grounded_by, restricts, defines) [V `store/predicates.py`]. | `implements: {axis: HOW, description: "..."}` |
| `generate` | generadores que `stores build` corre antes de trackear: `{command, args}`; son subcomandos de sldb ejecutados en proceso con `--store`; si salen != 0, el build falla. | `- {command: "selfdoc python-sync", args: [--out, knowledge/symbols]}` |
| `derived` | prefijos de ruta que los generadores escriben: no versionados y excluidos de la copia de `build --check` (`.sldb/core/` y `.sldb_cache/` siempre lo son). | `[knowledge/symbols/]` |
| `include` | reglas de tracking: `{model, glob}` (cada match con nombre = stem) o `{model, paths: [path | {path, name}]}`. Una ruta nombrada por varias reglas se resuelve a una fila (ver F-ST-12). | `- {model: SpecDoc, glob: "docs/specs/*.md"}` |
| `graph_backend` | sólo `networkx`; otro valor es `ValueError` al cargar. | `graph_backend: networkx` |
| `policy` | modelo (o `*`) -> código de hallazgo -> `ignore|allow|alert|error|reindex|hook: <cmd>`. Códigos sin prefijo `store/`, `-` y `_` equivalentes. | `"*": {missing: error}`, `InboxNoteDoc: {data_mutation: reindex}` |
| `folders` | directorios relativos a la raíz que `scaffold` crea. | `[inbox, notes/archive]` |
| `seeds` | `Seed`: `path`, `mode: if_missing|managed`, exactamente una fuente (`text`, `file` (relativo a template root = `.sldb/` o `pkg:modulo/ruta`), `document {model,name,payload}`), `track` (sólo con `document`, default true). `extra="forbid"`. | ver `docs/architecture/store-declaration.md` |
| `project_graph` | config del grafo de proyecto como datos; se valida al cargar (`config_from_mapping`); fuera de mi área. | dict libre |
| `tag_models` | modelos (y descendientes por `base_models`) que revisa la familia de tags; vacío = todos. | `[NoteDoc]` |
| `tag_namespaces` | lista única por nombre de `TagNamespace {name, hierarchical (true), folder_axis (false), anchored_by {model, field, value: tag|bare}}`; el store que declara alguno valida todo tag con namespace que escribe (regla `builtin:tag_namespace:<name>`). | `- {name: area, hierarchical: true, anchored_by: {model: AreaDoc, field: tag}}` |

Desacuerdos doc/código: `store-declaration.md` enumera claves hasta `seeds` pero el código tiene además `project_graph`, `tag_models`, `tag_namespaces` [V]. `declare_store.AUTHORED` conserva de una declaración existente: generate, derived, policy, graph_backend, folders, seeds, project_graph, tag_models, tag_namespaces [V `declare_store.py:36`]; `links`, `models`, `predicates` se rederivan del índice, `include` se completa sin reescribir globs.

## 4. Lista de features

Convención: ruta:línea aproximada (uso `archivo` sin línea cuando es el módulo entero). Tests por nombre [I].

### 4.A Ciclo de vida del store

**F-ST-01 init_store.** Crea `<path>/.sldb` con índice vacío sellado (`store_hash = hash_models_layer([])`), predicados por defecto, DAG vacío y `semantic_index` vacío; agrega `.sldb_cache/` al `.gitignore` si ninguna línea ya lo nombra; registra el store en `~/.sldb` si está bajo HOME. `force` borra el store previo (`shutil.rmtree`). Falla limpio (`StoreInitError`, borra lo escrito) si no se puede. `api/stores/init_store.py`, `store_skeleton.py`, `gitignore_lines.py`. CLI `stores init --path --force`; API `init_store`. Lee: nada. Escribe: `core/store_index.yaml`, `.gitignore`, catálogo global. Test: `tests/api/test_store_init_failures.py`, `tests/api/test_api_stores.py`. Oct (init limpio 10-18), layout reciente.

**F-ST-02 open_store.** Resuelve el store (ruta, alias de link de un ancestro o del store global, o descubre el más cercano subiendo desde cwd; en modo `readonly` cae al global con warning), migra el layout si hace falta (`migrate_store_layout`), pone en `sys.path` las raíces de import de los dueños de modelos heredados, y abre una "operación" nueva (`new_operation` en la firma de runtime y en `documents_hash`). TODA función de api abre así. `api/stores/open_store.py`, `store_discovery.py`. Escribe (puede): migración de layout. Test: `tests/store/test_resolver.py`, `tests/store/test_store_context_audit.py`.

**F-ST-03 read_store / store_is_prepared.** Igual que open_store pero nunca escribe: si el layout no es canónico lanza `SLDBStoreError("store not prepared: run sldb stores update")`. `api/stores/read_store.py`. **Reciente (11-16).** Test: no encontré uno dedicado [I; sólo `tests/api/test_run_view_reads.py` quizá].

**F-ST-04 migrate_store_layout (esquema de shards).** Mueve índices viejos (un solo archivo por modelo, `documents/<M>.yaml`, `semantic_index.yaml`, `sections/<M>.yaml`) a shards por documento; relocaliza el layout de raíz plano a `core/`. No reescribe si ya es canónico (`_already_canonical`: revisa cada modelo, cuesta una lectura de índices por apertura). `store/migration.py`, `migration_legacy_layout.py`. Test: `tests/store/test_legacy_layout_migration.py`, `test_upgrade_compat.py`.

**F-ST-05 migrate-layout (cache a `.sldb_cache`).** Mueve el contenido de `.sldb/runtime/` a `.sldb_cache/`, borra el journal sin archivar, borra `.sldb/runtime/`, reescribe `.gitignore` (quita sólo líneas `.sldb/core` y `.sldb/runtime`, pone `.sldb_cache/` en el lugar de la primera). Se niega si `.sldb_cache/` tiene contenido salvo `--force`. Idempotente. `api/stores/migrate_layout.py`, `gitignore_lines.py`. CLI `stores migrate-layout [--store] [--force]`. Test: `tests/store/test_migrate_layout.py`, `tests/store/test_runtime_wiped.py`. **Reciente (11-04).**

**F-ST-06 migrate-hashes.** Reescribe en `core/**/*.yaml` las claves `hash_a..hash_d` a `store_hash/model_hash/source_hash/content_hash` (los valores no cambian). `api/stores/migrate_hash_names.py`, `store/migration_hash_names.py`. CLI `stores migrate-hashes`. Test: `tests/store/test_migration_hash_names.py`, `tests/api/test_migration_hash_names.py`.

**F-ST-07 links entre stores.** `link_store` agrega `{name, path}` (path relativo a la raíz del linkeante); valida que el otro sea store, que no sea él mismo, nombre `^[A-Za-z0-9][A-Za-z0-9._-]*$` y distinto de `local`; `replace` re-apunta. `unlink_store` quita el link (rechaza si algún modelo local hereda de él). `store_links` calcula el estado de cada link al preguntar (`ok|missing|not-a-store`), nunca lo persiste. `api/stores/link_store.py`, `unlink_store.py`, `store_links.py`, `doc_ref.py`. CLI `stores add/remove/list`. Escribe `store_index.yaml`. Test: `tests/api/test_unlink_store.py`, `tests/test_links.py`, `tests/test_linked_doc_refs.py`. Oct.

**F-ST-08 catálogo global y reconcile.** `register_project_store` (bajo HOME, una vez por ruta); `reconcile_catalog` compara un subárbol (`rglob .sldb`) con el catálogo y con `--apply` lo reemplaza. `store/catalog.py`, `store/catalog_reconcile.py`. CLI `stores reconcile`, `stores list`. Test: `tests/store/test_catalog_reconcile.py`.

**F-ST-09 stores declare.** Imprime/escribe (`--write`) la declaración que el índice actual implica: links, modelos (con `origin` o `path` si está dentro del proyecto), predicados, include. Con declaración existente conserva las claves autoradas y los `include` tal cual, agregando sólo los documentos trackeados que ninguna regla nombra (un glob cubre por patrón aunque el archivo no esté en disco). Los includes derivados del índice agrupan por (modelo, directorio) y usan glob sólo si es exacto, >1 archivo, carpeta ≠ raíz, nombre = stem. `api/stores/declare_store.py`, `declared_includes.py`, `merged_includes.py`. CLI `stores declare [--write]`. Test: `tests/api/test_declare_store_authored.py`. Oct-11.

**F-ST-10 stores build.** Aplica la declaración: (1) crea el store vacío si falta; (2) `apply_links` (los que cambiaron; un link cuyo store no existe queda sin enlazar y se reporta como alerta `links_missing`); (3) `apply_models` (registra modelos faltantes: `inherit_model` o `add_model`); (4) `apply_predicates`; (5) `apply_includes` (trackea con `force=True` cada fila declarada no trackeada, existente y legible); (6) generadores; (7) `apply_includes` otra vez (los generados); (8) `update_store_indexes`; (9) calcula `undeclared`, `missing`, `links_missing`, `scaffold` (con apply), `include_conflicts`. Con `apply=False` no escribe y sólo reporta pendientes. Idempotente. CLI exit 1 si hay `undeclared`, `missing` o `include_conflicts`. `api/stores/build_store.py`, `apply_declaration.py`, `cli/commands/stores_build.py`, `stores_build_generators.py`. Test: `tests/store/test_stores_build.py`, `tests/store/test_include_resolution.py`. Oct-19; conflicto de includes 11-11; link ausente 11-10.

**F-ST-11 stores build --check.** Copia las fuentes (`git ls-files --cached --others --exclude-standard`) sin `.sldb/core/`, `.sldb_cache/` ni `derived` a un directorio temporal en la misma ruta absoluta bajo scratch, enlaza hermanos por symlink, corre `build` y `stores check` ahí, y compara filas (modelo,path,nombre) de la copia contra el store local; falla si el check de la copia falla, hay `undeclared`/`missing` o algo "only local". "Only rebuilt" es alerta `build/local-behind`. `cli/commands/stores_build_check.py`. Escribe sólo en el scratch. Test: `tests/store/test_stores_build.py` [I]. Oct-11.

**F-ST-12 resolución de include (una fila por ruta).** Si varias reglas nombran una ruta: mismo modelo -> una fila (gana `paths` por el nombre); `paths` de un hijo vs `glob` de su base (según `base_models` indexados) -> gana `paths`; cualquier otra combinación -> sin fila y error `store/include-conflict`. `untrack_losers` desrastrea del índice (no de `store.yaml`) las filas perdedoras de stores ya construidos, dentro de `update_store_indexes`. `api/stores/include_resolution.py`, `declared_files.py`, `include_losers.py`. Test: `tests/store/test_include_resolution.py`. **Reciente (11-11).**

**F-ST-13 scaffold_store / seeds.** Crea carpetas y semillas declaradas (`present/created/rewritten/conflicts`); `if_missing` nunca pisa, `managed` reescribe si los bytes difieren; fuentes `text`, `file` (template root o `pkg:`), `document` (renderizado por el modelo, trackeable). Todo dentro de `transaction`; rechaza rutas fuera de la raíz o absolutas. Acepta una `declaration=` en proceso que no escribe a `store.yaml`. `api/stores/scaffold_store.py`, `scaffold_sources.py`, `seed.py`, `scaffold_report.py`. CLI `stores scaffold [--apply] [--format]`, exit 1 con conflictos. Test: `tests/api/test_scaffold_store.py`. **Reciente (11-06).** Nota: una carpeta creada en una corrida cuyo rollback ocurre, queda vacía (doc).

**F-ST-14 update_store_indexes (`stores update`).** Re-hashea documentos tocados fuera de sldb. (a) `untrack_losers`; (b) por cada modelo importable, por cada documento: lee el archivo (guarda de symlink/raíz), `hash_text`; si coincide con `source_hash`, hay `field_hashes` y el contrato no se movió, lo salta; si no, re-extrae y recalcula `content_hash`, `field_hashes`, `provenance`; (c) bajo `store_lock` guarda los shards cambiados, re-sella cada modelo (`stamp_model_node`), `documents_count`, y reconstruye semantic/sections/edges y `cascade_store_hash`. Un modelo que no importa se salta pero re-firma su roster. `--prune-missing` desrastrea los ausentes; `--wait` espera el lock. `api/stores/update_store_indexes.py`, `index_refresh.py`, `index_commit.py`, `cli/commands/store_update.py`. Exit 1 si algo se saltó. Test: `tests/store/test_update_fault_tolerance.py`, `test_stale_shard_hashes.py`, `test_reread_remedy.py`. Detalle en sección 5.2.

**F-ST-15 check_store (`stores check`).** Corre `diagnose_store` (compara cada documento contra lo indexado: texto, campos, contrato, roster, raíz) y suma hallazgos del entorno (links, include-conflict, ruido estructural, rewrite-loss, modelos sombreados, gitignore, `unbuilt`), cada uno pasado por la política del store, más hallazgos de reglas `on_check` y anclas. Devuelve `{ok, valid, errors, alerts, damaged, reformatted, roster_only, moved_contracts, checked, mismatched, structural_noise, shadowed_models, broken_links, gitignore_policy, rewrite_loss}`. Sólo lee; `--apply` ejecuta `reindex` (un `stores update`) y `hook:` (comando con el hallazgo en JSON por stdin). Exit 1 sólo por errores. `api/stores/store_check.py`, `store_findings.py`, `store_surroundings.py`, `judge_findings.py`, `apply_findings.py`, `cli/commands/store_check.py`. Test: `tests/store/test_diagnostics.py`, `test_alerts_never_silent.py`, `test_doc_invalid_check.py`, `test_unresolved_model_check.py`, `tests/api/test_policy.py`. Códigos en sección 6.2. Oct-20 (política), 11-11 (doc-invalid).

**F-ST-16 diagnose_store.** Primitiva de F-ST-15 (`store/diagnostics.py`). Por documento: `DiagnosisNote` (`ok`, `benign_mutation` = texto cambió y campos iguales, `data_mutation`, `contract_mutation` = campos difieren por el contrato del modelo con texto intacto, `missing`, `path_outside_root`, `symlink_not_read`, `doc_invalid`), `changed_fields` (comparando mapas de hashes por campo), y si el texto difiere consulta git (`text_history`: `stale`, `unmatched`, `orphan`, `undetermined`, hasta 200 commits). Por modelo: `roster_ok`, `contract_ok`, `legacy_contract`. Por store: `root_ok`. Recomputa los hashes desde el disco: lee y re-extrae cada documento (no usa la caché de runtime). Test: `tests/store/test_diagnostics.py`, `test_text_history.py`, `test_field_set_drift.py`, `test_contract_in_model_node.py`.

**F-ST-17 store_fingerprint.** El árbol Merkle como un valor: `store_hash`, `{modelo: model_hash}`, `{"Model:doc": {source_hash, content_hash}}`, leído de los índices sin re-extraer. `api/stores/store_fingerprint.py`. Test: `tests/api/test_api_store_fingerprint.py`.

**F-ST-18 doc_status / doc_exists / tracked_documents.** Resuelven referencias de documento (`name`, `Model/name`, ruta trackeada, `alias:...`, `local:...`) desde los índices sin importar modelos ni escribir. Estados: `ok|missing|ambiguous|link-missing|link-broken`. `tracked_documents` resuelve muchas refs leyendo el índice una vez. `api/stores/doc_status.py`, `doc_ref.py`, `doc_ref_status.py`, `indexed_documents.py`, `tracked_documents.py`. CLI `docs exists`. Test: `tests/test_linked_doc_refs.py`, `tests/api/test_api_stores.py` [I]. Oct.

**F-ST-19 resolve_indexed_document / indexed_model_bases.** `Model:name` encontrado también bajo un modelo base (busca en modelos cuyas `base_models` lo incluyen); `AmbiguousDocument` si varios submodelos. `indexed_model_bases` da `{modelo: [bases]}` sin importar clases (para heredados usa el índice del dueño). `api/stores/resolve_indexed_document.py`. Test: `tests/api/test_resolve_indexed_document.py`. Nov-05.

**F-ST-20 store_declaration sync (declaración sigue al tracking).** Al trackear, destrackear o registrar/heredar un modelo por la api, se actualiza `store.yaml`: `declare_tracked` agrega la ruta a `include.paths` del modelo (si ninguna regla la nombra; `{path,name}` si nombre≠stem), `forget_tracked` la quita de todas las listas `paths` y borra reglas vacías, `declare_model`. Un store sin `store.yaml` no se toca. `api/stores/declaration_sync.py`. Test: `tests/api/test_api_stores.py` [I]. Oct-19/11-06.

**F-ST-21 tag namespaces.** Ver clave `tag_namespaces` en sección 3; `required(tag)` da los tags-ancla exigidos (prefijos por puntos si jerárquico) y `anchor_tag` el tag que aporta un documento ancla. La evaluación vive en reglas (fuera de mi área). `api/stores/tag_namespace.py`. Test: `tests/api/test_tags.py`, `test_tags_scope.py`. Nov-06/11-11.

**F-ST-22 política de hallazgos.** `Policy.of_store`, `response(code, model, subject)`: orden de resolución = `policy[modelo]`, `policy["*"]`, defaults. Defaults: error para `missing, model-unresolved, unbuilt, link-broken, check, unparseable, invalid, unavailable, core-ignored, include-conflict, doc-invalid`; `data-mutation` es error sólo si la autoridad está **declarada** como `source_raw` o `generated`; `structural-noise` es `allow` para modelos narrativos (template sin headings literales) o `authored` declarado; todo lo demás alerta. `reindex` y `hook:` se listan como alertas. `api/stores/policy.py`, `policy_subject.py`, `judge_findings.py`. Test: `tests/api/test_policy.py`. Oct-20.

**F-ST-23 gitignore_policy.** Con `git check-ignore --no-index` evalúa si `.sldb/core` o `store.yaml` están ignorados (error `stores/core_ignored`) y si la caché no (`stores/cache_not_ignored`, alerta). `None` fuera de un work tree. `api/stores/gitignore_policy.py`. Test: `tests/api/test_gitignore_policy.py`. Nov-04.

**F-ST-24 scans de contenido cacheados.** `scan_structural_noise` y `scan_rewrite_loss` recorren todos los documentos aceptados; resultados por documento cacheados en el user cache con la clave de F-2.3. `api/stores/scan_*.py`. Test: `tests/api/test_scan_cache.py`, `test_structural_noise.py`, `test_rewrite_loss.py`. Oct-18.

**F-ST-25 guardas de lectura (symlink y raíz).** Toda lectura/escritura de un documento trackeado pasa por `unreadable_reason`: rechaza rutas que resuelven fuera de la raíz (`store/path-outside-root`) o symlinks (`store/symlink-not-read`), y abre con `O_NOFOLLOW` (`read_source_text`, `write_source_bytes`). `store/source_read.py`, `source_guard.py`. Agregados nombran el hallazgo y siguen (`aggregate_text`), los comandos de un documento fallan con `SLDBDocumentTargetError`. Test: `tests/security/*`, `tests/store/test_live_stale_security.py`, `test_tracked_reads_guarded.py`. **Reciente (11-11).**

**F-ST-26 semantic export.** Payload `sldb_kgdb_semantic_export` v1 (contrato congelado; llaves con letras `hash_a..d` y con nombres nuevos más `roster_hash`/`contract_hash`): modelos, documentos (incl. provenance), secciones, DAG. Con `rebuild=True` reconstruye antes bajo lock. `store/export.py`. CLI `stores semantic-export`. Test: `tests/store/test_semantic_export.py`, `test_export_contract_frozen.py`.

**F-ST-27 semantic DAG.** Se crece incrementalmente con los tags nuevos (`sync_semantic_dag`: nodo por tag y arista por cada prefijo punteado `a.b.c`); `add/remove_semantic_parent` declaran padres extra con guardas (sin ciclos, no quitar el padre implícito del nombre); `stores semantic-map` agrega equivalencias simétricas; `tag_scope` da el tag y todo lo debajo. `store/semantic_dag_sync.py`, `semantic_parents.py`, `semantic_dag_graph.py`, `semantic_tags.py`. Test: `tests/store/test_semantic_dag_walk.py`.

### 4.B Hashing, árbol y derivados

**F-HS-01 hashes de documento.** `source_hash = sha256(texto utf8)`; `content_hash = sha256(json.dumps(payload extraído, sort_keys, ensure_ascii=False))`; `field_hashes = {campo: hash_payload(valor)}` (mapa vacío = "desconocido", no "sin campos"). `store/hashing.py`. Test: `tests/store/test_hashing.py`, `test_optional_field_hash_neutral.py`. La clave `sldb:` del frontmatter mueve `source_hash` pero no `content_hash` (el extractor la ignora) [V doc provenance §4; código: `index_block` + extractor].

**F-HS-02 roster y modelo.** `roster_hash = sha256(json.dumps(sorted([{name, hash_c, hash_d}] por name), sort_keys))` — **no incluye path, tags, field_hashes ni provenance**. `contract_hash = hash_payload({fields {nombre:[str(annotation), descripción]}, template, family, semantics, base_models, references, containment, compositions, (+reference_models si declarado)})`. `model_hash = hash_payload({contract_hash, roster_hash})`. `store/hashing.py:hash_model_contract`, `model_node.py:stamp_model_node`. Test: `tests/store/test_contract_in_model_node.py`, `test_merkle_*`.

**F-HS-03 raíz.** `store_hash = sha256(json.dumps([{name, hash_b: model_hash}] en el orden de store_index.models, sort_keys))`. **No ordena**: depende del orden de registro de modelos. No cubre predicados, links ni la declaración. `store/hashing.py:hash_models_layer`, `store/ops.py:cascade_store_hash` (recarga el `models/<M>.yaml` de TODOS los modelos en cada write). [V]

**F-HS-04 contrato legado.** Un `contract_hash` calculado con la fórmula anterior a cecd97b (2026-10-05, sólo campos+template) se reporta `store/legacy-contract` (alerta) en vez de contrato movido; `stores update` lo re-sella. `store/legacy_contract.py`. Test: `tests/store/test_contract_in_model_node.py` [I].

**F-HS-05 deriva de conjunto de campos.** Un documento firmado bajo otro set de campos (su `field_hashes` nombra un campo que la clase ya no declara) cuenta como contrato movido aunque el `contract_hash` del modelo ya se haya re-sellado por otro documento; distingue `extra="allow"` re-extrayendo. `store/field_set_drift.py`. Test: `tests/store/test_field_set_drift.py`. Oct.

**F-HS-06 shard de documentos y cache de roster por operación.** `documents_hash` guarda en memoria, por (store, modelo), las entradas compuestas de los shards y su roster; se revalida contra el `roster_hash` del índice del modelo al empezar una operación nueva (si no cambió se reutiliza). `note`/`forget` actualizan en sitio. Lleva un dirty set por espacio ("semantic", "sections", "edges") en `documents_dirty`. `store/documents_hash.py`, `documents_dirty.py`. Test: `tests/store/test_merkle_operation_cache.py`, `test_merkle_cost.py`, `test_documents_shards.py`.

**F-HS-07 semantic shards.** Un shard por documento con sus tags (`tags` = tags del modelo `__semantics__` aplanados ∪ campos `semantic_tags` y `tags` del payload). Reutiliza el shard si `source_hash` coincide; si el modelo rechaza el texto conserva los últimos tags (`store/doc-invalid`); si el modelo no importa, `models_unresolved` y deja los tags. `store/semantic.py`, `semantic_doc_contribution.py`, `semantic_doc_tags.py`, `semantic_tags.py`. Test: `tests/store/test_semantic_rebuild.py`.

**F-HS-08 section shards.** Parsea el Markdown (markdown-it gfm-like + plugin front_matter) y emite por heading: path jerárquico por slugs con `-N` en repetidos, breadcrumbs, `about` (breadcrumbs + slug sin guiones + tags), nivel, `line_start/line_end` (extendido hasta el siguiente heading de nivel ≤). Clave de caché: `model_key|sections:<SECTION_INDEX_VERSION=2>`. `store/section_rebuild.py`, `section_sync.py`, `section_doc_contribution.py`, `section_paths.py`. Test: `tests/store/test_section_rebuild.py`. Se resume una línea de aviso por modelo para documentos ausentes (11-16).

**F-HS-09 edge shards.** Por documento: nodos y aristas construidos con sus secciones, tags y payload leído; reutilizado si `source_hash`, `content_hash`, tags (como conjunto ordenado), `contract_hash` del modelo y `edges_version` coinciden, y si el `contract_signature` del nodo del documento coincide; `source_stat` evita reabrir el archivo en lecturas. Detalle del índice de aristas es de otro reporte. `store/edge_rebuild.py`, `edge_sync.py`, `edge_doc_contribution.py`, `edge_index/cache_keys.py`. Test: `tests/store/test_edges_follow_the_contract.py`, `test_edge_anchor_targets.py`.

**F-HS-10 built_cache.** Marca por modelo "ya derivé esto bajo esta clave" en el user cache; si la clave `model_hash|version|semantics` no se movió y el directorio de shards existe, se salta el modelo entero (no se abre ningún shard). `get_stale` permite ver la última clave aunque haya cambiado (usado por edges para detectar contrato movido). `store/built_cache.py`. Test: `tests/store/test_cache_identity.py`, `test_runtime_cache_outside_store.py`.

**F-HS-11 caché de payloads en runtime.** `load_runtime_documents` extrae cada documento una vez; clave en memoria y en disco: (store, path, source_hash[, mtime, size], contract_hash del modelo, nombre de store, nombre de modelo). `signature(store)` = (store_hash, model_hash de cada modelo, barrido de leaves: mtime+size de cada documento, una vez por operación). `SLDB_TRUST_CHAIN=1` omite el barrido y confía en la cadena de hashes. `store/query.py`, `runtime_cache.py`, `runtime_cache_signature.py`, `runtime_cache_disk.py`. Test: `tests/test_runtime_cache.py`, `tests/store/test_runtime_cache_outside_store.py`.

**F-HS-12 carga tolerante.** `load_runtime_documents(..., on_invalid="skip")` devuelve `RuntimeDocuments(documents, invalid[InvalidDocument(path, model, error)])`; `accepted_only` no usa caché. Federada (`include_linked`): recorre links una vez por store, seguro ante ciclos. `api/documents/runtime_documents.py`, `store/query.py`, `store/query_engine/accepted_load.py`. Test: `tests/store/test_doc_invalid_writes.py`. **Reciente (11-16).**

**F-HS-13 lock.** `store_lock(store, wait)` = `fcntl.lockf` exclusivo sobre `.sldb_cache/locks/store.lock`, no bloqueante salvo `wait`; reentrante por proceso (contador `_HELD`); busy = `SLDBStoreError("... is busy. Use --wait to block.")`. `_LOCK_TIMEOUT` declarado, sin uso [V]. `store/io/lock.py`. Test: `tests/api/test_apply_batch_lock.py`. Reentrancia reciente (11-16).

**F-HS-14 rollback.** `rollback_on_error(paths, forget_caches)` y `snapshot/restore_all` (bytes o ausencia). `store/io/rollback.py`. `RollbackError` con `unrestored`.

**F-HS-15 query (estructural/semántico).** Direcciones `st.{Model+}.doc.field` (estructurales), `se.<tag>` (semánticas), `gse.` (global), con prefijo de store `B:st...`; `list/get/glob/find`; el filtro `--where` es el lenguaje de predicados (`filter.py`, `where_*`, `rules.py`: `eval_predicate`), con campos por ruta punteada (`sldb.authority`). `store/query_engine/*`. Test: `tests/store/test_queries_expanded.py`, `test_predicate_language.py`, `test_where_*`, `tests/test_linked_structural.py`. No profundicé el lenguaje.

### 4.C Documentos

**F-DC-01 create_document.** Orden exacto en sección 5.1. CLI `docs create`. Escribe el `.md` (con bloque `sldb`) y trackea. Test: `tests/api/test_api_creation.py`, `test_api_documents.py`, `test_on_write_entering.py`, `test_structural_noise.py`.

**F-DC-02 save_document_payload.** Reemplaza el contenido por el renderizado de un payload completo; CLI `docs update`, `fields *`. Pasos en 5.1. Test: `tests/api/test_write_paths_validate.py`, `test_provenance.py`.

**F-DC-03 track_document_file.** Indexa un `.md` existente; lo rechaza si no hace round-trip bajo el modelo, tiene headings no declarados (según política) o claves de frontmatter no declaradas, salvo `force=True` (que saltea round-trip, ruido y frontmatter; igual evalúa `on_write` salvo que el archivo no extraiga). Ruta relativa a la raíz, no a cwd; rechaza symlinks y fuera de raíz. CLI `docs track [--glob]` (`track_glob` salta lo ya trackeado). Test: `tests/api/test_api_documents.py`, `tests/store/test_document_formats.py`.

**F-DC-04 untrack_document / forget_document.** Borra el shard del documento, actualiza roster/modelo/raíz y los derivados, bajo lock y con `rollback_on_error` sobre shard + models index + store index; quita la ruta de `store.yaml`. Deja el archivo. `api/documents/untrack_document.py`. CLI `docs untrack`.

**F-DC-05 delete_document.** `untrack` + `unlink` del archivo + purga de la entrada en `runtime-cache-extracted` (por sufijo de clave y nombre de archivo). `api/documents/delete_document.py`. CLI `docs delete`.

**F-DC-06 move_document.** `untrack` + `rename` + `track(force=True)` con el mismo nombre; el destino debe estar dentro de la raíz y libre. Mueve el archivo antes de re-trackear: no es atómico fuera de una transacción. `api/documents/move_document.py`.

**F-DC-07 transaction.** `with transaction(store) as tx:` con `create/update/delete/move/track/untrack/write_file/record`. Escribe de inmediato; rollback si el bloque falla: restaura bytes de todo archivo que cada operación puede tocar (md, shard de entrada, shards semantic/sections/edges, models index, edges del modelo, store index, edges del store, DAG, semantic_index, `store.yaml`, y `rules/dirty.json`). Anidar se une a la transacción abierta (por store). Al entrar, firma (mtime,size) de TODO archivo bajo `.sldb/` y `.sldb_cache/` salvo locks; al hacer rollback borra los nuevos y reporta los cambiados sin snapshot. Tras restaurar, invalida cachés. Mantiene el índice compuesto en memoria (sin escribirlo) mientras está abierta. `RollbackError`. `api/documents/transaction*.py`. Test: `tests/api/test_transaction.py`, `test_transaction_rules_dirty.py`. **Reciente (11-06), rules-dirty 11-09.**

**F-DC-08 apply_batch.** Prevalida todo (forma, ids únicos, modelo registrado, round-trip por payload, guardas, presencia/ausencia, ruta libre y dentro de raíz), y aplica en una transacción en el orden create/track -> update -> move -> untrack/delete; todo bajo `store_lock(wait=True)`. Devuelve `BatchReport(saved, inverse)` donde `inverse` es el lote que deshace (guardado por los source_hash que dejó). Guardas por cambio: `expected_source_hash`, `expected` (payload completo) -> `ConflictError`. `api/documents/apply_batch.py`, `batch_prevalidate.py`, `batch_inverse.py`, `batch_report.py`. Test: `tests/api/test_apply_batch_lock.py`, `test_write_paths_validate.py`. **Reciente (11-06; lock e inverso 11-16).**

**F-DC-09 replace_text.** Reemplaza exactamente `count` ocurrencias literales de `old` por `new` en un documento trackeado o en un archivo plano bajo la raíz; para documento: valida round-trip, corre `on_write`, y en la transacción `untrack` + `write_file(sha256 esperado)` + `track` con mismo modelo y nombre (la ruta sale y vuelve al final de `include.paths`). CLI `docs replace REF --old --new [--count]`. Test: `tests/api/test_replace_text.py`. **Reciente (11-06).**

**F-DC-10 select_documents / Selector.** Elige documentos por conjunción de `ref`, `prefix`, `path_glob`, `model` (familia), `where` (predicado sobre la proyección de payload del índice de aristas), `include_linked`; `expect="one"` hace ganar el nombre exacto sobre el prefijo; devuelve filas `{export_id, model, name, path}`; candidatos desde los índices de documentos sin importar modelos. `Selector` con `extra="forbid"` y rechazo de valores en blanco. `api/documents/select_documents.py`, `selector.py`, `store/query_engine/document_selection*.py`. CLI `docs select`. Test: `tests/api/test_select_documents.py`. **Reciente (11-06).**

**F-DC-11 runtime_document.** Una `RuntimeDocument` por nombre, ruta o `Model/name`, con `alias:`; ambigüedad y desconocido -> `SLDBDocumentTargetError`; un documento que la guarda de symlink rechaza se reporta con su hallazgo, no como desconocido. `leaf_paths(payload)` lista direcciones punteadas. `api/documents/document_payload.py`.

**F-DC-12 edición por ruta de payload.** `deep_get`, `deep_set`, `deep_delete`, `ensure_list`; `changed_paths`/`field_label` (diff de campos a rutas punteadas); `parse_data_value` (JSON luego YAML). `api/documents/payload_path_*.py`, `payload_diff.py`, `data_values.py`. Test: `tests/api/test_api_documents.py` [I].

**F-DC-13 ruido estructural.** `declared_headings(template)` -> headings literales y niveles abiertos (un heading-marcador `⸢rev•título⸥` abre su nivel); `find_structural_noise` lista headings del documento no declarados; `refuse_structural_noise` lanza `SLDBValidationError` salvo que la política (o el modelo narrativo / `authored` declarado) lo permita. Se aplica en `create_document` y `track_document_file`, NO en `save_document_payload` [V: `payload_save.py` no lo importa]. `api/documents/structural_noise.py`, `refuse_structural_noise.py`, `heading_scan.py`. Test: `tests/api/test_structural_noise.py`.

**F-DC-14 claves de frontmatter no declaradas.** Rechaza (al crear/trackear) claves top-level del frontmatter del documento que el template no declare, salvo si el frontmatter del template es un único marcador `dict` o la clave es `sldb`. `api/documents/frontmatter_keys.py`.

**F-DC-15 rewrite-loss.** Detecta bloques de contenido (párrafo, fence, listas, cita, tabla, html) del documento que no sobreviven a `render(extract(texto))`, excluyendo secciones totalmente derivadas del template. Hallazgo `store/rewrite-loss` (alerta). `api/documents/rewrite_loss.py`. Test: `tests/api/test_rewrite_loss.py`. Oct-18.

**F-DC-16 anclas a documentos (pinning).** Al crear/guardar, los `doc:Model:name` sin `@sha256` en campos `__doc_anchors__` y en `from:` de procedencia reciben el hash de contrato actual del destino; un destino no trackeado deja el valor tal cual. `api/documents/doc_anchor_pinning.py`. Test: `tests/api/test_doc_anchors.py`. Nov-06; create 11-16.

**F-DC-17 on_write.** Antes de escribir, reglas `on_write` (RuleDoc y familia de tags) evalúan el payload; error de severidad error -> `SLDBRuleViolationError`. En `save`: antes de renderizar; en `create`/`track`: después de renderizar/leer (`check_entering[_text]`). `api/documents/on_write_gate.py`; las reglas son de otro reporte. Test: `tests/api/test_on_write_entering.py`. **Reciente (11-11).**

**F-DC-18 document history.** Historia de un documento desde git: por commit que lo tocó (`git log --follow -n200`), el bloque `sldb` de esa versión (by/at/via/authority), `source_hash`, `content_hash` y campos cambiados contra la versión anterior; `--since`. `api/documents/document_history.py`, `document_history_versions.py`. CLI `docs history`. Test: `tests/api/test_document_history.py`. Oct-20.

**F-DC-19 serialización / IR.** `document_ast`, `build_document_ir_json`, `render_document_markdown`, `extract_document_payload`, `serialize_document` (incluye `version` = `content_hash` leído del shard), `extract_payload`. `api/documents/document_*.py`, `extract_payload.py`. No profundicé.

**F-DC-20 find_doc.** Busca por nombre (un chequeo de shard por modelo) y, si no, por ruta registrada; `SLDBError("Doc '<ref>' not found.")`. `api/documents/document_lookup.py`.

**F-DC-21 actor.** Quién firma `by`: argumento `actor=`, luego `set_actor` del proceso (CLI `--actor` global), luego `SLDB_ACTOR`, luego `agent`. `api/actor.py`. En `track`, `untrack`, `add_model` el parámetro `actor` se acepta y se ignora.

**F-DC-22 procedencia (`sldb:`).** Ver 5.3.

### 4.D Compat y muertos
**F-DD-01 journal retirado.** `api.journal()` devuelve `[]`, `verify_journal()` un reporte válido de 0 entradas, `sldb journal show|verify` imprime el aviso y sale 0; `JournalEntry` y `JournalVerifyReport` quedan; `doc_hashes(store, model, doc)` sigue en `api/journal.py`. Test: `tests/api/test_journal.py`. Retirado 2026-11-04.

**F-DD-02 facade.** `store/facade.py:get_tracked_docs(model, store, root)` -> `[(name, ruta absoluta)]`. [I: poco usado]

## 5. Rutas de escritura y de actualización

### 5.1 Escritura de un documento

**create_document** (`api/documents/create_document.py:35`):
1. `open_store` (migra layout si hace falta; nueva operación).
2. `load_registered_model` (sin registrar -> `SLDBStoreError`).
3. `pin_payload`: ancla `doc:` sin hash en campos `__doc_anchors__`.
4. Render + round-trip: `render_model_markdown(model, payload, project_root)` y `validate_model_input_roundtrip`; si no es idempotente -> `SLDBValidationError("Idempotency fail")`. Luego `refuse_unknown_family` (RuleDoc cuyo sujeto nombra un modelo no registrado -> `SLDBStoreError`).
5. Resolver la ruta (relativa a la raíz; symlink -> `SLDBDocumentTargetError`; una ruta simple fuera del proyecto sí se crea).
6. `_admit`: `refuse_structural_noise` (con política) + `refuse_undeclared_frontmatter` + `check_entering` (reglas `on_write`).
7. `_write`: crea directorios, `stamped(...)` (bloque `sldb`: by=actor, at=ahora UTC, via=`create_document`, authority=`__authority__` del modelo o `generated`), `pin_provenance`, y `write_source_text` con `O_NOFOLLOW`. **Sobrescribe el archivo si existía.**
8. `track_document` -> `StoreOperations.track`: `_pre_check` (si ya existe el shard del nombre -> `SLDBStoreError("Doc '...' already tracked")`; **ocurre después de haber escrito el archivo en el paso 7**, así que un nombre duplicado deja el archivo ya sobrescrito [V orden en `create_document.py` y `ops.py:_pre_check`]), lee el texto (O_NOFOLLOW), toma `store_lock` (no espera), y dentro: calcula `content_hash`/`field_hashes` (vacíos si no extrae), crea `DocumentEntry` con `provenance=index_block(texto)`, guarda el shard, `documents_hash.note`, actualiza el modelo (`stamp_model_node`: roster del mapa en memoria, contract hash de la clase, model_hash; `documents_count`), `rebuild_semantic_indexes`, `rebuild_sections_indexes`, `rebuild_edges_indexes`, y `cascade_store_hash`.
9. `declare_tracked`: agrega la ruta a `store.yaml` si ninguna regla la nombra.
10. Devuelve `DocumentReference(model, name, path)`.

**save_document_payload** (`payload_save.py`): `open_store` -> localizar modelo e índice (`SLDBPayloadSaveError` si el modelo o el documento no está registrado) -> clase del modelo (pythonpath, luego raíz del proyecto) -> `pin_payload` -> `check_rules_on_write` (antes de renderizar) -> `render_checked` (round-trip; `SLDBPayloadSaveError("Field mutation broke idempotency")`) -> `refuse_unknown_family` -> `write_rendered_document`: guarda de symlink, `stamped_file` (**conserva el bloque previo si el texto sin bloque no cambió**; si el documento declaraba autoridad la conserva, si no usa la del modelo; `from:` se hereda), `pin_provenance`, escribe con `O_NOFOLLOW`, recalcula `source_hash`, `content_hash`, `field_hashes`, `provenance` -> **bajo `store_lock`**: guarda el shard, `note`, re-sella el modelo, `documents_count`, guarda el models index, rebuild semantic/sections/edges, `cascade_store_hash`. **El archivo se escribe antes de tomar el lock y sin rollback propio**: si falla un paso de índice, el archivo queda nuevo con índice viejo; `stores check` lo mostrará como mutación (sólo una `transaction` lo deshace) [V: estructura de `payload_save._save` y `save_document_indexes`]. No pasa por `refuse_structural_noise` ni frontmatter (su salida es el render del propio template) [V].

**Dentro de una `transaction`/`apply_batch`**: cada operación llama a la función pública anterior con los archivos que puede tocar ya snapshoteados. `update` valida guardas (`expected_source_hash` contra `DocumentEntry.source_hash`, luego `expected` contra el payload actual extraído) antes.

### 5.2 Actualización y chequeo: cómo decide qué cambió y qué recomputar

Cadena (de hoja a raíz): `source_hash` (texto) -> `content_hash`/`field_hashes` (payload extraído) -> `roster_hash` (lista de (nombre, hash_c, hash_d) del modelo) + `contract_hash` (clase) -> `model_hash` -> `store_hash`.

**Qué se compara y con qué costo (código):**
- `stores update` **lee y hashea con sha256 el texto de TODO documento trackeado en cada corrida** (`_process_doc` -> `tracked_text` -> `hash_text`); no hay atajo por mtime/size. Sólo se evita la **extracción** (el paso caro) cuando `source_hash` coincide, hay `field_hashes` y el contrato no se movió (`_trusted`). Un contrato movido (`contract_moved`) o `field_hashes` ausente fuerza re-extracción de todo el modelo.
- `stores check` (`diagnose_store`) no usa ningún atajo: lee y re-extrae todos los documentos, recomputa roster y raíz, y por cada documento cuyo texto difiere llama a git (`git log --follow` + `git show` hasta 200 versiones). Los escaneos de ruido y rewrite-loss sí usan caché por (texto, contrato, código lector).
- **Derivados (semantic, sections, edges) por modelo**: antes de nada se mira `built_cache[modelo]`. Si la clave `model_hash|version|semantics` (más `|sections:2`, o `|model_ref|edges:<ver>`) no se movió y el directorio de shards existe, el modelo se salta entero sin abrir shards. Si se movió, se sincroniza **sólo el conjunto sucio** (documentos que `note`/`forget` marcaron en esta operación) cuando existe una línea base de esa clase de derivado; **si no hay línea base se compara cada documento del modelo** (abre su shard y compara `source_hash`, tags, contrato, etc.).
- La línea base (`documents_dirty._DIRTY`) es un dict en memoria del proceso [V `store/documents_dirty.py`]. [I, derivado del código] En un proceso CLI nuevo no existe, así que el primer rebuild de un modelo cuyo `model_hash` cambió (cualquier write a uno de sus documentos) abre y compara el shard de **cada** documento del modelo (semantic, sections y edges: tres pasadas, y en edges además un `lstat` por archivo para `source_stat`), y no sólo el documento tocado. En un proceso largo (servidor) o dentro de un lote/transacción el conjunto sucio sí limita el trabajo.
- Las cachés de shards en memoria (`io/shards._SHARDS`) y de índices (`io/_INDEXES`) se validan por (mtime_ns, size) del archivo; son por proceso.
- `signature()` y `leaf()` hacen un `stat` por documento una vez por operación (`new_operation`) para detectar ediciones hechas por fuera; `SLDB_TRUST_CHAIN=1` lo evita.
- Toda escritura recarga todos los `models/<M>.yaml` (cascada de raíz) y, en `rebuild_edges_indexes`, reescribe sólo si cambió la contribución del store. [V]
- `stores build` trackea cada documento nuevo individualmente con `track_document_file`: cada track repite `open_store` (`_already_canonical` recorre modelos), `declare_tracked` (carga `store.yaml`, hace `glob` del árbol vía `declared_files` y calcula bases) y el ciclo completo de rebuild + cascada. [V `apply_declaration.apply_includes`, `declaration_sync.declare_tracked`] Con miles de documentos generados (el doc cita 2.561 `PythonSymbolDoc`) eso es el patrón que más recomputa [I].
- `transaction` hace un `rglob` + `stat` de todo `.sldb/` y `.sldb_cache/` al entrar (y otra al hacer rollback). [V `transaction_paths.store_signatures`]
- `runtime-cache-extracted` se carga y reescribe entero (un JSON con todos los payloads del store). [V `runtime_cache_disk.entries/flush`]

**Recomputo mínimo cuando cambia un campo:** se salta la extracción si el texto no se movió; si se movió, se re-extrae sólo ese documento (más el modelo entero si el contrato cambió). El mapa `field_hashes` permite saber qué campos se movieron (`changed_fields` en check; `changed_paths` y `docs history`), pero [V] ningún derivado del store lo usa para recomputar por campo: los tres derivados se reconstruyen por documento completo.

**Ruta de update paso a paso (`update_store_indexes`):** `open_store` -> `untrack_losers` (include) -> por modelo (resolver clase; si falla -> `skipped_models`, `_queue_roster_only`) -> por documento `tracked_text` (ausente o ilegible -> `skipped_documents`) -> `_trusted?` -> `_restamp` (actualiza hashes, `provenance`) -> `commit_index_updates` bajo `store_lock(wait)`: `save_documents_index` (sólo shards distintos), `note` de los cambiados, `stamp_model_node` + `documents_count` + `save_models_index`, luego `rebuild_semantic_indexes`, `rebuild_sections_indexes`, `rebuild_edges_indexes`, `cascade_store_hash`. Devuelve `StoreUpdateReport(skipped_models, skipped_documents, semantic_index, sections_index)`; `complete` = nada saltado.

**Ruta de check paso a paso:** `diagnose_store` (por modelo: resolver clase, `legacy_contract`, `document_drifted` por documento, `contract_ok`, diagnóstico por documento con `fields_state` -> `DOC_INVALID` si pydantic rechaza, `roster_ok`; por store: `root_ok`) -> `diagnosis_findings` -> `surroundings_findings` (unbuilt corta todo; links, include-conflict, ruido, rewrite-loss, sombreados, gitignore) -> `judged` (política) -> `on_check_findings` -> `once_per_invalid`.

### 5.3 Procedencia (`sldb:`)

Bloque YAML bajo la clave `sldb` en el frontmatter (primera clave): `by`, `at`, `via`, `authority` (`source_raw|authored|theorem|generated|cache`), `from` (lista de `code:<path>::<name>` o `doc:Model:name[@sha256:<64hex>]`). Sin bloque: `agent`, `0`, `generated` (`declared=False`). `at` es UTC ISO con `Z`, sin microsegundos. [V `models/provenance.py`, `provenance_block.py`, `api/documents/provenance_stamp.py`]
- Se sella en cada escritura de la api (create, save, backfill, relaciones builtin) con `stamped`: **sólo cambia si el texto (sin bloque) cambió**; si no, se conserva el bloque anterior (para que un generador no mueva `at`). `track_document_file` no escribe ni sella.
- `__authority__` de la clase fija el default por modelo; el bloque puede subirlo o bajarlo. Sin declaración: `generated`.
- El índice guarda el bloque normalizado en `DocumentEntry.provenance` (`None` sin bloque); `find --where 'sldb.authority = "source_raw"'` lo consulta; lo exponen export y `RuntimeDocument`.
- El bloque está en el texto: mueve `source_hash`, no `content_hash`. Es regla del extractor ignorar claves no declaradas; `refuse_undeclared_frontmatter` exenta `sldb`.
- La historia vive en git (F-DC-18); la identidad de git no se usa para `by`.
- La autoridad alimenta la política (F-ST-22). Los niveles `theorem`, `cache` no tienen comportamiento propio en esta área salvo como etiqueta.
- Test: `tests/api/test_provenance.py`, `test_provenance_index.py`, `test_document_history.py`. Oct-20.

## 6. Invariantes y errores

### 6.1 Invariantes y garantías
1. Un documento está trackeado bajo exactamente un modelo y un nombre único por modelo (`_pre_check` por existencia de shard); nombres de documento se buscan en todos los modelos al resolver por nombre. [V `ops.py`, `document_lookup.py`]
2. Ninguna lectura escribe en `.sldb/` (`read_store`, `readonly`); las cachés de proceso viven en el user cache. [V doc + `user_cache.py`]; salvedad: `open_store` migra layout incluso en `readonly`. [V]
3. Escritura atómica de cada archivo del índice (tmp+fsync+replace) y "no escribir si no cambió". [V]
4. Toda escritura de documento valida el round-trip bajo el modelo (G4): `create`, `save`, `apply_batch`, `transaction`, `replace_text`, `track` (salvo `force`). [V doc + código]
5. `store_hash` cubre sólo `model_hash`s en orden de registro; no cubre predicados, links, declaración. [V]
6. Un modelo no importable no aborta el store: se salta, se re-firma su roster (update), y se reporta (`store/model-unresolved`, error en check). [V]
7. Rutas trackeadas nunca se siguen fuera de la raíz ni por symlink. [V]
8. Una transacción es atómica sobre md + índices + derivados + `store.yaml`, salvo archivos que cambian sin snapshot (reportados). [V]
9. Un lote (`apply_batch`) se aplica entero o nada, bajo un lock que cubre prevalidación y escritura; devuelve su inverso. [V]
10. Sin transacción, `create_document`, `save_document_payload`, `move_document`, `track` no son atómicos entre archivo e índice (ver 5.1). Sólo `untrack` tiene rollback propio. [V]
11. La caché es borrable: `stores build` o `edges rebuild` la regenera (`test_runtime_wiped`) [doc]. Excepción de contenido: el DAG semántico (padres y equivalencias declarados a mano) y `rules/dirty.json` viven allí [V layout]; borrar la caché pierde esa declaración [I].
12. Una alerta nunca es silenciosa; el exit code sale sólo de errores. [doc `provenance.md §5`, `findings` en código]

### 6.2 Códigos de diagnóstico (hallazgos)
Errores por defecto: `store/missing` (archivo trackeado ausente), `store/model-unresolved`, `store/unbuilt`, `store/link-broken` (directorio que no es store), `store/doc-invalid` (el modelo rechaza el texto; se nombra una vez y se omiten los otros hallazgos de ese documento), `store/include-conflict`, `store/structural-noise` (error en `surroundings`, pero la política por defecto lo `allow`a en modelos narrativos/`authored`), `stores/core_ignored`; también por política `check`, `unparseable`, `invalid`, `unavailable`. Alertas: `store/root-stale`, `store/roster-stale`, `store/contract-moved`, `store/legacy-contract`, `store/data-mutation` (error si autoridad declarada `source_raw`/`generated`), `store/contract-mutation`, `store/path-outside-root`, `store/symlink-not-read`, `store/rewrite-loss`, `store/model-shadowed`, `store/link-absent` (directorio ausente), `stores/cache_not_ignored`, `build/link-missing`, `build/local-behind`, `build/check` (error). Un documento `benign_mutation` (texto distinto, campos iguales) no genera hallazgo, sólo cuenta como "reformatted". [V `store_findings.py`, `store_surroundings.py`, `policy.py`, `stores_build_check.py`]
Los códigos de `rules` (on_check) y anclas no los revisé.

### 6.3 Excepciones
`SLDBError` (base); `SLDBStoreError` (también `StoreError`): store ocupado, no existe, ya trackeado, link inválido, ruta fuera de raíz, layout no preparado, modelo no registrado; `StoreInitError` (con `precondition`); `ConflictError` (hereda `SLDBStoreError`: `ref, expected, actual`); `RollbackError` (hereda `SLDBStoreError`, `unrestored`); `SLDBValidationError` (round-trip, ruido, frontmatter, argumentos de replace_text, payload de lote; lleva `details`); `SLDBPayloadSaveError` (guardar un documento/modelo no registrado o que rompe idempotencia); `SLDBDocumentTargetError` (ValueError; documento desconocido/ambiguo, symlink, ruta fuera de raíz); `AmbiguousSelectionError` (hereda de la anterior; `candidates`); `AmbiguousDocument` (LookupError, de `resolve_indexed_document`); `SLDBModelError` (clase no importable, modelo duplicado); `SLDBRuleViolationError` (`on_write`); `SLDBFieldPathError`/`SLDBFieldTypeError` (rutas de payload); `WherePredicateError`; `FileNotFoundError` (sin `store.yaml` al cargar la declaración) y `ValueError` (backend no admitido); `pydantic.ValidationError` (declaración o `Seed` malformados, selector con clave desconocida). `SLDBError("Doc '<ref>' not found.")` en `find_doc`. [V `core/exceptions/*`, usos en los módulos citados]

## 7. Lo que no pude determinar; muertos, duplicados, a medias

**No determinado**
- Cómo se reparten hash y costo exacto en `edge_index` (aristas, `live_stale`, `compose`), ni el lenguaje `--where` en detalle; son de otro reporte.
- Qué tests cubren exactamente cada feature: no abrí los tests; asigné por nombre.
- Si `version` en `store.yaml` tiene algún consumidor: no lo encontré [I].
- Qué escribe la `ModelsIndex` creada en `add_model` en cada campo (`write_new_model_indexes`, de `model_registry`).
- Si `stores list` y `stores semantic-export` tienen contratos más allá de lo visto.

**Desacuerdos doc vs. código**
- `docs/versionado-y-colaboracion.md` §1 describe `model_hash` como firma de `[{name, source_hash, content_hash}]`: eso hoy es `roster_hash`; `model_hash` incluye además `contract_hash` [V `hashing.py`]. Su sección "Pasos de una escritura" menciona `record(...)` al journal (retirado el 11-04) y propone un protocolo `base_version` que no está implementado (el doc mismo dice "no implementa").
- `derived-store-boundary.md` omite en el layout `rules/`, `composed/`, `signals.jsonl`; cita `corpus/` y `graphs/` (que sí existen). Dice que el DAG semántico es caché ("padres y equivalencias declaradas") sin advertir que lo declarado a mano no se puede regenerar.
- `store-declaration.md` no lista `project_graph`, `tag_models`, `tag_namespaces`.
- `store-extraction-proposal.md` es un draft del 2026-10-18; sólo se realizó el seam del codec (`store/codec.py:StoreCodec`, `default_codec`, `runtime_codec.py`) y los "tres seams" del commit beb8a99c; no existe el protocolo `Document` y el store sigue importando `sldb.core`, `sldb.models.provenance_block`, `sldb.runtime.validation` [V imports]. `migration_strategy.md` es histórico: `sldb_store`/`sldb_query` no se extrajeron [V: el árbol `store/` y `query_engine/` siguen en `sldb`].

**Muertos, duplicados o a medias**
- `layout.config_dir` sin uso. `_LOCK_TIMEOUT` repetido y sin uso en `io/lock.py`, `io/utils.py`, `io/*_index.py`; cabeceras de import copiadas (importan nombres que no usan). `store/io/rollback.py`, `lock.py` OK.
- Alias deprecados: `hash_a/b/c/d` (propiedades de los modelos, claves de lectura, export v1), `cascade_hash_a`, `hash_b_of`, `DocumentDiagnosis.hash_c_ok`... [V].
- `sections_index` como clave fantasma `.sldb/runtime/sections/<M>.yaml` (cambiarla reescribiría todos los `core/`). [V]
- `semantic_index_path` (archivo legado) y `legacy_*_path` siguen en el layout.
- `StoreIOLock._LOCK_TIMEOUT` y en `SemanticIndex` se mantiene `SemanticIndex.tags` invertido que se calcula pero el write path usa shards.
- Cuatro cachés con claves parecidas pero distintas (`built_cache`, `runtime-cache-extracted`, `content-scans`, `.sldb_cache/composed`) y tres mecanismos de "no cambió" (clave por modelo, dirty set en memoria, `source_stat`); sin una única noción de "cambió" persistida por documento (sólo `source_hash` en cada shard). [I]
- `RuntimeDocument.field_hashes` y `DocumentEntry.field_hashes` existen, pero ningún derivado los usa para recomputar por campo (sólo diagnóstico, historia, y un consumidor externo previsto: embeddings). [V búsqueda en el área]
- `docs history` y `text_history` duplican `_git(...)` (dos implementaciones de `git log --follow` + `git show`). [V]
- `api/stores/index_refresh.py` y `cli/commands/store_update.py` re-exportan nombres "por compat" (código movido de CLI a api).
- `apply_findings` ejecuta hooks con `subprocess.run(shlex.split(cmd))`, cwd = raíz: es ejecución arbitraria declarada por el store (`policy: hook:`). [V]

