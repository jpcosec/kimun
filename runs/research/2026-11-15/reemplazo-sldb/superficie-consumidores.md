# Qué usan de sldb sus consumidores: insumo para el contrato estable (2026-11-15)

> Evidencia no autoritativa. Es el insumo de `RS2` (`docs/v2/12-ecosistema-y-pendientes.md §1`):
> el contrato se escribe contra este uso real, no contra la API interna de sldb.
>
> **Cómo se hizo:** solo lectura sobre `/home/jp/proyectos/SLDB_1` (sldb en `641ecdfa`). Los
> imports se sacaron parseando cada archivo Python con `ast`, así que incluyen los que están
> dentro de funciones; lo demás, con `grep`. Los totales por consumidor se volvieron a contar
> aparte y coinciden. Las rutas son relativas a `/home/jp/proyectos/SLDB_1/`; las de sldb, a
> `sldb/src/sldb/`. **[I]** marca lo inferido.

## 1. Alcance

Cinco consumidores Python en el workspace: **deskops, pron, spec2viz, UIs/graph_ui e IR (specIR)**.

- `sldb-astryx/` no es consumidor: es el mismo repo de sldb en la rama `spike/astryx` [I].
- `scion` no importa sldb; solo lo instala (`scion/hooks/pre-start-deskops.sh:62-84`).
- `UIs/kbsurfaces` y `UIs/sldb-ui` usan solo CLI y archivos (§5 y §6). `UIs/marcado` no tiene acoplamiento.
- Fuera del workspace también importan sldb, sin inventariar: `TraderBot` (24 archivos),
  `legos` (7), `VSM viewer` (5), `midlifecrisissimulator` (3), `office` (2), `legacy` (67), y el
  propio kimun (15 archivos de `scripts/parity/`).

## 2. Cuánto y por dónde entran

527 sitios de import. Unos 140 van a módulos internos de sldb, no a su API pública.

| Consumidor | Sitios | Archivos | No test / test | Fachada (`sldb`, `sldb.api`) | Rutas profundas `sldb.api.*` | `sldb.cli*` | Internos (`store`, `runtime`, `core`, `links`) | `sldb.models*` | `sldb.mcp` / `sldb.notations` |
|---|---|---|---|---|---|---|---|---|---|
| deskops | 330 | 100 | 181 / 149 | 118 | 64 | 21 | 123 | 4 | 0 |
| pron | 75 | 53 | 65 / 10 | 29 | 15 | 7 | 24 | 0 | 0 |
| spec2viz | 29 | 9 | 23 / 6 | 28 | 0 | 0 | 0 | 1 | 0 |
| UIs/graph_ui | 56 | 19 | 36 / 20 | 19 | 16 | 5 | 13 | 3 | 0 |
| IR | 37 | 19 | 18 / 19 | 8 | 18 | 0 | 0 | 5 | 6 |

Ningún consumidor importa nombres con guion bajo inicial. Hay 9 tests que parchean sldb por
ruta de atributo (por ejemplo `deskops/tests/test_message_cli.py:211` y
`spec2viz/tests/test_refs.py:131-225`): dependen de la ruta del módulo, no solo del comportamiento.

### 2.1 Módulos internos más usados

| Módulo de sldb | deskops (no test / test) | pron | graph_ui | Nombres |
|---|---|---|---|---|
| `sldb.runtime.validation` | 23 / 45 | 4 / 1 | 2 / 1 | `extract_model_data`, `render_model_markdown`, `validate_model_input_roundtrip`, `validate_model_data_roundtrip`, `extract_payload`, `Validator` |
| `sldb.store.io` | 8 / 10 | 3 / 0 | — | `load_store_index`, `load_models_index`, `load_documents_index` |
| `sldb.core.exceptions` | 10 / 5 | 6 / 1 | 0 / 1 | `SLDBError`, `SLDBStoreError`, `SLDBModelError`, `SLDBValidationError`, `SLDBDocumentTargetError`, `SLDBPayloadSaveError` |
| `sldb.store.layout` | 8 / 0 | 1 / 1 | 0 / 4 | `project_root`, `store_exists`, `edges_shards_dir`, `edges_shards_root` |
| `sldb.cli` / `sldb.cli.main` | 0 / 11 | 1 / 6 | 0 / 1 | `main` |
| `sldb.store.hashing` | 3 / 4 | — | — | `hash_model_contract`, `hash_fields_with_map` |
| `sldb.cli.model_utils`, `sldb.cli.store_context` | 6 / 1 | — | 2 / 1 | `registered_model`, `resolve_model_ref`, `get_store_context` |
| `sldb.store.query`, `sldb.store.query_engine.filter` | 0 / 1 | 4 / 0 | 0 / 1 | `load_runtime_documents`, `find_structural`, `get_structural`, `glob_structural`, `list_structural`, `DocumentFilter` |
| `sldb.store.diagnostics` | 0 / 1 | 1 / 0 | — | `diagnose_store` |
| `sldb.store.runtime_cache*`, `sldb.store` | — | 2 / 0 | 1 / 0 | `new_operation`, `documents_hash` |
| `sldb.links`, `sldb.links.sections` | — | — | 3 / 0 | `parse_links`, `split_anchor`, `extract_section` |
| `sldb.core.ast`, `sldb.core.template_extractor` | 2 / 0 | — | — | `AST_Handler`, `TemplateExtractor` |

### 2.2 Rutas profundas bajo `sldb.api`

| Módulo | Quién | Nombres |
|---|---|---|
| `sldb.api.edges.project_graph` (y `.frontmatter_mapping`) | deskops (19 / 1) | 17 nombres: `ProjectGraph`, `ProjectGraphConfig`, `DocumentNode`, `FileNode`, `FileRule`, `MentionPattern`, `MissingProjectTarget`, `extract_document_nodes`, `extract_file_nodes`, … |
| `sldb.api.stores.*` | deskops (13 / 4), graph_ui (3 / 0) | `apply_includes`, `declare_store`, `declared_files`, `tracked_rows`, `doc_status`, `indexed_documents`, `init_store`, `open_store`, `declaration_path`, `load_declaration`, `save_declaration` |
| `sldb.api.edges.{referrer,referrers,rewrite_mentions,rewrite_references}` | deskops (8 / 0) | `Referrer`, `Target`, `find_hits`, `resolve_target`, `mention_hits`, `rewritten_line`, `rewrite_references` |
| `sldb.api.documents.*` | deskops (4 / 3), graph_ui (1 / 0) | `transaction_ops`, `undeclared_frontmatter_keys`, `replace_text`, `find_structural_noise`, `track_document_file`, `Transaction`, `doc_version` |
| `sldb.api.rules.{rule_graph,rule_subjects,rules_on_transition}` | deskops (4 / 0) | `rule_graph`, `resolve_subject`, `has_rules`, `payload_findings` |
| `sldb.api.graph`, `.graph.snapshot` | deskops, pron, graph_ui | `GraphSnapshot`, `snapshot_load`, `snapshot_save`, `KnowledgeNode`, `Edge`, `StructuredQuery`, `execute_query`, `query_snapshot`; pron: `children`, `descendants`, `neighbors_via`, `parent`, `roots`, `sources`, `targets` |
| `sldb.api.notations` | IR (4 / 14) | `load_spec`, `validate_spec`, `SpecSync`, `component_spec_from_graph`, `evaluate_projection_view`, `list_projection_views`, `node_id`, `resolve_view` |
| `sldb.api.corpus.*`, `sldb.api.matching.*`, `sldb.api.model_create.*` | pron (14 / 0) | `Corpus`, `CorpusEntry`, `Hit`, `IndexProjection`; `DifflibMatcher`, `DocumentIndex`, `Embedder`, `Matcher`, `cosine`; `FieldDecl`, `ModelCheck`, `ModelSource`, `example_payload` |
| `sldb.api.records.{extract,resolve}`, `sldb.api.edges.edge_reading`, `sldb.api.where` | graph_ui | `extract_sections`, `resolve_runtime_doc`, `load_edge_index`, `where_matches` |
| `sldb.mcp.{changes,paging,registry,write}` | IR (4 / 0) | `AssertEdge`, `Change`, `CreateDocument`, `UpdateDocument`, `page`, `WriteTools`, `PROPOSES`, `READ_ONLY`, `WRITES` |
| `sldb.models.notation.diagram_specs.*` | IR (5 / 0) | 8 clases de diagrama, `BaseDiagram`, `DiagramType`, `DiagramStoreEnvelope`, `AnyDiagram`, `EnforcementArtifact`, `EnforcementFact` |

### 2.3 Nombres tomados de la fachada `sldb.api`

- **deskops:** `doc_status` (16), `track_document_file` (10), `add_model` (9), `transaction` (7),
  `init_store` (5), `where_matches` (4), `link_store` (4), `check_store`, `doc_exists`,
  `edges_from`, `store_links`, `update_store_indexes` (3 cada uno), y 1 o 2 de: `Seed`,
  `StoreDeclaration`, `TagNamespace`, `Transaction`, `AmbiguousSelectionError`, `ConflictError`,
  `apply_effects`, `check_doc_anchors`, `describe_model`, `edges_blocked`, `edges_holding`,
  `inherit_model`, `init_relations`, `is_model_registered`, `remove_model`, `replace_text`,
  `rules_check`, `rules_explain`, `run_view`, `list_views`, `create_document`,
  `save_document_payload`, `scaffold_store`, `select_documents`, `store_fingerprint`, `tag_check`,
  `tag_folder`, `tag_namespaces`, `tag_tree`, `tracked_documents`, `transition_findings`,
  `unresolved_references`, `untrack_document`.
- **pron:** `resolve_model_ref` (4), `WherePredicateError`, `deep_get`, `deep_set`, `deep_delete`,
  `EdgeIndex`, `RegisteredModel`, `StoreUpdateReport`, `add_model`, `add_model_field`,
  `remove_model_field`, `check_edges`, `create_model`, `describe_model`, `describe_model_fields`,
  `edit_model_template`, `init_relations`, `link_store`, `load_edge_index`,
  `load_registered_model`, `locate_model_source`, `open_store`, `promote_model_draft`,
  `validate_model_draft`, `rebuild_edges`, `save_document_payload`, `track_document_file`,
  `untrack_document`, `update_store_indexes`.
- **spec2viz** (casi todo por `import sldb.api as api`): `load_runtime_documents` (5),
  `create_document`, `doc_exists`, `load_registered_model`, `where_matches`, `init_store`,
  `init_relations`, `rebuild_edges`, `add_model`, `check_edges`, `delete_document`, `open_store`,
  `save_document_payload`, `edges_from`, `edges_to`, `load_edge_index`, `store_links`.
- **graph_ui:** `rebuild_edges` (4), `open_store` (3), `add_model`, `create_document`,
  `build_document_ir_json`, `AmbiguousDocument`, `indexed_model_bases`,
  `resolve_indexed_document`, `is_model_registered`, `load_registered_model`,
  `load_runtime_documents`, `save_document_payload`, `track_document_file`, `untrack_document`,
  `init_store`, `load_edge_index`.
- **IR:** `is_model_registered` (4), `Seed` (4), `add_model`, `select_documents`,
  `load_runtime_documents`, `StoreDeclaration`, `init_relations`, `open_store`, `scaffold_store`,
  `update_store_indexes`, `runtime_document`, `serialize_document`, `run_view`, `init_store`.

## 3. Qué es público y qué es interno en sldb

Lo que sldb declara:

- `__init__.py:8`, `__all__` de 9 nombres: `StructuredNLDoc`, `AST_Handler`, `TemplateExtractor`,
  `DataExtractor`, `SLDBRenderer`, `SLDBConfig`, `configure`, `get_config`, `reset_config`.
- `api/__init__.py:148`, `__all__` de 183 nombres. `sldb/contracts/layers.toml` llama a `api`
  «la frontera pública» y a `cli`, `selfdoc` y `mcp`, capa de transporte.

| Lo que importan | Clase | Nota |
|---|---|---|
| `sldb.StructuredNLDoc`, nombres de la fachada `sldb.api` | público | salvo `build_document_ir_json`, que no está en `__all__` |
| `sldb.api.<sub>.<módulo>` de un nombre que la fachada reexporta | nombre público, ruta interna | la ruta es una ubicación de archivo |
| `sldb.api.notations`, `.records`, `.corpus`, `.matching`, `.model_create`, `.edges.project_graph` | público (`__all__` del subpaquete) | `SpecSync` y `component_spec_from_graph` no están listados |
| `sldb.api.graph` | sin declarar | no tiene `__all__` |
| `sldb.api.stores.{apply_declaration,declare_store,declared_files,declared_includes,indexed_documents,store_declaration}`, `sldb.api.documents.{frontmatter_keys,structural_noise,transaction_ops}`, `sldb.api.edges.{referrers,rewrite_mentions}`, `sldb.api.rules.{rule_graph,rule_subjects,rules_on_transition}` | interno | en ningún `__all__` |
| `sldb.core.exceptions` | semipúblico | solo `AmbiguousSelectionError`, `ConflictError` y `WherePredicateError` salen por `sldb.api` |
| `sldb.runtime.validation` | interno | capa 0; `contracts/identity.toml:385` lo registra «sin nombre público» |
| `sldb.store.*` | interno | `sldb.store.__all__` incluye `load_*_index` y `diagnose_store`; `layout` y `hashing` no |
| `sldb.cli.*` salvo `main` | interno (transporte) | deuda «interfaz-como-libreria» en `identity.toml:367-379` |
| `sldb.models.relation_doc`, `rule_doc`, `notation.diagram_specs.*` | ruta interna | `RelationDoc`, `RuleDoc` y los diagramas no se exportan |
| `sldb.mcp.*` | transporte | punto de extensión documentado: `build_server(..., extensions=[...])` y `--extension` |

## 4. Firmas de los nombres más usados

«Usos» suma imports y accesos `api.X` en todos los consumidores; mide referencias, no llamadas
en ejecución. C = cuántos consumidores lo usan.

| Nombre | Usos / C | Definido en | Firma | Qué hace |
|---|---|---|---|---|
| `extract_model_data` | 41 / 3 | `runtime/validation.py:65` | `(m: type[StructuredNLDoc], md: str) -> dict` | parsea Markdown contra el template del modelo y devuelve el payload validado |
| `render_model_markdown` | 34 / 3 | `runtime/validation.py:68` | `(m, d: dict, ref_root: Path\|None=None) -> str` | renderiza un payload a Markdown |
| `StructuredNLDoc` | 26 / 5 | `models/structured_doc.py:9` | `class (BaseModel)` | clase base de todo modelo de documento |
| `doc_status` | 22 / 2 | `api/stores/doc_status.py:18` | `(store, ref: str) -> DocRefStatus` | resuelve una referencia a `ok`, `missing`, `ambiguous`, `link-missing` o `link-broken` |
| `main` | 19 / 3 | `cli/main.py:11` | `(argv=None) -> int` | corre el CLI en proceso |
| `add_model` | 17 / 5 | `api/model_registry/add_model.py:18` | `(store, model_ref, pythonpath=None, canonical=False, actor=None) -> ModelRegistration` | registra una clase de modelo |
| `track_document_file` | 13 / 3 | `api/documents/track_document_file.py:22` | `(store, model_name, path, name=None, pythonpath=None, force=False, actor=None) -> DocumentReference` | indexa un archivo Markdown como documento de un modelo |
| `load_spec` | 13 / 1 | `api/notations/spec_models.py:12` | `(path) -> BaseDiagram` | carga un spec de diagrama YAML |
| `init_store` | 12 / 4 | `api/stores/init_store.py:15` | `(path='.', force=False) -> StoreLocation` | crea `<path>/.sldb` y lo registra en el catálogo global |
| `load_store_index` | 11 / 2 | `store/io/__init__.py:79` | `(store_path: Path) -> StoreIndex` | lee `core/store_index.yaml` |
| `load_runtime_documents` | 11 / 5 | `api/documents/runtime_documents.py:17` | `(store_path, pythonpath=None, include_linked=False) -> list` | devuelve todos los documentos trackeados, extraídos |
| `resolve_model_ref` | 10 / 3 | `api/model_registry/model_reference.py:67` | `(model_ref: str, pythonpath=None) -> type[StructuredNLDoc]` | importa la clase que nombra `módulo:Clase` |
| `SLDBStoreError` | 10 / 2 | `core/exceptions` | excepción | error a nivel de store |
| `create_document` | 9 / 3 | `api/documents/create_document.py:25` | `(store, model_name, path, payload, name=None, pythonpath=None, actor=None) -> DocumentReference` | renderiza un payload a archivo y lo trackea |
| `validate_spec` | 9 / 1 | `api/notations/spec_models.py:17` | `(model: BaseDiagram) -> None` | validación semántica de un spec |
| `load_models_index`, `load_documents_index` | 8 / 2 cada uno | `store/io/__init__.py:85`, `:91` | `(path: Path) -> ModelsIndex \| DocumentsIndex` | leen los índices YAML de un modelo |
| `open_store` | 8 / 5 | `api/stores/open_store.py:16` | `(store=None, mode='default') -> StoreLocation` | resuelve un store y abre una operación |
| `rebuild_edges` | 8 / 3 | `api/edges/rebuild_edges.py:18` | `(store, pythonpath=None, wait=False) -> EdgeRebuildReport` | reconstruye lo que cambió del índice de aristas |
| `transaction` | 7 / 1 | `api/documents/transaction.py:25` | `(store=None, pythonpath=None) -> Iterator[Transaction]` | escrituras todo o nada; las anidadas se unen a la abierta |
| `where_matches` | 7 / 3 | `api/where.py:23` | `(expression, payload, subject=None) -> bool` | evalúa un predicado `--where` |
| `init_relations` | 7 / 5 | `api/edges/init_relations.py:15` | `(store, pythonpath=None) -> RelationsInitReport` | registra `RelationTypeDoc` y `RelationDoc` y los tipos incorporados |
| `is_model_registered` | 7 / 3 | `api/model_registry/registration_query.py:25` | `(store, model: str) -> bool` | consulta el registro por nombre o referencia |
| `load_edge_index` | 6 / 3 | `api/edges/edge_reading.py:14` | `(store, include_linked=True, exclude_tags=()) -> EdgeIndex` | arma el índice de aristas, con stores enlazados |
| `Seed` | 6 / 2 | `api/stores/seed.py` | modelo `{path, mode, text\|file\|document, track}` | un archivo semilla de una `StoreDeclaration` |
| `SLDBModelError` | 6 / 3 | `core/exceptions` | excepción | error de resolución o registro de modelo |
| `hash_model_contract` | 5 / 1 | `store/hashing.py:80` | `(model_type) -> str` | hash del contrato de un modelo |
| `doc_exists` | 5 / 2 | `api/stores/doc_status.py:36` | `(store, ref) -> bool` | la referencia nombra exactamente un documento |
| `link_store` | 5 / 2 | `api/stores/link_store.py:19` | `(store, other_store, name=None, replace=False) -> LinkedStore` | registra otro store como enlace |
| `validate_model_input_roundtrip` | 5 / 3 | `runtime/validation.py:66` | `(m, md: str) -> tuple[bool, dict]` | comprueba extraer → renderizar → extraer |
| `update_store_indexes` | 5 / 3 | `api/stores/update_store_indexes.py:17` | `(store, pythonpath=None, wait=False) -> StoreUpdateReport` | rehashea y reindexa lo que cambió |
| `project_root`, `store_exists` | 5 / 2 cada uno | `store/layout.py:11`, `:141` | `(store_path: Path) -> Path \| bool` | raíz del proyecto; si existe el store |
| `GraphSnapshot` | 5 / 2 | `store/graph/snapshot.py` | clase | snapshot nodo-arista |
| `save_document_payload` | 4 / 4 | `api/documents/payload_save.py:15` | `(store, model_name, document_name, payload, pythonpath=None, actor=None) -> DocumentReference` | reemplaza el contenido de un documento; corre las reglas `on_write` |
| `edges_from`, `edges_to` | 4 / 2 | `api/edges/edge_reading.py:45`, `:61` | `(store, export_id, relation=None, include_linked=True, exclude_tags=()) -> list[EdgeRecord]` | aristas salientes o entrantes de un nodo |
| `load_declaration`, `save_declaration` | 4 + 3 / 1 | `api/stores/store_declaration.py:70`, `:92` | `(store_path) -> StoreDeclaration`; `(store_path, declaration) -> Path` | leen y escriben `.sldb/store.yaml` |
| `store_links` | 4 / 2 | `api/stores/store_links.py:15` | `(store_path) -> list[dict]` | enlaces directos de un store |
| `get_store_context` | 4 / 2 | `cli/store_context.py:19` | `(store_arg, mode='default') -> tuple[Path, Path]` | devuelve `(store_path, project_root)` |
| `untrack_document` | 4 / 3 | `api/documents/untrack_document.py:29` | `(store, document, pythonpath=None, actor=None) -> DocumentReference` | deja de trackear y reconstruye derivados |
| `load_registered_model` | 4 / 3 | `api/model_registry/load_registered_model.py:14` | `(store_path, model_name, pythonpath=None) -> RegisteredModel` | la clase registrada y su entrada de índice |

Otros usados por más de un consumidor: `check_store`, `select_documents`, `scaffold_store`,
`run_view`, `check_edges`, `describe_model`, `diagnose_store`, `snapshot_load`,
`serialize_document`, `registered_model`, `new_operation`.

## 5. Uso del CLI por subprocess

Código de producción que **lee la salida**:

| Dónde | Comando | Qué lee |
|---|---|---|
| `deskops/deskops/cli/commands/doctor.py:147`, `cli/commands/desk.py:327`, `workspace.py:431-441` | `python -m sldb stores check --store <root>/.sldb --format json` | `models[].name`, `models[].documents[].{path,name,note}` (`note` en `ok`, `doc_invalid`, `missing`), `errors[].{code,subject,document,what}` y una regex sobre `what` |
| `deskops/deskops/bootstrap.py:187-347` (solo cuando sldb no se puede importar) | `models add`, `stores add`, `models inherit`, `edges init`, `stores update`, `stores init`, `models list --format json`, `stores list --format json` | `models[].name`, `stores[].name` |
| `UIs/sldb-ui/src/lib/cli/exec.ts` (sin tocar desde agosto) | `python -m sldb <cmd> … --format json` para `stores`, `models`, `docs`, `find`, `ast`, `sections`, `extract`, `render`, `validate` | `JSON.parse` |
| `IR/tests/models/test_blog_chain.py:63` | `sldb rules check --store .sldb --format json` | `findings[].code` |

Solo código de salida:

- **CI:** `sldb stores build` y `stores build --check` en `deskops/.github/workflows/tests.yml:38,44`,
  `spec2viz/.github/workflows/tests.yml:53,56` y `UIs/graph_ui/.github/workflows/ci.yml:53-56,123-124`.
- **Scripts:** `deskops/scripts/migrate_desk_stores.py:212,242`, `deskops/scripts/ci-knowledge-check.sh:18`,
  `spec2viz/tools/suite-check.sh:244,357`, `pron/Makefile:30`.
- **Tests de IR** (`IR/tests/conftest.py:55-83`): `stores add --replace`, `stores update`,
  `edges rebuild`, `stores check`, `notations sync`.

Procesos largos:

- `python -m sldb serve` desde `UIs/graph_ui/frontends/mindmap/deskops_routes.py:55` (consume
  `GET /views/<name>` con ETag) y `UIs/kbsurfaces/cli.py:346-352` (consulta `GET /health`).
- `python -m sldb mcp --extension specir.mcp_extension:register` desde `IR/tests/conftest.py:137`.

## 6. Acceso directo a `.sldb/`

| Dónde | Acceso | Qué |
|---|---|---|
| `deskops/scripts/migrate_desk_stores.py:143-195` | lee YAML crudo | `core/store_index.yaml` (`models[].{name,model_ref,models_index}`), `core/models/<Name>.yaml` (`contract_hash`) |
| `UIs/kbsurfaces/discovery.py:32-74` | lee YAML crudo | `core/store_index.yaml`; cuenta archivos en `core/documents/` |
| `deskops/deskops/doc_access.py:242` | stat | mtime y tamaño de `core/store_index.yaml` como firma de caché |
| `deskops/deskops/graph/export.py:87-97`, `graph/self_reflection.py:122-147` | **escribe** | `.sldb/runtime/graphs/deskops.kg.json`, `.sldb/runtime/self_reflection_{findings,decisions}.json` |
| `UIs/graph_ui/frontends/mindmap/serve.py:39,50` | **escribe** | `<store>/runtime/mindmap-view.json` |
| `spec2viz/spec2viz/deskops.py:723` | lee | `.sldb/runtime/knowledge_graph.kg.json` |
| `deskops/deskops/services/closure.py:695-698` | git | `git add -A -- .sldb`: commitea el store |
| `deskops/deskops/cli/commands/desk.py:51-100`, `bootstrap.py:306-340`; `pron/src/pron/world/storage/*`, `world/fingerprint.py:62-76` | lee por `sldb.store.io` | recorren `models_index` → `documents_index`; leen `contract_hash`, `hash_b`, `base_models` |
| tests de pron y graph_ui | **borran** | shards de aristas (`edges_shards_root`, `edges_shards_dir`) |
| varios (deskops, pron, spec2viz, graph_ui) | existencia | `.sldb`, `.sldb/core/`, `core/store_index.yaml` como marcador de store |

`store.yaml` es además un formato de datos commiteado en deskops, spec2viz y graph_ui, con las
claves `version`, `links`, `models`, `predicates`, `generate`, `derived`, `include`,
`graph_backend` y `policy`.

## 7. Acoplamiento a modelos

Subclases de `StructuredNLDoc` fuera de tests:

| Consumidor | Directas | Con indirectas | Dónde |
|---|---|---|---|
| deskops | 13 | 26 | `deskops/deskops/models/*.py` |
| pron | 7 | 7 | `pron/src/pron/models/*.py` |
| spec2viz | 7 | 7 | `spec2viz/spec2viz/sldb_models.py:20-348` |
| IR | 1 | 10 | `IR/src/specir/models/domain.py:74` |
| graph_ui | 0 | 0 | |

- **Atributos de clase que fijan los modelos:** `__template__`, `__semantics__`, `__references__`,
  `__compositions__`, `__containment__`, `__doc_anchors__`, `__authority__` (los siete que
  declara la base en `models/structured_doc.py:10-16`), más `__family__`, `__contract__` y
  `__reference_models__`, que sldb también lee. `__artifact__` es solo de deskops. Los modelos
  usan además `@field_validator`, `@model_validator` y `@model_serializer` de pydantic.
- **Registro:** por referencia `módulo:Clase`. `add_model` importa la clase con
  `resolve_model_ref` y queda en `store.yaml` como `{name, model_ref, path}`, o
  `{name, model_ref, origin: <link>}` si se hereda de un store enlazado (`inherit_model`).
  El registro se comprueba contra `hash_model_contract`, que deskops también llama directo.
- **Modelos de sldb que los consumidores nombran:** `RelationDoc`, `RelationTypeDoc`, `RuleDoc`,
  `ViewDoc` (por referencia en `store.yaml`) y las clases de `notation.diagram_specs` (IR).
- spec2viz define clases propias con los mismos nombres que exporta `sldb.api`
  (`SpecDoc`, `SpecNodeDoc`, `NotationDoc`, `GlyphDoc`, `LinkDoc`, `PaletteDoc`, `ViewDoc`).

## 8. Contratos y costuras que ya existen en sldb

| Qué | Dónde | Estado |
|---|---|---|
| Superficie pública declarada desde lo que importan los consumidores | `sldb/contracts/identity.toml:288-389` | **desactualizada**: no lista pron, graph_ui ni IR |
| Chequeo de superficie | `sldb/tools/check_surface.py` (en `tools/check.sh:53`) | compara imports de consumidores con `identity.toml` |
| Contrato de capas | `sldb/contracts/layers.toml` | lo comprueba `tools/check_layers.py` |
| Contratos de datos | `sldb/contracts/integration.contract.yaml`, `contracts/schemas/*.schema.json` | payload de documento y export semántico, v1 |
| Envelope JSON del CLI | `sldb/docs/cli-envelope.md` | no se abrió |
| Protocolo `GraphBackend` | `store/graph/backend/protocol.py:14` | costura real, pero solo para análisis de grafo y predicados; único backend admitido: `networkx` |
| Protocolo `StoreCodec` | `store/codec.py:8` | costura texto → payload; la usan los fixtures de paridad de kimun |
| Punto de extensión MCP | `sldb.mcp.server.build_server(..., extensions=[...])` | lo usa IR |
| Propuesta de store genérico | `sldb/docs/architecture/store-extraction-proposal.md` | borrador; no hay protocolo `Document` en código |
| Paquete de kernel o interfaz de backend de persistencia | — | **no existe**: nada abstrae índices YAML, shards ni caché |

## 9. Lo que el contrato no puede dejar fuera

49 nombres los usan dos o más consumidores. Con 5 consumidores: `StructuredNLDoc`, `add_model`,
`init_relations`, `load_runtime_documents`, `open_store`. Con 4: `init_store`,
`save_document_payload`. Con 3: `extract_model_data`, `render_model_markdown`,
`validate_model_input_roundtrip`, `main`, `track_document_file`, `untrack_document`,
`resolve_model_ref`, `create_document`, `rebuild_edges`, `where_matches`, `is_model_registered`,
`load_edge_index`, `load_registered_model`, `update_store_indexes`, `SLDBModelError`, `ViewDoc`.

De esos 49, 15 están fuera de todo `__all__` público: `extract_model_data`,
`render_model_markdown`, `validate_model_input_roundtrip`, `project_root`, `store_exists`,
`get_store_context`, `RelationDoc`, `new_operation`, `snapshot_load`, `GraphSnapshot`, `main`,
`SLDBError`, `SLDBStoreError`, `SLDBModelError` y `diagnose_store`.

Preguntas que `RS2` tiene que responder [I]:

1. **Modelos.** Los consumidores definen 50 modelos (28 subclases directas) como clases pydantic con validadores
   propios. O el contrato sigue aceptando clases Python como fuente del modelo, o hay un camino
   de traducción a descriptores con equivalencia comprobada.
2. **Extraer y renderizar en proceso.** `extract_model_data` y `render_model_markdown` suman 75
   referencias y son lo más usado. Por subprocess no sirven para ese volumen de llamadas.
3. **Lecturas del layout.** Qué reemplaza a `load_store_index`, `load_models_index` y
   `load_documents_index`, y a las lecturas crudas de `core/store_index.yaml`.
4. **Escrituras de consumidores dentro del store** (`.sldb/runtime/*.json`): dónde viven.
5. **Salida de `stores check --format json`:** deskops lee claves concretas en tres lugares.
6. **Errores:** la jerarquía `SLDBError` es parte de la superficie aunque sldb no la exporte.
7. **Transacciones y `actor`:** `transaction` y el parámetro `actor` de las escrituras.
8. **Store commiteado:** deskops hace `git add -- .sldb`; en kimun el store va con `:git :ignore`.
