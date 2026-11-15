# D0 — segunda lectura de la síntesis v2 (Pi)

Solo lectura. **[D]** palabra del dueño (cito textual), **[F]** fuente, **[I]** inferencia mía.
Corrijo primero mi contradicción: en mi tabla «mover» decía *el mismo* y en §2.2 decía *nuevo sin
`continues`*; vale lo primero (mismo hecho detectado con anclaje nuevo declarado), §2.2 estaba mal.

## Punto 1 — ¿un hecho detectado estable o uno por observación?

**Veredicto: la lectura A (v2) es la más fiel, pero su formulación actual es demasiado fuerte y
tiene un contraejemplo que la rompe; necesita una frase de individuación bajo cambio.**

**(a) Texto que decide.** A favor de A:
**[D]** «La unidad es un nodo con identidad propia, un espacio mínimo donde convergen varias
dimensiones» (cabecera, 2026-11-14) — si cada lectura fuera un hecho, el nodo sería la lectura, no
la unidad.
**[D]** «identificar las topologías marcadas y **seguir sus cambios en el tiempo**, con niveles de
abstracción» (ídem): lo que se sigue es la topología, no el acto de leer.
**[D]** «lo marcado no cambió respecto de la huella registrada, **afirmación** que deja de
sostenerse ante una mutación»: el dueño llama *afirmación* a la comprobación, no al hecho; la
afirmación es la observación, y tiene historia.
En contra de A y a favor de B: **[D]** «Los hechos son proposiciones que tienen alguna relación con
la realidad. El valor de verdad de una proposición está en la realidad». Bajo A, el hecho estable
—«hay una topología marcada bajo este anclaje»— es verdadero siempre que *haya algo* ahí; no puede
ser falso salvo por borrado. Una proposición que sólo puede dejar de existir, no de ser verdadera,
no es la que el dueño describe. La proposición evaluable es la **observación** («se leyó π»), y A lo
reconoce al llamarla afirmación. V2 ya tiene las dos piezas; lo que hace es separarlas, y eso es
correcto.

**(b) Contraejemplo que rompe A tal como está escrita.** I4 dice: editar, reformatear o releer dan
observaciones **del mismo** hecho. Caso mínimo: en un intervalo, el archivo se **borra** y en la
misma ruta se crea otro con contenido no relacionado; el sensor ve «falta» y luego «presente», y una
declaración re-ancla el **mismo** hecho al fichero nuevo (I6). Resultado: un hecho cuya primera
observación sostenía una topología y cuya última sostiene otra sin relación, con historia que dice
«el mismo». Eso es indistinguible de «mover y editar», y choca con P4 (la afirmación anterior no se
sostiene) sólo si además se declara la discontinuidad: hoy v2 sólo exige «anclaje nuevo». Rompe
también **[D]** «Realidad y hecho son cosas distintas»: un hecho que atraviesa dos realidades
inconexas deja de estar *sobre* ninguna. Lo mismo con una reescritura total: 0 % de solapamiento.

**(c) Relaciones declaradas sobre ese hecho.** Ninguna cambia de verdad sola (P4): quedan apuntando
al hecho y piden revisión. En el caso del párrafo anterior, quedan sostenidas por un hecho que ya no
es el que las justificaba, y v2 no tiene señal para eso.

**La frase que falta (si las premisas no deciden):** una regla de **individuación bajo cambio** —
«un cambio que *reemplaza* el ámbito marcado es otro hecho detectado; un cambio que lo *edita*
dentro de su ámbito es el mismo». El dueño no la dijo: P4 habla de la afirmación, no del hecho.
Mientras no exista, A debe al menos exigir que la declaración de continuidad nombre la
discontinuidad observada (borrado+alta, sustitución total) y no sólo el anclaje nuevo.

Contra B (uno por observación): con `stores heartbeat`, un store sin cambios produce un hecho nuevo
cada 30 s; las relaciones declaradas sobre el anterior habría que migrarlas en cada latido, y el
mundo ([D] «el mundo es el conjunto de todos los hechos») pasaría a ser el conjunto de las
**lecturas**. B no puede distinguir «no cambió nada» de «no se leyó».

## Punto 2 — base de la recursión y terminación

**[D]** «Una proposición es un trío `(R A B)` donde `R` es una relación entre los hechos `A` y `B`»
y **[D]** «Los hechos son proposiciones…». Bajo esas dos frases, **A de v2 (hechos que no son
tríos) contradice al dueño**: si todo hecho es proposición y toda proposición es trío, no hay hechos
sin trío. **B al pie de la letra no termina**: si `A` y `B` son hechos, `(anclado-en X Y)` exige sus
propios anclajes, sin fondo, o un ciclo.

**La lectura que defiendo (C) y su axioma de terminación.** Todo hecho es un trío; lo que cambia es
la **especie de los términos**, que ya está en v2 cuando dice que el anclaje no es un nodo:

> **Axioma de terminación.** Hay dos especies de término: **hecho** (nodo, con identidad propia,
> siempre un trío) y **designador** (referencia a la realidad: ruta, contrato de lectura, extensión,
> huella, valor literal; **sin identidad de nodo**).
> 1. Todo hecho es `(R A B)`; `A` y `B` son hechos o designadores, nunca «cosas».
> 2. Un hecho con algún designador es **base**; los designadores son hojas y no se anidan.
> 3. La firma de `R` fija las especies (anclaje: hecho×designador o designador×designador;
>    proyección y jerarquía: hecho×hecho). Ningún hecho es término de sí mismo.
> 4. El rango de un hecho es 1 + el máximo rango de sus términos-hecho; los designadores tienen
>    rango 0. Toda declaración introduce un hecho cuyos términos ya existen (P5), así que el rango
>    es finito y crece con el orden de declaración: la recursión es bien fundada.

Esto conserva la letra del dueño (todo hecho es trío), conserva v2 (el anclaje no es nodo) y
termina. La base es `(anclado-en <designador> <valor>)`: el valor (huella, contrato, extensión) es
un literal del designador, no un nodo.

**El argumento de finitud (del coordinador) y la elección.** Si *todo* hecho es un trío y `A` y
`B` son *siempre* hechos, en un conjunto **finito** de hechos cada nodo tiene al menos una arista de
salida hacia otro hecho; una cadena finita se cierra: hay ciclo. No existe la cadena descendente
infinita en un store, así que la letra «todo es trío de hechos» no es terminante. Las salidas son
tres, y elijo **una**:

> **Regla operativa para S–M (C) — [I] inferencia de diseño, no palabra del dueño.** Todo hecho
> es un trío. Hay dos especies de término: **hecho** (nodo, con identidad propia, trío) y
> **designador** (ruta, contrato de lectura, extensión, huella, valor literal; **sin identidad de
> nodo**). El caso base usa designadores: `(anclado-en <designador> <valor>)`. La firma de cada `R`
> fija las especies de sus términos, y el rango (designador 0; hecho 1 + máximo de sus términos-hecho)
> es finito porque toda declaración introduce un hecho cuyos términos ya existen.

Esto **no se deriva** de las palabras del dueño: usa su «todo hecho es proposición/trío» y su
separación realidad≠hecho, pero **los átomos son invención de diseño** de Pi, coherente con que v2
diga que el anclaje no es nodo.

**Qué se pierde con las otras dos.**
- **(a) hechos base que no son tríos:** gana en simplicidad (la base es un hecho y se acabó), pero
  pierde la letra del dueño dos veces («los hechos son proposiciones», «una proposición es un
  trío») y deja el hecho más elemental fuera del lenguaje de relaciones: el sensor no puede escribir
  «lo marcado cambió» como `(R A B)`. Es la lectura de v2 y es la que ataco.
- **(b) ciclos o definición coinductiva:** admite el trío de hechos sin excepción, pero pierde la
  inducción estructural (no hay rango, no hay prueba de terminación), permite que un hecho se
  soporte a sí mismo —el sensor no puede distinguir soporte de auto-soporte— y hace indecidible
  «qué hechos necesita este hecho». Para S–M, donde el sensor debe decidir qué recalcular, es peor
  que una excepción explícita.

**Contraejemplos.**
- *Contra A (v2, hechos sin trío):* el propio sensor afirma «lo marcado en `notes/a.md` cambió».
  Con A esa afirmación **no se puede escribir** como `(R A B)`, porque el documento no es hecho y el
  hecho elemental no es trío: v2 no puede expresar su hecho más elemental en la forma que el dueño
  dio. Y permite lo que no debería: un hecho que no relaciona nada.
- *Contra B literal:* `(anclado-en a.md h1)` requiere que `a.md` y `h1` sean hechos; si lo son, sus
  anclajes piden más hechos… regresión infinita; si se corta con un ciclo, la definición no es bien
  fundada y admite relaciones entre una cosa y un hecho (p. ej. «el documento contiene el párrafo»),
  que es mezclar realidad y hecho (P3).
- *Contra C:* nada que deba expresarse queda fuera; su coste es una frase del dueño que no tenemos:
  **qué denotan los términos base**, si hechos o referencias. Con C se asume designador; es
  inferencia mía, explícita.

## Otras objeciones a v2 (breves)

1. «Ambigüedad irreductible: ninguna» es demasiado fuerte: la ambigüedad se traslada a **qué
   denotan los términos base** y a la **individuación bajo cambio** (punto 1).
2. I5 («todo cambio es mutación de ese hecho detectado») es lo que hace indistinguibles
   «borrar+crear en la ruta» y «mover+editar»; separar ambos exige la declaración de discontinuidad.
3. P9 es de Matrix y no del brief; v2 lo usa para «dos contextos ⇒ dos hechos», lo que sí se sigue
   de P8, pero conviene no apoyarlo en P9.
4. §9 dice que la identidad no se puede recomputar y que `include build --rebuild` hoy la perdería;
   falta la regla de arrastre en `rebuild` (una línea, no es identidad).
