# SLDB v2 — Orden filosófico

> De dónde se decanta la arquitectura. Este documento no describe mecanismos;
> describe qué es el objeto que se persiste y qué garantías tiene sentido pedirle.
> El sustrato que lo construye está en `02-sustrato-computacional.md`.

Fuentes: `raw/source/ecosystem/SMG_DEFINITIONS.md`, `raw/source/ecosystem/matrix-tractatus-intro.md`,
`raw/source/ecosystem/sldb-v1-smg-ir.md`, y la conversación de diseño del 2026-08-29.

---

## 1. Es una base de datos sobre lenguaje, no sobre documentos

SLDB v1 era una capa de persistencia sobre Markdown: el archivo era a la vez superficie de
trabajo y almacenamiento, y por eso necesitaba round-trip textual exacto. El refactor
intermedio (`raw/source/core/`) movió la autoridad a un grafo canónico, pero siguió
pensando el objeto persistido como *documento*.

v2 cambia el objeto. La unidad no es el documento sino **el lenguaje estructurado a
cualquier escala**: documento, sección, párrafo, oración, símbolo. La representación debe
*poder* llegar hasta la unidad más básica —símbolos y gramática por el lado estructural,
hecho y relación por el lado semántico— sin obligar a materializarla.

Un documento pasa a ser una de las muchas formas de indexar ese lenguaje.

## 2. Signo y símbolo (Tractatus)

El sustrato conceptual es el de Matrix / Tractatus Knowledge Machine:

- **Signo**: el medio perceptible. Un texto, un token, un bloque de Markdown, una
  celda de PDF. Un hecho puede expresarse con muchos signos.
- **Símbolo**: el signo *dentro de un espacio lógico*, en un contexto suficientemente
  determinado para distinguirlo de otros. "El cielo es azul" es un símbolo; las frases
  A, B, C, Z que lo expresan son signos.
- **Hecho**: una relación entre símbolos que es verdadera o falsa *respecto de la
  realidad* dentro de un contexto `W_i`.

La consecuencia arquitectónica es directa: **el nodo es el símbolo, no el signo**. El
grafo no tiene un nodo "el cielo es azul en el texto A"; tiene un nodo "el cielo es azul"
que A, B, C y Z referencian. Muchos signos → un símbolo. Un nodo está indexado por
distintos árboles.

## 3. S / M / G como capas del modelo persistido

El ecosistema Hum ya define la trinidad (`SMG_DEFINITIONS.md`); v1 la tenía como IR en
memoria (`R(T) = (S, M, G)`). v2 la convierte en el **modelo de persistencia**:

| Capa | Qué contiene | Qué es en el kernel |
|---|---|---|
| **S** — Surface | signos: texto, spans, árboles sintácticos concretos, anclajes a lo externo | nodos de superficie + árboles-índice de documento |
| **M** — Meaning | símbolos: proposiciones en forma canónica, forma lógica | nodos de significado + `binding` desde S |
| **G** — Graph | hechos validados en un contexto `W_i`; triples; matrices de verdad/sentido | nodos de hecho + `projection` desde M |

Las tres capas comparten **un mismo pool de nodos**. S, M y G no son tres almacenes que
se exportan entre sí: son tres clases de nodo y tres familias de índice sobre el mismo
sustrato. Esto revisa el ADR `sldb-text-layer-vs-kgdb-graph-layer.md` (SLDB = S, KGDB = G
como stores separados): en v2 un índice G es *otro árbol* sobre el mismo pool.

## 4. Principios

1. **El pool de nodos es la fuente de verdad.** Ningún formato externo (Markdown, Lisp,
   YAML, PDF) es autoridad. Todo se parsea hacia el pool y se proyecta desde él.
2. **Nada existente se modifica.** Toda mutación produce una revisión nueva con
   transacción, actor, motor y provenance.
3. **La identidad de un nodo es su contenido.** Dos signos idénticos son el mismo nodo;
   dos símbolos con la misma forma canónica son el mismo símbolo. La identidad *a través
   de ediciones* no es identidad: es **sucesión**, y se registra, no se adivina.
4. **Los árboles son índices.** Documento, sección, taxonomía, árbol sintáctico, contexto
   `W_i`: todos son conjuntos de aristas sobre el pool. Un nodo tiene un padre *por
   árbol*, no un padre global.
5. **Lo derivado es reconstruible y no tiene autoridad.** Índices, caches, embeddings,
   proyecciones, parses lingüísticos: todo se recalcula desde el log y el pool.
6. **Lo determinista es canónico; lo que sale de un motor es proyección; lo que afirma
   alguien es transacción.** Fronteras de grafema/palabra/oración (Unicode UAX #29) y
   markup son deterministas. Sintaxis, morfología y extracción de hechos dependen de un
   motor y una versión. Una relación afirmada por un humano o un agente tiene autor.
7. **La herramienta sabe sus límites.** No todo lenguaje estructurado es dato tratable.
   Cada nodo declara su clase de direccionabilidad; las regiones opacas se conservan y
   se anclan por evidencia, pero no se operan.

## 5. Qué garantías tiene sentido pedir

La reversibilidad del modelo canónico sobre las superficies **se va a romper**, y el
diseño debe asumirlo en vez de prometerla:

- **Dentro de S**: `texto ↔ árbol sintáctico concreto` es reversible por construcción
  (cada byte pertenece a un nodo). Garantía fuerte.
- **S → M**: es un *mapeo bajo contexto*, determinista dado `(signo, W_i, motor)`, pero
  el mismo signo mapea a símbolos distintos en contextos distintos ("gift" en inglés y
  alemán). Garantía condicional, con evidencia.
- **M → S**: **nunca es una inversa.** Un símbolo tiene N signos; elegir uno es generar
  (*deconversion*), no invertir. No hay garantía de round-trip, por diseño.
- **M → G**: consolidación validada (sinnvoll / sinnlos / unsinnig). Un hecho en G
  conserva vínculo indestructible con su origen en S.

La garantía que sí se puede sostener, y que sustituye a "round-trip exacto", es:

> **Reversible en S, trazable a través de S → M → G, generativo de vuelta.**

Lo que hay que exigir en lugar de reversibilidad es **transparencia** ("qué está anclado
en qué") y **detección de mutaciones**. Ambas son consecuencias directas de los principios
3 y 4: si los nodos son inmutables y content-addressed, una mutación es que un árbol ahora
apunta a otro nodo donde antes apuntaba a éste, y eso se detecta por comparación de hashes
entre revisiones — de forma exacta, sin heurísticas.

## 6. Dónde termina la tokenización

Por debajo del párrafo, el árbol deja de servir como estructura única: oraciones,
sintaxis y menciones **no anidan** con el markup (un énfasis puede cruzar dos oraciones;
una entidad puede estar partida por un `Strong`). Son jerarquías superpuestas sobre la
misma secuencia de caracteres.

La respuesta es *stand-off annotation*: la hoja canónica es la secuencia de símbolos de
un párrafo, y todo lo demás —markup inline, oraciones, tokens, sintaxis, menciones,
hechos— son capas de spans tipados sobre ella. Por eso "es solo tokenización" es cierto
en la base y falso en el borde: la base es una secuencia con offsets; el trabajo está en
(a) el emisor inverso-correcto por formato, (b) el modelo estructural neutro entre
formatos (referencia: el AST de pandoc, con `Raw` como escotilla), y (c) la sucesión de
nodos a través de ediciones.

## 7. Lo que esto retira del diseño anterior

- "Round-trip exacto de Markdown" y "árbol sintáctico lossless (Rowan)" como requisito
  del kernel: se sustituyen por la garantía de §5.
- La división binaria *familia reversible / no reversible*: pasa a ser un default por
  familia sobre una clasificación **por nodo** (`structural` / `opaque` / `external`).
- "Documento" como raíz de autoridad: pasa a ser un tipo de árbol-índice.
- Lisp como metalenguaje separado: Clojure ya es el Lisp; lo que se conserva es el
  boundary de *planes como datos* (ver `02`).
- Rust como lenguaje del kernel: se consumen artefactos WASM hechos en Rust (tree-sitter,
  BLAKE3), no se escribe Rust.
- "Identidad y reconciliación" como problema abierto: se descompone en identidad (hash,
  trivial) y sucesión (arista registrada en la transacción; heurística solo para cambios
  externos, marcada como tal).

## 8. Bucle autopoiético

Los estados de anclaje `drifted` y `orphan` (ver `02`, §6) son exactamente los eventos que
el ciclo D de SMG (G/M → S) debe convertir en registros nuevos en S: preguntas, tareas,
alertas. La detección de mutaciones no es solo integridad; es la señal que cierra el
bucle de mantenimiento del conocimiento.
