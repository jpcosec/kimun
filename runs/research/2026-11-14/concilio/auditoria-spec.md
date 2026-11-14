# Auditoría del spec y de la propuesta actual contra las correcciones del 2026-11-14 — Claude

Solo lectura: no se editó ningún archivo auditado, no se ejecutaron tests, no hay commit. No leí código.

## 0. Qué audité y contra qué

**Correcciones** (texto vigente: cabecera de `docs/v2/14-juego-del-lenguaje.md`, dos recuadros del 2026-11-14):

| id | corrección |
|---|---|
| A1 | kimun cumple función de sistema nervioso: sensoriza, detecta mutaciones y eventualmente acciones |
| A2 | Merkle es el mecanismo de sensorización |
| A3 | la identidad del nodo no es el hash |
| A4 | la identidad del nodo no es su anclaje estructural en la topología |
| A5 | debe identificar topologías marcadas y seguir sus cambios en el tiempo, con niveles de abstracción |
| A6 | dos verificaciones: vigencia respecto de la huella; verdad del resultado si se marcan ejecuciones |
| A7 | el trabajo está entre S y M; G solo después |
| B1 | unidad = nodo con identidad propia, espacio mínimo de convergencia |
| B2 | su valor está en relaciones declarables: jerárquicas, horizontales, métricas y/o recursivas |
| B3 | sistema para LLMs: sentido relacional discreto (semántico y estructural) más embeddings continuos |
| B4 | autopoiesis y VSM aplican al sistema, no a la unidad; la unidad permite recursión |
| B5 | retirada la definición unidad = elaboración/pregunta con ciclo propio |
| B6 | daemon cuya única función es el heartbeat para recalcular señales |
| B7 | sistema asíncrono: mutaciones, señales externas y respuestas a acciones internas activan respuestas |

"Embedding ≠ identidad" y "similitud ≠ relación validada" vienen de la instrucción del coordinador; **no constan en la
cabecera de `14`**. Las trato como B8, con esa salvedad.

**Auditado.** Tomé "spec" como (a) el spec candidato y su roadmap del concilio anterior y (b) los documentos de
`docs/v2/` que hoy tienen autoridad; y "propuesta actual" como (c) los informes de esta carpeta. Si el encargo se
refería a otro documento, falta esa parte.

## 1. Resumen

1. **El spec candidato y el roadmap no cubren ninguna corrección B** y contradicen tres de las A. Son anteriores
   (2026-10-13) y no llevan aviso.
2. **Hay dos contradicciones literales**: la fila K1 del roadmap ("ID por lugar/estructura") y `spec §5.4` ("ruta
   cualificada… candidato para identidad de documento").
3. **En `docs/v2/` la corrección está registrada solo en la cabecera de `14`** y en `drafts/preguntas-abiertas.md`. Los
   demás documentos siguen diciendo lo contrario sin aviso, incluidos dos de mayor autoridad que `14` no declara revisar.
4. **La propuesta del concilio no responde el encargo central** (A5, B1): nadie define en positivo la identidad. Tres
   informes siguen sin marca de superados y hay cuatro desviaciones puntuales del mandato en los informes de heartbeat.

## 2. Spec candidato — `runs/research/2026-10-13/concilio/produccion/spec-final-candidato.md`

| sección | dice | corrección | veredicto |
|---|---|---|---|
| cabecera (l. 5), §1.2, §12.3 | alcance = kimun completo S/M/G; "no se divide" | A7 | **tensión de secuencia**. El producto puede seguir siendo completo, pero el spec pone el diseño de G al mismo nivel que S–M. Falta decir que G espera |
| §1.1 Propósito | organiza conocimiento como `(R A B)` en contextos | A1, B3 | **sin cobertura**: no nombra la función sensorial ni que el sistema es para LLMs |
| §2, fila M | "estructura M: parse/proyección determinista" | A7 (M = desacople estructural/semántico) | **contradice** la definición de M del dueño; hereda la de `14 §2` |
| §2, reglas | "la unidad de anclaje es hoja + rango de grafemas NFC" | B1 | choque de vocabulario: "unidad" aquí es granularidad de anclaje, no la unidad del mandato. Renombrar al integrar |
| §2, reglas | índices y embeddings son derivados y borrables | B3, B8 | compatible, pero deja el embedding como índice de G, no como dimensión de cada unidad |
| §3.1 | el hash expresa estado, no identidad | A3 | **cubre** |
| §3.2 | "identidad no se deduce de ruta/posición ni solo de hash" | A4 | **cubre**, por la corrección del 10-13 |
| §3.2 | "la estructura asumida debe poder repararse automáticamente"; reconciliar "usando evidencia de contenido y relaciones semánticas" | A5 | **parcial**. No dice qué identifica; describe cómo reparar. "Automáticamente" choca con la regla vigente de que la sucesión se registra y no se infiere (`docs/v2/02-sustrato-computacional.md §10`, inv. 7) |
| §3.3 | entregable de D0: tabla de identidad para nodos **S/M/G**, formato del ID, casos creación/duplicación/movimiento/edición/borrado/recreación | A5, A7, B1–B3 | **parcial**. Faltan niveles de abstracción, división, fusión y cambio de contrato; no pide relaciones ni embeddings; incluye G |
| §4 | G bajo `OWNER GATE D-G` | A7 | compatible; debería decir además que G no empieza hasta cerrar S–M |
| §5.2 | Merkle estable por ruta sobre lo trackeado | A2 | **cubre**. Falta la salvedad de que la ruta organiza el sensor y no identifica |
| §5.4 | "ruta cualificada relativa es el candidato para identidad de documento" | A4 | **contradice** |
| §7 | clases inmutables, instancia de clase | B1 | compatible (clase ≠ individuo); no dice nada del individuo |
| §8 | superficies: CLI, HTTP, adaptadores, include | B6, B7 | **sin cobertura**: no hay daemon ni modelo de activación asíncrona |
| §9.1 | un efecto externo no muta el grafo directamente | B6 | compatible: sirve de frontera al heartbeat |
| §10 | operación: locks, crash, recuperación | B6 | **sin cobertura**: un proceso residente añade ciclo de vida y concurrencia con el lock de escritura |
| todo el documento | — | A6 (2) | **sin cobertura**: no menciona ejecuciones marcadas ni el evaluador |
| todo el documento | — | B2 | **sin cobertura**: no hay taxonomía de relaciones; las métricas no existen |
| §12 | bloqueos: K1/D0, D-G, operación… | A5, B | **incompleto**: faltan identidad en positivo, relaciones, embeddings por unidad, heartbeat y ejecuciones |

## 3. Roadmap — `runs/research/2026-10-13/concilio/produccion/roadmap-produccion.md`

| lugar | dice | corrección | veredicto |
|---|---|---|---|
| §1; Gate A.3 | producto completo; revisar Tractatus y derivar G1 en el primer gate | A7 | **contradice** el orden: G entra en Gate A, en paralelo con D0 |
| §2, fila D0/K1-contract | "reconciliación reparable basada en contenido y relaciones semánticas" | A5 | parcial (igual que `spec §3.2`) |
| §2, fila K1 | "ID por lugar/estructura según D0; hash como verificación" | A4 | **contradice**, y contradice su propia fila D0/K1-contract |
| §2, fila K8; Gate C ("direccionable") | nombres cualificados por ruta | A4 | compatible solo como alias o localizador; la fila no lo dice |
| §2, fila S2 | anclas por rangos de bytes o nodos del CST | A4 | compatible como localizador; depende de K1, que está mal definido |
| §2, fila G3; Gate E.4 | embeddings sobre anchors, después de G1 | B3 | **tensión de orden**: el mandato pone embeddings en cada unidad, en el tramo S–M |
| §2, inventario | — | B6, B7 | **sin cobertura**: no hay tarea de heartbeat ni de activación |
| §2, inventario | — | A6 (2) | **sin cobertura**: no hay tarea de ejecuciones marcadas (el evaluador S4/S5 de `docs/v2/05-estado.md §1` no aparece) |
| §2, inventario | — | B2 | **sin cobertura** |
| §6 | "D0 no está completamente especificada" | A5 | compatible |

## 4. Documentos con autoridad — `docs/v2/`

Lo ya listado en `claude.md §3` (C1–C13) sigue en pie. Cambió desde entonces: la cabecera de `14` registra las dos
correcciones y `drafts/preguntas-abiertas.md` declara históricas las alternativas de D0. Lo que queda:

| documento | problema | corrección |
|---|---|---|
| `14-juego-del-lenguaje.md`, recuadro "cómo leer" | sigue listando §21 como "pendiente de confirmar" y §18–§20 como vigentes sin salvedad | A4 |
| `14 §21` | el cuerpo no lleva marca; quien entre por la sección lee "IDs estructurales" como respuesta del dueño | A4 |
| `14 §18.8` | "dos documentos idénticos tienen los mismos nodos" | B1 |
| `14 §2`, fila M | M = estructura; identidad = hash | A3, A7 |
| `14 §7`, regla 1 | "como la identidad es el hash…" | A3 |
| `14 §19` regla 3; decisión 43 | la base no depende de modelos ni de G; el grafo es paquete aparte | B1, B3: si la unidad reúne lo estructural, lo semántico y el embedding en un nodo, hay que decir en qué lado vive. **Tensión, no contradicción** |
| `14 §12` decisión 38, `§16` P15; tabla de motores | embeddings sobre la forma canónica del **hecho**, índice por `(hash de hoja, motor, versión)` | B3, B8: están atados a G y su clave es una huella. Como clave de caché sirve; como vínculo con la unidad, no |
| `01-orden-filosofico.md §2`, `§4.3` | "el nodo es el símbolo"; identidad = contenido | A3, B1 |
| `01`, aviso de cabecera | solo cubre la mitad del hash | A4 |
| `01 §8` | bucle autopoiético a nivel de sistema | B4: **compatible** |
| `02-sustrato-computacional.md`, aviso de cabecera | remite a "ids estructurales para lo que ancla" | A4 |
| `02 §6`, `§2.1`, inv. 2, 13, 17 | identidad = hash | A3 |
| `02 §3` | siete tipos de arista | B2: **parcial**. Hay jerarquía (`ownership`), sucesión (`supersedes`), orden de hermanos; no hay relación métrica ni una clasificación por los cuatro ejes del mandato |
| `02 §10` inv. 11 y 18 | efectos externos no mutan el grafo; el estado de anclaje se recomputa | B6: **compatible**; es la base del heartbeat |
| `05-estado.md §1`; decisiones 1, 4, 46 | "motor content-addressed"; 46 pendiente con ids estructurales | A3, A4 |
| `05 §1` | "no implementado: evaluador anclado (S4) y derivados (S5)" | A6 (2): único lugar donde vive la segunda verificación |
| `06-superficies-y-cli.md` | inventario de CLI y `serve`; sin proceso residente | B6: **sin cobertura** |
| `12-ecosistema-y-pendientes.md §1` | D0 y K1 por lugar; G3 después de G1; sin heartbeat, sin ejecuciones | A4, B3, B6, A6 |
| `00-nucleo.md` 3.3 | nombra "D0, abierta" con el enunciado viejo | A4 (solo la referencia; la proposición trata identidad semántica) |
| `README.md`, tabla | "hash ≠ identidad (§21, pendiente)" | A3, A4 |
| `drafts/preguntas-abiertas.md` | el aviso cubre la primera corrección; no menciona unidad, relaciones ni heartbeat | B1–B7 |
| `drafts/fuentes-identidad.md §4–§5`, `drafts/ids-desde-matrix.md` | identidad por lugar, sin aviso | A4 |
| `AGENTS.md`, "Resuming work" | "hoy: D0, identidad de nodo"; principio "hash ≠ identity" | A4 |

**Problema de autoridad.** `14` es el segundo documento en la tabla de `docs/v2/README.md` y solo gana sobre los
documentos que declara revisar: `01 §4` (principio 1), `07`, `11 §1` y `12 §3`. La corrección toca además `01 §4.3` y
`01 §2`. Por la regla "gana el número menor", en esos puntos sigue ganando `01`. Hace falta que `14` declare que los
revisa, o que `01` se edite.

## 5. Propuesta actual — `runs/research/2026-11-14/concilio/`

| archivo | hallazgo | corrección |
|---|---|---|
| todos | **nadie define la identidad en positivo**. `sintesis.md` la deja en "referencia a una unidad marcada cuya continuidad se registra"; los informes de heartbeat repiten el mandato | A5, B1: el encargo sigue abierto |
| `sintesis.md` | "definir el nodo como **referencia a** una unidad marcada" | B1: el mandato dice que la unidad **es** el nodo |
| `sintesis.md` | no trata relaciones, embeddings ni heartbeat (es anterior) | B2, B3, B6 |
| `unidad-sintesis.md` | lleva aviso de superada; el cuerpo mantiene "unidad elaborada y sensor son distinciones necesarias" | B1, B5: correcto que esté marcada |
| `unidad-claude.md`, `unidad-pi.md`, `unidad-agy.md` | definen la unidad por elaboración, pregunta o ciclo; **sin aviso en el archivo**. La retirada consta solo en `heartbeat-{claude,pi,agy}.md` | B5 |
| `autopoiesis-claude.md` (crítica 3), `autopoiesis-pi.md` (l. 38–41), `autopoiesis-agy.md` (puntos 2–3) | piden que cada unidad conserve una organización propia | B4: aplican al individuo lo que es del sistema; sin aviso |
| `pi.md §2`, `§3.1` | `Unit {id, level, membrane, contract, lineage}`: único candidato técnico | B1–B3: no tiene dónde declarar relaciones ni la dimensión continua; A7: incluye una fila M con identidad semántica. Además sigue sin corregir en el archivo lo señalado en `sintesis.md` (CAS de contenido, `moved` por igual huella) |
| `agy.md §4`, `§6.2` | niveles L0–L4 y "opción C: grafo de continuidad" | A5: es lo más cercano a "niveles de abstracción"; no contrastado contra B |
| `heartbeat-pi.md`, consecuencia 3 | el daemon puede señalar "hay nueva relación candidata" | B6: proponer relaciones excede "recalcular señales" |
| `heartbeat-pi.md`, última sección | "embeddings como capa métrica separada" | B2, B3: el mandato lista las métricas entre las relaciones **declarables** y pone los embeddings aparte ("además") |
| `heartbeat-agy.md §2.A` | "ante cambios… recalcula"; estados `fresh`, `drifted`, `orphan`, `ambiguous` | B6: lo vuelve reactivo, no un latido. `fresh` y `ambiguous` no son estados de `02 §6.2` (`intact`, `superseded`, `orphan`; `drifted` como refinamiento) |
| `heartbeat-agy.md §2.B` | "identidad **direccionable** propia"; cuatro planos | B1, B3: "direccionable" y la división en cuatro son lectura de Agy, presentadas junto al mandato |
| `heartbeat-claude.md` | "relaciones métricas = declaradas con magnitud" | B2: es interpretación mía; está marcada como tal |
| `heartbeat-claude.md`, propuesta técnica | "bajar solo por las ramas del Merkle que cambiaron" | A2: para saber qué cambió hay que releer el ambiente; el coste del latido no está acotado en ninguna propuesta |
| `vsm-*.md`, `zettelkasten-*.md`, `epl2r-*.md` | lecturas de fuentes | compatibles como insumo. `epl2r-claude.md` propone un recorrido que ya no es contrato (B5) |

## 6. Cobertura por corrección

| corrección | spec candidato | roadmap | `docs/v2/` | concilio |
|---|---|---|---|---|
| A1 función sensorial | no | no | cabecera de `14`; `01 §8` | sí |
| A2 Merkle sensor | sí (§5.2) | sí (K2a) | `14 §20` | sí |
| A3 no hash | sí (§3.1) | sí | cabecera de `14`; contradicho en `01`, `02`, `05` | sí |
| A4 no lugar | parcial; contradicho en §5.4 | contradicho en K1 | cabecera de `14`; contradicho en `14 §21`, `02`, `05`, `12` | sí |
| A5 identidad en positivo, niveles | no | no | no (declarada pendiente) | **no** |
| A6 dos verificaciones | solo la primera | solo la primera | cabecera de `14` | sí, sin contrato |
| A7 S–M antes de G | no | contradicho en Gate A | cabecera de `14` | sí |
| B1 unidad = nodo | no | no | cabecera de `14`; contradicho en `14 §18.8`, `01` | repetida, no desarrollada |
| B2 relaciones | no | no | cabecera de `14`; parcial en `02 §3` | repetida; "métricas" sin lectura común |
| B3 LLMs, embeddings | no | orden contrario (G3) | cabecera de `14`; orden contrario en `12`, decisión 38 | sí |
| B4 sistema, no unidad | — | — | cabecera de `14`; `01 §8` | sí en heartbeat; no en tres informes previos |
| B5 retiro | — | — | cabecera de `14` | sí en heartbeat; tres archivos sin aviso |
| B6 heartbeat | no | no | cabecera de `14` | sí, con dos excesos |
| B7 asíncrono | no | no | cabecera de `14` | mencionado, sin contrato |

## 7. Qué corregir primero (recomendación, no hecho)

1. Marcar el spec candidato y el roadmap como anteriores a las correcciones, y corregir sus dos contradicciones
   literales: fila K1 y `spec §5.4`.
2. Resolver el problema de autoridad entre `14` y `01 §2`/`§4.3`.
3. Poner aviso en `14 §21`, en las cabeceras de `01` y `02`, en la decisión 46 y en las filas D0, K1 y G3 de `12 §1`.
4. Poner aviso de superados en `unidad-{claude,pi,agy}.md` y en las críticas de `autopoiesis-*.md`.
5. Decidir si "embedding ≠ identidad" y "similitud ≠ relación validada" entran a la cabecera de `14`.
6. Abrir como trabajo de diseño lo que nadie cubre: identidad en positivo con niveles, lectura común de "relaciones
   métricas", lugar de los embeddings respecto de G, contrato del heartbeat y contrato de ejecuciones marcadas.

Límites: no contrasté contra el código ni revisé `docs/v2/tests/`, `04`, `07`, `08`, `10`, `11` ni `13` más allá de lo
ya leído hoy; de `agy.md` leí la estructura, `§6.2` y `§7`, no el cuerpo completo.
