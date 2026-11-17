# sldb: inventario de MODELOS (revisor 02)

Fecha de lectura: 2026-11-16. Todo es de `/home/jp/proyectos/SLDB_1` (solo lectura). Rutas relativas a `sldb/src/sldb/` salvo que diga otra cosa. [V] = verificado en el archivo citado; [I] = inferencia mía. "Reciente" = entró desde 2026-10-01 según `git log`.

## 1. Qué es un modelo en sldb

Un modelo es una subclase Python de `StructuredNLDoc` (pydantic `BaseModel`, `models/structured_doc.py:9`) [V]. Un solo archivo de clase junta: (a) el esquema de campos, con descripción obligatoria no vacía por campo (`__pydantic_init_subclass__`, `structured_doc.py:19-23`, lanza `TypeError` al definir la clase) [V]; (b) la plantilla Markdown `__template__` con marcadores `⸢rev•campo⸥`, `⸢optrev•…⸥`, `⸢render•…⸥`, `⸢py•expr⸥` y modificadores `,list`, `,markdown`, `,dict` (las cuentas de uso están en `src/`; la gramática vive en `core/` y `runtime/validation.py`, no la leí entera) [V/I], que es a la vez el formato de archivo del documento, el extractor (texto→payload) y el renderizador (payload→texto), con roundtrip validado; (c) validadores pydantic (invariantes de UN documento); (d) metadatos de clase que sldb lee para derivar el grafo: semántica/tags, referencias, contención, composiciones, anclas a documentos, autoridad, familia, campos indexados, contrato de ancla; (e) la herencia de clases, que sldb guarda como `base_models` y consulta como `model <= X`; (f) un hash de contrato de la clase (`contract_hash`) que se guarda en el índice del modelo y detecta que la clase cambió. El store guarda por modelo una entrada de registro (`ModelEntry`: name, model_ref `module:Class`, path, version, canonical, family, semantics, origin) y un `ModelsIndex` (roster_hash, contract_hash, model_hash, base_models, documents_index) [V: `store/models/model_entry.py`, `models_index.py`]. Los modelos son código Python importable; el MCP nunca crea ni modifica módulos de modelo (nivel 3 "nunca servido", `mcp/mutability.py:6`) [V]. Reglas (`RuleDoc`), tipos de relación (`RelationTypeDoc`) y aristas (`RelationDoc`) son también modelos: el grafo se describe con documentos.

Hay TRES cosas distintas llamadas "contrato" (importante para el diseño en kimun):
1. `hash_model_contract` (`store/hashing.py:83`): hash de la CLASE (campos, plantilla, familia, semántica, bases, refs, contención, composiciones, `reference_models`). Guardado en `ModelsIndex.contract_hash` [V].
2. `contract_hash(model_type, field_hashes)` (`store/graph/language/contract_hash.py:13`): hash de un DOCUMENTO sobre sus campos (o los de `__contract__`); es lo que fija las anclas `doc:Modelo:nombre@sha256:…` [V].
3. `contract_signature` (`store/model_contract.py:84`): sha256 de nombres de campos + nombres de refs/contención/composiciones, para saber si las aristas de un shard siguen vigentes [V].

## 2. Atributos de clase que un modelo puede declarar

"Consumidores" = cuántos modelos de consumidor lo declaran DIRECTO en su clase (deskops 25 clases, spec2viz 7, pron 7, IR 10; total 49; ver §4). Los de sldb propio (20 clases de dominio) se anotan aparte. Cuenta hecha por AST, sin importar sldb [V].

| Atributo | Tipo / default (base) | Qué hace sldb | Dónde se lee | Consumidores (direc.) |
|---|---|---|---|---|
| `__template__` | `str` = `""` | Es el formato de archivo: render/extract/validación roundtrip, secciones, IR de documento. Entra al hash de contrato. Un campo solo hace roundtrip si su marcador está en la plantilla. `template_anchors()` expone el párrafo-ancla literal previo a cada campo. | `core/renderer.py:40`; `runtime/validation.py:16`; `cli/serve/sections_routes.py:49`; `store/hashing.py:91`; `api/model_drafts/backfill.py:74`; `api/model_registry/template_anchors.py:34` | deskops 17, spec2viz 7, pron 7, IR 9 (los 9 vía helper `_template`); sldb 19/20 |
| `__semantics__` | `dict[str, Any]` = `{"representation":["markdown"],"source":["document","markdown"]}` | Se aplana a tags `clave.valor.valor` (une el MRO de base a hijo; una clave del hijo reemplaza la de la base). Se guarda en `ModelsIndex.semantics`, se copia a los documentos como tags (más los campos `tags`/`semantic_tags` del payload) y alimenta nodos `semantic_tag` del grafo. Entra al hash. | `store/semantic_tags.py:13` (flatten) y `:28` (docs); `api/model_registry/model_class_attrs.py:15`; `cli/serve/schema.py:61` | deskops 16, spec2viz 7, pron 7, IR 10; sldb 18/20 |
| `__references__` | `list[str]` = `[]` | Campos que guardan ids de otro documento (`Model:name` o `store:Model:name`, o ruta `*.md`/con `/`). Cada valor produce arista `references` (`origin: schema`, lleva `field`, `declared`). Si el valor no es un id, queda como arista pendiente `sldb://unresolved_ref/…` hasta componer el índice. Entra al hash y al `contract_signature`. | `store/edge_index/declared_references.py:57`; `store/edge_index/doc_contribution.py:68`; `store/hashing.py:93`; `cli/serve/fields_routes.py:99`; `api/model_drafts/draft_contract.py:28` (valida que existan los campos) | deskops 3, spec2viz 6, IR 1 (pron 0); sldb 8 |
| `__reference_models__` | `dict[str, list[str]]`; no está en la base (se lee con `getattr`) | Restringe a qué modelos puede apuntar un campo de referencia; permite resolver ids sin calificar (`value` → `Model:value` del único modelo permitido). Un campo aquí cuenta como referencia aunque no esté en `__references__`. Solo entra al hash cuando existe ("no otro contrato se mueve"). | `store/edge_index/declared_references.py:56`; `store/hashing.py:86`; `api/edges/unresolved_references.py:16` | deskops 1 (TaskDoc); sldb 0 |
| `__containment__` | `dict[str, list[str]]` = `{}` | Campos-hijo: arista `contains` (one_to_many) con los modelos permitidos. Si un campo está en contención y en referencias, gana contención. | `declared_references.py:55`; `hashing.py:94`; `draft_contract.py:28`; `cli/serve/schema.py:59` | deskops 1 (BoardDoc), spec2viz 1 (SpecNodeDoc); sldb 1 |
| `__compositions__` | `dict[str, dict]` = `{}` (cada spec: `source_field`, `model`, `template`, `separator`, `ref`) | Campo "compuesto": `render_payload` lo rellena leyendo los documentos/campos que lista `source_field` (por doc: extrae el payload del archivo referido y aplica `template.format`; `ref: field`: resuelve campo a campo). Cada ref produce arista `composes`. Cada composición necesita marcador `⸢render•nombre⸥` en la plantilla. Lectura con guarda de raíz/symlinks. | `models/structured_doc.py:28-76`; `store/edge_index/composition_edges.py:41`; `store/hashing.py:94`; `api/model_drafts/composition_contract.py:13`; `backfill.py:73` | deskops 1 (BoardDoc), pron 1 (ReadmeDoc); sldb 0 |
| `__doc_anchors__` | `list[str]` = `[]` | Campos `str`/`list[str]` que guardan anclas `doc:Modelo:nombre[@sha256:<64 hex>]`. Validador de forma al construir (`check_anchor_fields`); al guardar se fija (pin) el hash de contrato del destino; el índice agrega arista `references` y un estado `<campo>__anchor_state` (intact/changed/lost). Sufijo `__anchor_state` reservado. Al registrar la clase se valida la definición. | `models/doc_anchor_value.py:53,68`; `store/model_contract.py:66`; `store/edge_index/doc_contribution.py:68`; `doc_anchor_edges.py`; `api/documents/doc_anchor_pinning.py`; `api/anchors/check_doc_anchors.py` | deskops 1 (ReviewDoc); sldb 0 (sí se usa el mismo mecanismo en `Provenance.from_`) |
| `__authority__` | `str` = `"generated"` (valores: source_raw, authored, theorem, generated, cache) | Autoridad por defecto de los documentos del modelo; se estampa en el bloque `sldb:` del frontmatter al escribir y la política de hallazgos lee autoridad por documento. NO entra al hash de contrato [V: `hashing.py:90-94` no lo incluye]. `Provenance.of_model` distingue "declarada" de heredada de la base. | `models/provenance.py:51-52`; `api/documents/provenance_stamp.py:39`; `api/model_registry/model_class_attrs.py:16` | IR 1 (SpecIRNodeDoc, `authored`); sldb 1 (CodeAnchorDoc `theorem`); deskops 0, pron 0, spec2viz 0 |
| `__family__` | `str`; NO está en la base (`getattr`) | Etiqueta de texto libre ("raíz de la rama"); se guarda en `ModelsIndex.family`/`ModelEntry.family`, se describe y exporta y entra al hash. **Ningún evaluador de reglas/consultas la lee** (lo afirma `docs/architecture/evidence-model-family.md §1.2` y mi grep no encontró otro consumidor) [V]. Se relee de la clase en cada firma del nodo (`store/model_node.py:34`). | `store/model_contract.py:27`; `api/model_registry/model_lineage.py:20`; `store/hashing.py:92`; `store/model_node.py:34` | pron 7 (`knowledge` ×6, `ledger` ×1); sldb 3 (`relation` ×2, `rule`); deskops 0 |
| `__indexed__` | `list[str]`; NO está en la base | Lista de campos del payload que se proyectan al grafo para filtros `--where` sobre el payload. Valida que existan y sean escalar o lista de escalares (lanza `SLDBModelError` al resolver la clase). Sin él, se proyecta todo campo cuyo tipo lo admita. NO entra al hash. | `store/model_contract.py:45,77,111`; `api/model_registry/model_reference.py:79` | 0 [V grep en los 4 consumidores] |
| `__contract__` | `list[str]`; NO está en la base | Campos que definen el contrato del DOCUMENTO para hash de ancla: `contract_hash` = sha256 de líneas `campo=hash` ordenadas. Ausente = todos los campos; declarada vacía = `ValueError` (H11). No entra al hash de contrato de clase. Sí entra a la firma de caché `composed_disk.py:97`. | `store/graph/language/contract_hash.py:22-26`; `store/edge_index/doc_anchor_states.py:37`; `store/edge_index/composed_disk.py:97` | deskops 1 (TaskDoc: 7 campos) |
| `__pydantic_init_subclass__` (no es atributo, pero es regla) | — | Rechaza campos sin descripción. | `structured_doc.py:19` | todos |
| `model_config` pydantic | — | No lo lee sldb; TaskDoc usa `extra: "ignore"` | `deskops/deskops/models/task.py:48` | 1 |
| `__artifact__` | dict (`id_pattern`, `status_default`, `tags`) | **No lo lee sldb** [V: sin coincidencias en `sldb/src`]; es convención de deskops (`deskops/specs/compiler.py:40`, `cli/model_introspection.py:310`, `doc_access.py:596`). | — | deskops 12 |

Atributos que NO existen en sldb y que a veces se supone (no los inventes): no hay `__abstract__`, `__id__`, `__validators__`, `__status__` ni similares [V grep de `getattr(..,'__x__')`: solo authority, compositions, containment, contract, doc_anchors, family, indexed, reference_models, references, semantics, template].

Qué NO puede editar el API de creación/drafts: `create_model` y `edit_model_metadata` solo manejan 5 atributos (`authority`, `semantics`, `containment`, `references`, `compositions`) + plantilla + campos (`api/model_create/model_source.py:69`; `api/model_drafts/metadata_drafts.py:43`) [V]. `__family__`, `__doc_anchors__`, `__reference_models__`, `__indexed__`, `__contract__` solo se escriben a mano en el `.py`. El camino CLI `models create` sí emite `__family__` (`cli/commands/models_create_attrs.py:11-17`), el API no [V].

## 3. Funcionalidades: registro, drafts, creación, herencia, hash, federación

Pruebas: `sldb/tests/…`. "Reciente" = cambios desde 2026-10-01.

| # | Función | Qué hace | Dónde | Cómo se llega | Lee / escribe | Test | Reciente |
|---|---|---|---|---|---|---|---|
| 1 | Resolver referencia `module:Class` | Importa la clase, exige que sea subclase de `StructuredNLDoc`, valida `__indexed__` y `__doc_anchors__` al cargar. Antepone `pythonpath` y el cwd a `sys.path`. Traduce prefijos legados (`spec2viz.sldb_models` → `sldb.models.notation_models`). | `api/model_registry/model_reference.py:65-103` | `resolve_model_ref`, todo el resto | lee módulo Python | `test_cli_v2.py`, `store/test_upgrade_compat.py` | parcial (GO07, 2026-11-06) |
| 2 | `add_model` | Registra una clase con índice de documentos vacío, nodo de modelo sellado (roster+contrato), reconstruye derivados, lo declara en `store.yaml`. Rechaza duplicados. `canonical` marca el modelo canónico de su familia. | `api/model_registry/add_model.py:19`; `model_index_writes.py:25-52` | `sldb.api.add_model`, CLI `models add` (`cli/commands/model.py`, `model_add.py`) | escribe `.sldb/core/models/*`, índice del store, shards derivados | `api/test_api_models.py`, `test_model_lifecycle.py` | no |
| 3 | `remove_model` | Desregistra: entrada, índices y shards; con `untrack_documents` también deja de seguir sus documentos (los .md quedan). Rollback si el rebuild falla. Nunca importa la clase. | `api/model_registry/remove_model.py:21` | `remove_model`, CLI `models remove [--with-documents]` | borra archivos del modelo; reconstruye derivados | `api/test_remove_model.py` | no |
| 4 | `reindex_model` | Recalcula hashes de contenido, de campos y bloque de procedencia de todos los documentos; opcionalmente sube `version`; re-sella el nodo del modelo. | `api/model_registry/reindex_model.py:19`; `model_index_writes.py:63-100` | `reindex_model`, CLI `models update` | lee .md, reescribe índices | `test_journal.py`, `test_model_lifecycle.py` | no |
| 5 | `describe_model` / `ModelDescription` | Contrato leído: campos (nombre, anotación, descripción), documentos, containment, references, compositions, semantics_map, authority, template, has_draft/draft_path, version, canonical, family, base_models. Solo importa ESA clase. | `api/model_registry/describe_model.py:22`; `model_class_attrs.py`; `model_description.py` | `describe_model`, CLI `models show`, MCP `describe_model`, HTTP `GET /models/detail` | solo lectura | `api/test_api_models.py`, `mcp/test_read_tools.py` | no |
| 6 | Esquema de campos por tipo | Clasifica anotaciones en kinds (`string`, `boolean`, `integer`, `number`, `object`, `stringlist`, `enumlist`, `list`, `enum`) para formularios; `StoreSchema` describe todos los modelos y lista aparte los `unavailable` (con razón). | `models/field_schema/kinds.py`; `api/schema/store_schema.py`, `unavailable_model.py`, `describe_field.py` | `/schema` de `sldb serve` (`cli/serve/schema.py`) | solo lectura | `api/test_machine_editor_schema.py` | no |
| 7 | `registered_models` / `is_model_registered` | Lista las entradas (propias y heredadas) o pregunta por nombre, `module:Class` o `module.Class`, sin leer archivos de índice a mano. | `api/model_registry/registration_query.py` | API | lee `store_index` | `api/test_registration_query.py` | no |
| 8 | Herencia de clases / familias por `base_models` | `model_base_names` guarda los bases `StructuredNLDoc` del MRO (más cercano primero) en `ModelsIndex.base_models`; el lenguaje de predicados evalúa `model <= X`, `st.{Model+}`, `RuleDoc.subject` y la arista `extends` salen de ahí, sin importar clases. Una clase base abstracta registrada con 0 documentos cuesta un índice vacío. | `api/model_registry/model_lineage.py:24`; `store/model_contract.py:base_names:20`; `store/graph/language/predicate_leaves.py:58`; `models/rule_doc.py:53` | automático al registrar/re-sellar | escribe `base_models` | `store/test_family_follows_the_class.py`, `api/test_declared_references_family.py`, `api/test_resolve_indexed_document.py` | no |
| 9 | Hash de contrato de clase `hash_model_contract` | sha256 del JSON ordenado de: campos `{nombre: [str(anotación), descripción]}`, `__template__`, `__family__`, semántica aplanada, `base_models`, `__references__`, `__containment__`, `__compositions__`, y `__reference_models__` solo si está declarado. Se guarda en `ModelsIndex.contract_hash`; `model_hash` = hash de (roster_hash, contract_hash). `contract_moved` indica que la clase cambió desde la firma; `stores check` informa "contract moved", `stores update` re-firma. | `store/hashing.py:83-97`, `hash_model_layer:100`; `store/model_node.py:20-55` | `stamp_model_node` en cada escritor de índices | escribe `contract_hash`, `model_hash` | `store/test_contract_in_model_node.py`, `store/test_upgrade_compat.py` | cubre metadata de clase desde 2026-10-05 (`cecd97ba`) |
| 10 | Compatibilidad con la fórmula vieja | Un hash guardado igual a (campos+plantilla) de la clase de hoy se reporta como `legacy_contract` (alerta, no fallo) y `stores update` lo re-sella. | `store/legacy_contract.py:17-28`; `store/diagnostics.py:100` | `stores check` | lee | `store/test_upgrade_compat.py` | 2026-10-16 |
| 11 | Hash de contrato de documento (anclas a documentos) | `contract_hash`: sha256 de `campo=hash_campo` ordenado por bytes UTF-8; con `__contract__` solo esos campos. `anchor_state` = intact (hash igual), changed, lost (no existe o no estaba pinneado). | `store/graph/language/contract_hash.py`; `store/edge_index/doc_anchor_states.py:20-70` | pin al guardar (`api/documents/doc_anchor_pinning.py`), `check_doc_anchors`, lenguaje de reglas (`anchor_state`) | lee `field_hashes` de shards | `api/test_doc_anchors.py`, `test_rules_language_spec.py` | 2026-11-05/06 (GO05, GO07) |
| 12 | Registro por módulo (`module:Class`) y `pythonpath` | La identidad de un modelo es su `model_ref`; `path` es solo pista. `--pythonpath`/`pythonpath` pone un directorio al frente de `sys.path`; las raíces de los dueños se agregan solas para modelos heredados. `same_model_class` compara por objeto. | `model_reference.py:23-62`; `store/model_origin.py:61` | CLI `--pythonpath`; `add_import_roots` | modifica `sys.path` del proceso | `api/test_model_source_portable.py` | 2026-10-18/19 |
| 13 | Modelos heredados (federación) `inherit_model` | `alias:ModelName`: registra una entrada heredada (`origin=alias`, sin `path` propio, import desde la raíz del store dueño). Convierte en sitio una copia local del mismo `model_ref` conservando documentos, roster y contrato firmado. Rechaza si es otra clase (anulación: debe extender con nombre propio) o ya hereda de otro. | `api/model_registry/inherit_model.py:24-107`; `federated_registration.py`; `store/model_origin.py` | `inherit_model`, CLI `models inherit`; también `alias:Model` perezoso en `load_registered_model` | escribe entrada, `models_index`, `store.yaml` | `api/test_inherit_model.py` | sí (2026-10-18; `502617d6` 2026-11-06 declara en `store.yaml`) |
| 14 | Lookup federado | `alias:ModelName` se resuelve por los enlaces de stores del destino y del store local ancestro; registra heredada al primer uso. | `api/model_registry/federated_lookup.py`; `load_registered_model.py:19` | cualquier API que resuelve modelo por nombre | escribe entrada heredada | `api/test_inherit_model.py` | sí |
| 15 | `shadowed_models` | Reporta modelos propios cuyo nombre también posee un store enlazado (copia convertible, o anulación). No rechaza. Ignora modelos de `sldb.*`. | `store/model_origin.py:80-102` | `stores check` | lee | `api/test_inherit_model.py` | sí |
| 16 | `create_model` (JSON → módulo → registro) | Declara campos `{name,type,description,required?,default?,enum?}` (tipos `str,int,float,bool,list[str]`, `Literal[...]` de strings; sin `dict`, `list[dict]`, `None`), valida nombre CamelCase y unicidad, genera módulo bajo `<pythonpath>/<target_package>` (por defecto `sldb_generated`), exige import + roundtrip de payload de ejemplo antes de escribir, registra con `add_model`. `base` = nombre de modelo registrado (hereda campos y cabecera de plantilla). Plantilla por defecto = una sección por campo + `⸢render•…⸥` por composición. | `api/model_create/create_model.py:20`, `field_decl.py`, `model_declaration.py:12`, `model_check.py`, `model_generation.py`, `model_source.py` | `create_model`, CLI `models create`, HTTP `POST /models/create`, `pron` `kb_model_create` | escribe `.py`, registra | `api/test_model_lifecycle.py`, `test_model_metadata_drafts.py` | 2026-11-04/06 |
| 17 | Drafts de contrato | Toda edición va a un `<módulo>.py.temp` hermano; el modelo activo no se toca. `edit_model_template`, `add_model_field`, `remove_model_field` (sección de plantilla incluida) y `edit_model_metadata` (reescritura por AST de `__containment__`, `__references__`, `__compositions__`, `__semantics__`, `__authority__`; sincroniza secciones `⸢render•…⸥` al agregar/quitar composiciones). Sigue re-exports hasta la clase definidora. | `api/model_drafts/{template_drafts,field_drafts,metadata_drafts,metadata_literals,template_literals,template_sections,source_location,field_blocks,source_editing}.py` | API `edit_model_template`, `add_model_field`, `remove_model_field`, `edit_model_metadata`; CLI `models template|fields|metadata`; HTTP `POST /models/metadata-edit` | escribe `.py.temp` | `api/test_api_model_drafts.py`, `test_model_metadata_drafts.py` | 2026-11-06 (SV04) |
| 18 | Validación de draft | Carga ambos contratos; plantilla solo nombra campos declarados; `__references__`/`__containment__` solo campos conocidos; cada composición tiene `⸢render•n⸥`; cada documento rastreado debe seguir haciendo roundtrip con el contrato nuevo (los campos nuevos requieren default u opcional). Devuelve `backfill` = docs que se reescribirían. | `api/model_drafts/draft_validation.py:23`, `draft_contract.py:17`, `composition_contract.py`, `backfill.py:46` | `validate_model_draft`, CLI `models validate` | solo lectura | `api/test_api_model_drafts.py` | no |
| 19 | Promoción + backfill | `promote_model_draft(backfill=…)`: valida, re-renderiza los documentos con campos nuevos (con sello de procedencia `via=backfill_document`), reemplaza el .py activo con el draft, borra el draft, `reindex_model(bump_version=True)`. Si algo falla restaura modelo activo, draft e índices. | `draft_validation.py:39-90`; `draft_restore.py` | `promote_model_draft`, CLI `models validate --promote --backfill`, HTTP `POST /models/promote` | escribe .py, .md de documentos, índices | `api/test_model_lifecycle.py`, `test_journal.py` | no |
| 20 | Bloque de procedencia (`sldb:` en frontmatter) | `Provenance` (by, at, via, authority, from): se lee/quita/pega del texto sin pasar por el modelo; `index_block` lo copia al índice del documento. Ítems `doc:` de `from` validados como ancla. | `models/provenance.py`, `provenance_block.py`; `api/documents/provenance_stamp.py` | toda escritura de documento | escribe frontmatter | `api/test_provenance.py`, `test_provenance_index.py` | sí (2026-10-20, GO07) |
| 21 | Anclas de código (`code:<ruta>::<nombre>`) | `parse_code_ref` (`.py .ts .tsx .js .jsx`, sin `..`), `CodeAnchorDoc` (autoridad `theorem`), `AnchorState` (8 estados con nivel ok/alert/error). | `models/code_ref.py`, `code_anchor_doc.py`, `anchor_state.py` | `anchors record|check` | — | no localicé test dedicado en este recorrido (no verificado) | sí (2026-10-20; frontend TS/JS 2026-11-16) |
| 22 | Convención de transiciones en `RelationDoc.attributes` | `sets` (campo→escalar o lista de escalares) y `terminal` (bool) validados; el resto de `attributes` es libre. | `models/transition_attributes.py`; validador en `relation_doc.py` | al escribir un `RelationDoc` | — | `test_relation_models.py` (cubre el modelo; la convención no la confirmé en detalle) | sí (GO09, 2026-11-06) |
| 23 | Tipos de relación builtin | 15 `RelationTypeDoc` que genera el propio índice (`has_model, links_to, has_document, has_section, tagged_as, semantic_parent, semantic_equivalent, has_field, extends, applies_to_source, applies_to_target, names, references, contains, composes`), con eje, cardinalidad y tipos de nodo. Se escriben como documentos `reltype-<name>` con `init_relations`. | `models/builtin_relation_types.py` | `sldb.api.init_relations` | escribe documentos | `test_relation_models.py` | no |
| 24 | Vistas declaradas (`ViewDoc` compuesto) | Vista de proyección de grafo (nodos `{model, where}`, relaciones, dirección, notación, layout) o vista compuesta (params + secciones con selector, campos, aristas, `group_by`, reglas, transiciones). | `models/notation/view_doc*.py`, `view_section*.py`, `view_param.py` | `run_view`, `list_views`, `resolve_view` (MCP), CLI de vistas | lee store | `api/test_view_doc.py`, `api/test_run_view_reads.py` | sí (GD25, 2026-11-06) |
| 25 | Esquemas de spec por tipo de diagrama | Modelos pydantic (no `StructuredNLDoc`) por tipo: activity, class, component, deployment, matrix, reflection, sequence, state, catalog, con cargador y validador. Solo por nombre. | `models/notation/diagram_specs/*` | `notations/` | — | `tests/notations/test_spec_*` | sí (2026-11-15, D3) |

## 4. Modelos de sldb propio y de consumidores (una fila por modelo)

"Campos" = propios/efectivos cuando difieren. "Validadores" detallados en §5.

### 4.1 sldb (`sldb/src/sldb/models/`)

| Modelo | Base | Atributos | Campos | Validadores |
|---|---|---|---|---|
| StructuredNLDoc | BaseModel | defaults de §2 | 0 | `_doc_anchor_forms` (after) |
| RelationDoc | StructuredNLDoc | family `relation`, semantics, references `[source_id,target_id]`, template | 7 | `attributes` before + after |
| RelationTypeDoc | idem | family `relation`, semantics, template | 9 | solo `Literal` (direction, cardinality) |
| RuleDoc | idem | family `rule`, semantics, template | 7 | `_parses`, `_is_a_family` + `Literal` severity/moment |
| CodeAnchorDoc | idem | authority `theorem`, semantics, template | 7 | ninguno |
| AnchorDoc (sldb) | idem | semantics, template | 7 | `pattern=` en `kind` y `ref` |
| CliCommandDoc | idem | semantics, template | 10 | `KnowledgeTag` (pattern) |
| SurfaceDoc | idem | semantics, template | 8 | `KnowledgeTag` |
| PythonSymbolDoc | idem | semantics, template | 16 | `KnowledgeTag` |
| SpecDoc / SpecNodeDoc | idem | semantics `system:spec2viz`, references, (SpecNode: containment `contains:[SpecNodeDoc]`), template | 13 / 11 | solo "ausente→vacío" |
| NotationDoc / GlyphDoc / LinkDoc / PaletteDoc | idem | semantics, references, template | 12 / 17 / 17 / 9 | Link: ausente→vacío |
| ViewDoc | idem | semantics, references `[notation]`, template | 11 | 6 validadores (ver §5) |
| ViewParam, ViewSection, ViewEdge | BaseModel (`extra=forbid`) | — | 3 / 8 / 3 | ver §5 |
| Provenance | BaseModel | — | 6 | `from_` |

Modelos de evidencia (`ProofDoc`, `EvidenceDoc`, `GoldensDoc`, etc.): **no existen** en código. `docs/architecture/evidence-model-family.md` (2026-11-07) compara herencia vs `__family__`, recomienda herencia con raíz registrada con 0 documentos, y su §6 "Decisión del dueño" está en "PENDIENTE" [V]. Lo que sí existe como evidencia es `ReviewDoc` de deskops con `kind: evidence`.

### 4.2 deskops (`deskops/deskops/models/`, 25 clases)

| Modelo | Base | Atributos que fija | Campos (propios/efect.) | Validadores |
|---|---|---|---|---|
| PrimitiveDoc | StructuredNLDoc | template | 5 | 0 |
| OperationalArtifactDoc | PrimitiveDoc | — | 2 / 7 | 0 |
| TaskDoc | OperationalArtifactDoc | artifact, **contract**(7), references(6), **reference_models**(5), semantics, template, `model_config extra=ignore` | 20 / 23 | before, title, after (ciclo de vida), serializer wrap |
| TaskCodeDoc, TaskDocsDoc, TaskDebugDoc, TaskDecisionDoc, TaskAggregateDoc, TaskExternalDoc | TaskDoc | nada; solo fijan `task_type: Literal[...]` | 1 / 23 | hereda los de TaskDoc |
| TaskRefactorDoc | TaskCodeDoc | idem | 1 / 23 | hereda |
| BoardDoc | OperationalArtifactDoc | artifact, **containment**(`tasks→TaskDoc, pills→PillDoc, rituals→RitualDoc`), semantics, **compositions**(`task_summaries`: de `tasks`, modelo `deskops.models:TaskDoc`, `- {title} [{status}] - {goal}`), template | 9 / 13 | 0 |
| PillDoc | OperationalArtifactDoc | artifact, semantics, template | 13 | 0 |
| RitualDoc | OperationalArtifactDoc | artifact, semantics, template | 10 / 14 | 0 |
| RuntimeProfileDoc | PrimitiveDoc | artifact, semantics, template | 14 / 19 | 0 |
| AtomDoc | StructuredNLDoc | artifact, semantics, template | 6 | `id` before, `tags` before |
| ProtoAtomDoc | StructuredNLDoc | semantics, template | 6 | `id`, `tags` before, `tags` after |
| CrossroadDoc | StructuredNLDoc | semantics, template | 4 | `path` before, `content`, `id` after |
| ReviewDoc | StructuredNLDoc | **doc_anchors**(`subject`), references `[]`, semantics, template | 20 | before, after; 3 `pattern=` |
| RunDoc | StructuredNLDoc | references `[task_id, role_id]`, semantics, template | 15 | 0 (nada lo escribe aún, docstring `run.py:9-15`) |
| DeskFAQDoc | StructuredNLDoc | artifact, semantics, template | 2 | 0 |
| InboxNoteDoc | idem | artifact, semantics, template | 9 | 0 |
| MaterializationContractDoc | idem | artifact, semantics, template | 9 | 0 |
| RepositoryDoc | idem | artifact, semantics, template | 6 | 0 |
| RoleDoc | idem | artifact, semantics, template | 12 | 0 |
| StepDoc | idem | artifact, semantics, template | 5 | 0 |

(Conteo directo en deskops: 174 declaraciones de campo en 25 clases [V, AST]. `AtomQuestion` es un `StrEnum`, `LifecycleEdge/Table` son dataclasses auxiliares, no modelos.)

### 4.3 spec2viz, pron, IR

| Modelo | Base | Atributos | Campos | Validadores |
|---|---|---|---|---|
| spec2viz `sldb_models.py`: SpecDoc, SpecNodeDoc, NotationDoc, GlyphDoc, LinkDoc, PaletteDoc, ViewDoc | StructuredNLDoc | copia vieja de los de `sldb/models/notation` (13/11/12/17/17/9/9 campos; ViewDoc **sin** `params` ni `sections`) | 88 en total | 8 validadores "ausente→vacío"/ViewDoc básico |
| pron AnchorDoc | StructuredNLDoc | family `knowledge`, semantics | 5 | 0 |
| pron ExplanationDoc | idem | family `knowledge` | 4 | 0 |
| pron ProjectionDoc | idem | family `knowledge` | 13 | `exposed` before, `mutability` before |
| pron MoveDoc | idem | family `ledger` | 11 | 0 |
| pron ReadmeDoc | idem | family `knowledge`, **compositions**(`parts_render` de `parts`, modelo `pron.models:ExplanationDoc`, separador `\n\n`) | 2 | 0 |
| pron SpecDoc | idem | family `knowledge` | 2 | 0 |
| pron TheoremDoc | idem | family `knowledge` | 5 | 0 |
| IR SpecIRNodeDoc (base, no se registra) | StructuredNLDoc | authority `authored`, semantics | 7 | `_provenance` (after) |
| IR InterfaceControlDoc, InterfaceActionDoc, ApiOperationDoc, HandlerDoc, DomainOperationDoc, PolicyDoc, DomainEntityDoc, TableDoc | SpecIRNodeDoc | semantics (`type`,`layer`), template por helper `_template` | 2/1/2/2/1/1/1/2 propios (+7 base) | hereda `_provenance` |
| IR StateDoc | SpecIRNodeDoc | semantics + references `[entity]` | 1 (+7) | hereda |

Ninguno de los 49 consumidores declara `__family__` salvo pron, ni `__indexed__`, ni `__authority__` salvo IR [V].

## 5. Validadores (todos los encontrados)

### 5.1 Formato de un id o valor (cadena)
| Dónde | Qué impone |
|---|---|
| `models/doc_anchor_value.py:52` `check_anchor_fields` (en `StructuredNLDoc._doc_anchor_forms`) | cada valor de campos `__doc_anchors__` y de `Provenance.from_` con prefijo `doc:` coincide con `doc:Modelo:nombre[@sha256:<64 hex>]` |
| `AnchorDoc.kind/ref` (sldb, `pattern=`) | `kind` en {model,doc,relation,operation,projection,expr}; `ref` con prefijo tipado (`model:`, `doc:`, `edge:`, `op:`, `fields:`, `view:`, `expr:(…)`) |
| `KnowledgeTag` (`models/knowledge_tag.py`) | `^[a-z][a-z0-9_]*:[a-z][a-z0-9_.-]*$` |
| deskops `AtomDoc._id…` y `ProtoAtomDoc._id…` | id `atom-<slug>` / `proto-<slug>` donde el slug ya está en forma `slugify` (importa `deskops.doc_access` perezoso) |
| deskops `AtomDoc/ProtoAtomDoc._tags_are_namespaced` | cada tag `namespace:valor`; `domain:` exige ruta de dominio (minúsculas con puntos); resto sin segmentos vacíos |
| deskops `CrossroadDoc._path_is_a_domain_path` | `path` = segmentos en minúscula con puntos |
| deskops `ReviewDoc` `pattern=` (3) | `id` `review-<tarea>-<kind>-<n>`; `subject` `doc:Modelo:id[@sha256:…]` o vacío; `subject_ref` `<ruta>#node:<ID>` o vacío |
| `ViewParam._identifier` | `name` es identificador Python |
| `ViewEdge._named` | `relation` no en blanco |

### 5.2 Enum / vocabulario cerrado
| Dónde | Qué impone |
|---|---|
| `RelationTypeDoc` `Literal` | direction ∈ {directed, undirected}; cardinality ∈ 4 valores |
| `RuleDoc` `Literal` | severity ∈ {error, alert}; moment ∈ {on_write, on_transition, on_check} |
| `Provenance.authority` (`Authority` Literal) | 5 autoridades |
| `ViewDoc._direction` | both/outgoing/incoming |
| `ViewDoc._layout` | `layout.style` ∈ {None, "", layered, rings} |
| `ViewEdge._direction` | out/in |
| deskops `ReviewDoc` Literals | `kind` (7), `fact` (7 con vacío), `verdict` (4) |
| deskops task types | `task_type: Literal["task.xxx"]` por subtipo (fija el tipo) |
| IR `Origen`, `Verificacion` Literals | taxonomía de procedencia IR05 |

### 5.3 Cruza campos del mismo documento
| Dónde | Qué impone |
|---|---|
| `ViewParam._required_has_no_default` | un param no puede ser `required` y tener `default` |
| `ViewDoc._composed` (after) → `view_doc_checks` | nombre de vista ≠ `projection` (reservado); params únicos; keys de sección únicas; selectores solo con claves conocidas (`ref, prefix, path_glob, model, where, include_linked`); `include_linked` bool; resto de strings no vacíos; `{param}` usados en `select` están declarados |
| `ViewSection._select/_names/_stripped/_edges/_transitions` | `select` no vacío; listas de nombres no vacíos; `transitions` exactamente `{from_field, relation}` con ambos no vacíos; `key` no vacía |
| `ViewDoc._nodes` | cada entrada `{model, where}` con `model` obligatorio y sin otras claves |
| `RelationDoc.attributes` (after → `check_transition_attributes`) | `terminal` bool; `sets` mapa de escalares o listas de escalares |
| `RuleDoc._parses` | `condition` y `where` (si hay) parsean con el parser de predicados |
| `RuleDoc._is_a_family` | `subject` es `model <= X` o conjunción de ellos |
| deskops `CrossroadDoc._id_names_the_path` | `id` = `crossroad_id(path)` |
| deskops `CrossroadDoc._content_is_written` | `content` no vacío |
| deskops `ProtoAtomDoc._hangs_from_one_place` | a lo más un tag `domain:` |
| deskops `ReviewDoc._validate_review` | exactamente uno entre `subject`/`subject_ref`; `fact` solo y obligatorio para `kind: evidence`; accepted/rejected requieren `reason` y `disposed_by`; pending no tiene `disposed_at/by`; sha256 de 64 hex minúsculas o vacío; `run_dir` bajo `runs/` |
| deskops `TaskDoc._title_is_written` | `title` no vacío |
| IR `SpecIRNodeDoc._provenance` | `verificado` exige `verificado_por` con prefijo `human:`/`check:` y `evidencia`; cada nombre en `desconocidos` es campo existente y su valor es nulo/vacío |
| `StructuredNLDoc.__pydantic_init_subclass__` | todo campo tiene descripción no vacía (error al definir la clase, no al construir) |

### 5.4 Normalización "ausente es vacío" (compatibilidad hacia atrás)
`RelationDoc._absent_is_empty`; `SpecDoc._absent_text`; `SpecNodeDoc._absent_data/_absent_collection`; `LinkDoc._absent_label`; `ViewDoc._absent_notation`, `_absent_is_empty`; `ProjectionDoc._absent_is_off/_absent_is_content`; deskops `TaskDoc._apply_absent_frontmatter_defaults` (before, elimina claves None de 15 campos), `ReviewDoc._absent_evidence_fields_are_empty`; `ViewSection._edges`. Existen porque un campo `optrev` ausente se extrae como `None` (`core/data_extractor.py:72`).

### 5.5 Depende de otros documentos o de datos externos
| Dónde | Qué impone |
|---|---|
| deskops `TaskDoc._lifecycle_is_coherent` (after) → `task_lifecycle.lifecycle_errors` | (status, current_node, history) debe ser un estado producible por el grafo del ciclo de vida: lee los `RelationDoc` semilla `deskops/data/lifecycle/` y `spec/workflows/task_lifecycle.yaml` en tiempo de validación (lee archivos del paquete, no del store). Entra, transición o cierre (closed/superseded/resolved). |
| `RuleDoc` en escritura (`api/rules`, `refuse_unknown_family`) | que `X` de `model <= X` sea un modelo registrado: depende del store, no puede ser validador (`rule_doc.py` docstring) |
| Anclas `doc:` | existencia y hash del destino: se fija al guardar y se reporta con `check_doc_anchors`; fuera del modelo |
| `ViewDoc`: que una regla de `sections[].rules` exista | se comprueba en `run_view`, no en el modelo |
| `ViewSection.select.where` | `parse_predicate` (importa de `store.graph.language`, import perezoso en `view_doc_checks._parse`) |

### 5.6 Serializadores
deskops `TaskDoc._omit_unset_optional_fields` (wrap): omite `from_drawer` y `closeout_evidence` cuando están vacíos para mantener bytes idénticos; docstring aclara que el hash de contrato de clase sí los incluye. `ViewEdge._as_declared` (wrap): vuelca `key` como `as`.

## 6. Lo que no pude determinar y lo muerto, duplicado o a medias

Contradicciones doc/código [V]:
1. `docs/architecture/model-federation-on-the-graph.md` dice "Estado: propuesta, sin implementar", pero `inherit_model`, `ModelEntry.origin`, `store/model_origin.py` y sus tests existen (2026-10-18).
2. `docs/architecture/model-authoring.md §1` lista solo 5 atributos: omite `__doc_anchors__`, `__family__`, `__reference_models__`, `__indexed__`, `__contract__`, que sí lee el código.
3. `docs/sldb_model_lifecycle.spec.yaml` dice que promover "updates hash_a"; el código sube `version` y re-sella `contract_hash`/`model_hash` (`model_index_writes.py:_bump_version`, `_stamp_reindexed`).
4. `store/models/models_index.py:15` comenta que `contract_hash` firma "field schema and `__template__`"; hoy cubre también familia, semántica, bases, refs, contención, composiciones, `reference_models` (`hashing.py:89-94`). El comentario es de la fórmula vieja.
5. `deskops/deskops/models/task.py:37-38` dice "once sldb reads GO02 (e)": `__contract__` ya lo lee sldb (`contract_hash.py`, 2026-11-06).
6. `evidence-model-family.md` y `bootstrap-model-set.md` son diseño sin decisión del dueño; no hay modelos de evidencia ni los 7 del set bootstrap en código.

Duplicado/muerto/a medias:
- `spec2viz/spec2viz/sldb_models.py` (433 líneas) es una copia desactualizada de `sldb/models/notation/*` (ViewDoc de 9 campos contra 11). `spec2viz/sldb_sync.py:34+` todavía registra `spec2viz.sldb_models:*`; sldb lo redirige con `LEGACY_MODULE_PREFIXES` a `sldb.models.notation_models` (`model_reference.py:19`), así que el archivo de spec2viz no se importa por esa ruta [I].
- `__family__` se guarda, hashea y describe pero ninguna regla/consulta de sldb la usa; solo pron (`@family/…`, `pron/mcp/family_selector.py`) [V por el doc; no abrí pron].
- Parámetro `actor` de `add_model`, `remove_model`, `reindex_model`, `inherit_model`, `create_model`: aceptado y ignorado (diario retirado 2026-11-04) [V].
- `canonical` (`ModelEntry`, `ModelsIndex`, `add_model(canonical=…)`): solo vi que se guarda y se describe; no encontré quién lo consume [I].
- `sldb.models.notation_models` y `sldb.models.knowledge_surface` son fachadas de reexportación.
- `AnchorDoc` existe dos veces con campos distintos: `sldb/models/anchor_doc.py` (symbol, kind, ref, motive…) y `pron/models/anchor.py` (symbol, forms, ref, steps, motive). `SpecDoc` también (sldb notation vs pron) [V]. Mismo nombre de clase en un store compartido colisiona en el grafo (un nodo por nombre, `model_origin.py` docstring).
- `ViewEdge`/`ViewSection`/`ViewParam` y `Provenance` son `BaseModel`, no documentos; `diagram_specs/*` también.
- El contrato guarda `str(info.annotation)`; esa cadena puede cambiar entre versiones de Python/pydantic (hay `__pycache__` de 3.10, 3.12 y 3.13 en el árbol) y movería el hash sin cambio de clase [I, no lo probé].

No determiné:
- Detalle del lenguaje de marcadores de plantilla (`core/`, `runtime/validation.py`, `core/renderer_engine/*`): lo vi solo por referencia; es el núcleo del round-trip y merece su propio inventario (otro revisor).
- Cómo `rules_on_write` aplica reglas `on_write` junto a `__doc_anchors__`: no abrí `api/rules/rules_on_write.py`.
- Tests dedicados de `CodeAnchorDoc` / `code_ref` y de `transition_attributes` (no aparecieron por nombre en mi grep de `tests/`).
- Si los `RuleDoc`/`RelationTypeDoc` seeds de deskops y IR dependen de `model <= …` de una familia registrada en los stores reales; solo vi que el código lo exige.
- Dos campos con `pattern=` en `AnchorDoc` de sldb: no lo cruzé con el uso en `docs/atoms`.
- `git log` solo muestra el último PR por área; para fechas exactas por función usar `git log -S`.
