# kimun — El grafo como juego del lenguaje: principios, autoridad y store

> Borrador estable (2026-10-08), escrito para fijar lo que kimun es antes de seguir construyendo.
> **Revisa** `01 §4.1` (autoridad única del pool), `07` (modelo = plantilla), `11 §1` (tree-sitter
> fuera de proceso) y `12 §3` (wittgenstein como "la capa G"). Mientras esas revisiones no se
> integren en sus documentos, este gana. **No toca** las decisiones 1–20 de `05`: todas siguen
> vigentes. Límite declarado por el dueño: motor AST-first para tratar texto, código y otros
> archivos como conocimiento; conocimiento = colección de proposiciones `(R A B)`; Clojure.
>
> Insumos: dossiers de diseño del store (`runs/research/2026-10-08/store-dossier-L{1,2,3}-report.md`),
> `drafts/analisis-juego-del-lenguaje.md`, spikes R1 (Datalog en bb) y R2 (tree-sitter como pod),
> `wittgenstein/matrix-shrdlu-spec/specs/{SPEC,matrix,grounding,provenance}.md`.

> **Cómo leer este documento (2026-10-09).** Creció por adendas durante la sesión de diseño
> (registro: `drafts/sesion-2026-10-08-09.md`). Estado por sección:
> - **Vigentes:** §1–§8 (principios P1–P12 y su despliegue), §11 (motores), §13 (etapas),
>   §16 (P13–P15), §17 (respuestas), **§18** (clases, instancias, anclaje y generación),
>   **§19** (caché, store estable, grafo), **§20** (trackeable vs trackeado; Merkle de reconciliación).
> - **Superados en parte:** §9 (por §18), §10 (el registro por documento lo fija §18–§20; la tabla
>   queda como diagnóstico del formato 2→3), §12 decisiones 24 y 31 (en revisión por la capa G:
>   `drafts/capa-g-dudas.md`).
> - **Pendiente de confirmar:** **§21** (el hash detecta mutaciones; no es identidad). Revisa
>   `01 §4` principio 3 y las decisiones 1, 3 y 4; lectura de base: `drafts/ids-desde-matrix.md`.

## 1. Principios rectores

Son del dueño; el resto del documento los despliega. Se numeran `P1…P12` para citarlos.

- **P1 — Juego del lenguaje.** El grafo es una estructura simbólica evaluable sobre un mundo de
  conocimiento. Evalúa "su realidad" contra principios rectores que llegan por **instrucción** al
  grafo o por **ingesta de documentos marcados como `source`**.
- **P2 — Todo es contextual** (para el dueño, "el principio 1 de todo": rige sobre los demás). No hay hechos ni símbolos universales: toda KB está circunscrita a
  un contexto `W_i`. Los contextos son **ruteables** entre sí por el mecanismo de wittgenstein
  (proyecciones y equivalencias promovidas), nunca por coincidencia de cadenas.
- **P3 — `(R A B)` por conveniencia, con jerarquía.** Representamos todo como `(R A B)`, pero `R`,
  `A` y `B` son instancias de árboles jerárquicos. Las propiedades algebraicas de `R` (transitiva,
  conmutativa, biyectiva…) **dependen del contexto**: `comparte` es transitiva en "el agua comparte
  liquidez con la bencina" y no lo es en "Juan comparte con Pedro".
- **P4 — Sin contradicciones dentro de un contexto.** La estructura no admite errores lógicos
  internos (categorías de sentido del Tractatus). Si dos fuentes chocan, queda **marcado como
  conflicto lógico** y el sistema pide desambiguación: contextos distintos, o una fuente
  equivocada. Nunca se resuelve en silencio.
- **P5 — Anchors con hash.** Los punteros del grafo sobre la base documental son *anchors*; cada
  uno lleva el hash del source evaluado. Si el source cambia, la KB que cuelga de él se recalcula.
- **P6 — Indexación estructural completa.** El documento se indexa entero de forma estructural
  (tree-sitter); esa indexación tiene anchors sobre anchors (Merkle, árbol de posiciones). Sobre
  ella **emergen** las evaluaciones semánticas (contexto, tipado, `(R A B)`). **El LLM no inventa:
  archiva.**
- **P7 — Evaluar en dos etapas y justificar.** Una proposición se evalúa contra la KB recorriendo
  el grafo (consulta) y por álgebra booleana sobre matrices por contexto. Toda respuesta puede
  trazarse hasta "de dónde sale la justificación".
- **P8 — Anchors de evaluación.** Un anchor puede ser una referencia textual, o una evaluación
  "en código" o "en prompt" sobre el texto. El tipado de documentos es flexible: encuentra la
  sección que interesa en documentos de largos distintos; la estructura de interés es una
  **abstracción sobre el AST**, no el AST crudo.
- **P9 — Matrices binarias por contexto.** Cada `W_i` de `(R A B)` se mapea a matrices booleanas
  no por capricho: las operaciones booleanas (producto, cierre) salen casi instantáneas, y con
  GPU más. En el fondo sigue siendo un grafo operando sobre texto.
- **P10 — Modelo = tipo de subgrafo estable.** La noción de "modelo" de SLDB deja de ser una
  plantilla dura por la que pasa el texto y pasa a ser un **tipo de subgrafo estructuralmente
  estable** en el AST. Qué es estable no es arbitrario: lo proponen Matrix y herramientas de
  clustering; lo promueve una decisión registrada.
- **P11 — No todo es igual de indexable.** Lo menos estructurado se indexa, pero cuesta más
  (motores). Los embeddings sobre anchors dan una ubicación pseudo-estable de co-ocurrencia,
  útil a escala; **co-ocurrencia ≠ `(R A B)`**.
- **P12 — Tierra firme.** Primero la capa S funcionando bien; después una arquitectura sostenible;
  al final completar. Se aprovecha lo que existe y está probado (si el grafo resulta ser Datalog
  sobre Clojure, bienvenido). Clojure da gratis inmutabilidad, herencia (`derive`/`isa?`) y
  homoiconicidad para instrucciones declarativas sobre el grafo.

## 2. Capas y autoridad

La autoridad **se parte por clase**; ya no hay una sola fuente de verdad. Esto revisa `01 §4.1`.

| capa | qué es | autoridad | dónde vive | reconstruible |
|---|---|---|---|---|
| **S** bytes | el texto tal cual | **el archivo**; git es su historia | fuera del store; el store guarda un anchor externo `{:locator :sample :fingerprint}` (`02 §2.1`) | no aplica: es la fuente |
| **M** estructura | árbol de posiciones sobre el pool; hojas `:text` compartidas; identidad = hash del contenido canónico | **el pool**, por determinismo (`atom-canonical-projected-or-asserted`) | caché verificable: se persiste el **root hash** y las posiciones que algún hecho referencia; el resto se recomputa del archivo | sí, por parse determinista |
| **G** hechos | `(R A B) @ W_i` con evidencia, verdad y sentido | **el pool**; las decisiones humanas además como documentos `source` (§7) | pool (nodos `:fact`, aristas `projection`/`binding` con evidencia) + índice booleano por contexto (derivado) | lo proyectado sí (motores); lo afirmado se reconstruye desde los documentos de decisión |

Regla que lo resume: **canónico ≠ persistido.** Un parse determinista es canónico *y* reconstruible;
no hay que guardarlo, hay que poder verificarlo. Se persiste lo que alguien referencia (regla que
ya rige para los spans, `atom-stand-off-addresses-are-virtual-until-referenced`) y lo que alguien
afirmó.

## 3. Contextos (P2)

- Un contexto `W_i` es, como hoy, un nodo `:fact/:context` **y** un árbol-índice sobre el pool
  (`atom-w-i-context-node-and-index`). Todo hecho nombra su `W_i`; no existe el hecho sin contexto.
- **Contexto por defecto:** cada documento `source` abre su propio `W_i`. Contextos más finos
  (sección, párrafo) o más gruesos (un corpus) se declaran por instrucción.
- **Ruteo entre contextos:** una pregunta cruza mundos solo por proyecciones y equivalencias
  **promovidas** (wittgenstein `convergence.md`, `grounding.md`: `equivalent` si `φ ≥ 0,95` en
  ambos sentidos; `projection` en uno). La identidad entre contextos se propone por implicación
  entre configuraciones, nunca por string, y se confirma por decisión registrada.
- Consecuencia de P4: la contradicción **solo es detectable dentro de un `W_i`**. Entre fuentes
  aparece en el momento de unir contextos; ahí se pide la desambiguación (§6), no en la ingesta.

## 4. `(R A B)` con jerarquía (P3)

- `R`, `A` y `B` son símbolos (`:symbol/:term`) que ocupan posiciones en **árboles de taxonomía**
  del pool (`:kind :taxonomy`, como el árbol `models` de `07`). Un símbolo hereda de sus ancestros
  en su árbol; en Clojure esto es un `hierarchy` (`make-hierarchy`/`derive`/`isa?`), que es un
  valor EDN y por tanto serializable y content-addressed.
- Las **propiedades algebraicas de `R` se declaran por contexto** como hechos del propio juego:
  `(property comparte transitive) @ W_i`. El evaluador booleano las lee de ahí (cierre transitivo
  solo si `transitive` vale en ese `W_i`).
- El **vocabulario admisible** de un contexto (relaciones, ejes `A × B`; `L_i` en Matrix) es un
  documento `source` en EDN. Homoiconicidad en serio: se declara igual que cualquier otro hecho.
- `:symbol/:proposition {:form …}` admite formas anidadas (reglas `(if …)`); cierra el desajuste
  anotado en `12 §3`.

## 5. Verdad y sentido

Hoy el triple es `{:subject :predicate :object :context}` y no tiene dónde poner verdad ni sentido
(`12 §3`). Se fija así:

- **Afirmación** (quién dijo qué): evidencia de la arista `projection` que une el hecho con su
  origen, con `:status ∈ #{observed confirmed rejected retracted}` y exactamente un origen
  (`:actor` o `:engine`+`:version`), como ya pide `atom-evidence-required-per-edge-type`. Tiers de
  wittgenstein (`observed > confirmed > inferred > statistical`): `statistical` nunca deduce.
- **Valor** (qué vale en el juego): `V ∈ #{TRUE FALSE UNKNOWN}` y `S ∈ #{sinnvoll sinnlos unsinnig}`
  por `(hecho, W_i)`, calculados por el evaluador booleano (§8) y guardados como **índice
  derivado**, nunca como nodo. `unsinnig` solo lo marca un humano (el silencio del corpus no es
  absurdo, `grounding.md`).
- **Nada pasa de `UNKNOWN` a `TRUE` sin una decisión registrada**: la promoción es una transacción
  con actor (sucesión + evidencia), igual que aceptar una propuesta `drifted` (decisión 10).

## 6. Conflicto lógico (P4)

Tercer tipo de conflicto, junto al estructural (`ConflictSet`, `02 §5.2`) y al de anclaje
(`drifted`, decisión 9–10):

- Lo **produce** el evaluador de sentido al unir contextos o al promover un hecho que vuelve
  inconsistente un `W_i`. Es un informe `{:context :facts [...] :kind :contradiction|:ambiguous
  :candidates [...]}`.
- **Vive fuera del pool** hasta que un actor lo resuelve, como toda propuesta (decisión 10).
- **Resoluciones admitidas**, cada una una transacción registrada: (a) separar contextos (nuevo
  `W_i`, los hechos se re-anclan); (b) retractar (sucesión + `:status :retracted`, `V = FALSE` o
  `UNKNOWN`, con historia); (c) marcar una fuente como equivocada en ese contexto (hecho sobre el
  anchor del documento). Nunca se sobrescribe en silencio (`provenance.md`).
- Un conflicto sin resolver **no bloquea** la KB: el `W_i` afectado se marca y las consultas que
  lo tocan devuelven `:ambiguous` con candidatos, nunca eligen (`matrix.md`, `09 §2`).

## 7. Anchors (P5, P8)

| tipo | forma | invalidación |
|---|---|---|
| **externo** | `:sign/:external {:locator :sample :fingerprint}`; el fingerprint es `<alg>:<hex>` de los bytes (decisión 11) | fingerprint ≠ hash actual ⇒ el documento está desactualizado |
| **estructural** | posición en un árbol de documento, u hoja + `[start end]` en grafemas (`02 §4.1`) | por sucesión: hoja igual ⇒ mismo id ⇒ `intact`; hoja cambiada ⇒ `orphan` ⇒ reconciliación ⇒ `drifted` ⇒ actor (decisiones 9–10) |
| **de evaluación** | derivado `:opaque {:format "edn/value"}` con aristas `derived` hacia su expresión y sus inputs (`docs/archive/v2/09 §4`); motores `sci` (puro), `shell:CMD` (opt-in), `llm:` (nuevo) | `anchor/states` sobre sus aristas `derived`; `derive run --stale --propagate` hasta punto fijo |

Reglas:

- **Recalcular tiene granularidad de hoja.** Como la identidad es el hash, solo lo que cuelga de
  hojas cambiadas pierde el anclaje. Un hecho curado sobre una hoja intacta sobrevive sin tocarlo.
  Un hecho que cruza hojas o depende del tipado del documento entero se marca `orphan` y pasa por
  reconciliación; **nunca se descarta en silencio**.
- **Lo que sale de un prompt es siempre `proposed`**: una proyección `llm:` nunca es canónica ni se
  auto-confirma; su versión es `modelo + hash del prompt + parámetros`. Coherente con "el LLM no
  decide verdad" (`grounding.md`).
- **Unidad de anclaje:** la de kimun (hoja + offsets en grafemas NFC). Los chunks con offsets en
  code points de wittgenstein se mapean a hojas al entrar (desajuste listado en `12 §3`).

## 8. Evaluación en dos etapas (P7, P9)

```
pregunta (EDN)
  → resolver símbolos        cascada de `09 §2`: :resolved | :ambiguous (exit 2) | :missing (exit 1)
  → recorrer el grafo        Datalog sobre triples con procedencia (índice derivado; R1)
  → evaluar en el W_i        álgebra booleana: V_i, S_i, I; cierre según propiedades de R (§4)
  → respuesta                {:answer :truth :sense :justification [aristas con evidencia] :citations [spans]}
```

- **Representación vinculante (P9):** cada `W_i` se materializa, para evaluar, como matrices
  booleanas por relación (`A × B`), más `V_i`, `S_i` e `I`. Son **índices derivados**, no nodos,
  y **nunca crecen `p×p` sobre el corpus entero** (límite que wittgenstein aprendió con tres OOM).
  Qué motor las calcula es implementación: hoy Matrix (Python) por protocolo; portar el núcleo
  booleano a Clojure (o a GPU) es una decisión posterior con datos.
- **Consulta (etapa 1):** R1 midió que, bajo Babashka, el único Datalog que carga es el **pod de
  Datalevin** (Datascript, Asami, core.logic y hiredman no corren en SCI). Con el desk real
  (47 349 triples): carga 2–3 s, consulta de dos saltos 35–205 ms, regla recursiva 15 ms; con 1 M
  de triples sintéticos: 30–60 s de carga, cierre recursivo de 1,47 M filas en 15–37 s, 200 MB
  en LMDB. Reservas: binario de 278 MB por plataforma y un NPE en reglas sembradas del pod 1.1.0.
  Decisión: Datalog sí; el índice G es **derivado y reconstruible** (`<store>/index/graph/`),
  con un evaluador semi-naive in-process como respaldo offline. El motor definitivo se confirma
  en la etapa A con el índice real.
- Nada de lo que produce una consulta se persiste, salvo que alguien lo `assert`e (vuelve por
  `TransactionPlan`, decisión 16).

## 9. Modelos como tipos de subgrafo (P10)

> **Superado por §18** (2026-10-08) en lo que contradiga: todo documento es instancia de una clase; la
> herencia resuelve lo que no calza; no todo el texto se indexa.

- Un modelo es: un **símbolo-tipo** (posición en el árbol `models`, con herencia) + un **selector**
  (consulta sobre el AST visto como triples: "una sección cuyo heading es X seguida de una tabla…")
  + **proyecciones** (render a Markdown, a JSON Schema, a Python). Los documentos no "pasan por"
  una plantilla: el selector **clasifica** subgrafos y produce hechos `(instance-of <subgrafo>
  <Modelo>) @ W_i` con evidencia del motor que los reconoció.
- El **template de v1 es un caso especial**: un selector que exige exactamente la secuencia de
  bloques del template. Mismo comportamiento, otra base. La suite de paridad (515 tests portados,
  `tests/parity-v1.md`) es la regresión de ese caso especial y no se pierde.
- Las **marcas de template** (`⸢rev•campo⸥`, `render`, traits) se conservan como azúcar sobre el
  selector; `07` se reescribe sobre esta base. La "receta por bloque" (`parity-v1:132`) deja de
  ser deuda: el selector es la receta.
- **Descubrimiento:** qué subgrafos son estables lo proponen motores (convergencia CHIC/SIA de
  wittgenstein, clustering) como modelos `proposed`; un humano promueve. Requiere el AST como
  triples (§8), por eso llega después de la capa G.
- **Lenguaje de selección:** pendiente de spike. Candidato principal: Datalog (una sola lengua para
  recorrer y seleccionar); alternativas: patrones EDN, consultas tree-sitter. No se improvisa.

## 10. El store (lo que cambia en disco)

> **El registro por documento lo fija §18** (2026-10-08): anchor + clase de la que es instancia + hash
> raíz; árbol, bloques y valores de campos son caché. La tabla de abajo sigue valiendo como
> diagnóstico del formato 2 → 3.

Formato **3** del store; `10-escala.md` sigue siendo el dueño del formato y recibirá esta sección.
`stores upgrade 2→3` reconstruye desde los archivos (`include build`), no migra copias.

| hoy (formato 2) | formato 3 |
|---|---|
| copia verbatim del texto (`source-node`, `src/kimun/surface/docs.cljc:65`) | **eliminada**; ningún spec la pedía |
| hojas `:text` con su texto dentro del objeto | **contenido por referencia**: el objeto guarda el id (hash); los bytes se leen del archivo por el anchor externo. `inline` opcional por store (`:content :inline` en `store.edn`) para fuentes que no se pueden releer barato |
| árbol de posiciones completo en el CAS por revisión | **caché verificable**: se persiste el root hash y las posiciones referenciadas por hechos; `index/` reconstruye el resto desde el archivo |
| log + snapshot mmap por cada transacción de tracking | el log de kimun es **caché** cuando el store es `:git :ignore`; una revisión por `build`. Las **decisiones humanas** (confirmar, desambiguar, retractar, promover) se materializan como documentos `source` en el repo, así el store siempre se reconstruye desde archivos + decisiones (autopoiesis G/M→S de SMG) |
| sin índice G | `index/graph/` (Datalog) e `index/vectors/` (embeddings): derivados, gitignored, por cabecera |

Qué **no** cambia: nodos content-addressed con `class`/`kind` en el hash, árboles de posiciones,
aristas con evidencia, `TransactionPlan` all-or-nothing, revisiones inmutables con sucesión,
estados de anclaje, capacidades por actor, `heads.edn` como punto de commit. Las 20 decisiones de
`05` siguen en pie.

**Tamaño esperado.** Hoy este repo guarda 27 MB (aparente; 119 MB en disco) por 0,44 MB de desk:
`log.edn` 5,8 MB más un snapshot del índice por cada una de ~540 transacciones, más blob verbatim
(763 KB) y hojas. Con formato 3 queda por documento un anchor externo (~150 B), los anchors que
algún hecho referencia y los hechos. Advertencia medida: un índice *completo* de hashes en EDN
(64 hex por posición) pesa más que el texto que indexa; por eso el árbol es caché, no registro.

## 11. Motores y protocolo

El protocolo 1 (`11`) se generaliza: además de `symbols|cli`, un perfil **`propositions`** cuyos
hechos son `(R A B) @ctx` con `prov {path source_hash span extractor confidence}`. Un motor es
cualquier proceso que cumpla el contrato; kimun persiste, indexa y consulta.

| motor | papel | lenguaje | frontera |
|---|---|---|---|
| tree-sitter | parser de la capa **M** (bloques de Markdown y código). R2: pod de 3–4 MB, 19 ms de carga, 1,2 ms/doc, paridad de bloques 1099/1099 tras normalizar `\n` final; CST completo ≈ 15× la entrada, por eso se usa `blocks` y no se materializa el CST en bb | Rust (crate + gramáticas compiladas) | pod de Babashka; `KIMUN_POD`; empaquetado por plataforma en `lib/` |
| descompositor | texto → `(R A B)` propuestos (heurística o LLM) | cualquiera | protocolo `propositions`; siempre `proposed` |
| Matrix / wittgenstein | evaluación booleana por `W_i` (`V_i`, `S_i`, `I`), detección de conflicto, convergencia entre contextos | Python | motor externo por protocolo; **productor y evaluador, no almacén** (R1 §5, `12 §3`) |
| Datalevin | consulta Datalog sobre triples con procedencia | pod nativo | índice derivado `index/graph/`; respaldo in-process |
| embeddings | co-ocurrencia pseudo-estable sobre hojas | cualquiera | índice derivado `index/vectors/`, por `(hash de hoja, motor, versión)` |

`11 §1` ("Babashka no puede cargar tree-sitter") queda obsoleto: la decisión 19 admite pods y R2
lo demostró.

## 12. Decisiones

Se numeran a continuación de `05` y pasan a su tabla cuando el dueño las confirme.

| # | decisión | estado |
|---|---|---|
| 21 | la autoridad se parte por clase: bytes → archivo (+git), estructura → pool, hechos → pool + documentos de decisión; revisa `01 §4.1` | confirmada por P1, P5, P12 |
| 22 | todo hecho vive en un `W_i`; contexto por defecto = el documento `source`; ruteo solo por proyecciones promovidas | confirmada por P2 |
| 23 | `R`, `A`, `B` son posiciones en taxonomías con herencia (`isa?`); las propiedades algebraicas de `R` se declaran por contexto; el vocabulario `L_i` es un documento `source` EDN | confirmada por P3 |
| 24 | afirmación = `:status` en evidencia de arista `projection`; `V`/`S` = índice derivado por `(hecho, W_i)`; nada pasa a `TRUE` sin decisión registrada | confirmada (Q7) |
| 25 | conflicto lógico = propuesta fuera del pool; se resuelve por transacción (separar contextos, retractar, fuente equivocada); nunca en silencio | confirmada por P4 |
| 26 | `W_i` se evalúa como matrices booleanas por relación; índices derivados; nunca `p×p` global; motor = implementación (Matrix hoy, por protocolo) | confirmada por P9 |
| 27 | formato 3: sin copia verbatim; contenido por referencia con `inline` por caso (p. ej. páginas web que cambian); árbol como caché verificable; log como caché bajo `:git :ignore`; decisiones humanas materializadas como documentos por la composición grafo→documento, formato según el caso | confirmada (Q4, Q5, Q6) |
| 28 | modelo = símbolo-tipo + selector + proyecciones; el template de v1 es un caso especial del selector; la paridad v1 es su regresión | confirmada por P10 |
| 29 | tree-sitter como pod es el parser de la capa M, por fases (R2 §5); SLDB-MD sigue siendo el perfil cerrado por encima; `blocks.cljc` queda como oráculo hasta la paridad byte a byte | confirmada por P6, P12 |
| 30 | Datalog para la consulta de G; Datalevin (pod) como motor candidato con respaldo in-process; el índice G es derivado | confirmada por P12, pendiente el motor definitivo |
| 31 | lo producido por prompt es `proposed` por defecto; versión = modelo + hash del prompt + parámetros. Un store o contexto puede declarar políticas de promoción automática: el mismo mecanismo sirve de **harness** para LLMs | confirmada (Q8) |
| 32 | unidad de anclaje = hoja + grafemas NFC; los chunks de wittgenstein se mapean al entrar | confirmada (Q9) |
| 33 | unidad de escritura: una revisión por acción (build, assert, decisión) **más** un log de hechos append-only en flujo que se consolida en revisiones | confirmada (Q1 = b) |
| 34 | transversales de primera clase = el **pack mínimo para que kimun diga dónde está parado**: clase de datos (texto, código, otro), procedencia (source, tier), contexto `W_i`, y la autodescripción de kimun (su store y sus modelos). Kimun procesa texto **y** código, y otros datos si hace falta; el pack debe cubrir ambos. Lista derivada de Q2, corregible por el dueño | confirmada (Q2) |
| 35 | autoridad: kimun es una superficie para **estabilizar** conocimiento entre LLMs, datos y humanos; cualquiera propone (selector, algoritmo, LLM), **el humano tiene la autoridad máxima**; la UI para ejercerla se define después | confirmada (Q3) |
| 36 | el diccionario de signos cortos (P14) es **otro grafo**, operable como cualquier grafo y **diferenciable por contexto**, igual que los hechos; no hay regla global de códigos ni alcance por store: el alcance es el contexto | confirmada (Q10, Q11) |
| 37 | los hechos se persisten de la forma más simple (objetos en el CAS mientras no haya razón para otra cosa); un **caché de derivados** evita repetir cálculos recurrentes, con clave por inputs | confirmada (Q12) |
| 38 | embeddings (P15): **sin idioma fijo ni plantilla declarada**; se calculan sobre la forma canónica del hecho rendida con los signos de su contexto, con un modelo **multilingüe**. Que `dog`, `perro` y `hund` caigan en la misma vecindad es un **test exigido al modelo**, no algo que kimun fija | confirmada (Q13) |
| 39 | el lenguaje de selección se decide por spike en la etapa A (Q14); el índice por revisión (mmap) pasa a **derivado** también en stores `:git :track`, modelo git: revisión → árbol raíz → entradas (Q15) | confirmada (defaults) |

## 13. Etapas (P12)

**Etapa S — la capa S bien (ahora).** Sin tocar G.

1. Formato 3 del store: eliminar `source-node`; contenido por referencia; árbol como caché; una
   revisión por `build`. Medir el tamaño de este repo antes y después. `stores upgrade 2→3` =
   reconstruir.
2. Parser M por pod tree-sitter, fases 1–2 de R2: segmentación (normalizar `\n` final, recortar la
   línea en blanco del `list`), luego SLDB-MD como vista sobre el CST. `blocks.cljc` se queda como
   oráculo diferencial; corpus + desk como regresión (1099 documentos).
3. Empaquetado del pod por plataforma (`release.edn :pods`, `lib/`, `KIMUN_POD`).
4. Documentar: `10` (formato 3), `04` (hojas por referencia), `11 §1`, `08` (pods en el bundle).

**Etapa A — arquitectura sostenible.** Specs con gate de cero contexto:

- `15-capa-g.md`: triples con procedencia, contextos, verdad/sentido, conflicto lógico, protocolo
  `propositions`, índice Datalog, contrato de las dos etapas.
- `16-modelos-como-subgrafos.md`: selector (tras spike del lenguaje), herencia, template como
  caso especial, importación/exportación de modelos en otros lenguajes.
- Reescritura de `07` y de `12 §3`.

**Etapa C — completar.** Motores (Matrix por protocolo, descompositor, embeddings), evaluación en
dos etapas de punta a punta, descubrimiento de modelos, UIs y cliente Python, pendientes de `12`.

## 14. Promesas (a trasladar a `tests/promises.md` cuando se implementen)

- Un documento `source` sin cambios conserva intactos sus anchors y sus hechos curados tras un
  `rebuild` (hoja igual ⇒ mismo id).
- Cambiar una hoja deja `orphan` solo los hechos anclados a ella; el resto queda `intact`.
- `include build` desde cero, con los mismos archivos y documentos de decisión, produce el mismo
  root hash por documento y los mismos hechos afirmados.
- Un store formato 3 de este repo ocupa menos que el texto del desk que indexa.
- Dos hechos contradictorios en el mismo `W_i` producen un conflicto lógico; en `W_i` distintos
  no, hasta que se proyecte uno sobre otro.
- Ninguna transacción lleva un hecho a `TRUE` sin `:actor` o motor con `:status :confirmed`.
- La segmentación por pod coincide con `blocks.cljc` en el corpus y el desk (kinds y spans).

## 15. Qué revisa de los documentos vigentes

| documento | qué cambia |
|---|---|
| `01 §4.1` | "el pool es la fuente de verdad; ningún formato externo es autoridad" → autoridad por clase (§2) |
| `04` | hojas `:text` con contenido por referencia; el mapeo AST → pool no cambia |
| `07` | modelo = símbolo-tipo + selector + proyecciones; template como caso especial (§9) |
| `10` | formato 3 (§10) |
| `11 §1` | tree-sitter como pod en proceso; perfil `propositions` (§11) |
| `12 §3` | wittgenstein/Matrix = productor y evaluador por protocolo; kimun es la KB |
| `docs/archive/v2/09` | se rescata §4 (derivados por hash) como anchors de evaluación (§7) |

## 16. Adenda 2026-10-08 — tres principios más (pendientes de integrar en §4, §7, §10)

- **P13 — Un Merkle por transversal.** Además de los árboles estructurales (documentos: grandes,
  recomputables del archivo, caché con root hash), hay árboles **transversales** de dominio (tipo
  de documento, dominio, contexto `W_i`, …): de más alto nivel, más pequeños, y **persistidos**
  porque son conocimiento, no estructura derivable. Su root hash delimita el recálculo: "¿cambió
  algo en este dominio?" es comparar un hash, y P5 se ejecuta por transversal, no globalmente.
  Pendiente: qué transversales son de primera clase y quién afirma la pertenencia (selector o
  actor).
- **P14 — Hechos comprimidos en vocabulario interpretable.** Un hecho se puede guardar en una
  forma compacta no gramatical (s-expresión con signos cortos: emoji, ideogramas…) siempre que un
  humano o un LLM la entiendan y se **reconstruya determinísticamente** en el idioma original. El
  diccionario corto ↔ símbolo ↔ signo por idioma es un árbol más, creable on the run. Reglas: el
  hash del hecho se calcula sobre la forma canónica (ids), nunca sobre la comprimida (identidad ≠
  almacenamiento, como git con los packs); si los códigos se asignan por regla determinista sobre
  el conjunto de símbolos, el diccionario no se guarda y recomprimir equivale a reempaquetar.
- **P15 — Todo hecho se mapea a una o varias `(R A B)`.** El embedding persistido se calcula sobre
  una verbalización determinista de la forma canónica (plantilla fija por relación, idioma fijo),
  clave `(id del hecho, motor, versión)`: hechos iguales ⇒ vector igual. Hipótesis para la etapa
  C, no decisión: fijar hechos a vecindades del embedding como prefijo común en la compresión.

Implicaciones para la escritura del store (deducidas, a confirmar por el dueño): la unidad de
escritura es la acción (build, assert, decisión), no el documento; se persisten anchors, hechos
y decisiones, y nada más; los índices no entran al log y pueden ser binarios; la identidad se
calcula sobre la forma canónica y la compresión es asunto del almacenamiento.

## 17. Preguntas al dueño (2026-10-08) — respondidas

Respuestas: Q1 b · Q2 pack mínimo = autodescripción (decisión 34) · Q3 el humano decide (35) · Q4 sí, inline por caso · Q5 caché · Q6 sí, por composición grafo→documento, formato según el caso · Q7 sí · Q8 sí, con políticas de promoción (harness) · Q9 sí · Q10–Q11 diccionario = grafo por contexto (36) · Q12 lo más simple + caché de derivados (37) · Q13 sin idioma ni plantilla; modelo multilingüe (38) · Q14–Q15 defaults (39).

Tabla original, con los defaults que se asumían:

| # | pregunta | opciones | default |
|---|---|---|---|
| Q1 | unidad de escritura | (a) una revisión por acción (build/assert/decisión); (b) además un log de hechos en flujo consolidado en revisiones | (a) |
| Q2 | transversales de primera clase | lista cerrada {tipo de documento, dominio, contexto `W_i`, tier de fuente}; abierta por store | cerrada + abiertos por store |
| Q3 | quién afirma la pertenencia a un transversal | selector (derivada); actor (asentada); ambas con evidencia distinta | ambas; gana el actor; la del selector queda `proposed` |
| Q4 | contenido de hojas | por referencia (default) con `inline` opcional por store; inline siempre | por referencia |
| Q5 | árbol estructural del documento | caché verificable (root hash + posiciones referenciadas); persistido completo | caché |
| Q6 | log bajo `:git :ignore` y decisiones humanas | log = caché, decisiones como documentos `source` (EDN o Markdown); log persistido | caché; EDN |
| Q7 | afirmación y valor | `:status` en evidencia de arista + `V`/`S` índice derivado; valor en el nodo | evidencia + índice |
| Q8 | salida de prompt | siempre `proposed` con versión = modelo + hash del prompt + parámetros | sí |
| Q9 | unidad de anclaje | hoja + grafemas NFC, chunks mapeados al entrar; chunk | hoja |
| Q10 | diccionario corto (P14) | regla determinista sobre el conjunto de símbolos (sin guardar); diccionario content-addressed referenciado | regla determinista |
| Q11 | alcance del vocabulario corto | por store; compartido | por store |
| Q12 | dónde viven los hechos | objetos EDN en el CAS; registro compacto propio (packfile, forma P14) con ids sobre la forma canónica | registro compacto |
| Q13 | verbalización para embeddings (P15) | idioma fijo (¿cuál?) + plantilla por relación en el vocabulario `source` | español; plantilla en el vocabulario |
| Q14 | spike del lenguaje de selección | etapa S; etapa A | A |
| Q15 | índice por revisión (mmap) en stores `:git :track` | registro; derivado (modelo git) | derivado |

## 18. Clases, instancias e indexación (2026-10-08)

Principio del dueño; gobierna los modelos (revisa §9) y el registro del store (revisa §10).

1. **Todo documento es instancia de una clase.** No hay documentos sin clase.
   - **Documento genérico:** su clase es la estructura que da tree-sitter, cortada a una
     granularidad declarada (perfil de indexación por tipo de archivo). Ejemplo en código: clases,
     funciones y variables globales, no variables locales.
   - **Documento gobernado:** instancia de una clase común (un modelo); de una clase salen muchos
     documentos.
2. **Lo que no calza se resuelve por herencia**, no forzando el árbol: `documento.resumen.resumen_tecnico`
   hereda de `resumen` y agrega lo suyo. La jerarquía es un dato del store (Clojure `derive`/`isa?`),
   inmutable y content-addressed. Esta es una razón de fondo de la elección de Clojure.
3. **Una clase cambiada es otra clase.** Las clases son inmutables: la clase con que se clasificó un
   documento sigue existiendo, y el documento sigue siendo instancia suya hasta que se reclasifique
   explícitamente. No hay "texto viejo" que recuperar (cierra la pregunta de leer bajo el contrato
   original sin guardar copias ni consultar git).
4. **No todo el texto se indexa.** Lo que se infiere de *modelo + contexto* (fórmulas de cortesía,
   "se despide atentamente Juanito Pérez", azúcar estilístico) se queda en el archivo y **no entra
   al store**. Indexar es elegir qué de la estructura es conocimiento; no es meter el árbol entero en
   un zapato chino.
5. **Dónde se define cada cosa:**
   - la **granularidad del perfil** por tipo de archivo se define **por store**;
   - **qué es estructura y qué es contenido o azúcar** (cuándo un bloque extra amerita subclase, se
     indexa como contenido de un campo, o se ignora) se define **por documento**.
6. **Registro por documento en el store:** su anchor (ruta + fingerprint), la clase de la que es
   instancia y el hash raíz de lo indexado. El árbol concreto, los bloques y los valores de los
   campos son **caché** recomputable con `archivo + clase` y verificable contra el hash raíz.
7. **Dos modelos distintos: anclaje y generación.** El template de v1 hacía las dos cosas a la vez
   (extraer y renderizar); en kimun se separan.
   - **Modelo de anclaje (lectura):** selector sobre la estructura de un source; ancla lo que es
     conocimiento. Lo que no selecciona no entra al store.
   - **Modelo de generación (escritura)** (antes "composición"): cómo se produce un texto desde el
     grafo. Un documento puede tener un modelo de anclaje y varios de generación (carta formal,
     resumen, JSON…), y no tienen por qué coincidir. La herencia aplica a cada uno por separado.
   - El round-trip estricto de v1 (extraer y renderizar idéntico) deja de ser obligación: era
     consecuencia de usar un solo template para las dos direcciones.
8. **El azúcar se observa en el nodo, no se declara.** Dos documentos idénticos tienen los mismos
   nodos (content-addressing). Un nodo es azúcar:
   - **en anclaje**, porque no hay nada anclado a él;
   - **en generación**, porque es una fórmula sobre el AST desde el que se genera, o un string fijo.
   No hace falta marcar documentos ni subclases para "ignorar" un párrafo.
9. **Documentos source y documentos generados.** La autoridad depende de la dirección:
   - **source (anclado):** el archivo es la verdad; el store guarda su anchor y solo los nodos con algo
     anclado (§18.6, §18.8).
   - **generado:** la verdad son los **datos de la instancia** (valores de campos, relaciones) más su
     modelo de generación; el archivo es una proyección regenerable. El store guarda esos datos; el
     árbol del archivo es salida de la generación, no se registra.
   - El desk de este repo es **generado** (deskops escribe sus documentos): su store no baja de tamaño
     por anclaje selectivo, sino por dejar de registrar el árbol y las instrucciones de armado.

## 19. Caché, store estable y grafo (2026-10-09)

Principio del dueño. Kimun se separa en tres capas con reglas distintas, para no convertir el store
en un zapato chino.

| capa | contiene | regla |
|---|---|---|
| **caché** | árboles, bloques, campos extraídos, índices (Datalog, vectores, búsqueda) | derivado; se recalcula y se puede borrar sin perder nada |
| **store estable** (base de kimun) | hashes y anchors del ambiente (texto, código y, a futuro, datos) + el registro mínimo de qué se trackea | **no guarda el contenido**: detecta cualquier modificación del ambiente por hash y permite **reconciliar** cuando la KB queda desactualizada respecto del ambiente |
| **grafo** (paquete aparte, sobre la base) | modelos (anclaje y generación), clases y herencia, capa G (hechos, contextos) y todo lo que vive **solo** en el grafo | es el conocimiento; se ancla al ambiente a través de los hashes del store estable |

Reglas:
1. Hay cosas que viven **solo en el grafo** y cosas que **se leen del texto**; cada una se lee de su
   lugar. El hash dice si lo leído del texto sigue vigente.
2. Los hashes existen para **detectar cambios sin guardar el contenido**. Si el ambiente cambió, el
   store lo sabe y se reconcilia; no se guarda una copia "por si acaso".
3. El grafo es un **paquete aparte** de la base de kimun: la base (store estable + caché) no depende
   de modelos ni de G; el grafo depende de la base.

## 20. Trackeable, trackeado y el Merkle de reconciliación (2026-10-09)

Principio del dueño.

1. **Trackeable** es la colección de todos los anchors posibles (todos los archivos del ambiente y
   todos los nodos de su estructura). **Trackeado** es lo que alimenta el grafo de la KB: la
   proyección que indexa el modelo de anclaje.
2. **El Merkle del store estable es sobre lo trackeado.** Si cambia algo trackeado, el hash sube
   hasta la raíz y se puede bajar solo por la rama que cambió, para **reconciliar localmente** sin
   recalcular el resto.
3. **Del árbol de un documento solo se guarda (y se hashea) la proyección que indexa lo trackeado.**
   El resto es ruido: no se guarda ni se hashea. El fingerprint del archivo basta para saber que lo
   trackeable cambió; el hash de la proyección dice si cambió lo trackeado.

Diagnóstico del formato 4 (2026-10-09, este repo): el store (2,51 MB) se compone de (a) el **plan
del build** (907 KB), la receta de la transacción, que duplica los objetos que produce; (b) el
**índice mmap** (708 KB), un trie Merkle por revisión ordenado por **ids internos** (no por lo
trackeado), que no localiza un cambio en términos de documento/campo; (c) **tres aristas por
documento** (489 KB: `reference`→modelo, `derived`→modelo, `reference`→archivo) que dicen lo mismo
("D es instancia de la clase X, anclado al archivo F") con hashes repetidos. Propuesta en discusión:
árbol Merkle por ruta (como git), con una entrada por documento `{fingerprint, clase, hash de la
proyección}` y los campos trackeados como hojas; revisiones apuntando a la raíz; sin plan en el CAS,
sin trie por ids, sin las tres aristas.

## 21. El hash detecta mutaciones; no es identidad (2026-10-09) — PENDIENTE DE CONFIRMAR ALCANCE

Corrección del dueño. Kimun no es una base de datos content-addressed:

| cosa | qué es | para qué |
|---|---|---|
| ID del nodo | su identidad (nombre, ruta, símbolo) | referenciar, enlazar, encontrar |
| content | lo que el nodo dice | el conocimiento |
| hash | huella del content | **detectar mutaciones** y reconciliar |
| embedding | ubicación semántica | vecindad, co-ocurrencia |

Consecuencias señaladas por el dueño: el **plan del build** puede ir al caché o eliminarse (no aporta);
los **índices y enlaces por hash** (mmap ordenado por ids-hash; aristas con `:from`/`:to`/`:ref-hash`)
usan el hash para funciones que son de **código** (resolver qué documento usa qué modelo).

**Alcance (a confirmar):** esto revisa el principio 3 de `01 §4` ("la identidad de un nodo es su
contenido") y las decisiones 1, 3 y 4 de `05`, que son la base del kernel actual. Tensión abierta con
§18.8 ("dos documentos idénticos tienen los mismos nodos"): ¿dos párrafos idénticos son dos nodos con
el mismo hash (ID = ocurrencia), o un solo nodo con dos ocurrencias (y entonces de dónde sale el ID)?
No se toca código hasta resolverlo.
