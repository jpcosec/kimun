# Síntesis del coordinador, 2026-11-14

Leídos los informes `claude.md`, `agy.md`, `pi.md` y las fuentes locales descritas en
`coordinador.md`. Investigación y propuesta; no implementación ni consenso sobre un formato.

La corrección del dueño está registrada en la cabecera de `docs/v2/14-juego-del-lenguaje.md`.
`docs/v2/drafts/preguntas-abiertas.md` advierte que las alternativas anteriores son históricas.

## Convergencia

Separar unidad marcada, estado observado, localizador y contrato/nivel de observación.
La continuidad temporal requiere evidencia; ni hash, ruta ni la asignación de un UUID la explica.
El mecanismo de almacenamiento queda subordinado a este contrato. Merkle detecta cambios
de la proyección observada bajo un contrato. S–M precede a G.

## Propuesta del coordinador

Definir el nodo como referencia a una unidad marcada cuya continuidad se registra a través
de observaciones y transformaciones justificadas. Cada observación vincula esa unidad con
su topología concreta, nivel de abstracción, contrato y huella en una revisión. La forma
del identificador queda abierta. No es una decisión atribuida al dueño.

En una función trasladada y editada, registrar por separado continuidad, nuevo localizador
y cambio de estado. Si no hay evidencia de continuidad, conservar candidatos e incertidumbre.
En copia, división y fusión, representar procedencia; no confundir linaje con identidad.
Cada nivel necesita definir qué conserva la unidad y qué termina o transforma su delimitación.

## Correcciones a las propuestas individuales

- Pi propone CAS de contenido: no adoptar una copia durable del ambiente. Distinguir registros
  propios de kimun de contenido externo recomputable en caché.
- Pi propone reconocer movimiento por igual huella: insuficiente ante copia más borrado.
- Agy conserva identidad y vigencia de hijos tras dividir/fusionar contenedores: solo vale si
  el contrato del hijo no depende del contexto que cambió y hay continuidad documentada.
- Claude presenta varias preguntas al dueño: no todas son bloqueos humanos nuevos. La instrucción
  actual fija S y M; corresponde elaborar y contrastar un contrato antes de pedir decisiones.
- Verdad de ejecución: separar resultado histórico acreditado y posibilidad de usarlo bajo
  condiciones actuales. Cambiar entradas puede invalidar su uso actual sin falsificar el registro
  de lo ocurrido. Definir qué comprueba el validador; no inferir corrección de un exit code.

## Siguiente trabajo de diseño

Contrato S–M con casos de edición, traslado, copia, división, fusión, borrado y cambio de parser;
declarar evidencia de continuidad y alcance de cada huella. Después alinear K1 y el resto del
backlog a ese contrato. No elegir un formato ni empezar G a partir del enunciado antiguo.

No se ejecutaron tests ni se cambió código. Cambios documentales y revisión sin commit.
