# D0 — contrato de identidad y continuidad en S–M: síntesis del concilio (v5)

Fecha: 2026-11-15. Autor de la síntesis: Claude, sobre dos derivaciones independientes
(`d0-contrato-claude.md`, `d0-contrato-pi.md`), la segunda lectura de Pi
(`d0-segunda-lectura-pi.md`), su revisión de la v4 y las observaciones del coordinador a cada
versión.
**Estado: contrato S–M del concilio, revisado por el coordinador (2026-11-15).** Sus reglas son
inferencias del concilio a partir de premisas del dueño; no son texto del dueño. `docs/v2/14 §21`
lo referencia. No implementa nada: K1 es el corte que lo llevará al kernel.

**Alcance.** El dueño dijo que el trabajo está entre S y M y que solo con esa base se aborda G
(cabecera de `docs/v2/14-juego-del-lenguaje.md`). Este contrato cierra **identidad y
continuidad de unidad, anclaje y observación en S–M**. No fija la lógica de `(R A B)`, ni
verdad, ni sentido: eso es G. El dilema de la base de la recursión queda en el anexo A como
diseño futuro, **sin regla adoptada**, y no bloquea K1.

**Historial.** v1 definía la unidad con ejemplos de cosas. v2: el nodo es un hecho detectado,
nunca la cosa. v3 adoptó una regla para los términos base de `(R A B)`. v4 la retira del
contrato: era lógica de G. v5, tras la revisión de Pi a la v4: una lectura repetida es una
observación nueva con su fecha; la ausencia observada **no** termina una unidad (el sensor
informa `missing` y cerrar o re-anclar se declara); la regla de reemplazo vale por nivel; I5
deja de atribuirse a P8; entre contextos se usa «proyección declarada», no `equivalent`; y la
observación queda definida en S–M como evento de la unidad, no como nodo.

Marcas: **[D]** palabras o dirección del dueño, con fuente; **[F]** una fuente; **[I]**
inferencia de diseño del concilio.

## 1. De qué se parte

El dueño ya fijó las premisas; no se le pregunta D0 otra vez (corrección transmitida por el
coordinador, 2026-11-15).

| # | premisa | fuente |
|---|---|---|
| P0 | «El mundo es el conjunto de todos los hechos, no de todas las cosas. Los hechos son proposiciones que tienen alguna relación con la realidad» | **[D]** `runs/research/2026-11-14/concilio/brief-d0-dos-etapas.md` |
| P1 | La identidad del nodo no es el hash ni su anclaje estructural en la topología. Sustituye «identidad = lugar» | **[D]** cabecera de `14`, 2026-11-14 |
| P2 | Debe permitir identificar las topologías marcadas y seguir sus cambios en el tiempo, con niveles de abstracción | **[D]** ídem |
| P3 | Realidad y hecho son cosas distintas: (1) anchoring, (2) el hecho detectado | **[D]** brief |
| P4 | El valor de verdad está en la realidad. Verificación (1): «lo marcado no cambió respecto de la huella registrada, **afirmación** que deja de sostenerse ante una mutación» | **[D]** brief; cabecera de `14` |
| P5 | Una proposición es `(R A B)`; `R` relaciona los hechos `A` y `B` | **[D]** brief |
| P6 | La unidad es un nodo con identidad propia; vale por las relaciones declarables sobre ella | **[D]** cabecera de `14` |
| P7 | Zettelkasten: referencia a la fuente por un lado, ficha direccionable en red por otro | **[D]** brief; **[F]** Luhmann: número asignado una vez, que nunca cambia y no depende del contenido |
| P8 | Anchor roto = borrado (se declara) o mutación (la detecta el hash). Lo nuevo nace en el contexto de su documento; subirlo o declararlo `equivalent` es curaduría | **[D]** `14 §21` punto 5; `docs/v2/drafts/fuentes-identidad.md §4` |

## 2. Qué es cada cosa en S–M

| | qué es | ¿tiene identidad de nodo? | ¿cambia? |
|---|---|---|---|
| **realidad** | el documento, la sección, la función: cosas del ambiente | no entra al grafo (P0, P3) | por fuera de kimun |
| **unidad** | el **hecho detectado**: que se marcó una topología y se la sigue (etapa 2) | **sí, propia** (P6) | nunca |
| **anclaje** | la referencia vigente de una unidad a una porción de la realidad: localizador + contrato de lectura (etapa 1) | no | sí; una unidad tiene anclajes sucesivos |
| **observación** | la afirmación fechada de qué se leyó bajo ese anclaje: huella y proyección | **no en S–M**: es un evento registrado sobre la unidad | cada lectura es una nueva, aunque lea lo mismo |
| **relación de continuidad o linaje** | un hecho declarado entre unidades | sí | no; corregirla es otra |

El documento no llega a ser un hecho: es realidad. Lo que entra es el hecho de haberlo marcado.

La observación es una **afirmación**, y la palabra es del dueño (P4): la verifica el sensor
contra la realidad y deja de sostenerse ante una mutación. Lo que veníamos llamando vigencia es
la verdad de esa afirmación. En S–M la observación **no es un nodo**: «deja de sostenerse» es
un cambio de estado de la unidad. Si en G conviene tratarla como hecho, es diseño de G
(anexo A).

## 3. Reglas

**Todas las reglas I1–I13 son inferencias del concilio.** Las premisas P0–P8 son del dueño; lo
que sigue es la lectura que el concilio hace de ellas. Cada regla cita la premisa en que se
apoya, y eso no la convierte en palabra del dueño.

**I1 [I]. La unidad es un hecho detectado, no una cosa.** Nada de la realidad es un nodo (P0, P3).

**I2 [I]. La identidad de una unidad nace en el acto de marcarla y no se calcula.** No sale del
contenido ni del lugar (P1); es «propia» (P6); el modelo es el número de la ficha (P7). No
cambia nunca y no se reutiliza.

**I3 [I]. El anclaje no tiene identidad de nodo.** P1: la identidad del nodo no es «su anclaje». La
unidad lo tiene y puede cambiarlo.

**I4 [I]. Una observación nueva no es una unidad nueva.** Editar, reformatear o releer dan
observaciones de la misma unidad. Cada lectura es una observación con su fecha, aunque lea lo
mismo que la anterior: «no cambió» y «no se leyó» son cosas distintas (P4). Cuáles de esas
lecturas se persisten es diseño de K1; hoy el latido no escribe ninguna.

**I5 [I]. Mientras el anclaje resuelve, un cambio es una mutación de esa unidad.** P8 dice que
el hash detecta la mutación de lo marcado; **que la unidad sobreviva a ella no lo dice P8**: es
inferencia del concilio, apoyada en P2 (seguir sus cambios) y P6. El lugar es evidencia de
continuidad, no identidad.

**I6 [I]. Individuación bajo cambio.** El sensor no distingue editar de **reemplazar**: borrar
algo y poner otra cosa sin relación bajo el mismo localizador se ve igual que una reescritura
(contraejemplo de Pi). La regla:
- por defecto vale I5: es una mutación de la misma unidad;
- el **reemplazo se declara**: cierra la unidad y marca otra;
- el sensor puede **señalar** indicios de reemplazo (ningún solapamiento, o una ausencia entre
  dos lecturas); nunca decide;
- vale **en cada nivel**: lo que se reemplaza es la extensión marcada (un archivo, una sección,
  un campo), no necesariamente el archivo. Una sección reemplazada dentro de un documento que
  sigue ahí no produce ausencia de archivo.

Límite conocido: sin declaración, un reemplazo queda registrado como mutación. Cualquier
mutación ya deja en revisión lo que se apoya en esa unidad.

**I7 [I]. La ausencia no termina una unidad.** Cuando el anclaje deja de resolver, el sensor
informa `missing` y la unidad sigue existiendo, sin anclaje vigente. Un montaje que desaparece
un segundo no puede matar una unidad: eso haría depender la identidad del anclaje, contra P1.
Lo que sigue es una **declaración**, con actor y evidencia, que nombra lo que pasó en medio:
- **cerrar** la unidad: es el borrado de P8, que «se declara»; o
- **re-anclarla**: darle un anclaje nuevo a la **misma** unidad (movida, o ausente y
  reaparecida).

**I8 [I]. Una unidad no se bifurca ni se funde.** Copiar, dividir y fusionar crean unidades nuevas,
unidas a las anteriores por relaciones de linaje declaradas. **[F]** Ramificación de Luhmann:
ficha nueva, sin renumerar la original.

**I9 [I]. Dos marcas son dos unidades aunque lo leído sea idéntico** (P1).

**I10 [I]. Lo mismo en dos fuentes son dos unidades.** P8: lo nuevo nace en el contexto de su
documento. Unirlas entre contextos es una **proyección declarada**, por curaduría. No se usa
`equivalent` por defecto: en las fuentes de Matrix (`fuentes-identidad §2.5`) `equivalent` es
una meta-relación entre símbolos con umbral, no la unión de dos hechos de contextos distintos.

**I11 [I]. La regla vale en cada nivel de abstracción** (P2). La jerarquía entre unidades es una
relación declarada, no parte del id.

**I12 [I]. El sensor propone; el cierre, la continuidad y la proyección entre contextos se
declaran** (P1, P8).

**I13 [I]. Una relación entre unidades lleva id propio, no derivado de sus términos.** Un
id calculado desde `(contexto, R, A, B)` sería «identidad = lugar semántico», lo que P1
sustituyó. La coordenada dice qué proposición es y sirve de clave; el id es aparte.

## 4. Casos

| caso | unidad | anclaje y observación | relación que queda | el sensor solo / hace falta declarar |
|---|---|---|---|---|
| **editar** en el sitio | la misma | mismo anclaje; la observación anterior deja de sostenerse; una nueva al re-registrar | ninguna nueva | detecta la mutación; re-registrar dice «visto» |
| **reformatear** | la misma | mismo anclaje; huella nueva, proyección igual | ninguna | reparación sin decisión (`stores update`) |
| **reemplazar** bajo el mismo localizador | por defecto la misma; otra solo si se declara | mismo localizador | si se declara: cierre de una y alta de otra | no lo distingue de editar; puede señalar indicios |
| **mover** | la misma | el anclaje viejo deja de resolver (`missing`); con declaración, anclaje nuevo | queda quién re-ancló, con qué evidencia y qué pasó en medio | ve «falta aquí» y «candidato allá»; la continuidad se declara |
| **copiar** | el original intacto; la copia es **otra** | cada una con su anclaje | `(copied-from nueva original)` si se declara | ve dos topologías con igual huella: candidato |
| **dividir** | el original queda `missing` hasta que se declare: o se cierra, o una parte lo continúa; las demás son nuevas | anclajes nuevos | `(split-from parte original)` por cada parte nueva | ve que el original no resuelve; el reparto se declara |
| **fusionar** | el resultado es nuevo, o sobrevive una por declaración; la absorbida termina | anclaje nuevo | `(merged-into absorbida resultado)` | candidatos por huella; la decisión se declara |
| **borrar** | sigue, sin anclaje vigente, hasta que se declare el cierre; su historia y sus relaciones quedan | `missing` | siguen apuntándola, con la pérdida de soporte declarada | informa `missing`; el cierre se declara |
| **lo mismo en dos fuentes** | dos | dos anclajes | una proyección declarada entre contextos, si alguien la declara | puede proponer la similitud |

Las relaciones sobre una unidad que terminó no se re-apuntan solas (P8;
`docs/v2/00-nucleo.md §2.4`). Los nombres de las relaciones son ilustrativos: fijarlos es
diseño de K1.

## 5. Los cuatro bloqueos del borrador anterior

`runs/research/2026-11-14/concilio/contrato-dos-etapas.md` los daba por reservados al dueño.

| | resultado |
|---|---|
| **B1** ¿re-apuntar o anchors inmutables con `continúa`? | **Resuelto** (I5–I8). Uno a uno: la misma unidad, re-anclada por declaración. Uno a varios o varios a uno: unidades nuevas con linaje |
| **B2** ¿cómo nace el id y cómo se individúa? | **Resuelto para la unidad** (I2). Para una relación: id propio (I13). La codificación es técnica |
| **B3** ¿el anchor es término? ¿uno o dos nodos? | **Resuelto en lo que toca a S–M** (I3): el anclaje no es un nodo. Qué son los términos base de `(R A B)` es del anexo A |
| **B4** ¿qué hechos son de S–M? | **Resuelto para identidad**: unidades, sus anclajes y observaciones, y linaje declarado. Lo demás es G |

## 6. Dónde difirieron las lecturas y cómo quedó

| punto | Claude | Pi | queda |
|---|---|---|---|
| ¿qué es el nodo? | la topología marcada | anchor y hecho, dos unidades | el hecho detectado; la cosa nunca |
| ¿el anclaje es nodo? | sí | sí | no (letra de P1) |
| mover | la misma, re-anclada | tabla: la misma; §2.2: nueva | la misma (Pi retiró su §2.2) |
| editar frente a reemplazar | no lo veía | contraejemplo: borrar y crear en la misma ruta | I6: el reemplazo se declara |
| ausencia observada | en v4, terminaba la unidad | objeción: un montaje que parpadea mataría la unidad | I7: `missing` es propuesta; el cierre se declara |
| lectura repetida | «no agrega nada» | es una observación nueva con su fecha | I4 |
| lo mismo en dos fuentes | dos | una con dos soportes | dos (P8) |
| identidad de una relación | propia | la coordenada | id propio, coordenada como clave (I13) |
| base de `(R A B)` | hecho que no es trío | trío con designadores | **no se adopta ninguna**: anexo A |

## 7. Lo que no es pregunta para el dueño

- **Formato o codificación del id.** Decisión técnica del equipo. El contrato fija propiedades:
  único en el store que lo acuña, nunca reutilizado, opaco al contenido, a la ruta y a la
  coordenada, estable de por vida.
- **Quién puede declarar continuidad.** Política de regulación. Por defecto el sensor propone y
  aplicar es un acto explícito, como ya hace `stores reconcile [--apply]`.
- **Ejecuciones marcadas.** El dueño las dejó «por especificar». Diseño pendiente aparte.
- **La base de la recursión y la lógica de verdad.** G (anexo A).

**Para identidad y continuidad en S–M el concilio no encontró ambigüedad que exija al dueño.**
Lo que queda son inferencias de diseño nuestras, listadas en §8.

## 8. Inferencias de diseño del concilio

Reglas que no son palabras del dueño ni se derivan literalmente de ellas:

1. **I5 e I6, supervivencia e individuación bajo cambio.** El dueño habla de la afirmación que
   deja de sostenerse, no de si la unidad sobrevive ni de cuándo pasa a ser otra.
2. **La unidad como hecho estable con observaciones sucesivas.** La alternativa, una unidad por
   observación, la descarta Pi con el latido: un store sin cambios produciría un hecho cada 30 s
   y no se distinguiría «no cambió» de «no se leyó». Choca además con P6 y P7. Ninguna frase del
   dueño la excluye expresamente.
3. **I3, el anclaje sin identidad**, sale de la gramática de una frase (P1).
4. **I13, id de una relación separado de su coordenada.**
5. **Editar conserva la unidad** aunque lo leído cambie por completo, salvo declaración.
6. **La identidad es local al store.** Ninguna premisa habla de varios stores.
7. **Uso de las fuentes de Matrix.** Se citan solo para los contextos. Su tesis de que la
   identidad de un símbolo es el lugar donde convergen sus ocurrencias es «identidad = lugar»,
   que la corrección del dueño sustituyó para los nodos: no se reimporta aquí.

## 9. Consecuencias para lo que sigue

- El nivel documento ya se comporta así: el id de árbol se acuña al registrar y sobrevive a
  editar. El hueco está debajo: bloques y texto se identifican por contenido. Eso es K1.
- Direccionar **valores** por hash sigue siendo válido (`14 §21` separa id, content, hash y
  embedding). Lo que no puede es servir de identidad de un nodo.
- **La identidad no se puede recomputar**: hay que persistirla. `include build --rebuild` hoy
  acuña ids nuevos; con este contrato eso pierde identidades y lo declarado sobre ellas. Falta
  una regla de arrastre para la reconstrucción; es diseño de K1.
- Estados de anclaje, sucesión por transacción y propuestas de reconciliación
  (`docs/v2/02 §6`) ya separan «el sensor detecta» de «alguien declara».

## 10. Qué se corrigió en `docs/v2/` con este contrato

Commit DOC-D0 (`runs/subagents/2026-11-15-DOC-D0/EVIDENCE.md`): la fila D0 de
`docs/v2/drafts/preguntas-abiertas.md`, `docs/v2/14 §21`, la decisión 46 de
`docs/v2/05-estado.md`, las filas de `docs/v2/12 §1` que esperaban a D0, y
`test/kimun/doc14_contract_test.cljc`, que fijaba varias de esas frases.

## 11. Lo que no se hizo

- No se releyeron las fuentes externas de Zettelkasten en su original.
- No se contó en el código cuántos sitios usan hoy un id de contenido como identidad.
- Agy no participó. Pi revisó la v4 y sus seis objeciones están integradas en esta v5; no ha
  visto la v5.

---

## Anexo A — la base de `(R A B)`: diseño futuro de G, sin regla adoptada

Se registra para no perderlo y para dejar corregidos dos argumentos. **No es parte del contrato
de S–M y no bloquea K1**: K1 necesita ids estables para unidades, anclajes como estado,
observaciones e hilos de linaje entre unidades. No necesita decidir qué forma tiene el hecho
más elemental.

**El dilema** (argumento del coordinador). Si todo hecho es un trío `(R A B)` y `A` y `B` son
siempre hechos, cada nodo apunta al menos a otro hecho, y en un conjunto finito alguna cadena
se cierra. Un store finito no puede cumplir a la vez «todo hecho es un trío», «los términos
son siempre hechos» y «sin ciclos». Hay que ceder en una:

| salida | qué cede de la letra del dueño | coste |
|---|---|---|
| (a) hechos base que no son tríos | «los hechos son proposiciones» y «una proposición es un trío», en la base | el hecho más elemental queda fuera de la forma `(R A B)` |
| (b) ciclos | nada | **no** es un problema de decidibilidad: en un grafo finito las dependencias se calculan (componentes fuertemente conexas, cierres). El coste es semántico: verdad y soporte sin base, y un hecho puede acabar justificándose a sí mismo |
| (c) términos base que no son hechos («designadores») | «`R` relaciona **los hechos** `A` y `B`», en los hechos base | hay que decir qué son esos términos. Es una **excepción explícita** a la letra, no una conservación |

**Correcciones a lo dicho antes.**
- La v2 de esta síntesis presentaba (a) como si uniera las frases del dueño. No: las tres
  salidas ceden algo o cuestan algo, y elegir es diseño.
- La segunda lectura de Pi decía que (b) hace «indecidible» qué hechos necesita un hecho. No es
  cierto; el coste es el de la tabla.
- La segunda lectura de Pi decía que (c) «conserva la letra del dueño». La rompe en los hechos
  base.
- Los designadores, si se adoptan, **no son del dueño**: son una propuesta de diseño de Pi.

**Inclinación del concilio, sin adoptarla.** (c) es la que mejor encaja con lo cerrado en S–M:
el anclaje ya es una referencia sin identidad de nodo, y la etapa 1 pondría designadores donde
la etapa 2 pone el hecho. Pi dejó escrito un axioma de terminación para (c) en
`d0-segunda-lectura-pi.md` (rango 0 para designadores, 1 + el máximo de sus términos para un
hecho, y solo se toman como términos hechos que ya existen). Queda como punto de partida para
cuando se aborde G.

**Lo que G tendrá que decidir**: cuál de las tres; si una observación es un nodo; y las firmas
de cada relación.
