# sldb: superficies (CLI, HTTP, MCP, fachada Python), selfdoc, contratos y crecimiento desde 2026-10-01

Revisor 7 de 7. Repo leído: `/home/jp/proyectos/SLDB_1/sldb` (solo lectura). Marcas: [V] verificado en el archivo citado, [I] inferencia mía.
Rutas relativas al repo sldb salvo que diga otra cosa. Versión: `pyproject.toml` dice `0.1.13` [V], paquete PyPI `structuredLanguageDB`, import y CLI `sldb`, `requires-python >=3.12` [V]. Extras: `click`, `mcp` (`mcp>=2.2`), `code` (tree-sitter) [V pyproject.toml líneas 36-46].

---

## 1. La CLI completa

### 1.0 Mecánica común [V salvo nota]

- Entrada: `src/sldb/cli/main.py` -> `CLI().run(argv)` (`dispatcher.py`). El árbol de argparse se arma en `src/sldb/cli/parsers/core.py` sumando 10 grupos de parsers; cada comando tiene un handler en un dict (`dispatcher.py`), y `tests/test_cli_every_command_has_a_handler.py` existe para que ninguno quede sin handler [V nombre del test; I que lo garantiza].
- Flags globales: `--version` (imprime `sldb <versión> (<git describe> at <checkout>)`, sale 0; `parsers/core.py`), `--actor <id>` (lo saca `main._take_actor` de cualquier posición de argv y fija quién firma toda escritura, `sldb.api.actor.set_actor`), `--envelope` (ver 1.2), `-h/--help` (imprime `SHORT_ARGPARSE_HELP` curado).
- Exit codes del modo plano [V main.py, dispatcher.py]: handler devuelve 0/1/otros; `SLDBError` -> `SystemExit(msg)` = exit 1 con mensaje en stderr; cualquier otra excepción -> `Unexpected: ...` exit 1; error de argparse -> el modo plano devuelve el código de argparse (2); comando sin handler imprime `Unknown command` y devuelve 2.
- La mayoría de los comandos de store aceptan `--store PATH|alias` (si falta: store ancestro más cercano, `store_context.get_store_context`) y `--pythonpath` (donde importar los modelos). `--format text|json|yaml` está definido una sola vez en `cli/output_format.py` (`add_format`, `emit`) pero varios parsers todavía lo declaran a mano [V].
- Alertas: toda finding sale por `cli/alert_sink.py` (`report`/`tally`): línea de texto, anotación `::warning`/`::error` bajo GitHub Actions, y se guarda para `warnings` del envelope. `sldb.core.finding_route` conecta las alertas que emite la librería (p. ej. `on_write`) al sink [V].

### 1.1 Árbol de comandos

Convención de salida: "0/1" = 0 ok, 1 falla/hallazgo con error; "J" = soporta `--format json` (y yaml donde el parser lo dice).

#### Sin store (modelo directo)
| Comando | Flags | Qué hace | API/función | Salida / exit |
|---|---|---|---|---|
| `extract MODEL INPUT OUTPUT` | `--format json\|yaml`, `--pythonpath` | Markdown -> datos según un modelo `module:Class` | `BasicCLI.extract` (core extractor) [V basic.py] | archivo; 0 |
| `render MODEL INPUT OUTPUT` | `--pythonpath`, `--ref-root` | datos -> Markdown | `BasicCLI.render` | archivo; 0 |
| `validate MODEL` | `--input`/`--data` (excluyentes, uno obligatorio), `--format text\|json\|yaml`, `--pythonpath` | prueba de idempotencia extract/render | `validate_model_input_roundtrip`/`validate_model_data_roundtrip` | 0 válido, 1 no [V basic.py] |
| `init [PATH]` | `--force` | crea el archivo skill del repo | `InitCLI.init` | 0 |
| `example [PATH]` | | desempaqueta el ejemplo incluido | `InitCLI.example` | 0 |
| `help [topic]` | | ayuda curada (`help_texts_*.py`) | `HelpCLI` | 0 |
| `faq [question]` | `--format`, `--faq-path docs/faq.md`, `--store` | navega `docs/faq.md` por pregunta | `FAQCLI` | 0; 1 si no encuentra [V faq.py] |
| `explore TERM` (y `docs explore`, duplicado) | `--source all\|docs\|docstrings`, `--regex`, `--docs-root`, `--code-root`, `--max-results 20`, `--format`, `--store` | busca en docs Markdown y docstrings de Python | `ExploreCLI` | 0 con hits, 1 sin hits [V explore.py:23] |
| `inbox [message]` | `--kind unclear\|suggestion`, `--title`, `--desk-root`, `--store`, `--pythonpath`, `--author cli`, `--list`, `--show`, `--limit 20`, `--format` | escribe/lista/muestra notas del inbox del desk | `InboxCLI` | 0; 1 error [V] |
| `lint [target]` | `--repo-root`, `--format` | lintea rutas canónicas en referencias de conocimiento; ruta bien formada pero inexistente = alerta `lint/lost`; mal formada = error | `lint_cli` (`commands/lint.py`) | 0 con alertas, 1 con error |
| `bench` | `--desk NAME=PATH` (repetible, obligatorio), `--ops`, `--modes cold,warm`, `--repeat 1`, `--timeout 7200`, `--touch`, `--sibling`, `--workdir`, `--keep`, `--deskops`, `--output`, `--format text\|json` | benchmark fijo de velocidad: copia el árbol de cada desk y mide `--help`, `stores update` (igual/cambiado), `stores check`, `deskops doctor/status`, opcional `stores build --check`, en frío y en caliente, con `os.wait4` (pared, CPU, RSS) | `src/sldb/cli/bench/` (9 módulos) [V] | tabla Markdown o JSON |

#### `stores` (ciclo de vida y federación) [V parsers/stores.py; handlers `commands/stores.py`, `store_*.py`]
| Subcomando | Flags | Qué hace | API | Exit |
|---|---|---|---|---|
| `init` | `--path .`, `--force` | crea `.sldb`, añade `.sldb_cache/` al `.gitignore`, registra en el catálogo global | `api.init_store` | 0 creado/ya existe, 2 el lugar lo rechazó (permisos), 1 falla a medias (`StoreInitError.precondition`) [V store_init.py] |
| `add PATH` | `--name`, `--store`, `--replace` | enlaza otro store (relativo a la raíz del proyecto; rechaza auto-enlace, alias fuera de `[A-Za-z0-9][A-Za-z0-9._-]*`, y `local`) | `api.link_store` | 0 |
| `remove NAME` | `--store` | quita el enlace; se niega si un modelo local hereda de él | `api.unlink_store` | 0 |
| `check` | `--store`, `--format`, `--quiet`, `--apply`, `--pythonpath` | diagnóstico de integridad: hashes, contrato de modelos, symlinks fuera de raíz, `.gitignore`, links rotos/ausentes, `store/doc-invalid`, `rewrite_loss`, historia git (`stale`/`unmatched`/`orphan`), reglas `on_check`, anclas doc | `api.check_store` / `all_findings` + `store.diagnose_store` | 1 si hay error; alertas solas = 0; `--apply` ejecuta lo que la política del store responde (`reindex`, `hook: <cmd>`) [V store_check_apply.py] |
| `update` | `--wait`, `--verbose`, `--prune-missing`, `--store`, `--pythonpath` | re-hashea y reconstruye índices derivados | `api.update_store_indexes` | 0 completo, 1 si hubo documentos saltados (faltantes/ilegibles) [V store_update.py:22] |
| `reconcile` | `--path .`, `--catalog`, `--apply`, `--format` | reconcilia el catálogo global `~/.sldb` con un subárbol | `store.catalog_reconcile.reconcile_catalog` | 0 |
| `semantic-map A B` | `--store` | declara conceptos semánticos equivalentes | `store.io` DAG semántico | 0 |
| `semantic-export` | `--store`, `--pythonpath`, `--format sldb`, `--encoding json\|yaml`, `-o`, `--rebuild` | exporta el contrato `sldb_kgdb_semantic_export` v1 | `store.export.export_semantic_payload` | 0 |
| `list` | `--store`, `--format` | lista los links y su estado (`ok`/`missing`/`not-a-store`) | `api.store_links` | 0 |
| `migrate-hashes` | `--store` | `hash_a..d` -> `store/model/source/content` | `api.migrate_store_hash_names` | 0 |
| `migrate-layout` | `--store`, `--force` | mueve `.sldb/runtime/` a `.sldb_cache/`, borra el journal, arregla `.gitignore` | `api.stores.migrate_layout` | 0 |
| `declare` | `--store`, `--write` | imprime (o escribe en `.sldb/store.yaml`) la declaración que el índice implica; conserva claves autoradas | `api.declare_store` | 0 |
| `build` | `--store`, `--check`, `--format` | reconstruye el store desde `store.yaml` + Markdown + modelos; `--check` reconstruye una copia scratch y falla si no pasa `stores check` | `api.build_store` con generadores de CLI (`run_generators`) | 1 si hay `undeclared`, `missing` o `include_conflicts` o si el scratch no pasa [V stores_build.py:48, stores_build_check.py:42] |
| `scaffold` | `--store`, `--apply`, `--format` | crea carpetas y semillas declaradas (`folders`, `seeds`) | `api.scaffold_store` | 1 si hay conflicto [V stores_scaffold.py:16] |

#### `models` [V parsers/models1.py, models2.py]
| Subcomando | Flags | Qué hace | API [I donde no hay cita] | Exit |
|---|---|---|---|---|
| `add MODEL` | `--store`, `--pythonpath`, `--canonical` | registra un modelo `module:Class` | `api.add_model` [V] | 0, 1 si no se encuentra |
| `update MODEL` | `--store`, `--pythonpath` | re-hashea y relee `__family__` | `api.reindex_model` [I] | 0 |
| `remove MODEL` | `--with-documents`, `--store`, `--pythonpath` | desregistra (no importa la clase); se niega si tiene documentos salvo `--with-documents` | `api.remove_model` [V models.py:53] | 0 |
| `inherit alias:Model` | `--store`, `--pythonpath` | hereda un modelo del store enlazado dueño (o convierte una copia local) | `api.inherit_model` [V] | 0 |
| `list` | `--store`, `--format` | modelos registrados | `store` índices [I] | 0 |
| `show MODEL` | `--store`, `--pythonpath`, `--format` (default yaml) | contrato + documentos del modelo | `api.describe_model` / `ModelDescription` [I] | 0 |
| `check MODEL...` | `--store`, `--format` | dice si cada modelo está registrado | `api.is_model_registered` [V models_check.py] | 1 si falta alguno |
| `validate MODEL` | `--store`, `--pythonpath`, `--promote`, `--backfill`, `--format` | valida el borrador (o el modelo activo); `--promote` lo instala; `--backfill` re-renderiza documentos | `api.validate_model_draft`/`promote_model_draft` [V models_validate.py] | 0 |
| `template show MODEL` | `--draft`, `--store`, `--pythonpath`, `--format` | muestra plantilla activa o borrador | `ModelSource` | 0 |
| `template edit MODEL` | `--input` (obligatorio) | escribe un borrador de plantilla | `api.edit_model_template` [V] | 0 |
| `fields add MODEL FIELD` | `--type` (obl.), `--description` (obl.), `--default`, `--store`, `--pythonpath` | añade un campo al borrador | `api.add_model_field` [V] | 0 |
| `fields remove MODEL FIELD` | | quita un campo del borrador | `api.remove_model_field` [V] | 0 |
| `create NAME` | `--template` (obl.), `--fields` (obl., YAML), `--output -`, `--stdout`, `--containment/--references/--compositions/--semantics` (JSON/YAML), `--authority` | genera código Python de un modelo `StructuredNLDoc` | generación en `commands/models_create.py` [I] | 0 |
| `metadata edit MODEL` | `--containment/--references/--compositions/--semantics/--authority`, `--store`, `--pythonpath` | edita metadatos de clase en el borrador (AST) | `api.edit_model_metadata` [V] | 0 |

#### `predicates`, `rules`, `tags`, `semantic`, `anchors`
| Comando | Flags | Qué hace | API | Exit |
|---|---|---|---|---|
| `predicates add NAME` | `--axis` (obl.), `--description`, `--store` | registra un predicado de links `[name:: [[x]]]` | `commands/predicates.py` | 0 |
| `predicates list\|show NAME\|validate [NAME]\|remove NAME` | `--store`, `--format` | CRUD de predicados | idem | `validate` 1 si hay errores |
| `predicates sync --file F` | `--prune`, `--store` | el store queda igual a un YAML versionado (`-` = stdin) | `predicates_file.py` | 0 |
| `predicates export` | `--file -`, `--store` | vuelca los predicados a YAML | idem | 0 |
| `rules check` | `--subject DOC`, `--store`, `--pythonpath`, `--format` | evalúa RuleDocs (y la familia `builtin:tag_namespace:*`); sin `--subject` solo `on_check` | `api.rules_check` [V rules.py] | 1 solo si hay un `error` |
| `rules explain RULE SUBJECT` | mismos | finding `{node, holds, clause, seen}` de una regla sobre un documento | `api.rules_explain`, `explain_exit` [V] | 0 cierto, 1 falso, 2 incompleto, 3 no aplica |
| `tags namespaces\|tree NAMESPACE\|check` | `--store`, `--pythonpath`, `--format` | namespaces declarados en `store.yaml`; árbol de un namespace (`roots`/`orphans`/`unplaced`); hallazgos de `builtin:tag_namespace:*` | `api.tag_namespaces`, `tag_tree`, `tag_check` (usa `tag_report`) [V tags.py] | `check` 1 solo ante error |
| `semantic parent add\|remove TAG PARENT` | `--store`, `--actor` (aceptado por compatibilidad) | declara/retira "TAG es un tipo de PARENT" | `api.add_semantic_parent`/`remove_semantic_parent` [V] | 0 |
| `semantic equivalent add LOCAL GLOBAL` | `--store` | mapea tag local a global (`gse.`) | `api.add_semantic_equivalence` | 0 |
| `semantic show TAG` | `--store` | padres, hijos, ancestros, equivalentes | `api.semantic_tag` | 0 |
| `anchors record REF` | `--write PATH`, `--claim`, `--name`, `--store`, `--pythonpath`, `--format` (default yaml) | lee lo que el símbolo `code:<ruta>::<nombre>` se ve ahora; con `--write` crea/re-graba un `CodeAnchorDoc` | `api.anchors.record_anchor`, `write_anchor` [V anchors.py] | 0 |
| `anchors check` | `--record`, `--store`, `--pythonpath`, `--format` | compara cada ancla de código con el código actual y suma `check_doc_anchors` (anclas a documentos); `--record` escribe las reconexiones | `check_anchors`, `check_doc_anchors` | 0 con solo alertas (`changed`, `reconnected`, `lost`, `renormalize`), 1 con error (unparseable, invalid, unavailable) |

#### `docs`, `fields`, `sections`, `find`, `ast`
| Comando | Flags | Qué hace | API | Exit |
|---|---|---|---|---|
| `docs create PAYLOAD` | `--model` (obl.), `-o` (obl.), `--name`, `--store`, `--pythonpath` | crea y trackea un documento | `api.create_document` [V doc.py] | 0 |
| `docs track [PATH]` | `--glob`, `--model` (obl.), `--name`, `--force`, ... | trackea un archivo (o un glob; los ya trackeados se saltan) | `api.track_document_file` | 0; batch 1 si falla alguno [V docs_batch.py] |
| `docs update DOC PAYLOAD` | `--format`... | reemplaza el contenido de un documento | `api.save_document_payload` [I] | 0 |
| `docs untrack DOC` / `docs delete DOC [--yes]` | | untrack (conserva el archivo) / untrack + borra el archivo (pide confirmación; stdin no interactivo = no) | `api.untrack_document` / `api.delete_document` [V] | delete 1 si no confirma |
| `docs show DOC` | `--format json\|yaml`, acepta `alias:ref` | AST + payload de un documento | `indexed_documents`, runtime doc [V docs.py] | 0 |
| `docs exists REF` | `--format text\|json` | resuelve la referencia desde los índices, sin importar modelos | `api.doc_status` [V] | 0 ok, 1 missing, 2 ambiguous, 5 link-missing/link-broken |
| `docs recover [DOC]` | `--all`, `--glob`, `--fail-on-custom`, `--depth`, `--links-only`, `--include-transclusions`, `--format` | resuelve `[[links]]` | `links.recovery` / `audit` | 1 con `--all/--glob` si hay rotos |
| `docs list` | `--format` | documentos trackeados | | 0 |
| `docs compose DOC` | `-o -`, `--format markdown\|json\|yaml`, `--render-links` | expande `![[transclusiones]]` | `links.composer` | 0 |
| `docs explore TERM` | = `explore` | | | |
| `docs history DOC` | `--since`, `--format` | versiones commiteadas en git: quién firmó, qué campos se movieron | `api.documents.document_history` [V docs_history.py] | 0 |
| `docs select` | `--ref --prefix --path-glob --model --where --one --include-linked`, `--format` | qué documentos nombra un selector | `api.select_documents` [V] | con `--one`, 1 si 0 o varios |
| `docs replace REF` | `--old` (obl.), `--new` (obl.), `--count 1`, `--store`, `--pythonpath`, `--format` | reemplaza exactamente N apariciones literales en un documento trackeado (validado) o en un archivo bajo la raíz | `api.replace_text` [V] | envelope: `validate/rejected` (3) si el conteo no coincide, `rules/violation` (1) |
| `fields show TARGET` | `--format json\|yaml` | esquema de campo de modelo (`models/<M>[/f]`) o valor (`docs/<Doc>/<f>`) | `commands/fields_show.py` | 0 |
| `fields query FIELD` | `--global`, `--format` | un campo en todos los documentos | idem | 0 |
| `fields create\|update\|remove\|append TARGET VALUE` | `--store`, `--pythonpath` | CRUD de campo (valor YAML/JSON inline) | `fields_save.py` -> `api.save_document_payload` [V import] | 0 |
| `fields clean TARGET` | `--dedupe`, `--drop-empty` | limpia una lista | | 0 |
| `sections show DOC` / `sections find TERM` / `sections fields TARGET` | `--format`, `--in semantic\|physical\|both`, `--global --regex --fuzzy --rebuild`, `--where` | contexto y navegación de secciones, búsqueda, campos de una sección | `commands/sections_*.py` | 0 |
| `find TERM` | `--in semantic\|physical\|both`, `--global`, `--regex`, `--fuzzy`, `--rebuild`, `--type all\|store\|model\|doc\|section\|field`, `--select`, `--where`, `--explain`, `--store`, `--pythonpath`, `--format` | recuperación unificada; `--where` usa el lenguaje de predicados completo (caminos sobre el índice) | `search_records` + `api.where.where_records` [V find.py] | 0 |
| `ast show [TARGET store]` / `ast schema` | `--store`, `--pythonpath`, `--format json\|yaml\|text` | IR normalizado de un documento / esquema de nodos y aristas | `api.document_ast` [V] | 0 |

#### Grafo y aristas
| Comando | Flags | Qué hace | API | Exit |
|---|---|---|---|---|
| `edges init` | `--store`, `--pythonpath` | registra `RelationTypeDoc`/`RelationDoc` y trackea los tipos builtin | `api.init_relations` [V edges.py] | 0 |
| `edges rebuild` | idem | pone al día el índice de aristas (solo reescribe lo que cambió) | `api.rebuild_edges` | 0 |
| `edges show NODE` | `--to`, `--relation`, `--holds-for DOC`, `--pythonpath`, `--evidence`, `--store`, `--local`, `--exclude-tag` | aristas que salen (o llegan) de un nodo; `--holds-for` evalúa condiciones y `effects` sobre el documento | `edges_from/edges_to/edges_holding`, `stamp_evidence` | 0; falla si NODE no es nodo y sugiere el export id |
| `edges check` | `--store`, `--local`, `--exclude-tag` | valida cada arista contra su tipo | `api.check_edges` | 1 solo con errores; stale = alerta |
| `edges unresolved` | `--format text\|json` | referencias declaradas que nombran nada | `api.unresolved_references` | siempre 0 |
| `edges referrers TARGET` | `--store --pythonpath --format` | quién referencia un documento o nodo del grafo de proyecto (campos y menciones en prosa) | `api.referrers` | 0 |
| `edges rewrite TARGET` | `--to NEW` \| `--remove` (obl. uno), `--dry-run`, `--store --pythonpath --format` | reescribe/quita todas las referencias, todo o nada, vía transacción | `api.rewrite_references` | 0; `--to`+`--remove` = uso incorrecto |
| `graph get NODE` / `list` / `query --query-file` / `neighborhood NODE [--depth 1 --direction incoming\|outgoing\|both]` | `--store --local --exclude-tag` | lecturas del índice de aristas | `api.graph.*` | get 1 si no existe |
| `graph path SRC TGT` | `--directed --all --cutoff --limit 10 --explain` + `--relation --all-relations --type` | cómo se conectan dos nodos; `--explain` da procedencia por tramo | `graph_path`, `graph_paths` [V graph_path_print.py] | |
| `graph cycles` (`--limit 10`) / `order` (`--layers`) / `components` (`--islands --isolated`) / `central` (`--kind pagerank... --limit 20`) / `similar NODE` (`--limit`, default relación `tagged_as`) | + flags de recorrido | análisis de grafo (networkx detrás de `GraphBackend`) | `graph_cycles/order/layers/components/islands/isolated/central/similar` [V graph_analysis.py] | `order` 1 si hay ciclo; `central` 1 si falta scipy |
| `graph snapshot save --output` / `load --input` | | exporta/lee grafo node-link JSON | `api.graph.snapshot` | 0 |
| `graph ingest-sldb --input --output` | | convierte un semantic export a node-link | | 0 |
| `graph project nodes [--kind]` / `edges --from ID \| --to ID` / `missing` | `--store`, `--format` | grafo de proyecto declarado en `store.yaml` | `api.project_graph` [V graph_project.py] | uso incorrecto si falta uno de from/to |
| `journal show\|verify` | `--store --limit --since --address --format` | RETIRADO 2026-11-04: imprime el aviso y sale 0 sea cual sea el argumento | `RETIRED_NOTICE` [V] | 0 |

#### Notaciones, vistas, selfdoc, servidores
| Comando | Flags | Qué hace | API | Exit |
|---|---|---|---|---|
| `notations list` | `--views`, `--store`, `--pythonpath` | notaciones (o vistas de proyección) del store y builtins | `list_notations` [V] | 0 |
| `notations sync` | `--notation`, `--palette`, `--spec` (rep.), `--catalog`, `--check`, `--prune`, `--strict`, `--json` | sincroniza notación/paleta/specs/`vistas.yml` al store | `api.sync_notation/sync_palette`, `SpecSync`, `sync_catalog`, `check_catalog` [V notations_spec_sync.py] | `--strict` 1 si falta una ref o hay errores de índice |
| `notations view SPEC` | `--projection` | resuelve un SpecDoc o una vista de proyección | `api.resolve_view`, `evaluate_projection_view` | 0 |
| `notations resolve` | `--notation` (obl.), `--docs` (obl., varios), `--relations` | estila documentos de cualquier modelo con una notación | `api.resolve_documents` | 0 |
| `notations from-graph` | `--snapshot` \| `--store` \| `--python` (obl. uno), `--node-type --model --relation --exclude-relation --drop-isolated --module-prefix --id --title --output` | compila un grafo o un árbol Python a un spec de componentes YAML | `component_spec_from_graph/python` [V notations_from_graph.py] | 0 |
| `views run NAME` | `--param K=V` (rep., ParamAction: malformado o repetido = error de uso), `--store --pythonpath --format` | evalúa una vista compuesta `{view, params, revision, sections}` | `api.run_view` [V views.py] | 0; 2/uso en envelope |
| `views list` | | vistas compuestas | `api.list_views` | 0 |
| `selfdoc scan\|sync\|check` | `--factory sldb.cli.parser:build_parser`, `--pythonpath`, `--store`, `--include-hidden`; sync/check: `--system sldb`, `--output knowledge` | reflexión de un parser argparse -> `CliCommandDoc` y `SurfaceDoc` | `ParserScanner`, `plan_documents`, `inspect_documents`, `write_documents` [V selfdoc.py] | JSON; sync/check 1 si `report.ok` es falso |
| `selfdoc python-scan\|python-sync\|python-check` | `--source-root src`, `--package`, `--store`, `--architecture-spec`, `--graph-output .sldb_cache/graphs/python-source.graph.json`; (`--kgdb-*` retirados, error explícito) | inventario estático de símbolos Python (AST, sin importar) -> `PythonSymbolDoc` + grafo fuente node-link | `PythonScanner`, `plan_python_documents`, `write_source_graph` [V] | scan 0; sync/check 1 si no ok o hay `metadata_errors` |
| `serve` | `--host 127.0.0.1`, `--port 8787`, `--store`, `--pythonpath`, `--cors`, `--token` (default `$SLDB_SERVE_TOKEN`) | servidor HTTP JSON (sección 2) | `ServeCLI` | corre hasta Ctrl-C, 0 |
| `mcp` | `--store` (obl.), `--level 0\|1\|2` (default 0), `--actor`, `--extension MODULE:FUNCTION` (rep.) | servidor MCP stdio (sección 2.2) | `sldb.mcp.server.serve` [V mcp.py] | 2 si falta el extra `mcp` (imprime cómo instalarlo) |

#### Superficies de compatibilidad (ocultas) [V parsers/hidden.py, legacy.py, singular_handlers.py]
- `legacy ls|get|glob|find|recover|compose` (direcciones crudas `st.{Model}.doc.field`, `se.tag`, `gse.tag`; `get` con `--format`, `find` con `--where` obligatorio, `recover` con `--depth/--links-only/--include-transclusions`, `compose` con `-o/--format`). Los alias de nivel superior `ls get glob raw-find recover compose` (ocultos) llevan al mismo handler.
- Grupos singulares ocultos `store` (init/add/check/update/semantic-map), `model` (add/update), `doc` (add/track/update/untrack): estaban parseados sin handler hasta 0.1.12 y el `singular_handlers.py` los reconectó. Duplican `stores`, `models`, `docs` con menos flags (p. ej. `store update` no tiene `--prune-missing`).

### 1.2 El envelope (`--envelope`, `docs/cli-envelope.md`, `cli/envelope.py`) [V]
- `sldb --envelope <comando> ...` ejecuta el comando con stdout/stderr capturados e imprime UN solo valor JSON: `{ok, command, store:{path}|null, revision:<store_hash>|null, data, warnings}`; en fallo `{ok:false, command, store, revision, error:{type:"ns/name", message}, exit, warnings}`. `command` = las primeras palabras antes de un flag (máx. 2). `data` = el JSON que imprimió el comando (hay que pasar `--format json`) o `{"text": ...}`.
- Un comando que corre y reporta hallazgos con exit != 0 es `<grupo>/failed` con su propio exit y su salida en `data`.
- Tabla de exits (`cli/error_types.py`): 0 ok; 1 `*/missing` (`fields/links/io`, `docs/missing`); 2 `*/ambiguous`; 3 `*/rejected` (`models`, `validate`, `docs`, `graph`); 4 `store/busy` (otro proceso escribe; `--wait` espera); 5 `store/error`, `markdown/runtime`, `sldb/error`; 6 `cli/usage` (argparse); 70 `cli/unexpected`. Aparte: `SLDBRuleViolationError` -> `rules/violation`, exit 1 (una escritura que una regla `on_write` rechazó).
- `warnings` = las alertas que el comando reportó (`{level, code, subject, what, fix, document}`), con ok true o false; nunca silenciosas (`alert_sink.kept()`).
- Opt-in: sin `--envelope` la CLI imprime y sale como siempre porque deskops y otros parsean esa salida. [V doc] El paso a envelope por defecto "se coordina por inbox con cada consumidor".
- Discrepancias doc/código [V]: (a) `docs/cli-envelope.md` no lista `rules/violation` (sí está en `error_types.py` y el CHANGELOG). (b) `envelope_store.py` resuelve el store con `open_store(arg)`, que ejecuta `migrate_store_layout` y `add_owner_import_roots` (lectura de `api/stores/open_store.py`): calcular `store` y `revision` para el sobre puede migrar el layout del store aunque el comando fuera de lectura [I: efecto lateral no documentado].
- `--actor` global lo toma `main._take_actor` antes de argparse; por eso el `--actor` propio del subparser `mcp` y de `semantic` nunca lo ve argparse [V main.py; I consecuencia].

### 1.3 `docs/architecture/current-cli-tree.md` frente a los parsers [V]
El documento está "Verified ... on 2026-09-13" y quedó viejo. Falta:
- `stores`: `remove`, `reconcile`, `migrate-hashes`, `migrate-layout`, `declare`, `build`, `scaffold`. El árbol lista `semantic-export` correctamente.
- `models`: `remove`, `inherit`, `check`, `metadata edit`, y las opciones `--promote/--backfill`.
- `predicates`: `sync`, `export`.
- `docs`: `exists`, `select`, `replace`, `history`.
- Grupos enteros ausentes: `rules`, `edges`, `graph` (con analysis, snapshot, ingest, project), `journal` (retirado), `semantic`, `anchors`, `notations`, `tags`, `views`, `bench`, `mcp`; y `selfdoc python-scan|python-sync|python-check`.
- `legacy`: el árbol dice `ls/get/glob/find`; el parser también tiene `recover` y `compose`.
- No menciona los flags globales `--version`, `--envelope`, `--actor`, ni los grupos ocultos `store/model/doc`.
- Cierra diciendo "The HTTP implementation exposes GET /health, /schema, /graph, /semantic-export, and POST /save": hoy son unas 45 rutas (sección 2.1).
- `docs/architecture/current-cli-tree.md` sí dice que los alias singulares "no son la superficie recomendada".

---

## 2. HTTP y MCP

### 2.1 `sldb serve` (rutas) [V `src/sldb/cli/serve/routes.py` + módulos de ruta]
Servidor `ThreadingHTTPServer` (`commands/serve.py`), JSON siempre (`Content-Type: application/json`), un handler ligado a un store resuelto una vez (`http_server.build_handler`). Auth opcional: con `--token`/`SLDB_SERVE_TOKEN` toda petición GET/POST debe traer `Authorization: Bearer <token>` (comparado con `hmac.compare_digest`), si no `401 {"ok":false,"error":"unauthorized"}`; OPTIONS (preflight CORS) está exento; sin `--cors` un OPTIONS da 404 `CORS disabled`. Errores no previstos: `SystemExit` -> 400, otras excepciones -> 500 `{ok:false, error}`. Método distinto de GET/POST/OPTIONS -> 405.

| Método y ruta | Parámetros | Respuesta / códigos |
|---|---|---|
| GET `/health` | | `{status: ok\|degraded, findings:[{kind,level,subject,explain,fix,needs_decision}], action}`; `degraded` solo ante un error (`health.py`) |
| GET `/schema` | | `{models:[{id, model_ref, fields:[descriptor+default], containment, references, semantics, template}], unavailable:[...]}` (un modelo que no importa va a `unavailable`, no tumba el listado) |
| GET `/graph` | | `{documents:[serialize_document...], unavailable:[...]}` |
| GET `/semantic-export` | | payload `sldb_kgdb_semantic_export` v1 |
| GET `/stores` | | `{store, root, model_count, doc_count, hash_a, stores:[{name,path}]}` (nota: sigue publicando `hash_a`, nombre que `migrate-hashes` retiró en el resto del sistema [V stores_routes.py; I] ) |
| GET `/stores/check` | | el resultado de `check_store` con verdicts por modelo/documento; 400 `SLDBError` |
| GET `/lint` | | `{problems:[{kind: store\|edge\|link, severity: error\|warning, doc, detail}], count}` (suma `diagnose_store`, `check_edges`, links sin resolver) |
| GET `/document`, `/document/ir`, `/document/segments` | `id` (obl.) | 400 sin id, 404 documento no trackeado, 422 si el IR no se puede construir; `/document` = `{id,model_name,path,payload,semantic_tags,ir}`; `/document/ir` = IR; `/document/segments` = `DocumentSegments` |
| GET `/fields` | `doc` (obl.), `path`, `index` | todos los campos con metadatos + `leaf_paths`, o un valor por camino con puntos; 404 doc/camino desconocido |
| GET `/sections` | `doc` (obl.) | `{doc, sections:[{path,title,breadcrumbs,semantic_tags,about,field_paths}]}`; 404/422 |
| GET `/ast` | `doc` (obl.) | IR completo (`document_ast`); 404/422 |
| GET `/extract` | `doc` (obl.), `format=json\|yaml` | `{doc, payload}` |
| GET `/render` | `doc` o `model`, `format=md\|html` | `{doc, format, markdown}`; con `model`, `{model, template}`. `html` devuelve el mismo Markdown (no hay conversor) [V docstring] |
| POST `/extract/segments` | body `{model, markdown}` | segmentos de extracción; 400 forma, 404 modelo no registrado/importable (`kind: model`), 422 markdown que no extrae (`kind: extraction`, `details`) |
| GET `/find` | `q` (obl.), `in=physical\|semantic\|both\|all`, `type=all\|store\|model\|doc\|section\|field`, `where`, `select`, `limit`, `regex`, `fuzzy`, `global` (1/true/yes/on) | `api.search` (resultados de `sldb find` en JSON); 400 parámetro inválido o `where` que no parsea. No expone `--rebuild` (el HTTP nunca muta) |
| GET `/similar` | `id` (obl.), `relations` (csv, default `tagged_as`), `limit`, `types` | `{similar:[[node_id, shared], ...]}`; 404 nodo desconocido |
| GET `/corpus/rank` | `q`, `k=5`, `threshold`, `among` (csv), `fields`, `refresh` | `{hits:[...]}`; 409 `kind: not_indexed` si el índice no existe y `refresh=0`; con `refresh=1` construye/refresca (lento) |
| GET `/edges` | `from` o `to`, `relation`, `include_linked` (default true); sin `from`/`to`: arista documento-a-documento, opcional `id=Model:name` | `{edges:[...]}`; 400 si falta; `include_linked` inválido 400 |
| GET `/edges/node` | `id` (obl.) | `{node}`, 404 desconocido |
| GET `/edges/nodes` | `type` (obl.) | `{nodes:[...]}` |
| GET `/edges/referrers` | `target` (obl.) | lista de `Referrer`; 404 desconocido, 409 ambiguo |
| POST `/edges/rewrite` | body `{target, replacement (string o null, obligatorio), dry_run}` | `RewriteReport`; 400 parámetro ausente/ inválido o escritura rechazada (nada escrito); 404/409 |
| GET `/graph/neighborhood` | `node` (obl.), `depth` | `{nodes:[...]}` |
| GET `/graph/path` | `source`, `target` (obl.), `explain` | `{path}` o con `explain` `{path, steps}`; 404 sin camino |
| GET `/graph/cycles` (`limit`), `/graph/components`, `/graph/central` (`limit`), `/graph/isolated` | | `{cycles}`, `{components}`, `{central:[{node,score}]}`, `{isolated}` |
| GET `/project-graph/nodes` (`kind`), `/project-graph/edges` (exactamente uno de `from`/`to`), `/project-graph/missing` | | mismo JSON que `graph project ... --format json`; 400 sin `project_graph` declarado |
| GET `/models` | | catálogo `{models:[{name, model_ref, path, version, canonical, family, semantics, documents}]}` |
| GET `/models/detail` | `model` (obl.) | `{ok, model: ModelDescription}`; un error de librería vuelve 200 con `{ok:false,error}` [V models_routes.py] |
| POST `/models/create` | `{name, fields, template, base, target_package, containment, references, compositions, semantics, authority}` | `{ok, ...registro}`; error de librería = 200 `{ok:false}` [V] |
| POST `/models/fields-add`, `/models/fields-remove`, `/models/template-edit`, `/models/metadata-edit`, `/models/validate`, `/models/promote` (`backfill`) | `model` + campos de cada operación | operaciones sobre el borrador (mismo contrato draft->validate->promote que la CLI) |
| POST `/docs` | `{model, name, payload}` | crea y trackea: `{ok, id, version}`; 400 body/nombre, 404 modelo no registrado, 409 nombre tomado, 422 payload inválido |
| POST `/docs/create`, `/docs/track`, `/docs/untrack` | `{model, name, path, payload}` | `{ok, doc}`; mismos códigos; todos bajo `SAVE_LOCK` |
| GET `/docs/select` | `ref prefix path_glob model where include_linked one` | `{selector, expect, documents}`; 404 desconocido con `one`, 409 ambiguo con `candidates`, 400 clave vacía/`where` inválido |
| POST `/save` | `{doc, payload}` (legado, un documento) o `{changes:[{id, action: create\|update\|delete\|move\|track\|untrack, model, payload, path, expected, expected_source_hash}]}` (máx. 500) | `{ok, saved}`; 409 conflicto (`expected` distinto del payload actual, "recarga"), 422 payload inválido, 500 `{rolled_back:true}` si falla al aplicar; atómico vía `api.apply_batch` |
| GET `/notations` | | notaciones y paletas builtin + del store |
| GET `/notations/view` | `spec` (obl.) | vista resuelta de un SpecDoc; 400 error |
| POST `/notations/resolve` | `{notation, docs\|doc_refs[], relations\|relation_refs, spec_style\|style}` | documentos estilados |
| GET `/views/projection` | | vistas de proyección guardadas |
| GET/POST `/views/projection/evaluate` | `view`/`ref`/`name` (GET) o body `view`/`ref`/vista inline | `{ok, ...}` |
| POST `/views/projection/save` | `{name, view}` | guarda una `ViewDoc` |
| GET `/views` | | `{views:[{name,title,params,sections}]}` |
| GET `/views/<name>` | `k=v` por cada param de la vista | JSON de `run_view` con **ETag = `"<revision>"`**; con `If-None-Match` que lo traiga (acepta `W/`, `*`) responde **304 sin cuerpo** y el ETag. 404 vista desconocida, 400 vista de proyección o param rechazado |

Observaciones [V salvo marca]:
- El único ETag/304 del servidor es `GET /views/<name>` (`views_routes.py`, `responses.send_json`). Nada más usa validación condicional.
- Concurrencia de escrituras: un `threading.RLock` (`SAVE_LOCK`) cubre `/save` y `/docs*`; el resto de las rutas de escritura (`/models/*`, `/edges/rewrite`, `/views/projection/save`) no lo toman [V: solo importan `SAVE_LOCK` `save_routes`, `docs_routes`, `docs_create_route`; I que las demás no lo hacen].
- CORS (`--cors`): `Allow-Origin: *`, `Allow-Methods: GET, POST, OPTIONS`, `Allow-Headers: Content-Type` (`responses.py`). `Authorization` no está en `Allow-Headers`: un navegador de otro origen no puede mandar el token Bearer tras el preflight [V código; I consecuencia práctica].
- Códigos de error no uniformes: algunas rutas dan 400/404/409/422 con `{ok:false,error}`, otras (`/models/detail`, `/models/create`, `/document` fallo de modelo) devuelven 200 o 422 con `ok:false`. Nada de envelope aquí: el servidor no usa `cli/envelope.py`.
- No hay ruta HTTP para `rules`, `tags`, `anchors`, `semantic`, `stores update|build|init`, `journal`, `selfdoc`, `predicates`. `sldb serve` no expone todo el CLI (lo dice el doc de 09-13, sigue cierto).

### 2.2 MCP (`src/sldb/mcp/`, `docs/architecture/mcp.md`) [V]
Capa de transporte (junto a `cli`, `selfdoc` en `contracts/layers.toml`), extra opcional `mcp`; solo los módulos de `server.py` importan la librería. Servidor `SignalServer(MCPServer)` nombre "sldb", transporte stdio; toda tool pasa por `guarded` (`guard.py`): fallo con causa, argumento desconocido = señal.

Niveles (acumulativos, fijados por el operador con `--level`, nunca por argumento de tool; `mutability.py`): 0 solo lectura, 1 contenido, 2 contenido + reglas como datos (`RelationTypeDoc`, `RuleDoc`). El nivel 3 (modelos como código Python) no se sirve nunca.

| Tool | Clase (anotaciones MCP) | Nivel | Parámetros | Qué hace |
|---|---|---|---|---|
| `list_models` | READ (readOnly, idempotent) | 0 | `offset=0, limit=50` | modelos registrados (propios y heredados), paginado |
| `describe_model` | READ | 0 | `model, offset, limit` | campos con tipos + una página de sus documentos |
| `list_relation_types` | READ | 0 | `offset, limit` | tipos de relación del índice de aristas con endpoints |
| `describe_relation_type` | READ | 0 | `name` | endpoints, dirección, cardinalidad, descripción |
| `read_document` | READ | 0 | `ref` (`Model:name` o `alias:Model:name`), `field` (camino con puntos) | payload o un campo |
| `select_documents` | READ | 0 | `model, where, ref, prefix, path_glob, include_linked=False, offset, limit` | selector (mismo que `docs select`) |
| `neighbors` | READ | 0 | `node, direction=outgoing, relation, offset, limit` | aristas que salen/llegan |
| `neighborhood` | READ | 0 | `node, direction=outgoing\|incoming\|both, depth=1, offset, limit` | ids de nodos a N saltos |
| `list_views` | READ | 0 | `offset, limit` | ViewDocs compuestas, de proyección y SpecDocs |
| `resolve_view` | READ | 0 | `view, params` | resuelve una vista a JSON |
| `check_integrity` | READ | 0 | `offset, limit` | hallazgos de `stores check` + índice de aristas, paginados |
| `list_signals` | READ | 0 | `signal_class, offset, limit` | lee `<proyecto>/.sldb_cache/signals.jsonl` |
| `propose_changes` | PROPOSES (readOnlyHint true, idempotent false) | 1 (2 si toca reglas) | `changes: list[Change]` con unión discriminada por `action`: `create {model,name,payload,path?}`, `update {model,name,fields: {camino: valor}}`, `delete {model,name}`, `assert_edge {name, source, target, relation_type, title?, condition?, attributes?, notes?, path?}`, `retract_edge {name}` | corrida en seco: arma el lote para `apply_batch` con `expected_source_hash` de cada documento tocado, lo sella (sello del servidor) y responde `would_write`, `checked_on_apply`, `pending`; no escribe |
| `apply_changes` | WRITES (destructive) | 1/2 | `proposal` exactamente como se devolvió (`{changes, level, store_hash, seal}`) | `apply_batch`, todo o nada; rechazos tipados: `stale` (cambió un documento guardado), `rules` (nombra los RuleDocs), `domain` (validadores de extensión), o el nombre de la excepción; devuelve `saved`, `documents`, `deleted`, `alerts`, `edge_findings`, `pending` |
| `undo_changes` | declarada, NO servida | | | llamarla da la señal `not_implemented` (catálogo `PENDING_TOOLS`) |

- Paginación: todo conjunto sale como `{items, total, offset, limit, truncated, next_offset}`, `DEFAULT_LIMIT=50`, `MAX_LIMIT=500`; un conjunto vacío puede traer `explanation` (`paging.py`).
- Señales (`signals/`): pedir algo que no existe no es error; se responde con `suggestions` (5 nombres de `api.matching.Matcher.rank`) y se anota una línea JSON en `.sldb_cache/signals.jsonl` (caché derivada, ni siquiera en nivel 0 escribe estado del store). Clases: `unknown_tool, unknown_model, unknown_document, unknown_field, unknown_relation, unknown_view, unknown_argument, not_implemented`. Sin rotación del log [V doc].
- Extensión: `build_server(store, level=0, actor=None, extensions=[fn(server, context)])` o `--extension modulo:funcion`. `ServerContext` expone `store_path, project_root, level, actor, log, batch_validators`; `server.add_tool(fn, name=..., annotations=...)` (o decorador `@server.tool()`) registra una tool que hereda guarda, paginado, actor y log. `context.add_batch_validator(fn(changes, context) -> [{code, level: error|alert, what, subject}])`: corre en `propose_changes` (sobre los cambios tipados) y en `apply_changes` (sobre los `BatchChange`); un `error` rechaza como `rejected: "domain"` sin escribir; las `alert` vuelven en `domain_alerts`. Contrato estable: `sldb.mcp.extension.Extension`, `BatchValidator`.
- Pendientes declarados en cada respuesta (`write/pending.py`): `dry_run` (propose no ejecuta la prevalidación real), `edge_validation_before_write`, `store_hash_precondition`, `guards_in_transaction`, `on_write_alerts`, `undo`. Discrepancia [V]: `write/apply.py:42` sigue emitiendo `PENDING["guards_in_transaction"]` en cada `apply_changes`, pero el commit `f8ad9000` (2026-11-16) puso la prevalidación y la transacción bajo el cerrojo del store con guardas `expected_source_hash` y `StoreIOLock` reentrante; el texto pendiente quedó obsoleto [I]. Y `undo` ya tiene su base (`BatchReport.inverse`, `83c60239`) pero la tool no se registró.
- Tests: `tests/mcp/` usa un cliente MCP real por stdio sobre una copia aislada de un store real [V doc/CHANGELOG].

---

## 3. La fachada `sldb.api` (`src/sldb/api/__init__.py`)

El `__all__` tiene **189** nombres sin repetidos [V: contado con `ast`], no 183. Los agrupo; cada nombre una línea (los parámetros finos están en el código de cada función).

**Abrir y leer stores**
- `open_store`: resuelve el argumento store (ruta, alias o nada) e inicia una operación; migra el layout si hace falta.
- `read_store`: igual pero solo lectura, nunca escribe, rechaza un store no preparado (`SLDBStoreError: store not prepared`).
- `store_is_prepared`: True si el store existe y su layout ya es canónico.
- `StoreLocation`: dónde vive un store ya resuelto (`store_path`, `project_root`).
- `init_store`: crea `<path>/.sldb`, ignora `.sldb_cache/`, registra en el catálogo.
- `link_store` / `unlink_store`: enlaza/desenlaza otro store (`replace=True`).
- `LinkedStore`, `store_links`: un store enlazado y la lista de enlaces directos con su estado.
- `check_store`: corre el chequeo de integridad del CLI y lo resume (dict `valid`, `errors`, `alerts`, ...).
- `update_store_indexes`, `StoreUpdateReport`: rehashea y reconstruye índices; qué hizo y qué saltó.
- `store_fingerprint`, `StoreFingerprint`, `DocumentHashes`: huella del árbol (raíz, hashes de modelos y de cada documento).
- `migrate_store_hash_names`: renombra `hash_a..d`.
- `build_store`, `scaffold_store`, `ScaffoldReport`, `Seed`, `StoreDeclaration`: aplican la declaración de `store.yaml` (modelos, links, generators, include, folders, seeds, tag_namespaces, tag_models, policy, graph_backend, project_graph).
- `TagNamespace`: lo que un store declara de un namespace de tags.

**Documentos: ciclo de vida y escritura**
- `create_document`: escribe el Markdown que un modelo renderiza desde un payload y lo trackea (evalúa `on_write`, pinnea anclas).
- `track_document_file`: indexa un Markdown existente como documento de un modelo.
- `untrack_document` / `delete_document`: quita del índice / además borra el archivo.
- `move_document`: mueve el archivo y re-trackea con el mismo nombre.
- `save_document_payload`: reemplaza el contenido desde un payload (evalúa `on_write`, pinnea anclas, sella procedencia).
- `replace_text`: reemplazo literal de exactamente N apariciones en un documento o archivo.
- `DocumentReference`: qué documento tocó una operación y dónde vive.
- `apply_batch`, `BatchReport`: prevalida y aplica un lote en una transacción (devuelve `saved` e `inverse`).
- `transaction`, `Transaction`, `RollbackError`, `ConflictError`: transacción multi-documento anidable con rollback de bytes, entradas, shards y dirty set de reglas.
- `deep_get`, `deep_set`, `deep_delete`, `ensure_list`, `leaf_paths`: acceso por camino con puntos a payloads.
- `extract_payload`, `extract_document_payload`: Markdown -> payload (de texto dado / de un documento trackeado).
- `render_document_markdown`, `render_model_template`: render de un documento / plantilla de muestra.
- `serialize_document`: forma JSON de un documento (la de `/graph`).
- `document_ast`: el payload de `ast show docs/<doc>`.
- `runtime_document`, `load_runtime_documents`, `RuntimeDocuments`, `InvalidDocument`: documentos cargados (`on_invalid="skip"` aísla los rechazados por su modelo).

**Selección, referencias y estado**
- `Selector`, `select_documents`, `AmbiguousSelectionError`: selector `ref/prefix/path_glob/model/where/include_linked`; ambigüedad con candidatos.
- `doc_status`, `doc_exists`, `DocRefStatus`, `tracked_documents`: estado de una referencia (`ok/missing/ambiguous/link-missing/link-broken`) desde los índices; versión en lote.
- `split_doc_ref`: separa `alias:ref`.
- `resolve_indexed_document`, `indexed_model_bases`, `AmbiguousDocument`: encuentra `Model:name` en la familia (submodelos) sin importar clases.
- `where_matches`, `WherePredicateError`: evalúa un predicado del lenguaje de reglas sobre un payload.
- `search`: lo que hace `sldb find`, serializado.
- `template_anchors`: por campo, el párrafo ancla literal de la plantilla del modelo.

**Modelos (registro, borradores, creación)**
- `StructuredNLDoc`: clase base de los modelos.
- `add_model`, `remove_model`, `inherit_model`, `reindex_model`: registro, retiro, herencia desde un store enlazado, re-hash.
- `registered_models`, `is_model_registered`: consulta de modelos registrados.
- `load_registered_model`, `resolve_model_ref`, `RegisteredModel`, `ModelRegistration`: cargan/resuelven la clase.
- `describe_model`, `ModelDescription`, `ModelDocumentSummary`, `ModelFieldSummary`: contrato y documentos de un modelo.
- `describe_field`, `describe_model_fields`, `FieldDescription`: descripción de campos pydantic para formularios.
- `create_model`, `FieldDecl`, `ModelCheck`: genera, comprueba, escribe y registra un modelo declarado como campos JSON.
- `add_model_field`, `remove_model_field`, `edit_model_template`, `edit_model_metadata`: edición por borrador.
- `validate_model_draft`, `promote_model_draft`, `DraftValidationReport`, `DraftDocumentCheck`, `ModelDraft`: valida/instala (con `backfill`).
- `ModelSource`, `locate_model_source`: archivo y clase que definen un modelo.

**Índice de aristas y grafo**
- `EdgeIndex`, `EdgeNodeRecord`, `EdgeRecord`, `EdgeRebuildReport`: el índice compuesto y sus registros.
- `load_edge_index`, `edge_node`, `edge_nodes_of_type`, `edges_from`, `edges_to`: lectura.
- `rebuild_edges`, `init_relations`, `RelationsInitReport`: reconstrucción / registro de tipos de relación builtin.
- `check_edges`, `EdgeCheckReport`: validación de aristas.
- `unresolved_references`, `UnresolvedReference`: referencias declaradas que no resuelven.
- `serialize_edge_records`, `serialize_edge_node_records`: JSON de aristas/nodos.
- `edges_holding`, `edges_blocked`, `effects_of`, `apply_effects`: aristas cuya condición se cumple sobre un sujeto, las bloqueadas por una regla `error`, y los efectos `sets/terminal` (transiciones como datos).
- `referrers`, `rewrite_references`, `Referrer`, `RewriteReport`: referencias entrantes y su reescritura (todo o nada).
- `project_graph`, `ProjectGraph`, `ProjectGraphConfig`: grafo de aristas declaradas del proyecto.
- ids de nodo: `anchor_node_id`, `bare`, `doc_node_id`, `field_node_id`, `kind`, `model_node_id`, `relation_type_node_id`, `section_node_id`, `tag_node_id`.

**Reglas, tags, semántica, anclas**
- `rules_check`, `rules_explain`, `rules_on_check`, `on_check_findings`, `transition_findings`, `refuse_unknown_family`: evaluación de RuleDocs por momento y rechazo de reglas con familia no registrada.
- `tag_namespaces`, `tag_tree`, `tag_check`, `tag_evaluations`, `tag_folder`: namespaces de tags (declaración, árbol, hallazgos, evaluación estructurada, carpeta de un tag).
- `add_semantic_parent`, `remove_semantic_parent`, `add_semantic_equivalence`, `semantic_tag`: DAG semántico.
- `check_doc_anchors`: alertas de anclas a documentos (`anchors/doc_*`, `anchors/provenance_*`).

**Segmentos y composición**
- `build_document_segments`, `DocumentSegments`, `Segment`, `FieldSegment`, `LiteralSegment`, `CompositionPiece`, `CompositionSpec`, `SourceRef`: render segmentado por origen de un documento.

**Notaciones y vistas**
- `SpecDoc, SpecNodeDoc, NotationDoc, GlyphDoc, LinkDoc, PaletteDoc, ViewDoc`: modelos de vocabulario visual (de spec2viz).
- `resolve_view`, `resolve_documents`, `evaluate_projection_view`, `list_projection_views`, `save_projection_view`, `sync_notation`, `sync_palette`: resolvedor de vistas/notaciones y sincronización al store.
- `list_views`, `run_view`: vistas compuestas con secciones y `revision`.

**Corpus de similitud**
- `Corpus`, `CorpusEntry`, `FieldEntry`, `FieldHit`, `Hit`, `FieldProjection`, `IndexProjection`, `field_id`, `split_field_id`: documentos/campos recuperables por similitud, índice y proyección que declara el consumidor (puerto `Embedder` inyectable; sin él degrada a difflib).

**Jornal (muerto)**
- `journal`, `verify_journal`, `JournalEntry`, `JournalVerifyReport`: cáscaras: el journal se retiró el 2026-11-04; `journal()` devuelve `[]` y `verify_journal()` un reporte válido y vacío (existen solo para importadores).

Sub-APIs que la CLI usa y la fachada **no** exporta [V imports en `cli/commands/*`]: `sldb.api.graph` (`graph_path/paths/cycles/order/layers/components/islands/isolated/central/similar`, `query_snapshot`), `sldb.api.anchors` (`check_anchors`, `record_anchor`, `write_anchor`), `sldb.api.documents.document_history`, `sldb.api.matching` (emparejamiento de átomos), `sldb.api.notations` (más de lo exportado: `SpecSync`, `sync_catalog`, `component_spec_from_graph/python`), `sldb.api.where.where_records/where_findings`, `sldb.api.actor`, `sldb.api.records`, `sldb.api.stores.declare_store`. Los consumidores los importan por ruta de módulo.

---

## 4. Autodocumentación, contratos y herramientas

### 4.1 `selfdoc` (`src/sldb/selfdoc/`, 31 módulos; CLI `selfdoc` y `cli/selfdoc_*.py`) [V]
Qué genera sldb sobre sí mismo (y sobre cualquier código):
1. Referencia de CLI: un parser argparse (`--factory module:callable`, por defecto `sldb.cli.parser:build_parser`; también `ClickScanner` para click, extra) se introspecciona sin ejecutar handlers (`parser.py`, `arguments.py`, `constraint.py`, `values.py`). Produce `CliCommandDoc` (uno por comando; `knowledge/commands/`: 130 hoy) y `SurfaceDoc` (`knowledge/surfaces/`: 32). `sync` los escribe/actualiza y registra modelos y documentos; `check` informa deriva sin escribir. Posee: identidad, sinopsis, argumentos, procedencia (`contract-sha256` de los hechos del parser). Lo autoral (`purpose`, `how_it_works`, ejemplos, tags) se preserva; lo nuevo se marca `Not documented.`. Comandos obsoletos se reportan, no se borran. Hidden commands omitidos salvo `--include-hidden`.
2. Inventario de símbolos Python: `PythonScanner` (AST estándar, sin importar módulos) -> `PythonSymbolDoc` (`knowledge/symbols/`: 2561 hoy) con id `python:<módulo>:<qualname>`, span, firma, docstring, imports, hash de archivo. `python-sync` además escribe el grafo fuente node-link (`.sldb_cache/graphs/python-source.graph.json`, `source_graph_file.write_source_graph`) con contención, imports (siguiendo cadenas de reexport), referencias de anotación, contexto del import (`type_checking`, `conditional`, `try`), sombreado, y tags locales y efectivos heredados por la cadena léxica (`python_tag_inheritance.py`). Metadatos de docstring: bloque YAML estricto `---` (`id`, `kind`, `tags`) (`docstring_metadata.py`); errores van a `metadata_errors` y `python-sync` no escribe mientras haya. `purpose` se genera del primer párrafo de la docstring si es placeholder (`python_purpose.py`); conflictos con un `purpose` autoral van a `purpose_conflicts`. `python-check` reporta `semantic_gaps`, `undeclared`, `unexplained`, `unresolved_references`, candidatos de renombre (solo pistas), `graph_stale`. Colisiones de id por mayúsculas/minúsculas (`transaction` vs `Transaction`) se desambiguan con sufijo de tipo (`python_doc_ids.py`).
3. `citations.py`: extrae las citas `spec NN` de docstrings para que un consumidor construya aristas `implements`.
- El árbol de qué hace y qué verifica (`docs/self-documentation.md`): una sync repetida con entradas vigentes no reescribe nada; el CI de la rama revisa la referencia con Python 3.13 y `selfdoc check` está dentro de `tools/check.sh`.
- `knowledge/` (salida) no es el proyecto `knowledge` sucesor; `identity.toml` lo reconoce como colisión de nombre y sugiere renombrarlo [V].
- El escáner tree-sitter vive ahora en `sldb.code`, antes en `selfdoc/treesitter` (commit `d683c5b4`, luego movido) [V CHANGELOG/estructura; I el movimiento].

### 4.2 Contratos (`contracts/`) [V]
- `layers.toml`: orden de dependencia entre paquetes y, en la misma archivo, ejes de capacidad. Niveles: 0 núcleo (`core`, `models`, `runtime`, `code`, `notations`), 2 persistencia (`store`, `links`), 3 operaciones (`api`), 4 transporte (`cli`, `selfdoc`, `mcp`), 5 derivados (`examples`, `assets`, `templates`). No existe el nivel 1 (hueco en la numeración). Regla: un paquete importa de su capa o de una más baja. `[allow].upward` autoriza `selfdoc->cli` (R5) y `models->store` (R6); `[[pending]]` R1, R3, R5, R6, `[[done]]` R2, R4. Ejes (`[[axis]]`) con ruta, dependencia externa confinada (`markdown-it-py`, `jinja2`, `networkx`), si es de identidad, `extractable`; deuda `[[axis_boundary_debt]]` del eje `grafo`.
- `identity.toml`: qué es el repo, en términos que permiten rechazar. Enunciado: sistema para representar cualquier cosa con estructura derivable determinista como documentos direccionables, consultables, anclados y reversibles a su superficie; superficies autoradas (`markdown`) y derivadas (`python-ast`, `argparse`, `click`). Grupos: producto (paquetes y modelos listados), autoaplicación (directorios `.sldb knowledge desk ledger runs contracts`), ajeno (`surface/`, `sldb/`), `[[orphan]]`, `[[boundary_debt]]` (`cli/commands/lint.py` nombra `desk/`; `cli/selfdoc_inspect.py` nombra `knowledge/`), `[[rejects]]`, `[surface]` (53 símbolos pedidos por consumidores `deskops, spec2viz, hum-scrapper, ontology`), `[status] state = "activo"` resuelto 2026-10-03.
- `integration.contract.yaml` (29 líneas): exporta dos contratos de datos, `sldb_document_payload` (`contracts/schemas/sldb_document_payload.schema.json`: `node_id`, `body`, `metadata`) y `sldb_kgdb_semantic_export` v1 (esquema + fixture `contracts/fixtures/sldb_kgdb_semantic_export.v1.json`: `contract, producer, store, models, documents, sections, semantic_dag`). `consumes: []`. El nombre `kgdb` en el contrato de datos se conserva congelado a propósito (`tests/store/test_export_contract_frozen.py`).
- `contracts/queries/sldb/*.json`: cinco consultas `StructuredQuery` de ejemplo (`scope` + `filters[{facet, where}]`), que usan ya `where` (GO10).

### 4.3 `tools/` [V cabeceras; cuerpo no ejecutado]
`tools/check.sh` es la única entrada: puertas en orden de costo. Rápidas: núcleo aislado (importar `sldb.core.renderer` no arrastra `store/api/cli`), capas, identidad, ejes, superficie. Lentas (`--fast` las omite): afirmaciones, pytest, `python -m sldb selfdoc check`.
| Puerta | Qué rechaza |
|---|---|
| `tools/check_layers.py` (173 l.) | import que cierra un ciclo entre paquetes; import que sube de capa; paquete sin capa. Ve solo imports entre paquetes, no `importlib`. `--graph` imprime el grafo |
| `tools/check_identity.py` (221 l., lee `git ls-files -z`) | modelo de autoaplicación importado desde un paquete producto; paquete sin grupo; directorio de primer nivel versionado sin clasificar. `--show` |
| `tools/check_axes.py` (369 l.) | ruta de eje inexistente; archivo de `src/` sin eje; dependencia externa confinada que se filtró; eje `extractable` que importa de fuera. `--map` |
| `tools/check_surface.py` (179 l.) | símbolo que un consumidor importa y no está declarado; consumidor importando un nombre privado o de `cli/`; cifra del contrato desactualizada. Se salta sola si los consumidores no están en disco (dice que se saltó). `--report` |
| `tools/check_claims.py` (231 l.) | afirmaciones de la documentación: ruta que no existe, `sldb.<paquete>` inexistente, bloque de comando con subcomando inexistente, cifra marcada verificable que no coincide |
| `tools/generate_vistas_html.py` (66 l.) | genera un HTML de navegación de vistas desde un catálogo `vistas`; no es una puerta |
CI: `.github/workflows/ci.yml` y `publish.yml` (PyPI con trusted publishing por tag `vX.Y.Z` o a mano) [V nombres; CHANGELOG/log para el contenido].

---

## 5. Lo que entró desde 2026-10-01

El repo tiene **627 commits** desde esa fecha [V `git log --since`], con picos: 150 el 2026-10-18, 149 el 2026-11-06, 64 el 2026-11-16, 54 el 2026-11-11. Mucho es del desk (cierres, preflights, notas de inbox con `[skip ci]`); abajo solo lo que cambió el producto. Código/plan entre paréntesis; SHA solo donde ayuda. Archivos: carpetas bajo `src/sldb/` salvo marca.

### 5.1 Línea de tiempo por tema

**A. Referencias entre stores, modelos heredados, links** 
- 10-16 `9dca856e` `alias:ref` en docs show / sections / serve y `docs exists` + `api.doc_status|doc_exists` (0.1.2); `stores list` reporta el estado del link. Archivos: `api/stores`, `cli/commands/docs_exists.py`.
- 10-18 `0463a9b6` modelos definidos una vez e heredados (`models inherit`, `api.inherit_model`, 0.1.11); `691f1ae3` `stores remove`, `stores add --replace`; `9675fd8d` referencias declaradas resuelven ids sin calificar y paths (`__reference_models__`, `unresolved_references`, `edges unresolved`, `EDGE_INDEX_VERSION` 4; 0.1.12); `b42a18b0` un cambio de contrato reconstruye aristas.
- 11-05 `5e5a7071`, `9dc1bf1e`, `29dde3eb`: `resolve_indexed_document` y bare ids encuentran submodelos (`indexed_model_bases`).
- 11-10 `8278d2a3`, `a9562c25`: link ausente = alerta `build/link-missing` y `store/link-absent`.
- 11-10 `84eb7f75` rule-graph local (no compone la federación entera si no hace falta; deskops `show task` 29,8 s -> 1,4 s).
- 11-11 `a7355d19` dbltrack (una ruta nombrada por varias reglas `include` se trackea una vez; `store/include-conflict`).

**B. Store derivado, layout y declaración** 
- 10-19 `7633cf44` `stores declare` y `stores build` (+ `--check`); `d2e6da06` "lo derivado del store sale de git" (build!); `243a3257`, `fadd3b59` declaración sigue al tracking; 10-29 `fa408854` `declare --write` conserva globs autorados.
- 11-04 `c420809c` split `.sldb/` versionado y `.sldb_cache/` ignorado, journal retirado, `stores migrate-layout` (decisión del dueño 2026-10-29).
- 10-18 `beb8a99c` el store solo importa el resto por tres costuras (`store/exceptions.py`, `runtime_codec.py`, `model_contract.py`), `tests/test_store_seams.py`.
- 11-06 `96d7b823` GD26 `scaffold_store`, `folders`, `seeds`; `502617d6` registrar/heredar modelo lo declara en `store.yaml`.
- 11-11 H5 (`29ed522b`) `declare --write` conserva todas las claves autoradas; 10-18/10-20 stores check mejoras: `stale/unmatched/orphan` con historia git (`f0d3978f`), `rewrite_loss` (`06516509`), gitignore (`f9c0babf`), cache de escaneos (`65fe325d`: 43 s -> 23 s frío, 7 s caliente), `store/doc-invalid` (`9668e881`, H1-H3).

**C. Procedencia, historia y anclaje** 
- 10-20 `ede2d686` cada escritura firma el documento (`sldb: by/at/via/authority`; `api/actor.py`, `models/provenance*.py`); `953f283e` el índice guarda el bloque; `1283062d` `docs history`; `72b7f194` política: cada hallazgo con la autoridad del documento y la política del store.
- 10-19/20 `ede2d686`...`52f63690` + `d683c5b4`: **anclaje a código** (`CodeAnchorDoc`, `code:<ruta>::<nombre>`, `sldb anchors record|check`, escáner tree-sitter de Python con fingerprints de docstring/cuerpo/firma). Docs `anchoring.md`, `code-anchoring.md`.
- 11-06 GO07 `4d521da8` anclas a documentos con hash de contrato (`__doc_anchors__`, `doc:Model:name@sha256:...`, estados `intact/changed/lost`, `check_doc_anchors`).
- 11-16 `600b51a9` (B2, parte TS; IR08, nota 20261115-022446) `sldb.code` lee TS/TSX/JS/JSX con tree-sitter-typescript (extra `code`): `code/lang_ts.py`, `ts_readers.py`, `ts_fingerprints.py`, `files.py`, `runtime.py`, `api/anchors`, `models/code_ref.py`, `uv.lock`. SQL va en tarea hermana (pendiente).

**D. Lenguaje de reglas, evaluación y transiciones (plan `grafo-operable`, GO01-GO10, entregado 2026-11-06)** 
- GO01 (11-04) frontera: sldb evalúa reglas declaradas, los consumidores las autoran. GO02 (`043282c3`) `docs/architecture/rules-language.md`. GO03 (`804f1ad4`) `GraphBackend` sobre networkx (`graph_backend` en `store.yaml`). GO04 (`d7eefdeb`) payload proyectado al grafo, staleness, `EDGE_INDEX_VERSION` 5. GO05 (`8be66bde`) lenguaje de predicados unificado (`store/graph/language/`: 27 archivos; `find --where --explain`). GO06 (`622da0b5`) `RuleDoc`, `rules check|explain`. GO07 (ver C). GO08 (`3af872e7`) `on_write`, `on_transition`, `on_check`, caché `.sldb_cache/rules/`, `SLDBRuleViolationError`, `core/finding_route.py`. GO09 (`33b3c7a0`) transiciones como datos de la arista (`sets`/`terminal`, `effects_of`, `apply_effects`). GO10 (`1e6ecd55`) retiro de `where_parse`/`FieldCondition` (cambio de contrato de `graph query`).
- 10-16 0.1.6 `fae6fa5e` grafo como máquina de estados (`where_matches`, `edges_holding`, `edges show --holds-for`) — precursor.
- 11-11 H4 (`bb0e0e9a`/`106e9a7a`) `create_document` y `track_document_file` evalúan `on_write`; H6 `tag_evaluations`; H7 guía.

**E. Plan `generalizar-desde-deskops` (GD21-GD27, entregado 2026-11-06)** 
- GD21 `a2a0174f` grafo de proyecto declarado (`ProjectGraph`, `/project-graph/*`, `graph project`); GD22 `2570b710` `referrers`/`rewrite_references`; GD23 `850f8fca` `select_documents` + `docs select` + `/docs/select`; GD24 `0cbd307e` `transaction`, `move_document`, `apply_batch`, `/save` atómico (cambio de contrato DA2); GD25 `05b4135f` vistas declaradas `ViewDoc` con secciones, `run_view`, `views run|list`, `GET /views/<name>` con ETag/304; GD26 `96d7b823` scaffold; GD27 `fb733e86` `tag_namespaces`, `tags namespaces|tree|check`, `replace_text`, `docs replace`, `sldb.api` reexporta `sldb.api.semantic`.
- Seguimientos de deskops: H9 (`04a0e94e`, 11-11) `run_view` y `list_views` encuentran ViewDoc de stores enlazados; GD27-para-deskops (`3be74f5b`) `anchored_by.value: bare` y `tag_models`; H11 (`47b75674`, 11-14) `run_view` lee solo el store que llama + `.sldb_cache/composed/` (7,5 s -> 1,4 s); `referrers`/`rewrite_references` con grafo en proceso, `excluded_sources` (11-16 `506406e8`, H-a..H-c de CC35c); `template_anchors` (`4206cfe8`); `create_document` pinnea anclas (`477d1bc0`); rollback restaura dirty set de reglas (`5d4e0771`, hallazgo de CC32).

**F. Absorción de spec2viz (SV01-SV04, 11-06; D1-D4 y B1-B4 de 11-15; IR11-IR13)** 
- SV01 `6b80df36` modelos `SpecDoc, SpecNodeDoc, NotationDoc, GlyphDoc, LinkDoc, PaletteDoc, ViewDoc` y `sldb.notations` (8 notaciones builtin, paleta); SV02 `ddc049b4` resolvedor de vistas/notaciones en api, cli (`notations`) y serve (`/notations*`, `/views/projection*`); SV03 `ea85af56` transclusión de campos y segmentos ricos (`CompositionSpec.ref` `field|view|spec`); SV04 edición draft de `containment/references/compositions/semantics/authority`.
- D3 `e3da781f` modelos de spec por tipo de diagrama (`models/notation`: 37 archivos); D1 `96109856` `SpecSync` + `notations sync --spec/--catalog/--check/--prune`; D2 `c3316caa` spec de componentes desde grafo o Python (`notations from-graph`); D4 `a25874d0` emparejamiento de átomos (`api/matching`, 18 archivos); 11-16 `d5fe66e6` 91 pruebas portadas de spec2viz; `da98e7d6` oráculo D2 congelado; `7f3ca069` imports anidados; `eb643e87` `label_key`.

**G. Servidor MCP, deshacer y escritura segura (specIR: IR01-IR04b)** 
- 11-15 `ce0a854e` `sldb mcp` (A) migrado de specIR: 14 tools, niveles 0/1/2, señales, extensión; 11-16 `1a976fbb` gancho `add_batch_validator`; `f8ad9000` `apply_batch` bajo el cerrojo del store (guardas `expected_source_hash`, `StoreIOLock` reentrante; nota 022444); `83c60239` (B-undo, IR04b) `BatchReport.inverse` (deshacer = `apply_batch(report.inverse)`); `77b6dc34` `read_store`, `store_is_prepared`, `extract_payload`; `4253c7b9` carga tolerante `on_invalid="skip"` + `EdgeIndex.problem_details`.

**H. Rendimiento y seguridad** 
- Seguridad 11-11: `39221856` `live_stale` no abre fuera de la raíz ni sigue symlinks (`store/hashing.py: unreadable_reason`, `source_stat`); `a71a5ea8` (secrest) y `dba50c74` (secrest2) toda lectura/escritura de documento trackeado por la guarda (`store/source_guard.py`, `store/source_read.py`, `O_NOFOLLOW`); `tests/security/` con audit hook de `open` (12 archivos). Códigos `store/path-outside-root`, `store/symlink-not-read`.
- Rendimiento: 10-18 `9e8ec426` (re-parseos), `65fe325d` (check); 11-10 `84eb7f75` (rule graph); 11-14 H11; 11-16 `901f5361` `list_views` no federa toda la máquina (2-5 GB por proceso de `serve`) + `compose_edge_index` single-flight (`store/edge_index/compose_lock.py`); 11-15 `5f0d5a19` **V0 `sldb bench`**.

**I. CLI y empaquetado** 
- 10-12 `fc0e25e9` `docs recover --all/--glob`, `compose --render-links`, `docs track --glob`, `stores update --prune-missing`; `bb0c720d` `predicates export|sync`; `ad0f1dab` transclusión sin frontmatter y `![[doc#Sección]]`.
- 10-14/15 `b5f10515`, `b93ea4c5` publicar en PyPI como `structuredLanguageDB`. 10-19 `4a3860a0` piso Python 3.12.
- 10-19 `2065f0c5` `--envelope`; `7a6e48c7` `edges show --evidence`; 10-18 `b128e279` `--format` en `models show`, `models template show`, `docs update`; `35b79402` `models check`, `registered_models`; 11-16 `1577ff55` `--version`.
- 10-18 `ab78747c`, `52553cde` kgdb se integra a sldb (nada invoca un kgdb externo; `export_kgdb_semantic_payload` -> `export_semantic_payload`; `--format kgdb` -> `sldb`; ruta `/kgdb/snapshot` -> `/semantic-export`; contrato de datos `sldb_kgdb_semantic_export` v1 congelado).
- 10-18 retiro de `runtime/edges` cancelado (`554e268e`); 10-18 `97c98036` ids de símbolos `sldb.sldb.*` -> `sldb.*`.
- 11-16 clean code C1-C7 (cierres de tareas de limpieza para respetar `tests/test_clean_code_rules.py`: máx. 10 líneas de lógica por función, 80 efectivas por archivo [V CHANGELOG]).

### 5.2 Planes anunciados y no terminados
- Plan `desk/drawer/plans/grafo-operable/` (brief, alcance, dag, decisiones, cierre, 8 revisiones): `status: delivered` en `dag.yaml` [V]; `external_references.deskops_consumer_plan` = `not_written` (tipos de tarea como documentos, ReviewDoc como recibo, gate de activación como RuleDoc; es de deskops) y `kimun` = `reference_only` (Datalog, evidencia con hash en aristas, estados derivados: "nada de eso en código"; capa G bloqueada por D0 y D-G) [V dag.yaml].
- `desk/drawer/tasks/task-atomo-y-field-convergieron-falta-un-set-bootstrap.md` y su gemela de diseño en `desk/tasks` [V].
- `desk/tasks/` tiene 46 TaskDocs; todas con `status: draft` y `current_node: ritual-execution` en su frontmatter [V], de modo que el campo no distingue abiertas de cerradas aunque el git log tenga closeouts de varias [I: el Board está desfasado respecto del log]. Sin cierre visible y por nombre de tarea, quedan abiertas (agrupadas) [I]:
  - API de escritura/MCP: `apply-batch-dry-run-publico` (nota 20261115-022430), `apply-batch-aristas-validadas-antes-de-escribir-alertas-on-write` (022655), `api-publica-busqueda-rankeada-escrituras-recientes-y-store-hash-como-precondicion` (074447), `on-write-save-document-payload-devuelve-las-alertas`, `sldb-escucha-suscripciones-y-stream-de-eventos-de-escritura` (idea de JP 11-14).
  - Código/anclas: `sldb-code-lee-sql-tablas-columnas-vistas-e-indices` (parte SQL de B2).
  - Notaciones: `sldb-notations-validate`, `sldb-notations-from-graph-elige-el-campo-del-label`, `sldb-api-notations-reexporta-lo-que-specir-importa`, `specdoc-guarda-los-datos-de-un-spec-que-no-son-elementos`, `raw-atoms-by-view`, `atomos-del-catalogo-sin-rutas-absolutas`, `matching-attach-atom-edges-deja-de-recomponer-live-stale`, `el-emparejamiento-de-atomos-pasa-a-su-propio-eje`.
  - Store/índices: `merge-de-dos-sldb-sin-detenerse-merge-driver`, `separar-el-store-en-sldb-store-y-sldb-cache`, `edgeindex-stale-manda-a-la-operacion-correcta`, `contract-signature-cubre-indexed-doc-anchors`, `check-edges-clasifica-como-stale-propio...` (nombres con `:`), `stores-check-sobre-un-clon-sin-build...`, `docs-show-sobre-un-store-enlazado...`, `sldb-stores-add-guarda-tal-cual-un-enlace-absoluto`, `rule-graph-en-frio-y-deskops-show-task-perfilar-de-nuevo`, `una-sola-forma-de-serializar-un-escalar`, `reproducir-si-sldb-docs-update-reescribe-indices-ajenos`, `run-view-dice-que-el-store-no-esta-construido-en-vez-de-unknown-view-h12`, `sldb-docs-track-canonical`, `r6-el-tokenizer-y-el-parser-bajan-a-core` (cierra la arista `models->store` de capas).
  - Diseños: `diseno-jerarquia-de-modelos-de-evidencia`, `diseno-set-bootstrap-de-modelos` (decisión pendiente del dueño).
  - 7 "decisión de JP": publicar 0.1.13 (fechar CHANGELOG, tag, avisar), resolver colisión de `SpecDoc` local vs builtin, arreglar refs por ruta a través de store enlazado, conflicto de git en índices derivados, separar el store en paquete propio (decisión 6), dónde vive un hook que bloquea escritura sobre rutas trackeadas, AST intermedio entre marcador y campo, python-sync destrackeando símbolos desaparecidos, qué de las vistas de deskops expresa `ViewDoc`.
  - Algunas ya tienen trabajo entregado según el log aunque su TaskDoc siga sin cerrar [I]: V0 bench (`5f0d5a19`), H4-H7 (`bb0e0e9a`...), `stores-check-no-aborta` (`9668e881`), `sldb-views-run-board-en-menos-de-1-s` (H11 `47b75674`), `main-de-sldb-en-verde` (`dfa5c504`), `reglas-rule-graph...` (`84eb7f75`), link-ausente (`a9562c25`, `8278d2a3`).
- Inbox: 56 notas en `desk/inbox/` (varias de specIR IR01-IR13, deskops, graph_ui, MEPU, pron); entre las abiertas relevantes: un store por rama no se puede unir al mergear (`20261115-195033`), `docs track --force` no fuerza (`162239`), `stores remove` no quita el enlace de `store.yaml` (`172753`), `models validate` falla con `No module named deskops.base` (`094415`), `fields update` parsea YAML y el inbox no tiene `promote` (`161013`), `declared target nodes: aceptar un directorio` (`054845`), `pron contra sldb: undo no restaura el hash del mundo y track/untrack pierde...` (`053107`) [V nombres de archivo; I estado].
- CHANGELOG: sección `0.1.13 — not released yet (no tag, not on PyPI)`; tarea "decisión de JP: publicar sldb 0.1.13".

---

## 6. Lo que no pude determinar y lo muerto, duplicado o a medias

**No determinado**
- Qué TaskDocs de `desk/tasks/` están realmente cerradas: el frontmatter no lo dice (todas `draft`/`ritual-execution`), y el estado real está en commits `closeout:` y en `.sldb/` / `runs`. No ejecuté deskops.
- La API exacta detrás de varios subcomandos (`models update/list/show/create`, `docs update`, `fields *`, `sections *`, `docs list/recover/compose`): cité el archivo del handler pero no leí cada función; están marcados [I] en las tablas.
- Contenido de `.github/workflows/ci.yml` (jobs, matriz): solo vi los nombres; el CHANGELOG habla de matriz 3.12/3.13 y un job `store` que reconstruye el store con hermanos deskops y pron.
- Si `check_surface.py` pasa hoy: sus consumidores (`../deskops`, etc.) no estaban leídos.
- No leí `sldb.code`, `store/`, `core/`, `links/` en detalle (son de otros revisores); solo lo que toca a las superficies.
- La semántica fina de `docs/surface/`, `surface/` (aplicación web Vite `@sldb/surface`, 140 archivos, clasificada "ajena" en `identity.toml`) y `workbench_demo`.

**Doc que no coincide con el código**
- `docs/architecture/current-cli-tree.md` (2026-09-13) omite unos 45 subcomandos y cinco grupos enteros (1.3); su párrafo de HTTP lista 5 rutas, hay ~45.
- `CHANGELOG.md` línea 3: "pyproject.toml declares 0.1.12"; en realidad `0.1.13` (commit `a0b86941`).
- Facade: 189 nombres, no 183.
- `docs/cli-envelope.md` sin `rules/violation`; `--envelope` abre el store con `open_store` (migra el layout) [1.2].
- `contracts/layers.toml`: el comentario dice que R1 y R3 "ya no figuran acá" pero siguen como `[[pending]]`; no hay nivel 1.
- `contracts/identity.toml`: lista como `[[orphan]]` `TASK-sldb-serve.md` y `TASK-store-generalize.md` ("el comando no existe en la interfaz"), archivos que ya no están en la raíz (cerrados el 2026-10-29, `cc60d33d`, `b23c2256`) y `sldb serve` existe; cifras de "evidence" (1551 documentos, 1279 símbolos, 68 comandos) vs hoy (2561 símbolos, 130 comandos): números viejos que `check_claims.py` solo comprueba si están marcadas verificables [V archivos y conteos; I que los números quedaron viejos].
- `docs/architecture/mcp.md` y `write/apply.py`: `guards_in_transaction` sigue como pendiente tras `f8ad9000`.

**Muerto, duplicado o a medias**
- Journal: `sldb journal show|verify`, `api.journal`, `api.verify_journal`, `JournalEntry`, `JournalVerifyReport` son cáscaras sin función desde 2026-11-04.
- Duplicados de superficie: `explore` (nivel superior y `docs explore`, mismas flags); `recover`/`compose` tres veces (`docs`, `legacy`, alias ocultos de nivel superior); grupos ocultos `store/model/doc` frente a `stores/models/docs`; `ls/get/glob/raw-find` como alias de `legacy`.
- `ProjectGraphConfig(closed_document=callable)` deprecado con `DeprecationWarning`; se retira cuando deskops migre (CC33) [V CHANGELOG].
- `/stores` HTTP y su campo `hash_a` (nombre que `migrate-hashes` reemplazó por `store_hash` en el resto) [V código; I desfase].
- Servidor: rutas de escritura sin el `SAVE_LOCK` (`/models/*`, `/edges/rewrite`, `/views/projection/save`) [I].
- `lint` tiene prefijos de ruta de este repo en el código (`desk/`, `source_docs/`, `projects/`, `software/`...), deuda declarada en `identity.toml` (`boundary_debt`) [V]; `cli/selfdoc_inspect.py` también nombra `knowledge/`.
- `python_source_graph.py` docstring aún dice "KGDB GraphSnapshot payload" y los contratos de datos conservan `kgdb` por decisión; el resto del rename es completo [V].
- `docs/architecture/current-cli-tree.md`, `identity.toml [[orphan]]`: `sldb.spec.yml`, `sldb_architecture.spec.yaml`, `sldb_extraction_flow.spec.yaml`, `sldb_store_cascade.spec.yaml` siguen en la raíz declarados huérfanos sin referencias [V ls raíz + identity.toml].
- MCP: `undo_changes` declarada y sin servir; el pendiente `dry_run` implica que `propose_changes` no valida lo que `apply_batch` valida al aplicar (no hay `apply_batch(dry_run=True)` público).
- Capa R1/R3/R5/R6 (aristas que suben de capa) abiertas; R6 tiene tarea (`r6-el-tokenizer...`).
- Estado de `desk/tasks/Board.md`: lista 46 tareas, varias con trabajo ya integrado (ver 5.2).
