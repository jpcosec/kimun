# D0 desde dos etapas — respuesta de Pi

> Read-only. No implementación ni tests. Distingo **palabras del dueño**, **fuentes** e
> **inferencias propias**. El esquema `(R A B)` del dueño es su representación para kimun; no lo
> atribuyo literalmente al *Tractatus*.

## 1. Palabras textuales del dueño

Del brief `runs/research/2026-11-14/concilio/brief-d0-dos-etapas.md`:

> «Mira, creo que el problema de identidad y nodo esta resuelto en el tractatus de wittgenstein. El
> principio 1 es que el mundo es el conjunto de todos los hechos, no de todas las cosas. Los hechos
> son proposiciones que tienen alguna relacion con la realidad. El valor de verdad de una proposicion
> esta en la realidad. Una proposicion es una trio (R A B) donde R es una relación entre los hechos A
> y B. entiendes a lo que voy?»

> «Es que realidad y hecho son cosas distintas»

> «son 2 etapas. 1- anchoring y 2- el hecho detectado. revisa zettelskasten pa entender»

> «pulento, destrabamos el bloqueo entonces? conversa con el concilio, enviale explicitamente lo que te dije y las fuentes para que entiendan»

## 2. Lo que dicen las fuentes

- **Tractatus local** (`/home/jp/proyectos/wittgenstein/Matrix/Old/tractatus/truth_machine/tractatus_tree_de.json`):
  - **1.1**: «Die Welt ist die Gesamtheit der Tatsachen, nicht der Dinge.»
  - **2**: «Was der Fall ist, die Tatsache, ist das Bestehen von Sachverhalten.»
  - **2.01**: «Der Sachverhalt ist eine Verbindung von Gegenständen.»
  - **2.0272**: «Die Konfiguration der Gegenstände bildet den Sachverhalt.»
  - **4.01**: «Der Satz ist ein Bild der Wirklichkeit.»
  - **4.06**: «Nur dadurch kann der Satz wahr oder falsch sein, indem er ein Bild der Wirklichkeit ist.»
  - **4.062**: «wahr ist ein Satz, wenn es sich so verhält, wie wir es durch ihn sagen.»
- **Paper del dueño** (`.../paper_v2/sections/02_philosophical_foundation.md`): «A proposition is a
  description of a fact in relation to reality… A proposition is true or false not based on the fact
  itself, but on its relationship with reality.»
- **Zettelkasten**: la Wikipedia describe notas individuales numeradas, con referencias cruzadas y
  etiquetas; no define identidad temporal. La introducción metodológica primaria
  (`https://zettelkasten.de/introduction/`) insiste en que «each Zettel needs a unique address» para
  poder enlazarlo y que el valor está en las conexiones, no en la colección. El tutorial del archivo
  Luhmann (`https://niklas-luhmann-archiv.de/bestand/zettelkasten/tutorial`) requiere JavaScript y no
  entregó texto extraíble; no le atribuyo un contrato que no pude leer.
- **Contrato actual**: `docs/v2/14-juego-del-lenguaje.md` cabecera 2026-11-14: identidad no es hash
  ni anclaje topológico; Merkle sensoriza; dos verificaciones (vigencia de lo marcado y, si se marcan
  ejecuciones, verdad del resultado); S–M antes de G.

## 3. Respuestas

### 3.1 ¿Qué queda resuelto conceptualmente de D0?

**Dicho por el dueño:** realidad y hecho son distintos; hay dos etapas, anchoring y hecho detectado;
la verdad de la proposición se evalúa en la realidad.

**Inferencia Pi:** lo resuelto es la **separación de planos**, no la fórmula positiva de identidad.
Queda claro que:

1. El **anchor** es la unidad marcada/soportada en una fuente; su vigencia se mide contra una huella.
2. El **hecho detectado** es una proposición `(R A B)` en un contexto; su verdad se evalúa contra la
   realidad, no contra el anchor.
3. La **realidad** no es el conjunto de hechos registrados: es el dominio contra el que una
   proposición se contrasta. Un hecho detectado no “es” la realidad; es una proposición sobre ella.

Esto elimina la necesidad de que un solo nodo cargue a la vez la identidad del anchor, la del hecho y
la verdad. Lo que **no** queda resuelto es la identidad positiva del anchor ni la del hecho, ni su
continuidad temporal. La pregunta D0 (`docs/v2/drafts/preguntas-abiertas.md`) sigue abierta en ese
punto, y la auditoría lo dice: «nadie define la identidad en positivo» (`auditoria-spec.md §5`).

### 3.2 ¿Qué identidad tiene el nodo de cada etapa y cómo se conectan?

**Dicho por el dueño:** hay dos etapas; no dice qué ID ni qué formato.

**Inferencia Pi:**

- **Nodo de anchoring:** identidad de la **unidad marcada** en la fuente. No es el hash de su
  contenido ni su posición topológica (cabecera de `14`). Sigue pendiente cómo se asigna y cómo se
  conserva al editar/mover. Zettelkasten aporta la exigencia de dirección única para poder enlazar,
  pero **no** define continuidad temporal.
- **Nodo de hecho detectado:** identidad de la **proposición en su contexto** (`(R A B) @ W_i`), no
  del texto que la soporta. Su verdad se evalúa en la realidad; su identidad no se sigue del anchor.
- **Conexión:** una relación explícita de **soporte/evidencia** entre anchors y hecho detectado. No
  es una relación de identidad ni de derivación de verdad: un anchor puede soportar varios hechos y
  un hecho puede apoyarse en varios anchors. Un anchor vigente no hace verdadero el hecho; un anchor
  roto no lo hace falso, solo deja el soporte en revisión.

Esto es compatible con `00-nucleo.md 2.3–2.4` (anchor verificable; si el soporte muta, se revisa) y
con `4.3` (registrar una afirmación no la establece).

### 3.3 ¿Qué criterios de verdad corresponden al anchor y al hecho?

- **Anchor:** no hay verdad proposicional; hay **vigencia/soporte**. Criterio: la fuente y la
  proyección marcada siguen coincidiendo con la huella registrada; si no, `mutated`/`missing`/
  `contract-changed`. Es la verificación 1 del dueño.
- **Hecho detectado:** hay **verdad en la realidad** (palabras del dueño; TLP 4.06/4.062; paper §2).
  Antes de verdad hay sentido/contexto: `sinnvoll`, `sinnlos`, `unsinnig` (`00-nucleo 4.1`). El
  anchor solo aporta evidencia; no sustituye la evaluación en la realidad.
- **No confundir:** un hecho detectado con evidencia válida no es automáticamente verdadero; una
  huella válida solo acredita que lo marcado no cambió.

### 3.4 ¿Qué implementación concreta queda autorizada?

**Autorizado conceptualmente:** separar los registros/interfaces de **anchor** y **hecho detectado**,
y hacer explícita la relación de soporte. No autoriza:

- inventar un formato de ID;
- decidir que el ID del anchor es el del hecho;
- persistir un valor de verdad por el hecho de tener un anchor;
- inferir verdad desde una huella o desde una ejecución correcta.

**Primer slice S–M verificable (propuesta, no implementación):**

- **Slice A — anchoring:** observación de una unidad marcada con `source`, selector/membrana,
  contrato de parse y huella; estados `ok/mutated/missing/contract-changed`; reutilizar los handles
  actuales de documento/sección/span, sin fijar un ID público nuevo.
- **Criterio de aceptación:** editar la fuente produce `mutated`; borrar produce `missing`; cambiar
  parser/selector produce `contract-changed`; ningún caso escribe un valor de verdad ni crea un hecho.
- **Fuera del slice:** identidad positiva/continuidad definitiva del anchor, hecho detectado y verdad.
  Eso queda para D0/D-G y G.

## 4. Clasificación de D0

**Parcialmente desbloqueada.** El eje **realidad ≠ hecho ≠ anchor** queda resuelto conceptualmente y
habilita el slice de anchoring y la separación anchor/hecho. No queda **completamente** desbloqueada
porque faltan:

- identidad positiva y continuidad del anchor (`ambiguedades.md` A2.1–A2.2);
- identidad del hecho detectado y su individuación en contexto;
- regla explícita de conexión soporte/hecho y de revisión cuando el anchor muta.

No es “bloqueada por un punto” único: es un avance de contrato con dos cabos abiertos. Llamarla
“destrabada” sin matiz sobrestima lo que el dueño dijo: él dio la dirección, no el formato.

## 5. Contradicciones con backlog y spec

- `docs/v2/12 §1` sigue diciendo D0 = «identidad = lugar» y K1 = «ids por lugar»; la cabecera de `14`
  lo contradice. Hay que corregir ambas filas antes de K1.
- `docs/v2/05-estado.md` decisión 46 y `02 §6`/`01 §4.3` mantienen identidad por contenido/hash; la
  cabecera de `14` ya no lo autoriza.
- El spec/roadmap anterior (`runs/research/2026-10-13/concilio/produccion/`) conserva K1 por lugar y
  `spec §5.4` “ruta cualificada” como candidata a identidad; la auditoría los marca como
  contradictorios (`auditoria-spec.md §2–§3`).
- `14 §21` y `drafts/ids-desde-matrix.md` siguen leyéndose como “identidad = lugar”; la corrección
  actual los supera en esa parte.
- El backlog no tiene una fila para “anchor vs hecho detectado”; hay que añadirla antes de K1/G1.

## 6. Límites

No atribuyo al *Tractatus* el esquema recursivo `(R A B)` con R entre hechos A y B: TLP habla de
objetos, estados de cosas, hechos y proposiciones como figuras de la realidad. La tríada es la
representación del dueño para kimun. Tampoco atribuyo a Zettelkasten una teoría de identidad temporal:
solo aporta direccionabilidad, referencias a fuentes y vínculos cruzados. Sin implementación ni tests.
