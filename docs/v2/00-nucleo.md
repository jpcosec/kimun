# Núcleo de kimun

> Lo que, si cambia, hace que kimun deje de ser kimun. Una página, en proposiciones al modo del
> *Tractatus*: cada número decimal comenta al anterior. No describe implementación ni backlog; las
> decisiones abiertas del dueño se nombran como abiertas. Redactado por Claude y Astra en tres
> rondas (2026-10-09: borradores, críticas cruzadas y revisión, en `drafts/nucleo-*.md`); fuentes: `14`, `drafts/fuentes-identidad.md`,
> `drafts/capa-g-dudas.md`, `drafts/preguntas-abiertas.md`.

**1** El conocimiento de kimun consiste en proposiciones `(R A B)` en contexto.

**1.1** Las cosas no significan solas; significan por sus relaciones.

**1.2** No hay hechos ni símbolos universales: toda proposición pertenece a un contexto, y el contexto fija el álgebra de `R` y el sentido de `A` y `B`.

**1.3** Relaciones, términos y contextos ocupan lugares en jerarquías. Cruzar de un contexto a otro exige una correspondencia justificada.

**1.4** La misma forma proposicional en dos fuentes conserva sus contextos y procedencias; relacionarlas es explícito.

**2** Kimun trata texto, código y otros artefactos como conocimiento a partir de su estructura (AST-first).

**2.1** El documento es una de las formas en que se organiza lo estructurado; reconocer conocimiento no exige someter las fuentes a una plantilla previa.

**2.2** El conocimiento se forma desde fuentes estructuradas y principios explícitos, recibidos por instrucción o desde las fuentes.

**2.3** Un anchor es un vínculo verificable entre un conocimiento y su soporte en una fuente, sea un lugar en su estructura o una evaluación sobre ella; vincula hacia afuera al ingerir y al proyectar.

**2.4** Si el soporte muta o desaparece, se revisa lo que depende de él; si se pierde soporte, se declara. Ninguna pérdida de soporte se oculta.

**2.41** La revisión reconcilia todas las dependencias afectadas, sin recalcular lo independiente.

**3** El signo no es el símbolo.

**3.1** El signo es lo perceptible; el símbolo es una posición en un espacio lógico, dentro de un contexto. Igualar cadenas no identifica símbolos.

**3.2** Un símbolo nace en el contexto de su fuente; extender su alcance o declararlo equivalente a otro es curaduría explícita.

**3.3** El sentido de un símbolo depende de su contexto; una huella verifica cambios, no determina significado ni equivalencia. El lugar contextual de un símbolo es relación y evidencia, no identidad: la identidad de la unidad es propia, acuñada al marcarla, y no es su huella ni su anclaje en la topología (corrección del dueño del 2026-11-14, que sustituye «identidad = lugar»; `14 §21`). **D0 (ya no abierta, 2026-11-15):** la identidad y continuidad de las unidades en S–M es el contrato de `14 §21`, que el concilio derivó de las premisas del dueño (sus reglas son inferencias del concilio, no palabras del dueño; `runs/research/2026-11-15/concilio/d0-contrato-sintesis.md`). Queda su implementación (`K1`). Esto no fija nada de la capa G: cómo se relacionan y evalúan los símbolos entre contextos sigue en `D-G` y `G1`.

**4** El conocimiento se evalúa dentro de su mundo, y su justificación debe poder recorrerse.

**4.1** La evaluación de la verdad presupone esclarecer el sentido en su contexto: *sinnvoll*, *sinnlos* o *unsinnig*.

**4.2** Sentido y verdad son cuestiones distintas. **D-G, abierta:** si «anclado / derivado» es el eje de la verdad o expresa el soporte junto a un valor operacional de verdad.

**4.3** Registrar una afirmación, humana o producida por un modelo, no basta para establecerla; la semejanza y la coocurrencia tampoco sustituyen una proposición justificada. Ningún motor confiere autoridad por el solo hecho de producir un resultado.

**4.4** Una contradicción dentro de un contexto se señala y exige desambiguación o corrección explícita.

**5** Los modelos expresan formas estables de estructura y conocimiento; el anclaje y la generación tienen modelos distintos.

**5.1** Todo documento es instancia de una clase; lo que no calza se expresa por especialización.

**5.2** Una clase cambiada es otra clase; sus instancias siguen perteneciendo a la clase original hasta su reclasificación explícita.

**5.3** El modelo de anclaje selecciona qué estructura soporta conocimiento; no todo lo expresado se vuelve conocimiento registrado.

**5.4** El modelo de generación produce expresiones desde el conocimiento; una misma comprensión admite muchas, sin prometer recuperar una única.

**6** Cada cosa conserva la autoridad que corresponde a su origen.

**6.1** La fuente responde por lo que contiene; el conocimiento propio del grafo responde desde el grafo; una expresión generada depende de sus datos y de su modelo de generación.

**6.2** Kimun conserva su conocimiento propio y los vínculos necesarios para verificar su dependencia del ambiente; no conserva una copia del ambiente como respaldo.

**6.3** Lo reconstruible puede descartarse sin perder conocimiento propio ni procedencia, y nunca adquiere autoridad por sí mismo.

**6.4** Los cambios del registro propio se registran sin reescribir su historia.

**7** Clojure es el lenguaje constitutivo de kimun.

**7.1** Las formas de sus instrucciones pueden tratarse como datos: declarar estructuras y operar sobre ellas comparten un mismo lenguaje.
