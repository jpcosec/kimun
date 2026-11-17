# 04 - La capa de grafo de sldb (el antiguo "kgdb" absorbido)

Fuente leída: `/home/jp/proyectos/SLDB_1/sldb` (solo lectura). Rutas `src/...` y `tests/...` son relativas a esa raíz; `src/sldb/` se abrevia `S/`. Ejemplos reales sacados de `/home/jp/proyectos/SLDB_1/deskops/.sldb_cache/edges/` y `deskops/desk/lifecycle/`.
Marcas: [V] verificado en el archivo citado; [I] inferencia mía.
"Reciente" = commits desde 2026-10-01 (`git log --since=2026-10-01`). Casi toda el área lo es (627 commits del repo en ese lapso); anoto la fecha del commit relevante cuando la conozco.

---

## 1. Para qué sirve esta área

Convierte los documentos Markdown estructurados de un store en un grafo tipado (nodos + aristas dirigidas) que se mantiene por documento ("shards" en `.sldb_cache/edges/`), se compone al leer en un `EdgeIndex` en memoria (más una copia JSON en disco) y se consulta: una vuelta (`edges_from/to`), recorridos relación-parametrizados, consulta estructurada (filtros por facet + alcance + relaciones), vecindad, análisis de grafo completo (caminos, ciclos, orden, componentes, centralidad, similitud) sobre networkx, y snapshots portables en node-link JSON. Además hace de máquina de estados: un `RelationDoc` es una arista autorada con `condition` y `attributes.sets/terminal`; `edges_holding` dice qué transiciones valen para un sujeto y `apply_effects` calcula el payload resultante. Encima de eso hay (a) un "project graph" configurado por datos (documentos y archivos del proyecto como nodos, menciones en prosa como aristas) y (b) `referrers` / `rewrite_references` (referencias entrantes y reescritura atómica). El grafo NO tiene verdad propia: es proyección derivada de los documentos [V: `docs/architecture/sldb-absorbs-kgdb.md`, `S/store/edge_index/__init__.py:1-8`].

---

## 2. Tabla de tipos de NODO

Forma común: `EdgeNodeRecord{id, node_type, semantics, facets, payload, field_hashes, source_hash, contract_signature}` [V: `S/store/models/edge_node_record.py:13-23`]. Ids: `sldb://<kind>/...` [V: `S/store/edge_index/node_ids.py:18-50`]. "Export id" de un documento = `Modelo:nombre` (o `store:Modelo:nombre` si es de un store enlazado).

| node_type | Qué representa | Cómo se forma el id (ejemplo real) | Campos que lleva | Quién lo crea |
|---|---|---|---|---|
| `<NombreDelModelo>` (p. ej. `ReviewDoc`, `RitualDoc`, `TaskCodeDoc`) | un documento trackeado "plano" | `sldb://document/<Model>:<name>` -> `sldb://document/RitualDoc:ritual-execution`. Enlazado: `sldb://document/<store>:Model:name` | `semantics`: `export_id, name, model, path, semantic_tags[], hash_c, hash_d`. Campos de primer nivel de proyección: `payload` (escalares y listas de escalares de nivel superior, o exactamente `__indexed__`), `field_hashes` (mapa completo de hashes por campo), `source_hash` (= hash_c), `contract_signature` (sha256 de nombres de campos + `__references__`/`__containment__`/`__compositions__`). `facets.source` = `{generated_from, store_path, hash_a}` (se sella al componer, no se persiste) | `doc_structure._document_node` `S/store/edge_index/doc_structure.py:23`; proyección `payload_projection.with_projection` `:29` [V] |
| `sldb_section` | una sección (encabezado) de un documento | `sldb://section/<exportid>#<ruta-de-seccion>` -> `sldb://section/ReviewDoc:review-...-bundle-1#bundle-review-of-task-cc35a-.../summary` | `semantics`: `export_id` (`doc#path`), `document_id`, `path, title, breadcrumbs[], about[], semantic_tags[], slug, level, line_start, line_end` (volcado de `SectionContextRecord`) | `doc_structure._section_node` `:28`; las secciones vienen del shard de sections [V `S/store/edge_doc_contribution`/`edge_rebuild._doc_edges` carga `load_sections_shard`] |
| `sldb_model` | un modelo registrado | `sldb://model/<Nombre>` -> `sldb://model/AtomDoc` | `semantics`: `name, model_ref, path, version, canonical, family, semantic_tags, base_models`. `roster_hash` se deja fuera a propósito | `model_contribution._model_node` `S/store/edge_index/model_contribution.py:46` [V] |
| `sldb_field` | un campo de un modelo | `sldb://field/<Modelo>.<campo>` -> `sldb://field/AtomDoc.id` | `semantics` = `describe_field(...)` sin `annotation`/`description` + `model` + `description` -> real: `{name:'id', kind:'string', required:True, model:'AtomDoc', description:...}`. Solo se crea si la clase del modelo se pudo importar | `model_contribution._field_node` `:51` [V] |
| `semantic_tag` | una etiqueta semántica | `sldb://semantic_tag/<tag>` -> `sldb://semantic_tag/type.relation.instance` | `semantics: {tag}`. Se crea para todo tag que cualquier documento, sección, modelo o entrada del DAG lleve | `records.tag_nodes` `S/store/edge_index/records.py:16` [V] |
| `relation_type` | un tipo de relación (un `RelationTypeDoc`); REEMPLAZA al nodo documento | `sldb://relation_type/<name del payload>` -> `sldb://relation_type/task_activates` | `semantics` = payload completo del RelationTypeDoc (`title,name,direction,cardinality,axis,source_types,target_types,condition,description`) + `doc` (nombre del documento, p. ej. `type-task_activates`) | `doc_kinds._relation_type` `S/store/edge_index/doc_kinds.py:34` [V] |
| `anchor` | una "palabra alias" (documento con tag `type.knowledge.anchor`, payload con `symbol` y `ref`) | `sldb://anchor/<symbol>` | `semantics` = payload + `doc`. REEMPLAZA al nodo documento | `doc_kinds._anchor` `:51` [V]. No hay anclas en el store de deskops [V: no aparecen en `.sldb_cache/edges`]; solo la lógica leída en código/tests (`tests/store/test_edge_anchor_targets.py`) |
| `sldb_store` | el store y cada store enlazado | `sldb://store` (el propio) y `sldb://store/<alias>` (enlazado, p. ej. `sldb://store/pron`) | sin semantics (el nodo no lleva path ni hash para que el shard sea portable) | `store_contribution.build_store_edges` `S/store/edge_index/store_contribution.py:11` [V] |

Cosas que NO son nodos del índice [V]:
- Un `RelationDoc` NO es nodo: produce una arista y nada más (`doc_kinds._relation` `:41`; `RelationDoc` docstring "It is not itself a node of the index" `S/models/relation_doc.py:18`).
- No existen nodos de "archivo", "mención", "link" `[[x]]`, ni de "composición" en el índice de aristas. Archivos y menciones solo existen en el project graph (sección 5, F-PG*), con sus propios ids `kind:identity`. Un `grep` de `sldb://file` / `sldb://link` en `S/store` y `S/api` no encontró nada [V].

Nodos del project graph (grafo aparte, no son `EdgeNodeRecord`) [V: `S/api/edges/project_graph/`]:

| Clase | Qué es | Id | Campos |
|---|---|---|---|
| `DocumentNode` (`document_node.py:12`) | un archivo de una "surface" | `kind:identity`, identity según `surface.identity`: `path` \| `document_id` (cae a path) \| `document_id_or_slug` (id, o slug del título, o stem). Ej. deskops: `task:task-xxx`, `atom:atom-yyy`, `doc:docs/...md` | `id, kind, identity, path, label, provenance{path, source_kind, extractor}, document_id` |
| `FileNode` (`file_node.py:12`) | archivo de código/test/config/spec clasificado por regla o declarado | `kind:path` -> `source_file:deskops/lifecycle.py` | `id, kind, identity(=path), path, file_kind, provenance` (`source_kind` = `path_rule` o el de la declaración, p. ej. `declared_materialization_target`) |

Detalle importante de nomenclatura: en el índice de aristas el `node_type` de un documento es el NOMBRE DEL MODELO (`RitualDoc`), pero la ruta alternativa del snapshot por "semantic export" (`ingest_sldb`) etiqueta todos los documentos `sldb_document` (`S/store/graph/ingest_sldb_nodes.py:37`) [V]. Ver sección 7.

---

## 3. Tabla de tipos de ARISTA

`EdgeRecord{source, target, relation, metadata, effects?, findings?}` [V: `S/store/models/edge_record.py:10-29`]. `metadata.origin` ∈ `structural | schema | alias | relation_doc` (y, en el project graph, `schema | mention`... ver nota). Las construye `records.edge(source,target,relation,origin,**meta)` [V `records.py:11`].

Los 15 tipos "builtin" (tabla `_TABLE` `S/models/builtin_relation_types.py:15-31`) con eje/cardinalidad/tipos [V]:

| relation | origin | source -> target | Qué en el documento/modelo la produce | metadata | Ejemplo real |
|---|---|---|---|---|---|
| `has_model` (WHAT, many_to_many) | structural | `sldb://store` -> `sldb://model/X` | cada modelo registrado en el store (`store_contribution.py:24`) | `origin_store` (alias del store dueño) si el modelo es heredado de un store enlazado (`:36`) | `sldb://store -> sldb://model/AtomDoc` |
| `links_to` (WHERE, m:m) | structural | `sldb://store` -> `sldb://store/<alias>` | `store_index.stores` (`stores add`) | - | `sldb://store -> sldb://store/pron` |
| `has_document` (WHAT, one_to_many) | structural | `sldb://model/X` -> `sldb://document/X:name` | cada documento trackeado (`doc_structure.py:15`) | - | `sldb://model/ReviewDoc -> sldb://document/ReviewDoc:review-...-bundle-1` |
| `has_section` (WHERE, one_to_many) | structural | documento -> sección | cada sección del shard de sections (`doc_structure.py:16`) | - | doc -> `sldb://section/...#...` |
| `tagged_as` (WHAT, m:m) | structural | modelo / documento / sección -> `semantic_tag` | `doc.semantic_tags`, `section.semantic_tags`, `model.semantics` (`records.tagged_as`) | - | doc -> `sldb://semantic_tag/type.workflow.review` |
| `semantic_parent` (WHAT, m:m) | structural | tag -> tag padre | DAG semántico (`dag.nodes[].parents`) (`store_contribution.py:27`) | - | (en `.sldb_cache/edges.yaml`) |
| `semantic_equivalent` (WHAT, m:m) | structural | tag local -> tag global | `dag.equivalences` (`:29`) | - | idem |
| `has_field` (WHAT, one_to_many) | schema | modelo -> `sldb_field` | `model_type.model_fields` (`model_contribution.py:31`) | - | `sldb://model/AtomDoc -> sldb://field/AtomDoc.id` |
| `extends` (WHAT, m:m) | schema | modelo -> modelo base | `m_idx.base_models` (herencia); si el índice no los trae, `base_names(model_type)` (`:36-43`) | - | `sldb://model/BoardDoc -> sldb://model/OperationalArtifactDoc` |
| `applies_to_source` / `applies_to_target` (WHAT, m:m) | schema | `relation_type` -> modelo | `source_types` / `target_types` del RelationTypeDoc (`doc_kinds.py:37`) | - | `sldb://relation_type/task_activates -> sldb://model/RitualDoc` |
| `names` (WHAT, m:m) | alias | `anchor` -> modelo / campo / relation_type / documento | `ref` del anchor (`anchor_targets`) | - | (no hay ejemplo en deskops) |
| `references` (WHAT, m:m) | schema | documento -> documento | (a) un campo de `__references__` / `__reference_models__` del modelo; (b) un campo de `__doc_anchors__` (`doc:Modelo:nombre[@sha256:...]`) | `field`, `declared` (valor tal como se escribió, con hash si es ancla), `allowed_models` (solo (a) si el modelo restringe) | `ReviewDoc:review-task-add-task-... -[references {field:'subject', declared:'doc:TaskCodeDoc:task-add-task-...'}]-> TaskCodeDoc:task-add-task-...` |
| `contains` (WHERE, one_to_many) | schema | documento -> documento hijo | un campo de `__containment__ = {campo: [Modelos]}` | `field`, `declared`, `allowed_models` | `BoardDoc:Board -[contains {field:'tasks', declared:'desk/tasks/task-...md', allowed_models:['TaskDoc']}]-> ...` |
| `composes` (WHAT, m:m) | schema | documento compositor -> documento componente | `__compositions__ = {campo_render: {source_field, model}}`; una arista por ref del `source_field` | `field` (campo compuesto), `source_field`, `ref_path` (ruta tal cual), `ref_index`, `composed_model` (si el spec lo da) | `BoardDoc:Board -[composes {field:'task_summaries', source_field:'tasks', ref_path:'desk/tasks/task-...md'}]-> TaskDoc:...` |
| `<cualquier verbo definido por el usuario>` | relation_doc | documento -> documento (extremos escritos como export id) | un `RelationDoc` (`source_id, target_id, relation_type`) | `relation_doc` (nombre del doc), `condition` (propia, o la del tipo), `axis` (del tipo), `attributes` (si el RelationDoc los trae), `reverse: true` en la arista espejo de un tipo `undirected` | `RitualDoc:ritual-execution -[task_activates {relation_doc:'execution-task_activates', condition:'', axis:'', attributes:{sets:{status:active}, terminal:false}}]-> RitualDoc:ritual-testing` |

Notas [V salvo indicación]:
- Los 15 builtins existen también como `RelationTypeDoc` en el store tras `edges init` (`reltype-<name>`, carpeta `sldb/relation_types/`) (`builtin_relation_writes.py:22-45`).
- `references` y `contains` y `composes` se emiten como aristas con target provisional (`sldb://unresolved_ref/<valor>`, `sldb://unresolved_path/<ref>`) si el valor no es un export id; se resuelven al componer (sección 5, F-I12, F-I13). Ejemplo real en shard: `target: sldb://unresolved_ref/desk/tasks/task-deskops-status-...md`.
- Metadata `evidence` ({ref_hash, contract, ref?, actor | engine}) se sella al leer, a pedido, nunca en shard (F-A5).
- Metadata `from_store` (alias del store enlazado) se agrega a toda arista de un store enlazado (`federation.qualify`).
- NO hay aristas de "link" ni de "mention" en el índice (los `[[x]]` los maneja `S/links/`, fuera de mi área). Las menciones en prosa son aristas del project graph (`ProjectEdge`, role/locator), distintas de las del índice. El docstring de `Referrer` habla de `origin: mention` pero ese origin es del `Referrer`, no una `EdgeRecord`.
- Aristas de documentos especiales (RelationType/Relation/Anchor) NO leen `__references__` (se saltan: `doc_contribution._declared_edges` `:57`) para no duplicar la arista autorada.

Aristas del project graph (`ProjectEdge`, `project_edge.py:10`): `source_id, target_id, role, source_kind('declared'), confidence('high'), provenance_path, provenance_locator ('frontmatter' | 'line:N:<label>' | 'line:N:yaml'), extractor, record(bool|None)`; clave de deduplicación `(source_id, target_id, role, locator)` [V].

---

## 4. Modelo de datos exacto

### 4.1 Disco (`.sldb_cache/`, no versionado) [V salvo nota]

```
.sldb_cache/
  edges.yaml                       # shard del store (EdgeContribution)
  edges/<Model>.yaml               # shard del modelo (ModelEdges)
  edges/<Model>/<doc>.yaml         # shard de documento (DocEdges)
  composed/<hash16>.json           # índice compuesto persistido (composed_disk)
  sections/ semantic/ semantic_dag.yaml graphs/ locks/ rules/   # otros índices (fuera de mi área)
```
Rutas: `S/store/layout.py:99-119` (`edges_store_shard_path`, `edges_shards_root`, `edges_model_shard_path`, `edges_shard_path`). Nota: docs antiguos y tests hablan de `.sldb/runtime/edges/`; el layout vigente usa `.sldb_cache` [V: existe `deskops/.sldb_cache/edges`; `.sldb/` solo tiene `core/` y `store.yaml`].

Campos de cada shard:

- **EdgeContribution** (shard del store, `edges.yaml`): `edges_version: str`, `nodes: [EdgeNodeRecord]`, `edges: [EdgeRecord]` [V `models/edge_contribution.py:11-16`].
- **ModelEdges** (hereda EdgeContribution): `+ model_name: str`, `projected_fields: [str]` (campos que un filtro `payload` puede nombrar: `__indexed__`, o todo campo cuyo tipo es escalar/lista de escalares/`Any`; `[]` si el modelo no se pudo cargar) [V `model_edges.py:10-14`, `model_contract.projected_fields`]. Real: `AtomDoc.yaml -> projected_fields: ['answer','five_wh_one_plus','id','provenance','tags','title']`.
- **DocEdges** (hereda EdgeContribution): `+ doc_name, model_name, source_hash, content_hash, contract_hash, semantic_tags[], source_stat: [mtime_ns, size] | None` [V `doc_edges.py:10-20`]. Real (`RelationDoc/execution-task_activates.yaml`): `edges_version: 5|sections:2`, `nodes:` (solo 4 `semantic_tag`: el RelationDoc no aporta nodo documento), `edges:` (la arista `task_activates`), `doc_name, model_name, source_hash, content_hash, contract_hash, semantic_tags, source_stat`.
- `edges_version` real = `"5|sections:2"` = `EDGE_INDEX_VERSION|sections:SECTION_INDEX_VERSION` (`cache_keys.edge_shard_version` `:10-13`) [V].
- `EdgeNodeRecord` serializa omitiendo `payload, field_hashes, source_hash, contract_signature` cuando vacíos (`model_serializer`, `edge_node_record.py:25-32`); `facets` se declara "filled on read, not persisted" (aunque el shard real muestra `facets: {}`) [V].
- `EdgeRecord` omite `effects` y `findings` de todo volcado mientras sean `None` (`edge_record.py:21-29`); solo `edges_holding`/`edges_blocked` los llenan [V].

### 4.2 `EdgeIndex` (composición en memoria) [V `S/store/edge_index/edge_index.py:15-35`]

| Campo | Contenido |
|---|---|
| `nodes: dict[str, EdgeNodeRecord]` | "el mismo id contribuido dos veces es un nodo": gana el primer contribuyente (`compose._merge` `setdefault`) |
| `edges: list[EdgeRecord]` | con reglas cross-documento aplicadas (condición/eje heredados, reversa de tipos no dirigidos, aristas a nada descartadas) |
| `problems: list[str]` | aristas AUTORADAS cuyo extremo no es nodo; reportadas, nunca guardadas |
| `problem_details: list[EdgeProblem{kind, document, detail}]` | derivado de `problems`; único `kind` = `endpoint-not-a-node` (`edge_problem.py:11,25`) |
| `stale: list[str]` | export ids con shard faltante/otro hash/otra `EDGE_INDEX_VERSION`/archivo editado fuera de sldb/otra `contract_signature` del modelo |
| `payload_fields: dict[modelo, [campo]]` | de `ModelEdges.projected_fields` + `<campo>__anchor_state` derivados |
| `unresolved_references: list[UnresolvedReference{source, model, field, target, target_export_id, relation(references|contains), reason(missing_document|ambiguous|unknown_path), source_path}]` | `unresolved_reference.py:10` |
| privados `_out`, `_in` | mapas de adyacencia perezosos |

Métodos: `edges_from(node_id, relation=None)`, `edges_to(...)`, `node(id)`, `nodes_of_type(type)` (ordenado por id). Un id sin prefijo `sldb://` se lee como `sldb://document/<id>` (`as_node_id`) [V].

### 4.3 Snapshot portable [V `S/store/graph/snapshot.py`, `node.py`, `edge.py`, `identity.py`, `vocabulary.py`]

- `GraphSnapshot{version: str ("1.0"), created_at: datetime UTC, nodes: [KnowledgeNode], metadata: dict}`.
- `KnowledgeNode{identity: SystemIdentity{node_id, node_type}, edges: [Edge{target_id, relation_type, metadata}], semantics, ast, io_ports[], compliance, adr, test_map, git, source}` (cada facet es `FacetPayload` con `extra=allow`). `VocabularyTerm` = `str` con patrón `^[A-Za-z][A-Za-z0-9_.:-]*$`.
- Solo `semantics`, `git`, `source` los usa `convert.py`; `ast, io_ports, compliance, adr, test_map` jamás los llena el código de sldb (solo `graph_io._status` lee `compliance.status`). Herencia del kgdb [V grep: sin asignaciones fuera de `store/graph/node.py`].
- Archivo en disco = node-link de networkx: `{"directed":true,"multigraph":false,"graph":{},"nodes":[{"type","status","schema":<KnowledgeNode dump>,"id"}],"links":[{"relation","metadata","source","target"}]}` (`graph_io.py:27-34`). `load_graph` acepta también un snapshot nativo (`nodes` sin `links`/`edges`).
- Contrato de export semántico `sldb_kgdb_semantic_export` v1 (nombre congelado; `sldb_semantic_export` solo se lee): llaves `contract{name,version}, producer, store{root,store_path,hash_a,runtime_sources}, models[], documents[], sections[], semantic_dag{nodes,equivalences}` (`ingest_sldb.py:22-46`, `docs/architecture/semantic-export-boundary.md`).

---

## 5. Lista de features

Formato: **ID nombre** - qué hace. Dónde. Acceso. Lee/escribe. Test. Reciente.

### 5.A Construcción del índice (shards)

**F-I1 Contribución de documento plano** - Cada documento trackeado aporta su nodo (tipado por modelo), un nodo por sección, y las aristas `has_document`, `has_section`, `tagged_as` (documento y secciones). Los tags de documento y secciones además se vuelven nodos. `S/store/edge_index/doc_structure.py:10-30`, `doc_contribution.py:19-46`. Acceso: `rebuild_edges` / cualquier escritura de sldb (mantiene shards al día). Lee: entrada del documento + shard de sections + (para payload) el archivo `.md` vía `guarded_text`. Escribe: `edges/<Model>/<doc>.yaml`. Test: `tests/store/test_documents_shards.py`, `tests/api/test_api_edges.py` [I por nombres]. Reciente (shards por documento 2026-09-17; v5 2026-11-06).

**F-I2 Documentos especiales** - `RelationTypeDoc` -> nodo `relation_type` + aristas `applies_to_source/target`; `RelationDoc` -> una arista autorada; documento con tag `type.knowledge.anchor` y payload `{symbol, ref}` -> nodo `anchor` + aristas `names`. Un payload ilegible los deja como documento plano. `doc_kinds.py:16-56`. Test: `tests/test_relation_models.py`, `tests/store/test_edge_anchor_targets.py`. Reciente.

**F-I3 Aristas de referencia declaradas** - Por cada campo de `__references__` / `__reference_models__` ({campo: [Modelos permitidos]}) emite `references`; por cada campo de `__containment__` emite `contains` (si un campo está en ambos, gana contención). El campo puede tener un valor o lista; se hace strip de espacios y backticks. Solo emite si el valor es export id, o el campo restringe modelos, o es `contains`, o parece ruta (`/` o `.md`). `declared_references.py:38-76`. Lee: payload extraído. Test: `tests/api/test_declared_references.py`, `test_declared_references_family.py`. Reciente (2026-10-18; v2 del índice).

**F-I4 Resolución diferida de referencias** - Al componer: export id tal cual; ruta de un documento trackeado -> ese documento (se ignora para stores enlazados); id sin calificar -> `Modelo:id` para cada modelo permitido, luego submodelos (por `base_models` en nodos modelo): 1 coincidencia resuelve, varias = `ambiguous`, ninguna = `missing_document`. Lo que no resuelve NO es arista: va a `unresolved_references`. Un campo libre solo reporta export ids y rutas. `declared_resolution.py:30-93`. Acceso: `unresolved_references(store)`, CLI `sldb edges unresolved [--format json]`. Test: `tests/api/test_unresolved_references.py`. Reciente (2026-10-18, 2026-11-05).

**F-I5 Aristas de composición** - `__compositions__` = `{campo_render: {source_field, model?}}`; una arista `composes` por cada ref (ruta de proyecto) del `source_field`, con `ref_path`, `ref_index`, `field`, `source_field`, `composed_model`; se resuelve al componer comparando con la ruta (normalizada, sin `./`) de los documentos propios (los de stores enlazados no cuentan). Ref sin dueño: arista descartada (es `schema`). `composition_edges.py:28-64`, `composition_targets.py:21-54`. Habilita "radio de impacto" recursivo. Test: `tests/api/test_composition_edges.py`, `test_composition_ref_root.py`. Reciente (2026-10-01).

**F-I6 Aristas de ancla de documento (GO07)** - Para cada campo de `__doc_anchors__` (str o list[str]) con valor `doc:Modelo:nombre[@sha256:hash]`, una `references` con `field` y `declared` (con hash) hacia `doc:Modelo:nombre` sin sufijo. `doc_anchor_edges.py:19-33`. Test: `tests/api/test_doc_anchors.py`, `tests/api/test_anchors.py`. Reciente (2026-11-06).

**F-I7 Estado del ancla (`<campo>__anchor_state`)** - Derivado al componer (nunca en shard): `intact` (el hash de contrato visto = hash de contrato actual del destino), `changed`, `lost` (destino no trackeado o ancla sin hash). Se agrega al `payload` de copias del nodo documento (escalar para campo str, lista alineada por índice para list[str]) y a `payload_fields`. El hash de contrato sale de `field_hashes` filtrado por `__contract__` del modelo destino. `doc_anchor_states.py:44-113`; `contract_hash` en `S/store/graph/language/contract_hash.py`. Requiere `model_type_of`. Reciente (2026-11-06).

**F-I8 Contribución de modelo** - nodo modelo + nodos campo + `has_field`, `extends`, `tagged_as`; `projected_fields`. `model_contribution.py:12-56`, `S/store/edge_sync.py:28-41`. Reciente (herencia `base_models` 2026-11-05).

**F-I9 Contribución del store** - nodo `sldb_store`, `has_model` (con `origin_store`), `links_to`, y las aristas del DAG semántico (`semantic_parent`, `semantic_equivalent`). `store_contribution.py:11-42`; `refresh_store_edges` reescribe solo este shard tras cambiar el DAG (`S/store/edge_rebuild.py`). Reciente (2026-10-18).

**F-I10 Proyección del payload (GO04) y `__indexed__`** - El nodo documento lleva `payload` plano: campos de nivel superior con valor escalar o lista de escalares (vacía incluida); nada de dict/lista no escalar; nulos ausentes; fechas y enums como string. Si el modelo declara `__indexed__`, exactamente esos campos (validados al cargar el modelo: debe ser campo escalar o lista de escalares, si no `SLDBModelError`). `payload_projection.py:21-48`, `payload_types.py`, `model_contract.indexed_fields`. Reciente (2026-11-06).

**F-I11 Payload ilegible tolerado** - Si el modelo rechaza el texto (`SLDBError, ValueError, OSError`), el nodo se aporta sin proyección ni aristas declaradas y el rebuild sigue (`payload_projection.read_payload:40-48`); `stores check` lo nombra `store/doc-invalid`. Reciente (2026-11-11/16).

**F-I12 Reconstrucción incremental** - Solo se reescribe un shard cuando cambió `source_hash`, `content_hash`, tags, `contract_hash` del modelo, `edges_version` o `contract_signature`; modelo no recorrido si su cache key (`edge_cache_key`) no se movió; shards de documentos ya no trackeados se podan; shards de modelos retirados se borran. Con `stale` no vacío se compara todo (`full=True`). Devuelve `EdgeRebuildReport{models_walked, docs_written, docs_reused, docs_skipped_missing, models_unresolved}`. `S/store/edge_rebuild.py`, `S/store/edge_sync.py`, `S/store/edge_doc_contribution.py:20-64`, `api/edges/rebuild_edges.py:18-39`. Acceso: `sldb.api.rebuild_edges(store, pythonpath, wait)`, CLI `sldb edges rebuild`. Bajo `store_lock`; antes reconstruye semantic y sections. Reciente.

**F-I13 `EDGE_INDEX_VERSION`** - Hoy `"5"` (`node_ids.py:11`). 2: `references/contains`; 3: `composes`; 5: payload/field_hashes/source_hash/contract_signature en el nodo documento (4 no figura en comentario). Cambiarla deja stale todos los shards. El sello efectivo es `5|sections:2`. Reciente.

### 5.B Composición, lectura y frescura

**F-C1 `compose_edge_index(store_path, include_linked=True, exclude_tags=(), model_type_of=None)`** - Lee shards de cada store de la federación, qualifica ids de documentos/secciones ajenos con `<store>:`, une nodos (primer contribuyente gana, se sella `facets.source`), aplica `resolve_composition_edges` -> `resolve_edges_reporting` y devuelve `EdgeIndex`. `S/store/edge_index/compose.py:26-77`. Cacheado en memoria por clave `(store, linked, tags, model_type_of)` con firma de shards; single-flight por `compose_lock`. Reciente (2026-11-16: single-flight).

**F-C2 Índice compuesto en disco** - `<cache>/composed/<sha256[:16]>.json` con `{key, index, facts}`; la clave incluye `FORMAT`, `edge_shard_version`, linked, tags, firma de shards y estado de `documents/` de cada store y, con `model_type_of`, `contract_signature|__doc_anchors__|__contract__` de cada clase. Cualquier diferencia = miss. `hold_persistence`/`release_persistence` evitan escribir durante una transacción. `composed_disk.py:42-97`, `composed_reading.py:17-47`. Reciente (2026-11-14, H11). Test: sin test directo hallado por nombre [I].

**F-C3 Frescura viva (`stale`)** - En cada lectura se compara por archivo `lstat` vs `source_stat` del shard; solo se abre/hashea un archivo cuyo stat cambió; ruta fuera de la raíz o symlink = stale, nunca se abre (O_NOFOLLOW); firma de contrato distinta = stale. `live_stale.py:32-100`, `composed_reading.with_live_stale:43`. Test: `tests/store/test_live_stale_security.py`, `tests/store/test_stale_shard_hashes.py`. Reciente (2026-11-11).

**F-C4 `exclude_tags`** - Un documento con un tag excluido deja solo sus nodos de tag (`shard_reading._filtered:30`). El índice siempre guarda todo. Acceso: `--exclude-tag` (CLI), argumento en toda lectura.

**F-C5 Federación** - `federated_stores` recorre enlaces en anchura (cada store una vez); `qualify_id` pone `<alias>:` solo a `sldb://document/` y `sldb://section/` con un solo `:` en su id; modelos, campos, tags, relation_types y anchors son un vocabulario único; `sldb://store` -> `sldb://store/<alias>`. Aristas ajenas llevan `from_store`. `federation.py:15-55`. Reciente (2026-10-18).

**F-C6 Reglas cross-documento (`resolve.py`)** - (1) extremos que son export id de un RelationTypeDoc se mueven al nodo `relation_type`; (2) arista autorada hereda `condition` del tipo si la propia está vacía y siempre `axis` del tipo; (3) tipo `direction: undirected` agrega la arista inversa con `reverse: true`; (4) arista autorada con extremo no-nodo -> `problems` y se descarta; (5) arista `schema` o `alias` cuyo destino no es nodo se descarta en silencio. `resolve.py:32-74`.

**F-C7 `load_edge_index`, `edges_from`, `edges_to`, `edge_node`, `edge_nodes_of_type`** - Puerta única de lectura; abre el store en modo readonly, nunca reconstruye. `S/api/edges/edge_reading.py:14-73`. Acceso: `sldb.api.*`, CLI `sldb edges show NODE [--to] [--relation R] [--holds-for DOC] [--evidence] [--local] [--exclude-tag]`, HTTP `GET /edges`, `/edges/node`, `/edges/nodes`, MCP `neighbors`/`neighborhood` (`S/mcp/read/graph.py`). Reciente.

**F-C8 `document_edges(store, pythonpath, doc_id)`** - Hace `rebuild_edges` y devuelve solo las aristas documento->documento como `DocumentEdge{id, source, target, type, origin, relation_doc}` (id = nombre del RelationDoc o `relacion:src>tgt`). Para UIs. `api/edges/document_edges.py:15-41`.

**F-C9 Evidencia de arista (`stamp_evidence`)** - Opcional: agrega `metadata.evidence = {ref_hash (content_hash del documento destino / model_hash del modelo; para sección el del documento + `ref:'document'`), contract (contract_hash del modelo fuente), actor:'relation_doc:<n>' (autorada) | engine:{name:'sldb',version}}`. Sin timestamp; solo en lectura. `S/store/edge_index/edge_evidence.py:20-48`, `S/api/edges/edge_evidence.py:21-45`. CLI `edges show --evidence`. Reciente (2026-10-19).

### 5.C Validación

**F-V1 `check_edges(store, include_linked=True, exclude_tags=())`** - Devuelve `EdgeCheckReport{errors, stale, untracked_builtins, linked_errors, linked_stale}` (`.ok` = sin `errors` ni `stale`). `errors` = `index.problems` + violaciones propias + ciclos de `semantic_parent`/`extends`. Los de stores enlazados van aparte y no fallan. `S/api/edges/check_edges.py:14-31`. CLI `sldb edges check`.

**F-V2 `validate_edge_index`** - Por arista: tipo de relación conocido (RelationTypeDoc trackeado); extremos existen; clase de source/target dentro de `source_types`/`target_types` (herencia por `base_models`; `sldb_document` en la lista admite cualquier modelo; lista vacía = cualquiera); cardinalidad (`one_to_one|many_to_one` limita targets por source; `one_to_one|one_to_many` limita sources por target); toda `condition` debe parsear como predicado (GO08). En un store con `edges init`, las aristas derivadas (`structural|schema|alias`) se validan contra la definición que sldb trae si el store no trackea ese builtin; `untracked_builtins` lo informa. `S/store/edge_index/validation.py:28-117`. Test: `tests/store/test_upgrade_compat.py`, `tests/api/test_api_edges.py`. Reciente (2026-10-16 y 2026-11-06).

**F-V3 `cycle_errors`** - `semantic_parent` y `extends` deben ser acíclicos; usa `analysis.cycles(limit=1)` y emite "Relation 'X' has a cycle: a -> b -> a." `acyclic.py:17-23`. Test: `tests/store/test_graph_analysis.py`.

**F-V4 `init_relations(store, pythonpath)`** - Idempotente: registra `RelationTypeDoc` y `RelationDoc` (o los re-apunta si estaban bajo `kgdb.models`), escribe un RelationTypeDoc por builtin bajo `sldb/relation_types/<name>.md` (nombre de documento `reltype-<name>`), reescribe los builtins cuya spec (`cardinality, axis, description, source_types, target_types`) se movió, y registra cada nombre como predicado del store con su eje. Devuelve `RelationsInitReport{models_added, models_repointed, types_written, types_updated, predicates_added}`. `S/api/edges/init_relations.py:15-38`, `builtin_relation_writes.py`, `relation_models.py`. CLI `sldb edges init`. Deskops lo llama en bootstrap (`deskops/bootstrap.py:267`, `deskops/lifecycle.py:410`) [V].

### 5.D Recorrido, consulta y vecindad

**F-Q1 Primitivas de recorrido relación-parametrizadas** - `targets, sources, roots(node_type, relation), children, parent, descendants(depth), neighbors_via(out_relation, in_relation, exclude_prefixes, same_kind), exists(source,target,relation)` (+ `bare`, `kind`). `S/store/graph/traversal.py:13-91`; fachada `S/api/graph/traverse.py` (compone el índice con `include_linked=True` y `exclude_tags`). Sin CLI. Sin tests directos hallados [V: ningún test importa `traversal` ni `api.graph.traverse` ni `neighbors_via`]; el uso real era de pron/MCP [I por docstring].

**F-Q2 Consulta estructurada `StructuredQuery`** - `{filters: [FacetFilter{facet, where}], relations: [RelationFilter{relation_types[], direction both|outgoing|incoming}], scope: GraphScope{descendant_of, ancestor_of, node_id_prefix}}`. `execute_query(index, query) -> list[GraphNode]`: candidatos según scope (primer criterio presente, en ese orden: descendant_of, ancestor_of, node_id_prefix; sin scope todos, ordenados por id); un nodo pasa si cumple TODOS los filtros; el resultado lleva las aristas filtradas por `RelationFilter` (solo salientes si no hay filtro). `S/store/graph/executor.py:13-76`, `edge_filter.py`, `language/*.py`. Acceso: `sldb.api.graph.execute_query(store, query, include_linked, exclude_tags)`, CLI `sldb graph query --query-file Q.json`. `GraphNode{id, node_type, semantics, facets, edges}`. Test: `tests/store/test_graph_executor.py`, `tests/store/test_graph_language.py`.

**F-Q3 Filtros por facet** - `facet` ∈ `identity` ({node_id,node_type}), `semantics`, `payload` (proyección del documento), o cualquier clave de `node.facets` (`source`, `git`). `where` es un predicado del lenguaje de reglas SIN caminos (un cuantificador o `count` -> `SLDBGraphArgumentError`; texto que no parsea -> `WherePredicateError`; vacío = sin condición). Un filtro `payload` solo casa con nodos documento y nombra campos que algún modelo proyecta (si no, `SLDBGraphArgumentError` con el nombre del campo); campo ausente solo lo decide `has()`. `payload_match.py:26-78`, `facet_match.py`. [Handoff al lenguaje: `as_predicate`, `payload_holds`, `leaf_fields`, `walk` de `S/store/graph/language/predicate_*`; no lo cubro.]

**F-Q4 `graph_get(store, node_id)` / `graph_list(store)` / `query_snapshot(snapshot, query)`** - Un nodo con sus aristas salientes; todos los ids ordenados; la misma consulta sobre un `GraphSnapshot` en memoria sin store (vía `index_from_snapshot`). `S/api/graph/execute.py:18-37`. CLI `graph get NODE`, `graph list`.

**F-Q5 Vecindad** - `collect_neighborhood(index, seeds, depth)` (BFS ambos sentidos, incluye semillas) y `collect_neighborhood_by_direction(index, seeds, depth, direction incoming|outgoing|both)` (excluye semillas; cualquier otro valor se lee como `both`). Sin filtro de relación. `S/store/graph/neighborhood.py:8-39`, `S/api/graph/neighborhood.py`. CLI `graph neighborhood NODE --depth N --direction D`, HTTP `/graph/neighborhood`, MCP `neighborhood` (valida dirección y `depth>=1`). Test: `tests/store/test_graph_neighborhood.py`.

### 5.E Análisis (networkx), todos vía `GraphBackend.analyze(op, **args)`

Todas toman `store` + `include_linked=True`, `exclude_tags=()` (cualquier otro kwarg -> `SLDBGraphArgumentError("Unexpected arguments: ...")`) y `backend=None` (inyectable) [V `api/graph/analyze.py:93-98`]. `relations`: `None` = relaciones AUTORADAS (presentes menos las 15 derivadas), si no hay autoradas = todas las presentes; `"*"` = todas incluida la espina derivada; lista = esas. `S/store/graph/analysis/relations.py:34-44`. Vista: `MultiDiGraph` con `key=relation`, nodos con `node_type` y `semantics`; `collapsed` = `DiGraph` con `weight` = nº de aristas (`nx_graph.py`).

| ID | Operación | Parámetros y defecto | Devuelve | Detalle |
|---|---|---|---|---|
| F-A1 | `graph_path` (op `path`) | `source, target, relations=None, directed=False, explain=False` | `list[str]` o `None` | camino más corto; no dirigido por defecto. `analysis/paths.py:10` |
| F-A2 | `graph_paths` (op `paths`) | `source, target, relations, cutoff=None, limit=10, explain=False` | `list[list[str]]` | hasta `limit` caminos simples, más cortos primero; siempre no dirigido. `paths.py:24` |
| F-A3 | `explain=True` (ops `path_explained`, `paths_explained`) | idem | `{"path":[ids], "steps":[{source,target,edges:[{relation, direction forward|backward, provenance:{origin, relation_doc|field, from_store}}]}]}` | por cada tramo lista todas las aristas que lo unen, ordenadas por origen (relation_doc, schema, structural, alias, otro), luego relación; el espejo `reverse` no se lista aparte; excluye `evidence`. `analysis/path_steps.py`. CLI `graph path --explain`, HTTP `?explain=1`. **Reciente 2026-11-16** |
| F-A4 | `graph_cycles` (op `cycles`) | `relations, limit=10` | `list[list[str]]` | `simple_cycles` sobre grafo colapsado, más cortos primero. `cycles.py:15`; existe `is_acyclic` (no expuesto en backend) |
| F-A5 | `graph_order` (op `order`) | `relations` | `list[str]` | orden donde un nodo va después de lo que apunta; `SLDBGraphCycleError(ciclo)` si no es DAG. `order.py:12` |
| F-A6 | `graph_layers` (op `layers`) | `relations` | `list[list[str]]` | `topological_generations` del reverso, cada nivel ordenado. `order.py:23` |
| F-A7 | `graph_components` (op `components`) | `relations, node_types=None` | `list[list[str]]` | componentes débilmente conexas, mayor primero; `node_types` RESTRINGE EL GRAFO. `components.py:24` |
| F-A8 | `graph_islands` (op `islands`) | idem | todas menos la mayor | `components.py:31` |
| F-A9 | `graph_isolated` (op `isolated`) | `relations, node_types=None` | `list[str]` | nodos sin ninguna arista de las relaciones, medido sobre el grafo entero y DESPUÉS filtrado por tipo. `components.py:36` |
| F-A10 | `graph_central` (op `central`) | `kind` ∈ `pagerank|betweenness|degree|in_degree|out_degree` (def. `pagerank`), `relations, node_types=None, limit=20` | `list[(id, score)]` | desempate por id; `node_types` filtra la RESPUESTA; `pagerank` exige scipy (extra `graph`), si no `SLDBError` con el remedio; `kind` desconocido `ValueError`. `centrality.py:27` |
| F-A11 | `graph_similar` (op `similar`) | `node_id, relations=("tagged_as",), limit=10, node_types=None` | `list[(id, n_compartidos)]` | proyección bipartita: nodos que comparten más destinos; `[]` si el nodo no está. `similarity.py:18` |

CLI `sldb graph path|cycles|order [--layers]|components [--islands|--isolated]|central|similar` con `--relation R` (repetible), `--all-relations`, `--type T` (repetible), `--local`, `--exclude-tag`, `--store` [V `S/cli/parsers/graph_analysis.py`]. HTTP `/graph/path`, `/graph/cycles`, `/graph/components`, `/graph/central`, `/graph/isolated`, `/graph/neighborhood` [V `cli/serve/graph_routes.py:80-85`]. No hay `/graph/similar`, `/graph/order` ni `layers` por HTTP [V]. Tests: `tests/store/test_graph_analysis.py`, `tests/api/test_api_graph_analysis.py`, goldens `tests/fixtures/graph_goldens/*`. Recientes (analytics 2026-09-20, seam 2026-11-05, explain 2026-11-16).

**F-B1 `GraphBackend` (protocolo)** - Tres métodos: `load(index, projection=None)`, `eval(predicate, subject=None) -> [finding]`, `analyze(op, **args)`. `S/store/graph/backend/protocol.py:14-33`. El backend nunca expone su grafo interno; `compose` queda fuera. Única implementación: `NetworkXBackend` (`networkx_backend.py:39-88`) con 12 ops (`path, paths, path_explained, paths_explained, cycles, order, layers, components, islands, isolated, central, similar`); op desconocida -> `ValueError`; `analyze` antes de `load` -> `RuntimeError`. Selección: `store.yaml: graph_backend: networkx` (único admitido; otro valor -> `ValueError('graph_backend X no admitido; admitidos: ["networkx"]')`); `backend_for(store_path)` en `api/graph/analyze.py:88`, `make_backend` en `backend/__init__.py:17`. Test: `tests/store/test_graph_backend.py`. Reciente (2026-11-05). Deskops declara `graph_backend: networkx` [V `deskops/.sldb/store.yaml:600`].

**F-B2 `eval` del backend** - Evalúa un predicado del lenguaje sobre un sujeto (payload dict, o export id leído de la proyección cargada) o sobre todo el dominio (un finding por documento). `eval` es el punto de entrega al lenguaje. `index_view` (`backend/index_view.py:18-56`) arma la vista que el lenguaje consume: nodos = documentos por export id (sin prefijo), payload = proyección de `load` o la del shard, familia = modelo + todas sus bases (transitivas, de los nodos modelo), `out/in` = aristas del índice como `{source,type,target,field,declared,unavailable}`. Handoffs: `predicate_reach.reach_step` sigue `rel:<tipo>` (cualquier relación por nombre) o `campo` (solo aristas `references` con `field==campo`), `^` invierte, `+` clausura, y expone `anchor_state` desde `<campo>__anchor_state`. **`unavailable`** (extremo en un store enlazado no abrible -> elemento incompleto) lo lee la vista pero NINGÚN código del índice lo escribe en `metadata`; solo los tests lo inyectan (`tests/test_rules_language_spec.py:238`) [V].

### 5.F Snapshots portables

**F-S1 `snapshot_save(store, path, include_linked, exclude_tags)`** - Dos rutas: (a) por defecto, `export_semantic_payload` (índices fuente: store, modelos, documentos, secciones, DAG) -> `sldb_semantic_export_to_snapshot` -> `save_graph`; (b) la ruta vieja desde los shards (`snapshot_from_index(load_edge_index(...))`) cuando hay `exclude_tags` o stores enlazados (GAP G6/G5 anotados). `S/api/graph/snapshot.py:17-35`. CLI `sldb graph snapshot save --output F`. Test: `tests/store/test_graph_io.py`, `tests/api/test_graph_source_parity.py`, `tests/api/test_graph_native_lifecycle.py`.

**F-S2 `snapshot_load`/`load_graph`**, **`save_graph`** - node-link JSON <-> `GraphSnapshot` sin networkx. Un node-link sin `schema` por nodo se lee como `KnowledgeNode{identity{id, type or 'concept'}, edges:[]}`; la lectura de node-link descarta aristas (los `links` no se reingresan; las aristas viven dentro de `schema`) [V `graph_io.py:45-54`]. CLI `graph snapshot load --input F`.

**F-S3 `snapshot_from_index` / `index_from_snapshot`** - Conversión exacta índice<->snapshot (todas las aristas salientes de cada nodo; `semantics`, `git`, `source` como facets). `S/store/graph/convert.py:14-61`. `index_from_snapshot` copia aristas crudas, sin reglas cross-documento ni `payload_fields`.

**F-S4 `ingest_sldb` / `graph ingest-sldb`** - Convierte un payload `sldb_kgdb_semantic_export` v1 en snapshot. Crea: nodo store (+`has_model`), nodos tag (+`semantic_parent`, `semantic_equivalent`), modelos (+`has_document`, `tagged_as`), documentos (+`has_section`, `tagged_as`), secciones (+`tagged_as`). Valida `contract.name` y `version==1`, si no `ValueError("Expected sldb_kgdb_semantic_export version 1")`. `S/store/graph/ingest_sldb.py`, `ingest_sldb_nodes.py`. Test: `tests/store/test_semantic_export.py`, `tests/store/test_export_contract_frozen.py` (congela contrato, ids y claves).

### 5.G Transiciones y efectos (ver sección 6)

**F-T1 `edges_holding(store, export_id, subject, relation=None, include_linked=True, exclude_tags=(), document=None)`**. `S/api/edges/edges_holding.py:14-43`.
**F-T2 `edges_blocked(...)`** `S/api/edges/edges_blocked.py:13-21`.
**F-T3 `effects_of(attributes, subject)`** `S/api/edges/edge_effects.py:13-44`.
**F-T4 `apply_effects(subject, effects, model)`** `S/api/edges/apply_effects.py:12-26`.
**F-T5 Convención `attributes.sets/terminal`** validada en `RelationDoc` (`S/models/transition_attributes.py:16-40`, `S/models/relation_doc.py:74-78`).
**F-T6 `edges show --holds-for DOC`** (`cli/commands/edges.py:30-45`): lee el documento y filtra las aristas por su condición (no combina con `--to`).
Tests: `tests/api/test_edge_effects.py`, `tests/api/test_rules_end_to_end.py`, `tests/api/test_rules_evaluation.py`. Recientes (GO09 2026-11-06, `edges_holding` 2026-10-16).

### 5.H Referencias entrantes y reescritura

**F-R1 `referrers(store, target, pythonpath=None, *, graph=None)`** - Todas las referencias a un documento o a un nodo del project graph: primero campos declarados (`origin: schema`, de aristas del índice con `field` y `declared`, con posición en lista), luego menciones en prosa (`origin: mention`, `path:line`, del project graph). `target`: `nombre`, `Modelo/nombre`, `Modelo:nombre`, ruta trackeada, o id de nodo `kind:identity`; varios candidatos o ninguno -> `SLDBDocumentTargetError`. Una mención en una línea de frontmatter de un documento que ya es referrer `schema` se omite (misma referencia). Devuelve `[Referrer{source, field, index, location, origin}]`. `S/api/edges/referrers.py:55-118`, `rewrite_values.schema_hits:24`, `referrer_frontmatter.py`. CLI `sldb edges referrers TARGET`. `graph=` admite un grafo armado en proceso. Test: `tests/api/test_referrers.py`. Reciente (2026-11-06; `graph=`/excluir fuentes 2026-11-16).

**F-R2 `rewrite_references(store, target, replacement, *, transaction=None, dry_run=False, pythonpath=None, graph=None, excluded_sources=())`** - Reescribe (o quita si `replacement is None`) toda referencia entrante dentro de UNA transacción (todo o nada). Campo escalar: toma el valor nuevo (None lo deja nulo; lo valida el modelo); campo lista: reemplaza el elemento en el lugar o lo quita, sin duplicar; el valor nuevo conserva la forma del original (nombre, `Modelo:nombre`, `Modelo/nombre`, ruta, `doc:Modelo:nombre` sin hash para que el guardado lo vuelva a anclar); mención: reemplaza solo el texto capturado por un `MentionPattern` si es token completo (`[A-Za-z0-9_:./-]`, puntos finales de frase permitidos), nunca borra prosa (se reporta `skipped`). Todo payload y texto nuevo se valida en memoria (`validate_model_data_roundtrip`/`validate_model_input_roundtrip`); con `dry_run` solo eso, no se abre nada para escribir ni corren reglas `on_write`. Devuelve `RewriteReport{changed:[{source,field,index,location,before,after}], skipped:[{source,location,reason}]}`. Errores: `SLDBDocumentTargetError` (destino desconocido/ambiguo, reemplazo ambiguo, fuente excluida no trackeada), `SLDBValidationError` (nada se escribe). `S/api/edges/rewrite_references.py:30-101`, `rewrite_plan.py`, `rewrite_values.py`, `rewrite_mentions.py`. CLI `sldb edges rewrite TARGET --to NEW | --remove [--dry-run]`; HTTP `edges_rewrite_routes`. Test: `tests/api/test_rewrite_references.py`, `tests/api/test_rewrite_loss.py`. Reciente. Lo usa deskops (`deskops/services/references.py:117`) [V].

### 5.I Project graph (GD21; `sldb.api.project_graph(store)`)

**F-PG1 `ProjectGraphConfig` (datos puros, `from_mapping`/`to_mapping`)** - Pydantic congelado; se declara en `.sldb/store.yaml` bajo `project_graph` o se construye en proceso. Claves: `document_extractor, file_extractor, edge_extractor` (str); `surfaces: [DocumentSurface{kind, directory, pattern(glob), suffixes, excluded_names, excluded_parts, identity(path|document_id|document_id_or_slug), closed (predicado del lenguaje)}]`; `file_rules: [FileRule{kind, file_kind, path_prefixes, suffixes, hidden_name, stem_keywords}]` (la primera que casa gana; nodo `kind:path`); `exclusions: FileExclusions{directories, path_prefixes, suffixes, path_endings}`; `declared_file_targets: [DeclaredFileTargets{directory, pattern='*.md', field, kind, file_kind, source_kind}]`; `reference_fields: [ReferenceField{path(tuple), target_prefix, default_role, allowed_roles, scalar_only, index_owned}]`; `read_yaml_blocks=True`; `fields_by_kind`, `fields_by_model`, `kind_by_model`, `provenance_fields`; `section_mentions: [SectionMentions{headings, patterns[MentionPattern{regex, target(plantilla str.format con {0} y grupos con nombre)}], role, kinds}]`; `line_mentions: [LineMentions{kinds, excluded_path_prefixes, skipped_frontmatter_fields, patterns, role, locator}]`; `missing_target_kinds: [MissingTargetKind{parts, one_of_first, ending, kind}]`; `closed_document` (callable, DEPRECADO, no se carga ni se vuelca). `from_mapping` rechaza clave desconocida o valor que no es dato, citando la ruta (`project_graph.surfaces[0].closed_document: unknown key`). `S/api/edges/project_graph/project_graph_config.py:27-84`, `project_graph_config_mapping.py`. Test: `tests/api/test_project_graph_config.py`. Reciente (2026-11-05/06).

**F-PG2 Nodos de documento y archivo** - `extract_document_nodes(root, config)`: glob por surface, filtros de sufijo/nombre/parte; un archivo en dos surfaces = dos nodos; etiqueta = título (frontmatter `title`, o línea `# `, o stem humanizado); `document_id` = frontmatter `id` o línea `ID:`. `extract_file_nodes`: recorrido `os.walk` podado por exclusiones (no sigue symlinks de directorio) + reglas + destinos declarados (campo de frontmatter con una ruta existente, no documento ni ya clasificada). `document_nodes.py`, `file_nodes.py`, `project_file_walk.py`, `declared_target_nodes.py`, `document_metadata.py`.

**F-PG3 Declaraciones por documento** - Orden: campos de frontmatter por `reference_fields`; campos de modelo del `kind` (`fields_by_kind`; valores de `provenance_fields` solo enlazan mientras la fuente existe); bloques ```yaml``` fenced (un mapeo) leídos con `reference_fields` (locator `line:N:yaml`); menciones por sección (líneas bajo encabezado nivel 2-6 cuyo texto normalizado está en `headings`; locator `line:N:<heading>`); menciones por línea (cada línea salvo las `---` de frontmatter y los campos omitidos; locator `line:N:<locator>`). `index_owned`/`fields_by_model`: el índice de sldb es dueño de esos campos y no se releen del texto. Solo `.md` declaran. `document_declarations.py`, `text_declarations.py`, `mapping_declarations.py`, `frontmatter_mapping.py`.

**F-PG4 Resolución a aristas y objetivos faltantes** - `NodeLookup.reference_id` resuelve id, nombre sin kind, ruta, stem o `kind:id` (texto con espacios = prosa, no referencia); `target_id`: `kind:id` tal cual, ruta de nodo -> su id, o id "faltante" por `missing_target_kinds` (`kind:path`). Si el destino existe -> `ProjectEdge`, si no -> `MissingProjectTarget{source_id, target_id, provenance_path, provenance_locator, reason, extractor, record}` (razón fija "declared target was not found among extracted graph nodes", o "...not resolved by the sldb edge index (<reason>)" para lo que el índice dejó sin resolver). `document_edge_reader.py`, `node_lookup.py`, `store_sources.py`, `store_edges.py` (solo `references`/`contains` con `field` entre dos nodos del proyecto), `store_unresolved.py`.

**F-PG5 Historia/`record`** - Un documento es "historia" (aristas `record: true`) si su frontmatter dice `record: true` o su surface `closed` (predicado sobre el payload proyectado del índice, o su frontmatter si no está trackeado) se cumple. `project_texts.py`.

**F-PG6 API de lectura** - `ProjectGraph.nodes.{documents, files, lookup}`, `edges_from(node_id)`, `edges_to(node_id)` (mapa inverso único de toda la extracción, construido al primer uso), `missing_from(node_id)`, `extraction()` -> `ProjectGraphExtraction{edges, missing_targets}`, `node_ids()`. `project_graph.py:20-91`. Lectura por fuente (perezosa, deduplicada por clave).

**F-PG7 Memoización por proceso** - `project_graph(store)` guarda un grafo por store; se rehace solo si cambia la firma: firma de shards + objeto `nodes` del índice compuesto + `(path, mtime_ns, size)` de cada archivo leído + mtime de cada directorio recorrido. No camina el árbol al verificar; no persiste. `project_graph_memo.py:26-100`. Error si `store.yaml` no declara `project_graph` (`SLDBStoreError`). `store_project_graph.py:15-42`.

**F-PG8 CLI/HTTP** - `sldb graph project nodes [--kind K]`, `edges --from ID | --to ID`, `missing` (con `--store`, `--format text|json|yaml`); HTTP `GET /project-graph/nodes?kind=`, `/project-graph/edges?from=|to=`, `/project-graph/missing`. `S/cli/parsers/graph_project.py`, `docs/architecture/project-graph.md`. Test: `tests/api/test_project_graph.py`. Reciente.

Uso real: deskops NO declara `project_graph` en su `store.yaml`; lo arma en proceso (`deskops/graph/desk_graph.py`: 17 surfaces como `atom, task, issue, pill, role, run, doc, spec, board, ritual, step...`, 6 `FileRule` (`spec, test_file, config_file x3, source_file`), `FileExclusions`, `DeclaredFileTargets` de `desk/materializations/target_identity`, `ReferenceField` sobre campos de atom, `MentionPattern` `TASK_OR_ISSUE, ATOM, ATOM_ID, DRAWER_BULLET, SOURCE_PATH`, `SectionMentions` y `LineMentions`, `MissingTargetKind`) [V].

### 5.J Otros hallazgos de plomería
- **F-X1 Serialización**: `serialize_edge_record(s)`, `serialize_edge_node_record(s)` = `model_dump` (`api/edges/edge_serialization.py`).
- **F-X2 Guarda de lectura**: todo archivo trackeado se abre solo vía `guarded_text`/`source_guard` (raíz del proyecto, sin symlinks, O_NOFOLLOW); un documento no legible no entra al índice y se nombra (`store/path-outside-root`, `store/symlink-not-read`) (`docs/architecture/graph-backend.md`). Reciente (2026-11-11).

---

## 6. Transiciones como máquina de estados

### 6.1 Cómo un `RelationDoc` se vuelve transición [V]

1. Documento `RelationDoc` (modelo `sldb.models.relation_doc`): `source_id`, `target_id` (export ids `Modelo:nombre`, `store:Modelo:nombre` si es enlazado), `relation_type` (debe coincidir con el `name` de un `RelationTypeDoc` trackeado), `condition` (predicado del lenguaje; vacío = hereda el del tipo), `attributes` (dict libre), `notes`. `__references__ = ["source_id","target_id"]` (pero esos campos NO generan `references`: `RelationDoc` es "especial", `doc_contribution._declared_edges` lo salta).
2. Al reconstruir, `doc_kinds._relation` emite UNA arista `origin: relation_doc` con `metadata = {relation_doc: <nombre>, condition: <propia>, axis: "", [attributes: {...}]}` entre `sldb://document/<source_id>` y `sldb://document/<target_id>`.
3. Al componer (`resolve._authored`): si algún extremo no es nodo -> problema `relation '<doc>': source|target '<id>' is not a tracked document` y la arista se descarta; si no, `condition` = propia o la del tipo (`rt["condition"]`), `axis` = el del tipo; si el tipo es `undirected`, se agrega la inversa (`reverse: true`).
4. `attributes` solo entra a la arista si no está vacío (un shard viejo sin `attributes` sigue válido).
5. "Estados" = nodos documento arbitrarios (`edges_holding` toma `export_id` de cualquier nodo). Una transición es una arista cuyo `source` es el estado actual. No hay clase "State": en deskops los estados son `RitualDoc`.

### 6.2 Resolución de condiciones y efectos [V]

`edges_holding(store, export_id, subject, relation, include_linked, exclude_tags, document)`:
1. `edges = edges_from(store, export_id, relation, ...)` (aristas SALIENTES del estado; sin filtrar por origin: toda arista cuenta, no solo `relation_doc`).
2. Se queda con `payload_holds(e.metadata["condition"] or "", subject)` (el evaluador del lenguaje; condición vacía siempre cumple; `{campo}` en la condición se llena desde el sujeto; `WherePredicateError` si no parsea).
3. Para cada arista que cumple: `effects_of(metadata["attributes"], subject)` -> `{"sets": {...}, "terminal": bool}`: `sets` null/ausente = `{}`; `terminal` null/ausente = `False`; `{campo}` se sustituye solo dentro de strings (también cada string de una lista) con el escalar del sujeto en forma YAML (`true/false`, números, string tal cual); si el campo falta, es null o no escalar -> `LookupError` internamente -> `None` -> **la arista no se cumple** (se descarta). `sets: {x: null}` (null literal) sí asigna null. Otras claves de `attributes` se conservan en `metadata.attributes` pero no entran a los efectos.
4. Con `document` (`Model:name`, `store:Model:name` o ruta): `transition_findings(store, document, subject)` evalúa las reglas `on_transition` (RuleDoc con `moment: on_transition`, `subject:` y `condition:`; solo si el store registra `RuleDoc` o un derivado). Un finding `level == "error"` bloquea TODAS las aristas (`edges_holding` devuelve `[]`); sin error, cada arista devuelta lleva `findings` (las alertas). Sin `document`, no corre ninguna regla y `findings` queda `None`.
5. `edges_blocked(...)` es la otra mitad: las aristas cuya condición cumple pero que un error de `on_transition` bloquea, cada una con `effects` y `findings` (todos los del sujeto); `[]` si no hay error o sin `document`.
6. `apply_effects(subject, effects, model)`: copia profunda; cada clave de `effects["sets"]` reemplaza (o agrega) la clave de PRIMER NIVEL (sin fusión anidada); valida el payload completo con `model.model_validate` (`pydantic.ValidationError`); un valor de `sets` mapa o lista anidada -> `ValueError`. Nunca persiste. Quien llama debe resolver la clase del modelo (`load_registered_model(store, name, pythonpath).model_type`).
7. Validación de la convención en el propio `RelationDoc`: `terminal` debe ser booleano o null; `sets` mapa o null; cada valor de `sets` escalar (str, int, float, bool, date, null) o lista de escalares. Cualquier ruta de escritura de un RelationDoc que lo viole lanza `ValidationError` sobre `attributes`.
8. `terminal` solo se transporta; sldb no hace nada con él (lo interpreta deskops).

### 6.3 Ciclo de vida real de deskops (`/home/jp/proyectos/SLDB_1/deskops/desk/lifecycle/`) [V]

Estados = documentos `RitualDoc` (`ritual-execution`, `ritual-testing`, `ritual-closeout`; otros rituales `context-review`, `phase`, `drawer-triage`, `knowledge-distillation-pass` no tienen aristas). Tres tipos (RelationTypeDoc) con `direction: directed, cardinality: many_to_many, axis: HOW, source_types/target_types: [RitualDoc]`, `condition: ''` y tres RelationDoc:

| RelationDoc (nombre) | source -> target | `relation_type` | `condition` | `attributes` |
|---|---|---|---|---|
| `execution-task_activates` | `RitualDoc:ritual-execution` -> `RitualDoc:ritual-testing` | `task_activates` | `''` (siempre) | `sets: {status: active}`, `terminal: false` |
| `testing-task_hands_to_closeout` | `ritual-testing` -> `ritual-closeout` | `task_hands_to_closeout` | `nonempty(validation)` | `sets: {status: ready_for_testing}`, `terminal: false` |
| `closeout-task_completes` | `ritual-closeout` -> `ritual-closeout` (auto-arista) | `task_completes` | `status = "ready_for_testing"` | `sets: {status: closed}`, `terminal: true` |

Máquina: execution --(sin condición, status<-active)--> testing --(si `validation` no vacío, status<-ready_for_testing)--> closeout --(si status = ready_for_testing, status<-closed, terminal)--> `complete`.

Compuertas de actividad: RuleDocs `rule-activation-task-<tipo>.md` (code, debug, decision, docs, external, refactor, aggregate) con `moment: on_transition`, `subject: model <= TaskCodeDoc` (los subtipos heredan), `where: current_node = "ritual-execution"`, `severity: error`, y una `condition` que pide recibos `^subject` con `anchor_state = "intact"` y `verdict = "accepted"` de `kind = "preflight"` y `"bundle"` y `all(depends_on: status = "closed")`; reglas `rule-closeout-task-*` y `rule-closeout-alert-*` para el cierre (no abiertas por mí; contenido de reglas lo cubre otro revisor). Resultado: tarea en `ritual-execution` sin recibos intactos = `edges_holding(..., document="TaskCodeDoc:<id>")` devuelve `[]` y `edges_blocked` devuelve la arista con el finding.

Cómo lo consume deskops (`deskops/lifecycle.py`): `transitions_holding` = `edges_holding(store, "RitualDoc:<nodo>", payload, include_linked=False, document=<Modelo:nombre de la tarea>)`; `_is_lifecycle_edge` exige `origin == "relation_doc"` y target `RitualDoc:*`; `advance` toma la primera transición que cumple (el grafo tiene una arista por ritual), construye `sets = {**transition.sets, history: [... , relation_type], current_node: target|"complete"}` y llama `apply_effects(payload, {**effects, sets}, model)`; si `terminal`, `current_node = "complete"`. Comprueba que el efecto coincida con la tabla del paquete (`_check_against_package`, `LifecycleSeedOutOfDate`). Siembra: seeds gestionadas por `deskops init`/`desk update --apply`.

---

## 7. Invariantes, errores, y lo muerto/duplicado/a medias

### 7.1 Invariantes y garantías [V salvo [I]]
- Un shard es función de (documento + modelo + sections + versión): no depende de otros documentos; todo lo que depende de otros documentos (resolución de referencias, condiciones/ejes heredados, reversas, aristas colgantes, estados de ancla, evidencia, ciclos) se calcula al componer y NUNCA se guarda (`resolve.py`, `doc_anchor_states.py`, `edge_evidence.py`).
- Compose nunca reconstruye. Reconstrucción solo en `rebuild_edges`, escrituras de sldb y `update_store_indexes`.
- Ids de nodo `sldb://...` y el nombre de contrato `sldb_kgdb_semantic_export` v1 están congelados (`tests/store/test_export_contract_frozen.py`).
- Una arista `schema`/`alias` a un destino inexistente se descarta; una `relation_doc` a un destino inexistente produce `problems`; ninguna se guarda.
- Un id de documento en un store enlazado se califica solo cuando tiene un `:` (Model:name); un `store:Model:name` ya calificado no se vuelve a calificar.
- `EdgeIndex` compartido entre llamadores = solo lectura.
- `semantic_parent` y `extends` deben ser DAG (`check_edges`).
- Análisis: por defecto solo relaciones autoradas (la espina derivada hace a todo par de documentos vecino a 2 saltos).
- Errores propios: `SLDBGraphArgumentError` (kwargs inesperados, filtro `payload` sobre campo no proyectado, filtro con camino), `SLDBGraphCycleError` (`order`, `layers`), `WherePredicateError`, `SLDBError` (pagerank sin scipy), `SLDBDocumentTargetError`, `SLDBValidationError`, `SLDBModelError` (`__indexed__`, `__doc_anchors__` inválidos), `SLDBStoreError` (sin `project_graph`), `ValueError` (backend no admitido, op desconocida, centralidad desconocida, `sin proyección para <id>`), `RuntimeError` (`analyze` antes de `load`).

### 7.2 Discrepancias entre documento y código [V]
1. **`docs/architecture/edges-retirement-plan.md`**: el retiro de `runtime/edges/` fue CANCELADO (2026-10-18, nota de cabecera), pero el cuerpo sigue describiendo el plan y GAPs G1-G6. Las rutas `runtime/edges/` de los docs hoy son `.sldb_cache/edges/`. El plan cita `sldb stores migrate-legacy-edges` y `kgdb_sync`, ya inexistentes.
2. **`docs/architecture/model-federation-on-the-graph.md`** dice "propuesta, sin implementar" (2026-10-17); el código ya lo implementa (`links_to`, `origin_store` en `has_model`, `ModelEntry.origin` `S/store/models/model_entry.py:17`, commits 2026-10-18).
3. **`docs/architecture/sldb-absorbs-kgdb.md`** cuerpo dice "sin networkx"; la enmienda lo revierte (networkx>=3.0 dependencia dura; PageRank en extra `graph`).
4. **`docs/export_provenance_contract.md`** pide `doc_address`, `doc_version`, `local_address`, `model_identity` y aristas de tag con `origin=sec.roadmap`; el export/ingest NO emite nada de eso (un `grep` de `doc_address|local_address` solo toca `api/documents/document_serialization.py`, que usa `version`). Es un contrato aspiracional [V].
5. `docs/architecture/graph-backend.md` dice "las diez" operaciones de `analyze`; el backend tiene 12 (suma `path_explained`, `paths_explained`).
6. El docstring de `contract_signature` y el doc dicen que cubre `{fields, references, containment, compositions}`; no incluye `__reference_models__` ni `__doc_anchors__` (estos mueven `DocEdges.contract_hash` o la clave del índice en disco, pero no `contract_signature`) [V `model_contract.py:89-102`, `composed_disk._stamp`].
7. Nomenclatura de snapshots incoherente: la ruta del export semántico etiqueta documentos `sldb_document` y sus modelos llevan `hash_b`; la ruta desde shards usa el nombre del modelo como `node_type`; el export NO lleva nodos `sldb_field`, `relation_type`, `anchor` ni aristas `has_field/extends/references/contains/composes/relation_doc` (GAPs G1-G3 del plan). El `GraphSnapshot` por defecto de `snapshot_save` es, por tanto, un subconjunto del índice [V `snapshot.py:17-35`, `ingest_sldb_nodes.py`].

### 7.3 Muerto, duplicado o a medias [mezcla V/I]
- **`unavailable` en aristas**: la vista del lenguaje (`index_view._edge`, `predicate_reach`) lo respeta, pero ningún productor del índice lo escribe. Código sin camino real de entrada [V].
- **Slots de `KnowledgeNode`** `ast, io_ports, compliance, adr, test_map, git`: herencia de kgdb; nadie los llena en sldb (solo `git` y `source` se copian si existen) [V].
- **`FacetPayload._DEFAULTS`** (`compiled_from: wiki-compiler`, `coverage_percent`, `exemption_reason`, `failing_standards`, `raw_docstring`): residuo de kgdb [V `facet.py:14-21`].
- **`traversal.py` + `api/graph/traverse.py`**: sin CLI y sin test hallado; solo uso externo (pron/MCP, por docstring) [V/I].
- **`composed_disk`** sin test propio hallado por nombre [I].
- **`is_acyclic`** existe pero solo se usa para el chequeo de ciclos vía `cycles`; no está expuesto por backend ni CLI [V].
- **Doble implementación de vista del lenguaje**: `predicate_view.node/edge/with_node` y `backend/index_view` ambas construyen la misma vista [I].
- **`ModelEdges.projected_fields`** se vuelve a calcular en `_derived` agregando `__anchor_state`; el campo `payload_fields` mezcla campos de modelos distintos en una lista plana en `unprojected_fields` (un filtro `payload` válido para cualquier modelo basta) [V `payload_match.py:63-67`].
- **`edges init` no hay `edges_blocked` CLI**: solo existe como API; deskops la usa.
- **`document_edges` hace `rebuild_edges(wait=True)` en una lectura**: contradice "reading never rebuilds" del resto (es la única lectura que reconstruye) [V `document_edges.py:30`].
- **`Referrer.origin == "mention"` vs aristas**: "mention" no es un `metadata.origin` de `EdgeRecord`; el docstring de `document_edge` menciona `structural` y `schema`, no `alias` [I].
- **Federación en snapshots**: `snapshot_save` con stores enlazados o `exclude_tags` cae a la ruta vieja (G5/G6 abiertos), pese al "retiro cancelado" [V].
- **`api/graph/snapshot.py` y `graph_get`** no tienen filtro `exclude_tags` en la ruta del export (documentado G6).

### 7.4 Lo que NO pude determinar
- El layout y la semántica exactos de `store/io/shards.py`, `store/io/shard_compose.py` (`compose_edges_documents`) y `documents_dirty`/`built_cache` (los llamo pero no abrí su código) [no leído].
- Qué pasa con el índice en disco si dos procesos escriben el mismo `composed/<hash>.json` (usa `os.replace`, pero no vi pruebas) [I].
- El contenido de las reglas `rule-closeout-*` y `rule-activation-*` salvo `rule-activation-task-code` (de reglas se ocupa otro revisor).
- Los tests exactos que cubren cada feature: los asigné por nombre de archivo y `grep` de símbolos; no ejecuté nada.
- Tamaño real del índice de deskops (el doc de retiro cita 1812 nodos / 16013 aristas en otra época, no verificado).
