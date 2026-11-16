# kimun (SLDB v2) — Estado (2026-10-09; nota al día 2026-11-16)

> **Nota 2026-11-16.** El 2026-11-15 el dueño cambió el marco y el 2026-11-16 aprobó un plan
> (`drafts/plan-producto.md`; palabras del dueño en `drafts/sesion-2026-11-15.md`; diseño en
> `drafts/diseno-declaraciones.md`). Estado real: kimun está **en rearquitectura** y no hay
> release publicada. Lo que corre hoy es la implementación del diseño anterior (store de formato 4,
> superficie Markdown, modelos, marcas de unidades, `serve`); el 2026-11-15 se borraron `python/`, el
> subsistema de adaptadores, los formatos de store anteriores al 4 y su código muerto. En curso (etapa 0 del
> plan): el sensor por git (`stores changed --since`), el borrado de la paridad v1 y de sus modos de
> compatibilidad, y la poda de documentos. **No existe todavía**: leer o evaluar declaraciones en Clojure,
> proposiciones `(R A B)` vivas, relaciones como nodos, varias clases por nodo, nodos de código, caché
> explícita, generar desde el grafo, MCP. Las secciones de abajo conservan el texto del 2026-10-09 salvo donde
> se indica; las decisiones 48–56 son las del dueño del 2026-11-15.

> Solo estado actual y decisiones vigentes. La autoridad y el orden de lectura están en
> `docs/v2/README.md`. Los pendientes, en `12-ecosistema-y-pendientes.md`. La historia
> (pista S, drawer pre-v2, evidencia de los gates de cero contexto) está en `docs/archive/`.

## 1. Qué hay hoy

Un motor de versionado content-addressed sobre lenguaje estructurado, en Clojure `.cljc`
puro sobre Babashka, más las superficies que lo usan (PENDIENTE: el hash detecta mutaciones,
no es identidad — `14 §21`). Cada capacidad tiene un dueño:

- **Kernel (formato 4)** — pool de nodos S/M/G, árboles de posiciones con Merkle, aristas
  tipadas con evidencia, revisiones inmutables con sucesión, estados de anclaje derivados y
  reconciliación de ediciones externas. `open` lee las cabezas sin replay; solo existe el
  formato 4 (los anteriores y su código se borraron el 2026-11-15; un store anterior se reconstruye con `include build --rebuild`). En formato 4 un documento con archivo se registra (anchor + clase + hashes) y su árbol vive en un caché por hash. Contrato: `02-sustrato-computacional.md`;
  forma en disco: `10-escala.md`.
- **Superficie Markdown** — perfil cerrado SLDB-MD: texto → CST → AST neutro → árbol de
  documento en el pool, render canónico y reingesta de ficheros editados por fuera. Dueño:
  `04-superficie-markdown.md`.
- **Modelos** — descriptores EDN como nodos opacos del árbol `models`, versionados por
  sucesión y proyectables para auditarlos, más el modo directo de extract/render/validate.
  Dueño: `07-modelos-como-nodos.md`; CLI: `06-superficies-y-cli.md`.
- **Documentos tipados** — documentos Markdown rastreados contra un modelo, con estado,
  payload extraíble y reingesta. CLI: `06-superficies-y-cli.md`.
- **Campos y secciones** — edición y consulta de campos y secciones de un documento. CLI:
  `06-superficies-y-cli.md`.
- **Índice, `find` y corpus** — índice derivado cacheado, direcciones, búsqueda física y
  semántica, `--where` y ranking de corpus. CLI: `06-superficies-y-cli.md`.
- **Aristas, grafo y predicates** — el grafo como vista derivada: relaciones y tipos,
  recorridos, análisis y export; con los stores enlazados alcanzables dentro (calificados
  por nombre de enlace; `--local` los deja fuera). CLI: `06-superficies-y-cli.md`.
- **Semántica** — tags como términos `:symbol/:term`, jerarquía de padres y equivalencias.
  CLI: `06-superficies-y-cli.md`.
- **Diagnósticos** — veredictos y reparaciones del store, con categorías que nombran la
  acción. CLI: `06-superficies-y-cli.md`.
- **Inclusión en git** — `kimun.include.edn` declara qué versiona un repo. Dueño:
  `13-include.md`.
- **Descomposición de código** — la hace el pod de tree-sitter (`pods/treesitter`,
  `kimun.host.treesitter`), único mecanismo; no hay adaptadores ni código Python (borrados el 2026-11-15).
- **`serve`** — API HTTP sobre los comandos, con token opcional. CLI:
  `06-superficies-y-cli.md`.
- **Empaquetado** — `bb jar`, `bb release` y el launcher. Dueño: `08-distribucion.md`.
- **No implementado** — el evaluador anclado (S4) y los derivados (S5), los motores de derivados
  (el cliente Python no existe: `C2` quedó diferida el 2026-11-15 hasta que kimun esté probado, decisión 47 y `12 §1`). `06 §6`, `docs/archive/v2/09-evaluador-anclado.md`.

## 2. Medidas

**Paridad con sldb v1**: suspendida desde el 2026-11-15 (decisión 54). Sus pruebas, scripts y marcador se borraron el 2026-11-16; viven en el historial de git.

**Store real** (1.551 documentos migrados de v1; antes → después, `10-escala.md §9`):
`docs show` 187,9 s → 0,37 s; `docs list` 180,3 s → 0,77 s; `stores check` 173,2 s → 18,7 s;
`stores verify` 228,4 s → 32,0 s; `.kimun/` 606 MB → 97,7 MB; migración desde v1 522 s → 71 s.
Abrir y commitear no crecen con la historia.

**Suite** — (el plan del 2026-11-16 cita 1 176 pruebas; no re-medido aquí) 890 tests / 9.584 aserciones, 109 namespaces (última corrida de `bb test`,
2026-10-09, con el pod tree-sitter construido), 0 fallos. `bb oracle`: 9/9 ids
canónicos; `bb lint`: 99 ficheros.

**Inclusión Wittgenstein (`C01–C28`, 2026-11-14)** — manifiesto `T01` en
`tests/wittgenstein-manifest.md` y `test/fixtures/wittgenstein/manifest.edn`: **28**
capacidades inventariadas (`20` de `W1` `matrix-shrdlu-spec`, `8` de `W2` `Matrix/Matrix`;
**25** `required`, **3** `deferred`: solo diálogo `C18` REPL completo en prosa (la vista mínima
del circuito/señales en `12 §1` y el API de sesión `T13` siguen requeridos), `C24` hipertensor
opt-in, `C28` OWL/UNL; más **5** exclusiones transversales `X01–X05`). Estado honesto en
`kimun` hoy: **0** `implemented`, **12** `partial` (`C01`, `C02`, `C11`, `C12`, `C14`, `C17`,
`C19`, `C20`, `C21`, `C23`, `C26`, `C27`), **5** `direction` (`C03`, `C05`, `C06`, `C07`,
`C13`) y **11** `absent` (`C04`, `C08`, `C09`, `C10`, `C15`, `C16`, `C18`, `C22`, `C24`, `C25`,
`C28`). Ninguna intención marcada como `direction` cuenta como implementada. `D-G` sigue abierta
para el dueño; `D0` ya no (decisión 46): su contrato S–M está derivado y espera a K1.

**Formato 4 de este repo** (desk de 433 KB, 539 documentos; antes = formato 3 de S1b, después
= formato 4): aparente **8,07 MB → 2,51 MB** (objetos 12.494 → 4.808; tree-object 1,65 MB →
3,9 KB; plan 4,03 MB → 907 KB; el registro: anchor externo 172 KB + descriptor 186 KB +
nodo raíz 62 KB + aristas 489 KB). El árbol del documento ya no está en el CAS: vive en
`<store>/cache/` (derivado, gitignored, borrable; 2,26 MB, medido aparte por `bb store-size`).
Detalle en `10-escala.md §11`. Lo que el formato 4 todavía **no** hace: el plan resuelto del
build sigue en el CAS, así que el store no baja de los 433 KB del desk que indexa (`12 §1`).

## 3. Decisiones de diseño (confirmadas por el usuario salvo las marcadas EN REVISIÓN o PENDIENTE)

| # | decisión | dónde |
|---|---|---|
| 1 | `class`/`kind` entran al hash del nodo; mismo texto como `:text` y `:opaque` son dos nodos | `02 §2.1`, `atom-canonical-content-and-node-hashing` |
| 2 | sucesión: `reference`/`binding` siguen al sucesor (en cualquier extremo); `semantic`/`projection` quedan `superseded`; `derived` se invalida | `02 §6.1`, `atom-re-anchoring-rules-under-succession` |
| 3 | el timestamp no entra en el id de arista: misma afirmación por el mismo origen = una arista | `02 §3.2`, `atom-evidence-required-per-edge-type` |
| 4 | ids de árbol nominales (ULID), descriptor en CAS, Revision de 8 campos | `02 §3.1, §5`, `atom-revision-id-edge-set-and-diff` |
| 5 | primer slice: SHA-256, backend de archivos, validación solo en Babashka, M/G como datos | `02 §8.1`, `atom-first-slice-runtime-choices` |
| 6 | **árboles de posiciones** (Git-like): un nodo puede ocurrir N veces en un árbol; ownership por path; op `:detach` | `02 §3.1, §5.1`, `atom-decision-trees-are-trees-of-positions-git-like` |
| 7 | superficie Markdown como **perfil cerrado** (SLDB-MD): fuera del perfil = opaco; marcas inline viven en el bloque, las hojas de texto son puras y compartidas; `_` nunca es énfasis; párrafos canónicos de una línea | `04`, `atom-sldb-md-profile-…`, `atom-inline-marks-live-on-the-block-…` |
| 8 | no hay Lisp aparte (EDN + SCI); kernel `.cljc` con hosts como adaptadores. La parte "cero Rust" queda **reemplazada por la 19** (2026-10-06) | `02 §8`, `atom-decision-no-separate-lisp-metalanguage`, `atom-decision-no-rust-in-the-repository` → decisión 19 |
| 9 | los estados deterministas son **tres** (`intact`/`superseded`/`orphan`), se calculan **por extremo** y la arista toma el peor; `drifted` es un `orphan` que la reconciliación supo nombrar, no un cuarto estado | `02 §6.2, §6.5`, `atom-anchor-state-is-derived-and-computed-per-endpoint`, `atom-drifted-is-a-reconciled-orphan` |
| 10 | una propuesta de reconciliación vive **fuera del pool**; aceptarla es una transacción `supersedes` cuya única evidencia es el actor: responde quien acepta, no el heurístico | `02 §6.5`, inv. 18 |
| 11 | el `:fingerprint` externo es `<alg>:<hex>` con el algoritmo del store; el kernel valida la forma y compara, y qué bytes se digieren es contrato del motor emisor | `02 §6.4`, `atom-external-fingerprint-form-and-who-computes-it` |
| 12 | **un solo producto v2**: sldb v1 y kgdb se congelan (`v1-frozen`); la lectura del evaluador de `pron` se porta aquí (S4, pendiente) | `06`, `08 §1`, plan 2026-09-06 |
| 13 | los modelos son **nodos del árbol**: descriptores EDN como opacos `edn/model` en el árbol `models`, versionados por `:replace` ⇒ `supersedes`, proyectables para auditarse | `07` |
| 14 | **Babashka es el host definitivo del CLI**; el kernel sigue `.cljc`; la paridad Node queda en el drawer (UI) | `08 §2` |
| 15 | rutas **relativas** en `store.edn` (`:links`), índices y export; nunca absolutas (vicio v1) | `06 §2` |
| 16 | toda escritura (evaluador incluido) es un `TransactionPlan`; los derivados se invalidan por `anchor/states` sobre aristas `derived`, sin tracker aparte | `docs/archive/v2/09-evaluador-anclado.md §3–4` |
| 17 | **tres nombres para tres cosas** (2026-09-07): el producto v2 es **`kimun`** (Mapudungun *kimün*); **`pron`** es la KB **y** su evaluador Python en un solo paquete (repo `jpcosec/pron`, pip `pron`), que importa `kinesis`; `knowledge` queda congelado como el legacy de julio. Nada de v2 usa la palabra `knowledge` como identificador | `08 §0` |
| 18 | **v1 sigue en v1** (2026-10-06): los consumidores de sldb v1 (deskops, pron, legos, UIs…) se quedan en v1 por ahora; kimun avanza sin migrarlos. Como kimun no está desplegado, sus stores y su historial se pueden regenerar cuando haga falta ("retrackear"): la paridad byte a byte con v1 es referencia, no obligación | `12-ecosistema-y-pendientes.md` |
| 19 | **política de lenguajes** (2026-10-06): la lógica de alto nivel se escribe mayormente en Clojure (kernel `.cljc`, superficies, CLI). Se admiten servicios nativos (Rust, C, Zig, Go…) si son fácilmente portables y empaquetables por plataforma, y plugins en cualquier lenguaje (Python, Java, JS/TS…) para no reimplementar lo que ya existe. Condición: pocos y con frontera clara — un protocolo documentado (pods de Babashka; el subsistema de adaptadores se borró el 2026-11-15), nunca lógica de dominio repartida entre lenguajes. Primer candidato: tree-sitter (C) como pod para el parseo de Markdown y código | decisión del dueño (2026-10-06) |
| 20 | **los namespaces del código pasan de `sldb.*` a `kimun.*`** (2026-10-08), solo en este repo: `sldb.kernel.*` → `kimun.kernel.*`, `sldb.host.*` → `kimun.host.*`, `sldb.surface.markdown.*` → `kimun.surface.markdown.*`. Los **identificadores de datos** se mantienen intactos porque entran en los hashes: nombres de engine (`sldb.surface.markdown`), ids de nodo `sldb://…`, tag lang `sldb/tag`, contextos `sldb/semantic/…`, contrato de export `sldb_kgdb_semantic_export` y keywords de error/formato. El lint de anillos resuelve el prefijo más específico, así que `kimun.kernel./host./surface.markdown.` conservan las mismas reglas | `03 §1` |
| 21 | **autoridad por clase** (2026-10-08): bytes → archivo (+git); estructura → pool como caché verificable; hechos → pool, decisiones humanas además como documentos. Revisa `01 §4` (principio 1) | `14 §2` |
| 22 | todo hecho vive en un `W_i`; contexto por defecto = el documento `source`; ruteo entre contextos solo por proyecciones promovidas | `14 §3` |
| 23 | `R`, `A`, `B` son posiciones en taxonomías con herencia; las propiedades algebraicas de `R` se declaran por contexto; el vocabulario `L_i` es un documento `source` | `14 §4` |
| 24 | **(EN REVISIÓN, ver `drafts/capa-g-dudas.md`)** afirmación = `:status` en evidencia de arista `projection`; `V`/`S` índice derivado por `(hecho, W_i)`; nada pasa a `TRUE` sin decisión registrada | `14 §5` |
| 25 | conflicto lógico = propuesta fuera del pool; se resuelve por transacción; nunca en silencio | `14 §6` |
| 26 | cada `W_i` se evalúa como matrices booleanas por relación; índices derivados; nunca `p×p` global; motor = implementación | `14 §8` |
| 27 | **formato 4 del store**: registro por documento (anchor + clase + hash raíz + hash de contenido); el árbol/bloques/nodos de un documento con archivo son caché verificable (root hash + posiciones referenciadas, `<store>/cache/`); contenido por referencia con `inline` por caso; log como caché bajo `:git :ignore`; decisiones humanas como documentos | `14 §10`, `14 §18–§19` |
| 28 | modelo = símbolo-tipo + selector + proyecciones; el template de v1 es un caso especial; la paridad v1 es su regresión | `14 §9` |
| 29 | tree-sitter como pod es el parser de la capa M, por fases; SLDB-MD sigue como perfil cerrado; `blocks.cljc` como oráculo hasta paridad byte a byte | `14 §11`, spike R2 |
| 30 | Datalog para la consulta de G; Datalevin (pod) candidato con respaldo in-process; índice G derivado | `14 §8`, spike R1 |
| 31 | **(EN REVISIÓN, ver `drafts/capa-g-dudas.md`)** salida de prompt = `proposed` por defecto; políticas de promoción por store/contexto (harness) | `14 §7` |
| 32 | unidad de anclaje = hoja + grafemas NFC | `14 §7` |
| 33 | unidad de escritura = revisión por acción + log de hechos en flujo consolidado | `14 §12` |
| 34 | transversales de primera clase = pack mínimo de autodescripción (clase de datos, procedencia, contexto, kimun mismo); texto y código | `14 §12`, `§16 P13` |
| 35 | cualquiera propone; el humano tiene la autoridad máxima; UI por definir | `14 §12` |
| 36 | el diccionario de signos cortos es otro grafo, por contexto | `14 §16 P14` |
| 37 | hechos de la forma más simple + caché de derivados por inputs | `14 §12` |
| 38 | embeddings sin idioma fijo ni plantilla; modelo multilingüe; `dog/perro/hund` misma vecindad es un test | `14 §16 P15` |
| 39 | lenguaje de selección por spike en etapa A; índice por revisión derivado también en `:git :track` | `14 §12` |
| 40 | **un documento se lee bajo el contrato con el que se escribió** (A1, 2026-10-08): la evidencia de la arista `reference` root→fuente registra `:contract` = id del descriptor, y la sucesión del modelo no la toca; `docs show\|list`, `fields`, `sections`, `find`, `edges` y `stores check\|health` leen ese descriptor (el nodo viejo sigue en el CAS) hasta que `docs update` o `stores update` o `models define --backfill` re-renderizan el payload registrado bajo el contrato actual. Un documento sin `:contract` (previo a A1 o migrado de v1) cae al contrato actual | `06`, `07 §1` |
| 41 | **todo documento es instancia de una clase** (2026-10-09): genérico = estructura tree-sitter a la granularidad del perfil (por store); gobernado = un modelo; lo que no calza se resuelve por herencia (Clojure `derive`/`isa?`); una clase cambiada es otra clase (inmutable) | `14 §18` |
| 42 | **anclaje y generación son modelos distintos**: el de anclaje selecciona lo que se ancla al leer (lo no seleccionado no entra al store); el de generación produce texto; el template de v1 confundía ambos y el round-trip estricto deja de ser obligación. El azúcar se observa en el nodo (sin anclaje / fórmula o string fijo en generación). Documentos **source** (el archivo es la verdad) vs **generados** (la verdad son los datos de la instancia; el desk de este repo es generado) | `14 §18.7–9` |
| 43 | **tres capas**: caché (recomputable, borrable), store estable (hashes y anchors del ambiente para detectar mutaciones y reconciliar; nunca contenido) y grafo (modelos, clases, capa G) como paquete aparte sobre la base | `14 §19` |
| 44 | **trackeable vs trackeado**: el Merkle del store estable va sobre lo trackeado (lo que alimenta el grafo), organizado **por ruta** (no por clase), para bajar por la rama que cambió y reconciliar localmente; de un documento solo se guarda/hashea la proyección trackeada; el plan del build va a caché o se elimina | `14 §20` |
| 45 | **formato 4** (implementado, S1c): registro por documento (anchor + clase + hash raíz + hash de contenido) y árbol/bloques/campos como caché por hash en `<store>/cache/`; el mapa mmap vacío es una constante que no se exige en el CAS | `10 §11`, `14 §18–19` |
| 46 | el hash detecta mutaciones; no es identidad, y tampoco lo es el anclaje estructural (corrección del dueño, 2026-11-14, que sustituye «identidad = lugar»). **Contrato S–M derivado por el concilio (2026-11-15), sin pregunta pendiente al dueño:** la unidad es el hecho detectado, con identidad propia acuñada al marcarla; el anclaje es su referencia vigente, sin identidad de nodo; una observación nueva no es una unidad nueva; la ausencia no termina una unidad (el sensor informa `missing`; cerrar o re-anclar se declara); uno a uno conserva la unidad, y copiar, dividir o fusionar crean unidades nuevas con linaje declarado. Las reglas son inferencias del concilio. Implementación pendiente: K1. Revisa el principio 3 de `01 §4` y las decisiones 1, 3 y 4 en lo que toca a unidades (los valores pueden seguir direccionándose por hash) | `14 §21`, `runs/research/2026-11-15/concilio/d0-contrato-sintesis.md` |
| 47 | **kimun reemplazará el núcleo de sldb, y el wrapper Python va después de la prueba** (dueño, 2026-11-15): el reemplazo está decidido; hoy kimun no está listo para hacerlo (`runs/research/2026-11-15/reemplazo-sldb/preparacion.md`). El wrapper Python para los consumidores se agrega **después** de que kimun esté probadamente funcionando, no antes: `C2` deja de estar retirada y pasa a diferida, detrás de `RS1` (prueba sobre el sldb actual) y `RS2` (contrato estable de superficie). Matiza la decisión 18: los consumidores siguen en v1 hasta entonces. Qué cuenta como «probadamente funcionando» es una propuesta del equipo en `RS1`, pendiente del dueño | `12-ecosistema-y-pendientes.md §1` (`RS1`, `RS2`, `C2`), `runs/research/2026-11-15/reemplazo-sldb/` |
| 48 | **kimun ve, sintetiza y genera; no exige nada al resto** (dueño, 2026-11-15): «Kimun solo ve, sintetiza y genera, no debería exigir nada al resto.» Y: «la regla más importante de este repo, la realidad existe NO en nuestro sistema, sino en el código de afuera.» Lectura del equipo: un documento generado es una materialización desde el grafo, no una fuente | `drafts/sesion-2026-11-15.md §2`, `§11`; `drafts/diseno-declaraciones.md §7` |
| 49 | **las declaraciones son Clojure que corre; el journal y los planes como registro propio sobran** (dueño, 2026-11-15): «Creo que es un poco redundante tener ese comando de journal o no?»; a la pregunta de si las declaraciones deben vivir como formas de Clojure en archivos, con su docstring tipado, y el store ser solo un índice derivado, «Así es.» Lectura del equipo: la historia la lleva el control de versiones del proyecto; el sensor sigue haciendo falta. **No está diseñado** (cómo se escribe el «docstring tipado») | `drafts/sesion-2026-11-15.md §6`, `§10`; `00-nucleo.md 7.1` |
| 50 | **dos niveles de monitoreo; git para el de archivo; rehacer git, diferido** (dueño, 2026-11-15): «1- es el monitoreo "hasta" el nivel de archivo, que es básicamente lo que hace git. 2- el monitoreo "dentro" de los archivos a nivel de fields. Solo lo que está anchored importa a nivel de fields.» Y: «el "rehacer git" déjalo EXPLÍCITAMENTE diferido para después.» Kimun usa git para el nivel de archivo; lo que ya tiene de eso (árbol Merkle por ruta, huella por archivo, revisiones, log) no se sigue construyendo. Dijo además: «dejemos de hablar de identidad porque confunde» | `drafts/sesion-2026-11-15.md §11`; `12 §1` (nota «Rehacer git») |
| 51 | **clases como etiquetas que comprimen** (dueño, 2026-11-15): «Pongámosle contexto, namespace, clase, tipo, etc. da lo mismo.»; «La gracia de poder hacer clases es que así podemos reutilizar código, lo que haría que indexar muchas cosas iguales sea una declaración en lista nomás.» Un nodo lleva varias clases, una por eje, y los nombres con puntos son jerarquía («correcto») | `drafts/sesion-2026-11-15.md §1`, `§10`, `§11` |
| 52 | **dos cachés** (dueño, 2026-11-15): «tenemos 2 tipos de cache»: el generado «TIENE que separarse entre código comprimido (piensa en git) y código explícito». Comprimido = lo que kimun genera al indexar en masa, para máquinas y desechable; explícito = lo que importa, expandido y legible (confirmado por el dueño: «estás en lo correcto») | `drafts/sesion-2026-11-15.md §11`; `drafts/diseno-declaraciones.md §6` |
| 53 | **todo lo que generaba SLDB es materialización desde modelos** (dueño, 2026-11-15): «TODOS los documentos que genera SLDB siguen lo mismo. Pasarían a ser MATERIALIZACIONES DESDE MODELOS. Los documentos fuentes son los documentos fuentes de cualquier otro proyecto tal y como si no usara NADA de kimun/sldb/deskops.» Redefine `M2` (`12 §1`): no es regenerar modelos para leer los Markdown del desk como fuentes | `drafts/sesion-2026-11-15.md §4` |
| 54 | **la paridad con v1 queda suspendida** (dueño, 2026-11-15): «olvidémonos de la paridad con v1 hasta que terminemos esta "rearquitecturización" que estamos haciendo.» Suspendida, no borrada; un corte que cambie el comportamiento a propósito retira o ajusta la prueba de paridad diciéndolo | `drafts/sesion-2026-11-15.md §8`; `12 §1` |
| 55 | **nada se borra sin marcar; la limpieza se declara por proyecto y tipo de dato** (dueño, 2026-11-15): «no deberíamos borrar nada así automáticamente, algo que nada referencia se marca como "borrado" y luego si queremos por ejemplo reducir el caché o limpiar se quita.» y «eso debería poder declararse por cada proyecto y según cada tipo de dato.» Supuesto del equipo, sin confirmar: sin política declarada no se quita nada | `drafts/sesion-2026-11-15.md §5` |
| 56 | **borrar la grasa** (dueño, 2026-11-15): «necesito que borres toda la grasa, incluidos esos adaptadores. No tengas cuidado, todo lo que se borra vive en el historial git.» Hecho ese día: `python/`, el subsistema de adaptadores, los formatos de store viejos y el código muerto del kernel y el host; el resto, en el plan (`drafts/plan-producto.md`, etapas 0–2). Sobre las ideas de arquitectura: «Creo que lo que traza las fronteras al final es clean_code, NO duplicar fronteras» | `drafts/sesion-2026-11-15.md §3`, `§9`; `drafts/plan-producto.md` |
