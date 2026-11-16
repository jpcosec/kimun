<!-- Informe de un agente de exploración de solo lectura (2026-11-16). Es salida de un modelo: verifica antes de apoyarte en una cifra. -->

# Qué existe en kimun para la nueva dirección y qué falta

Revisé `main` el 2026-11-16. Leí los tres documentos de contexto y luego el código. No ejecuté nada y no modifiqué nada.

Etiquetas que uso:
- **[V]**: lo verifiqué leyendo código o con grep.
- **[V-bin]**: lo verifiqué buscando cadenas dentro del binario `bb`, sin ejecutarlo.
- **[I]**: es una inferencia mía.

Todas las rutas son relativas a `/home/jp/proyectos/kimun/`.

**Lo esencial:**
- Las piezas puras (anclas, marcas, hashes, jerarquía de etiquetas y de clases) se pueden reutilizar casi tal cual.
- Ninguna proposición `(R A B)` se crea hoy desde el código de producción.
- No hay ningún lector de Clojure en `src/`, y el pod no trae la gramática de Clojure.
- Las marcas, unidades y relaciones viven solo en el store; nada de eso está escrito como formas de Clojure.
- No existe superficie MCP ni interfaz HTML.

---

## 1. Descomposición («ve»)

### Pod de tree-sitter

**Gramáticas compiladas** [V]:
- `pods/treesitter/Cargo.toml:15-18`: `tree-sitter` 0.26, `tree-sitter-md` 0.5.3 (bloques e inline) y `tree-sitter-python` 0.25.
- La función `language` (`pods/treesitter/src/main.rs:129-136`) solo acepta `markdown`, `markdown-inline` y `python`.
- `version` responde esa misma lista (`main.rs:190-195`).
- **No hay gramática de Clojure.**

**Funciones que expone el pod** (`main.rs:302-314`): `parse`, `parse-block`, `blocks`, `parse-inline`, `parse-lang`, `query`, `version` y `bench`.

**Qué devuelve** [V]:
- Un CST en JSON (`node_json`, `main.rs:36-77`). Cada nodo trae `type`, `start_byte`, `end_byte`, `start_point`, `end_point`, `named`, más `field`, `missing`, `error` y `has_error` cuando corresponden, `text` en las hojas con nombre, y `children`.
- `blocks` devuelve una lista plana de bloques, sin hijos ni texto (`main.rs:368-392`).
- `query` devuelve las capturas con nombre, tipo, rango y texto (`main.rs:140-176`). Sirve para cualquier gramática que esté compilada.

**Cómo se invoca** [V]:
- Es un pod de Babashka: mensajes bencode por stdin/stdout con contenido JSON.
- `src/kimun/host/treesitter.cljc` es el único namespace que lo carga. Lo busca en `candidates` (33-43): `$KIMUN_POD`, luego `<bb>/../lib`, luego el build de desarrollo.
- Se carga una sola vez por proceso, con un `delay` (52-61). Las llamadas pasan por `call` (69-78) y hay envoltorios en 80-111.

**Quién lo usa** [V]: solo código de Markdown.
- `surface/markdown/cst_ast.cljc:233` usa `parse-block`.
- `surface/models/blocks.cljc:241` usa `blocks`.
- `surface/models/selector_cst.cljc:148` usa `parse-block`.
- `surface/parse_contract.cljc` registra su versión.
- Nadie en `src/` llama a `parse-lang` ni a `query`, y nadie usa la gramática de Python.

**Qué tan cerca está**: el mecanismo de transporte y la función `query` ya sirven para código. Para «ver» Clojure falta añadir la crate de la gramática y una entrada en `language` (son unas pocas líneas de Rust), y luego recompilar [I]. La sesión anota que eso necesita descargar la dependencia y que está pendiente de que el dueño lo indique.

### Parsers de Markdown (`src/kimun/surface/markdown/`) [V]

| archivo | qué hace hoy |
|---|---|
| `cst.cljc` | Segmentación por bloques, total y sin pérdida, con regex. `(text (parse s)) == s`. `parse` en 100-121, `parse-document` (con frontmatter) en 136-153, `text` en 155-159. |
| `ast.cljc` | Convierte el CST en un AST neutro del perfil SLDB-MD; lo que queda fuera del perfil pasa a `:opaque`. `parse` (129-142) usa el parser nativo por defecto y la vista del pod solo con `KIMUN_PARSER=treesitter`. `parse-native` está en 100-105. |
| `cst_ast.cljc` | Vista sobre el CST del pod (`parse`, 225-240). Ante cualquier error o nodo desconocido vuelve al parser nativo. |
| `inline.cljc` | Parser inline con pila de delimitadores. Devuelve `{:text :marks}` con offsets en grafemas, o `:unparsed`. |
| `offsets.cljc` | Traducción pura entre code points y grafemas NFC (72-95), con rechazos tipados. |
| `plan.cljc` | Del AST al kernel: `ast->plan` (48), `ast->tree` en memoria (60), `ast->update-plan` (112), `store->ast` (145), `store->markdown` (172). |
| `render.cljc` | Renderer canónico: `parse(render(A)) == A` (`render`, 107). |
| `report.cljc` | Cobertura del AST: cuánto es estructural y cuánto es opaco. |
| `anchors.cljc` | Ver la sección 2. |

### ¿Hay algo que lea código Clojure como datos?

- **En `src/` no** [V]. El único lector es `clojure.edn/read-string`, y se aplica a datos EDN: `include.cljc:79`, `models.cljc:47`, `kernel/store.cljc:134`, `fs_store.cljc:82` y `:837`, `store_cache.cljc:52`, entre otros. Tampoco hay `eval` ni `load-string`.
- Hay una excepción mínima: `graph/edges.cljc:283` lee con `edn/read-string` las referencias de ancla de v1 escritas como s-expresión (`"(model X)"`). `form-targets` (261-275) despacha según la cabeza de la forma. Es una pequeña interpretación de formas, heredada de v1.
- **`scripts/check_rings.clj`** usa `clojure.tools.reader`:
  - `read-forms` (51-55) abre un `rt/indexing-push-back-reader` y llama a `r/read` con `{:read-cond :allow :features #{:clj} :eof ::eof}`.
  - Toma la primera forma `ns` (97). `requires-of` (57-63) recorre las cláusulas `:require`.
  - Además revisa docstrings (65-86) y prohíbe `def` mutables en el kernel (88-93).
  - El reader con índices conserva `:line` en la metadata de cada forma.
- Los scripts del análisis del grafo propio (`runs/research/2026-11-15/grafo-propio/scripts/graph.clj`, `dups.clj`, `fns.clj`) copian ese mismo lector. Están fuera de `src/`.

### Qué trae Babashka

- `deps.edn` solo declara `org.clojure/clojure 1.12.0` (más `test.check` en el alias de tests). `bb.edn` no declara `:deps` [V].
- El binario instalado es `bb` v1.13.219. Contiene los namespaces de `edamame.core`, `rewrite-clj` (parser y zip), `clojure.tools.reader`, `sci.core`, `selmer.parser`, `hiccup.core`/`hiccup2.core`, `cheshire`, `clj-yaml`, `org.httpkit.server`, `babashka.pods`, `clojure.data.xml` y `nextjournal.markdown` [V-bin].
- El código usa hoy `cheshire`, `clj-yaml`, `httpkit`, `babashka.fs`, `babashka.process`, `babashka.pods` y `babashka.cli` [V]. No usa edamame, rewrite-clj, sci, selmer ni hiccup [V].

---

## 2. Anclas y marcas

### `src/kimun/surface/markdown/anchors.cljc` [V]

El namespace declara que son evidencia, no identidad: no acuña ids, no escribe en el store y no toca la caché.

**Calcular las anclas:**
- `anchors` (129-177) recibe texto y devuelve `{:sections [{:locator :level :hash :lines :bytes :blocks}] :blocks [{:locator :hash :lines :bytes}]}`.
  - Opcionalmente añade `:leaves` (texto plano de párrafos y títulos, en grafemas) y `:digest-fn`.
  - El localizador de una sección es su camino de títulos con el ordinal entre hermanos.
  - El localizador de un bloque es `{:section :type :n}`.
  - El hash es `canon/digest` del sub-AST.
  - Los rangos de líneas y bytes solo aparecen si quien llama pasa `:spans` alineados.
- `span-anchor` (185-216) resuelve un rango de grafemas dentro de un bloque y lo rechaza con errores tipados si no encaja.
- `resolve-anchor` (235-243) busca un ancla por igualdad exacta de localizador.

**Anclas por debajo de un campo:**
- `field-anchors` (256-274) recibe **cualquier mapa** de payload y devuelve `:items` (`{:field :index}`) y `:nested` (`{:field :path}`), cada uno con su hash.
- No depende de Markdown.

**Estado de las marcas:**
- `mark-states` (313-353) compara cada marca (`{id {:kind :locator :hash :label}}`) con las anclas leídas ahora. Devuelve `:anchored`, `:mutated`, `:ambiguous` o `:missing`, con candidatos propuestos y nunca elegidos.
- `advance-marks` (355-370) solo actualiza el hash de las marcas `:mutated`. Nunca borra.
- Esta tabla es genérica: compara localizador más hash. La única atadura es `entries-of` (276-284), que fija los tipos `:section`, `:block`, `:item`, `:nested` y `:span`.

**Dónde viven hoy las marcas y unidades:**
- En el descriptor del árbol, dentro del store: `surface/docs.cljc`, `marks-of` 1188-1198, `mark-anchors` 1200-1210, `mark-plan` 1216-1234; cierre y traslado de unidades en `close-unit-plan` 1250 y `move-unit-plan` 1268.
- La CLI acuña un ULID en `cli/commands/docs.cljc`, `mark-here` 726-776.
- No son nodos y no tienen relaciones. Esto coincide con lo que dice el informe del grafo propio.

### `src/kimun/surface/models/capture.cljc` [V]

- `aligned-spans` (30-52) comprueba que la capa de bloques coincida uno a uno con el AST.
- `capture-selector` (66-84) devuelve, por cada campo, `{:value :path :spans :leaf-hashes}`.
- No es puro respecto del host: importa `kimun.host.default` directamente (línea 23).

### `selector.cljc` y `selector_cst.cljc` [V]

- `selector.cljc` ejecuta pasos EDN sobre la vista de bloques SLDB-MD (`extract-selector` 251, `extract-located` 267). Tiene dos modos: `:legacy-v1-template` y `:strict`. Es específico de Markdown y además usa YAML.
- `selector_cst.cljc` implementa los pasos que necesitan el pod:
  - `:want :find`: una sección por regex sobre el título.
  - `:want :table`: una tabla estructurada.
  - `:preserve-nesting`: una lista anidada que conserva su jerarquía.
- Depende de los nombres de nodo de la gramática Markdown (`atx_heading`, `pipe_table`) y no tiene alternativa sin el pod.

### Qué es puro y reutilizable sin importar de dónde venga la declaración

- **Puro y genérico** (entra texto o datos, salen datos):
  - `field-anchors`, `mark-states`, `advance-marks`, `span-anchor` y `resolve-anchor` de `anchors.cljc`.
  - Todo `offsets.cljc`.
  - `kernel/canon.cljc` (`normalize` 25, `digest` 78).
  - `cst.cljc` y `render.cljc`.
- **Puro pero solo para Markdown**: `anchors` (pasa por `ast/parse`), `inline.cljc` y `selector.cljc`.
- **Depende del pod o del host**: `selector_cst`, `cst_ast` y `capture` (por usar `host/default`).

---

## 3. Etiquetas, clases y jerarquía

### `src/kimun/surface/semantic.cljc` [V]

**Etiquetas de un documento** (`tags-of`, 64-75): se suman dos fuentes.
- Las del modelo (`model-tags`, 54-62): `root-semantics`, pisado por el `:semantics` de cada base y luego por el del propio modelo, aplanado en nombres con puntos.
- Las cadenas de las listas `semantic_tags` y `tags` del payload.
- Resultado: **un documento lleva muchas etiquetas.**

**Los puntos son jerarquía:** `prefix-edges` (87-91) deriva `UI → UI.view → UI.view.dashboard`. Es exactamente la regla que confirmó el dueño.

**El DAG de etiquetas** (`dag-of`, 93-103) tiene recorridos:
- `parents`, `children`, `roots`
- `above`, `under`, `scope`, `reach` (105-118)
- `equivalents`, `query-scope` e `in-scope?` (120-159)
- globs con `*` y `**` en `matches?` (137-142)

**Cómo se persiste:**
- Cada etiqueta es un nodo `:symbol/:term` `{:name tag :lang "sldb/tag"}`.
- Un padre o una equivalencia declarada es una arista `:semantic` del kernel cuya evidencia `:context` apunta a un nodo `:fact/:context` (`sldb/semantic/parent` o `sldb/semantic/equivalent`). Ver `term`/`context` en 168-169, `declared` en 183-197 y `relation-ops` en 223-227.
- Los padres implícitos por los puntos se derivan; nunca se guardan.

### Clases (`src/kimun/surface/models/subgraph.cljc` y `descriptor.cljc`) [V]

- `hierarchy` (133-143) usa la jerarquía nativa de Clojure (`make-hierarchy` y `derive`) sobre `:base`.
- `isa-model?` (145-148) y `ancestors-of` (150-155) responden preguntas de herencia.
- La composición está en `compose-selector` (162-171), `compose-fields` (173-180), `compose-projections` (182-186) y `check-composition!` (188-205).
- `validate!` de spec 16 está en 64-100.
- `descriptor.cljc` tiene los tipos de campo (`type-valid?`, 11-20), la validación v1 (`validate!`, 47-79) y `finalize` (93-114).

### `src/kimun/surface/models/store.cljc` [V]

- Los modelos son nodos opacos `edn/model` dentro del árbol taxonómico `models`.
- Redefinir un modelo es un `:replace`, que registra `supersedes` (`define-plan`, 118).

### Cómo se pegan clases y etiquetas a un documento hoy, y si una cosa puede llevar varias

- **Clase: exactamente una por documento** [V]. Es el `:class` del descriptor del árbol: el id del descriptor del modelo, o `"generic/md"` (`docs.cljc:161-170`). La entrada de ruta lleva un solo `:class` (`route-entry`, 643-657). El registro escribe una sola arista `:reference` y una `:derived` de la raíz al modelo (`registry`, 733-767).
- **Etiquetas: muchas, pero solo sobre documentos.** En el grafo, cada sección hereda las etiquetas de su documento (`graph/edges.cljc`, `doc-part` 355+). Ni los bloques ni las marcas tienen etiquetas.
- **Una clase y una lista ya existe en forma primitiva** [V]: `kimun.include.edn`, con `:documents [{:glob :model}]`, procesado por `surface/include.cljc` (`parse` 74-105, `plan` 129-159).
  - Gana la primera regla que calza, así que hay un solo modelo por archivo.
  - Solo cubre documentos Markdown con modelo.
  - Hoy está vacío: `kimun.include.edn` tiene `:models []` y `:documents []`.
- `derive` de Clojure admite varios padres, así que la misma jerarquía podría sostener varios ejes de clase [I].

---

## 4. Relaciones

### Kernel [V]

**Aristas** (`kernel/edge.cljc`):
- Siete tipos (16-18): `:ownership`, `:supersedes`, `:reference`, `:binding`, `:projection`, `:semantic`, `:derived`.
- Cada tipo exige cierta evidencia (24-33): `:ref-hash`, un origen (`:actor` o `:engine` con `:version`), `:context` y `:status`.
- `:status` admite `sinnvoll`, `sinnlos` o `unsinnig` (20-22).
- El id es el hash de la forma de identidad (69-77). La marca de tiempo nunca forma parte de la arista.
- **Las aristas no son nodos:** son objetos aparte. `validate` solo exige que `:from` y `:to` sean cadenas. No verifiqué si el kernel aceptaría el id de una arista como extremo de otra [I: sin probar].

**Nodos** (`kernel/node.cljc:12-28`):
- `:sign` → `:text`, `:block`, `:opaque`, `:external`, `:span`
- `:symbol` → `:term`, `:proposition`
- `:fact` → `:triple`, `:context`

**Lo más cercano a `(R A B)` existe, pero sin uso:**
- `:symbol/:proposition` es `{:form [string & átomos]}`: plano, rechaza subformas anidadas (24-26).
- `:fact/:triple` es `{:subject :predicate :object :context}` (27-28).
- Ninguno de los dos se construye en `src/`. Solo aparecen en tests: `test/kimun/kernel/generators.cljc:45-49` y `hardening_test.cljc:63`.
- `:fact/:context` sí se usa, para nombrar relaciones de etiquetas y relaciones autoradas.

**Estados de ancla** (`kernel/anchor.cljc`):
- `intact`, `superseded` y `orphan`, siempre derivados y nunca guardados (14-22).
- `placements` está en 29, `state`/`states` en 129-147.

### Paquete `graph` [V]

**Nodos y aristas** (`graph/model.cljc:1-50`):
- Un nodo es `{:type :semantics :facets}`. Una arista es `{:from :to :type :metadata}`.
- `derived-relations` (13-19) enumera las relaciones que escribe el propio índice.

**De dónde sale el grafo** (`graph/edges.cljc`):
- `index` (493-511) **deriva** el grafo del store en cada lectura y no lo persiste.
- Incluye: el nodo del store, el DAG de etiquetas, los tipos de relación, los modelos con sus campos, los documentos con sus secciones, `tagged_as`, las relaciones declaradas en el payload, las composiciones y los links con predicado del texto.
- Federa los stores enlazados (`qualify-part`).
- Los ids son de estilo v1: `sldb://document/Model:name`, etc. (37-50).

**Relaciones autoradas:**
- Son aristas `:semantic` del kernel entre las raíces de dos documentos, con contexto `kimun/relation/<nombre>` (`context-node` 204, `authored` 212-227, `relate-plan` 235-247).
- Los tipos de relación son nodos del grafo (`sldb://relation_type/x`), con aristas `applies_to_source` y `applies_to_target`. Es decir, se reifican los **tipos** de relación, no las **instancias**.
- Predicados y tipos de relación se guardan en el árbol taxonómico `relations` (31-33, 53-127).

**Otras fuentes y consultas:**
- `graph/store.cljc` acepta como fuente el store, un snapshot node-link o una exportación semántica (`canonical` 16, `from-store` 25, `from-snapshot` 49). `node-id` (60-67) reescribe cualquier id que no empiece por `sldb://`.
- `graph/query.cljc` es el lenguaje de consulta de v1 (`scope`, `filters`, `relations`).
- `graph/traversal.cljc` tiene vecindarios y descendientes.
- `graph/analysis.cljc` tiene caminos, ciclos, capas topológicas, componentes, PageRank, intermediación y similitud.
- `graph/io.cljc` lee y escribe node-link en JSON y lee exportaciones sldb.
- `graph/export.cljc` emite la exportación semántica.

**Comandos de la CLI** (`cli/commands/graph.cljc:281`): `get`, `list`, `query`, `neighborhood`, `path`, `cycles`, `order`, `components`, `central`, `isolated`, `similar`, `snapshot`, `export`, `ingest-sldb`.

**¿Una relación que sea a la vez nodo?** No [V]. Las aristas del grafo son mapas dentro de un vector.

### Wittgenstein (`docs/v2/tests/wittgenstein-manifest.md` y `test/fixtures/wittgenstein/manifest.edn`) [V]

Son 28 capacidades: 12 en estado `partial`, 5 `direction` y 11 `absent` (líneas 33-40). Las relevantes para esta dirección:

| capacidad | estado | comentario del manifiesto |
|---|---|---|
| `C02` (símbolo = signo en contexto) | partial | `:term` no exige contexto |
| `C04` (mundos por contexto y relación) | absent | |
| `C07` (álgebra de relaciones por contexto) | direction | |
| `C08` (fórmulas anidadas) | absent | `:proposition` rechaza anidar |
| `C26` (reificación `rel:`/`ctx:`) | partial | «contextos existen como nodos; reflexión operativa ausente» |
| `C27` (emisión determinista) | partial | `docs render` |

Ojo: la fila `C03` (línea 56) todavía cita los adaptadores `symbols|cli`, que ya fueron borrados. El manifiesto está desfasado en ese punto [V].

---

## 5. Generación («genera») [V]

**Render de plantillas** (`surface/models/render.cljc`):
- `render` (218-230) toma payload y descriptor y produce Markdown con la plantilla v1 de marcadores `⸢rev•campo⸥`/`⸢optrev…⸥`: listas, tablas, YAML y composiciones `⸢render•nombre⸥`. La resolución de composiciones se inyecta con `ctx`.
- `render-marked` (232) produce el render con centinelas de span; lo usa `segments.cljc`.
- Los marcadores están en `marker.cljc`.

**Composición** (`cli/compose.cljc`):
- `context` (51) resuelve las referencias contra la raíz del proyecto (`ref-text-fn` 21-40).

**Comandos que escriben o emiten:**
- `docs create` (`cli/commands/docs.cljc:307-339`): render, luego track, luego `write-file!`.
- `write-file!` (246-250) crea directorios y hace `spit`. Devuelve advertencias en vez de lanzar.
- `docs render` (188-201): Markdown canónico de un documento desde el store.
- `docs compose` (`cli/commands/links.cljc` y `surface/links/composer.cljc`, `compose-document` 50): expande `![[transclusiones]]`.
- `render` directo (`cli/commands/direct.cljc`).
- `fields set` y afines reescriben el archivo (`fields.cljc:153`).
- `graph export`, `graph snapshot save` y `semantic export` emiten JSON.

**Proyecciones de spec 16:**
- Se validan y se componen (`subgraph.cljc:94-99`, `182-186`).
- `compile.cljc` (`template->spec16`, 11-30) envuelve la plantilla v1 como proyección `:markdown/v1`.
- **No existe un renderer de spec 16:** `refuse-spec16-render!` (`docs.cljc:263-271`) lo rechaza explícitamente («M2»).

**Plantillas en uso:** solo las plantillas v1 con marcadores, guardadas en el `:template` del descriptor. No se usan selmer ni hiccup.

**HTTP:** solo responde envelopes en JSON. No hay vistas HTML.

---

## 6. Salidas y superficies [V]

### CLI (`cli/main.cljc`)

Los grupos están en `groups` (30-54). Saqué los subcomandos de cada mapa `commands`, que es la fuente real:

| grupo | subcomandos |
|---|---|
| `stores` | init, check, verify, show, link, unlink, links, unreachable, pack, unpack, health, sense, heartbeat, update, reconcile |
| `docs` | ingest, update, render, list, create, track, untrack, delete, reanchor, anchors, mark, unmark, marks, show, status; más recover y compose (desde `links.cljc`) |
| `edges` | show, check, node, nodes, relate, unrelate, types, type-define, init, rebuild |
| `graph` | get, list, query, neighborhood, path, cycles, order, components, central, isolated, similar, snapshot, export, ingest-sldb |
| `predicates` | add, list, show, validate, remove |
| `find` | (sin subcomandos) |
| `corpus` | rank, refresh |
| `rebuild-indexes` | (sin subcomandos) |
| `fields` | get/show, schema, query, set/create, update, unset, append, clean, clear, … |
| `sections` | list, show, find, fields |
| `journal` | show, verify |
| `semantic` | show, tags, parent-add, parent-remove, equivalent-add, export |
| `serve` | start |
| `models` | define, list, show, check, history, field-add, field-remove, template |
| `include` | check, gitignore, hook, build, status |
| `extract`, `render`, `validate` | directos, sin store |

- El texto de ayuda (`usage`, 62-134) no menciona `docs reanchor|anchors|mark|unmark|marks`.
- Un grupo desconocido responde `:eval/not-available`: «the evaluator surface … arrives in milestone S4» (166-170). Es un hueco reservado para un evaluador.

### Envelope (`cli/out.cljc`)

- Éxito: `{:ok true :command :store :revision :data :warnings}` (`ok`, 17-27).
- Fallo: `{:ok false :error {:type :message :data} :exit}` (`failure`, 29-35).
- `emit` (71-90) lo imprime en json, edn, text, yaml, md (usa `:data :markdown`) o ast.
- Códigos de salida: `main.cljc:134`.

### HTTP

- `surface/http.cljc` es un único handler estilo Ring sobre `cli.main/run`, servido con http-kit (`start!` 212). Admite token Bearer y CORS.
- Las rutas son datos (`surface/http/routes.cljc:183-260`):
  - salud y stores: `/health`, `/stores…`
  - vistas: `/schema`, `/graph`, `/lint`, `/ast`, `/save`
  - documentos: `/docs…`, `/document`, `/document/ir`, `/document/segments`, `/extract/segments`, `/fields`, `/extract`, `/render`, `/sections`
  - búsqueda y grafo: `/find`, `/edges…`, `/graph/{cycles,components,central,isolated,neighborhood,path}`, `/corpus/rank`, `/similar`
  - modelos y diario: `/models…`, `/journal…`

### MCP

No existe [V]: grep de `mcp` y de `json-rpc` sin resultados. Tampoco hay ninguna interfaz HTML.

---

## 7. Índices y cachés [V]

### `<store>/index/<head>.edn`

- Lo construye `surface/index/build.cljc` (`build`, 88-132). Es puro, reconstruible y sin autoridad.
- Contiene: `:models`, `:docs` (con payload, etiquetas, `:hash-d`, `:hash-c` y `:field-hashes`), `:by-model`, `:by-name`, `:tags`, `:tag-dag`, `:equivalences` y `:sections`. Las claves `:links` y `:anchors` están reservadas y vacías.
- `index/cache.cljc` lo lee y valida (`read-cache`). El único que escribe es `rebuild!` (96+), que además crea un `.gitignore` con `*`.

### Búsqueda

- `search.cljc`: registros por capa física y semántica, más `search`.
- `address.cljc`: la gramática `st.`/`se.`/`gse.`.
- `where.cljc`: filtro con sintaxis v1 o vector EDN con `:and`/`:or`/`:not`.
- `corpus.cljc`: ranking léxico con Dice, o con una `:score-fn` inyectada. No hay embeddings.

### `<store>/cache/` (`host/store_cache.cljc`)

- `objects/`: CAS de los objetos derivados de los documentos por referencia (árboles y nodos de bloque, texto y opacos).
- `docs/<sha(fingerprint,clase,parser)>.edn`: entradas por documento (`cache-key`, 85-89).
- `plans/<tx>.edn`: planes resueltos.
- Packs de caché (`fs_store.cljc:192-198`, `pack-cache!` 697).
- El backend recurre a esta caché cuando falta un objeto, y verifica el hash (`read-cached-verified`, 514).
- Hay además una caché de objetos en memoria, en un atom por backend (cabecera de `fs_store.cljc`).

### Qué es hoy «masivo, derivado, de máquina»

- `cache/`, `index/` y el grafo derivado, que se calcula en memoria en cada lectura.
- El árbol de rutas Merkle (`kernel/route_tree.cljc`) es store, no caché. Es la «primera versión de rehacer git» que quedó diferida.
- **No existe una caché «explícita y legible»:** las declaraciones (marcas, unidades, relaciones, padres de etiquetas) están en el CAS y el log del store, no en formas legibles.

### Uso de git

- Solo `cli/commands/include.cljc` llama a git: `ls-files` (80), `diff --cached` (83) y `rev-parse` (61, 223).
- **El sensor por git del §9 del borrador (qué cambió desde un commit) no está en el código** [V por grep]. El sensor actual (`surface/signals.cljc` y `heartbeat.cljc`) trabaja sobre los diagnósticos del store.

---

## (A) Lo que existe y se puede reutilizar tal cual o con cambios pequeños

1. **El pod de tree-sitter y su función genérica `query`** (`main.rs:140-176`, `host/treesitter.cljc`). Basta añadir la gramática de Clojure o la de otros lenguajes y recompilar [V el mecanismo, I el esfuerzo].
2. **El lector de formas de `check_rings.clj:51-63`** (tools.reader con índices). Se puede llevar a `src/` como lector de declaraciones y de código Clojure. Babashka trae además `edamame`, `rewrite-clj` y `sci` [V-bin].
3. **La tabla de estados de marcas**: `mark-states` y `advance-marks` (`anchors.cljc:313-370`). Es genérica sobre localizador más hash; solo hay que generalizar `entries-of` (276-284) a nuevos tipos de ancla [V].
4. **`field-anchors` (256-274)**: anclas sobre cualquier mapa de datos. Sirve para declaraciones EDN o Clojure [V].
5. **`canon/digest`, `offsets.cljc`, `cst.cljc`, `render.cljc` y la segmentación de Markdown**: hashes, localizadores y anclas de Markdown [V].
6. **El DAG de etiquetas de `semantic.cljc`**: puntos como jerarquía, padres y equivalencias declaradas, `scope`/`reach`, globs. Es exactamente «muchas etiquetas por nodo, con jerarquía» [V]. Habría que soltarlo de «etiqueta = documento» para que cualquier nodo pueda llevar etiquetas [I].
7. **`hierarchy` e `isa-model?` sobre `derive` de Clojure (`subgraph.cljc:133-148`)**, más la composición de campos, selectores y proyecciones [V]. Admite varios padres [I].
8. **La regla glob → modelo de `include.cljc`**: es la semilla de «una clase + una lista» [V]. Falta que admita varias clases por archivo y archivos que no sean Markdown [I].
9. **Las formas de nodo del kernel `:symbol/:proposition`, `:fact/:triple` y `:fact/:context`**, y las aristas con evidencia, contexto y `:status`. Están validadas y probadas en tests, pero sin uso en producción [V].
10. **El paquete `graph.*` (modelo, análisis, consulta, recorridos, E/S node-link)**. Sirve sobre cualquier grafo de mapas planos. Ya se usó sobre el grafo propio mediante un snapshot (`runs/research/2026-11-15/grafo-propio/informe.md`) [V].
11. **El render de plantillas, `compose`, `write-file!`, el envelope, la CLI y las rutas HTTP como datos**: sirven como superficies para materializar. Añadir una ruta es añadir una entrada a la tabla [V].
12. **La separación `cache/` e `index/` ya derivada, desechable y verificada por hash**: es la base de la caché comprimida [V].
13. **Las funciones de git de `include.cljc` (45-83)**: base para el sensor de archivos por git [V].

## (B) Lo que falta por completo

1. **Gramática de Clojure en el pod** y cualquier consumidor de `parse-lang`/`query` para código. Hoy kimun no descompone ningún lenguaje de programación: el adaptador Python fue borrado y la gramática Python no tiene consumidor [V].
2. **Un lector o evaluador de declaraciones Clojure en `src/`**: no hay `read`, `eval` ni sci aplicados a archivos de declaraciones. La CLI reserva ese hueco como `:eval/not-available` «S4» [V].
3. **Formas de declaración** (`isa`, `anchors`, `summarizes`, …) y el «docstring tipado» con porqué, quién y cuándo. Nada existe [V].
4. **Proposiciones `(R A B)` vivas**: nada crea `:proposition` ni `:triple`. `:proposition` no anida (`C08`) [V].
5. **Relaciones que sean nodos**: ni en el kernel (las aristas son objetos aparte) ni en `graph` [V]. Solo se reifican los tipos de relación.
6. **Unidades y marcas como nodos con relaciones**: hoy son entradas del descriptor del árbol [V].
7. **Varias clases por nodo, una por eje**: hay una sola clase por documento [V]. No hay etiquetas sobre secciones, bloques o marcas [V].
8. **Nodos estructurales de código**: archivo de código, `ns`, `defn`, ejecución de un script, CLI o agente. No hay clases ni anclas para ellos [V]. Las anclas de hoy son solo de Markdown, más los ítems de payload.
9. **Fichas puras sin ancla** con su materialización [V/I]. Un documento sin archivo existe (inline), pero siempre como documento Markdown con modelo o genérico.
10. **El sensor por git**: qué cambió desde un commit y si cambió una región anclada comparando contra la versión guardada en git. Tampoco existe el puntero «miré hasta este commit» [V].
11. **La caché «explícita» legible** y la separación formal entre caché comprimida y explícita. La política de marcar como borrado y luego quitar, declarable por proyecto y por tipo de dato, tampoco existe [V].
12. **Un renderer de proyecciones spec 16 (M2)** y la generación desde el grafo, no desde el payload de un documento [V]. Tampoco hay una «receta de materialización» como dato.
13. **Superficie MCP y cualquier interfaz visual (HTML)** [V].
14. **Lógica de capa G**: contexto obligatorio en símbolos, álgebra de relaciones por contexto, deducción, `why` y `retract` (`C04`–`C10`, `C22`, `C25`) [V según el manifiesto].
15. **Que kimun se indexe a sí mismo dentro del producto** [V]. El análisis del grafo propio se hizo con scripts sueltos fuera de `src/`. `graph` no carga EDN externo, solo snapshots JSON o `ingest-sldb`, y `node-id` reescribe los ids que no son `sldb://` (`graph/store.cljc:60-67`).
