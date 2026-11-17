# sldb: capa de texto (inventario de features)

Repo leido: /home/jp/proyectos/SLDB_1/sldb (solo lectura). Las rutas son relativas a ese repo. [V] = verificado en el archivo citado; [I] = inferencia. "Nuevo" = entro o cambio desde 2026-10-01 (por `git log`).

## 1. Para que sirve esta area

La capa de texto convierte un documento Markdown en un payload tipado (extraer) y un payload en Markdown (renderizar), usando como unica fuente el `__template__` de una clase pydantic `StructuredNLDoc`. El template es a la vez la plantilla de render y la receta de extraccion: los marcadores `⸢tipo•campo⸥` dicen que campo escribe cada trozo. Sobre eso se cuelgan: la composicion (un campo de solo-render armado con otros documentos o campos), un render instrumentado que devuelve el texto con spans exactos por campo (segmentos), la alineacion de esos spans con Markdown crudo, los links `[[x]]` y su transclusion, y las lecturas por secciones/campos (AST/IR, registros de busqueda). No hay store aqui: la capa es pura salvo `FieldRefResolver`, los links y `records`, que leen el store.

## 2. Features

### 2.1 Parseo y marcadores

| # | Feature | Que hace | Donde | Como se alcanza | Lee / escribe | Test | Nuevo |
|---|---|---|---|---|---|---|---|
| 1 | Parser de Markdown a nodos | Usa markdown-it `gfm-like` + tablas + plugin front_matter y convierte los hijos de la raiz a `SLDBNode` (type, tag, content, children, `map` de lineas, `metadata` con `info`/`markup`/`attrs` del token). Solo se usan bloques de primer nivel como unidad de matching. [V] | `src/sldb/core/ast/markdown_ast_handler.py:7-36`, `core/node.py` | `AST_Handler().split_nodes(md)` | lee texto; devuelve lista de nodos | `test_parse_once.py::test_a_fence_keeps_its_language_and_markup` | metadata y MarkdownIt unico: 2026-10-18 |
| 2 | Gramatica de marcador | `⸢KIND[,TRAIT...]•NOMBRE⸥`. Se parte en el primer `•`; la cabeza se parte por comas fuera de corchetes `[]`; el primer trozo es `kind`, el resto `traits`; el nombre es lo que sigue a `•` (con strip). Sin cabeza el kind por defecto es `rev`. [V] | `core/contracts/marker.py:4-34` | `parse_marker(inner)` | puro | `test_field_ownership.py` | no |
| 3 | `rev` | Campo reversible: se renderiza y se extrae. Es "la" fuente canonica; dos `rev` con el mismo nombre en un template dan `SLDBASTError`. [V] | `marker.py:44`, `core/template_extractor.py:86-92` | marcador `⸢rev•campo⸥` | ver 2.2/2.3 | `test_marker_invariants.py` | no |
| 4 | `optrev` | Como `rev` pero puede faltar. En extraccion un `optrev` vacio queda ausente (no `""`); en render con valor `None` se borra la linea entera si el marcador esta solo (o `clave: ⸢optrev•x⸥`); si comparte linea con otra cosa se reemplaza por `""`. Un `rev` y un `optrev` del mismo nombre conviven. [V] | `marker.py:47`, `renderer_engine/base.py:36-60`, `data_extractor.py:59-75`, `renderer_engine/yaml.py:44-57` | marcador `⸢optrev•campo⸥` | | `test_standalone.py::test_empty_optional_section_body_is_absent_not_empty`, `test_optional_section_body_survives_line_breaks` | captura de cuerpo: 2026-10-03/05 |
| 5 | `render` | Solo-render: se sustituye con el valor del payload pero nunca se extrae (su regex es `.*?`). Valor `None` da `""`. Es el kind de los campos compuestos. [V] | `handlers/text.py:20-30`, `renderer_engine/base.py:80-91` | `⸢render•nombre⸥` | escribe texto | `test_composition.py` | no |
| 6 | `py` | Evalua una expresion Python con `eval` y builtins restringidos (str,int,float,len,min,max,sum,sorted,list,dict,set,tuple,enumerate) sobre el payload. Solo corre en modo `unsafe` (`SLDB_PYTHON_EXECUTION_MODE=unsafe` o `configure(python_execution_mode="unsafe")`); en modo `safe` el marcador queda literal en la salida. Se puede filtrar con `python_expression_filter(expr, data)`. Error de eval deja el marcador literal. [V] | `renderer_engine/python_expr.py`, `runtime/config.py` | `⸢py•title.lower()⸥`; `configure`, `reset_config`, `get_config` | lee payload | `test_standalone.py::test_python_markers_render_in_unsafe_mode`, `..._can_be_filtered_in_unsafe_mode` | no |
| 7 | Rasgo `list` | En un `rev,list•x` el valor es lista; en render, si el valor no es list/dict lo imprime con `str`. Con lista, el renderer de listas repite el item. [V] | `renderer_engine/base.py:93-97`, `renderer_engine/list.py` | `- ⸢rev,list•items⸥`, o en frontmatter `tags: ⸢optrev,list•tags⸥` | | `test_list_items_round_trip.py` | escapado de items: 2026-10-18 |
| 8 | Rasgo `dict` | Un marcador `dict` solo dentro de un bloque `fence` o `front_matter` hace que todo el YAML del bloque sea el valor del campo; en render un dict/list se vuelca con `yaml.dump(allow_unicode, sort_keys=False)`. [V] | `handlers/yaml.py:19-26`, `renderer_engine/base.py:93-97` | ```` ```yaml\n⸢optrev,dict•meta⸥\n``` ```` | | `test_standalone.py::AdvancedMarkersDoc` | no |
| 9 | Rasgo `table` / `table[a,b,c]` | Un marcador solo en un parrafo declara un campo `list[dict]`. En render emite tabla Markdown con las columnas declaradas, o las claves del primer registro si no hay lista. Celdas: `\n` -> espacio y `|` escapado. En extraccion exige un bloque `table` en esa posicion y lee filas por nombre de cabecera (`table[...]` filtra columnas; `table` toma todas); celdas faltantes dan `""`. [V] | `handlers/text.py:44-49,66-72`, `handlers/table.py:48-91`, `renderer_engine/table_markdown.py` | `⸢rev,table[name,status,goal]•tasks⸥` | | `test_standalone.py::test_table_marker_renders_and_extracts_explicit_columns`, `..._infers_columns...` | no |
| 10 | Otros rasgos (`markdown`, `diagram`, `view`, `opt`) | `markdown` aparece en 22 marcadores de deskops pero ningun codigo lo lee: no cambia render ni extraccion. `diagram` y `view` solo los lee `segment_enrichment` (proyeccion). `rev,list,opt` (un solo uso) no tiene efecto. Un rasgo desconocido se acepta y se ignora. [V] por grep de `traits` en `src` | `api/segments/segment_enrichment.py:19`; resto no hay lector | | | `test_field_ownership.py::test_unknown_extra_modifier` | no |
| 11 | Expresion Jinja inline `{{ ... }}` | En el template, `{{ x }}` se renderiza con Jinja sobre el payload (despues de sustituir marcadores). En extraccion equivale a `.*?` (comodin). [V] | `renderer_engine/base.py:30-35`, `handlers/text.py:9,20-23` | texto del template | lee payload | `test_standalone.py::AdvancedMarkersDoc` ("Hello {{ title }}!") | no |

### 2.2 Compilacion del template a recetas (extraccion)

| # | Feature | Que hace | Donde | Test | Nuevo |
|---|---|---|---|---|---|
| 12 | Compilador de template | Para cada bloque de primer nivel del template, baja por el arbol y pide una receta al handler. Un bloque sin marcadores ni `{{}}` produce una receta ancla (`anchor: True`, regex `^texto escapado$`). Cada receta guarda `outer_index`, `outer_type`, `outer_tag`, `inner_path`. [V] | `core/template_extractor.py:13-84` | `test_marker_invariants.py` | 2026-10-05 |
| 13 | Handler de texto (parrafo, heading, todo lo no listado) | Convierte el texto en regex: literal escapado, cada `rev`/`optrev` es `(.*?)`, cada `render`/`py`/`{{}}` es `.*?`. El regex se aplica con `fullmatch` y sin `re.S` a proposito, de modo que un marcador inline solo cubre una linea. Receta `all_optional`, `optional_block` (un unico `optrev` que ocupa todo el parrafo), o `anchor` si no tiene datos. [V] | `core/handlers/text.py:32-91` | `test_standalone.py` | 2026-10-05 |
| 14 | Marcador solo en un parrafo ("lone marker") = cuerpo de seccion | Un `rev`/`optrev` unico que ocupa todo un parrafo captura por rango de bloques (`capture_mode: section_body`): todos los bloques desde la posicion actual hasta el siguiente bloque que reclama una receta posterior (heading ancla o literal ancla). Corta en el texto crudo por lineas (`map`), que conserva saltos, listas y subtitulos. Si el marcador unico esta en un bloque que no es parrafo ni heading (p.ej. `> ⸢rev•x⸥`) lanza `SLDBASTError("... lone marker in a 'blockquote' block; move it to its own paragraph.")`. [V] | `template_extractor.py:46-67`, `data_extractor.py:20-36,59-75`, `extractor/section_boundary.py` | `test_standalone.py::test_lone_data_marker_in_unsupported_block_is_rejected`, `test_section_body_extraction.py` | 2026-10-03/05 |
| 15 | Seccion vacia no desplaza a las demas | El indice consumido es `boundary_idx - 1`; una seccion vacia deja el bloque limite para la receta siguiente. [V] | `data_extractor.py:59-75` | `test_section_body_extraction.py` | no |
| 16 | Frontera por heading ausente | Pasado un heading que el documento no tiene, solo los headings posteriores cuentan como limite; evita que el campo de una seccion faltante se coma la ultima seccion. Un documento que termina antes que su template conserva su ultima seccion. [V] | `extractor/section_boundary.py` | `test_last_section_kept.py` | 2026-10-18 |
| 17 | Ancla literal de parrafo | Un parrafo literal del template (p.ej. `_Describe the goal._`) ancla solo un parrafo con ese texto exacto; si el documento no lo trae, el primer parrafo real es el valor. Y al renderizar el parrafo literal se escribe siempre. [V] | `extractor/recipe_matcher.py:34-41` | `test_literal_paragraph_anchor.py` | 2026-10-18 |
| 18 | `template_anchors(model)` | API publica: mapa campo -> texto del parrafo literal inmediato anterior al marcador del campo (con `_` incluidos). Deskops lo usa para distinguir placeholder de valor. [V] | `api/model_registry/template_anchors.py` | `test_literal_paragraph_anchor.py::test_template_anchors_*` | 2026-11-16 (4206cfe8) |
| 19 | Orden y busqueda de bloques | El extractor recorre recetas en orden del template y busca cada una en bloques del documento a partir de la posicion actual (`search`), comprobando `outer_type` y `outer_tag`. Un `optional_block` solo mira el bloque actual y se salta si ese bloque pertenece a una ancla posterior. [V] | `data_extractor.py:44-97`, `extractor/recipe_matcher.py` | | 2026-10-18 |
| 20 | Handler de lista | Marcador en un item (`- ⸢rev,list•x⸥`): la receta es por item con marcador. En extraccion devuelve la lista con el texto fuente de cada item (strip). Si el item es solo el marcador (`bare`) se des-escapa. Al renderizar solo se usa el primer item como plantilla y se repite; lista vacia renderiza un item vacio; valor no-lista renderiza `""`. Items dict usan sus claves como marcadores del item. [V] | `handlers/list.py`, `renderer_engine/list.py` | `test_list_items_round_trip.py` | 2026-10-18 |
| 21 | Escapado de items de lista | Un valor escalar que, al ir tras `- `, Markdown leeria como sintaxis de bloque (`# `, `>`, `- `, `1.`, ```` ``` ````, `~~~`, `<`, espacios iniciales, vacio, regla horizontal) se escribe con una `\` delante y se lee sin ella; un valor que ya empieza con `\` + sintaxis tambien se escapa (mapeo uno a uno). Solo si el valor abre el item; `- run: ```bash` no se escapa. [V] | `handlers/list_escape.py`, `renderer_engine/list.py:35-39` | `test_list_items_round_trip.py` | 2026-10-18 |
| 22 | Handler de tabla (plantilla con marcadores por columna) | Tabla del template con marcadores en la ultima fila de datos: una receta por tabla, `col_markers` por indice de columna; cada fila del documento se lee celda a celda con el regex de la celda; la primera fila (cabecera) se ignora. Render: toma header, separador y fila 3 del template como plantilla de fila y la repite por cada elemento (lista, o dict ordenado por clave numerica); sin filas renderiza el bloque tal cual. [V] | `handlers/table.py:8-47,62-67`, `renderer_engine/table.py` | `test_standalone.py` (indirecto) [I] | no |
| 23 | Handler YAML/front matter/fence | Front matter o fence con marcadores en lineas: `clave: ⸢rev•campo⸥` o marcador solo en la linea siguiente a `clave:`. En extraccion quita los marcadores, `yaml.safe_load`, y para cada marcador toma `data[clave]`; fechas/datetimes se normalizan a ISO. Un marcador `dict` unico toma todo el YAML. Error de parseo da `SLDBASTError("Failed to parse YAML content for ...")`. [V] | `handlers/yaml.py` | `test_standalone.py::test_frontmatter_fields_render_and_extract` | no |
| 24 | Ruta de campos con `rev` repetido | `rev` duplicado por nombre en el template -> `SLDBASTError("Multiple canonical 'rev' markers found for field '...'")`. Pero `rev` + `optrev` del mismo nombre se aceptan. [V] | `template_extractor.py:86-92` | `test_marker_invariants.py` | no |
| 25 | Cache de recetas | `_recipes(template)` con `lru_cache(512)`: el template se parsea una sola vez. [V] | `runtime/validation.py:166-170` | `test_parse_once.py` | 2026-10-18 |

### 2.3 Render

| # | Feature | Que hace | Donde | Nuevo |
|---|---|---|---|---|
| 26 | Render de documento | Parte el template en bloques, renderiza cada bloque segun su handler (lista, tabla, yaml/front_matter/fence, resto por reemplazo de marcadores) y los une con `\n\n`; `render` devuelve `.strip()` (sin grabadora). `render_payload` del modelo da el payload (`model_dump(mode="json")` + campos compuestos). [V] | `core/renderer.py`, `models/structured_doc.py:28-32` | no |
| 27 | Sustitucion de marcadores en linea | Linea a linea: omite lineas con optrev ausente; reemplaza `⸢...⸥` por el valor (`str`, o YAML si dict/list, o tabla); marcador requerido sin valor queda literal; luego pasa toda la linea por Jinja (`autoescape=False`). [V] | `renderer_engine/base.py:25-100` | no |
| 28 | Render de front matter | Quita `---`, procesa linea a linea. `clave: ⸢optrev•x⸥` con valor `None` omite la linea; con valor la reescribe con `yaml.safe_dump({clave: valor})` (por eso listas y strings se escapan en YAML). Para `render` con `None` mantiene `null`. [V] | `renderer_engine/yaml.py` | no |
| 29 | Contrato `render_payload` flexible | `render_payload_of` pasa `recorder`, `ref_root`, `field_resolver` solo si el modelo los acepta. [V] | `renderer_engine/payload_call.py` | no |
| 30 | Trailing newline | Corrige que los sentinelas escondan el unico salto final que Jinja descarta, para que el render instrumentado sea byte a byte igual al normal. [V] | `renderer_engine/jinja_trailing.py` | no |
| 31 | API de funciones | `extract_model_data(Modelo, md)`, `render_model_markdown(Modelo, dict, ref_root=None)`, `validate_model_input_roundtrip`, `validate_model_data_roundtrip`, y variantes por string de template: `extract_payload(template, md)`, `render_markdown(template, data)`. [V] | `runtime/validation.py:172-183` | no |
| 32 | CLI de texto | `sldb extract`, `render`, `validate` (con `--ref-root` en render sin store), `init` (skill). [V] por `cli/commands/basic.py`, tests `test_standalone.py` | `cli/commands/basic.py` | no |

### 2.4 Validacion y roundtrip

| # | Feature | Que hace | Donde | Nuevo |
|---|---|---|---|---|
| 33 | `Validator.validate(markdown|data, mode)` | Extrae (o toma el dict), renderiza, extrae de nuevo y compara. `render_validity` (por defecto): compara solo los campos `rev` y `optrev` del template tras `strip` de strings; `render`/`py` quedan fuera. `strict_roundtrip`: igualdad total de dicts. Devuelve `(ok, details)` con `input_data`, `rendered_markdown`, `extracted_payload`. [V] | `runtime/validation.py:118-164` | validate cubre optrev: 2026-10-05 |
| 34 | Extraccion tipada | `Validator.extract` llama `modelo(**extraido).model_dump(mode="json", exclude_none=True)`: la validacion pydantic ocurre al extraer; campos `optrev` ausentes toman default. [V] | `validation.py:125-126` | no |
| 35 | Exigencia de descripciones | Una subclase de `StructuredNLDoc` con un campo sin `description` no vacia lanza `TypeError`. [V] | `models/structured_doc.py:22-23` | `test_standalone.py::test_structured_nl_doc_requires_field_descriptions` |
| 36 | Perdida de reescritura | `rewrite_loss(model, md)`: devuelve los bloques de contenido del documento (parrafo, fence, listas, quote, tabla, html) que no sobreviven `render(extract(md))`, salvo bajo headings que el template llena solo con `render`/`py`. [V] | `api/documents/rewrite_loss.py` | `tests/api/test_rewrite_loss.py` (existe, no abierto) | no |
| 37 | Hallazgos (`Finding`) | Dataclass `level` (error/alert/warning), `code` `ns/name`, `subject`, `what`, `fix`, `document`; `route(findings)` los pasa a escuchas instaladas por el CLI. Es infraestructura transversal, no de la capa de texto en si. [V] | `core/finding.py`, `core/finding_route.py` | 2026-10-20 / 2026-11-06 |
| 38 | Excepciones | 18 clases (ver 4). [V] | `core/exceptions/__init__.py` | algunas nuevas |

### 2.5 Composicion (`__compositions__`, `__containment__`)

| # | Feature | Que hace | Donde | Test | Nuevo |
|---|---|---|---|---|---|
| 39 | Composicion por documento | `__compositions__ = {nombre: {source_field, model, template, separator, ref}}`. `compose_field` lee la lista de rutas en `source_field`, extrae cada archivo con `model` (`"mod:Clase"` importable o clase) y formatea con `str.format(**payload)` (defecto `"- {title}"`); une con `separator` (defecto `\n`). Un archivo inexistente, symlink, fuera de `ref_root`, que no extrae o cuyo `template` falla se omite en silencio. Marcador asociado: `⸢render•nombre⸥`. [V] | `models/structured_doc.py:34-71` | `test_composition.py`, `tests/api/test_composition_ref_root.py` | no (ref_root: 2026-10-01; guarda de lectura: 2026-11-11) |
| 40 | Raiz de refs | Una ref relativa se resuelve contra `ref_root` (raiz del proyecto) y no contra el cwd; sin `ref_root` cae al cwd (`render_model_markdown` directo, `sldb render` sin store). Las comillas invertidas se quitan. [V] | `structured_doc.py:73-76` | `test_composition.py::test_composition_refs_resolve_against_the_root_not_the_cwd` | 2026-10-01 |
| 41 | Composicion por campo (`ref: "field"`) | Cada ref es `Model:name#campo`, `docs/<Model>/<name>/<campo>` o `docs/<name>/<campo>`; `FieldRefResolver.resolve` carga todos los documentos del store (cache por instancia), busca el unico documento coincidente y devuelve `{model, doc, path, field, value, payload}`. El texto es `template.format(value=..., val=..., **payload_del_destino)` (defecto `"{value}"`). Sin resolver (render puro) devuelve `""`; un error al resolver o valor `None` omite la pieza. [V] | `structured_doc.py:43-54`, `api/segments/field_ref_resolver.py` | `tests/segments/test_field_transclusion_and_diagram_segments.py` | 2026-11-06 (SV03) |
| 42 | Arista `composes` | Cada ref de un campo compuesto produce una arista `composes` del padre al documento compuesto (metadatos: campo, source_field, ref, ref_index, composed_model); se resuelve al componer el indice; refs sin dueño no dejan arista. No hay edicion de vuelta (el texto compuesto no se extrae). [V] | `store/edge_index/composition_edges.py` | `tests/api/test_composition_edges.py` | 2026-10 [I] |
| 43 | Contrato de composicion en borradores de modelo | `check_composition_contract`: cada composicion debe ser dict, nombrar un `source_field` que exista y tener `⸢render•nombre⸥` en el template, o `SLDBModelEditError`. [V] | `api/model_drafts/composition_contract.py` | `tests/api/test_model_metadata_drafts.py` | no |
| 44 | `__containment__` | `{campo: [Modelos permitidos]}`; declara que el campo contiene hijos. No toca render ni extraccion: solo genera aristas `contains` (y `references` los otros declarados) en el indice de aristas, con `allowed_models`. Aparecen tambien en `/schema`. Hermanos: `__references__`, `__reference_models__`, `__doc_anchors__` (valida forma `doc:Modelo:nombre[@sha256:...]` al construir). [V] | `store/edge_index/declared_references.py`, `models/doc_anchor_value.py` | `tests/api/test_declared_references.py` | 2026-11-06 (GO07) |

### 2.6 Segmentos y transclusion de campo

| # | Feature | Que hace | Donde | Test | Nuevo |
|---|---|---|---|---|---|
| 45 | Grabadora de spans (`SpanRecorder`) | Durante el render envuelve cada valor escrito en sentinelas privados `U+E000 id U+E001 texto U+E002`, con metadatos `{path, marker, traits}`; en items de lista la ruta lleva prefijo `lista/i` o `lista/i/campo`. | `core/renderer_engine/span_recorder.py` | `test_segment_structure.py` | no |
| 46 | Decodificador de sentinelas | Quita sentinelas y devuelve `(id, inicio, fin)` sobre el texto limpio; `strip_with_offsets` recorta como el renderer y acota los spans. | `core/segments/sentinel_decoder.py` | `test_document_segments.py` | no |
| 47 | `render_with_spans(modelo, payload, ref_root, field_resolver)` | Render canonico + lista de `(meta, inicio, fin)`. | `core/segments/instrumented_render.py` | `test_document_segments.py::test_instrumented_render_matches_canonical_render...` | no |
| 48 | Arbol de segmentos | Anida spans por contencion (orden: inicio, mas ancho primero, wrap mas tardio primero en empate: una composicion de una sola pieza queda dentro de su campo) y rellena huecos con segmentos `literal`, de modo que la concatenacion de los segmentos reproduce exacto el texto. | `core/segments/span_nesting.py`, `segment_tree.py` | `test_segment_structure.py`, `test_document_segments.py` | no |
| 49 | `build_document_segments(store, pythonpath, doc)` | Segmentos del render canonico de un documento rastreado: `DocumentSegments{id, doc, model, path, text, canonical, payload, segments}`; `canonical` dice si el archivo en disco (sin bloque de procedencia) iguala el render. HTTP `GET /document/segments`. | `api/segments/build_document_segments.py`, `cli/serve/segments_routes.py` | `tests/api/test_composition_ref_root.py` | FieldRefResolver inyectado: 2026-11-06 |
| 50 | Tipos de segmento | `LiteralSegment`; `FieldSegment{start,end,text,path,marker,traits,editable (rev/optrev),display (inline|block),kind,value,source,composition,projection,table_data,children}`; `CompositionPiece{... source: SourceRef, ref_index, ref, editable=False}`; `SourceRef{doc,path,model,field}`. `display` es "block" si el span ocupa lineas propias entre lineas en blanco. `kind` sale de `sldb.api.schema.field_kinds` (cuyo contenido no abri). | `api/segments/*.py`, `segment_display.py`, `payload_values.py` | `test_segment_structure.py` | `field`/`projection`/`table_data` 2026-11-06 |
| 51 | Enriquecimiento de segmentos | Si el segmento tiene rasgo `diagram`/`view` (o composicion `ref` view/spec) y hay store: `value|ref|text` que empiece por `SpecDoc:` se resuelve con `resolve_view`, `ViewDoc:` con `evaluate_projection_view`, y el resultado va en `projection` (o `{"error": ...}` sin romper). Con rasgo `table`/`table[..]`: `table_data={columns, rows}`. [V] | `api/segments/segment_enrichment.py` | `tests/segments/test_field_transclusion_and_diagram_segments.py` | 2026-11-06 |
| 52 | Segmentos de Markdown crudo | `build_extraction_segments(modelo, md, ref_root)`: extrae, renderiza instrumentado, alinea canonico<->crudo con `SequenceMatcher(autojunk=False)` por caracter, y reubica cada span: `alignment` exacto/aproximado, `unmatched` (runs de crudo no explicados, recortados), `coverage` (fraccion de texto no blanco cubierta, 0-1). Las piezas de composicion se descartan. `extract_markdown_segments` envuelve fallos como `SLDBValidationError` (model no registrado = `SLDBStoreError`). HTTP `POST /extract/segments`. | `api/segments/build_extraction_segments.py`, `extract_markdown_segments.py`, `core/segments/canonical_alignment.py`, `unmatched_runs.py` | `test_extraction_segments.py` | 2026-10-01 (ref_root) |

### 2.7 Links y transclusion de documentos (`src/sldb/links/`)

| # | Feature | Que hace | Donde | Test | Nuevo |
|---|---|---|---|---|---|
| 53 | Parser de links | `[[destino]]` (link), `![[destino]]` (transclusion), `[pred:: [[destino]]]` y `[pred:: ![[destino]]]` (link con predicado). El tipo del predicado (eje 5W1H) viene de `predicate_types`; si falta es `CUSTOM`. Las coincidencias estandar dentro de un link con predicado no se duplican. | `links/parser.py` | `test_links.py` | no |
| 54 | Resolucion de destino | Orden: nombre de documento rastreado (si su archivo existe y la guarda lo deja leer) -> relativo al documento actual -> ruta tal cual. Antes de resolver, la guarda de lectura (`unreadable_reason`) rechaza symlinks y salidas de la raiz: el destino queda sin resolver. `doc#Seccion` cae a resolver `doc` si la forma completa no resuelve. | `links/resolver.py` | `test_links.py`, `test_composition_sections.py` | 2026-10-12/2026-11-11 |
| 55 | Seccion por heading | `extract_section(md, "Titulo")`: desde el heading (insensible a mayusculas, ignora los dentro de fences) hasta el siguiente de igual o mayor nivel; `None` si no existe. `strip_frontmatter`, `split_anchor`. | `links/sections.py` | `test_composition_sections.py` | 2026-10-12 |
| 56 | `compose_document` (`docs compose`) | Expande recursivamente `![[x]]` y `![[x#Seccion]]`; un documento incluido pierde su frontmatter, el raiz conserva el suyo; ciclos: la cadena de inclusion impide el bucle (se incluye una vez por rama, el mismo doc dos veces si no es ciclo); seccion inexistente -> `unresolved` y queda el link crudo; devuelve `{root, path, markdown, transclusions, unresolved}`. Los links normales no se expanden. | `links/composer.py` | `test_composition_sections.py` | 2026-10-12 |
| 57 | `--render-links` | Reescribe `[[x]]` a `[titulo](ruta/relativa.md#slug)` (titulo del frontmatter o nombre), y `[pred:: [[x]]]` a `*pred*: [titulo](...)`. | `links/render.py` | `test_links_batch_cli.py` [I] | 2026-10-12 |
| 58 | `recover_links` (`docs recover`) | Lista links con `resolved`, `path`, `source` (store/path), predicado y tipo; profundidad configurable, sin repetir documentos, dedupe por `(target, kind)`; con ancla `#Seccion` comprueba que la seccion exista. | `links/recovery.py` | `test_links.py` | 2026-10-12 |
| 59 | `audit_links` | Problemas por documento: `unresolved` o `custom` (predicado no declarado en el store). Lo usan `docs recover --all` y `/lint`. `tracked_documents` enumera los documentos del store. | `links/audit.py`, `links/tracked.py` | `test_links_batch_cli.py` | 2026-10-12 |

### 2.8 Registros de campos y secciones (`api/records`, `core/ir`)

| # | Feature | Que hace | Donde | Test | Nuevo |
|---|---|---|---|---|---|
| 60 | Extraccion de secciones | `extract_sections(md)`: por cada heading h1-h6 produce `SectionRecord{title, slug, level, path, line_start, line_end}`; el `path` es la cadena de slugs de ancestros (`overview/roadmap`), con sufijo `-2`, `-3` ante hermanos de igual titulo; `line_end` se extiende hasta antes del siguiente heading de igual o mayor nivel (el doc dice "solo el token del heading": desactualizado, el codigo extiende). [V] | `api/records/extract.py` | `test_field_ownership.py::test_section_span_covers_body...`, `test_repeated_sibling_titles...` | no |
| 61 | Propiedad de campos por seccion | `_map_fields_to_sections(template, secciones)`: ubica en el template la linea de cada marcador `rev`/`optrev` y su heading anterior, y la traduce a la ruta de seccion del documento renderizado. Se niega (devuelve `{}` y avisa) si el numero de headings del template y del documento difiere; con una sola seccion de template tolera headings extra (venidos de un valor). Un marcador sobre un campo desconocido avisa. | `api/records/map_fields.py` | `test_field_ownership.py` | no |
| 62 | `flatten_payload` | Aplana dicts anidados a `a.b.c`; las listas quedan como un valor. | `api/records/flatten.py` | | no |
| 63 | IR del documento | `build_document_ir(runtime_doc, md, template)`: `DocumentIR{context, structure (secciones anidadas), nodes (campos con `field_path`, `value`, `owning_section`), surface (nodos Markdown con lineas y tag), graph (doc has_section/has_field), context_index (breadcrumbs y `about`)}`. | `api/records/build_ir.py`, `core/ir/*` | `tests/api/` (ast) [I] | no |
| 64 | `ast show`/`ast_for_target` | Targets `store`, `models/X`, `docs/X`, `fields/campo`; `docs/X` devuelve payload, procedencia, secciones e IR. | `api/records/ast_target.py`, `build_ast.py` | `tests/api/test_store_ast_unimportable_model.py` | 2026-10-18 (un modelo no importable no tumba el AST) |
| 65 | Registros de busqueda | `iter_search_records` arma `SearchRecord` de tipo store/model/doc/section/field (secciones del indice persistido o reconstruidas del Markdown con aviso). `search_records` filtra por termino (`stores|models|docs|sections|fields`), `--in physical|semantic|both`, exacto/regex/difuso (ratio 0.7). `RecordFilters` evalua `--where` por tipo (doc: motor del store; field: `value/doc/model/field/path/owning_section`, `has()`; section: `title ~`, `path =`, `"x" in about|breadcrumbs|semantic_tags`). `serialize_records`, `select_keys`. | `api/records/*` | `test_cli_*`, `tests/api/` [I] | 2026-10-20 (provenance en doc) |
| 66 | `query_field_records` | Todos los campos de todos los documentos cuya ruta aplanada es o termina en `.campo`, con su seccion dueña. | `api/records/fields.py` | | no |
| 67 | `resolve_runtime_doc` | Un documento como dict (por nombre, ruta, `Modelo/nombre`, `alias:ref`) con payload, tags y template. | `api/records/resolve.py` | | 2026-10-16 (alias) |

## 3. Gramaticas y formatos exactos

### 3.1 Marcador

```
marcador   := "⸢" cabeza ["•" nombre] "⸥"
cabeza     := kind ("," rasgo)*          -- comas dentro de [ ] no separan
kind       := rev | optrev | render | py | (otro: ignorado en extraccion, sustituido por "" o valor en render)
rasgo      := list | dict | table | table[col,col,...] | cualquier otro texto (sin efecto salvo diagram/view)
nombre     := texto hasta "⸥" (strip); para py es la expresion Python
```
Patron: `⸢([^⸥]+)⸥`. Un marcador solo en su linea (o `clave: ⸢optrev•x⸥`) se omite por completo en render si es `optrev` y el valor es `None`. [V] `core/contracts/marker.py`, `renderer_engine/base.py`.

Resumen por kind (extraccion / render) [V]:

| kind | extraccion | render |
|---|---|---|
| `rev` | captura y exige | escribe valor; sin valor queda el marcador literal |
| `optrev` | captura; vacio = ausente | escribe valor; `None` = `""`, o linea omitida si esta sola |
| `render` | `.*?` (se ignora) | escribe valor; `None` = `""` |
| `py` | `.*?` (ignora) | evalua (solo `unsafe`); si no, deja el marcador |
| `{{expr}}` | `.*?` | Jinja |

### 3.2 Ejemplos reales (consumidores)

Board (deskops, `deskops/deskops/models/board.py`):
```
---
id: ⸢rev•id⸥
tasks: ⸢rev•tasks⸥        # lista -> se vuelca como YAML en frontmatter
tags: ⸢rev•tags⸥
---
# ⸢rev•title⸥
## Purpose
_Explain what this board routes and why it exists._      <- ancla literal
⸢rev•purpose⸥                                              <- cuerpo de seccion
## Task Details
_Generated from the task references above._
⸢render•task_summaries⸥                                    <- campo compuesto
```
con
```
__containment__ = {"tasks": ["TaskDoc"], "pills": ["PillDoc"], "rituals": ["RitualDoc"]}
__compositions__ = {"task_summaries": {"source_field": "tasks", "model": "deskops.models:TaskDoc", "template": "- {title} [{status}] - {goal}"}}
```
Task (`deskops/deskops/models/task.py`): `status: ⸢optrev•status⸥`, `tags: ⸢optrev,list•tags⸥`, `history: ⸢optrev,list•history⸥`, `# ⸢rev•title⸥`, `⸢rev•why⸥`.

Conteos de marcadores en src, deskops y tests (grep, solo kind inicial): rev 630, optrev 67, rev,list 59, render 33, rev,markdown 22, rev,dict 12, optrev,list 12, py 6, rev,table 2 (+ `table[name,status,goal]`, `table[name,state]`, `table[name,role]`), `rev,diagram` 1, `rev,list,opt` 1, `optrev,dict` 1. [V]

Otros ejemplos de tests:
```
⸢rev,table[name,status,goal]•tasks⸥
⸢optrev•subtitle⸥  /  Rendered slug: ⸢render•slug⸥  /  Python slug: ⸢py•title.lower().replace(' ', '-')⸥
Jinja greeting: Hello {{ title }}!
* ⸢rev,list•items⸥
```yaml\n⸢optrev,dict•meta⸥\n```
- run: ⸢rev,list•validation⸥        (valor tras etiqueta: no se escapa)
```

### 3.3 Receta de extraccion (diccionario interno)

`{outer_index, outer_type, outer_tag, inner_path, handler: text|list|table|yaml, props, props_info (Marker[]), regex, anchor?, optional_block?, all_optional?, capture_mode: section_body?, marker?, markers?, col_markers?, table_columns?, item_template?}`. No es API publica pero `_recipes` la usa `template_anchors`. [V]

### 3.4 Metadatos de span y segmentos

Meta de campo: `{path, marker, traits}`; de pieza: `{piece: True, source: {doc,path,model,field}, ref_index, ref}`. Sentinelas: `U+E000 <id> U+E001 <texto> U+E002`. Ruta de campo: `titulo`, `steps/2`, `steps/2/title`. [V]

### 3.5 Links

```
LINK_PATTERN = (!)?\[\[([^\]]+)\]\]
INLINE_PREDICATE = \[([a-zA-Z0-9_-]+)::\s*(!)?\[\[([^\]]+)\]\]\]
destino = doc | doc#Seccion
```
Frontmatter y headings: `\A---...---` ; heading `^(#{1,6})[ \t]+(.*?)[ \t]*#*[ \t]*$`. [V] `links/parser.py`, `links/sections.py`.

### 3.6 Direcciones y selectores (docs, fuera de la capa de texto pero asignados)

`docs/addressability_model.md` [V leido]: espacios `st.{Modelo}.<doc>.<campo>[.<sub>|.<i>]`, `st.{Modelo+}` (familia), `se.<tag>` (semantico), `<store>:st.`, `gse.<tag>`; predicados `has()`, `nonempty()`, `"x" in campo`, `campo ~ "re"`, `=`/`!=`, `>=`/`<=`, `model <= Base`, unidos por ` and ` (sin `or` ni parentesis). `docs/architecture/selectors.md`: `select_documents(store, {ref, prefix, path_glob, model, where, include_linked}, expect="many"|"one")` (conjuncion; `AmbiguousSelectionError` con `candidates`). `docs/architecture/references.md`: `referrers`/`rewrite_references` (origen `schema` o `mention`, transaccion todo-o-nada, validacion en memoria antes de abrir, `dry_run`). Estos tres viven en `api/edges`, `store/query_engine`: otros revisores los cubren; aqui solo los doy por existentes segun su doc. [V del doc, no del codigo]

## 4. Invariantes, garantias y errores

Garantias [V]:
- Roundtrip: `extract(render(extract(md)))` coincide en los campos `rev`/`optrev` (comparacion con `strip`); `validate` lo comprueba. `strict_roundtrip` compara todo.
- El render instrumentado es byte a byte igual al normal (`test_document_segments.py::test_instrumented_render_matches_canonical_render_for_every_document`) y los segmentos concatenados reproducen el texto (`test_segments_concatenate_back_into_the_document_text`).
- Un item de lista escalar se lee de vuelta tal como se escribio (`test_list_items_round_trip.py`).
- Un `optrev` vacio queda ausente; un `rev` vacio queda `""`.
- Una seccion vacia no desplaza a las siguientes; el ultimo bloque de un documento corto se conserva.
- Un parrafo literal del template no se traga el primer parrafo real si falta.
- La composicion no ejecuta nada del store sin resolver: sin `field_resolver` los campos `ref: field` dan `""`.
- Los refs de composicion y los links respetan la guarda de lectura: ruta fuera de la raiz o symlink = se omite / queda sin resolver.
- Los campos de un `StructuredNLDoc` exigen `description`.

Errores que lanza el area [V]:
- `SLDBASTError`: `rev` duplicado; marcador unico en bloque no soportado; fallo de YAML.
- `SLDBDocumentTargetError`: ref de campo invalida/desconocida/ambigua (`FieldRefResolver`), destino de documento rechazado por la guarda.
- `SLDBValidationError`: `extract_markdown_segments` cuando el Markdown no cumple el modelo.
- `SLDBModelEditError`: contrato de composicion en borradores.
- `ValueError`: modo de ejecucion Python no valido; `Validator.validate` sin markdown ni data.
- `TypeError`: campos sin descripcion; `template_anchors` con algo que no es clase.
- Errores de pydantic al construir el modelo desde lo extraido (campo `rev` ausente en el documento).
- Lista de excepciones del paquete: SLDBError, SLDBModelError, SLDBModelDraftError, SLDBModelEditError, SLDBValidationError, SLDBStoreError, ConflictError, SLDBASTError, SLDBLinkError, SLDBPayloadSaveError, SLDBGraphCycleError, SLDBFieldPathError, SLDBFieldTypeError, SLDBDocumentTargetError, AmbiguousSelectionError, SLDBGraphArgumentError, SLDBRuleViolationError, WherePredicateError.

## 5. No determinado, dudoso, muerto o a medio hacer

1. **`ref: "view"` / `"spec"` en `__compositions__` es letra muerta en render** [V]. `compose_field` solo distingue `"field"`; cualquier otro valor va a `_compose_by_doc`. `docs/architecture/segments-and-transclusion.md` lo anuncia como transclusion de diagramas, pero lo unico que existe es el enriquecimiento de segmentos por rasgo `diagram`/`view` o por `composition.ref` (ver 51). Ademas la clave `composition` de `EXTRA_KEYS` en `api/segments/segment_nodes.py` nunca la escribe nadie: grep de `"composition"` solo la encuentra al leer. `CompositionSpec` y `FieldSegment.composition` quedan siempre `None`. Probablemente se escribiria desde el render y no se hizo.
2. **Contratos sin uso**: `NodeData` y `RenderContext` (`core/contracts/`) no los importa nadie fuera de `contracts/` [V grep]. `parse_marker` esta duplicado en `core/contracts/marker.py` y `core/handlers/utils.py` (mismo codigo) [V]. `core/node_handler.py` es un reexport. `core/handlers/base.py` define `get_text`, que `Marker`-handlers usan; `SLDBNode._raw` guarda el nodo markdown-it pero no vi quien lo lea [I].
3. **Los rasgos `markdown`, `opt`, `yaml`, `view`/`diagram` sin store no hacen nada** (ver 10). El docstring de `FieldSegment.traits` habla de `'yaml'`, pero ese rasgo no existe en el codigo.
4. **Inyeccion de Jinja desde los datos** [V codigo, I efecto]: `replace_markers` sustituye valores y luego ejecuta Jinja sobre toda la linea, de modo que un valor que contenga `{{ ... }}` o `{% ... %}` se evalua (vacio o error). No vi prueba ni escape. Si kimun reemplaza el render, hay que decidir si se conserva.
5. **Listas**: el render usa solo el primer item del template como molde; en extraccion cada item con marcador da una receta y se extrae el texto crudo del item (con su etiqueta si la hay: `- run: x` extrae `run: x`, no `x`) [V handlers/list.py]. Items dict (varios marcadores por item) se renderizan pero no vi extraccion simetrica [I].
6. **Tabla con marcadores por columna** (22): sin test dedicado que haya abierto; solo hay `table[...]` y `table` inferido en tests. Render usa `lines[2]` como plantilla de fila: una tabla con mas de una fila en el template usa solo la primera [V].
7. **Marcador `render` solo en un parrafo se compila como ancla `^.*?$`** [V text.py:58-59 y regex]; coincide con cualquier parrafo de una linea en esa posicion del extractor [I], lo que podria comerse un parrafo ajeno tras un campo compuesto. No lo probe.
8. **Docs desactualizados**: `docs/ast_query_primitives.md` y `addressability_model.md` dicen que el rango de linea de una seccion cubre solo el heading, pero `extract_sections._extend_section_spans` ya lo extiende hasta la siguiente seccion de igual o mayor nivel [V]. `docs/composition_modes.md` tiene un comentario `<!-- gone -->` huerfano. Tambien dice que la arista `composes` existe y que no hay edicion de vuelta: coincide con el codigo.
9. **No abri**: `sldb.api.schema.field_kinds` (de donde sale `kind` de cada segmento), `sldb.store.source_guard` / `source_read` (guarda de lectura), `provenance_block.strip_block`, la logica de `edges/composition_targets`, ni `api/documents/*` salvo `rewrite_loss`. Tampoco los tests `test_field_corpus.py` (corpus embebido por campo: aparece por el nombre; es otra area), `test_links_batch_cli.py`, `tests/api/test_rewrite_loss.py`. Los tests solo los lei por nombre y por las secciones que cito.
10. **Modos extraccion "lenientes"**: la extraccion no falla ante un documento que no cumple el template: lo que no coincide queda como campo ausente y recien pydantic protesta si era requerido [I]. Por eso existe `rewrite_loss`, que detecta contenido que una reescritura perderia.
11. **Rendimiento**: `FieldRefResolver` carga todos los documentos del store para resolver una referencia (cache solo por instancia); `docs/addressability_model.md` admite que toda lectura por direccion extrae todos los documentos. [V]
