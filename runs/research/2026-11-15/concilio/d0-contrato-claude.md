# D0 — contrato positivo de identidad y continuidad, derivado (Claude)

Fecha: 2026-11-15. Solo lectura y diseño: no cambia `docs/v2/` ni código. Derivación
independiente de la de Pi (`d0-contrato-pi.md`), que no leí antes de escribir esta.

Marcas: **[D]** palabras o dirección del dueño, con su fuente; **[F]** lo que dice una fuente;
**[I]** inferencia mía. No propongo formato de id ni diseño de G.

## 0. Por qué esto no es una pregunta al dueño

La síntesis del concilio (`runs/research/2026-11-14/concilio/d0-dos-etapas-sintesis.md`) dijo
«parcialmente desbloqueada» y el borrador `contrato-dos-etapas.md` dejó cuatro bloqueos
«reservados al dueño» (B1–B4). El dueño corrigió eso (mensaje del coordinador, 2026-11-15): las
premisas ya están fijadas y lo que falta es derivar. Esta nota hace la derivación y encuentra
que **B1–B4 se resuelven sin preguntarle nada**. Lo que queda (§6) son dos inferencias más
débiles que las demás, con un valor por defecto reversible; ninguna bloquea.

## 1. Premisas fijadas

| # | premisa | fuente |
|---|---|---|
| P1 | La identidad del nodo **no es el hash ni su anclaje estructural en la topología** | **[D]** cabecera de `docs/v2/14-juego-del-lenguaje.md`, corrección 2026-11-14 |
| P2 | Debe permitir **identificar las topologías marcadas y seguir sus cambios en el tiempo, con niveles de abstracción** | **[D]** ídem |
| P3 | **Realidad y hecho son cosas distintas**: (1) anchoring, (2) el hecho detectado | **[D]** `runs/research/2026-11-14/concilio/brief-d0-dos-etapas.md` |
| P4 | El valor de verdad de una proposición está en la realidad; la huella solo comprueba vigencia del anchor | **[D]** ídem y cabecera de `14` |
| P5 | Una proposición es `(R A B)`; `R` relaciona hechos `A` y `B`; recursiva | **[D]** ídem |
| P6 | La unidad es un **nodo con identidad propia**; su valor viene de las relaciones declarables sobre ella (jerárquicas, horizontales, métricas, recursivas: herencia, temporales, secuenciales) | **[D]** cabecera de `14`, precisión 2026-11-14 |
| P7 | Zettelkasten es la fuente para la separación entre referencia a la fuente y ficha direccionable | **[D]** brief |
| P8 | Un anchor estructural roto es **borrado (se declara) o mutación (la detecta el hash)**; un símbolo nuevo **nace en el contexto de su documento**; subirlo o declararlo `equivalent` es **curaduría explícita** | **[D]** `14 §21` punto 5 (2026-10-09) |

Lo que aporta P7 **[F]** (Luhmann, vía `zettelkasten-claude.md`): cada ficha recibe un número
**una vez**, que **nunca cambia** y no depende del tema ni del contenido; por eso admite
cuantas referencias se quiera. Se continúa o ramifica desde cualquier ficha con una ficha
**nueva** (57/12 → 57/12a) sin renumerar la original. La bibliografía va en **otra caja**.

## 2. Regla de identidad

**R1. La identidad es nominal: nace en el acto de registro y no se calcula.**
Una identidad se crea cuando algo se marca (etapa 1) o se registra como unidad (etapa 2). No
se deriva del contenido ni del lugar: P1 excluye las dos cosas, P6 dice «identidad propia» y
P7 da el modelo, un número asignado una vez. **[I]** «nominal» es mi palabra para lo que P1, P6
y P7 dejan como única salida.

**R2. Una identidad no cambia nunca y no se reutiliza.** **[F]** P7. Es lo que permite que las
relaciones se acumulen sobre ella (P6).

**R3. Dos registros son dos identidades, aunque el contenido sea idéntico.** De R1. Resuelve
la tensión de `14 §18.8`: dos párrafos con los mismos bytes son dos ocurrencias. La igualdad
de huella es un dato del sensor, no identidad (P1).

**R4. Lo que cambia en el tiempo son los estados de una identidad, no la identidad.**
Localizador, huella, contrato de lectura y contenido leído son **observaciones** fechadas de
la misma identidad. Eso es «seguir sus cambios en el tiempo» (P2).

**R5. La regla vale igual en cada nivel de abstracción.** Documento, sección, bloque, campo,
símbolo: cada topología marcada tiene su identidad, y la jerarquía entre ellas es una relación
declarada (P6), no parte del id. Un cambio abajo sube como señal de mutación (Merkle) sin
cambiar ninguna identidad de arriba (P2, P4).

**R6. Hay dos clases de nodo con identidad, y no se confunden.**
- El **anchor** (etapa 1) es la referencia a una porción de la realidad: no afirma nada y no
  tiene valor de verdad, solo vigencia (P3, P4). Es la «otra caja» de P7.
- La **unidad o hecho detectado** (etapa 2) es lo que participa en relaciones (P5, P6).

Un mismo anchor sostiene varias unidades (un documento, muchos campos), así que no pueden ser
un nodo con dos facetas: son dos nodos unidos por una relación de soporte **[I]**.

**R7. Un anchor no es término de `(R A B)`.** `R` relaciona **hechos** (P5) y el anchor no es
un hecho (P3). Los términos son unidades de etapa 2; la recursión toca fondo en hechos
detectados, cada uno con su soporte. Una relación declarada es a su vez un hecho y puede ser
`A` o `B` de otra.

**R8. Lo mismo dicho en dos fuentes son dos unidades.** P8: lo nuevo nace en el contexto de su
documento, y declararlo `equivalent` es curaduría explícita. Dos detecciones de la misma
proposición nacen como dos unidades; que sean «la misma» es un hecho declarado
`(equivalent H1 H2)`, nunca una fusión automática por contenido igual.

**Propiedades que debe tener el id** (no su codificación): único en el store que lo acuña,
nunca reutilizado, opaco (nada lee contenido ni ruta en él), estable de por vida.

## 3. Regla de continuidad

**C1. Uno a uno conserva la identidad.** Si lo marcado sigue siendo una sola cosa, es la misma
identidad con un estado nuevo.
- Mientras el anchor **resuelve** (mismo localizador bajo su contrato), un cambio es una
  mutación **de esa identidad**: lo dice P8 («si es mutación, la detecta el hash»). Aquí el
  lugar es evidencia de continuidad, no identidad.
- Cuando el anchor **deja de resolver**, P8 lo llama borrado y exige declararlo. Que lo que
  aparece en otro lugar sea su continuación no lo decide el sensor: es una **declaración**,
  con actor y evidencia, que re-ancla la **misma** identidad.

**C2. Una identidad no se bifurca ni se funde.** Uno a varios (copiar, dividir) y varios a uno
(fusionar) crean identidades **nuevas** unidas a las anteriores por hechos de linaje
`(R A B)`. **[F]** Es la ramificación de Luhmann: ficha nueva, la original no se renumera.

**C3. Nada se re-apunta en silencio.** Las relaciones declaradas sobre una identidad que
terminó se conservan apuntando a ella, con su pérdida de soporte declarada (P8; `00-nucleo`
§2.4). Moverlas a otra identidad es curaduría.

**C4. El sensor propone; la continuidad se declara.** El sensor ve huellas iguales o cercanas y
puede **proponer** candidatos. No afirma continuidad (P1: la huella no es identidad).

## 4. Casos

| caso | anchor (etapa 1) | unidad (etapa 2) | qué queda registrado | sensor solo / declaración |
|---|---|---|---|---|
| **editar** en el sitio | misma identidad; huella nueva; vigencia caída hasta re-registrar | misma identidad; soporte por revisar; **su verdad no cambia sola** (P4) | observación nueva del anchor | el sensor detecta la mutación; re-registrar es el acto que dice «visto» |
| **reformatear** | misma identidad; huella nueva, proyección igual | intacta | observación nueva | reparación sin decisión (ya existe: `stores update`) |
| **mover** a otra ruta | en la ruta vieja deja de resolver: borrado declarado (P8). En la nueva hay un candidato | misma identidad, con soporte perdido hasta re-anclar | si se declara: la **misma** identidad con localizador nuevo, y quién lo declaró | el sensor ve «falta aquí» y «candidato allá»; la continuidad es declaración |
| **copiar** | el original intacto; la copia es una identidad **nueva** | las unidades del original no se heredan | `(copied-from nuevo viejo)` solo si se declara | el sensor ve una segunda topología con igual huella: candidato, no afirmación |
| **dividir** uno en dos | el original termina (borrado declarado) salvo que se declare cuál parte lo continúa; las demás partes son nuevas | conservan identidad, con soporte por revisar | `(split-from parte original)` por cada parte nueva | el sensor ve que el original no resuelve y hay candidatos; qué parte continúa es declaración |
| **fusionar** dos en uno | la fusión es nueva, o una de las dos sobrevive por declaración; la absorbida termina | las de la absorbida conservan identidad y pierden soporte, declarado | `(merged-into absorbida resultado)` | igual: candidatos por huella, decisión declarada |
| **borrar** | termina, declarado; su historia queda | conserva identidad y relaciones; queda «sin soporte vigente» | el estado terminal | el sensor lo detecta solo |
| **misma proposición en dos fuentes** | dos anchors, cada uno con su vigencia | **dos** unidades (R8) | `(equivalent H1 H2)` si alguien lo declara | la similitud se puede proponer; la equivalencia es curaduría (P8) |

Caso que discriminaba en la síntesis anterior (dos fuentes sostienen lo mismo; cambia una):
dos anchors, dos unidades; la que cambió pide revisión, la otra sigue vigente; si estaban
declaradas `equivalent`, esa relación queda señalada para revisión. Ninguna cambia de verdad
sola.

## 5. B1–B4 del borrador anterior

| | qué preguntaba | resultado |
|---|---|---|
| **B1** | ante un movimiento, ¿se re-apunta el mismo anchor o hay anchors inmutables unidos por `(continúa …)`? | **Resuelto**: las dos cosas, según la aridad. Uno a uno: misma identidad (C1, de P2 y P8). Uno a varios o varios a uno: identidades nuevas con linaje (C2, de P7) |
| **B2** | ¿cómo nace el id? ¿el hecho se individúa por `(contexto, R, A, B)` o por id propio? | **Resuelto**: nace en el registro (R1); por id propio (P6). Individuarlo por su coordenada lógica lo haría cambiar al corregir `R`, `A` o `B`, contra P2. La codificación no es parte del contrato |
| **B3** | ¿el anchor es hoja de `(R A B)`? ¿un nodo con facetas o dos? | **Resuelto**: no es término (R7, de P3 y P5); son dos nodos (R6) |
| **B4** | ¿qué hechos son de S–M y cuáles de G? | **Resuelto para la identidad**: la regla es la misma en ambos. En S–M entran los hechos que el sensor detecta y re-verifica solo: existencia y vigencia de lo marcado, relaciones leídas de la topología, y linaje declarado. La lógica de verdad y sentido de lo demás es G (D-G), que el dueño dejó para después. El contrato de ejecuciones marcadas sigue «por especificar» según la cabecera de `14`: es diseño pendiente, no D0 |

## 6. Lo que queda

**Ambigüedad irreductible que exija al dueño: no encontré ninguna.** Hay dos puntos donde mi
derivación es más débil que en el resto. Los nombro con el valor por defecto que tomaría:

1. **Identidad de una relación declarada.** Aplico R1 también a los hechos `(R A B)`: dos
   declaraciones iguales son dos hechos, y deduplicarlas es curaduría. Se sigue de P5 (una
   relación es un hecho) y P6 (nodo con identidad propia), pero P6 habla de «unidad» y no dice
   explícitamente que una relación lo sea. La alternativa es que una relación se individúe
   por `(R, A, B)`. En S–M la diferencia casi no se nota; pesa en G.
2. **Quién puede declarar continuidad uno a uno.** El contrato exige una declaración con actor
   y evidencia. Si ese actor puede ser una regla mecánica (misma huella, candidato único, el
   original ya no está) es una política de regulación, no de identidad. Por defecto: el sensor
   propone y aplicar exige un acto explícito. Es lo que ya hace `stores reconcile [--apply]`.

## 7. Consecuencias que conviene decir ahora

- **El nivel documento ya cumple el contrato.** El id de árbol se acuña al registrar, no
  depende de contenido ni ruta, y sobrevive a editar. El hueco está debajo: los nodos de
  bloque y texto tienen id por contenido y las posiciones son estructurales. Eso es K1.
- **Direccionar contenido por hash sigue siendo válido para valores.** `14 §21` separa id del
  nodo, content, hash y embedding. El contrato prohíbe usar la dirección de un contenido como
  identidad de una unidad; no exige que los valores dejen de guardarse por hash **[I]**.
- **La identidad no se puede recomputar.** Si no sale del contenido ni del lugar, hay que
  persistirla. Reconstruir un store desde los archivos (`include build --rebuild`) hoy acuña
  ids nuevos: con este contrato eso **pierde** identidades y las relaciones declaradas sobre
  ellas, salvo que el registro identidad ↔ último estado conocido se lleve al store nuevo.
- **La identidad es local al store que la acuña** **[I]**, como el número lo es a la caja.
  Entre stores, que dos cosas sean la misma es `equivalent` declarado (P8).
- **La maquinaria existente encaja.** Estados de anclaje, sucesión registrada por transacción
  y propuestas de reconciliación (`docs/v2/02 §6`) ya separan «el sensor detecta» de «alguien
  declara».

## 8. Qué habría que corregir después, no ahora

Cuando el contrato esté revisado: la fila D0 de `docs/v2/drafts/preguntas-abiertas.md`; el
rótulo «D0 ABIERTO» y la lista «qué queda abierto» de `docs/v2/14 §21`; la decisión 46 de
`docs/v2/05-estado.md`; las filas de `docs/v2/12 §1` que «esperan a D0» (K1, K2b, S2, G1, resto
de P1, implementación de M1). `test/kimun/doc14_contract_test.cljc` fija varias de esas frases
y habría que cambiarlo en el mismo commit.

## 9. Lo que no hice

- No releí las fuentes externas de Zettelkasten: usé lo que el concilio ya había extraído.
  El texto de Schmidt (2016) sigue sin leerse.
- No revisé `docs/v2/drafts/fuentes-identidad.md` ni `ids-desde-matrix.md` para esta nota.
- No comprobé en el código cuántos lugares usan hoy un id de contenido como identidad.
