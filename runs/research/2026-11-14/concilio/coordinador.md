# Lectura del coordinador — corrección del dueño 2026-11-14

Investigación; no representación técnica aprobada ni implementación.

## Fijado por el dueño

- Kimun sirve como sistema nervioso de un sistema autopoiético de conocimiento: sensorización, detección de mutaciones y eventualmente acciones.
- Los nodos identifican lo sensorizado, con niveles de abstracción. Su identidad no es el hash ni el anclaje en la topología. Debe permitir identificar las topologías marcadas y seguir cambios temporales.
- Merkle permite detectar cambios en lo observado.
- Dos verificaciones: vigencia de lo marcado respecto de su hash y, si se incorpora marcar ejecuciones, verdad del resultado.
- Alcance actual: transición S–M. G requiere completar esa base.

## Lectura y propuesta, por revisar con el concilio

La pregunta A/B de D0 era prematura: ambas opciones dejaban sin explicar la continuidad de la unidad observada. Elegir CAS o UUID no resuelve ese contrato.

Separar unidad observada, observación fechada, localizador, nivel/contrato de observación y huella. Un identificador permite referir a la unidad; la evidencia y reglas de continuidad determinan si dos observaciones corresponden a ella. La codificación del identificador queda abierta.

Ejemplo: mover una función de archivo y modificar su implementación puede mantener una unidad en un nivel y cambiarla en otro. Registrar copia, división y fusión requiere procedencia explícita; la igualdad de bytes no prueba continuidad de una unidad. La ausencia de evidencia suficiente debe quedar visible.

La comparación de huellas es relativa a lo seleccionado y al contrato de observación: una proyección intacta no acredita que todo el archivo esté intacto. Cambiar parser/selector exige distinguir cambio de contrato de cambio de fuente (K6).

Para ejecución, distinguir resultado observado, condiciones/dependencias de ejecución y criterio que permite validarlo. Que un proceso termine correctamente no basta para acreditar cualquier afirmación acerca del resultado. Esto es trabajo de especificación pendiente, no una imposición de TRUE/FALSE/UNKNOWN para G.

## Fuentes

- `docs/research/autopoiesis/README.md`: aclaración del dueño del 10 de octubre, omitida en la pregunta anterior.
- `docs/research/autopoiesis/sources/maturana-1975-reprint-1999.txt`, pp. 151–152: distinciones unidad, organización y estructura; también distingue compartir organización de ser una unidad particular. Aplicación a kimun es interpretación de diseño.
- `docs/v2/14-juego-del-lenguaje.md §21`, `docs/v2/drafts/preguntas-abiertas.md`: identificación por lugar a revisar a la luz de la corrección actual.
- `src/kimun/kernel/node.cljc`: `identity-form` y `node-id` implementan actualmente identidad por contenido canónico.
- `docs/v2/drafts/capa-g-dudas.md`: ya exige cerrar S y M antes de G.

No se ejecutaron tests ni se modificó producto.
