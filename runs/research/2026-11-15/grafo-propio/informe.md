# El grafo de dependencias de kimun, leído sobre sí mismo

Rama `main` (HEAD `b1dc07e9`), 2026-11-15. Análisis de solo lectura: no se tocó el repo, ni ningún store real. Todo lo medido sale de `src/kimun/**` leído con el mismo lector que `scripts/check_rings.clj` (`clojure.tools.reader`, `:read-cond :allow`, `:features #{:clj}`); lo que es inferencia se rotula **[inferencia]**. Los datos y los scripts están en `/tmp/kimun-planes/grafo-propio/` (`grafo.edn`, `grafo.dot`, `snapshot.json`, `metricas.edn`, `capas.txt`, `comunidades.txt`, `nombres-repetidos.txt`, `ns-docstrings.txt`, `funciones-publicas-grandes.txt`, `kimun-graph-*.json`, `scripts/`).

---

## 1. Cómo se construyó y números globales

- Nodos = los 112 namespaces `.cljc` de `src/kimun/**`; aristas = cláusulas `:require` entre ellos (`scripts/graph.clj`; el lector de `check_rings.clj` se copió, no se ejecutó sobre el repo). Las `requires` externas (clojure 101, babashka 24, cheshire 9, clj-yaml 8, httpkit 1) no cuentan como aristas.
- Por nodo: líneas, `defn` públicos, directorio y anillo de `check_rings.clj` (`grafo.edn`). Los `defn` dentro de `#?(:clj (do …))` se aplanan (sin eso `host/fs_store` salía con 2 públicos; son 8).

| medida | valor |
|---|---|
| namespaces / aristas | 112 / 477 (4,26 salidas por nodo) |
| líneas totales / `defn` públicos | 23 751 / 803 (956 vars públicas de 2 049 definiciones) |
| ciclos de `:require` | **0** (Tarjan: 112 componentes de tamaño 1) |
| profundidad (camino más largo) | 0 a 13; histograma por nivel 13, 22, 14, 9, 3, 9, 5, 6, 9, 5, 11, 4, 1, 1 |
| aristas entre namespaces del mismo directorio | 141 de 477 (30 %) |
| aristas entre dos namespaces del anillo comodín `kimun.*` | 231 de 477 (**48 %**): el lint no dice nada de ellas |
| `cli.commands.*` | 19 namespaces, 5 386 líneas (22,7 % del código), 137 `defn` públicos |

Por directorio (namespaces / líneas / públicos): `cli` 7/944/30; `cli.commands` 19/5386/137; `graph` 8/1576/91; `host` 8/1451/35; `kernel` 18/4038/169; `kernel.legacy` 1/408/7; `surface` 14/4034/164; `surface.http` 2/525/18; `surface.index` 6/1275/27; `surface.links` 4/213/7; `surface.markdown` 9/1479/37; `surface.models` 16/2422/75.

**Aristas ocultas** (medido, `grep requiring-resolve src`): hay dos dependencias que `:require` no ve.
- `kimun.surface.markdown.ast` → `cst-ast` (`ast.cljc:140`), y `cst-ast` requiere `ast`: es un **ciclo real** `ast ⇄ cst-ast` escondido tras `requiring-resolve`. Por eso `cst-ast` tiene entrada 0 en el grafo estático.
- `kimun.cli.commands.include` → `kimun.cli.main/run` (`include.cljc:286`), y `cli.main` requiere `include`: otro ciclo (`include ⇄ main`) oculto.
Sin ellos el grafo estático es un DAG; con ellos hay dos ciclos de dos nodos.

Capas (profundidad, `capas.txt`): nivel 0 son 13 hojas (`kernel.err`, `kernel.ports`, `cli.out`, `graph.model`, `host.store-cache`, `host.treesitter`, `surface.adapters`, `surface.signals`, `surface.heartbeat`, `surface.links.parser/resolver`, `surface.markdown.cst`, `surface.models.marker`). El núcleo mide 6 niveles (kernel hasta `store` en el 5, `fs-store` en el 6); `surface.docs` está en el 6; la CLI ocupa los niveles 7–13 (`cli.main` es el único en el 13).

---

## 2. ¿Puede kimun verse a sí mismo hoy?

Respuesta corta: **solo a medias, y no por ingesta de su código**.

Probado en una copia de `src/` con `stores init` en un proyecto temporal (borrado al terminar), con `KIMUN_POD`:

| intento | resultado |
|---|---|
| `adapters list` | funciona; `treesitter-symbols` aparece con `available? false` (le falta el runtime `tree_sitter` de Python) |
| `adapters scan treesitter-symbols . --language javascript` sobre `src/` | ok pero 0 hechos |
| ingerir `.cljc` | **imposible hoy**: la tabla `LANGUAGES` de `python/kimun/adapters/treesitter_symbols.py:187` solo tiene `python` y `javascript`; no hay Clojure. `docs/v2/11 §4.2`: ese adaptador solo emite `contained-in` e `imports` crudos, ni siquiera `requires` resueltos. El único que emite `imports`/`references` resueltos es `python-symbols` |
| `stores init` + `graph cycles/order/components` sobre el store | ok, pero el grafo contiene solo los 15 nodos de siembra (`sldb://relation_type/*`, `sldb://store`): 0 ciclos, 15 componentes |
| `graph cycles|order|components|central|isolated|neighborhood|path --snapshot` sobre un snapshot node-link generado desde `grafo.edn` | **funciona**. Hay que usar ids `sldb://ns/<ns>` porque `gs/node-id` (`graph/store.cljc:60`) reescribe cualquier id que no sea `sldb://` como `Modelo:nombre` |

Resultados del motor `kimun.graph.*` sobre el snapshot (`kimun-graph-*.json`): `cycles` count 0; `components` count 1 (un solo componente débil); `isolated` 0; `order` da 14 capas con tamaños 13, 22, 14, 9, 3, 9, 5, 6, 9, 5, 11, 4, 1, 1, **idénticas a mi histograma de profundidad** (validación cruzada de las dos implementaciones); `path cli.main → kernel.err` longitud 2; `neighborhood kernel.store` 25 nodos; `central` (PageRank) ordena `kernel.err` 0,129, `kernel.ports` 0,076, `kernel.canon` 0,038, `host.treesitter` 0,025, `cli.out` 0,021, `kernel.revision` 0,020, `models.descriptor` 0,019, `surface.docs` 0,018.

Qué falta para que kimun se vea solo, **[inferencia]** a partir de lo anterior:
1. Un adaptador de símbolos para Clojure (entrada en `LANGUAGES` más una consulta tree-sitter para `ns`/`defn`; habría que ver si el pod trae la gramática) que emita los `:require` como relaciones resueltas, no como cadenas de import.
2. Un camino de relaciones de adaptador hacia aristas del grafo: hoy los hechos del adaptador acaban como documentos con modelo (`adapters sync` define modelos y crea documentos), y las `relations` solo se guardan en el payload; no hay un paso que las convierta en aristas de `graph.edges`.
3. La CLI de `graph` no trae un modo para cargar un `edn`/`dot` externo: solo snapshot JSON node-link o `ingest-sldb` (formato v1).
El `selfdoc`/`source-graph-check` (`surface/adapters.cljc:443`) verifica que los nodos fuente registrados coinciden con los escaneados (deriva); no es un análisis de grafo.

---

## 3. Lectura del grafo

### 3.1 Hubs

Más entradas (quién depende de ellos), medido:

| ns | entradas | comentario |
|---|---|---|
| `kernel.err` | 61 | hoja de errores; es infraestructura, no un hub dañino |
| `kernel.ports` | 27 | protocolos del host; ídem |
| `cli.out` | 24 | envelope de la CLI |
| `cli.store` | 21 | abrir/descubrir el store (293 líneas); lo requieren también `surface.http.views` y `surface.index.cache` |
| `surface.docs` | 19 | **el hub real**: 1 325 líneas, 54 públicos, requiere 21 y lo requieren 19 (9 comandos, `graph.edges`, `graph.export`, `semantic`, `index.*`, `http.views`, `diagnostics`, `fields`) |
| `surface.models.store` | 18 | modelos como nodos del store |
| `host.default` | 15, `kernel.revision` 15, `kernel.canon` 14, `kernel.store` 14 | |

Más salidas (qué tanto requiere cada uno) y en cuántas áreas distintas (área = directorio de 2 niveles):

| ns | salidas | áreas |
|---|---|---|
| `cli.main` | 23 | 2 (todos los grupos de comandos; es la raíz) |
| `surface.docs` | 21 | 5 (`host`, `kernel`, `surface`, `surface.markdown`, `surface.models`) |
| `cli.commands.docs` | 20 | 6 |
| `cli.commands.stores` | 17 | 5 |
| `cli.commands.include`, `migrate`, `models` | 15 cada uno | 5–6 |
| `cli.commands.fields` | 12 | **7** (el máximo: cli, cli.commands, host, kernel, surface, surface.index, surface.models) |
| `cli.commands.adapters` | 10 | 6 |
| `cli.commands.semantic` | 7 | 6 (único comando que toca `graph`, `host`, `kernel` y `surface` a la vez) |

Lectura **[inferencia]**: `cli.commands.*` son el borde de integración y por eso salen alto; lo llamativo no es que requieran mucho sino lo que requieren: 44 aristas `cli.commands → kernel` (`kernel.err` 18, `store` 10, `ports` 7, `revision` 4, `plan` 2, …) y 11 → `host` (10 a `host.default`). Los comandos se saltan la superficie y abren/aplican planes ellos mismos.

### 3.2 Namespaces grandes: qué cosas distintas conviven

Fuente: `funciones-publicas-grandes.txt` (nombre, línea, primera frase del docstring).

**`surface/docs.cljc` (1 325 líneas, 54 públicos): ~9 cosas.**
1. Hash/huellas: `py-json` :83, `text-hash` :94, `content-hash` :95, `field-hashes` :96, `fingerprint` :97, `projection-hash` :634 (y `json-str` :79).
2. Lectura de documentos: `documents` :304, `candidates` :330, `resolve-any` :366, `find-document` :389, `document` :476, `of-model` :490.
3. Extracción y contrato: `extract-under` :217, `extract-doc` :432, `payload-of` :441, `contract-descriptor` :414, `current-reading` :246.
4. Árbol cache/store: `cached-tree?` :496, `rebuild-tree` :502, `rebuilt-root` :514, `doc-tree` :535, `document-markdown` :579, `document-ast` :588.
5. Rutas: `route-entry` :643, `route-owner` :659, `route-state` :665, `routed?` :689, `route-coverage` :1140.
6. Planes de escritura: `track-plan` :847, `create-plan` :892, `update-plan` :900, `move-text` :832, `untrack-plan` :1162.
7. Documentos genéricos (K3): `generic-registrable?` :988, `generic-documents` :1048, `generic-document` :1057, `generic-root` :1080, `generic-plan` :1088, `generic-update-plan` :1104, `refuse-anchored!` :1065.
8. Marcas y unidades (K1c/e/f/h, S2b-r): `unit-states` :454, `marks-of` :1196, `mark-anchors` :1208, `mark-plan` :1224, `unit-field` :1252, `close-unit-plan` :1258, `move-unit-plan` :1276.
9. Sync/diff: `sync-report` :1183, `changed-fields` :599, `file-missing!` :467, `refuse-collision!` :398.
Es a la vez el repositorio de documentos, el escritor de planes y la casa de marcas/unidades.

**`cli/commands/docs.cljc` (939 líneas, 20 públicos): ~6 cosas.** Ciclo de vida (`ingest` :113, `update-doc` :152, `create` :307, `track` :341, `untrack` :480, `reanchor` :414); lectura (`render` :188, `list-docs` :203, `show` :853, `status` :879); anclas (`anchors` :554); marcado (`mark` :778, `unmark` :796, `marks` :833 y los privados `parse-locator` :525, `locator-kind` :540, `field-locator` :613, `range-opt` :629, `resolve-locator!` :663, `move-unit` :691, `mark-here` :726: unas 330 líneas); utilidades que otros comandos importan (`documents` :44, `resolve-document` :62, `timestamp` :37, `write-file!` :246, `open` :252); y `dispatch`/`spec`. El tramo 508–850 (~36 % del archivo) es marcado y resolución de localizadores, lógica de superficie vestida de comando.

**`host/fs_store.cljc` (953 líneas, 8 públicos): ~4 cosas** en un solo `#?(:clj (do …))`: E/S atómica y bloqueo (`with-store-lock` :88, `write-atomic!` :72, `append-line!` :78, `tail-lines` :102); formato de packs/índices (`pack-magic` :141, `scan-pack` :285, `load-pack` :365, `pack!` :667, `unpack!` :762, `pack-stats` :805); caché de packs (`load-cache-pack` :445, `pack-cache!` :705, `cache-bad-packs` :752); y el `defrecord FsBackend` con 17 métodos del puerto (:836–:938). Solo 8 públicos pero 953 líneas: la API pública no deja ver el tamaño. `host.store-cache` (150 líneas) ya existe para la caché, y `fs-store` no la absorbe del todo.

**`kernel/store.cljc` (1 009 líneas, 21 públicos): ~5 cosas.** Serialización (`hashed-form` :140, `serialize` :149, `deserialize` :153, `load-object` :167); log y heads (`heads-record` :205, `transaction?` :218, `log-tail` :225, `committed-log` :261, `uncommitted-tail` :266); init/replay/open/commit (`init!` :300, `replay` :346, `replay-open` :353, `open` :494, `commit!` :536); rutas (`upgrade-routes!` :578, `route-coverage` :636); verificación y alcanzabilidad (`reachable` :712, `object-class` :767, `unreachable` :795, `verify` :872, `verify-full` :942). Esta última familia (4 públicos, ~300 líneas) es de diagnóstico, no de persistencia.

**`kernel/revision.cljc` (877 líneas, 25 públicos): ~4 cosas.** Accesores de lectura del store (`get-object` :70, `revision` :74, `active-edges` :105, `tree-entry` :173, `tree-descriptors` :191, `load-tree` :201 …); construcción del store perezoso (`lazy-store` :218, `refresh-views` :273); el motor de aplicar planes (`apply-plan` :777 y ~30 privados `*-op`); y `diff` :604. Más: `kernel.legacy.revision` (408 líneas) es una **copia** del motor de formato 1 con 16 funciones privadas idénticas (§3.4).

**`cli/commands/stores.cljc` (822 líneas, 17 públicos): ~5 cosas.** Ciclo de vida (`init` :38, `show` :734, `check` :74); salud (`health` :267, `sense` :186, `heartbeat` :211, `update-store` :290); verificación (`verify` :388, `unreachable` :432); empaquetado (`pack` :514, `unpack` :561); migración de formato (`upgrade-store` :619, `reconcile` :362); enlaces (`link` :759, `unlink` :773, `links` :782). Es un grupo de comandos con cinco asuntos de operación; cada uno razonable como comando, pero 822 líneas indica lógica dentro (p. ej. `upgrade-store` ~115 líneas, `pack`/`unpack` ~100).

**`cli/commands/include.cljc` (614 líneas, 6 públicos): ~3 cosas.** Lectura/verificación de la declaración (`check` :151, `status` :581); construcción del store (`build` :497, con el arrastre de identidades `:units-carried`/`:marks-carried`, :332–:483); integración con git (`gitignore` :206, `hook` :269 y el `requiring-resolve` a `cli.main/run`). `build` aislada ocupa unas 85 líneas y ya conoce `:units` y `:marks`.

**`graph/edges.cljc` (589 líneas, 28 públicos): ~5 cosas.** Ids de nodos (`store-id` :37 … `anchor-id` :44, `as-node-id` :45); predicados y tipos de relación (`default-predicates` :53, `builtin-relation-types` :67, `predicates` :103, `add-predicate-plan` :164, `define-relation-type-plan` :183); relaciones autoradas (`authored` :212, `relate-plan` :235, `unrelate-plan` :249); derivación del índice de aristas (`index` :493, `graph` :513, `anchor-targets` :276); consultas (`edges-from` :520, `edges-to` :526, `node` :531, `check` :557). Mezcla **planes de escritura** con un **índice derivado puro** y las constantes de ids, que `graph.io` repite.

**`cli/commands/adapters.cljc` (507 líneas) + `surface/adapters.cljc` (481):** el planificador puro está bien separado de la CLI; el comando mezcla ejecución de subproceso Python (`run-adapter` :114), traducción de alias v1 (`aliases` :469) y aplicación de plan (`apply-plan!` :358); `surface.adapters` mezcla el protocolo de adaptadores con formato exacto de v1 (`py-json` :45, `v1-symbol-record` :134, `v1-provenance` :152).

Resumen **[inferencia]**: las unidades "grandes" se descomponen en 3–9 cosas, pero las cosas que *no* son suyas son claras: en `surface.docs` el bloque 8 (marcas y unidades) y el 5 (rutas); en `kernel.store`, la verificación; en `cli.commands.docs`, el marcado.

### 3.3 Dónde vive hoy lo que el dueño nombró

| concepto | dónde está (archivo:función) | ¿un lugar o repartido? | ¿se mezcla? |
|---|---|---|---|
| **marcado** | decisión de estados: `surface/markdown/anchors.cljc` `mark-states` :313, `advance-marks` :355; declaración: `surface/docs.cljc` `mark-plan` :1224, `close-unit-plan` :1258, `move-unit-plan` :1276; comando y localizadores: `cli/commands/docs.cljc` `mark` :778, `move-unit` :691, `parse-locator` :525; arrastre en reconstrucción: `cli/commands/include.cljc` :332, :438 | **repartido en 4 capas** (superficie Markdown, superficie docs, CLI docs, CLI include) | sí: dentro de `surface.docs` y `cli.commands.docs`, junto al ciclo de vida del documento |
| **descomposición** | árbol de la fuente: `surface/markdown/cst.cljc` (a mano) y `cst_ast.cljc` (tree-sitter), `ast.cljc` (neutro), `inline.cljc`; vista de bloques para modelos: `surface/models/blocks.cljc`; pod: `host/treesitter.cljc`; anclas: `markdown/anchors.cljc` `anchors` :129, `field-anchors` :256, `span-anchor` :185; selectores: `models/selector.cljc`, `selector_cst.cljc`, `capture.cljc` | **repartido en dos pilas paralelas**: la hecha a mano (`cst`/`ast`/`inline`/`blocks`) y la de tree-sitter (`cst-ast`, `selector-cst`), con ~7 funciones privadas repetidas entre ellas (§3.4) | se mezcla con modelos: `blocks` y `selector` viven en `surface.models` pero son análisis de Markdown |
| **generación** | AST→Markdown: `markdown/render.cljc` `render` :107; payload→Markdown: `models/render.cljc` `render` :218 (usa `blocks`, `descriptor`, `extract`, `marker`); planes que escriben archivos: `surface/docs.cljc` `create-plan` :892, `track-plan` :847, `update-plan` :900; comando `docs create` `cli/commands/docs.cljc` :307; `cli/compose.cljc` ata las dos funciones de render | **repartido** en 3 sitios (`markdown.render`, `models.render`, `docs` planes) | sí: `create-plan` mezcla render, extracción y construcción de plan |
| **nodos** | nodo de pool: `kernel/node.cljc` `make` :85 (id = H(canónico clase+tipo+contenido)); posiciones en árbol: `kernel/tree.cljc`; anclas estructurales: `kernel/anchor.cljc`; nodos de documentos como árbol-caché: `markdown/plan.cljc` `ast->tree`; modelos como nodos: `models/store.cljc`; nodos derivados de grafo: `graph/edges.cljc` (ids :37–:44) y `graph/io.cljc` (ids :87–:90, repetidos) | **tres definiciones de "nodo"**: kernel (contenido hasheado), grafo derivado (`sldb://…`), y árbol de documento | **el id de nodo del grafo se construye en dos sitios** (`graph.edges` y `graph.io`) |
| **relaciones** | aristas del kernel: `kernel/edge.cljc` `make` :80 (con evidencia); aristas autoradas y derivadas: `graph/edges.cljc` `relate-plan` :235, `index` :493; enlaces `[[x]]`/`pred::`: `surface/links*`; etiquetas y DAG: `surface/semantic.cljc`; propiedad padre-hijo del árbol: `kernel/tree.cljc` | **repartido en 4 lugares** que producen aristas (kernel, links, semantic, edges) y uno que las une (`graph.edges` requiere `surface.docs`, `links`, `semantic`, `models.segments`, `models.store`) | las relaciones **no son nodos** hoy: `kernel.edge` es un tipo aparte de `kernel.node` |
| **modelos de conocimiento** | `surface/models/*` (16 ns, 2 422 líneas): `descriptor`, `store` (modelos como nodos), `subgraph` (jerarquía `:base`, `derive`/`isa?`, spec 16), `recipe`, `compile`, `extract`, `render`, `validate`, `edit`, `noise`, `segments`, `selector*`; etiquetas: `surface/semantic.cljc` | un directorio, pero **mezcla** análisis de Markdown, el modelo (descriptor/jerarquía) y la proyección (render/extract) | `surface.docs` y `graph.edges` requieren `models.store`; el grafo no puede salir de `surface` sin llevarse `models.*` (ya anotado en `03 §1`) |

**Unidades y marcas con identidad (K1c/e/f/h, S2b-r): ¿nodos del grafo del kernel o entradas en un descriptor?** Medido: **entradas en el descriptor del árbol del documento**. Citas:
- `surface/markdown/plan.cljc` `ast->tree` (:70–:99): `(seq units) (assoc :units units)` y `(seq marks) (assoc :marks marks)` sobre el mapa `descriptor`; el docstring dice «store, never part of the root hash» y «never enter the merkle root».
- `K1c` EVIDENCE: «`:units {campo id}` … guardado en el descriptor del árbol: store, no caché … Sin cambios en el kernel». `K1h`: «Kernel y `store` sin tocar». `K1f`: «el kernel no se tocó».
- El kernel solo ve un mapa opaco: `kernel/revision.cljc` `register-tree-op` :428 guarda «the surface-computed descriptor» y la raíz Merkle, y ya.
- El id es un ULID acuñado por la superficie (`cli/commands/docs.cljc` `mint-unit` :86), no un hash de contenido: no pasa por `kernel.node/make`.

¿Tienen relaciones entre sí? **No como aristas.** Sus únicas relaciones son (a) pertenencia: viven dentro de `:units`/`:marks` de un solo documento, y (b) un traslado declarado entre documentos, que solo queda como entrada del journal: `{:kind "unit-move" :unit :from :to :reason}` (`cli/commands/docs.cljc:714`) y `"unit-closure"` (:821), guardadas en la metainformación del plan (`surface/docs.cljc:75`). Ni `kernel.edge`, ni `graph.edges` leen `:units` ni `:marks` (no hay ninguna aparición en `src/kimun/graph/**` ni `src/kimun/kernel/**`). Es decir: una unidad tiene identidad propia pero **no es alcanzable desde el grafo**: no hay arista de «documento contiene unidad», ni «unidad movida desde», ni de «unidad cerrada». Hoy el grafo ve el documento, no sus unidades.

### 3.4 Fronteras duplicadas

Medido en `nombres-repetidos.txt` (2 049 definiciones; 26 con nombre **y** cuerpo idénticos entre namespaces distintos; más decenas con el mismo nombre y cuerpo distinto).

1. **Anillos de `check_rings.clj` y `03 §1` frente a los directorios.** El lint define 5 anillos y el estándar describe «ring 3 = kimun.* (CLI y demás superficies)»: un solo prefijo para `cli`, `surface`, `surface.index`, `surface.http`, `surface.models`, `surface.links`. Resultado medido: 231 de las 477 aristas (48 %) están dentro de ese anillo y nada las comprueba. Dentro de él, hay **6 aristas hacia arriba** (superficie → CLI) que el lint deja pasar: `surface.http` → `cli.errors`, `cli.out`; `surface.http.routes` → `cli.out`; `surface.http.views` → `cli.out`, `cli.store`; `surface.index.cache` → `cli.store`. Y hay 66 aristas CLI → superficie; las segundas están ordenadas, las primeras no.
2. **`kimun.surface.*` frente a `kimun.graph.*`.** `graph` requiere `surface` en 8 aristas (todas desde `graph.edges` y `graph.export`: a `surface.docs`, `links`, `models.segments`, `models.store`, `semantic`), y los anillos lo permiten expresamente. El paquete "separable" (`12 §1`, decisión 43) no lo es todavía: `graph.edges` es la mitad del paquete (28 públicos) y depende de `surface.docs`. `pending-graph-crossings` está vacía porque la regla que se lintea es la inversa (base → graph). La separación `graph.{model,io,query,traversal,analysis}` (6 ns, sin dependencias en `surface`) sí está limpia.
3. **Lógica de superficie dentro de `cli.commands.*`.** `cli.commands.docs` lo requieren 9 comandos hermanos (`adapters`, `edges`, `fields`, `links`, `migrate`, `models`, `predicates`, `semantic`, `stores`) para `documents`, `resolve-document`, `write-file!`, `open`. `cli.commands.models` lo requieren otros 3. Hay 12 aristas `cli.commands → cli.commands`: un comando es una librería de otros.
4. **Código repetido** (los casos que importan, de `nombres-repetidos.txt`):
   - `kernel.legacy.revision` / `kernel.revision`: **16 funciones privadas idénticas** (`ancestors`, `bind-alias`, `check-base`, `children-map`, `conflicts-for`, `detach-op`, `move-op`, `op-parent-paths`, `op-position-paths`, `position-map`, `removed-edge-conflicts`, `rejecting`, `require-node`, `resolve-edge`, `r`, `tree-changed-since?`) más 15 con mismo nombre y cuerpo distinto (`add-node-op`, `apply-plan`, `ws-init`…). El motor de formato 1 está copiado, no compartido.
   - `kernel.mmap` / `kernel.route-tree`: `empty`, `fetch`, `from-entries`, `node-count`, `removal`, `upd`, `child-ids`: dos tries Merkle con la misma forma.
   - `surface.markdown.cst` / `cst-ast`: `fence-close?`, `leading-spaces`, `same-list-marker?`, `closes-after?`: las dos pilas de descomposición.
   - `graph.edges` / `graph.io`: `tag-id` idéntico; `model-id`, `section-id`, `document-id`, `edge` en variantes (ids `sldb://…` construidos en dos sitios).
   - `kernel.store` :636 / `surface.docs` :1140: `route-coverage` en dos capas, con formas de resultado distintas (`:missing-trees/:orphan-routes` vs `:registered/:routed/:missing/:shared/:outside`).
   - `surface.http` :134/:139/:143 / `surface.http.views` :23/:31 y `routes`: `run-command`, `command-route`, `routes` re-exportados con otro docstring.
   - `host.hash` / `host.fs-store` / `host.store-cache` / `cli.commands.adapters`: `sha256-hex` ×4.
   - `cli.commands.*`: `store-ref` en 10 comandos, `usage!` en 8, `require-arg!` en 6, `python-dir` ×2 idénticos, `write-file!` ×2.
   - `surface.docs` :79 / `surface.adapters` :38: `json-str`/`py-json` («como Python»), dos veces.
5. **Superficies paralelas.** `surface.http.routes` repite uno a uno los comandos de la CLI (`ingest`, `track`, `untrack`, `render`, `extract`, `fields`, `health`) como "route" sobre `cli.main/run`: es una tercera cara (la CLI, HTTP, y la MCP/API que menciona el dueño) que pasa por el parser de la CLI.

---

## 4. Qué jerarquía sugiere el grafo mismo

Método (medido, `scripts/comm.clj`): Louvain de un nivel, grafo no dirigido, **sin los cuatro hubs de infraestructura** (`kernel.err`, `kernel.ports`, `cli.out`, `cli.store`; con ellos todo se pega). Modularidad 0,385; 19 grupos. Los grupos grandes y qué mezclan:

| grupo (por uso) | ns | composición por directorio | lectura |
|---|---|---|---|
| A. núcleo de contenido-direccionado | 17 | 13 kernel, `kernel.legacy`, `cli.commands.edges`, `cli.commands.journal`, `surface.journal` | el kernel sale como un grupo; el journal (superficie) y `edges` (CLI) se pegan por usar `revision` |
| B. modelos y proyección | 22 | 14 `surface.models`, `surface.adapters`, `surface.fields`, `surface.migrate`, `surface.index.build`, `cli.commands.{adapters,direct,migrate,models}` | **un grupo donde conviven el modelo, su CLI y sus consumidores**: lo que usa el descriptor |
| C. Markdown | 7 | 6 `surface.markdown` + `host.treesitter` | `cst`, `ast`, `inline`, `plan`, `render`, `cst-ast`, pod: coherente con el directorio, pero sin `anchors`, `models.blocks`, `selector*` (que lo usan y están en otros grupos) |
| D. paquete grafo | 7 | 6 `graph` + `cli.commands.graph` | `graph.{model,io,query,traversal,analysis,store}` es un grupo limpio |
| E. enlaces | 5 | `surface.links.*` | limpio |
| F. host | 5 | `host.{default,hash,lazy,text,ulid}` | limpio; `fs-store` y `store-cache` caen con *stores*/*signals* |
| G. documentos y su caché | 4 | `surface.docs`, `surface.diagnostics`, `surface.parse-contract`, `host.store-cache` | `surface.docs` se agrupa con diagnóstico y caché, no con modelos |
| H. HTTP | 5 | `surface.http*`, `cli.errors`, `cli.commands.serve` | la superficie HTTP cae con `cli.errors` |
| I. salud del store | 4 | `cli.commands.stores`, `host.fs-store`, `surface.heartbeat`, `surface.signals` | la operación del store es un grupo que cruza host, superficie y CLI |
| J. aristas | 3 | `graph.edges`, `cli.commands.{links,predicates}` | `graph.edges` se agrupa con sus comandos, no con el paquete D |
| K. anclas y capturas | 4 | `cli.commands.docs`, `cli.linked`, `markdown.anchors`, `models.capture` | marcado/anclaje se pega al comando `docs` |

Comparación con los directorios y anillos **[inferencia]**:
- **Coinciden bien** (el directorio ya dice la verdad): `kernel` (con `kernel.legacy` como apéndice), `host` (menos `fs-store`/`store-cache`), `graph.{model,io,query,traversal,analysis,store}`, `surface.links`, `surface.markdown` menos `anchors`.
- **No coinciden**: (1) `cli.commands.*` se reparte en 9 grupos distintos, cada uno pegado a la cosa que opera, no a los demás comandos; el directorio `cli/commands/` agrupa por *forma* (todos son comandos), el grafo agrupa por *asunto*. (2) `surface.docs` no forma grupo con `surface.models` (aunque está en el mismo anillo), sino con `diagnostics`, `parse-contract` y `store-cache`. (3) `graph.edges` se separa de `graph.*`: su dependencia real es `surface.docs`/`semantic`/`links`. (4) `surface.markdown.anchors` y `surface.models.{blocks,selector*,capture}` agrupan por uso con `cli.commands.docs` y `models`, no con `surface.markdown`. (5) `surface.http` cae con `cli.errors`.

Esos cinco desajustes son los candidatos a frontera mal puesta.

---

## 5. Hipótesis (para el dueño; no son un plan aprobado)

Ordenadas por cuánto aclaran el grafo. Cada una es una hipótesis, no una decisión.

**H1. Sacar del `cli/commands/` lo que es superficie.** Mover `documents`, `resolve-document`, `write-file!`, `store-ref`, `usage!`, `require-arg!`, `timestamp` (y el bloque de localizadores y `move-unit`/`mark-here` de `cli.commands.docs`, ~330 líneas) a `surface` (p. ej. `surface.docs.read`/`surface.docs.mark`) o a un `cli.util`. Desaparecen las 12 aristas `cli.commands → cli.commands` y las dependencias de 9 comandos sobre `cli.commands.docs`; `cli.commands.*` quedaría como hojas de nivel casi uniforme. Riesgo: `docs mark` y sus 5 pruebas de CLI (`unit_close_move_test`, `nested_units_test`, `range_marks_test`) importan estos nombres privados; el movimiento es mecánico pero toca archivos que otras sesiones editan.

**H2. Partir `surface.docs` (1 325 líneas, 5 áreas) por los bloques que el grafo ya separa.** Candidatos: rutas (`route-*`, :643–:1140), marcas y unidades (:1196–:1325, junto a `anchors.mark-states`) y documentos genéricos (:988–:1140). `surface.docs` pasaría de 19 entradas / 21 salidas a un núcleo de lectura y planes con menos aristas; los consumidores (`graph.edges`, `semantic`, `diagnostics`) pedirían solo la parte que usan. Riesgo: `surface.docs` es el hub real (19 entradas); la partición cambia los `:require` de 19 namespaces y hay funciones privadas compartidas entre bloques (`doc-update` :925 gestiona units y marks a la vez: 80 líneas que habría que repartir).

**H3. Poner `surface → cli` en el lint.** Pasar `kimun.cli.*` a su propio anillo (por encima de `surface`) en `check_rings.clj` y mover `cli.store`/`cli.out`/`cli.errors` a un lugar que `surface.http` e `index.cache` puedan requerir, o hacer que `surface.http` reciba el store y el `run` por contexto (ya recibe `:run` por `ctx`). Desaparecen las 6 aristas ascendentes; el 48 % de aristas sin vigilancia baja a las internas de `surface`. Riesgo: separar `cli.store` en "descubrir/abrir" (reutilizable) y "resolver `--store` de la CLI" (propio de la CLI) toca a 21 dependientes.

**H4. Cerrar el paquete `graph`: `graph.edges` fuera o `surface` hacia abajo.** El paquete limpio son 6 ns; `graph.edges`/`export` (8 aristas hacia `surface`) son la parte del grafo que sabe de documentos y modelos. Dos lecturas posibles **[inferencia]**: (a) `graph.edges` recibe un «lector de documentos» por parámetro (puerto) y deja de requerir `surface.docs`; (b) se admite que es parte de `surface` y el paquete `graph` se queda con el motor puro (`model`, `io`, `query`, `traversal`, `analysis`, `store`). El resto del grafo ya prefiere (b). Riesgo: depende de la decisión sobre D-G (eje de verdad de la capa G) y de separar anclaje de generación (spec 16, `03 §1`); no implementar antes de que el dueño lo diga.

**H5. Que unidades y marcas sean alcanzables desde el grafo.** Hoy son entradas de descriptor sin aristas (§3.3). Si el dueño quiere que «lo importante son las relaciones», la unidad necesita ser un nodo y su pertenencia/traslado, aristas; en tal caso `graph.edges` leería `:units`/`:marks` y los `unit-move`/`unit-closure` pasarían de entradas del journal a aristas con evidencia (`kernel.edge` ya tiene `:evidence`). No requiere tocar el kernel (`register-tree-op` solo guarda el descriptor), pero sí decidir la identidad (D0, K1; `14 §21`: «hash ≠ identidad»). Riesgo: el invariante de huellas (unidades no entran en la raíz Merkle) obliga a que estas aristas sean store, no caché.

**H6. Una sola fuente para lo copiado.** (a) `kernel.legacy.revision`: extraer las 16 funciones idénticas a un `kernel.revision.common` o `kernel.plan` y quedarse con lo propio de formato 1 (≈ −150 líneas, sin aristas nuevas); (b) `graph.io` y `graph.edges`: una sola definición de los ids `sldb://…` en `graph.model`; (c) `host`: un único `sha256-hex`; (d) `cli.commands.*`: un módulo `cli.args`-adyacente con `store-ref`, `usage!`, `require-arg!`. Riesgo: bajo, pero el formato 1 está «kept verbatim» a propósito (`kernel/legacy/revision.cljc:1`): conviene preguntar si esa copia es una garantía (nadie la toca) o una deuda.

**H7. Resolver las dos aristas ocultas.** `ast ⇄ cst-ast` (`ast.cljc:140`) e `include ⇄ main` (`include.cljc:286`) son ciclos tapados con `requiring-resolve`. Para el primero: elegir el parser por una función inyectada (`host` o `opts`) en vez de un `requiring-resolve`; para el segundo: que `include build` reciba `run` por contexto como ya hace `surface.http`. Con eso el grafo estático diría toda la verdad. Riesgo: bajo; el primero afecta el arranque de babashka (carga perezosa del pod).

**H8. Dar a kimun una forma de verse a sí mismo.** Un lector Clojure para el adaptador `treesitter-symbols` (o uno nuevo) que emita `requires` resueltos y un paso de «hechos → aristas». Con eso el análisis de este informe sería un comando (`kimun graph cycles` sobre su propio código) y no un script. Riesgo: es trabajo nuevo (gramática, pod), y hoy `treesitter-symbols` ni siquiera tiene runtime disponible (`available? false`); va después de las demás porque no endereza ninguna arista.

---

## Qué no se hizo / límites

- Las aristas son solo `:require` estáticos; no se midieron llamadas a funciones, así que "quién usa qué de `surface.docs`" se infiere del `:require`, no de las llamadas.
- Los grupos de §4 salen de un Louvain determinista de un nivel con orden fijo; otro algoritmo daría otra partición. Es una vista, no una verdad.
- «Cosas distintas» por namespace (§3.2) se leyó de los nombres y docstrings de los públicos: es juicio, no una métrica.
- No se corrió `bb test`, ni se modificó el repo; el proyecto temporal con la copia de `src/` y su `.kimun/` se borró.
