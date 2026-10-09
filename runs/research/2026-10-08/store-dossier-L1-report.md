# Dossier del store — Carril L1

> Investigación de solo lectura (sin opiniones). Corpus: `docs/v2/*.md` (incl. `tests/*.md`),
> `docs/README.md`, `docs/faq.md`, `docs/archive/raw/source/{core,sldb-v1,ecosystem,architecture,architecture-retired}/`.
> Ejes del brief común. Citas cortas entre comillas; `ruta:línea` con la numeración **propia** de
> cada fichero. Sección final: lo que el código persiste hoy (`store.cljc`, `revision.cljc`,
> `mmap.cljc` y dónde se crean los `:sign` con `:content`/`:blob`).

---

# Documentos de dirección vigentes (`docs/v2/`)

## docs/v2/01-orden-filosofico.md — vigente (autoridad 1)

- Fuente de verdad: "El pool de nodos es la fuente de verdad." (`01:61`); "Ningún formato externo
  (Markdown, Lisp, YAML, PDF) es autoridad." (`01:61-62`). Las tres capas comparten pool: "S, M y G
  no son tres almacenes que se exportan entre sí: son tres clases de nodo y tres familias de índice
  sobre el mismo sustrato." (`01:54-56`).
- Qué se persiste: "el nodo es el símbolo, no el signo" (`01:38`); tabla S/M/G (S = signos + árboles
  de documento; M = nodos de significado + `binding`; G = nodos de hecho + `projection`) (`01:48-52`).
- Punteros/identidad: "La identidad de un nodo es su contenido." (`01:65`); "Los árboles son
  índices." (`01:68`); "Un nodo tiene un padre *por árbol*, no un padre global." (`01:69-70`).
- Historia: "Nada existente se modifica." (`01:63`); "La identidad *a través de ediciones* no es
  identidad: es **sucesión**, y se registra, no se adivina." (`01:66-67`).
- AST: stand-off bajo el párrafo ("la hoja canónica es la secuencia de símbolos de un párrafo, y
  todo lo demás … son capas de spans tipados sobre ella", `01:108-116`).
- Capa G: "Hecho: una relación entre símbolos que es verdadera o falsa … dentro de un contexto
  `W_i`." (`01:35-36`); "en v2 un índice G es *otro árbol* sobre el mismo pool." (`01:57`).
- Escala/derivados: "Lo derivado es reconstruible y no tiene autoridad." (`01:71-72`).
- Decisiones y reversiones: retira "Round-trip exacto de Markdown" y "árbol sintáctico lossless
  (Rowan)" (`01:123-124`), la división binaria reversible/no-reversible (`01:125-126`),
  "'Documento' como raíz de autoridad" (`01:127`), "Lisp como metalenguaje separado" (`01:128-129`)
  y "Rust como lenguaje del kernel" (`01:130-133`).
- Revierte explícitamente el ADR de capas: "Esto revisa el ADR
  `sldb-text-layer-vs-kgdb-graph-layer.md`" (`01:56`).
- Bucle autopoiético: `drifted`/`orphan` son "los eventos que el ciclo D de SMG (G/M → S) debe
  convertir en registros nuevos" (`01:139-144`).

## docs/v2/02-sustrato-computacional.md — vigente (autoridad 2)

- Fuente de verdad / paralelo Git: "blob … **nodo** del pool" y "identidad = hash del contenido
  canónico" (`02:17`); "ref / HEAD | **document head** y heads de índice" (`02:20`).
- Qué se persiste: `Node {id class kind content address}` (`02:31-38`). Tabla class/kind/content
  (`02:91-99`): `:text {:text …}` (`02:91`), `:block {:format :markdown :type :attrs}` (`02:92`),
  `:opaque {:format "html" :blob "<bytes como string>"}` ("el blob completo entra al hash",
  `02:93`), `:external {:locator :sample :fingerprint}` (`02:94`), `:span {:leaf :range}`
  (`02:95`), `:symbol :term`/`:proposition` (`02:96-97`), `:fact :triple`/`:context` (`02:98-99`).
- Hashing: `id(nodo) = H(canonical-bytes({:class :kind :content}))` (`02:87`); "`class` y `kind`
  **entran** al hash" y "`address` no entra" (`02:88-89`); contenido canónico (NFC, `pr-str`, sin
  floats/`#inst`/`#uuid`) (`02:70-86`).
- Punteros/identidad: id de árbol "nominal, no content-addressed: un ULID … acuñado al crearlo"
  y descriptor en el CAS (`02:152-163`); "un árbol es un árbol de *posiciones*, no de nodos";
  identidad de posición = path (`02:164-167`); objeto de árbol `{:node :children [[c hash] …]}`
  (`02:168-175`); invariante "Los ids de árbol son nominales (refs); los ids de nodo, arista y
  objeto de árbol son content-addressed." (`02:844`).
- Aristas: tabla de 7 tipos con capa y Merkle (`02:130-147`); `id(arista) = H(canonical-bytes(Edge))`
  (`02:205`); "El **timestamp no forma parte de la arista**" (`02:208-215`); "Cada arista se
  persiste **individualmente** como objeto `objects/<edge-id>`" (`02:229-231`).
- Historia: "El contrato es una **revisión de ocho campos**" (`02:290`); formato 2 con `:trees
  :edges :incidence` como raíces mmap (`02:296-297`); ops primitivas (`02:308-311`); CAS de head
  por árbol (`02:320-325`); `ConflictSet`/rebase/diff (`02:396-428`).
- AST: capas deterministas "se **derivan bajo demanda** y se cachean por hash de hoja; no son nodos
  del grafo hasta que algo las referencia" (`02:247-249`); span materializado por el plan
  (`02:262-266`); superficie CST→AST neutro (§7, `02:741-768`).
- Capa G: `W_i` como nodo `:fact/:context` **y** árbol-índice (`02:48-55`); reglas de sucesión
  `supersedes` (`02:446-448`); re-anclaje `reference`/`binding` siguen, `semantic`/`projection`
  quedan `superseded`, `derived` se invalida (`02:468-477`); reconciliación "vive **fuera del
  pool**" (`02:685-686`).
- Escala/runtime: "Persistencia | **log de transacciones + CAS de archivos** (formato 2)"
  (`02:780`); "Hashing | **SHA-256** … BLAKE3 cuando exista" (`02:779`); "Índice de consulta | **mapa
  EAV derivado**" (`02:781`); backend de archivos y layout (`02:793-794`); roadmap hitos 0–9
  (`02:807-819`); invariantes 1–18 (`02:828-856`).
- M/G solo datos: "Hoy las clases M y G son **solo formas de datos**" (`02:44-46`); "M/G | solo
  formas de datos … el evaluador … es el hito 7" (`02:799`).

## docs/v2/03-estandares-de-codigo.md — vigente (autoridad 3)

- Anillos de dependencia y kernel puro: "ring 0 kimun.kernel.* ← puro" (`03:10`).
- Puerto del store: "`kimun.kernel.store` define el puerto `Backend`; `kimun.host.fs-store` lo
  implementa." (`03:32`); "Un puerto por capacidad (`Hasher`, `TextNormalizer`, `IdMinter`,
  `Backend`)" (`03:30-31`).
- Kernel sin I/O: "El kernel no importa nada de fuera" (`03:25`).
- Errores namespaced que cubren el store: `:store/error`, `:store/stale` (`03:46`).

## docs/v2/04-superficie-markdown.md — vigente (autoridad 4)

- Qué se persiste (mapeo AST → pool, `04:255-259`): `:document/:quote/:list/:item/:thematic-break`
  → `:sign/:block {:format :markdown :type <type> :attrs <attrs>}`; `:heading/:paragraph/:code` →
  `:sign/:block` con "**exactamente un hijo** `:sign/:text {:text Text}`"; `:opaque` →
  `:sign/:opaque {:format :format :blob :blob}`.
- Marcas inline: "viven en `:attrs` del bloque padre y **entran en su hash** (son contenido
  canónico, no una capa derivada: no se pueden recomputar desde el texto plano)" (`04:262-266`).
- Identidad/punteros: hojas `:text` "compartibles entre documentos (mismo texto ⇒ mismo nodo)"
  (`04:264-265`); el árbol es `:kind :document` (`04:261`).
- AST: `parse(render(A)) == A` para todo AST canónico (`04:234`); opacos re-emitidos byte a byte
  (`04:250-251`); el AST no se persiste como tal, se mapea a nodos (`04:253-259`).
- Reingesta externa: `markdown->update-plan` "No emite **ningún** `:replace` … y por tanto ninguna
  arista `supersedes`: la superficie ve texto nuevo" (`04:297-301`).

## docs/v2/05-estado.md — vigente (estado y decisiones)

- Definición del producto: "Un motor de versionado content-addressed sobre lenguaje estructurado,
  en Clojure `.cljc` puro sobre Babashka" (`05:13-14`).
- Kernel formato 2: "pool de nodos S/M/G, árboles de posiciones con Merkle, aristas tipadas con
  evidencia, revisiones inmutables con sucesión, estados de anclaje derivados" (`05:21-25`).
- Medidas: paridad v1 (515/590 portadas; `05:31-33`); store real con `10-escala` (`05:36-38`);
  suite 875 tests/9.438 aserciones (`05:40-41`).
- Decisiones 1–20 (`05:67-86`). Relevantes al store: 1 class/kind al hash (`05:67`); 2 sucesión
  (`05:68`); 3 timestamp fuera del id de arista (`05:69`); 4 "ids de árbol nominales (ULID),
  descriptor en CAS, Revision de 8 campos" (`05:70`); 5 "primer slice: SHA-256, backend de archivos,
  validación solo en Babashka, M/G como datos" (`05:71`); 6 árboles de posiciones Git-like (`05:72`);
  12 "un solo producto v2: sldb v1 y kgdb se congelan" (`05:78`); 13 modelos como nodos (`05:79`);
  15 rutas relativas en `store.edn` (`05:81`); 16 "toda escritura … es un `TransactionPlan`"
  (`05:82`); 18 "v1 sigue en v1" (`05:84`); 19 política de lenguajes (`05:85`); 20 rename de
  namespaces con "identificadores de datos … intactos porque entran en los hashes" (`05:86`).

## docs/v2/06-superficies-y-cli.md — vigente

- Layout de un store formato 2 (`06:58-66`): `store.edn`
  `{:format-version 2 :hash-alg :sha-256 :capabilities {…} :name :links}`; `objects/<id>`
  "CAS: EDN canónico del objeto; H(fichero) == id"; `log.edn` "una Transaction por línea,
  append-only"; `heads.edn {:head rev-id :heads-root <id-mmap>} (commit point)"; `lock`;
  `index/<head>.edn` derivado.
- Enlaces: `:links` son `{:name :path}` con `:path` "**relativo al store**" (`06:69`).
- Capacidades por defecto al crear: `{actor :all "kimun/derive" :all "kimun/materialize" :all}`
  (`06:70`); "`index/` es derivado: leer nunca escribe; `rebuild-indexes` lo reconstruye" (`06:71`).
- Comandos de store: `stores check` "abre por replay" (`06:80`); `stores verify` "verify-full
  (replay + hash)" (`06:81`); `stores upgrade` 1→2 (`06:82`); `stores show` "sin replay" (`06:83`).
- Migración y API: `migrate --from-v1` (`06:233`); `serve` sobre los comandos (`06:276-278`).
- No implementado: evaluador anclado (S4/S5) y motores de derivados (`06:316-318`).

## docs/v2/07-modelos-como-nodos.md — vigente

- Fuente/identidad: "Un modelo es **datos**: un descriptor EDN, content-addressed como cualquier
  signo, colocado en un árbol de taxonomía llamado `models`." (`07:11-12`); necesidad del usuario:
  "los modelos siguen siendo elementos en el árbol, pero tienen que poder definirse y proyectarse
  para poder auditarse." (`07:4-6`).
- Qué se persiste: nodo `{:class :sign :kind :opaque :content {:format "edn/model" :blob
  "<canonical-bytes del descriptor>"}}` (`07:82-85`); "el blob entero entra al hash" (`07:87-89`).
- Identidad/punteros: árbol `models` `{:kind :taxonomy :name "models" :root …}`; descriptor = hija
  del root (`07:92-94`); se versiona por `:replace` ⇒ `supersedes` (`07:90-91`).
- AST/modelo como nodo: "el descriptor podrá convertirse en símbolos `M` con `binding` cuando llegue
  el hito 7, sin migrar datos: el `edn/model` seguirá en el pool." (`07:96-98`).
- Template y receta: el `:template` "se ingesta **además** como un documento … `model/<Name>/template`"
  (`07:102-105`); la receta es "una vista derivada del template ya parseado: `[{:path [..] :kind
  …}]`" y "No se persiste" (`07:117-119`).
- `py•` no migra (`07:173-178`).

## docs/v2/08-distribucion.md — vigente

- Nombres y artefactos: kimun (store `.kimun/`, `$KIMUN_STORE`) / pron / knowledge (`08:9-13`).
- Toolchain: "host del CLI | **Babashka, definitivo** … El kernel sigue siendo `.cljc`" (`08:41`);
  política de lenguajes decisión 19 (`08:42`).
- Layout de release y launcher (`08:62-92`); `resources/VERSION` única fuente (`08:90-91`).

## docs/v2/10-escala.md — vigente (único dueño del formato 2)

- Autoridad: "Este documento es el **único dueño del formato 2**; `02` fija el contrato de la
  revisión … Autoridad: `02` … > este documento." (`10:4-6`).
- Antes: cada revisión guardaba estado completo (`:roots`, tree-set, edge-set) (`10:32-35`);
  `.kimun/` 606 MB (`10:20`).
- Diseño: "Mapas Merkle independientes de la historia (`kimun.kernel.mmap`)" (`10:49-58`);
  "Revisión de formato 2: las tres colecciones por revisión pasan a ser raíces de mmaps"
  (`10:59-67`); `apply-plan` "únicamente … las claves que la transacción tocó" (`10:68-71`);
  "Cabezas por árbol en el CAS" (`10:72-74`); "Valor de store perezoso" (`10:75-82`).
- Alternativas descartadas: snapshot EDN, deltas, fan-out 256, `leaf-capacity`, heads mapa,
  "store en SQLite" (`10:89-102`).
- Formato en disco (`10:104-118`); qué confía `open` y qué comprueba cada verificación
  (`10:132-153`); escritor perdedor, lock y ventana de caída (`10:155-185`); ruta de upgrade 1→2
  (`10:187-214`); medidas antes/después y escala (`10:216-245`).

## docs/v2/11-adaptadores.md — vigente

- Frontera: adaptador "trabaja en la frontera S→M … No toca el store; lo que kimun hace con los
  hechos es asunto del planificador" (`11:12-15`); "procesos separados por diseño" (`11:17`).
- Procedencia: `source_hash` es `"sha256:<hex>"` "hash de los bytes del archivo fuente" (`11:78`).
- Los hechos se convierten en documentos "por su camino de escritura normal (`docs
  create|update|untrack`)" (`11:4-5`).

## docs/v2/12-ecosistema-y-pendientes.md — vigente

- Pendientes kernel: "**Packfiles**: el disco desperdicia un bloque de 4 KB por objeto pequeño"
  (`12:27-28`); "**Índice de nombres O(árboles)**" (`12:29-30`); "**Sin GC** ni informe de objetos
  inalcanzables." (`12:31`).
- Consumidores: "**kimun no tiene consumidores en código.**" (`12:60`); tabla de consumidores de
  sldb v1 (`12:64-76`).
- Capa G: "**Capa G**: nada produce `:fact/:triple`; el evaluador de S-expressions (S4) y los
  derivados (S5) no existen" (`12:37-38`); `wittgenstein` es "la capa G" en Python (`12:93-101`);
  tabla de equivalencias wittgenstein↔kimun (`12:103-110`).
- v1: "**v1 sigue en v1** (decisión 18): los consumidores … no se migran por ahora" (`12:127-129`).

## docs/v2/13-include.md — vigente

- Qué versiona git: `{:include/version 1 :store {:path ".kimun" :git :ignore} …}` (`13:14-15`);
  "`:ignore` = se construye en la corrida y nunca se commitea; `:track` = el repo persiste su
  store." (`13:26`).
- `build`: "`stores init`, los descriptores … y luego cada documento (`docs track` …)" (`13:64-66`).
- Este repo: `:store {:path ".kimun" :git :ignore}` (`13:73`); "Declara 539 documentos" (`13:77`).

## docs/v2/tests/promises.md — vigente (evidencia)

- Trazabilidad "cada enunciado normativo … y el test que **fallaría si la promesa se rompiera**"
  (`promises:3-5`).
- Store/backend: "objects/<id> con H(fichero) == id (inv. 17)" (`promises:141`); `store.edn`
  (`142`); "log.edn una Transaction por línea" (`143`); "heads.edn atómico; CAS obsoleto rechazado
  sin escribir" (`144`); ventana de caída y `:uncommitted-tail` (`145`); "formato 2: `open` =
  `store.edn` + `heads.edn` … sin replay ni log" (`146`); `verify`/`verify-full` (`147`);
  `stores upgrade` (`151`).

## docs/v2/tests/parity-v1.md — vigente (evidencia)

- Marcador: 515 portadas, 14 oráculo, 55 n/a, 3 brechas (`parity-v1:14-24`).
- K1 escala: "abrir desde `heads.edn` y la Revision de cabeza sin replay, conjuntos por revisión
  como trie Merkle independiente de la historia (`kimun.kernel.mmap`)" (`parity-v1:86`).
- Diferido: disco "94 MB de datos pero 364 MB en disco … packfiles al estilo git" (`parity-v1:112`);
  índice O(árboles) y "no hay GC" (`113`).
- Desviaciones: "El template **no** se ingesta como documento `model/<Name>/template` (`07 §4`)"
  (`parity-v1:131`); "La receta es por bloque, no por `:path` como pide `07 §4`" (`132`); "la
  búsqueda física también mira el texto del documento" (`123`).

---

# Historia archivada (`docs/archive/raw/source/`)

> `docs/v2/README.md:10` fija que `docs/archive/` es "material histórico, no autoritativo; nunca
> corrige a `docs/v2/`". Los estados de abajo son los que el propio material declara.

## docs/archive/raw/source/core/core_README.md — archivado (refactor intermedio; superseded por v2)

- Fuente de verdad: "El grafo canónico es la fuente de verdad." (`core_README:51`); "El kernel no
  utiliza Markdown, Lisp, Tree-sitter o Pydantic como modelo persistente principal." (`core_README:53`).
- Qué se persiste: `Document Revision Node Edge Transaction Artifact` (`:57-64`); shapes `struct
  Document/Revision/Node/Edge` (`:168-222`); ops primitivas `PutNode RemoveNode PutEdge RemoveEdge
  SetDocumentRoot AttachArtifact EmitDomainEvent` (`:230-238`).
- Aristas: `Ownership Reference Semantic Derived` (`:154-163`); "Las aristas `Ownership` forman el
  árbol o DAG estructural." (`:161`).
- Historia/Merkle: "El hashing es perezoso." (`:363`); "El Merkle estructural utiliza principalmente
  aristas `Ownership`." (`:371`).
- Persistencia: "La fuente de verdad está formada por: Transaction Log / Revision Store /
  Content-Addressed Store / Document Heads" (`:397-404`); "Registro append-only de todas las
  transacciones." (`:419`); "Guarda contenido mediante su hash: hash → contenido" (`:425-429`);
  "Document Heads … compare-and-swap" (`:438-442`).
- Escala/derivados: "Los siguientes componentes son reconstruibles: Petgraph Índices Caches
  Embeddings Proyecciones Hechos derivados" (`:406-415`).
- AST: "canonicalize(parse(render(C))) == C" (`:481`); "Rowan puede utilizarse para representar
  árboles sintácticos lossless." (`:502`).
- Decisiones/stack: Clojure + Lisp metalenguaje (`:95-99`, `:124-144`); tabla de librerías
  Steel/lexpr/Tree-sitter/Rowan/redb/Petgraph/BLAKE3/Ascent/CozoDB/Wasmtime (`:623-642`);
  invariantes 1–14 (`:694-711`); "no objetivos iniciales" (`:799-809`).

## docs/archive/raw/source/core/reasoning.md — archivado

- "El modelo canónico es la fuente de verdad" (`reasoning:5`); "El log es inmutable; los índices
  no" con "Transaction log + Content-addressed store + Revisions + Document heads" (`reasoning:95-104`).
- "Solo `Ownership` debería participar directamente en el Merkle estructural." (`reasoning:91`).
- "Las proyecciones son datos derivados" (`reasoning:121`); "El Merkle es perezoso"
  (`reasoning:167`); "Los efectos ocurren fuera de la transacción" (`reasoning:143`).
- Invariantes 1–10 (`reasoning:184-194`).

## docs/archive/raw/source/core/plan_core.md — archivado

- Fase 2 "Persistencia y Merkle": "`StorageBackend`; redb o CozoDB; transaction log;
  content-addressed storage; document heads; BLAKE3; invalidación perezosa; recuperación después de
  crash." (`plan_core:56-65`); criterio "cerrar y abrir el proceso produce exactamente el mismo
  estado e historial." (`plan_core:67`).
- Diferenciación: "No es obligatorio que: render(parse(source)) == source" y modo lossless con Rowan
  (`:480-503`).
- Pide un `KERNEL_SPEC.md` "normativo, no explicativo" (`:462-486`).

## docs/archive/raw/source/core/interfaces.md — archivado

- Contratos de salida: `DocumentSource` (`identify/metadata/read/write`), `StorageBackend`
  (`append_transaction` / `put_revision` / `get_revision` / `put_content` / `get_content` /
  `compare_and_swap_head`), `SemanticProvider`, `EffectExecutor`, `AgentProvider`
  (`interfaces:165-203`).
- Implementaciones previstas: `RedbBackend`, `CozoBackend` (`:210-225`); "el kernel solo conozca
  **traits propios**." (`:291`).

## docs/archive/raw/source/core/also_core.md — archivado

- Contratos que faltan: conformidad (`also_core:5-27`), identidad/reconciliación (`:31-48`), modelo
  de fallos (`:51-79`), compatibilidad (`:83-104`), "Garbage collection" (`:107-125`), backup/export
  "`kernel export` / `import` / `verify` / `rebuild-indexes` / `recover`" (`:130-155`).
- "La inmutabilidad no implica conservar todo para siempre." (`also_core:109`).

## docs/archive/raw/source/core/libraries_core.md — archivado

- Stack reutilizable propuesto: Steel, `lexpr`, Tree-sitter, Rowan, **redb**, DataScript/Asami,
  **BLAKE3**, Ascent, **CozoDB**, Wasmtime (`libraries_core:3-15`).
- "Persistencia propia | **redb** | Tablas para transacciones, revisiones, blobs, heads e índices."
  (`libraries_core:11`); "redb o CozoDB: motor persistente." (`:27`).
- "Comenzar con CozoDB detrás de un `StorageBackend` reemplazable" (`:99-102`).

## docs/archive/raw/source/sldb-v1/README.md — archivado (v1 frozen; superseded)

- Objeto persistido v1: "treat Markdown files as a structured persistence layer, mapping them
  directly to Pydantic models" (`sldb-v1/README:3`).
- Store v1: "a `.sldb/` pointer database that tracks model contracts and document instances via a
  Merkle-style hash cascade" (`:16`); cascada YAML de tres niveles con `hash_c` (sha256 del `.md`) y
  `hash_d` (sha256 de los valores extraídos) (`:267-275`).
- Semántica no persistida: "Model relationships (`inherits`, `has_many`) are **not** stored in the
  index — they are derived at query time from the Python class hierarchy and field types. The YAML
  index stores only physical pointers; the Pydantic class is the single source of truth for schema
  and semantics." (`:346`).
- Diagnóstico: "`sldb stores check` walks the chain and reports fractures without modifying
  anything" (`:277`).

## docs/archive/raw/source/sldb-v1/faq.md — archivado

- "A store is SLDB's metadata workspace … It does not replace your Markdown files and it does not
  become the canonical source-of-truth for the document text." (`sldb-v1/faq:20`).
- "In practical terms, the store holds metadata about documents, not the documents themselves."
  (`:44`); "files under `.sldb/core/` plus rebuildable runtime artifacts under `.sldb/runtime/`"
  (`:42`); "the local store wins when both exist" (`:31`).

## docs/archive/raw/source/sldb-v1/docs-README.md — archivado

- Workflow: modelos "intentionally shallow" (`sldb-v1/docs-README:23-33`); "Track the documents,
  then rebuild indexes" con `docs track` y `python -m sldb stores update` (`:54-62`);
  "remove it explicitly before deleting or moving it" con `docs untrack` (`:64-68`).

## docs/archive/raw/source/sldb-v1/workspaces.md — archivado

- "Git remains the durable historical record. The store is the current queryable routing layer."
  (`sldb-v1/workspaces:46`).
- Store discipline: `docs track`/`docs create`, `stores update`, `docs untrack` (`:34-43`).

## docs/archive/raw/source/ecosystem/SMG_DEFINITIONS.md — fuente conceptual (archivada)

- S/M/G: S "Ancla el conocimiento a la realidad" (`SMG:10-15`); M "Forma Lógica … S-expressions"
  (`:17-22`); G "Grafo de triples … resultante de la validación en M" (`:24-28`).
- "Cada hecho en G mantiene un vínculo indestructible con su origen en S." (`SMG:27`).
- "Autopoiesis (G/M ➔ S)" (`:45-46`).

## docs/archive/raw/source/ecosystem/matrix-tractatus-intro.md — fuente conceptual (archivada)

- "A sign is the perceptible medium … A fact can be expressed in many signs … a sign can represent
  different symbols" (`matrix:12`).
- "A symbol is a sign and a structure within a logical space, used within a context" (`matrix:14`);
  `W_i` como contexto + `L_i` + `V_i` + `S_i` (`:16-23`).

## docs/archive/raw/source/ecosystem/sldb-v1-smg-ir.md — archivado (IR de v1)

- "R(T) = (S, M, G)" (`sldb-v1-smg-ir:14`); capas Surface/Meaning/Graph (`:14-55`).
- `DocumentIR` con `context structure nodes surface graph context_index` (`:62-70`).
- "Section context indexes are now persisted in store artifacts during `stores update`, allowing
  queries to access section context without reparsing markdown." (`:85`).

## docs/archive/raw/source/architecture/sldb-text-layer-vs-kgdb-graph-layer.md — archivado (ADR "Proposed"; revisado por `01:56`)

- Decisión: "SLDB as the structured text substrate and KGDB as the graph-native semantic substrate"
  (`sldb-text-layer-vs-kgdb:50`).
- SLDB owns: "readable source documents as canonical authored artifacts", "store-local indexes and
  graph-ready semantic export" (`:54-64`).
- KGDB owns: "graph-native persistence and graph-level reasoning over exported knowledge"
  (`:66-77`).
- "Alternative 2: Treat KGDB as the primary source of truth … Rejected." (`:188-192`).

## docs/archive/raw/source/architecture/target-system-overview.md — archivado (target del refactor)

- "the canonical persistence model is an append-only immutable revisioned graph" (`target:9`).
- "The current YAML-index-first store should evolve into an **append-only, immutable
  graph-store-first** local `.sldb/` workspace." (`target:20`).
- "persist canonical documents, revisions, nodes, and typed edges"; "persist canonical links and
  anchor nodes"; "treat hashes as canonical fields" (`:32-38`).

## docs/archive/raw/source/architecture/structured-text.atom.md — archivado

- "It lives in the authored Markdown documents themselves and in the AST-like structural
  representation SLDB derives from those documents at runtime."
  (`structured-text.atom:27`).

## docs/archive/raw/source/architecture-retired/contracts/clojure-canonical-core-contract.md — retirado

- "Canonical AST (Rowan)" y "Green Tree Architecture … lossless parsing" (`clojure-core:17-20`).
- Identidad: "**Algorithm**: Blake3." (`:23`); "Two AST nodes with the same hash are structurally
  identical." (`:25`).
- "**Persistence-Ready**: Every node produced by the core must be capable of being stored in the
  `graph-store` without loss of identity." (`:40`).

## docs/archive/raw/source/architecture-retired/contracts/markdown-roundtrip-contract.md — retirado

- "**Byte-Equivalent Round-Trip**: For any document in the `reversible-document-family`, a
  `Render(Extract(Source))` operation must produce a file that is byte-for-byte identical to
  `Source`" (`markdown-roundtrip:15-16`).
- "**Lossless Parsing**: White-space, comments, and idiosyncratic formatting must be preserved in
  the `CanonicalAST`" (`:18-19`).

## docs/archive/raw/source/architecture-retired/contracts/phase-1-parity-contract.md — retirado

- "`stores init`: Creation of the `.sldb` append-only infrastructure." (`phase-1-parity:30`).
- "Store Integrity Attestation: Verification that the `graph-store-contract` invariants hold after
  a mass document import." (`:51`).

## docs/archive/raw/source/architecture-retired/phase-1-resolution-pack.md — retirado

- "Canonical persisted store scope is: AST nodes, typed edges, relation payloads, anchor nodes, hash
  fields." (`phase-1-resolution-pack:71-77`).
- "Operational store scope is: append-only event log, snapshots, provenance artifacts,
  rebuild/integrity surfaces." (`:79-84`).
- "Derived store scope is: field index, section index, search index, semantic index only to parity
  floor." (`:86-91`).
- "Clojure owns all canonical/runtime truth … Python owns only orchestration" (`:46-67`).

## docs/archive/raw/source/architecture-retired/phase-1-macro-implementation-plan.md — retirado

- Constraints: "**Graph store is append-only / immutable**" y "**Canonical AST is sovereign**"
  (`phase-1-macro:8-18`).
- "Milestone 4 — Append-only graph store": "canonical persistence is separate from derived indexes"
  (`:163-198`); "Milestone 5 — Canonical links and anchors" (`:202-239`); "Milestone 6 — Derived
  indexes and query surface" (`:242-278`).

---

# Tabla de ejes

Una frase por celda. "—" = el documento no dice nada de ese eje.

| documento | fuente de verdad | qué persiste | identidad/punteros | historia | AST | G/grafo | escala/runtime |
|---|---|---|---|---|---|---|---|
| `01` | pool de nodos | nodos S/M/G + árboles-índice | identidad = contenido; padre por árbol | nada se modifica; sucesión registrada | stand-off bajo el párrafo | índice G = otro árbol del pool | derivado reconstruible, sin autoridad |
| `02` | pool de nodos (contrato del kernel) | nodo `{class kind content}`; aristas; Revision 8 campos; Evidence | hash canónico; árbol ULID + descriptor CAS; paths | log + CAS + heads; supersedes; conflict/rebase | CST→AST neutro; capas UAX#29 derivadas | 7 tipos de arista; `W_i` `:fact/:context`; reconciliación fuera del pool | log + CAS de archivos; SHA-256; índice EAV derivado |
| `03` | — (reglas de código) | — | puerto `Backend`; puertos por capacidad | — | — | — | kernel puro sin I/O; `kimun.host.fs-store` |
| `04` | perfil SLDB-MD cerrado | mapeo AST→ `:sign/:block/:text/:opaque`; marcas en `:attrs` | hojas `:text` compartidas; árbol `:document` | reingesta externa sin `supersedes` | `parse(render(A))==A`; opacos verbatim | — | — |
| `05` | content-addressed sobre lenguaje | kernel formato 2 (pool, Merkle, revisiones, evidencia) | ULID árbol; Revision 8 campos; SHA-256 | revisiones inmutables con sucesión | — | M/G como datos | medidas store real; decisiones 1–20 |
| `06` | `.kimun/` formato 2 | `objects/<id>`, `log.edn`, `heads.edn`, `index/` derivado | `H(fichero)==id`; `:links` relativos | commit point `heads.edn`; upgrade 1→2 | — | grafo como vista derivada | comandos check/verify/health |
| `07` | modelo = nodo del pool | `:sign/:opaque {:format "edn/model" :blob …}` en árbol `models` | descriptor content-addressed; árbol `:taxonomy` | versionado por `:replace`→`supersedes` | — | — | — |
| `08` | — (distribución) | — | store `.kimun/`, `$KIMUN_STORE` | — | — | — | Babashka definitivo; kernel `.cljc`; jar/launcher |
| `10` | formato 2 dueño | 3 mmaps por revisión; heads por árbol en CAS | mmap root ids; `H(obj)==id` | lock + log/heads juntos; upgrade 1→2 | — | — | mapas Merkle; coste O(plan); tabla de medidas |
| `11` | — (adaptadores) | no toca el store; produce hechos | `source_hash "sha256:<hex>"` | — | — | — | fuera de proceso |
| `12` | — (pendientes) | — | — | — | — | wittgenstein = capa G en Python | packfiles, índice O(árboles), sin GC |
| `13` | — (inclusión git) | store `.kimun/` ignorado o versionado | `:links`/`:store` relativos | `include check` vs git y store | — | — | `build` = `stores init` + track |
| `promises` | — (evidencia) | objects/store.edn/log/heads | `H(fichero)==id` | CAS atómico; `:uncommitted-tail`; upgrade | — | — | open sin replay en formato 2 |
| `parity-v1` | — (evidencia) | trie Merkle por revisión | sin replay desde `heads.edn` | K1 escala | — | — | disco 364 MB; packfiles pendientes |
| `core_README` | grafo canónico | Document/Revision/Node/Edge/Transaction/Artifact | hash content-addressed; heads CAS | log append-only | Rowan lossless | Ownership/Reference/Semantic/Derived | redb/CozoDB; BLAKE3; Petgraph materializado |
| `reasoning` | modelo canónico | log + CAS + revisions + heads | — | log inmutable; índices no | — | solo Ownership en Merkle | reconstruible; efectos fuera |
| `plan_core` | — (plan) | StorageBackend + log + CAS + heads | — | crash recovery | `canonicalize(parse(render(C)))==C` | — | redb o CozoDB; BLAKE3 |
| `interfaces` | — (puertos) | — | `StorageBackend` (cas_head) | — | — | — | redb/CozoDB detrás de trait |
| `also_core` | — (faltantes) | — | — | GC, backup/export | — | — | inmutabilidad ≠ conservar todo |
| `libraries_core` | — (stack) | — | — | — | Rowan | — | Steel/redb/BLAKE3/CozoDB/Wasmtime |
| `sldb-v1 README` | Markdown + Pydantic | índices YAML `.sldb/`; no documentos | hashes `hash_c`/`hash_d`; Pydantic SSOT | cascada Merkle; Git aparte | mdast/markdown-it | — | pointer database |
| `sldb-v1 faq` | Markdown, no el store | metadata, no documentos | — | — | — | — | `.sldb/core` + `.sldb/runtime` |
| `sldb-v1 workspaces` | Git es el registro durable | store = capa de routing | — | Git histórico; store consultable | — | — | — |
| `SMG_DEFINITIONS` | — (conceptual) | — | — | — | — | S/M/G; hecho ligado a S | — |
| `matrix-intro` | — (teoría) | — | signo vs símbolo; `W_i` | — | — | `W_i = V_i ∧ S_i` | — |
| `sldb-v1-smg-ir` | `R(T)=(S,M,G)` de v1 | `DocumentIR` | `context_index` por sección | — | IR entre Markdown y store | Graph layer de relaciones | section context persistido en `stores update` |
| `ADR text-vs-graph` | SLDB texto; KGDB grafo | export como proyección | — | — | docs como AST | KGDB dueño del grafo | — |
| `target-system-overview` | grafo de revisiones | documentos, revisions, nodes, edges, anchors, hashes | hashes canónicos | append-only | AST canónico | links/anclas canónicos | graph-store-first `.sldb/` |
| `structured-text.atom` | Markdown | AST derivada en runtime | — | — | AST-like | — | — |
| `clojure-canonical-core-contract` | AST canónica | nodos en `graph-store` | Blake3; identidad por hash | — | Rowan lossless | — | FFI-ready/PyO3 |
| `markdown-roundtrip-contract` | Markdown exacto | AST canónica lossless | fragment IDs/hashes implícitos | — | round-trip byte a byte | — | — |
| `phase-1-parity-contract` | — (contrato) | — | — | `.sldb` append-only | — | — | graph-store-contract |
| `phase-1-resolution-pack` | — (contrato) | AST nodes, edges, payloads, anchors, hashes | — | append-only event log | AST | anchors canónicas | índices derivados limitados |
| `phase-1-macro-plan` | AST canónica soberana | grafo append-only | — | — | reversible families | links/anclas canónicos | Clojure engine, Python shell |

---

# Contradicciones

Citas de ambos lados, sin resolver.

1. **Quién es autoridad: el store o el archivo/Markdown.**
   - v1: "It does not replace your Markdown files and it does not become the canonical
     source-of-truth for the document text." (`docs/archive/raw/source/sldb-v1/faq.md:20`); "The
     YAML index stores only physical pointers; the Pydantic class is the single source of truth"
     (`docs/archive/raw/source/sldb-v1/README.md:346`).
   - v2: "El pool de nodos es la fuente de verdad. Ningún formato externo (Markdown, Lisp, YAML,
     PDF) es autoridad." (`docs/v2/01-orden-filosofico.md:61-62`).
2. **Dónde vive la capa G: mismo pool o store/repo aparte.**
   - v2 `01`: "en v2 un índice G es *otro árbol* sobre el mismo pool" (`docs/v2/01:57`).
   - ADR archivado: "KGDB owns graph-native persistence and graph-level reasoning over exported
     knowledge" (`docs/archive/raw/source/architecture/sldb-text-layer-vs-kgdb-graph-layer.md:66-68`).
   - v2 `12`: la capa G es "`wittgenstein` (la capa G)" en Python y "nada produce `:fact/:triple`"
     (`docs/v2/12-ecosistema-y-pendientes.md:3`, `12:37-38`, `12:93-101`).
3. **Algoritmo de hash.**
   - Archivado: "**Algorithm**: Blake3." (`.../architecture-retired/contracts/clojure-canonical-core-contract.md:23`);
     "| Hashing | BLAKE3 |" (`.../core/core_README.md:633`).
   - v2: "**SHA-256** a través del protocolo `Hasher` | BLAKE3 cuando exista en ambos hosts"
     (`docs/v2/02-sustrato-computacional.md:779`); decisión 5 "primer slice: SHA-256"
     (`docs/v2/05-estado.md:71`).
4. **Lenguaje y stack del kernel.**
   - Archivado: "Lisp embebido | Steel" y crates `kernel-core … kernel-cli`
     (`.../core/core_README.md:627`, `:717-730`); "Comenzar con CozoDB detrás de un
     `StorageBackend` reemplazable" (`.../core/libraries_core.md:99-102`).
   - v2: "Clojure, `.cljc` puro" y "Lisp separado | **no**" (`docs/v2/02:774-775`); decisión 8
     "no hay Lisp aparte (EDN + SCI)" (`docs/v2/05:74`); decisión 19 admite nativos "pocos y con
     frontera clara" (`docs/v2/05:85`).
5. **Round-trip de Markdown.**
   - Archivado: "must produce a file that is byte-for-byte identical to `Source`"
     (`.../architecture-retired/contracts/markdown-roundtrip-contract.md:15-16`).
   - v2: "La reversibilidad del modelo canónico sobre las superficies **se va a romper**"
     (`docs/v2/01:83`); retira "Round-trip exacto de Markdown" (`docs/v2/01:123-124`).
6. **Backend de persistencia.**
   - Archivado: "CozoDB puede proporcionar inicialmente: persistencia; consultas Datalog; índices…"
     (`.../core/core_README.md:658-664`); "redb o CozoDB" (`.../core/plan_core.md:57-58`).
   - v2: "backend | **solo archivos** (formato 2)" (`docs/v2/02:794`); "store en SQLite | cambia el
     backend sin cambiar el problema … queda abierto como backend" (`docs/v2/10-escala.md:99`).
7. **El template del modelo: ¿se ingesta como documento?**
   - v2 `07`: "El `:template` se ingesta **además** como un documento del perfil SLDB-MD llamado
     `model/<Name>/template`" (`docs/v2/07-modelos-como-nodos.md:102-105`).
   - v2 `parity-v1` (desviación declarada): "El template **no** se ingesta como documento
     `model/<Name>/template` (`07 §4`): duplicaría revisiones" (`docs/v2/tests/parity-v1.md:131`).
8. **La receta del template: ¿por `:path` o por bloque?**
   - v2 `07`: la receta es "`[{:path [..] :kind …}]` en orden de documento"
     (`docs/v2/07:117-119`).
   - v2 `parity-v1`: "La receta es por bloque, no por `:path` como pide `07 §4`: **deuda de
     diseño**" (`docs/v2/tests/parity-v1.md:132`).

---

# Revisados y descartados

Una línea cada uno (sin contenido propio sobre el store, o solo índice/dato).

- `docs/v2/README.md` — índice y tabla de autoridad; no describe el store.
- `docs/README.md` — índice de `docs/`; remite a `05`, `archive/` y `faq`.
- `docs/v2/tests/parity-map.edn` — dato del marcador (1 función mapeada); sin prosa del store.
- `docs/v2/tests/parity-na.edn` — dato: 55 mecanismos de v1 "n/a" (shards, cachés, layouts); sin diseño de v2.
- `docs/v2/tests/parity-oracle.edn` — dato: temas de oráculo del motor.
- `docs/v2/tests/parity-gaps.edn` — dato: brechas abiertas (aristas/export, descripción por campo).
- `docs/archive/raw/source/core/diagramas_core.md` — índice de ficheros `.puml`; sin texto de store.
- `docs/archive/raw/source/architecture/ritual-testing.md` — ritual de validación de contrato; sin store.
- `docs/archive/raw/source/README.md` — índice del material archivado (fuera del corpus declarado).

---

# Lo que el código persiste hoy (solo lectura)

Contraste spec (arriba) contra `src/kimun/kernel/{store,revision,mmap}.cljc` y los puntos donde se
crean nodos `:sign` con `:content`/`:blob`.

## Store en disco y valor en memoria

- Layout documentado en el propio kernel: `<dir>/store.edn`, `objects/<id>` ("canonical EDN text of
  the hashed form of the object; H(file) == id"), `log.edn` ("one Transaction map … per line,
  append-only"), `heads.edn` ("format 2: {:head rev-id :heads-root mmap-root-id}"), `lock`
  (`src/kimun/kernel/store.cljc:5-12`).
- `heads.edn` es el punto de commit; `commit-tx!` compara cabezas, añade la línea y reemplaza las
  cabezas como una operación bajo lock (`store.cljc:14-21`).
- Valor de store formato 2: `{:host :capabilities :format-version :objects :trees :edges :head
  :heads :heads-root :revisions}` (`src/kimun/kernel/revision.cljc:6-14`); `empty-store`
  (`revision.cljc:43-47`).
- Descriptor en disco: `init!` escribe `{:format-version fv :hash-alg (ports/algorithm (ports/hasher
  host)) :capabilities capabilities}` (`store.cljc:197`); `:format-version` por defecto `2`
  (`store.cljc:176-178`).
- Puerto `Backend`: `read-descriptor`, `write-descriptor!`, `read-object`, `write-object!`,
  `object-ids`, `read-log`, `read-heads`, `commit-tx!` (`store.cljc:45-62`); `commit-tx!` documenta
  escritura atómica de log+cabezas y `:store/stale` / `:store/busy` (`store.cljc:54-62`).

## Qué se hashea y cómo se serializa

- `hashed-form` decide el valor hasheado: para nodos usa `node/identity-form`; para aristas
  `edge/identity-form`; el resto tal cual (`store.cljc:80-87`).
- `serialize` escribe `canon/canon-str` de esa forma ("Canonical text written to objects/<id>")
  (`store.cljc:89-91`); `deserialize` reconstruye y añade `:id` y `:address` a nodos (`store.cljc:93-103`).
- Nodo: `id(node)=H(canonical-bytes {:class :kind :content})` (`src/kimun/kernel/node.cljc:80-83`);
  `identity-form` = solo `class`/`kind`/`content` (`node.cljc:75-78`).
- Formas admitidas por clase/kind — lo que realmente puede persistirse como `:content`
  (`node.cljc:12-29`): `:sign/:text` (`string? :text`), `:sign/:block` (`keyword? :format`,
  `keyword? :type`, `map? :attrs`), `:sign/:opaque` (`string? :format`, `string? :blob`),
  `:sign/:external` (`:locator` con `:kind`, `:sample`, `:fingerprint`), `:sign/:span`,
  `:symbol/:term`, `:symbol/:proposition`, `:fact/:triple`, `:fact/:context`.

## Revisión (formato 2) y transacción

- Revisión construida en `apply-plan`: `{:trees (:root tm) :edges (:root em) :incidence (:root im)
  :parents … :tx tx-id :actor … :engines … :timestamp …}` (`revision.cljc:701-708`); `rev-id =
  canon/digest host rev` (`revision.cljc:709`).
- `:trees` es `tree-id → [descriptor-id merkle-root]`; `:edges` es `edge-id → true`; `:incidence` es
  `node-id → [edge-id …]` o `{:set root}` por encima de `hub-threshold` (`revision.cljc:19-25`,
  `106-111`).
- Transacción: `{:id tx-id :plan resolved-plan :revision rev-id}` (`revision.cljc:713`); el plan
  resuelto se guarda como objeto y el `Transaction` vive en el log (`revision.cljc:714-716`).
- `:new-objects` es "everything the transaction adds to the CAS (nodes, edges, tree objects …,
  descriptors, mmap nodes, the resolved plan and the Revision)" (`revision.cljc:662-666`,
  `716-720`).
- `commit!`: aplica, escribe `:new-objects` y llama a `commit-tx!`; obsoleto ⇒ `:store/stale`,
  sin escribir log ni cabezas (`store.cljc:352-372`).

## mmap (mapa Merkle persistente)

- Nodos: `leaf {:mmap :leaf :entries [[k v] …]}` y `branch {:mmap :branch :count n :children {slice
  id}}` (`src/kimun/kernel/mmap.cljc:8-9`); `leaf?`/`branch?`/`node?` (`mmap.cljc:66-77`).
- Formato en disco: `leaf-capacity 16` (`mmap.cljc:41-48`) y `nibbles-per-level 1` = fan-out 16
  (`mmap.cljc:50-54`) son "part of the on-disk format".
- Path de clave: id hex de 64 chars es su propio path; cualquier otra clave usa `canon/digest`
  (`mmap.cljc:99-103`).
- `empty` devuelve `{:root id :new {id empty-leaf}}` (`mmap.cljc:271-274`); `update-many` devuelve
  `{:root id :new {id node}}` con exactamente los nodos nuevos (`mmap.cljc:284-301`); `from-entries`
  (`mmap.cljc:303-314`); `diff` (`mmap.cljc:338-345`).
- `apply-plan` usa `mmap/update-many` para `:trees`, `:edges` y `:incidence`, y `:heads-root` para
  las cabezas por árbol (`revision.cljc:693-698`, `711-712`); `incidence-value` decide vector inline
  vs `{:set root}` (`revision.cljc:608-629`).

## open / verify

- `open` formato 2 con `LazyMaps`: no replay; comprueba `store.edn`+hash-alg, forma de `heads.edn`,
  y hashea/valida los dos objetos cabeza (`:head` es Revision, `:heads-root` es nodo mmap)
  (`store.cljc:317-348`; `checked-head-object` `store.cljc:270-285`).
- `reachable` calcula la clausura transitiva de referencias desde las cabezas (mmaps de la revisión,
  `:heads-root`, y estructuras formato 1) (`store.cljc:404-432`).
- `verify` rehashea todo lo alcanzable y compara el final del log con las cabezas
  (`store.cljc:434-459`); `verify-full` = `replay-open` + `verify` (`store.cljc:461-474`).

## Dónde se crean los nodos `:sign` con `:content`/`:blob` (grep)

- `src/kimun/surface/markdown/plan.cljc:14-19`: `block-node` → `{:class :sign :kind :opaque :content
  {:format format :blob blob}}` (`:16`) o `{:class :sign :kind :block :content {:format :markdown
  :type type :attrs …}}` (`:17`); `text-node` → `{:class :sign :kind :text :content {:text text}}`
  (`:19`).
- `src/kimun/surface/docs.cljc:64-68`: `root-node` `:sign/:block {:format :markdown :type :document
  :attrs {:doc doc-id}}` (`:64`); `source-node` `{:class :sign :kind :opaque :content {:format
  source-format :blob text}}` (`:65`); `external-node` `:sign/:external {:locator {:kind :file :path
  path} :sample … :fingerprint (fingerprint host text)}` (`:66-68`).
- `src/kimun/surface/models/store.cljc:22`: `root-node` `:sign/:block {:format :markdown :type
  :document :attrs {}}`; `:33`: descriptor `{:class :sign :kind :opaque :content {:format
  blob-format :blob (serialize descriptor)}}`.
- `src/kimun/surface/edges.cljc:137`: `entry-node` `{:class :sign :kind :opaque :content {:format
  fmt :blob (pr-str (into (sorted-map) value))}}`; `:138`: `root-node` `:sign/:block {:format
  :markdown :type :document :attrs {:taxonomy tree-name}}`.
- `:blob` también se lee/valida en `src/kimun/kernel/node.cljc:16` (`:opaque` exige `string? :format`
  y `string? :blob`) y en `src/kimun/kernel/reconcile.cljc:46` (`:opaque → :content :blob`).
