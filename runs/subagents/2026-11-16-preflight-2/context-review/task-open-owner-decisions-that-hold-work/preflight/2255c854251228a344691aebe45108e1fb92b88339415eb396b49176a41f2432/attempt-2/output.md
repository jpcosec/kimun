## REFORMULACION

Encargo: obtener del dueño (JP) respuesta a cuatro decisiones abiertas y dejarlas registradas en la tabla de `docs/v2/drafts/preguntas-abiertas.md` (filas SERVE-4, GRAMATICA-CLJ, RAZON, PRUEBAS-ALMACEN). Resultado: cada fila responde en su primera celda con la cita literal del dueño, fechada, bajo el prefijo `**Respondida (dueño, <date>):**`; si rehúsa decidir, se anota «sin decidir» con fecha y la fila queda abierta. Repo: kimun, trabajo sobre `desk/tasks/task-open-owner-decisions-that-hold-work.md` y el documento de preguntas. Límites: no implementar ninguna respuesta — cada una se convierte en tarea propia —; no tocar código; una pregunta por mensaje, en el orden dado.

## AMBIGUEDADES

1. «Ask one question per message, in conversation» — no se define canal, interlocutor ni ventana temporal; obligaría a inventar cómo y cuándo se contacta a JP.
2. «a question the owner declines to decide is recorded as «sin decidir»» frente a «grep -c "Respondida (dueño" ... increased by the number of questions answered» — una negativa explícita no incrementa el grep; obligaría a inventar si la validación cuenta negativas o solo respuestas.
3. «Each of the four rows ... carries the owner's answer, verbatim and dated ... prefixed by **Respondida (dueño, <date>):**» — no se fija formato de fecha; obligaría a elegir formato.
4. «in a commit on main» — sin formato de mensaje ni criterio de agrupación; obligaría a inventarlo.
5. Los textos de las cuatro preguntas se citan en Scope pero la fuente declarada es la tabla; no se dice cuál prevalece si difieren — obligaría a decidir precedencia.

## DRY-RUN

Lecturas: `docs/v2/drafts/preguntas-abiertas.md` (filas y estilo de filas ya respondidas), los tres REPORT.md de `runs/subagents/` citados y `pods/treesitter/Cargo.toml` para verificar hechos de GRAMATICA-CLJ. Cambios previstos: edición de cuatro celdas de la tabla, más eventual commit en main. Dependencias: respuestas del dueño y un canal de conversación. Validaciones futuras: conteo grep antes/después, cita entre «» por fila, revisión de que ninguna respuesta implementa código.

## FUTURO

El dueño responde o declina; un humano decide canal y fechas; cada respuesta afirmativa se abre como tarea nueva por otro nodo; el commit en main cierra la tarea.
