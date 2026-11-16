REFORMULACION
Propósito: obtener del dueño (JP) respuestas o rechazos explícitos a cuatro preguntas abiertas de `docs/v2/drafts/preguntas-abiertas.md` (SERVE-4, GRAMATICA-CLJ, RAZON, PRUEBAS-ALMACEN). Resultado: cada fila con respuesta textual fechada al inicio de su segunda celda, con el prefijo indicado, en commits sobre `main`. Repo: `kimun`. Límites: no implementar ninguna respuesta; cada respuesta será otra tarea. No es agregado ni paquete: es una tarea de decisión que depende de interacción humana.

AMBIGUEDADES
1. «Whoever coordinates the work on kimun (the agent session the owner, JP, is talking to)»: no identifica una sesión concreta ni cómo actuar si no existe. Obligaría a inventar el ejecutor y el canal.
2. «whenever the owner is present»: no define presencia, disponibilidad ni qué hacer si no responde. Obligaría a inventar el criterio de cuándo preguntar.
3. «in the order given (the first blocks code nodes; the others block nothing)»: el orden de Scope es GRAMATICA-CLJ, SERVE-4, RAZON, PRUEBAS-ALMACEN, pero Rationale enumera SERVE-4 primero. Obligaría a inventar el orden definitivo.
4. «one commit per answer: 'docs(open-questions): owner answers <ROW-ID>'»: también aplica a rechazos, pero el mensaje dice “answers”. Obligaría a inventar el mensaje para «Sin decidir».

DRY-RUN
Lecturas hipotéticas: TaskDoc, tabla `preguntas-abiertas.md`, los tres REPORT.md citados y `pods/treesitter/Cargo.toml` para contexto. Cambios previstos: preguntar una cuestión por mensaje, registrar la respuesta textual entre «» con fecha y prefijo, y commitear una por fila. Dependencias: presencia del dueño, estructura de la tabla y evidencia disponible. Validaciones futuras: comprobar prefijos, comillas, fecha, cuatro filas tratadas y commits en `main`. Sin ejecutar nada.

FUTURO
JP decide y responde o rechaza cada pregunta. El coordinador transcribe y commitea. Las respuestas aceptadas pasan a tareas separadas de implementación; el rechazo no genera implementación.
