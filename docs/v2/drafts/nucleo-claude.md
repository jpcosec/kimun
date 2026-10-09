# Núcleo de kimun — borrador de Claude (ronda 1, 2026-10-09)

1 El mundo de kimun es la totalidad de las proposiciones en contexto, no de los documentos.
1.1 Conocimiento es una colección de proposiciones `(R A B)`; las cosas no significan solas, significan por sus relaciones (TLP 1.1, 3.3).
1.2 No hay hechos ni símbolos universales: toda proposición vive en un contexto `W_i`, y los contextos forman un árbol que se recorre.
1.21 El contexto fija el álgebra de `R` (transitiva, conmutativa, biyectiva…) y el sentido de `A` y `B`.
1.3 El mismo `(R A B)` en dos fuentes son dos hechos; unirlos es una decisión registrada, no una coincidencia de cadenas.

2 Todo conocimiento está anclado a algo observable, o derivado de lo anclado.
2.1 Las fuentes son texto, código, datos, tests: cualquier artefacto del ambiente. kimun las lee primero como estructura (AST-first).
2.2 Un anchor es un lugar estructural en una fuente; une un contenido semántico con el afuera, al leer y al proyectar, que son la misma operación.
2.21 Un anchor que se rompe es una mutación o un borrado. La mutación se detecta; el borrado se declara. Nada se pierde en silencio.
2.3 Toda respuesta puede recorrerse de vuelta hasta lo que la sostiene.

3 El signo no es el símbolo.
3.1 El signo es lo perceptible (una palabra, un token, unos bytes); el símbolo es una posición en un espacio lógico dentro de un contexto.
3.2 La identidad de un nodo es su contenido en sentido semántico: un lugar, estructural para lo que ancla, semántico para lo que significa.
3.21 El hash es un resumen matemático que verifica; no identifica. (Cómo se realiza en el kernel: decisión abierta D0.)
3.3 Un símbolo nace en el contexto de su fuente; subirlo de contexto o declararlo equivalente a otro es curaduría.

4 Antes de preguntar si algo es verdadero, se pregunta si tiene sentido.
4.1 Sentido: sinnvoll, sinnlos, unsinnig, siempre relativo a un contexto.
4.2 La verdad no se otorga por quién afirma ni por qué motor extrajo: se sostiene por anclaje o por derivación. (Forma exacta del eje de verdad: decisión abierta D-G.)
4.3 Una contradicción dentro de un contexto nunca se resuelve en silencio.

5 Leer y escribir son dos modelos distintos.
5.1 Todo documento es instancia de una clase; lo que no calza se resuelve por herencia.
5.2 El modelo de anclaje decide qué de una fuente es conocimiento; el de generación decide cómo se produce texto desde el grafo.
5.21 Lo que no se ancla no se guarda: el azúcar se reconoce en el nodo, no se declara.

6 Se guarda lo que no se puede recalcular.
6.1 Tres capas: caché (recomputable, borrable), store estable (huellas del ambiente para detectar cambios) y grafo (el conocimiento).
6.2 Trackeable es todo lo que podría anclarse; trackeado es lo que alimenta el grafo. El cambio se localiza sobre lo trackeado y se reconcilia ahí, sin tocar el resto.
6.3 Nada existente se modifica: todo cambio es una acción registrada.

7 Las estructuras son datos, y los datos son código.
7.1 Clojure: inmutabilidad, jerarquías, homoiconicidad; declarar una estructura y escribir el AST son el mismo gesto.
7.2 La lógica vive en Clojure; los motores especializados (parsers, matrices, embeddings) entran por protocolo y nunca son autoridad por sí mismos.
