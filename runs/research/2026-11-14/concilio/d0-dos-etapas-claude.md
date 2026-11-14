# D0 desde dos etapas — Claude (2026-11-14)

Revisión documental. Sin implementación, sin tests, sin cambios de producto. Responde las preguntas de
`runs/research/2026-11-14/concilio/brief-d0-dos-etapas.md`.

Convención: **[D]** = dicho por el dueño (textual en el brief). **[F]** = dicho por una fuente citada.
**[I]** = inferencia mía.

## 0. Respuesta corta

**D0 queda parcialmente desbloqueada.** Lo conceptual se destraba: la pregunta ya no es «¿identidad =
lugar o hash?» para un solo tipo de nodo, sino qué es cada uno de dos nodos distintos y dónde se evalúa
la verdad. Lo que sigue bloqueado es un punto preciso: **la regla de continuidad cuando la realidad
cambia bajo un anchor** (§5). Sin ella no se puede escribir K1.

## 1. Qué dijo el dueño y qué dicen las fuentes

**[D]** Cuatro afirmaciones:

1. El mundo es el conjunto de los hechos, no de las cosas.
2. Los hechos son proposiciones con relación a la realidad; el valor de verdad está en la realidad.
3. Una proposición es un trío `(R A B)`, con `R` relación entre los hechos `A` y `B`.
4. Realidad y hecho son cosas distintas; son dos etapas: anchoring y hecho detectado.

**[F] Tractatus** (`/home/jp/proyectos/wittgenstein/Matrix/Old/tractatus/truth_machine/tractatus_tree_de.json`):

| nº | texto | qué respalda |
|---|---|---|
| 1.1 | «Die Welt ist die Gesamtheit der Tatsachen, nicht der Dinge.» | afirmación 1, literal |
| 2 | «Was der Fall ist, die Tatsache, ist das Bestehen von Sachverhalten.» | un hecho es que algo se da |
| 2.06 | «Das Bestehen und Nichtbestehen von Sachverhalten ist die Wirklichkeit.» | la realidad incluye lo que **no** se da: realidad ≠ hecho |
| 2.223 | «Um zu erkennen, ob das Bild wahr oder falsch ist, müssen wir es mit der Wirklichkeit vergleichen.» | la verdad se evalúa comparando con la realidad |
| 2.224 | «Aus dem Bild allein ist nicht zu erkennen, ob es wahr oder falsch ist.» | registrar no establece |
| 4.06 | «Nur dadurch kann der Satz wahr oder falsch sein, indem er ein Bild der Wirklichkeit ist.» | afirmación 2 |
| 2.141, 3.14 | «Das Bild ist eine Tatsache.» / «Das Satzzeichen ist eine Tatsache.» | lo registrado es a su vez un hecho |

**Lo que el Tractatus no dice.** 2.01: «Der Sachverhalt ist eine Verbindung von Gegenständen», y 2.02:
«Der Gegenstand ist einfach». Allí se conectan **objetos** simples, no hechos; la proposición
elemental es una concatenación de nombres (4.22), no un trío. El esquema `(R A B)` con `A` y `B`
hechos, y por tanto recursivo, es **del dueño para kimun**. Tampoco identifica hecho con
proposición: la proposición es imagen (4.01).

**[F] Paper del dueño** (`/home/jp/proyectos/wittgenstein/Matrix/paper_v2/sections/`):

- `02_philosophical_foundation.md`: «A proposition is true or false not based on the fact itself, but
  on its relationship with reality». «The sign is not the fact itself, but rather a mapping of the fact».
- `03_proposed_representation.md`: un hecho con coordenada definida se extiende así: «find the
  coordinate and set its value to true». El hecho tiene **coordenada** en un espacio lógico `L_i` de un
  contexto; verdad (`V_i`) y sentido (`S_i`) son capas aparte.

**[F] Zettelkasten.** Según el artículo de Wikipedia (es), cada nota tiene un identificador propio y fijo,
y se enlaza con otras. En «Kommunikation mit Zettelkästen», Luhmann lleva además un aparato
bibliográfico separado: las referencias a lo leído van en fichas distintas de las notas con ideas
propias. Ninguna de las dos fuentes establece identidad temporal. El tutorial del archivo
(`niklas-luhmann-archiv.de/.../tutorial`) **no lo pude leer**: la página se sirve vacía sin JavaScript.

## 2. Qué queda resuelto

**[I]** Cuatro cosas, leyendo las dos etapas contra las fuentes:

1. **Las cosas de la realidad no son nodos.** Si el mundo son hechos y no cosas, kimun no necesita
   identificar «la función» ni «el archivo» en sí. El dilema original de D0 («¿es la misma función si
   se movió?») era una pregunta sobre una cosa. Deja de ser una pregunta de identidad.
2. **Hay dos nodos distintos, uno por etapa.** El anchor apunta a la realidad; el hecho dice algo sobre
   lo anclado. Coincide con Zettelkasten: ficha de referencia a la fuente y nota propia.
3. **El hash queda fuera de la identidad en ambas etapas.** Es el instrumento que avisa que la
   realidad bajo un anchor cambió. Coincide con la corrección anterior del dueño (cabecera de
   `docs/v2/14-juego-del-lenguaje.md`).
4. **La verdad no vive en el nodo.** Un hecho detectado no es verdadero por estar registrado
   (2.224; `docs/v2/00-nucleo.md` 4.3). Se evalúa contra la realidad, a través de los anchors.

## 3. Identidad de cada etapa y cómo se conectan

| | etapa 1: anchor | etapa 2: hecho detectado |
|---|---|---|
| qué es | una referencia puesta sobre la realidad | una proposición `(R A B)` en un contexto **[D]** |
| atributos, no identidad | localizador (ruta, selector), contrato de observación, huella | quién o qué lo detectó, cuándo, sobre qué observación |
| identidad **[I]** | propia del registro de kimun, dada al anclar; no es la ruta ni el hash | su coordenada lógica: contexto, `R`, `A`, `B` (paper 03) |
| análogo Zettelkasten | ficha de referencia a la fuente | nota con número fijo |

**Conexión [I].** `A` y `B` son nodos: anchors u otros hechos; de ahí la recursión. El anchor llega a la
realidad por localizador y contrato. Cuando el heartbeat ve que la huella cambió, los hechos que
dependen de ese anchor quedan por reevaluar; no quedan falsos.

Esto reconcilia dos frases que parecían opuestas. «Identidad no es anclaje topológico» (dueño, 11-14)
habla del lugar **en el ambiente**. «Identidad = lugar semántico para M/G» (D0 vieja) puede leerse
como lugar **en el espacio lógico**. La primera se retira; la segunda sobrevive solo para el hecho.

**Punto sin decidir.** El dueño dice que `A` y `B` son hechos. Si el anchor no es un hecho, es el
término base de la recursión. Si lo es, sería el hecho elemental «esto está ahí». Propongo **[I]**: el
anchor es referencia, y «el anchor resuelve y está intacto» es un hecho detectado sobre él. Las
señales de `stores sense` ya tienen esa forma.

## 4. Criterios de verdad

| | anchor | hecho detectado |
|---|---|---|
| ¿tiene valor de verdad? | no **[I]**: una referencia no afirma | sí, y está en la realidad **[D]** |
| qué se verifica | que resuelve bajo su contrato y que la huella coincide (vigencia) | primero sentido en su contexto (`00-nucleo.md` 4.1); luego comparación con la realidad (2.223) |
| estados | los de `docs/v2/02-sustrato-computacional.md §6.2` | no los fijo: el eje de verdad es D-G, abierta (`00-nucleo.md` 4.2) |
| cuándo | en cada latido | en cada observación; un cambio de huella pide reevaluar |

**[I]** En S–M solo son evaluables mecánicamente los hechos cuyo detector se puede volver a correr:
estructurales («el archivo contiene esta función») y, si se marcan ejecuciones, de resultado («este
test pasa sobre este código»). Son las dos verificaciones que el dueño ya nombró. Evaluar hechos que
requieren juicio es G.

## 5. Casos

| caso | anchor | hechos | qué falta |
|---|---|---|---|
| se edita el cuerpo de una función | huella distinta | por reevaluar | nada |
| la función se mueve de archivo | el anchor viejo no resuelve; aparece algo con igual huella | huérfanos de soporte | **la regla de continuidad** |
| se copia idéntica a otro archivo | el viejo sigue intacto; hay otro lugar con igual huella | intactos | nada: igual huella no es continuidad |
| se borra | no resuelve | por reevaluar; algunos pasan a no darse | nada |
| cambia el contrato de observación | huella distinta sin cambio de fuente | por reevaluar | distinguir `contract-changed` (K6) |
| el mismo `(R A B)` se detecta dos veces | — | ¿uno o dos? | individuación del hecho |

**El punto que bloquea.** En el caso «se mueve», ¿el anchor se reapunta y sigue siendo el mismo, o
nace otro? Propuesta **[I]**, para contrastar en el concilio antes de llevarla al dueño: el anchor
no se reapunta nunca; la continuidad es **un hecho más**, `(continúa A-viejo A-nuevo)`, declarado o
detectado como candidato, y evaluable como cualquier otro. Así la igualdad de hash propone y no
prueba, y dividir o fusionar son varios hechos de continuidad. Si el dueño lo confirma, D0 queda
desbloqueada entera.

## 6. Qué implementación queda autorizada

El dueño preguntó «¿destrabamos el bloqueo entonces?»; no declaró D0 cerrada. Autorizar es suyo. Lo
que **[I]** se sigue sin inventar formato de ID ni inferir verdad:

- **Sí:** separar D0 en los documentos en «anchor» y «hecho»; K6, K8 y K2a, que ya no esperaban a D0
  (`docs/v2/12-ecosistema-y-pendientes.md §1`); observaciones de solo lectura como la del §8.
- **No todavía:** K1 (ids en el kernel), K2b, S2, persistir hechos o su valor de verdad, G1.

## 7. Contradicciones con backlog y spec

| path / sección | dice | choque |
|---|---|---|
| `docs/v2/12-ecosistema-y-pendientes.md §1`, fila D0 | una sola identidad: «lugar (estructural para S, semántico para M/G)» | hay dos nodos; el lugar en el ambiente es atributo del anchor |
| ídem, fila K1 | «ids por lugar» | K1 se parte en dos: anchors y hechos |
| ídem, fila G1 | los hechos son de la capa G y esperan a D0, D-G, M1 | el dueño pone «hecho detectado» como etapa 2 mientras se trabaja S–M |
| `docs/v2/14-juego-del-lenguaje.md §19` | «capa G (hechos, contextos)» solo en el grafo | la misma tensión: falta decir qué hechos son de S–M |
| `docs/v2/14-juego-del-lenguaje.md §21` | «ID del nodo = identidad (nombre, ruta, símbolo)» | mezcla localizador del anchor con identidad |
| `docs/v2/02-sustrato-computacional.md §6` | «Identidad = hash» | contradice ambas etapas |
| `runs/research/2026-10-13/concilio/produccion/spec-final-candidato.md §5.4` | ruta cualificada «candidato para identidad de documento» | la ruta es localizador |
| `docs/v2/drafts/preguntas-abiertas.md`, cabecera | «no se considera… desbloqueada su implementación» | sigue vigente; falta registrar las dos etapas |

Sin choque: `00-nucleo.md` 1.2 (toda proposición pertenece a un contexto) y 4.3.

## 8. Primer slice verificable S–M (propuesta, no implementada)

Una observación de solo lectura sobre un archivo de código ya trackeado, en la línea de `stores sense`:

1. **Anchors:** por cada función marcada, su localizador, contrato y huella, y si resuelve.
2. **Hechos detectados:** solo estructurales, por ejemplo «el archivo contiene la función».
3. **Nada se persiste y no se acuña ningún ID:** dentro de una observación los nodos se nombran con las
   etiquetas que ya existen, declaradas como etiqueta y no como identidad.

Verificable con tres casos del §5: al editar, la huella cambia y el hecho se vuelve a detectar; al
borrar, el hecho deja de detectarse; al copiar, aparecen dos anchors con igual huella y **ningún**
hecho de continuidad. No cubre mover, que depende del punto abierto.

## Límites

- No leí el tutorial del archivo Luhmann. Lo de las dos colecciones de fichas viene del ensayo de
  Luhmann leído para `zettelkasten-claude.md`, en traducción.
- Del Tractatus cité el alemán del árbol local; las glosas son mías.
- No revisé código ni corrí nada.
