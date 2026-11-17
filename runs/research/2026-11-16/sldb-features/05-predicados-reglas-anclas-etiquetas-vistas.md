# sldb: predicados, reglas, anclas, etiquetas, selectores, where y vistas

Inventario de funciones para diseñar kimun como núcleo de sldb. Fuente: `/home/jp/proyectos/SLDB_1/sldb` (repo git propio; HEAD entra hasta 2026-11-16) y `/home/jp/proyectos/SLDB_1/deskops`. Solo lectura. Marcas: [V] verificado en el archivo citado, [I] inferencia mía. Las rutas `src/...` son relativas a `/home/jp/proyectos/SLDB_1/sldb/`. Casi todo lo de esta área entró entre 2026-10-16 y 2026-11-16 (ver "reciente" en cada entrada; el chequeo fue `git log --since=2026-10-01`).

## 1. Para qué sirve esta área

Es la capa que decide y explica "qué documentos cumplen qué". Un único lenguaje de predicados (parser propio, lógica trivaluada, caminos por el grafo de referencias) sirve para: filtrar (`--where`, selectores, vistas), declarar reglas como documentos (`RuleDoc`) que se evalúan al escribir, al transicionar y al chequear, condiciones de aristas de ciclo de vida, y filtros de facetas de consultas estructuradas. Encima viven las anclas a documentos (valor de campo `doc:Modelo:nombre@sha256:...` con estado `intact|changed|lost` derivado del hash de contrato del destino), las anclas a código (`code:ruta::nombre` con fingerprints tree-sitter), los espacios de nombres de tags (declarados en `store.yaml`, comprobados como una familia de reglas incorporada), el DAG semántico de tags, los selectores de documentos (`select_documents`) y las vistas declaradas (`ViewDoc` con `params`/`sections`, servidas con `revision`/ETag). deskops usa casi todo: sus compuertas de activación y cierre de tareas son 16 `RuleDoc`.

## 2. El lenguaje de predicados tal como está implementado

Código: `src/sldb/store/graph/language/` (tokens, parser, AST, átomos, términos, caminos, alcance, evaluación, explicación). Doc de contrato: `docs/architecture/rules-language.md` (lenguaje único desde GO05, 2026-11-06; GO10 retiró `where_parse` y `FieldCondition`). [V]

### 2.1 Tokens (`predicate_tokens.py:16-24`)

Regex única, sin transformaciones: [V]

- literal: `"(?:[^"\\]|\\.)*"`. Solo comillas dobles. El valor se desescapa con `unquote` (`store/query_engine/where_literal.py:19`): únicamente `\"` y `\\`; cualquier otra barra invertida sobrevive tal cual (importa para regex: `"\d+"` llega como `\d+`).
- número: `-?\d+(\.\d+)?` (entero si no hay punto, si no float). No hay notación científica ni `+`.
- ident: `[A-Za-z_][\w.]*`. Un nombre incluye los puntos: `sldb.authority`, `tasks.0.title` son un solo token. No admite guion (`-`).
- op: `!=`, `<=`, `>=`, `=`, `<`, `>`, `~`, `(`, `)`, `[`, `]`, `,`, `{`, `}`, `:`, `^`, `+`, `.`.
- Espacios sin significado. Cualquier carácter fuera de estos es `WherePredicateError(texto completo)`.
- Palabras clave (en minúscula, sensibles a mayúsculas): `and or not in has nonempty model any all none count rel`. No están reservadas como nombres de campo: se reconocen por posición (p. ej. `model` es palabra solo si la sigue `<=`; `has`/`nonempty`/`count`/`any`/`all`/`none` solo si las sigue `(`, `predicate_atoms.py:18-24`). [V]

### 2.2 Gramática efectiva (de `predicate_parser.py`, `predicate_atoms.py`, `predicate_terms.py`, `predicate_paths.py`)

```
expr       := or
or         := and ("or" and)*            # asociativo a la izquierda
and        := not ("and" not)*           # asociativo a la izquierda
not        := "not" not | primary
primary    := "(" expr ")" | atom
atom       := "has" "(" ident ")"
            | "nonempty" "(" ident ")"
            | "model" "<=" ident
            | ("any"|"all"|"none") "(" path ":" expr ")"
            | "count" "(" path ":" expr ")" cmp term     # count solo como lado izquierdo
            | term "in" term
            | term "~" literal                            # el patrón debe ser literal y compilar con re
            | term cmp term
cmp        := "=" | "!=" | "<" | "<=" | ">" | ">="
term       := ident | literal | número | lista | "{" ident "}"
lista      := "[" [(literal|número) ("," (literal|número))*] "]"   # escalares, sin anidar
path       := group ("." group)*
group      := ["^"] names ["+"]
names      := ident(con puntos = varios pasos de campo) | "rel" ":" ident ...  # `rel:<tipo>` en cualquier posición
```

Detalles verificados: [V]

- Precedencia: `not` > `and` > `or`. Las comparaciones no encadenan (`a < b < c` no parsea porque `operation` consume un solo `cmp` y luego espera EOF o `and/or/)`).
- Todo el texto debe consumirse; si no, `WherePredicateError` con la expresión completa (no la posición). Un regex inválido se rechaza al parsear (`predicate_terms.py:73-81`).
- `parse_predicate` está cacheado con `lru_cache(512)` (`predicate_parser.py:18`).
- Paso de camino (`predicate_paths.py`): `campo` = aristas `references` cuyo `field` es ese nombre; `rel:<tipo>` = aristas de ese tipo de relación; `^` invierte solo el primer paso del grupo; `+` (clausura transitiva) aplica solo al último paso del grupo; un nombre con puntos son N pasos de campo (`depends_on.subject`). Un campo llamado `rel` seguido de `:` se lee como paso de relación (H12).
- `count` solo se admite al inicio de un átomo: `0 = count(...)` no parsea. [V]
- No existe literal nulo ni booleano. `{campo}` es sustitución del sujeto (ver 2.4).

### 2.3 Semántica de evaluación

- Árbol: tuplas `("and"|"or", cláusula, izq, der)`, `("not", cl, hijo)`, `("leaf", cl, kind, args)` con kind `has|nonempty|model|in|regex|cmp`, `("quant", cl, any|all|none, path, cuerpo)`, `("count", cl, path, cuerpo, op, término)` (`predicate_ast.py`). Cada nodo guarda el texto fuente que lo originó (la "cláusula"). [V]
- Hojas (`predicate_leaves.py`): [V]
  - Campo: se lee por clave exacta y si no por ruta con puntos (`where_lookup._lookup`: dict por clave, lista por índice dígito). Un campo ausente no cumple ninguna comparación, `in` ni `~` (H14); una comparación que Python no puede hacer (`"a" < 1`) es falsa.
  - `has(f)`: presente y no `None` ni `""`. `nonempty(f)`: presente, no `None`, y si es str/list/dict/tuple/set no vacío. Un `0`, `False` cumplen `has`.
  - `model <= X`: el `model` del nodo es `X` o `X` está en `base_models`.
  - `a in b`: verdadero si `b` es lista o string y `a in b` (miembro o subcadena); cualquier otro tipo de `b` es falso; `a` ausente es falso.
  - `f ~ "re"`: `re.search` (no ancla), sobre `str(valor)`. El caso `doc ~ "re"` lee el nombre del documento, no un campo `doc` (H13, `predicate_leaves.py:73`).
  - Comparación: `operator.eq/ne/lt/le/gt/ge`; `=` sobre lista compara la lista entera.
- Trivaluado, Kleene fuerte (`predicate_explain.py`): valores `True|False|None` (None = `incomplete`). `and` decide con False, `or` con True; el lado derecho se evalúa solo si hace falta (cortocircuito). `not None = None`. `any/all/none` generalizan or/and (`none` = no-`any`); conjunto vacío: `any` falso, `all` verdadero, `none` verdadero. `count(...) cmp t` es `incomplete` si algún elemento lo es; si no, cuenta los elementos con valor True. Un elemento incompleto hace `incomplete` todo predicado sobre él (`predicate_eval.py:17`). [V]
- Alcance de caminos (`predicate_reach.py`): lee las aristas del `view` (`out(id)`/`in(id)`); un destino que la vista no tiene se omite (mundo cerrado); una arista con `unavailable` produce un elemento incompleto; la clausura `+` es BFS con visitados, excluye el origen, sin duplicados, termina con ciclos. [V]
- `anchor_state` en el lenguaje (`predicate_reach.py:46-74`): al cruzar un campo `F` del origen cuyo payload trae `F__anchor_state`, el elemento alcanzado expone en su payload la entrada bajo el nombre `anchor_state` (para listas, el elemento `i` lee la entrada `i`, buscándolo por `declared` o por `target`). Al cruzar `^F`, la entrada se lee del elemento alcanzado (que es el que tiene `F`). Sin entrada, `anchor_state` queda indefinido (la comparación es falsa). [V]
- Dominio de una evaluación sin sujeto (`predicate_api.py:45-49`): todos los documentos de la vista, o la intersección de las familias de los `model <= X` de la conjunción de nivel superior (`top_level_models`); un `model <=` dentro de `not`/`or`/cuantificador no restringe. [V]

### 2.4 Sustitución `{campo}` (`predicate_api.py:25-55`, `predicate_terms.py:52`)

`{campo}` es un término que lee el valor de primer nivel del sujeto (por defecto el payload del propio elemento evaluado; en `where_matches` se puede pasar `subject`). Si el valor falta, es lista/dict o es booleano (`scalar_literal` excluye `bool`, `where_literal.py:47`), todo el predicado es `false` con `clause` = texto completo y `seen` = los valores leídos, incluso dentro de `not`. Dentro de un literal entre comillas, `"{x}"` no se sustituye (es un literal). Corre también dentro de cuerpos de cuantificadores (lee del sujeto, no del elemento). [V]

Aparte existe `where_subject.fill_braces` (`store/query_engine/where_subject.py:19`), el sustituidor textual antiguo, hoy solo conservado por `payload_matches`; el lenguaje nuevo ya no lo usa para evaluar. [V] (`payload_matches` llama a `payload_holds`).

### 2.5 Explicación: el finding (`predicate_explain.py:18`)

`{node, holds: "true"|"false"|"incomplete", clause, seen}`. `seen` es el mapa campo→valor leído de la rama que decidió: en `and` la primera rama falsa, en `or` la primera verdadera, en cuantificador el elemento testigo (primero que falsea un `all`, primero que cumple un `any`); `any` falso, `all` verdadero y `count` no tienen testigo (`seen = {}`, H16). Hoja incompleta: `{"incomplete": "<id sin resolver>"}`. [V]

### 2.6 Hash de contrato (`contract_hash.py`)

`sha256` de las líneas `"<campo>=<field_hash>"` ordenadas por bytes UTF-8 del nombre, unidas por `\n` sin salto final. Campos: los de `__contract__` de la clase del modelo; sin `__contract__` son todas las claves de `field_hashes`; `__contract__ = []` declarado es `ValueError` (H11); un campo listado ausente aporta `campo=`. `field_hashes` viene de `DocumentEntry.field_hashes` del shard. [V]

### 2.7 Estructura de la consulta estructurada (`structured_query.py`, `facet_filter.py`, `graph_scope.py`, `relation_filter.py`)

Modelos pydantic: `StructuredQuery{filters: [FacetFilter{facet, where}], relations: [RelationFilter{relation_types, direction: outgoing|incoming|both}], scope: GraphScope{descendant_of | ancestor_of | node_id_prefix}}`. El `where` de cada filtro es un predicado sin caminos (un `quant`/`count` es `SLDBGraphArgumentError`, `payload_match.py:49`); la faceta `payload` lee la proyección del índice; un campo que ningún modelo proyecta es error (`executor.py:30`). En `candidate_ids` solo se aplica un criterio de `scope`: gana el primero de `descendant_of`, `ancestor_of`, `node_id_prefix` (`executor.py`). [V] Entrada CLI `sldb graph query` (archivo JSON).

### 2.8 Dónde se evalúa el lenguaje (todos pasan por `predicate_api`/`predicate_documents`)

[V] salvo indicación.

| Quién | Entrada | Vista de grafo | Archivo |
|---|---|---|---|
| `where_matches(expr, payload, subject)` | payload suelto | ninguna (los caminos no alcanzan nada) | `src/sldb/api/where.py:23` |
| `where_records(records, expr, store, ...)` (`sldb find --where`) | registros `kind==doc` | índice del store solo si `has_paths(expr)` | `api/where.py:55` |
| `where_findings(store, expr, subject)` | proyección del índice | índice | `api/where.py:40` (sin llamadas fuera de un test) |
| `GraphBackend.eval` / `NetworkXBackend.eval` | export id o payload | índice compuesto | `store/graph/backend/networkx_backend.py:61` |
| `select_documents` / `select_rows` (`where` del selector) | proyección del índice | `compose_edge_index` + `index_view` | `store/query_engine/document_selection_filters.py:47` |
| `find_structural` (`sldb query find st.{M+}`) | payload extraído de cada doc | ninguna (`EMPTY_VIEW`) | `store/query_engine/structural_queries.py:87` |
| `find_semantic` (`se.`) | payload extraído | ninguna | `store/query_engine/semantic.py:79` |
| `RecordFilters`, `cli/commands/find_filter.py` (duplicado) | registros doc | ninguna | `api/records/record_filters.py:22` |
| `RuleDoc` (`subject`, `where`, `condition`) | proyección | `RuleGraph` (índice, con o sin links) | `api/rules/*` |
| Condición de arista `edges_holding` / `view_transitions` | payload sujeto | ninguna | `api/edges/edges_holding.py:52`, `api/views/view_transitions.py:34` |
| Filtros de faceta de `StructuredQuery` | proyección | ninguna (caminos rechazados) | `store/graph/payload_match.py:41` |
| Validación de `ViewDoc` (`sections[].select.where`, `nodes[].where`) | solo parseo | n/a | `models/notation/view_doc_checks.py:70`, `api/notations/projection_filters.py:40` |
| Proyecciones de grafo del proyecto, plantillas de notación, `closed` de `DocumentSurface` | payload suelto | ninguna | `api/edges/project_graph/project_texts.py:51`, `api/notations/template_render.py:53`, `api/edges/project_graph/document_surface.py:34` |
| `sldb edges check` (la condición debe parsear) | texto | n/a | doc `rules.md` §9.6 [V solo doc] |

Diferencia relevante [V]: `select_documents.where` lee la **proyección del índice** (solo campos escalares/listas de escalares o `__indexed__`; no extrae el archivo), mientras `find_structural`/`find_semantic`/`RecordFilters` leen el **payload extraído completo** (`docs/architecture/selectors.md`, "Where it lives"). Los caminos (`any(...)`) solo funcionan donde hay vista de grafo.

### 2.9 Desviaciones de `rules-language.md` respecto al código

1. La BNF del doc (§1) no incluye `{campo}` en `term`; el código sí (`predicate_terms.py:47`); el texto de §2 lo describe, así que es omisión de la gramática, no de comportamiento. [V]
2. Nombres "provisionales" que ya son definitivos: `sldb.store.query_engine.rules` (H1) sigue siendo solo fachada de tests (`store/query_engine/rules.py`, 23 líneas). [V]
3. H4 (`unavailable` por store enlazado que no abre): el único productor de `unavailable` es `predicate_view.edge(...)` con el argumento en `False` por defecto. En `src/sldb/store/edge_index/` no hay ningún sitio que ponga `metadata.unavailable` (grep sin resultados fuera del lenguaje). El valor `incomplete` es, por tanto, inalcanzable con datos reales hoy: [I] está listo en el evaluador, pero sin productor. Las reglas con `incomplete` solo se prueban con el grafo de test (`graph_view`).
4. `rules.md` §3 dice que el grafo de las reglas "es el índice compuesto con sus stores enlazados"; el código desde 2026-11-10 compone los enlazados solo si las reglas pueden leerlos (`rule_reach.needs_linked`, ver F-R7). [V]
5. El doc dice de los campos "no se proyecta un diccionario": es R1 y se cumple en `rules`, `select_documents` y filtros de faceta, no en `where` sobre documento extraído. [V]

## 3. Lista de funciones (una entrada por función)

Formato: **Nombre** — qué hace — dónde — cómo se llega — lee/escribe — tests — reciente.

Tests principales de toda el área: `tests/test_rules_language_spec.py`, `tests/store/test_predicate_language.py`, `tests/store/test_where_empty_and_errors.py`, `tests/store/test_where_retired.py`, `tests/api/test_rules.py`, `test_rules_evaluation.py`, `test_rules_end_to_end.py`, `test_rules_linked_reach.py`, `test_transaction_rules_dirty.py`, `test_doc_anchors.py`, `test_anchors.py`, `test_tags.py`, `test_tags_scope.py`, `test_select_documents.py`, `test_view_doc.py`, `test_views.py`, `test_views_linked.py`, `test_run_view_reads.py`, `test_api_semantic.py`, `tests/store/test_semantic_dag_walk.py`, `tests/store/test_edge_anchor_targets.py`. [V que existen; no los abrí uno a uno salvo donde se dice]

### 3.1 Lenguaje y `--where`

**F-L1. Parser y AST de predicados.** Convierte texto en un `Predicate(text, root)` inmutable, cacheado. Rechaza regex inválidas y todo texto no consumido con `WherePredicateError(texto)`. `src/sldb/store/graph/language/predicate_parser.py:19`. API `parse_predicate`. Lee texto, no escribe. Test `tests/test_rules_language_spec.py`. Reciente (2026-11-06, GO05).

**F-L2. Evaluador trivaluado con explicación.** Evalúa un árbol sobre un elemento y devuelve `(valor, cláusula, seen)`; `finding()` lo vuelve `{node, holds, clause, seen}`. `predicate_eval.py:15`, `predicate_explain.py:18`. Vía `findings`, `node_finding`. Reciente.

**F-L3. Caminos, inversa y clausura.** Pasos por campo, `rel:<tipo>`, `^`, `+`, varios pasos con `.`; alias de `anchor_state`. `predicate_paths.py`, `predicate_reach.py`. Lee la vista (`out/in/node/ids`). Reciente (GO05/GO07).

**F-L4. Vista mínima de grafo.** Diccionario de cuatro funciones (`node`, `out`, `in`, `ids`) más constructores `make_view`, `graph_view` (grafo de test), `with_node` (sustituye un nodo por otro payload). `predicate_view.py`. Reciente.

**F-L5. Documento como nodo y matcher.** `document_node(doc)` toma modelo, bases (MRO por nombre) y payload de un documento runtime; `document_matcher(expr)` devuelve `(doc, ...)→bool` parseado una vez; `payload_holds` evalúa sobre un payload suelto con `subject` opcional (expresión vacía siempre cumple; `model <=` nunca cumple sin modelo). `predicate_documents.py`. Reciente.

**F-L6. `where_matches` (API pública).** `where_matches(expression, payload, subject=None) -> bool`; levanta `WherePredicateError`. `api/where.py:23`. Exportada en `sldb.api` (`api/__init__.py:90`). Usada por deskops (`deskops/lifecycle.py:137`). Reciente.

**F-L7. `where_records` (`sldb find --where`, `--explain`).** Evalúa por el lenguaje los registros de tipo doc (payload + el bloque `sldb` de proveniencia como campo `sldb.*`), conserva solo `holds == "true"` (un `incomplete` no entra) y devuelve también los findings; registros de campo y de sección usan una lectura propia ligada a `RecordFilters`. `api/where.py:55`, `cli/commands/find.py:16`, `api/search.py:47`, `api/records/record_filters.py`. CLI `sldb find ... --where W [--explain]`; MCP `select_documents` también lleva `where`. Lee el índice solo si el predicado tiene caminos. Reciente.

**F-L8. `where_findings` y `store_view`.** Evalúa un predicado sobre un store completo (un finding por documento del dominio, o el de un sujeto) leyendo la proyección del índice, sin re-extraer. `api/where.py:40`, `:50`. No hay otra llamada que el test `tests/store/test_predicate_language.py`; no está re-exportada en `sldb.api` (grep). [I] media-muerta (solo la usa el test y la documentación la menciona). Reciente.

**F-L9. Fachada de tests.** `eval_predicate(pred, grafo_dict, sujeto)`, `contract_hash`, `parse_predicate`. `store/query_engine/rules.py`. Solo para `tests/test_rules_language_spec.py`.

**F-L10. Filtro de faceta y consulta estructurada.** `FacetFilter{facet, where}` más `StructuredQuery`; el `where` es un predicado sin caminos evaluado sobre los campos de la faceta (None = ausente). `store/graph/payload_match.py`, `store/graph/executor.py`. CLI `sldb graph query <archivo.json>` (`cli/parsers/graph.py:40`); un JSON con la clave vieja `conditions` es `ValidationError`. Reciente.

**F-L11. Lenguajes antiguos retirados.** `where_parse` y `FieldCondition` ya no existen; `tests/store/test_where_retired.py` y goldens en `tests/fixtures/where_goldens/` cubren la equivalencia (`rules-language.md` §7, §9). [V]

### 3.2 Reglas (`src/sldb/api/rules/`, `src/sldb/models/rule_doc.py`)

**F-R1. `RuleDoc`.** Documento con `subject` (solo `model <= X` o conjunción de ellos), `where` (opcional), `condition`, `severity` (`error|alert`), `moment` (`on_write|on_transition|on_check`), `message`, `title`. Los validadores pydantic garantizan que `condition` y `where` parsean y que `subject` es solo familias (`models/rule_doc.py:60-70`). Modelo builtin `sldb.models.rule_doc:RuleDoc`; se registra con `sldb models add`. Reciente (2026-11-06).

**F-R2. Rechazo de familia no registrada.** `refuse_unknown_family(store_path, model_type, payload)`: al guardar un RuleDoc, cada `X` de su `subject` debe ser un modelo registrado (`SLDBStoreError: Model 'X' not registered.`). `api/rules/rule_writes.py:15`. Lo llaman `create_document`, `save_document_payload`, `docs update`. Una regla ya escrita y trackeada sin pasar por ahí no se comprueba (R3 del doc).

**F-R3. Qué reglas hay y a quién aplican.** `rules_of(graph)` = todos los `RuleDoc` o submodelos locales (leídos de la proyección, no del archivo); `applies(graph, rule, node)` es `None` si `subject` da `true` y `where` (si hay) da `true`; si no, `("subject", texto)` o `("where", cláusula)`. `api/rules/rule_subjects.py:19-35`. Reciente.

**F-R4. Resolución de referencias.** `resolve_subject` acepta export id (`Modelo:nombre`, `store:Modelo:nombre`), nombre, `Modelo/nombre` o ruta (absoluta o relativa); `resolve_rule` acepta `RuleDoc:nombre`, `nombre` o ruta; ambos levantan `SLDBDocumentTargetError("Unknown document target: ...")`. `rule_subjects.py:43-73`. Reciente.

**F-R5. `rules check [--subject]`.** Con sujeto evalúa todas las reglas que le aplican (cualquier momento); sin sujeto solo las `on_check`, sobre documentos locales (los enlazados no se recorren). Convierte `false` en `Finding(level=severity, code="rules/<nombre>")` e `incomplete` en `alert` con código `rules/<nombre>/incomplete`. La explicación es `[<message> | ]<cláusula> (seen: <JSON compacto>)[ (witness: <Modelo:nombre> <seen>)]`. `api/rules/rules_check.py:23-58`. CLI `sldb rules check [--subject DOC] [--store] [--pythonpath] [--format text|json|yaml]`, exit 1 solo si hay `error`. Lee índice/proyecciones; no escribe. Test `tests/api/test_rules.py`. Reciente.

**F-R6. `rules explain RULE SUBJECT`.** Devuelve `{rule, subject, finding}` (también si se cumple) o `{rule, subject, applies:false, reason: subject|where, detail}`; `explain_exit`: 0 true, 1 false, 2 incomplete, 3 no aplica; la severidad no cuenta. `api/rules/rules_explain.py`. CLI `sldb rules explain`. Reciente.

**F-R7. Alcance federado de las reglas.** `rule_graph(store, linked=None)` compone los stores enlazados solo si `rule_reach.needs_linked` lo exige: (1) las familias de modelos locales no son iguales en el índice propio, (2) algún camino usa un paso `rel:`, (3) un campo nombrado en un camino tiene referencias sin resolver que apuntan a un store enlazado o aristas a un documento no local, (4) un ancla de documento local nombra un store enlazado; ante un texto que no parsea, lee la federación. `api/rules/rule_reach.py`, `rule_graph.py:73-85`. Reciente (2026-11-10).

**F-R8. `RuleGraph.with_payload`.** El mismo grafo donde un nodo lee otro payload (el que se va a escribir o el sujeto de una transición), con modelo y aristas del índice. `rule_graph.py:34-60`. Reciente.

**F-R9. Momento `on_write`.** `check_on_write(store_path, model_type, node, payload)`: dentro de `save_document_payload` justo después del pinneo de anclas y antes de renderizar; la regla lee el payload ya pinneado con `<campo>__anchor_state` calculado como lo compondría el índice (`anchored`); aristas del índice anterior a la escritura. Un finding `error` levanta `SLDBRuleViolationError` y no se escribe nada; los `alert` se emiten por `sldb.core.finding_route.route` pase o no. Marca el dirty set. `api/rules/rules_on_write.py:30`; hooks `api/documents/payload_save_steps.py:78` y `api/documents/on_write_gate.py` (`check_entering`, `check_entering_text`) para `create_document` y `track_document_file` (sin aristas previas; un archivo trackeado con `force` que no se extrae no se evalúa). Tests `tests/api/test_rules_evaluation.py`. Reciente (2026-11-06; alta H4 después).

**F-R10. Momento `on_transition`.** `transition_findings(store, document, subject)` evalúa las reglas `on_transition` que aplican al documento leyendo el `subject` como payload; un error bloquea todas las aristas que salen de ese estado (`edges_holding` devuelve `[]`, `edges_blocked` devuelve las bloqueadas con sus findings). Las reglas son del sujeto, no de la arista. Sin `document` no corre ninguna regla. `api/rules/rules_on_transition.py`; consumidores `api/edges/edges_holding.py`, `edges_blocked.py`, `api/views/view_transitions.py`. CLI `sldb edges show NODE --holds-for DOC`. Sin RuleDoc registrado en el store no corre nada (`has_rules`). Reciente.

**F-R11. Momento `on_check` incremental.** `rules_on_check(store)` por regla y documento usa la caché y recalcula solo lo sucio; `on_check_findings` suma el resultado, la familia incorporada de tags (sin caché) y `check_doc_anchors` (sin caché). Lo invoca `sldb stores check`, `check_store`, `GET /stores/check`. `api/rules/rules_on_check.py`. Escribe caché `.sldb_cache/rules/` bajo `store_lock(wait=True)`. Reciente.

**F-R12. Caché de reglas y dirty set.** `<.sldb_cache>/rules/<content_hash de la RuleDoc>.json` (`{version:1, results, stamps}`) y `dirty.json`. Escribir D agrega D y sus vecinos a un salto (aristas entrantes y salientes). Un archivo ausente, ilegible o de otra versión = todo sucio. Cada resultado guarda un stamp (`sha256` del `source_hash` + payload del documento y vecinos, o del store entero si la regla es ancha); un cambio fuera de escritura recalcula. Regla "ancha" = cualquier clausura `+`, camino de más de un paso o cuantificador anidado (`dirty_set.wide`; solo mira `condition` y `where`, no `subject`). Se podan los archivos de hashes de reglas que ya no existen. `api/rules/rule_cache.py`, `dirty_set.py`. La caché nunca es autoridad. Reciente.

**F-R13. Testigo (witness) y punto fijo.** Los únicos hechos derivados son los conjuntos de clausura `+` (semi-naive, en memoria, nunca persistidos). `witness(graph, predicate, found)` nombra el elemento detrás de un cuantificador de nivel superior que decidió (primero que falsea un `all`, que cumple el `any` interno de un `none`, el primero alcanzado en un `any` falso, el primero incompleto). `api/rules/fixpoint.py`. Reciente.

**F-R14. Familia incorporada `builtin:tag_namespace:<nombre>`.** Ver F-T2; corre donde corren las reglas (`on_write`, `on_check`, `rules check`, `rules explain`) también en un store sin RuleDoc que declare `tag_namespaces`. Reciente.

**F-R15. Alertas y violaciones.** `Finding(level, code, subject, what, fix, document)` (`sldb.core.finding`), `SLDBRuleViolationError(findings)` (`sldb.core.exceptions`), canal `finding_route.route`/`alert_sink`; envelope `rules/violation` exit 1 y `rules/failed`. [V doc; abrí rules_check y rules_on_write]

### 3.3 Anclas a documentos

**F-A1. Forma de un ancla.** `doc:Modelo:nombre` o pinneada `doc:Modelo:nombre@sha256:<64 hex>`; regex `doc:([^:@\s]+):([^@\s]+)(?:@sha256:([0-9a-fA-F]{64}))?` (`models/doc_anchor_value.py:19`). `parse_doc_anchor` devuelve `(Modelo:nombre, hash|None)`; el hash se baja a minúsculas. Reciente (GO07, 2026-11-06).

**F-A2. Declaración en el modelo.** Atributo de clase `__doc_anchors__ = [nombres de campos]` (str o list[str], opcionales o no) más cada elemento de `from:` de la proveniencia que empiece por `doc:`. Validadores: `check_anchor_fields` (en el modelo base) y `check_from_items` (`Provenance`); `definition_problems` rechaza al cargar la clase (`SLDBModelError`) un nombre que no es campo, no es `str`/`list[str]`, o un campo que termine en el sufijo reservado `__anchor_state`. `models/doc_anchor_value.py`, `store/model_contract.py:53`. Ejemplo real: `deskops/models/review.py:48`.

**F-A3. Pinneo al guardar.** `pin_payload(store_path, model_type, payload)` completa un valor sin `@sha256` con el hash de contrato actual del destino; si el destino no está trackeado el valor queda sin hash; un valor ya pinneado no se repinnea. `pin_provenance(store_path, text)` hace lo mismo con `from:` del bloque `sldb`. `api/documents/doc_anchor_pinning.py`; llamado en `payload_save.py:46`, `payload_save_steps.py:65,87`, y `create_document` (`create_document.py:~55`). Escribe el campo y/o el bloque de proveniencia. Reciente.

**F-A4. Estado de un ancla: `anchor_state`.** `intact` si el hash visto es el contrato actual del destino; `changed` si difiere; `lost` si el destino no está trackeado o el ancla no trae hash (aunque el destino aparezca después). Un `__contract__` declarado vacío da hash `None` → `changed`. Calculado al componer el índice (`with_anchor_states`) y al pinneo, nunca escrito en shards (no sube `EDGE_INDEX_VERSION`). La entrada se añade a una copia del payload del nodo como `<campo>__anchor_state` (escalar para `str`, lista alineada por índice para `list[str]`, `null` en elementos vacíos). `store/edge_index/doc_anchor_states.py`. No hay `reconnected` para documentos. Reciente.

**F-A5. Aristas de ancla.** Cada valor no vacío de un campo `__doc_anchors__` genera una arista `references` con `field`=nombre del campo hacia `Modelo:nombre` (sin sufijo) y `declared`=valor tal como se escribió; así existe `^campo` en el grafo. Un destino inexistente no produce arista; `from:` no produce aristas. `store/edge_index/doc_anchor_edges.py`. Reciente.

**F-A6. `check_doc_anchors(store, pythonpath)`.** Un `Finding` de nivel `alert` por ancla no intacta: códigos `anchors/doc_changed`, `anchors/doc_lost`, `anchors/provenance_changed`, `anchors/provenance_lost`; `what` = `campo -> destino: estado (valor)`. Solo lee. Lo llama `on_check_findings`, y deskops (`deskops/services/closure.py:1177`). `api/anchors/check_doc_anchors.py:30`. Compone el índice **sin** links (`include_linked=False`). Reciente.

**F-A7. Alias de `anchor_state` en el lenguaje.** Ver 2.3. Uso real: `any(^subject: anchor_state = "intact" and verdict = "accepted")` en 13 reglas de deskops.

### 3.4 Anclas a código

**F-C1. Referencia `code:<ruta>::<nombre_calificado>`.** Ruta relativa con `/`, sin `..` ni absoluta, con extensión `.py|.ts|.tsx|.js|.jsx`; nombre vacío = el módulo; un nombre repetido lleva `~2`, `~3`. `models/code_ref.py`. Extensiones TS/JS entraron el 2026-11-16 (commit 600b51a9).

**F-C2. `CodeAnchorDoc`.** Modelo con `ref`, `kind` (`module|class|function|method`), `seen` (dict: `signature`, `shape`, `docstring` (null si no hay), `body`), `normalization` (versión de reglas, hoy `1` en `code/fingerprints.py:18`), `recorded_with` (`treesitter-symbols/1 (tree-sitter-python <ver>)`; para TS/JS agrega la gramática), `history` (eventos `recorded`/`reconnected`, nunca se borra), `claim`. `__authority__ = "theorem"`. `models/code_anchor_doc.py`. Reciente (2026-10-20).

**F-C3. `anchors record REF [--write PATH --claim ... --name ...]`.** `record_anchor(root, ref)` escanea con tree-sitter y devuelve `{ref, kind, seen, normalization, recorded_with}`; con `--write` crea o re-graba el `CodeAnchorDoc` pasando por la API de documentos (queda firmado como cualquier documento; en re-graba conserva claim e historia). Errores: `ValueError` (forma, archivo, símbolo), `TreeSitterUnavailable` (falta el extra `[code]`). `api/anchors/record_anchor.py`, `anchor_docs.py:write_anchor`. CLI `sldb anchors record`. Escribe un documento. Reciente.

**F-C4. `anchors check [--record]`.** Un `AnchorReport` por `CodeAnchorDoc`: estados `intact` (ok); `changed` (alerta, lista cuál de signature/docstring/body); `reconnected` (alerta, un único candidato); `lost` (alerta, ninguno o varios); `renormalize` (alerta); `unparseable`, `invalid`, `unavailable` (errores). El nivel sale de `AnchorState.level` (`models/anchor_state.py`). Finding con código `anchors/<estado>`. `--record` es el único que escribe: guarda la reconexión (nuevo `ref`, firma y forma nuevas, docstring y body viejos, evento `reconnected` en `history`). `api/anchors/check_anchors.py`, `anchor_report.py`, `reconnect.py`. CLI `sldb anchors check [--record]`; exit 0 con solo alertas. Reciente.

**F-C5. Heurística de reconexión.** Candidatos del mismo `kind` (nunca módulo): primero el mismo archivo, luego todo el proyecto; coincide si el cuerpo es igual o forma y docstring son iguales; uno = `reconnected`, ninguno o varios = `lost` con lista. `api/anchors/reconnect.py`. Reciente.

### 3.5 Tags y espacios de nombres

**F-T1. Tags de documento.** `payload_tags(payload)` junta `semantic_tags` y `tags` (strings recortados); `split_namespace(tag)` corta en el primer `:`; `namespaced_tags` da pares `(namespace, tag)` sin repetir; `tag_prefixes('a.b.c') = ['a','a.b','a.b.c']`. `store/semantic_tags.py`. También `flatten_model_semantics` / `collect_document_semantic_tags` (tags de la clase `__semantics__` como `k.v1.v2`, más los del documento). Reciente (namespaces: 2026-11-06).

**F-T2. `TagNamespace` y la declaración.** Entrada de `tag_namespaces` en `.sldb/store.yaml`: `name` (sin `:` ni espacios), `hierarchical` (por defecto true: `a.b.c` es camino y sus prefijos son tags), `folder_axis` (solo declarativo), `anchored_by` (`{model, field[, value: tag|bare]}`; `bare` = el campo guarda `a.b` y el tag exigido es `ns:a.b`). `TagNamespace.required(tag)` da los tags que deben estar anclados (los prefijos, o solo el valor si no es jerárquico); `anchor_tag(valor)` decide qué tag ancla un campo. Más `tag_models` (lista de modelos; vacío = todos) en la declaración. `api/stores/tag_namespace.py`. Un nombre repetido es error de `StoreDeclaration`. Reciente (2026-11-06 y 2026-11-11).

**F-T3. Familia de reglas incorporada.** Para cada documento local en alcance (`tag_models`), por cada namespace que su payload nombre: la regla `builtin:tag_namespace:<ns>` se cumple si el namespace está declarado (`clause: "namespace declared"`) y, con `anchored_by`, si cada tag requerido es el `field` de un documento local de `model` (incluyendo el documento que se escribe). Clause `each prefix anchored by Model.field`, `seen {namespace, tags, missing}` (faltantes en orden de arriba hacia abajo, una vez cada uno). Todo hallazgo es `error` con código `rules/builtin:tag_namespace:<ns>`. Un documento fuera de `tag_models` pasa con clause `model in tag_models`. `api/rules/builtin_tag_rules.py`, `builtin_tag_findings.py`. Corre en `on_write` (incluida `replace_text`), `on_check` (sin caché), `rules check`, `rules explain` y `tags check`. Solo lee documentos indexados (H7). Reciente.

**F-T4. `tags namespaces`.** Lista los namespaces declarados como datos (`{name, hierarchical, folder_axis, anchored_by}`). `api/tags/tag_check.py:15`. CLI `sldb tags namespaces`. Reciente.

**F-T5. `tags check`.** Lo mismo que `rules check` filtrado a la familia; JSON `{findings, evaluations}` donde cada evaluación es `{document, namespace, rule, holds, clause, tags, missing}` por documento local y namespace que lleva, cumpla o no. `api/tags/tag_check.py:21`, `tag_evaluation.py`. CLI `sldb tags check`, exit 1 con error. Reciente (2026-11-11).

**F-T6. `tag_tree(store, namespace)`.** Árbol `{namespace, roots: [{tag, document, children}], orphans: [{tag, used_by}], unplaced: [{tag, document}]}`. Con `anchored_by` el árbol es el de las anclas (un prefijo sin ancla es nodo con `document: null`); `orphans` = tags usados por documentos locales que ninguna ancla declara; `unplaced` = anclas sin documento que las use (directa o, si es jerárquico, por un tag debajo). Sin `anchored_by` el árbol es el de los tags usados y ambas listas quedan vacías. Namespace no declarado = `SLDBStoreError`. Orden alfabético. `api/tags/tag_tree.py:28`. CLI `sldb tags tree NAMESPACE --format json`. Usado por deskops (`atom_tags.py`, `domain_tree.py:158`). Reciente.

**F-T7. `tag_folder(tag)`.** Función pura: `a.b` o `area:a.b` -> `a/b`; valor en blanco, segmento vacío (`a..b`) o `/` dentro de un segmento = `ValueError`. `api/tags/tag_folder.py`. Usada por deskops para carpetas de átomos. Reciente.

**F-T8. DAG semántico de tags.** Los tags forman un grafo `hijo -> padre`; el nombre con puntos da un padre; se pueden declarar más padres y equivalencias local→global. `semantic_tag(store, tag)` devuelve `{tag, parents, children, below, above, equivalents}` (`SLDBStoreError` si el tag no existe). `add_semantic_parent` (rechaza tags desconocidos, ciclos y auto-padre), `remove_semantic_parent` (rechaza el padre implícito por el nombre), `add_semantic_equivalence`; cada escritura refresca el índice de aristas (`refresh_store_edges`). `api/semantic/*.py`, `store/semantic_parents.py`, `store/semantic_dag_graph.py` (networkx). CLI `sldb semantic parent add|remove`, `semantic equivalent add`, `semantic show`. `actor` se acepta por compatibilidad (el journal se retiró el 2026-11-04). Tests `tests/api/test_api_semantic.py`, `tests/store/test_semantic_dag_walk.py`. Reciente (2026-11-04 solo se movió el layout).

**F-T9. Espacios de direcciones `st.`, `se.`, `gse.`.** `sldb query list|get|glob|find` sobre: `st.{Modelo}.doc.campo` (familia con `{Modelo+}`, ruta por puntos `tasks.0.title`, prefijo `alias:st....` para un store enlazado), `se.<tag>` (un tag nombra todo su subárbol del DAG; un patrón con `*` (un segmento, `[^.]+`) o `**` (cualquier cosa) se compara tag por tag), `gse.<tag>` (alcanza cada tag del documento hacia arriba y las equivalencias locales→globales de cada store), `gse.<global>.se.{store}` (puente: tags locales equivalentes a un global). `store/query_engine/structural*.py`, `semantic*.py`, `global_semantic.py`, `store_prefix.py`. [V] Spec: `docs/sldb_query_engine.spec.yaml` (31 líneas, diagrama de actividad). Desviación: el spec dice "aplicar filtros → resolver DAG", pero el código resuelve el alcance del DAG primero y luego aplica el predicado (`semantic.py:77-81`). [V]

### 3.6 Selectores

**F-S1. `Selector` y `select_documents(store, selector, expect="many"|"one")`.** Selector pydantic de claves combinadas por conjunción: `ref` (nombre, `Modelo/nombre`, `Modelo:nombre` o ruta, con `alias:` de un link directo; `local:` fuerza local), `prefix`, `path_glob`, `model` (el modelo y todo el que lo extienda, por `base_models` del índice de modelos), `where` (predicado sobre la proyección del índice), `include_linked`. Una clave en blanco o desconocida es `ValidationError` (`extra=forbid`). Devuelve filas `{export_id, model, name, path}` ordenadas: local por ruta, luego cada link por alias y ruta. `expect="one"`: el prefijo exacto gana al prefijo parcial; ninguna fila = `SLDBDocumentTargetError("Unknown document: <selector>")`; varias = `AmbiguousSelectionError(selector, candidates)` (subclase de la anterior); `expect` inválido = `ValueError`. `api/documents/select_documents.py:23`, `api/documents/selector.py`, núcleo `store/query_engine/document_selection.py`, `document_selection_filters.py`. CLI `sldb docs select [--ref --prefix --path-glob --model --where --one --include-linked]`; HTTP `GET /docs/select?...` (404 ninguno, 409 con `candidates` si varios, 400 si clave en blanco o `where` no parsea); MCP `select_documents`. Lee los índices de documentos y modelos (no importa modelos, no extrae). Reciente (2026-11-06, GD23). Test `tests/api/test_select_documents.py`.

**F-S2. Glob de rutas.** `*` y `?` no cruzan `/`; `**/` es `(?:.*/)?`; `**` es `.*`; `[seq]` como `fnmatch`. `glob_regex` en `document_selection_filters.py:40`.

**F-S3. Referencias con alias de store.** `split_doc_ref(store_path, ref)` → `(alias|None, resto)`: el prefijo cuenta solo si es el alias de un link DIRECTO (los links de links nunca se siguen); `local:` es el local; un prefijo que no es link deja la referencia entera (un nombre local con `:` resuelve igual). `api/stores/doc_ref.py`. Reciente.

**F-S4. Direcciones `store:Model:name` y `Model:name`.** Export id = `Modelo:nombre` local o `alias:Modelo:nombre` de un link; así nombran los nodos el índice, las reglas y las vistas. `names_document` acepta nombre, ruta, `Modelo/nombre`, `Modelo:nombre` (`document_selection_filters.py:35`). Un `Modelo:nombre` pedido bajo un modelo base que varios submodelos trackean es otra pregunta (`AmbiguousDocument` de `resolve_indexed_document`, queda aparte). [V doc selectors.md]

### 3.7 Vistas

**F-V1. `ViewDoc` (modelo).** Campos exactos en la sección 5.3. `models/notation/view_doc.py`. Reciente (GD25, 2026-11-06; proyección SV01/SV02 anterior).

**F-V2. `run_view(store, name, params)`.** Evalúa una vista compuesta: cada sección selecciona filas (`select_documents` con `{param}` sustituido), proyecta campos de la proyección del índice y agrega por fila `edges`, `findings` de RuleDocs nombradas y `holding`/`blocked`; agrupa por `group_by` en orden de `group_order`. Devuelve `{view, params, revision, sections}`. `api/views/run_view.py:30`. CLI `sldb views run NAME [--param k=v ...]`; HTTP `GET /views/<name>?k=v` con `ETag: "<revision>"` y 304 para `If-None-Match` (débil, fuerte o `*`; 404 vista desconocida, 400 vista de proyección o param rechazado); MCP `resolve_view`. Reciente.

**F-V3. `list_views(store)`.** `[{name, title, params, sections: [claves]}]` de los ViewDoc con `sections`, locales y enlazados; una vista enlazada cuyo nombre choca sale como `<link>:<nombre>`. Lee solo los shards ViewDoc de la federación (nunca compone el índice federado). `api/views/list_views.py`, `view_nodes.py`. CLI `sldb views list`; HTTP `GET /views`; MCP `list_views` (que además lista las vistas de proyección y los `SpecDoc`). Reciente.

**F-V4. Vista de un store enlazado.** El ViewDoc local gana; si no, el único enlazado con ese nombre; `<link>:<nombre>` nombra uno directo; varios = `SLDBDocumentTargetError('Ambiguous view: <n> (linked in ...)')`. Su archivo se lee desde la raíz del store dueño pasando la guarda de raíz/symlink (`store/source_guard`); las secciones leen el store que llama, no el dueño de la vista. `api/views/view_source.py`. Reciente (H9, 2026-11-11).

**F-V5. Cuándo `run_view` lee enlazados.** Solo si una sección tiene `include_linked` verdadero, o un `ref`/`prefix` con alias de link (después de llenar los params), o `edges`/`transitions` con una referencia local sin resolver que nombra un alias de link, o el ViewDoc solo está en un link. Si alguna sección lo necesita, se leen todos. `api/views/view_links.py`, `view_reading.py` (`ViewReading.widen`). Reciente (H11, 2026-11-14).

**F-V6. Params.** `resolve_params`: cada param dado debe estar declarado y ser `str`; los `required` deben venir; defaults llenos; devuelve en orden de declaración y omite los que quedan en `None`. En `where` el valor entra como literal de cadena (`owner = {owner}` → `owner = "ana"`; un valor con `"` se rechaza); en otras claves entra como texto. `api/views/view_rows.py:25-55`. [V]

**F-V7. `edges` de una sección.** `{relation, direction: out|in, as}`; `relation` nombra un tipo de relación o un campo declarante (`metadata.field`); resultado = ids ordenados del índice y luego los del grafo de proyecto (GD21) si el store lo declara; la clave de fila es `as`, por defecto `<direction>:<relation>`. `api/views/view_edges.py`. Reciente.

**F-V8. `rules` de una sección.** Nombres de RuleDoc evaluados con el mismo `rule_finding` de `rules check` sobre cada fila; la fila gana `findings` (los que aplican y no se cumplen, como `Finding.as_dict()`). Una regla que el store no tiene = `SLDBDocumentTargetError` que nombra vista, sección y regla. `SharedRules` construye una sola vista del índice para todas las filas. `api/views/view_rules.py`. Reciente.

**F-V9. `transitions` de una sección.** `{from_field, relation}`: para cada fila lee el valor de `from_field` como nodo de origen (`Modelo:nombre`), toma las aristas de `relation` que salen de él cuya condición se cumple con el payload proyectado de la fila, y aplica las reglas `on_transition`; resultado `holding`/`blocked` con `effects` y `findings`. Sin valor en `from_field` ambos son `[]`. Sin RuleDoc en el store, `findings` no aparece. `api/views/view_transitions.py`. Reciente.

**F-V10. `revision` de la vista.** `sha256` de: `source_hash` de cada documento de las filas (ordenado), `content_hash` del ViewDoc (del store que lo trackea) y de cada RuleDoc usado, y la firma de shards del store que llama. Cambia con cualquier escritura que mueva los shards. `api/views/view_revision.py`. Reciente.

**F-V11. Vistas de proyección (ViewDoc sin `sections`).** `evaluate_projection_view(store, view_or_ref)` → `{view, nodes, edges, layout[, notation]}`; `list_projection_views`; `save_projection_view` (escribe en `sldb/views/<name>.md`). Entradas `{model, where}` evaluadas con `where_matches` sobre el payload aplanado (`flatten`). HTTP `/views/projection`, `/views/projection/evaluate`, `/views/projection/save`; CLI `sldb notations list --views`, `sldb notations view <spec> --projection`. `api/notations/projection_view.py`, `projection_filters.py`, `projection_save.py`. Esta es el área "notaciones y vistas" (`docs/architecture/notations-and-views.md`); la nombro porque comparte el modelo. Anterior a GD25.

## 4. Predicados reales de deskops

Fuente: `/home/jp/proyectos/SLDB_1/deskops/desk/lifecycle/` (22 archivos) y `/home/jp/proyectos/SLDB_1/deskops/deskops/data/{rules,lifecycle,views}/` (la copia empaquetada). Las 16 reglas de `desk/lifecycle/rule-*.md` son idénticas byte a byte a `deskops/data/rules/rule-*.md` (comparé con `diff`; ninguna diferencia) [V]. No hay otros RuleDoc ni otros `moment:` en `deskops/desk/` ni en `deskops/deskops/` (grep) [V]. La declaración que los instala es `deskops/deskops/data/desk_declaration.yaml` (`mode: managed`) [V]. La tabla de evidencias `deskops/deskops/data/rules/closeout-evidence.yaml` no es un predicado: es la tabla de la que se derivan las reglas de cierre.

### 4.1 Las 16 reglas (`RuleDoc`)

Todas llevan `subject` = un único `model <= <Familia>` (ninguna usa conjunción) y `severity: error`, salvo las dos alertas. Las 14 `on_transition` llevan `where: current_node = "<ritual>"` (7 sobre `"ritual-execution"`, 7 sobre `"ritual-closeout"`); `current_node` es un campo del payload de la tarea que deskops pasa como sujeto a `transition_findings`.

Activación (momento `on_transition`, `where: current_node = "ritual-execution"`):

1. `rule-activation-task-aggregate.md`: subject `model <= TaskAggregateDoc`.
   `condition: all(depends_on: status = "closed")`
2. `rule-activation-task-code.md`: subject `model <= TaskCodeDoc`.
   `any(^subject: anchor_state = "intact" and verdict = "accepted" and kind = "preflight") and any(^subject: anchor_state = "intact" and verdict = "accepted" and kind = "bundle") and all(depends_on: status = "closed")`
3. `rule-activation-task-debug.md`: subject `model <= TaskDebugDoc`.
   `any(^subject: anchor_state = "intact" and verdict = "accepted" and kind = "preflight") and any(^subject: anchor_state = "intact" and verdict = "accepted" and kind = "reproduction") and all(depends_on: status = "closed")`
4. `rule-activation-task-decision.md`: subject `model <= TaskDecisionDoc`.
   `any(^subject: anchor_state = "intact" and verdict = "accepted" and kind = "owner") and all(depends_on: status = "closed")`
5. `rule-activation-task-docs.md`: subject `model <= TaskDocsDoc`.
   `any(^subject: anchor_state = "intact" and verdict = "accepted" and kind = "preflight") and all(depends_on: status = "closed")`
6. `rule-activation-task-external.md`: subject `model <= TaskExternalDoc`.
   `any(^subject: anchor_state = "intact" and verdict = "accepted" and kind = "preflight") and all(depends_on: status = "closed")`
7. `rule-activation-task-refactor.md`: subject `model <= TaskRefactorDoc`.
   `any(^subject: anchor_state = "intact" and verdict = "accepted" and kind = "goldens")`

Cierre (momento `on_transition`, `where: current_node = "ritual-closeout"`):

8. `rule-closeout-task-aggregate.md`: subject `model <= TaskAggregateDoc`.
   `any(^subject: kind = "evidence" and fact = "commit" and anchor_state = "intact" and verdict = "accepted")`
9. `rule-closeout-task-code.md`: subject `model <= TaskCodeDoc`.
   `any(^subject: kind = "evidence" and fact = "tests" and anchor_state = "intact" and verdict = "accepted") and any(^subject: kind = "evidence" and fact = "commit" and anchor_state = "intact" and verdict = "accepted") and (any(^subject: kind = "evidence" and fact = "atom" and anchor_state = "intact" and verdict = "accepted") or any(^subject: kind = "evidence" and fact = "materialization" and anchor_state = "intact" and verdict = "accepted"))`
10. `rule-closeout-task-debug.md`: subject `model <= TaskDebugDoc`. Condición idéntica a la 9.
11. `rule-closeout-task-decision.md`: subject `model <= TaskDecisionDoc`.
    `any(^subject: kind = "evidence" and fact = "commit" and anchor_state = "intact" and verdict = "accepted") and any(^subject: kind = "evidence" and fact = "document" and anchor_state = "intact" and verdict = "accepted")`
12. `rule-closeout-task-docs.md`: subject `model <= TaskDocsDoc`. Condición idéntica a la 11.
13. `rule-closeout-task-external.md`: subject `model <= TaskExternalDoc`.
    `any(^subject: kind = "evidence" and fact = "external" and anchor_state = "intact" and verdict = "accepted")`
14. `rule-closeout-task-refactor.md`: subject `model <= TaskRefactorDoc`.
    `any(^subject: kind = "goldens" and anchor_state = "intact" and verdict = "accepted")`

Alertas (momento `on_write`, `severity: alert`, `where: ''`, subject `model <= TaskDoc`):

15. `rule-closeout-alert-implicit-code.md`: `has(task_type) or nonempty(closeout_evidence)` (mensaje: avisa que la tarea no declara `closeout_evidence` y se trata como `code`).
16. `rule-closeout-alert-pill-graduation.md`: `not nonempty(pills) or any(references: model <= AtomDoc)` (mensaje: una tarea con pills debe referenciar el átomo al que se graduaron).

Cada regla lleva además un `message` en texto libre (p. ej. "Una tarea task.code se activa con recibos preflight y bundle aceptados e intactos y sus dependencias cerradas.").

### 4.2 Condiciones de aristas (RelationDoc / RelationTypeDoc, evaluadas por `edges_holding`)

17. `closeout-task_completes.md` (RelationDoc `task_completes`, `RitualDoc:ritual-closeout` → idem): `status = "ready_for_testing"`; efecto `sets: {status: closed}`, `terminal: true`.
18. `execution-task_activates.md` (`task_activates`, ritual-execution → ritual-testing): `''` (vacía = siempre cumple); efecto `sets: {status: active}`.
19. `testing-task_hands_to_closeout.md` (`task_hands_to_closeout`, ritual-testing → ritual-closeout): `nonempty(validation)`; efecto `sets: {status: ready_for_testing}`.
20-22. `type-task_activates.md`, `type-task_hands_to_closeout.md`, `type-task_completes.md` (RelationTypeDoc): `condition: ''` las tres (sin uso del lenguaje).

deskops evalúa estas condiciones con `sldb.api.edges_holding` (`deskops/lifecycle.py:209`), y para texto suelto con `where_matches(condition, payload)` (`deskops/lifecycle.py:135-139`) [V].

### 4.3 Vistas (ViewDoc) de deskops (`deskops/deskops/data/views/`)

- `board.md`: tres secciones con `select` por `model` y `path_glob` (`desk/tasks/Board.md`; `TaskDoc` con `desk/tasks/**/*.md`; `PillDoc`; `AtomDoc`), `edges` (`tasks`/`pills`/`atoms` salientes y `tasks` entrante como `boards`), `group_by: current_node` con `group_order: [ritual-execution, ritual-testing, ritual-closeout]`, sin `rules`, sin `transitions`, sin `params`.
- `task.md`: igual que `board` más un `param` `id` (`required: true`) y la sección `task` con `where: id = {id}` y `fields: ['*']`.

El `{id}` de esa vista es un parámetro de vista (sustitución textual a literal de cadena antes de parsear), no la sustitución del sujeto de la sección 2.4. [V `view_rows.py`]

### 4.4 Otros usos del área en deskops

- `__doc_anchors__ = ["subject"]` en `ReviewDoc` (`deskops/models/review.py:48`) con `SUBJECT_PATTERN = ^(doc:[A-Z][A-Za-z0-9_]*:[^\s:@]+(@sha256:[0-9a-f]{64})?)?$` propio. [V]
- `tag_namespaces`/`tag_models` generados desde `desk/atoms/tag-namespaces.yaml` (`deskops/atom_tags.py:146-205`): `TAG_MODELS = ["AtomDoc", "ProtoAtomDoc"]`, `DOMAIN_ANCHOR = {"model": "CrossroadDoc", "field": "path", "value": "bare"}` y `folder_axis` NO se declara (se lee de `desk/config.json`). [V]
- `select_documents` con `path_glob` y `query` en `deskops/doc_access.py:645-675,942`; `check_doc_anchors` en `services/closure.py:1177`; `tag_tree`, `tag_folder`, `tag_namespaces` en `atom_tags.py`/`domain_tree.py`. [V]

### 4.5 Recuento por construcción

Universo: 49 predicados no vacíos = 16 `subject` + 14 `where` no vacíos + 16 `condition` + 2 condiciones de arista no vacías (`status = ...`, `nonempty(validation)`) + 1 `where` de vista (`id = {id}`). [V] Conté con `grep -l` sobre `desk/lifecycle/rule-*.md` y a mano sobre el resto.

| Construcción | Predicados que la usan | Detalle |
|---|---|---|
| `model <= X` | 17 | 16 `subject` + 1 dentro de la condición de la regla 16 |
| comparación `=` | 30 | 14 `where` + 14 condiciones (13 con `^subject` + la 1 `aggregate`) + 1 arista + 1 vista |
| `!=`, `<`, `<=`, `>`, `>=`, `in`, `~`, listas, números | 0 | solo igualdad con literal de cadena |
| `has(f)` | 1 | regla 15 |
| `nonempty(f)` | 3 | reglas 15 y 16, arista 19 |
| camino por campo | 7 | `depends_on` en 6 reglas (activación); `references` en la regla 16 |
| `^campo` (inversa) | 13 | siempre `^subject` |
| `anchor_state` (alias de ancla) | 13 | siempre `anchor_state = "intact"` |
| `any(` | 14 | 13 con `^subject` + 1 con `references` |
| `all(` | 6 | todas sobre `depends_on`, cuerpo `status = "closed"` |
| `none(`, `count(` | 0 | |
| `rel:<tipo>` | 0 | |
| clausura `+` | 0 | |
| varios pasos `a.b` | 0 | |
| `and` | 13 conjunciones (6 activaciones + 7 cierres) | |
| `or` | 4 | cierres code y debug (atom o materialization), reglas 15 y 16 |
| `not` | 1 | regla 16 |
| paréntesis | 2 | agrupan el `or` en los cierres code y debug |
| `{campo}` del sujeto | 0 | |
| `{param}` de vista | 1 | `id = {id}` |
| valor incompleto (`incomplete`) | 0 | ninguna regla puede producirlo hoy (ver 6.2) |
| regla con `where` | 14 de 16 | los 14 de `on_transition` |
| momento `on_transition` / `on_write` / `on_check` | 14 / 2 / 0 | ninguna `on_check` |
| severidad `error` / `alert` | 14 / 2 | |

Familias de sujeto: `TaskAggregateDoc`, `TaskCodeDoc`, `TaskDebugDoc`, `TaskDecisionDoc`, `TaskDocsDoc`, `TaskExternalDoc`, `TaskRefactorDoc` (2 reglas cada una, activación y cierre) y `TaskDoc` (2 alertas). Lectura de [I]: el lenguaje que deskops necesita de verdad hoy es mucho menor que el que sldb ofrece: conjunciones de `any`/`all` con `^campo`, `anchor_state`, comparación de igualdad y `has`/`nonempty`/`not`/`or`.

## 5. Formatos exactos

### 5.1 Anclas

- Documento: `doc:Modelo:nombre` o `doc:Modelo:nombre@sha256:<64 hex>`; el hash es el `contract_hash` del destino (ver 2.6). Regex en `models/doc_anchor_value.py:19`. `Modelo` no contiene `:` ni `@` ni espacios; `nombre` no contiene `@` ni espacios (puede contener `:`). [V]
- Estados del documento (`store/edge_index/doc_anchor_states.py:1-30`): `intact`, `changed`, `lost`. Cuándo se calculan: al componer el índice (`compose_edge_index` → `with_anchor_states`), al pinnear en cada `save_document_payload` y `create_document`, y en `check_doc_anchors`. Nunca se persisten. [V]
- Código: `code:<ruta>::<nombre>` (ver F-C1). Estados (`models/anchor_state.py`): `intact`, `changed`, `reconnected`, `lost`, `renormalize` (alerta); `unparseable`, `invalid`, `unavailable` (error). Se calculan solo en `anchors check` (lee el árbol de archivos con tree-sitter en el momento). [V]
- Nombre reservado: ningún campo puede terminar en `__anchor_state`.

### 5.2 Tags

- `tags`/`semantic_tags` de un documento: lista de strings; `namespace:valor` (se corta en el primer `:`; el valor puede contener `:`); sin `:` = sin namespace y nunca se chequea.
- Valor jerárquico: segmentos por `.`; los prefijos son tags.
- Declaración (`.sldb/store.yaml`): `tag_namespaces: [{name, hierarchical, folder_axis, anchored_by: {model, field, value}}]`, `tag_models: [...]`.
- Operaciones: `tags namespaces`, `tags tree NS`, `tags check`, `tag_folder`, `rules explain builtin:tag_namespace:<ns> DOC`, DAG (`semantic parent add|remove`, `semantic equivalent add`, `semantic show`), direcciones `se.`/`gse.`.

### 5.3 `ViewDoc` (campos exactos de `models/notation/view_doc.py`, `view_section.py`, `view_param.py`, `view_section_parts.py`)

Documento: `id` (= `name`), `name` (no puede ser `projection`), `title`, `nodes: list[dict]` (cada entrada `{model, where}`, solo esas claves), `relations: list[str]`, `direction` (`both|outgoing|incoming`), `notation` (`NotationDoc:<nombre>` o `""`), `layout` (`{kind, style: layered|rings, ring_field}`), `params: list[ViewParam]`, `sections: list[ViewSection]`, `description`. Semántica de clase `type: [view]`, `layer: [presentation]`, `system: [spec2viz]`; `__references__ = ["notation"]`.
`ViewParam` (`extra=forbid`): `name` (identificador), `required: bool`, `default: str|None`; un param no puede ser requerido y tener default.
`ViewSection` (`extra=forbid`): `key` (único), `select` (mapa no vacío con claves de `{ref, prefix, path_glob, model, where, include_linked}`; `where` debe parsear; strings no en blanco; `{param}` debe estar declarado), `fields` (por defecto `["*"]`), `edges: [{relation, direction: out|in, as}]`, `group_by`, `group_order`, `rules: [nombres de RuleDoc]`, `transitions: {from_field, relation} | None`.
Validación cruzada: `params` nombres únicos, `sections` claves únicas, params referenciados declarados, nombre `projection` reservado. [V]

Resultado de `run_view`: `{"view": name, "params": {...valores usados}, "revision": "<sha256>", "sections": {clave: {"rows": [...]} | {"groups": [{"value", "rows"}]}}}`. Fila: `{export_id, model, name, path, fields}` más, si la sección los declara, una clave por entrada de `edges` (ids ordenados), `findings`, `holding` y `blocked`. Grupos: primero los valores de `group_order` (presentes aunque sin filas), luego el resto ordenado como texto, y al final `value: null` solo si hay filas sin el campo; un valor lista/dict agrupa como texto. [V]

HTTP: `GET /views/<name>?k=v` responde el mismo JSON con `ETag: "<revision>"`; 304 sin cuerpo si `If-None-Match` lo trae (se ignora el prefijo `W/`); `GET /views` lista; el nombre no puede contener `/`. [V `cli/serve/views_routes.py`]

## 6. Invariantes, errores y puntos dudosos

### 6.1 Invariantes y garantías

- Un único lenguaje: todos los `--where`, selectores, condiciones de arista, filtros de faceta, reglas y vistas pasan por `predicate_api`/`predicate_documents`. [V]
- Un predicado que no parsea es siempre `WherePredicateError` con el texto completo de la expresión; no hay posición ni mensaje del analizador. Un regex inválido también. [V]
- Una referencia a un documento inexistente no produce error: se omite (mundo cerrado), por lo que `all` sobre nada es `true`, `any` es `false`, `count` es `0`. [V]
- El `holds` de un documento sin camino ni modelo nunca falla por excepción: ausencias y tipos incomparables son `false`. [V]
- `on_write` es atómico: un `error` impide toda escritura; los `alert` se emiten siempre. [V `rules_on_write.py`]
- `check_doc_anchors` y `anchors check` solo leen; `anchors check --record` es el único que escribe, y solo las reconexiones. [V]
- La caché de reglas nunca es autoridad: borrarla da los mismos findings; se invalida por stamp. [V]
- `run_view`: dos evaluaciones sin escrituras intermedias dan la misma `revision`. [V código; I que sea determinista con todos los inputs]
- El estado de ancla no se escribe en shards (no sube `EDGE_INDEX_VERSION`). [V doc anchoring.md §6]
- Los links de links nunca se leen (selectores, `ref` con alias, `include_linked`). [V]

### 6.2 Errores que levanta esta área

`WherePredicateError` (parseo; subclase de `ValueError`, `from sldb.api import WherePredicateError`); `SLDBGraphArgumentError` (camino o `quant`/`count` en filtro de faceta, campo de payload no proyectado); `SLDBDocumentTargetError` (documento, regla, vista o `ref` desconocidos; vista ambigua entre links; `Unknown document: ...` con `expect="one"`); `AmbiguousSelectionError(selector, candidates)`; `SLDBStoreError` (modelo no registrado en el `subject` de una RuleDoc, namespace no declarado, tag desconocido en el DAG, ciclo, quitar el padre implícito); `SLDBModelError` (definición de `__doc_anchors__`/`__indexed__` inválida); `SLDBRuleViolationError` (error de `on_write`, con `findings`); `SLDBValidationError` (param no declarado/requerido/no texto; vista sin sección); `ValueError` (`contract_hash` con `__contract__` vacío, `tag_folder`, `expect` inválido); `pydantic.ValidationError` (selector con clave en blanco o desconocida, `RuleDoc`, `ViewDoc`, forma de ancla). [V]

### 6.3 Desviaciones doc/código y cosas que parecen muertas o a medias

1. **Pinneo en `create_document`**: `docs/architecture/anchoring.md` §6 dice "`create_document` no pinnea: la siguiente escritura lo hace"; el código SÍ pinnea (`api/documents/create_document.py`, docstring y llamada `pin_payload`). El doc está desactualizado. [V]
2. **`code-anchoring.md`**: §2 muestra un bloque `code:` anidado y §4 dice que `reconnected` "puede venir con `changed`"; el modelo real guarda `ref/kind/seen/...` como campos de primer nivel de `CodeAnchorDoc` y `check_one` devuelve `reconnected` sin evaluar `changed` en el mismo informe. [V código; doc leído en parte]
3. **`incomplete` inalcanzable**: ningún productor real marca aristas `unavailable` (ver 2.9 punto 3). Las partes de código que lo manejan (`rule_finding` con código `/incomplete`, exit 2 de `rules explain`, `where_records` descartando `incomplete`) están, hasta donde llegué, sin datos reales que las ejerciten. [V grep; I conclusión]
4. **`where_findings`**: sin llamadas fuera del test y no exportada en `sldb.api`. [V]
5. **Duplicación**: `cli/commands/find_filter.py` repite la lógica de `api/records/record_filters.py` (`has(`, `=`, `title ~`, `"x" in about|breadcrumbs|semantic_tags`) con una sintaxis antigua propia para registros de campo y de sección, distinta del lenguaje. Para registros no-doc, `--where` no es el lenguaje de predicados. [V]
6. **`where_subject.fill_braces`**: sustitutor textual antiguo que el lenguaje ya no usa para evaluar. [V que `payload_matches` delega en `payload_holds`; I que `fill_braces` quedó huérfano — no busqué otros llamantes]
7. **`sldb_query_engine.spec.yaml`**: orden "filtros → DAG" invertido respecto al código (ver F-T9). [V]
8. **`rules.md` §3** ("caminos cruzan links") vs `rule_reach.needs_linked` (sólo si hace falta). [V]
9. **deskops, comentario obsoleto**: `deskops/atom_tags.py` ("Puente hasta sldb H4") afirma que `create_document`/`track_document_file` no evalúan `on_write`; `on_write_gate.py` y `rules.md` §9.1 dicen que sí lo hacen. Deskops sigue aplicando su propio puente. [V]
10. **`StructuredQuery.scope`**: aunque el modelo acepta varios criterios a la vez, el ejecutor aplica solo el primero (`descendant_of`, `ancestor_of`, `node_id_prefix`). [V]
11. **Nombre `predicates`**: el comando `sldb predicates ...` (`cli/parsers/predicates.py`, `store/predicates.py`) no es el lenguaje de predicados sino predicados de enlace `[nombre:: [[destino]]]` con ejes (`HOW`, `WHY`, ...). Cuidado con la colisión de nombre al migrar. [V]
12. **Documentos escritos a mano**: la familia de tags y las reglas leen la proyección indexada, no el archivo en disco (H7); tras editar a mano hay que correr `stores update`. [V doc tags.md]
13. **`anchored_by` solo local**: las anclas de la familia de tags se leen solo del store local (los enlazados no anclan) y `anchors_of` recorre todos los documentos locales en cada evaluación (O(N²)); lo dice el propio doc como límite conocido. [V doc tags.md]
14. **No determiné**: cómo se compone exactamente `compose_edge_index` y su caché (`.sldb_cache/composed/`) ni la forma del payload de proyección por modelo (`__indexed__`); son de otra área. Tampoco abrí los tests uno por uno ni corrí nada; la afirmación "los tests existen" es por nombre de archivo.
15. **Lo que el predicado de reglas no ve** (R1): un campo que no se proyecta (dict, lista de no-escalares, fuera de `__indexed__`) es invisible a una regla y a `select_documents`; sí lo ve `find_structural` sobre payload extraído. Para kimun esto define qué debe exponer su "proyección" a un lenguaje de reglas. [V doc; I implicancia]
