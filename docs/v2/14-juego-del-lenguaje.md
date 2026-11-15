# kimun — El grafo como juego del lenguaje: principios, autoridad y store

> **Corrección del dueño, 2026-11-14 — dirección vigente para la frontera S–M.**
> Kimun cumple una función de sistema nervioso en un sistema autopoiético de conocimiento:
> sensoriza documentos y código, detecta mutaciones de topología y, eventualmente, acciones,
> para permitir regulación y otras acciones. Merkle es el mecanismo de sensorización.
> La identidad del nodo **no es el hash ni su anclaje estructural en la topología**.
> Debe permitir identificar las topologías marcadas y seguir sus cambios en el tiempo,
> con niveles de abstracción. Su definición y representación técnica siguen por elaborar;
> esto sustituye la propuesta de §21 «identidad = lugar» y no aprueba CAS, UUID ni otra codificación.
> Las verificaciones requeridas son: (1) lo marcado no cambió respecto de la huella registrada,
> afirmación que deja de sostenerse ante una mutación; (2) si se incorpora marcar ejecuciones,
> verificar la verdad del resultado. El contrato preciso de ejecuciones queda por especificar.
> El trabajo actual está entre **S (texto como signo) y M (desacople estructural/semántico)**;
> solo con esa base se aborda G, el conocimiento. Esta corrección no fija una lógica de verdad de G.
> Revisión solicitada al concilio: `runs/research/2026-11-14/concilio/`.

> **Precisión posterior del dueño, 2026-11-14 — unidad, relaciones y heartbeat.**
> La unidad es un **nodo con identidad propia**, un espacio mínimo donde convergen varias
> dimensiones. Su valor proviene de las relaciones que pueden declararse sobre ella:
> jerárquicas, horizontales, métricas y/o recursivas (herencia, temporales, secuenciales).
> El sistema está concebido para trabajar con LLMs: cada unidad tiene sentido relacional
> discreto, semántico y estructural, además de **embeddings continuos**.
> **Autopoiesis y VSM aplican al sistema, no a la unidad**; la unidad permite recursión.
> Queda retirada como definición general la propuesta del concilio que exigía a cada unidad
> ser una elaboración/pregunta con ciclo propio de revisión.
> El dueño propone un **daemon cuya única función es el heartbeat para recalcular señales**:
> un momento de autopercepción. El sistema es asíncrono: mutaciones, señales externas y
> respuestas a acciones provocadas internamente pueden activar respuestas.
> La representación de identidad, relaciones y embeddings, y los contratos operativos del
> heartbeat y de activación, siguen por especificar; esta dirección no implica implementación.

> **Aclaración del dueño, 2026-11-14 — dos etapas.** «Realidad y hecho son cosas
> distintas»: **(1) anchoring; (2) el hecho detectado**. El valor de verdad
> de una proposición está en su relación con la realidad. El dueño propone
> representar una proposición como `(R A B)`, donde `R` relaciona hechos `A`
> y `B`; esa recursión es el modelo de kimun, no una transcripción literal
> del *Tractatus*. Zettelkasten orienta la separación entre referencia a la
> fuente y ficha direccionable que participa en una red de vínculos. La huella
> comprueba vigencia del anchor; no fija por sí sola la verdad del hecho.
> La separación conceptual de D0 queda aclarada. Siguen por especificar la
> identidad positiva y continuidad de los nodos y el contrato entre etapas.
> Lectura cruzada: `runs/research/2026-11-14/concilio/brief-d0-dos-etapas.md`
> y los tres informes `d0-dos-etapas-{claude,agy,pi}.md`.

> **Actualización 2026-11-15 — D0 no es una pregunta pendiente al dueño.** Las tres notas de
> arriba ya fijan las premisas. El concilio derivó de ellas el contrato de identidad y
> continuidad para S–M, resumido en `§21` y completo en
> `runs/research/2026-11-15/concilio/d0-contrato-sintesis.md`. Sus reglas son inferencias
> del concilio, no texto del dueño. Donde este documento dice que algo «espera a `D0`» o a
> «`K1`/`D0`», léase: espera a `K1`, la implementación de ese contrato. La base de la recursión
> de `(R A B)` y la lógica de verdad siguen siendo de G.

> **Borrador estable y alcance de autoridad (2026-10-08; reordenado por tema el 2026-11-15, DOC1 / F18).**
> Fija lo que kimun es antes de seguir construyendo sobre la base S/M y ordena las adendas
> `§16–§21` separando **contrato objetivo**, **estado implementado**, **decisiones abiertas (`D0`, `D-G`)**
> y **registro histórico**.
> **Revisa** `01 §4.1` (autoridad única del pool), `07` (modelo = plantilla y ubicación de `:contract`),
> `11 §1` (tree-sitter fuera de proceso) y `12 §3` (wittgenstein como "la capa G"). Mientras esas
> revisiones no se integren en sus documentos, este gana (`docs/v2/README.md`). **No toca** las
> decisiones 1–20 de `05`: todas siguen vigentes salvo el alcance abierto por `D0`.
> Límite declarado por el dueño: motor AST-first para tratar texto, código y otros archivos como
> conocimiento; conocimiento = colección de proposiciones `(R A B)`; Clojure.
>
> Insumos: dossiers de diseño del store (`runs/research/2026-10-08/store-dossier-L{1,2,3}-report.md`),
> `docs/archive/v2/drafts/analisis-juego-del-lenguaje.md` (histórico), spikes R1 (Datalog en bb),
> R2 (tree-sitter como pod) y R3 (`16-modelos-como-subgrafos.md`),
> `wittgenstein/matrix-shrdlu-spec/specs/{SPEC,matrix,grounding,provenance}.md` y `docs/v2/tests/wittgenstein-manifest.md`.

> **Cómo leer este documento (reordenado por tema, DOC1 / F18).**
> Se conservan las secciones numeradas `## 1.`–`## 21.` para mantener válidas las citas del
> repositorio y de los tests, pero se agrupan en **seis temas** según su estatuto:
>
> | tema | secciones | estrato | qué contiene |
> |---|---|---|---|
> | **I. Principios y arquitectura de tres capas** | `§1`, `§16`, `§19`, `§2` | Contrato objetivo + Estado implementado (Formato 4) | Principios rectores `P1–P15` ya conciliados, integración de `P13–P15`, separación caché / store estable / grafo y autoridad por clase S / M / G |
> | **II. Documentos, clases, anclaje vs. generación y Merkle** | `§18`, `§9`, `§20`, `§7` | Mixto: `§18.6`/`§20` cortes `C2a`/`C2b`/`C4`/`C5` implementados (árbol Merkle por ruta en stores nuevos; coste dentro de un directorio ancho pendiente); `§18`/`§9` especificados en `16`; `§7` parte en `D-G` | Clases e instancias (`§18.1–§18.9`), modelos como subgrafos (remite a `16-modelos-como-subgrafos.md`), trackeable vs. trackeado y anchors |
> | **III. Identidad del nodo y frontera S–M (`D0`)** | `§21` | **Contrato S–M derivado por el concilio (`D0`, 2026-11-15)**; implementación pendiente (`K1`) | Distinción entre ID del nodo, contenido, huella Merkle y embedding; sustitución de «identidad = lugar»; unidad, anclaje, observación y continuidad |
> | **IV. El juego del lenguaje en la capa G (`D-G`)** | `§3`, `§4`, `§5`, `§6`, `§8`, `§11` | Contrato objetivo; `§5` y `proposed` **En revisión (`D-G`)** | Contextos `W_i`, recursión `(R A B)` y jerarquía, sentidos × verdad, conflicto lógico, evaluación en dos etapas y protocolo de motores |
> | **V. Estado implementado vs. contrato objetivo** | `§12`, `§13`, `§14`, `§15` | Matriz de estado, decisiones y referencias | Decisiones `21–46` con estrato/dueño/evidencia, etapas `S`/`A`/`C`, promesas resumidas y revisiones a otros docs |
> | **VI. Registro histórico** | `§10`, `§17` | **Registro histórico (2026-10-08)** | Diagnóstico del formato 2→3 y cuestionario `Q1–Q15` original con sus defaults superados anotados sin reescribir la historia |

---

# Tema I — Principios rectores y arquitectura de tres capas (`§1`, `§16`, `§19`, `§2`)

## 1. Principios rectores

Son del dueño; el resto del documento los despliega. Reúne los doce principios iniciales (`P1–P12`)
y los tres principios de la adenda 2026-10-08 (`P13–P15`), ya conciliados con las decisiones `34–38`.

- **P1 — Juego del lenguaje.** El grafo es una estructura simbólica evaluable sobre un mundo de
  conocimiento. Evalúa "su realidad" contra principios rectores que llegan por **instrucción** al
  grafo o por **ingesta de documentos marcados como `source`**. *(Estrato: Contrato objetivo.)*
- **P2 — Todo es contextual** (para el dueño, "el principio 1 de todo": rige sobre los demás). No
  hay hechos ni símbolos universales: toda KB está circunscrita a un contexto `W_i`. Los contextos
  son **ruteables** entre sí por proyecciones y equivalencias promovidas, nunca por coincidencia de
  cadenas. *(Estrato: Contrato objetivo.)*
- **P3 — `(R A B)` por conveniencia, con jerarquía.** Representamos todo como `(R A B)`, pero `R`,
  `A` y `B` son instancias de árboles jerárquicos (la recursión del dueño en `2026-11-14` modela
  proposiciones donde `R` relaciona hechos `A` y `B`, no como transcripción literal del *Tractatus*).
  Las propiedades algebraicas de `R` (transitiva, conmutativa, biyectiva…) **dependen del
  contexto**: `comparte` es transitiva en "el agua comparte liquidez con la bencina" y no lo es
  en "Juan comparte con Pedro". *(Estrato: Contrato objetivo.)*
- **P4 — Sin contradicciones dentro de un contexto.** La estructura no admite errores lógicos
  internos (categorías de sentido). Si dos fuentes chocan en un mismo contexto, queda **marcado
  como conflicto lógico** y el sistema pide desambiguación: contextos distintos, o una fuente
  equivocada. Nunca se resuelve en silencio. *(Estrato: Contrato objetivo.)*
- **P5 — Anchors con hash.** Los punteros del grafo sobre la base documental son *anchors*; cada
  uno lleva la huella del source evaluado. Si el source cambia, la huella deja de sostener vigencia
  y lo derivado o anclado entra en reconciliación. (Sobre su relación con la identidad, ver `§21` y `D0`.)
  *(Estrato: Parcialmente implementado en Formato 4 y cortes `C2a`/`C2b`/`C4`/`C5` de `K2a` para archivos y rutas (árbol Merkle por ruta en stores de esquema 2; los anteriores siguen en esquema 1 hasta `stores upgrade`); el coste dentro de un directorio ancho sigue pendiente y la verificación en G es Contrato objetivo.)*
- **P6 — Indexación estructural completa.** El documento se indexa estructuralmente
  (tree-sitter), delimitando qué de la estructura se trackea (`§18`, `§20`). Sobre ese anclaje
  emergen las clasificaciones y proyecciones semánticas. **El LLM no inventa: archiva.**
  *(Estrato: Mecanismo CST/bloques implementado en S1/S2; anclaje completo espera K1/D0 y M1.)*
- **P7 — Evaluar en dos etapas y justificar.** Una proposición se evalúa contra la KB recorriendo
  el grafo (consulta) y por álgebra booleana sobre matrices por contexto (`§8`). Toda respuesta puede
  trazarse hasta su justificación. *(Estrato: Contrato objetivo, Etapa C.)*
- **P8 — Anchors de evaluación.** Un anchor puede ser una referencia textual, o una evaluación
  "en código" o "en prompt" sobre el texto. El tipado de documentos es flexible: encuentra la
  sección que interesa en documentos de largos distintos; la estructura de interés es una
  **abstracción sobre el AST**, no el AST crudo. *(Estrato: Contrato objetivo; selectores especificados en `16-modelos-como-subgrafos.md`.)*
- **P9 — Matrices binarias por contexto.** Cada `W_i` de `(R A B)` se materializa para evaluar como
  matrices booleanas por relación (`A × B`), nunca `p×p` global: las operaciones booleanas
  (producto, cierre) salen casi instantáneas, y con GPU más. Sigue siendo un grafo sobre texto.
  *(Estrato: Contrato objetivo, Etapa C.)*
- **P10 — Modelo = tipo de subgrafo estable.** La noción de "modelo" de SLDB deja de ser una
  plantilla dura única para leer y escribir (`07`) y pasa a separarse en modelos de **anclaje** y
  de **generación** como clases con herencia y selectores estructuralmente estables sobre el AST
  (`§18`, desarrollado en `16-modelos-como-subgrafos.md`). Qué es estable lo proponen motores o
  clustering y lo promueve una decisión registrada. *(Estrato: Contrato objetivo especificado en M1; v1 template sigue implementado como caso especial hasta migrar.)*
- **P11 — No todo es igual de indexable.** Lo menos estructurado se indexa, pero cuesta más
  (motores). Los embeddings sobre anchors dan una ubicación continua / pseudo-estable de
  co-ocurrencia, útil a escala; **co-ocurrencia ≠ `(R A B)`**. *(Estrato: Contrato objetivo.)*
- **P12 — Tierra firme.** Primero la capa S funcionando bien; después una arquitectura sostenible;
  al final completar. Se aprovecha lo que existe y está probado (si el grafo resulta ser Datalog
  sobre Clojure, bienvenido). Clojure da inmutabilidad, herencia (`derive`/`isa?`) y homoiconicidad
  para instrucciones declarativas. *(Estrato: Principio rector operativo vigente.)*
- **P13 — Un Merkle por transversal.** Además de los árboles estructurales de cada documento
  (recomputables desde el archivo como caché), existen árboles **transversales** de dominio
  (autodescripción mínima —clase de datos, procedencia, contexto `W_i` y el propio store de kimun—
  más los declarables por store; decisión 34). Su root hash delimita el recálculo por dominio:
  "¿cambió algo aquí?" es comparar una raíz sin recalcular todo el store. *(Estrato: Contrato objetivo.)*
- **P14 — Hechos comprimidos en vocabulario interpretable.** Un hecho puede serializarse en forma
  compacta no gramatical siempre que un humano o un LLM la entiendan y se **reconstruya
  determinísticamente** en el idioma original. El diccionario corto es **otro grafo**, operable
  como cualquier grafo y **diferenciable por contexto** `W_i` (decisión 36). La huella canónica se
  calcula siempre sobre los ids canónicos, nunca sobre la forma comprimida (`identidad ≠ almacenamiento`).
  *(Estrato: Contrato objetivo.)*
- **P15 — Todo hecho se mapea a `(R A B)` y admite embedding multilingüe.** Todo hecho se proyecta
  a una o varias formas `(R A B)` y su embedding persistido se calcula sobre una verbalización
  determinista de la forma canónica rendida con los signos de su contexto, **sin idioma fijo ni
  plantilla declarada**, mediante un modelo **multilingüe** (decisión 38: que `dog`, `perro` y `hund`
  caigan en la misma vecindad es un test exigido al modelo, no algo fijado por kimun). Clave de
  caché del derivado: `(id del hecho, motor, versión)` (decisión 37). *(Estrato: Contrato objetivo;
  corrige la hipótesis inicial de plantilla fija anotada en `Q13` de `§17`.)*

## 16. Integración de P13–P15 e implicaciones de escritura (adenda 2026-10-08 integrada)

> **Estado de la adenda `§16`.** Sus tres principios (`P13`, `P14` y `P15`) quedan integrados arriba en
> `§1` y su desarrollo normativo en `§4` (P14 / decisión 36) y `§7` (P13 / decisión 34; P15 /
> decisión 38). Se conserva esta sección para no romper citas (`14 §16 P14`, `14 §16 P15`) y para
> fijar las implicaciones de escritura confirmadas con `Q1`, `Q12` y `§19–§20`.

- **Transversales de primera clase (`P13`, decisión 34):** pack mínimo de autodescripción de kimun
  para cubrir texto, código y otros datos: clase de datos, procedencia (`source`/tier), contexto
  `W_i` y el propio store/modelos de kimun. Quien afirma pertenencia puede ser un selector o un
  actor (el humano conserva autoridad máxima, decisión 35). *(Contrato objetivo.)*
- **Vocabulario compacto por contexto (`P14`, decisión 36):** regla local a cada contexto `W_i`, no
  global ni por store; los diccionarios viven como grafos. *(Contrato objetivo.)*
- **Embeddings multilingües (`P15`, decisión 38):** reemplaza explícitamente el borrador de `Q13`
  ("idioma fijo + plantilla por relación en el vocabulario"); no hay idioma fijo ni plantilla en el
  vocabulario. *(Contrato objetivo.)*
- **Implicaciones para la escritura del store (decisiones 33 y 37):**
  - la unidad de escritura es la **acción** (`build`, `assert`, `decisión`), más un log de hechos
    append-only en flujo que se consolida en revisiones;
  - el store estable persiste **hashes, anchors y decisiones humanas**, no árboles derivados ni
    índices;
  - los índices (`Datalog`, `vectors`, búsqueda) no entran al log y viven en cachés derivadas; la
    compresión de almacenamiento (`P14`) nunca altera las huellas sobre la forma canónica. *(Contrato objetivo.)*

## 19. Caché, store estable y grafo (2026-10-09)

Principio del dueño. Kimun se separa en **tres capas** con reglas distintas, para no convertir el
store en un zapato chino:

| capa | contiene | regla | estado actual en kimun |
|---|---|---|---|
| **caché** | árboles concretos, bloques, campos extraídos, índices (`Datalog`, vectores, búsqueda) | **derivado**: se recalcula desde fuente + contrato y se puede borrar sin perder conocimiento (ver cautela `K6` abajo) | **Implementado en parte**: `<store>/cache/` en Formato 4 (`10 §11`, `docs.cljc`, `store_cache.cljc`) guarda árboles por hash; índices Datalog/vectores son Etapa C |
| **store estable** (base de kimun) | hashes y anchors del ambiente (texto, código y, a futuro, datos) + el registro mínimo de qué se trackea | **no guarda el contenido**: detecta cualquier modificación del ambiente por huella y permite **reconciliar** | **Implementado en parte**: anchors/hashes por documento en Formato 4, cortes `C2a`/`C2b`/`C4`/`C5` de `K2a` (`3b2d6d5d`..`b4931b7c`: `kimun.kernel.route`, planes fuera del CAS, mantenimiento desde `surface.docs`, `route-coverage` en `stores check\|verify` y el mapa de rutas como árbol Merkle por directorio en los stores nuevos, esquema 2; los stores anteriores siguen en esquema 1, con `route-root` como digest plano `O(n)` de entradas ordenadas, hasta `stores upgrade`), nombres por ruta (`K8`: `bcd85a53`), clase genérica (`K3`: `076be35b`, `43d9db6d`) y `K6` en Markdown (`555b2dbc` digest de contrato + `rebuild-mismatch` puro en memoria); **sigue pendiente en `K2a` el coste dentro de un directorio ancho (sin fan-out) y su medición en un store real**, la extensión de `K6` a selectores de código (`M1`) y `K2b` (`K1`/`D0`) |
| **grafo** (paquete aparte, sobre la base) | modelos (anclaje y generación), clases y herencia, capa G (hechos, contextos) y todo lo que vive **solo** en el grafo | **es el conocimiento**; se ancla al ambiente a través de las huellas del store estable | **Parcialmente separado (primer corte `P1` en `70b8abe9`) / Contrato objetivo**: `kimun.graph.{model,io,query,traversal,analysis,store}` vive en su paquete con anillo en `bb lint` (cruce residual declarado `kimun.surface.semantic` → `kimun.graph.io`); mover `kimun.surface.models.*`, `semantic` y `edges` espera separar anclaje de generación (`M1` / `D0`) y la capa G completa espera `D0` y `D-G` |

Reglas normativas:

1. Hay cosas que viven **solo en el grafo** y cosas que **se leen del texto**; cada una se lee de su
   lugar. La huella comprueba si lo leído del texto sigue vigente.
2. Los hashes existen para **detectar mutaciones sin guardar el contenido**. Si el ambiente cambió, el
   store lo sabe y reconcilia; no se guarda una copia verbatim "por si acaso". *(El límite operativo del
   borrado del caché requiere además registrar el contrato de parse/selector que lo produjo — hallazgo
   `F08` / nodo `K6` — para distinguir cambio de fuente de cambio de parser o gramática.)*
3. El grafo es un **paquete aparte** de la base de kimun: la base (`store estable + caché`) no depende
   de modelos ni de G; el grafo depende de la base (decisión 43).

## 2. Capas y autoridad

La autoridad **se parte por clase**; ya no hay una sola fuente de verdad (revisa `01 §4.1`; decisión 21).
Leído junto con `§19` y la aclaración de **dos etapas (`2026-11-14`)**, distingue el soporte del signo
(capa **S**), la estructura indexable (capa **M**) y el hecho afirmado en contexto (capa **G**):

| capa | qué es | autoridad | dónde vive | reconstruible / verificación |
|---|---|---|---|---|
| **S** bytes | el texto tal cual | **el archivo**; git es su historia | fuera del store; el store guarda un anchor externo `{:locator :sample :fingerprint}` (`02 §2.1`) | no aplica: es la fuente |
| **M** estructura | árbol de posiciones y proyecciones estructurales (etapa 1: *anchoring*) | **el pool / caché verificable**, por determinismo bajo contrato de parse (`K6`) | `<store>/cache/` en Formato 4: se persiste el hash raíz y lo trackeado (`§20`); el resto se recomputa del archivo | sí, por parse determinista verificable contra la huella *(nota `D0`: en el kernel actual la identidad sigue siendo hash hasta resolver `D0` / `§21`)* |
| **G** hechos | `(R A B) @ W_i` (etapa 2: *hecho detectado*) con procedencia y sentido × verdad (`§5`) | **el paquete del grafo** + documentos `source` de decisiones humanas | grafo (nodos/aristas con evidencia) + índice booleano por contexto (derivado) | lo proyectado sí (motores); lo afirmado humanamente se reconstruye desde documentos de decisión |

Regla que lo resume: **canónico ≠ persistido.** Un parse determinista es canónico *y* reconstruible;
no hay que guardarlo completo en el CAS, hay que poder verificarlo. La huella comprueba vigencia
del anchor en la etapa 1 (**anchoring**); no fija por sí sola la verdad del hecho en la etapa 2 (`§5`, `§21`).

---

# Tema II — Documentos, clases, anclaje vs. generación y Merkle sobre lo trackeado (`§18`, `§9`, `§20`, `§7`)

## 18. Clases, instancias e indexación (2026-10-08)

Principio del dueño (decisiones 41–42); gobierna los modelos (actualiza `§9`) y el registro del store
(actualiza `§10`). Detalle de diseño en `docs/v2/16-modelos-como-subgrafos.md` (**Spec 16 / `M1`**,
redactado en Etapa A; implementación dependiente de `P1` y `K1`):

1. **Todo documento es instancia de una clase.** No hay documentos sin clase.
   - **Documento genérico:** su clase es la estructura que da tree-sitter, cortada a una granularidad
     declarada (perfil de indexación por tipo de archivo). Ejemplo en código: clases, funciones y
     variables globales, no variables locales. *(Estado implementado: `K3` cerrado en `076be35b` y
     `43d9db6d` (`runs/subagents/2026-11-14-K3/`, `2026-11-14-K3-untrack/`) registra documentos sin
     modelo bajo `generic/md` con `untrack|delete` y resolución conjunta con tipados; perfiles finos
     por tipo de archivo y código especificados en Spec 16 / `M1`.)*
   - **Documento gobernado:** instancia de una clase común (un modelo); de una clase salen muchos
     documentos. *(Estado implementado: `models` como descriptores EDN en `07-modelos-como-nodos.md`; modelos de subgrafo en Spec 16.)*
2. **Lo que no calza se resuelve por herencia**, no forzando el árbol: `documento.resumen.resumen_tecnico`
   hereda de `resumen` y agrega lo suyo. La jerarquía es un dato del store (Clojure
   `derive`/`isa?`), inmutable y content-addressed. *(Estado: diseñado en Spec 16 `M1`, con fixtures en `test/fixtures/models/spec16_examples.edn`; aún no activo en runtime sobre modelos legacy de `07`.)*
3. **Una clase cambiada es otra clase.** Las clases son inmutables: la clase con que se clasificó un
   documento sigue existiendo, y el documento sigue siendo instancia suya hasta que se reclasifique
   explícitamente. No hay "texto viejo" que recuperar: el documento se lee bajo el contrato con el que
   se escribió. *(Estado implementado, `A1` / decisión 40: en Formato 4 el contrato `:contract` vive en la
   **evidencia de la arista `reference` (`root → external`)**, no en la arista `derived` de `07`; la sucesión del modelo no lo altera. Spec 16 proyecta esta misma inmutabilidad al descriptor de anclaje/generación.)*
4. **No todo el texto se indexa.** Lo que se infiere de *modelo + contexto* (fórmulas de cortesía,
   "se despide atentamente Juanito Pérez", azúcar estilístico) se queda en el archivo y **no entra al
   store**. Indexar es elegir qué de la estructura es conocimiento; no es meter el árbol entero en un
   zapato chino. *(Contrato objetivo; desarrollado en Spec 16.)*
5. **Dónde se define cada cosa:**
   - la **granularidad del perfil** por tipo de archivo se define **por store**;
   - **qué es estructura y qué es contenido o azúcar** (cuándo un bloque extra amerita subclase, se
     indexa como contenido de un campo, o se ignora) se define **por documento**. *(Contrato objetivo.)*
6. **Registro por documento en el store:** su anchor (ruta + fingerprint), la clase de la que es
   instancia y el hash raíz de lo indexado. El árbol concreto, los bloques y los valores de los campos
   son **caché** recomputable con `archivo + clase` y verificable contra el hash raíz. *(Estado
   implementado: Formato 4 en `store.cljc`, `store_cache.cljc`, `docs.cljc`, `10 §11` — el árbol concreto vive en `<store>/cache/` y no se exige en el CAS.)*
7. **Dos modelos distintos: anclaje y generación.** El template de v1 hacía las dos cosas a la vez
   (extraer y renderizar); en kimun se separan (`16-modelos-como-subgrafos.md`).
   - **Modelo de anclaje (lectura):** selector sobre la estructura de un source; ancla lo que es
     conocimiento. Lo que no selecciona no entra al store.
   - **Modelo de generación (escritura)** (antes "composición"): cómo se produce un texto desde el
     grafo. Un documento puede tener un modelo de anclaje y varios de generación (carta formal,
     resumen, JSON…), y no tienen por qué coincidir. La herencia aplica a cada uno por separado.
   - El round-trip estricto de v1 (extraer y renderizar idéntico) deja de ser obligación: era
     consecuencia de usar un solo template para las dos direcciones. *(Contrato objetivo en Spec 16; los modelos EDN vigentes de `07` y adaptadores `11` conservan compatibilidad legacy hasta `M2`.)*
8. **El azúcar se observa en el nodo, no se declara.** Un nodo es azúcar:
   - **en anclaje**, porque no hay nada anclado a él;
   - **en generación**, porque es una fórmula sobre el AST desde el que se genera, o un string fijo.
   No hace falta marcar documentos ni subclases para "ignorar" un párrafo. *(Nota abierta con `D0`:
   la frase inicial «dos documentos idénticos tienen los mismos nodos» reflejaba CAS puro por contenido;
   con la corrección de `§21`/`2026-11-14`, la igualdad de contenido detecta identidad/reutilización
   de hash sin zanjar por sí sola la identidad del nodo ni si dos ocurrencias son un nodo o dos.)*
9. **Documentos `source` y documentos `generado`s.** La autoridad depende de la dirección:
   - **source (anclado):** el archivo es la verdad; el store guarda su anchor y solo los nodos con algo
     anclado (`§18.6`, `§18.8`).
   - **generado:** la verdad son los **datos de la instancia** (valores de campos, relaciones) más su
     modelo de generación; el archivo es una proyección regenerable. El store guarda esos datos; el
     árbol del archivo es salida de la generación, no se registra. *(Corrección por hallazgo `F10` y retiro de modelos del desk el `2026-10-09`: no existe evidencia de instancia independiente fuera de los `.md` en el desk actual, y los modelos `desk/models/*.edn` fueron retirados dejando `kimun.include.edn` vacío; cuando `M2` regenere los modelos desde `16`, los documentos del desk se tratarán como `source` salvo donde exista una instancia durable independiente en el grafo.)*

## 9. Modelos como tipos de subgrafo (P10; integrado con §18 y Spec 16)

> **Estado:** lo formulado originalmente aquí fue precisado por `§18` (clases inmutables, herencia,
> anclaje ≠ generación) y detallado en el spec de diseño objetivo **`docs/v2/16-modelos-como-subgrafos.md` (`M1`)**.
> Conviene distinguir tres estratos:

- **Contrato objetivo (`M1`, `16-modelos-como-subgrafos.md`, depende de `P1` y `D0`/`K1` para implementarse):**
  - Un modelo se separa en **modelo de anclaje** y **modelo de generación**. Cada modelo es una clase
    inmutable en el árbol `models` (herencia con Clojure `make-hierarchy` / `derive` / `isa?`, `§18.2–3`).
  - **Lenguaje de selección (`R3`, supera la duda original sobre Datalog):** el spike `R3` fijó
    **selectores EDN puros evaluados sobre el substrato tree-sitter / AST** (sin esperar a Datalog
    para anclaje de modelos). Los selectores producen hechos de clasificación/anclaje sobre
    subgrafos estables; qué subgrafos emergen por descubrimiento (clustering / CHIC / SIA) y cómo se
    promueven pertenece a la Etapa C.
  - Las marcas legacy (`⸢rev•campo⸥`, `render`, traits) quedan como azúcar de generación y la
    "receta por bloque" de `04` deja de ser deuda estructural al pasar a selector EDN.
- **Estado implementado hoy (`07-modelos-como-nodos.md`):**
  - Descriptores EDN opacos con template de bloques (`markdown-it` / `kimun.surface.models.blocks`) y
    composición `07`.
  - **Regresión de v1 (`tests/parity-v1.md`):** el template estricto de v1 es el caso especial del
    sistema actual; marcador vigente tras `C1/F25` y `C1` chequeo fuente: **`520/1/14/55/0/0/0/0`**
    (`520` portadas, `1` mapeada, `14` oráculo, `55` n/a, `0` brechas, `0` corpus, `0` abiertas).
- **Retirado / pendiente:** el descubrimiento automático (`CHIC`/`SIA`) requiere la capa G y vive en
  Etapa C; la regeneración del desk (`M2`) espera la implementación de `M1`.

## 20. Trackeable, trackeado y el Merkle de reconciliación (2026-10-09)

Principio del dueño (decisión 44). Separa qué existe en el ambiente de qué entra al cálculo del
store estable:

1. **Trackeable** es la colección de todos los anchors posibles (todos los archivos del ambiente y
   todos los nodos de su estructura). **Trackeado** es lo que alimenta el grafo de la KB: la
   proyección que indexa el modelo de anclaje.
2. **El Merkle del store estable es sobre lo trackeado.** Si cambia algo trackeado, la huella sube
   hasta la raíz y se puede bajar solo por la rama que cambió, para **reconciliar localmente** sin
   recalcular el resto.
3. **Del árbol de un documento solo se registra (y se hashea como proyección) lo trackeado.** El resto
   no se guarda ni entra al hash de lo trackeado: el fingerprint del archivo basta para saber que lo
   **trackeable** cambió; la huella de la proyección dice si cambió lo **trackeado**. *(Esto fija la
   frontera S→M de sensorización Merkle; no resuelve la identidad positiva de nodo de `§21` / `D0`.)*

Separación de estratos sobre rutas e indexación de lo trackeado:

- **Implementado en cortes (`C2a`, `C2b`, `C4`, `C5` de `K2a`, `3b2d6d5d`..`b4931b7c`):** planes resueltos fuera del CAS
  (`f837069d`), mapa durable de entradas por ruta (`kimun.kernel.route`, `790007e2`), mantenimiento desde
  `surface.docs` (`C2b`, `ff9d44c3`, `b776ae42`), informe de cobertura (`route-coverage` en
  `stores check` y `stores verify`, `C4`, `2c56e680`) y el árbol Merkle por ruta (`C5`, `b4931b7c`,
  `kimun.kernel.route-tree`). El mapa de rutas tiene **dos esquemas** (`10 §11.2`):
  - **Esquema 1 (legado):** un objeto plano; `route-root` es un digest plano `O(n)` de entradas ordenadas
    y `open` valida el mapa completo. Así sigue todo store creado antes de `C5`, **sin cambios**, hasta que se
    pide `stores upgrade` o `include build --rebuild`; la raíz cambia al cambiar de esquema.
  - **Esquema 2 (stores nuevos):** un nodo por directorio. El cambio sube a la raíz por los nodos de su
    camino y se baja solo por la rama que cambió (punto 2 de arriba): una escritura reescribe esos nodos,
    `open` valida solo el nodo raíz y el diff baja por donde los hashes difieren.
- **Límites de `C5` (pendientes en `K2a`, sin depender de `D0`):** no hay fan-out **dentro** de un
  directorio: cada nodo del camino se lee, se rehashea y se reescribe entero, de modo que un directorio
  ancho cuesta en proporción a sus hijos y un store con todo en un directorio paga como el esquema 1; el
  árbol acota la cantidad de nodos tocados, no hace el coste independiente del número de rutas. Enumerar
  las rutas y la cobertura (`route-coverage`) leen el árbol entero. No está medido en un store real. Las
  hojas son entradas por documento: los **campos** trackeados como hojas dependen de `K1`/`D0`
  (`runs/subagents/2026-11-15-C5/EVIDENCE.md`).
- **Pendiente (`K2b`, bloqueado por `K1`/`D0`):** retirar el índice `mmap` ordenado por hashes internos
  y sustituir/retirar las tres aristas redundantes por documento cuando los IDs de nodo estén cerrados.
- **Diagnóstico histórico (2026-10-09, previo a `K2a`):** en el primer corte del formato 4 sobre
  el desk de este repo (433 KB, 539 docs), el store medía `2,51 MB`: `907 KB` correspondían al plan del
  build en el CAS, `708 KB` al índice `mmap` por ids internos y `489 KB` a tres aristas repetidas por
  documento (`reference`→modelo, `derived`→modelo, `reference`→archivo).

## 7. Anchors (P5, P8, P13, P15)

| tipo | forma | invalidación / estado |
|---|---|---|
| **externo** | `:sign/:external {:locator :sample :fingerprint}`; el fingerprint es `<alg>:<hex>` de los bytes (decisión 11) | fingerprint ≠ hash actual ⇒ el documento está desactualizado (**implementado** en `02 §2.1`/`§6.4`; `§21`: detecta mutación, no define identidad) |
| **estructural** | posición en un árbol de documento, u hoja + `[start end]` en grafemas NFC (decisión 32; `02 §4.1`) | por sucesión: hoja igual ⇒ `intact`; hoja cambiada ⇒ `orphan` ⇒ reconciliación ⇒ `drifted` ⇒ actor (decisiones 9–10). **Abierto `D0` (`§21`):** hoy la igualdad sigue por hash hasta `K1`, y los chunks en code points (`wittgenstein`) se mapean a grafemas NFC en la frontera (`S2`) |
| **de evaluación** | derivado `:opaque {:format "edn/value"}` con aristas `derived` hacia expresión e inputs (`docs/archive/v2/09 §4`); motores `sci`, `shell:CMD` (opt-in), `llm:` | `anchor/states` sobre aristas `derived` (**No implementado**; archivado en `docs/archive/v2/09-evaluador-anclado.md`) |

Reglas vigentes y frontera de revisión:

- **Recalcular tiene granularidad de hoja/nodo anclado.** Solo lo que cuelga de hojas cambiadas pierde
  vigencia de anclaje; un hecho sobre una hoja intacta conserva anclaje válido mientras no cambie su
  propio anchor ni el contrato (`§18.3`). Un anclaje perdido pasa a reconciliación (`orphan` → `drifted`),
  **nunca se descarta ni se sobreescribe en silencio**. *(Nota `D0`: en la arquitectura objetivo la
  continuidad del ID no depende del hash; el hash comprueba vigencia — etapa 1 anchoring.)*
- **Transversales (`P13`, decisión 34):** cada árbol transversal (empezando por el pack mínimo de
  autodescripción) mantiene su propio Merkle para acotar el recálculo por dominio. *(Contrato objetivo.)*
- **Embeddings sobre anchors (`P15`, decisión 38):** se calculan sobre una verbalización determinista
  de la forma canónica rendida con los signos de su contexto (`§4`, `P14`), con modelo **multilingüe**,
  **sin idioma fijo ni plantilla declarada**; clave de caché `(id del hecho, motor, versión)`
  (decisión 37). Además, los nodos contemplan embeddings continuos junto a sus relaciones discretas
  (`2026-11-14`). *(Contrato objetivo, Etapa C.)*
- **Unidad de anclaje (`Q9`, decisión 32):** hoja + grafemas NFC; las referencias de `wittgenstein` en
  code points se traducen al entrar. *(Contrato objetivo, `S2`.)*
- **Salida de prompts LLM (`decisión 31` — EN REVISIÓN bajo `D-G`):** el borrador del `2026-10-08`
  marcaba todo resultado de prompt como `:status :proposed` con versión `modelo + hash del prompt +
  parámetros` y políticas de promoción automática ("harness"). **Está en revisión** (`docs/v2/drafts/capa-g-dudas.md`):
  bajo la dirección del dueño no existe la categoría «propuesto» como eje de verdad, y los signos de
  una salida de LLM siguen requiriendo contraste y decisión humana cuando aspiran a afirmación
  verificada (`el LLM no decide verdad`).

---

# Tema III — Identidad del nodo, frontera S–M y contrato `D0` (`§21`)

## 21. El hash detecta mutaciones; no es identidad (2026-10-09; actualizado el 2026-11-14 y el 2026-11-15) — D0: contrato S–M derivado

> **Estado: contrato S–M derivado por el concilio (`D0`, decisión 46, 2026-11-15); no hay
> pregunta pendiente al dueño.** Esta sección integra la corrección inicial del `2026-10-09`,
> las tres precisiones del dueño del `2026-11-14` (`cabecera`) y el contrato que el concilio
> derivó de ellas. **Sigue sin aprobar CAS, UUID ni otra codificación como identidad**: el
> formato del id es una decisión técnica del equipo, dentro de las propiedades que fija el
> contrato. Contrato completo, casos e inferencias:
> `runs/research/2026-11-15/concilio/d0-contrato-sintesis.md`. Fuentes:
> `docs/v2/drafts/fuentes-identidad.md` y `runs/research/2026-11-14/concilio/`.

Distinción base confirmada (separa cuatro funciones que el diseño content-addressed puro mezclaba):

| cosa | qué es | para qué |
|---|---|---|
| **ID del nodo** | identidad propia del nodo, acuñada en el acto de marcarlo (contrato `D0`, abajo) | identificar las topologías marcadas y seguir su continuidad en el tiempo con niveles de abstracción; soportar vínculos |
| **content** | lo que el nodo dice / expresa | el conocimiento |
| **hash (Merkle)** | huella criptográfica del content o proyección | **sensorizar el ambiente**: comprobar si lo marcado cambió respecto de la huella registrada (`§20`) |
| **embedding** | ubicación semántica continua en un espacio multilingüe | vecindad, co-ocurrencia y búsqueda aproximada junto a relaciones discretas (`§1 P11, P15`) |

Qué quedó **zanjado por el dueño (`2026-10-09` y `2026-11-14`)**:

1. **La identidad del nodo no es el hash ni su anclaje estructural en la topología.** La dirección del
   `2026-11-14` **sustituye** expresamente la propuesta anterior de `§21` («identidad = lugar»). El
   hash Merkle es únicamente el mecanismo de **sensorización**: comprueba vigencia del anchor y deja
   de sostener la afirmación cuando ocurre una mutación, sin constituir la identidad del nodo.
2. **Dos etapas conceptualmente separadas (`2026-11-14`):** «Realidad y hecho son cosas distintas»:
   **(1) anchoring** y **(2) el hecho detectado**. La huella comprueba la vigencia del anchor; no fija
   por sí sola la verdad del hecho ni la continuidad del nodo. Zettelkasten orienta la separación entre
   referencia a la fuente y ficha direccionable que participa en una red de vínculos.
3. **La unidad es un nodo con identidad propia, no una unidad autopoiética:** en la unidad convergen
   varias dimensiones (sentido relacional discreto —jerárquico, horizontal, métrico y/o recursivo— y
   **embeddings continuos**). **Autopoiesis y VSM aplican al sistema nervioso completo, no a cada
   unidad** (queda retirada la propuesta del concilio que exigía a cada unidad ser una
   elaboración/pregunta con ciclo propio); la unidad permite recursión.
4. **Regulación y daemon de heartbeat (dirección `2026-11-14`, no implementada):** el dueño propone un
   daemon asíncrono cuya única función es el **heartbeat para recalcular señales** (mutaciones, señales
   externas o respuestas a acciones internas). Si se incorpora marcar ejecuciones, debe verificarse la
   verdad del resultado (contrato aún por especificar).
5. **Transparencia ante ruptura de anchor estructural (`2026-10-09`):** un anchor estructural que se
   rompe es **un borrado o una mutación**. Si es borrado, no se conserva falsamente un anclaje que ya
   no apunta a nada (el conocimiento que dependía de él puede perdurar en otra capa o revisión, pero se
   **declara** con transparencia que su soporte estructural se rompió). Si es mutación, el hash la
   detecta. Un símbolo nuevo nace en el contexto de su documento; ascenderlo de contexto o declararlo
   `equivalent` es curaduría explícita.
6. **Consecuencia para el store actual (`§20`):** el **plan del build** en el CAS e índices por
   ids-hash (`mmap` por hash) cumplen funciones técnicas de código/caché, no de identidad ni de
   registro de conocimiento; pueden eliminarse o moverse a caché (`K2a` inició el camino por ruta; `K2b`
   espera `D0`/`K1`).

**Contrato de identidad y continuidad en S–M (`D0`, derivado por el concilio el `2026-11-15`).**
Las reglas son **inferencias del concilio** a partir de lo zanjado arriba; ninguna es texto del
dueño. Resumen (reglas `I1–I13`, casos y contraste de lecturas en
`runs/research/2026-11-15/concilio/d0-contrato-sintesis.md`):

| | qué es | ¿identidad de nodo? |
|---|---|---|
| **realidad** | el documento, la sección, la función | no entra al grafo |
| **unidad** | el **hecho detectado**: que se marcó una topología y se la sigue | sí, propia; nace al marcarla y no cambia |
| **anclaje** | la referencia vigente de la unidad a la realidad: localizador + contrato de lectura | no; la unidad tiene anclajes sucesivos |
| **observación** | la afirmación fechada de lo leído (huella, proyección); deja de sostenerse ante una mutación | no en S–M: es un evento registrado sobre la unidad |

1. **Una observación nueva no es una unidad nueva.** Editar, reformatear o releer dan
   observaciones de la misma unidad. Cada lectura es una observación con su fecha, aunque lea
   lo mismo: «no cambió» y «no se leyó» son distintos.
2. **Mientras el anclaje resuelve, un cambio es una mutación de esa unidad.**
3. **La ausencia no termina una unidad.** Cuando el anclaje deja de resolver, el sensor informa
   `missing` y la unidad sigue, sin anclaje vigente; un montaje que desaparece un instante no
   la mata. Cerrarla (el borrado, que «se declara») o re-anclarla en otro lugar son
   declaraciones, con actor y evidencia, que nombran lo que pasó en medio.
4. **Una unidad no se bifurca ni se funde.** Copiar, dividir y fusionar crean unidades nuevas,
   unidas a las anteriores por linaje declarado. Dos marcas son dos unidades aunque lo leído
   sea idéntico (resuelve la tensión de `§18.8`), y lo mismo en dos fuentes son dos unidades;
   unirlas entre contextos es una **proyección declarada**, no `equivalent` por defecto.
5. **El sensor no distingue editar de reemplazar** (poner otra cosa sin relación bajo el mismo
   localizador). Por defecto es una mutación; el reemplazo se declara. Vale en cada nivel: lo
   reemplazado es la extensión marcada (archivo, sección, campo).
6. **El sensor propone; el cierre, la continuidad y la proyección entre contextos se declaran.**
7. **El id** es único en el store que lo acuña, nunca reutilizado, opaco al contenido, a la ruta
   y a la coordenada de una relación, y estable de por vida. Direccionar **valores** por hash
   sigue siendo válido; lo que no puede es servir de identidad de una unidad.

Qué queda **pendiente tras `D0`**, y nada de ello es una pregunta al dueño sobre identidad:

- **Implementación (`K1`)**: llevar el contrato al kernel debajo del nivel documento, donde hoy
  bloques y texto se identifican por contenido; formato del id. **Actualización 2026-11-15
  (K1a):** el arrastre de identidad a nivel **documento** al reconstruir un store está hecho
  (`include build --rebuild` reutiliza id de documento e id de árbol por ruta cualificada y
  declara en `:identities` lo que no puede seguir). **K1b:** el re-anclaje y el cierre a nivel
  documento son declaraciones (`docs reanchor NAME FILE --reason`, `--reason` en
  `docs untrack|delete`; la declaración queda en el journal) y kimun no mueve archivos ni detecta
  movimientos. **K1c:** cada campo de primer nivel que el modelo de un documento tipado
  selecciona es una unidad con id propio (ULID acuñado la primera vez que el campo se registra,
  persistido en el descriptor del árbol como `:units {campo id}`: es store, no caché) y estado
  `anchored|missing`; la ausencia no la termina (sigue con su id) y renombrar un campo es una
  unidad `:missing` más otra nueva, sin linaje declarable todavía. *(Las decisiones de K1c
  —qué es una unidad, su anclaje, su identidad, la continuidad, la ausencia y el renombrado— son
  **inferencias del concilio/equipo**, no palabras del dueño.)* Quedan fuera: documentos
  genéricos, campos anidados e ítems de lista, documentos `:inline` o de formato < 4, ids de
  modelos, cierre y linaje de unidades de campo, y G. Pendientes reales de `K1`: las unidades de
  **sección y bloque** (esperan la serie `S2a` → `K1e` → `S2b`), los campos anidados e ítems de
  lista, el cierre y el linaje de unidades de campo y copiar/dividir/fusionar con linaje
  declarado; los ids de modelos los acuña de nuevo el rebuild. `K2b`, esa serie y la
  implementación de `M1` esperan a `K1`.
- **Alcance sobre `01 §4` principio 3 y decisiones `1`, `3`, `4` de `05`**: se revisa al
  implementar `K1`, en lo que toca a unidades.
- **Diseño de G, no de S–M**: la base de la recursión de `(R A B)` (un store finito no puede
  cumplir a la vez «todo hecho es un trío», «sus términos son siempre hechos» y «sin ciclos»),
  si en G una observación pasa a ser un nodo (en S–M no lo es), y la lógica de verdad y sentido (`D-G`). Registrado sin regla
  adoptada en el anexo A de la síntesis.
- **Contratos operativos aparte**: marcado de ejecuciones y activación del heartbeat, que el
  dueño dejó por especificar.

---

# Tema IV — El juego del lenguaje en la capa G (`§3`, `§4`, `§5`, `§6`, `§8`, `§11`)

> **Alcance temporal de todo este tema:** la capa G (`G1–G3`, `15-capa-g.md` pendiente de reescritura)
> es **Contrato objetivo bloqueado** por `D0`, `D-G`, `M1` y `T01` (ya cerrada). Sus capacidades fueron
> inventariadas y acotadas en `docs/v2/tests/wittgenstein-manifest.md` (`C01–C28`, hoy `0` implementadas).
> El trabajo activo de kimun antes de `D0` está en la frontera **S–M**.

## 3. Contextos (P2)

- Un contexto `W_i` es un nodo `:fact/:context` y un ámbito-índice sobre el grafo (`atom-w-i-context-node-and-index`).
  Todo hecho nombra su `W_i`; no existe hecho ni símbolo sin contexto (`P2`). *(Contrato objetivo; el
  kernel actual admite aristas globales sin obligar contexto aún, hallazgo `F02`/`F22`.)*
- **Contexto por defecto:** cada documento `source` abre su propio `W_i`. Contextos más finos
  (sección, párrafo) o más gruesos (un corpus) se declaran por instrucción. *(Contrato objetivo.)*
- **Ruteo entre contextos y licencia de inferencia (`K7`, `F22`):** una consulta cruza mundos solo
  por proyecciones y equivalencias **promovidas** (`equivalent` o `projection`, nunca por igualdad de
  cadenas de texto ni por mero alcance de búsqueda sobre stores enlazados o tags globales).
  *(Contrato objetivo; `K7` separa licencia de inferencia del alcance de búsqueda `find`.)*
- **Consecuencia de P4:** la contradicción lógica se evalúa dentro de un `W_i`; al proyectar o unir
  contextos aparece conflicto entre fuentes y se pide desambiguación (`§6`), nunca fusión silenciosa.

## 4. `(R A B)` con jerarquía (P3, P14)

- Una proposición se modela como `(R A B)`, donde `R` relaciona hechos `A` y `B`, admitiendo
  **recursión** como modelo propio de kimun (`2026-11-14`, no transcripción literal del *Tractatus*).
- `R`, `A` y `B` son términos (`:symbol/:term`) situados en **árboles de taxonomía** (`make-hierarchy`
  / `derive` / `isa?`), serializables en EDN y heredables por contexto. *(Contrato objetivo; la semántica actual
  de tags en `surface.semantic` es un subconjunto plano en S/M, `12 §3`.)*
- Las **propiedades algebraicas de `R` se declaran por contexto** como hechos del juego:
  `(property comparte transitive) @ W_i`. El evaluador aplica cierre transitivo o simétrico solo
  donde esa propiedad vale en `W_i`. *(Contrato objetivo.)*
- El **vocabulario admisible** (`L_i`) y el diccionario compacto por contexto (`P14`, decisión 36) se
  declaran como datos EDN en el mismo grafo/documentos `source` (homoiconicidad). *(Contrato objetivo.)*
- `:symbol/:proposition {:form …}` admite formas anidadas y conectivos lógicos (`if`, `and`, `or`, `not`,
  `iff`, `xor`; `F03`, manifiesto `wittgenstein`). *(Contrato objetivo.)*

## 5. Verdad y sentido — EN REVISIÓN (`D-G`)

Hoy el triple del kernel es `{:subject :predicate :object :context}` sin ejes separados de sentido y
verdad (`12 §3`). Esta sección distingue explícitamente la **dirección del dueño** del **borrador de
octubre puesto en revisión**:

- **Dirección vigente del dueño (`D-G`, `docs/v2/drafts/capa-g-dudas.md` y `2026-11-14`):**
  - El valor de verdad de una proposición está en su **relación con la realidad**: se separa el eje de
    **sentido** (estructural/categorial: `sinnvoll` / `sinnlos` / `unsinnig` según el juego del
    lenguaje) del eje de **verdad / soporte** respecto de las **dos etapas (`2026-11-14`)**:
    **(1) anchoring** (comprobado por huella/anchor sobre el source) y **(2) el hecho detectado**
    (su relación con la realidad y su verificación cuando involucra ejecuciones o inferencias).
  - La huella Merkle comprueba vigencia del anchor en la etapa 1; **no fija por sí sola la verdad del
    hecho** en la etapa 2.
  - **No existe la categoría ontológica «propuesto»** en el modelo de verdad del dueño.
- **Formulación del 2026-10-08 actualmente EN REVISIÓN (`D-G` — decisiones 24 y 31, NO implementar como contrato cerrado):**
  - El borrador inicial modelaba la afirmación en la evidencia de arista `projection` con
    `:status ∈ #{observed confirmed rejected retracted}` (tiers `observed > confirmed > inferred >
    statistical`, donde `statistical` nunca deduce) y derivaba `V ∈ #{TRUE FALSE UNKNOWN}` y
    `S ∈ #{sinnvoll sinnlos unsinnig}` por `(hecho, W_i)`, exigiendo transacción con actor para
    pasar de `UNKNOWN` a `TRUE`.
  - Su compatibilidad con el eje **sentido × verdad** (`anclado` / `derivado` / `operacional` y
    retractación con mantenimiento de pruebas AND/OR, hallazgos `F04` y `F17`) permanece abierta en
    **`D-G`** (`preguntas-abiertas.md`, `capa-g-dudas.md`, insumo de `G1`).
- **Principio firme compartido (en revisión `D-G` sin ambigüedad):** ni métricas estadísticas ni
  salidas de LLM deciden verdad automáticamente, y ninguna afirmación pasa a verdad verificada en
  silencio sin trazabilidad (`el silencio del corpus no es absurdo`, `el LLM no inventa: archiva`).

## 6. Conflicto lógico (P4)

Tercer tipo de conflicto, junto al estructural (`ConflictSet`, `02 §5.2`, **implementado**) y al de
anclaje (`drifted`, decisiones 9–10, **implementado**). *(El conflicto lógico completo es Contrato
objetivo de la capa G, Etapa C):*

- Lo produce el evaluador de sentido al detectar incompatibilidad dentro de un `W_i` o al proyectar
  contextos (`{:context :facts [...] :kind :contradiction|:ambiguous :candidates [...]}`).
- Vive fuera del pool afirmado hasta que un actor lo resuelve.
- Resoluciones admitidas (siempre mediante transacción registrada con trazabilidad): **(a)** separar en
  contextos `W_i` distintos; **(b)** retractar o invalidar con historia y mantenimiento de dependientes
  (`F17`: distinguir pruebas conjuntivas vs. alternativas); **(c)** marcar una fuente como equivocada
  en ese contexto mediante un hecho sobre su anchor. Nunca se sobrescribe en silencio.
- Un conflicto sin resolver no corrompe ni bloquea el resto de la KB: marca el `W_i` afectado y las
  consultas que lo tocan devuelven estado ambiguo con candidatos, nunca eligen arbitrariamente.

## 8. Evaluación en dos etapas (P7, P9)

> **Contrato objetivo (Etapa C, posterior a `G1`).** Ninguna parte del evaluador `G` ni del evaluador
> anclado (`docs/archive/v2/09`) está implementada hoy.

```
pregunta (EDN)
  → resolver símbolos        cascada de `09 §2`: :resolved | :ambiguous (exit 2) | :missing (exit 1)
  → recorrer el grafo        Datalog sobre triples con procedencia (índice derivado; R1)
  → evaluar en el W_i        álgebra booleana: V_i, S_i, I; cierre según propiedades de R (§4)
  → respuesta                {:answer :truth :sense :justification [aristas con evidencia] :citations [spans]}
```

- **Representación vinculante (P9):** cada `W_i` se materializa para evaluar como matrices booleanas por
  relación (`A × B`) más las tablas de verdad/sentido/indeterminación derivadas por contexto. Son
  **índices derivados**, nunca nodos del pool, y **nunca crecen `p×p` sobre el corpus entero**. Qué
  motor las ejecuta (Matrix en Python por protocolo o núcleo Clojure/GPU) es detalle de
  implementación separable por protocolo (`§11`).
- **Consulta Datalog (spike `R1`):** bajo Babashka, el pod de **Datalevin** cargó 47 349 triples en
  2–3 s, consultas de dos saltos en 35–205 ms y cierre recursivo en 15 ms (1 M triples sintéticos en
  30–60 s de carga y cierre de 1,47 M filas en 15–37 s), con respaldo semi-naive in-process como
  opción offline. El índice `<store>/index/graph/` es estrictamente **derivado y reconstruible**.
- Nada de lo que produce una consulta se persiste salvo afirmación explícita por transacción.

## 11. Motores y protocolo

El protocolo 1 de adaptadores (`11-adaptadores.md`, **implementado** para perfiles `symbols` y `cli`
y aliases `python-*`/`selfdoc-*` incluido chequeo de actualidad de nodos fuente `C1`) se generaliza
en el contrato objetivo a perfil **`propositions`** (`(R A B) @ ctx` con procedencia `{path
source_hash span extractor confidence}`). Un motor es cualquier proceso que cumpla el contrato; kimun
coordina la sensorización, persistencia, indexación y consulta:

| motor | papel | lenguaje / frontera | estado actual |
|---|---|---|---|
| **tree-sitter** | parser de la capa **M** (bloques Markdown y símbolos de código) | Rust (`pods/treesitter`) como pod de Babashka (`KIMUN_POD`); `R2`: pod 3–4 MB, 19 ms carga, 1,2 ms/doc, paridad de bloques `1099/1099` (`S1`/`S2`) | **Implementado** para bloques Markdown (`S1` CST libre ~3 % pendiente) y adaptador Python `treesitter_symbols.py`; supera `11 §1` |
| **descompositor** | texto → `(R A B)` detectados (heurística o LLM) | proceso externo bajo protocolo `propositions` (sin auto-confirmación ni categoría `proposed` como verdad) | **Contrato objetivo** (Etapa C) |
| **Matrix / wittgenstein** | evaluación booleana por `W_i`, detección de conflicto y convergencia entre contextos | Python externo por protocolo; **productor y evaluador, no almacén** (`R1 §5`, `12 §3`) | **Contrato objetivo** (`G1` / Etapa C; manifiesto `T01` fijado en `28` capacidades) |
| **Datalevin** | consulta Datalog sobre triples con procedencia | pod nativo (`index/graph/` derivado) + respaldo in-process | **Spike `R1` medido**; integración es **Etapa C** (`G2`) |
| **embeddings** | ubicación semántica continua multilingüe sobre nodos/anchors (`P15`, `2026-11-14`) | motor externo pluggable; caché derivada `(id, motor, versión)` | **Contrato objetivo** (`G3`, Etapa C) |

---

# Tema V — Estado implementado vs. contrato objetivo (`§12`, `§13`, `§14`, `§15`)

## 12. Decisiones

Reúne las decisiones `21–46` (numeradas a continuación de `05 §3`), señalando explícitamente su
**estrato actual**, el **documento dueño** y la **evidencia/tests asociados** (`F18`).

| # | decisión | estrato / estado | dónde vive | evidencia / tests |
|---|---|---|---|---|
| 21 | Autoridad partida por clase: bytes → archivo (+git), estructura → caché/pool verificable, hechos → grafo + docs de decisión (revisa `01 §4.1`) | **Parcialmente implementado** (S/M en Formato 4; G objetivo) | `14 §2`, `01 §4`, `05 §3` | `docs_test`, `durability_test` |
| 22 | Todo hecho vive en un `W_i`; contexto por defecto = documento `source`; ruteo solo por proyecciones promovidas | **Contrato objetivo** (bloqueado por `G1`/`D0`) | `14 §3`, `05 §3` | `manifest.edn` `C01–C02` |
| 23 | `R`, `A`, `B` en taxonomías con herencia (`isa?`); propiedades algebraicas de `R` por contexto; vocabulario `L_i` en EDN (`source`) | **Contrato objetivo** (herencia usada en docs/tags; reglas G esperan `G1`) | `14 §4`, `05 §3` | `semantic_test`, `manifest.edn` `C06–C08` |
| 24 | Afirmación en arista `projection` + `V`/`S` por `(hecho, W_i)` + promoción explícita a `TRUE` | **EN REVISIÓN (`D-G`)** | `14 §5`, `drafts/capa-g-dudas.md` | — (bloqueado hasta cierre `D-G`) |
| 25 | Conflicto lógico vive fuera del pool; se resuelve por transacción (separar contextos, retractar, fuente equivocada); nunca en silencio | **Contrato objetivo** (`G1`, Etapa C) | `14 §6`, `05 §3` | `manifest.edn` `C11` |
| 26 | Evaluación booleana por matrices `W_i`, índices derivados, nunca `p×p` global | **Contrato objetivo** (`G1`, Etapa C) | `14 §8`, `05 §3` | — |
| 27 | Formato 4 del store: registro por documento + caché de árbol/bloques en `<store>/cache/`; `:content :inline` opcional; log bajo `:git :ignore` | **Implementado** (`S1c`, Formato 4; `K6` en Markdown implementado; `K2b` pendiente de `D0`) | `10-escala.md §11`, `14 §19`, `05 §3` | `revision_format2_test`, `durability_test`, `store_size.clj` |
| 28 | Modelo = símbolo-tipo + selector + proyecciones; template v1 como caso especial (regresión `parity-v1`) | **Especificado (`M1`) / Parcial** (v1 implementado; selector EDN en diseño objetivo) | `16-modelos-como-subgrafos.md`, `07-modelos-como-nodos.md`, `14 §9` | `spec16_models_contract_test`, `adapters_parity_test` |
| 29 | Tree-sitter como pod es el parser de la capa M, por fases; SLDB-MD perfil cerrado; `blocks.cljc` como oráculo | **Implementado** (fase 1 `blocks` `1099/1099`; fase 2 `cst_ast` en `S1`) | `04-superficie-markdown.md`, `14 §11` | `blocks_parity_test`, `segments_parity_test` |
| 30 | Datalog para consulta de G; Datalevin (pod) candidato con respaldo in-process; índice G derivado | **Spike `R1` cerrado / Contrato objetivo (`G2`)** | `14 §8`, `12 §1` | `runs/research/2026-10-08/R1-report.md` |
| 31 | Salida de prompt como `proposed` + políticas de promoción (harness) | **EN REVISIÓN (`D-G`)** (no existe categoría `"propuesto"`) | `14 §7`, `drafts/capa-g-dudas.md` | — |
| 32 | Unidad de anclaje = hoja + grafemas NFC; chunks en code points mapeados al entrar | **Parcialmente implementado** (hojas/grafemas en M; cruce completo es `S2` tras `K1`) | `02-sustrato-computacional.md §4.1`, `14 §7` | `anchor_test`, `cst_ast_test` |
| 33 | Unidad de escritura = revisión por acción + log de hechos append-only consolidado en revisiones | **Parcialmente implementado** (revisión por acción en Formato 4; stream de hechos G es objetivo) | `14 §16`, `02 §5`, `10 §11` | `revision_test`, `store_test` |
| 34 | Transversales de primera clase = pack mínimo de autodescripción (clase, procedencia, `W_i`, kimun) | **Contrato objetivo** | `14 §1 P13`, `14 §16` | — |
| 35 | Cualquiera propone (selector, motor, LLM); el humano tiene la autoridad máxima | **Principio vigente / Contrato objetivo** (UI pendiente) | `14 §16`, `01 §4` | — |
| 36 | Diccionario de signos cortos = otro grafo, diferenciable por contexto `W_i` | **Contrato objetivo** | `14 §1 P14`, `14 §4` | — |
| 37 | Hechos almacenados en la forma más simple (CAS) + caché de derivados con clave por inputs | **Contrato objetivo** (caché de árbol ya existe; derivados G en Etapa C) | `14 §12`, `05 §3` | — |
| 38 | Embeddings multilingües sin idioma fijo ni plantilla declarada sobre forma canónica (`dog/perro/hund`) | **Contrato objetivo** (`G3`; corrige `Q13`) | `14 §1 P15`, `14 §7` | — |
| 39 | Lenguaje de selección por spike en etapa A + índice por revisión derivado también en `:git :track` | **Mixto**: spike `R3` cerró selector EDN (`M1`); en el índice por ruta están implementados los cortes `C2a`/`C2b`/`C4`/`C5` de `K2a` (árbol Merkle por directorio en stores nuevos; pendiente el coste dentro de un directorio ancho) | `16-modelos-como-subgrafos.md`, `14 §20`, `10 §11` | `spec16_models_contract_test`, `route_store_test`, `routes_test` |
| 40 | Todo documento se lee bajo el contrato con el que se escribió (`:contract` en evidencia de arista `reference` root→external) | **Implementado (`A1`, `2026-10-08`)** | `06-superficies-y-cli.md`, `07-modelos-como-nodos.md`, `14 §18.3` | `closed_gaps_test`, `models_parity_test` |
| 41 | Todo documento es instancia de una clase; herencia con `derive`/`isa?`; clase cambiada = otra clase | **Parcialmente implementado** (`K3` clase genérica cerrado en `076be35b`, `43d9db6d`; Spec 16 `M1` redactado, runtime M1 espera `P1`/`D0`) | `14 §18`, `16-modelos-como-subgrafos.md`, `10 §11` | `spec16_models_contract_test`, `typed_docs_test` |
| 42 | Anclaje y generación son modelos distintos; azúcar en nodo; `source` vs `generado` | **Especificado (`M1`)** (desk actual tratado como `source` tras retiro de modelos legacy, `F10`) | `14 §18.7–9`, `16-modelos-como-subgrafos.md` | `spec16_models_contract_test`, `manifest.edn` |
| 43 | Tres capas: caché, store estable (hashes/anchors, nunca contenido) y grafo (`kimun.graph.*` paquete aparte) | **Parcialmente implementado**: `Formato 4` en `main`; primer corte de `P1` implementado (`70b8abe9`: `kimun.graph.*` con anillo en `bb lint`; modelos/`semantic`/`edges` pendientes de `M1`/`D0`); `K2a` con cortes `C2a`/`C2b`/`C4` implementados y estructura Merkle incremental/coste pendiente | `14 §19`, `12 §1`, `03 §1` | `durability_test`, `lint_test` |
| 44 | Trackeable vs. trackeado; Merkle del store estable por ruta sobre lo trackeado; plan fuera del CAS | **Parcial (`K2a`)**: cortes `C2a`, `C2b`, `C4` y `C5` implementados (`3b2d6d5d`..`b4931b7c`: plan fuera del CAS, mapa durable de rutas y, en stores nuevos, árbol Merkle incremental por directorio con descenso local — esquema 2; los stores anteriores siguen en esquema 1, `route-root` como digest plano `O(n)` de entradas ordenadas, hasta `stores upgrade`); **pendiente el coste dentro de un directorio ancho (sin fan-out) y medir en un store real**; retirada de `mmap`/aristas redundantes es `K2b` (`D0`/`K1`) | `14 §20`, `10 §11`, `10 §11.2`, `06-superficies-y-cli.md` | `route_store_test`, `route_tree_test`, `route_tree_store_test`, `route_schema_test`, `routes_test`, `c4_route_rebuild_test` |
| 45 | Formato 4 implementado (`S1c`): registro por documento y árbol como caché por hash en `<store>/cache/` | **Implementado (`S1c`)** | `10-escala.md §11`, `05 §1` | `durability_test`, `diagnostics_test` |
| 46 | El hash detecta mutaciones; no es identidad (ni lugar es identidad positiva; dirección `2026-11-14`) | **Contrato S–M derivado por el concilio (`D0`, 2026-11-15)**; implementación pendiente | `14 §21`, `runs/research/2026-11-15/concilio/d0-contrato-sintesis.md`, `drafts/fuentes-identidad.md` | **Sin código todavía**: lo implementa `K1`; `K2b`, `S2` y `M1` esperan a `K1`; `G1` además a `D-G` |

## 13. Etapas (P12)

Estado real de ejecución por etapa (el backlog vivo y dependencias residen en
`docs/v2/12-ecosistema-y-pendientes.md §1`):

- **Etapa S — Base de octubre completada + cortes S/M cerrados y pendientes:**
  - **Base S de octubre completada (`2026-10-08/09`, `runs/research/2026-10-08/S{1,1b,1c,2}-report.md`):**
    Formato 3 → Formato 4 en disco (`S1`, `S1b`, `S1c`, `10 §11`) y parser M fase 1 por pod
    `tree-sitter` empaquetado (`etapa S.2` de octubre: `blocks` `1099/1099` en corpus + desk).
  - **Cortes S/M con implementación cerrada en `main` (`2026-11-14`):**
    - **`K3` (cerrada):** documentos sin modelo registrados bajo la clase genérica `generic/md` con
      `docs untrack|delete` y resolución conjunta con documentos tipados (`076be35b`, `43d9db6d`;
      evidencias en `runs/subagents/2026-11-14-K3/` y `2026-11-14-K3-untrack/`).
    - **`K8` (cerrada):** la ruta relativa es el nombre cualificado y el nombre corto es un alias
      que falla con `docs/ambiguous` ante colisión (`bcd85a53`, integrado con rutas en `ff9d44c3`;
      evidencia en `runs/subagents/2026-11-14-K8/`).
    - **`C1` (cerrada):** guarda `before-image` en `adapters sync` (`cdce1cb9`) y chequeo de
      actualidad de nodos fuente `source-graph-check` en `adapters check` (`3902731d`; marcador
      `parity-v1` en `520/1/14/55/0/0/0/0`; evidencias en `runs/subagents/2026-11-14-c1-*`).
    - **Sensorización (`stores sense` y `stores heartbeat`):** observación read-only de señales
      (`c07c0e0c`, `runs/subagents/2026-11-14-sense/`) y daemon NDJSON de sensorización (`86a0ba42`,
      `08e2bbb4`, `f0619f7a`, `runs/subagents/2026-11-14-heartbeat*`).
  - **Cortes S/M con avance parcial (siguen abiertos sin esperar a `D0`):**
    - **`K2a` (parcial — cortes `C2a`, `C2b`, `C4` y `C5` implementados; quedan límites de coste):**
      planes resueltos fuera del CAS (`f837069d`), primitiva y mapa durable de rutas (`3b2d6d5d`,
      `ecb30774`, `790007e2`), mantenimiento desde `surface.docs` (`C2b`: `ff9d44c3`, `b776ae42`),
      cobertura de rutas en `stores check|verify` (`C4`: `2c56e680`; evidencias en
      `runs/subagents/2026-11-14-k2a-*` y `2026-11-14-C2b/`) y **árbol Merkle incremental por ruta
      (`C5`: `b4931b7c`, `runs/subagents/2026-11-15-C5/`)**: los stores nuevos son de esquema 2 (un nodo
      por directorio, descenso local); los anteriores siguen en esquema 1 (`route-root` como
      digest plano `O(n)` de entradas ordenadas) hasta `stores upgrade` o `include build --rebuild`
      (`10 §11.2`).
      **Sigue pendiente:** el coste dentro de un directorio ancho (no hay fan-out: un directorio plano
      cuesta en proporción a sus hijos) y medirlo en un store real; las hojas por campo son de `K1`/`D0`.
    - **`K6` (implementada para Markdown; selectores de código con `M1`):** el digest y comparación
      del contrato de parse están implementados en `555b2dbc` (`10-escala.md §11.1`, evidencia
      `runs/subagents/2026-11-14-K6/`), y el corte de `rebuild-mismatch` añade el recálculo puro
      en memoria (`rebuild-tree`/`rebuilt-root`, sin escribir en `<store>/cache/`): `stores
      check|health|sense|heartbeat` distinguen fuente mutada (`source/*`), contrato cambiado
      (`contract/changed`) y reconstrucción divergente bajo la misma fuente y contrato
      (`contract/rebuild-mismatch`), reparable tras decisión con `docs update` (evidencia en
      `runs/subagents/2026-11-15-k6-rebuild-mismatch/`); resta únicamente la extensión a documentos
      de código al implementar `M1`.
    - **`S1` fase 2 del parser M (`cst_ast.cljc`, `KIMUN_PARSER=treesitter`, cerrada 2026-11-15):**
      base reconciliada en `65ca4b5f`, `03a9f552`, `6930afa8` (evidencia `runs/subagents/2026-11-14-s1-cst-ast/`),
      paridad `735/735` documentos reales en corpus + desk y **0 divergencias de AST en 500 y
      3000 documentos libres muestreados** (`runs/subagents/2026-11-15-S1/`): el ~3 % era la medición
      histórica de `66ff1e9c`, con la vista pod-driven. Lo que resta es cobertura pod-driven (619
      bloques de 500 documentos que la vista no adopta) y pasa a la fase 3 (`S2`, `04 §11.2`).
  - **Fase 3 del parser M (espera a `K1`):**
    - **`S2` fase 3 del parser M (`12 §1` — anclas por rangos de bytes / nodos del CST):**
      **espera a `K1`**, la implementación del contrato `D0` (`runs/research/2026-10-08/R2-report.md §5`; distinta de la
      `etapa S.2` de octubre que empaquetó el pod de bloques). Para que no haya ciclo con `K1` (que
      necesita un localizador estable para dar identidad a secciones y bloques), se parte en
      `S2a` (anclas CST como evidencia, **sin identidad**; no espera a nada) → `K1e` (unidades de
      sección y bloque) → `S2b` (anclas que referencian ids de unidad): lo que espera a `K1` es
      `S2b` (`12 §1`; inferencia del equipo, 2026-11-15).
- **Etapa A — Arquitectura sostenible:**
  - `M1` redactado y contratado (`ac09b764`, `docs/v2/16-modelos-como-subgrafos.md`; implementación
    espera `P1` y `K1`/`D0`);
  - `T01` cerrado (`9e106029`, `docs/v2/tests/wittgenstein-manifest.md`, `C01–C28`);
  - `P1` primer corte integrado (`70b8abe9` + fix `971b6b2c`, `runs/subagents/2026-11-15-P1/`):
    `kimun.graph.*` con anillo en `bb lint` y cruce declarado `kimun.surface.semantic` →
    `kimun.graph.io` (mover `models.*`, `semantic` y `edges` espera `M1` y `D0`);
  - `DOC1` cerrado (este reordenamiento por tema, `F18`; contrato en `test/kimun/doc14_contract_test.cljc`,
    evidencia en `runs/subagents/2026-11-15-doc1-spec14-por-tema/`);
  - `K1` (implementación del contrato `D0`, ya derivado; no espera al dueño). Esperan a `K1`: `K2b`, resto de `P1`, `S2` (fase 3); `15-capa-g.md` (`G1`) espera además a `D-G`.
- **Etapa C — Completar el sistema nervioso y la capa G:** motores (`Matrix`/wittgenstein por
  protocolo, descompositor, embeddings `G3`), Datalog `G2`, evaluación en dos etapas de punta a
  punta, descubrimiento de modelos, `M2` (regeneración de modelos del desk), `C2` (cliente Python y
  HTTP `graph_ui`) y heartbeat asíncrono completo (`2026-11-14`, por especificar).

## 14. Promesas (resumen; la traza viva está en `tests/promises.md`)

La tabla promesa → test, con la evidencia implementada (incluidos formatos 3 y 4 y paridad v1), vive en
`tests/promises.md` y `tests/parity-v1.md`. Resumen normativo y límites medidos:

- Un documento `source` sin cambios conserva intactos sus anchors tras un `rebuild` (`hoja igual ⇒ mismo id` en el esquema actual; sujeto a revisión al resolverse `D0`).
- Cambiar una hoja marca `orphan` solo lo anclado a ella; el resto conserva anclaje (`intact`).
- `include build` / `include build --rebuild` sobre fuentes idénticas estabiliza el registro trackeado y su `route-root` (`C4`, `c4_route_rebuild_test`).
- **Límite heredado del formato 4 inicial (en reducción con `K2a`–`K2b`):** en la medición de `2026-10-09`, el store del desk (`2,51 MB`) pesaba más que el texto indexado (`433 KB`) por retener el plan del build en el CAS, `mmap` por ids internos y tres aristas por documento (`§20`); además, en disco real de `4 KB`/bloque `K4` (packfiles) sigue pendiente (`tests/parity-v1.md §5`).
- **Contratos aún NO implementados (capa G):** conflicto lógico al unir contextos (`§6`), promoción controlada de verdad (`§5`, pendiente `D-G`) y evaluación booleana en dos etapas (`§8`).
- **Segmentación Markdown:** `tree-sitter pod` coincide `1099/1099` con `blocks.cljc` en corpus + desk (`blocks_parity_test`, `segments_parity_test`).

## 15. Qué revisa de los documentos vigentes

| documento | qué cambia o precisa respecto a su texto actual | estado de integración |
|---|---|---|
| `01 §4.1` (y principio 3 en `D0`) | "el pool es la fuente de verdad" → autoridad partida por clase (`§2`, decisión 21). El principio 3 («identidad = contenido») queda **pendiente bajo `D0`** (`§21`, decisión 46) | `§2` vigente por tabla de autoridad; `§21` abierto en `D0` |
| `04` | hojas `:text` con contenido por referencia (`Formato 4`); el mapeo AST → árbol de caché sigue determinista | Implementado en Formato 4 (`10 §11`) |
| `07` | modelo = símbolo-tipo + selector EDN + generación (`§18`, Spec 16); `:contract` en Formato 4 se registra en la evidencia de la arista **`reference` (`root → external`)**, no en `derived` (decisión 40 / `A1`) | `A1` implementado en Formato 4; selector EDN en Spec 16 (`M1`, pendiente de implementación) |
| `10` | formato 4 (`10 §11`) es el formato actual; `stores upgrade` migra **en sitio** (`2→3`) o reconstruye (`1→2`, `3→4`) según `10-escala.md`, superando la nota histórica de `§10` | Implementado (`10-escala.md`, `05-estado.md`) |
| `11 §1` | tree-sitter como pod en proceso (`etapa S.2`, decisión 29); perfil `propositions` para Etapa C (`§11`) | Pod implementado; chequeo `C1` implementado; `propositions` es objetivo |
| `12 §3` | wittgenstein/Matrix = productor y evaluador externo por protocolo, no almacén; inventario en `tests/wittgenstein-manifest.md` (`T01`) | `T01` cerrado; `12 §3` pendiente de reescritura junto a `G1` |
| `docs/archive/v2/09` | rescatado como referencia de anchors de evaluación (`derived`, `§7`), aún no implementado | Archivado (`no implementado`) |

---

# Tema VI — Registro histórico (`§10`, `§17`)

> Se conservan aquí las secciones que documentan decisiones y diagnósticos fechados en el punto de
> partida (`2026-10-08`), aclarando explícitamente qué superó el contrato implementado posterior para
> no reescribir la historia ni confundirla con especificaciones vigentes (`F18`).

## 10. El store (lo que cambia en disco) — sección histórica

> **Sección histórica del formato 3 (`2026-10-08`).** Sustituida por el registro por clase/documento de
> `§18`–`§20` y por el **contrato implementado del formato 4** en **`docs/v2/10-escala.md §10–§11`**.
> Se conserva como registro del salto formato 2 → 3 y para mantener citas de código/tests (`:content :inline`, `markdown/source`).
>
> **Divergencia histórica ya resuelta (`F18`):** la frase inicial de abajo decía que «`stores upgrade 2→3`
> reconstruye desde los archivos (`include build`)». En el **contrato implementado** (`10-escala.md §10–§11`,
> `05-estado.md §1`), `stores upgrade` migra **`1→2`** reconstruyendo, **`2→3` en el sitio** sin
> reimportar ni perder historia, y **`3→4`** pide `include build --rebuild`.

| hoy (formato 2, histórico) | formato 3 (histórico 2026-10-08) |
|---|---|
| copia verbatim del texto (signo `markdown/source`; referencia histórica) | **eliminada**; el texto vive una sola vez en el archivo externo |
| hojas `:text` con su texto dentro del objeto | **contenido por referencia**: el objeto guarda la huella; bytes leídos del archivo por anchor externo, con `:content :inline` opcional por store (`store.edn`) para fuentes que no se releen barato |
| árbol de posiciones completo en el CAS por revisión | **caché verificable**: persiste hash raíz y lo referenciado; hoy `<store>/cache/` en Formato 4 (`10 §11`) |
| log + snapshot mmap por cada transacción de tracking | log como **caché** cuando el store es `:git :ignore`; una revisión por `build`; decisiones humanas como documentos `source` |
| sin índice G | `index/graph/` (Datalog) e `index/vectors/` (embeddings): derivados, gitignored, por cabecera |

Qué **no** cambió al pasar de formato 2 a 3 y 4: árboles de posiciones, aristas con evidencia,
`TransactionPlan` all-or-nothing, revisiones inmutables con sucesión, estados de anclaje, capacidades
por actor y `heads.edn`. Las decisiones 1–20 de `05` siguen en pie salvo lo abierto por `D0`.

**Tamaño medido (histórico `2026-10-08` / `2026-10-09`):** el detalle comparativo de los formatos 2 → 3 → 4
vive en `10-escala.md §10–§11`: en la medición del desk de este repo (`433 KB`, `539` documentos), el
store pasó de `8,07 MB` (formato 3) a `2,51 MB` (formato 4) y `<store>/cache/` aparte (`2,26 MB`), sin
bajar todavía de los `433 KB` indexados (`§20`).

## 17. Preguntas al dueño (2026-10-08) — respondidas (registro histórico)

Respuestas históricas del dueño registradas el `2026-10-08`, traducidas directamente a las
decisiones `33–39` de `§12` (se conserva la columna `default` de la propuesta original junto con la
resolución real):

- **Respuestas confirmadas:** `Q1` b (`dec. 33`) · `Q2` pack mínimo = autodescripción (`dec. 34`) ·
  `Q3` el humano decide (`dec. 35`) · `Q4` sí, `:inline` por caso (`dec. 27`) · `Q5` caché (`dec. 27`) ·
  `Q6` sí, por composición grafo→documento (`dec. 27`) · `Q7` sí (`dec. 24`, hoy **en revisión `D-G`**) ·
  `Q8` sí con políticas (`dec. 31`, hoy **en revisión `D-G`**) · `Q9` sí (`dec. 32`) ·
  `Q10–Q11` diccionario = otro grafo por contexto (`dec. 36`) · `Q12` lo más simple + caché de derivados (`dec. 37`) ·
  `Q13` **rechaza el default**: sin idioma fijo ni plantilla, modelo multilingüe (`dec. 38`) ·
  `Q14–Q15` defaults (`dec. 39`).

Tabla original (registro histórico con sus defaults propuestos antes de responder):

| # | pregunta | opciones | default propuesto el 2026-10-08 | resolución posterior |
|---|---|---|---|---|
| Q1 | unidad de escritura | (a) una revisión por acción; (b) además log de hechos en flujo consolidado | (a) | **b** (decisión 33) |
| Q2 | transversales de primera clase | lista cerrada `{clase, dominio, W_i, tier}`; abierta por store | cerrada + abiertos por store | pack mínimo de autodescripción (decisión 34) |
| Q3 | quién afirma pertenencia a un transversal | selector; actor; ambas | ambas; gana el actor; selector `proposed` | humano con autoridad máxima (decisión 35; `proposed` en revisión `D-G`) |
| Q4 | contenido de hojas | por referencia con `:inline` opcional; inline siempre | por referencia | por referencia + `:inline` por caso (decisión 27) |
| Q5 | árbol estructural del documento | caché verificable; persistido completo | caché | caché verificable (decisión 27, implementado en Formato 4) |
| Q6 | log bajo `:git :ignore` y decisiones humanas | log = caché, decisiones como `source`; log persistido | caché; EDN | composición según caso (decisión 27) |
| Q7 | afirmación y valor | `:status` en evidencia de arista + `V`/`S` derivado; valor en nodo | evidencia + índice | **EN REVISIÓN (`D-G`)** (decisión 24) |
| Q8 | salida de prompt | `proposed` con versión modelo + prompt + parámetros | sí | **EN REVISIÓN (`D-G`)** (decisión 31; no existe `"propuesto"`) |
| Q9 | unidad de anclaje | hoja + grafemas NFC, chunks mapeados al entrar; chunk | hoja | hoja + grafemas NFC (decisión 32) |
| Q10 | diccionario corto (P14) | regla determinista (sin guardar); diccionario referenciado | regla determinista | otro grafo (decisión 36) |
| Q11 | alcance del vocabulario corto | por store; compartido | por store | **por contexto `W_i`** (decisión 36) |
| Q12 | dónde viven los hechos | objetos EDN en el CAS; registro compacto propio | registro compacto | forma más simple (CAS) + caché de derivados (decisión 37) |
| Q13 | verbalización para embeddings (P15) | idioma fijo + plantilla por relación; sin idioma/plantilla | español; plantilla en el vocabulario | **default descartado**: sin idioma fijo ni plantilla, modelo multilingüe (`P15` / decisión 38) |
| Q14 | spike del lenguaje de selección | etapa S; etapa A | etapa A | etapa A → cerrado por spike `R3` en `M1` (decisión 39) |
| Q15 | índice por revisión (mmap) en `:git :track` | registro; derivado (modelo git) | derivado | derivado (decisión 39; `K2a`/`K2b`) |
