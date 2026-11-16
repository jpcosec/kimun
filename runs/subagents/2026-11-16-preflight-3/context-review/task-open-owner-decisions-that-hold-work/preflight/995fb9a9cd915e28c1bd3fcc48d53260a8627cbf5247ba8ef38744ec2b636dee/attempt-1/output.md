## REFORMULACION

**Propósito:** obtener del dueño (JP) respuesta o rechazo explícito a cuatro preguntas abiertas de kimun. **Resultado:** las filas SERVE-4, GRAMATICA-CLJ, RAZON y PRUEBAS-ALMACEN de `docs/v2/drafts/preguntas-abiertas.md` empiezan su segunda celda con `**Respondida (dueño, YYYY-MM-DD):**` o `**Sin decidir (dueño, YYYY-MM-DD):**`, con la respuesta literal entre «». **Repo:** kimun (workspace desk). **Límites:** no implementar ninguna respuesta —cada una será otra tarea—; solo preguntar, una pregunta por mensaje, y registrar; un commit por respuesta en main.

## AMBIGUEDADES

1. «the agent session the owner, JP, is talking to»: no identifica sesión ni canal concreto; obligaría a inventar cuál es.
2. «whenever the owner is present»: sin criterio de presencia ni de espera; obligaría a inventar cuándo preguntar y qué hacer si no está.
3. «in the order given (the first blocks code nodes; the others block nothing)»: el orden de Scope empieza por GRAMATICA-CLJ, pero no aclara si «the first» es esa fila o SERVE-4; obligaría a inventar la prioridad.
4. «at the start of its second cell» y validación «starts its second cell with **Respondida (dueño, or **Sin decidir (dueño,»: no se da la estructura de la tabla y el formato entrecomillado está desbalanceado; obligaría a inventar sintaxis exacta del prefijo.
5. «as the answered rows of that table already do»: no se incluye la tabla; obligaría a inventar el formato de las filas ya respondidas.
6. «one commit per answer» frente a Done When «in a commit on main»: ¿cuatro commits o uno? obligaría a inventar.
7. «quotes the owner between «»»: no dice cómo escapar «» ni saltos de línea en la respuesta; obligaría a inventar la codificación.

## DRY-RUN

**Lecturas:** `docs/v2/drafts/preguntas-abiertas.md` (filas y formato de respondidas); referencias solo si hace falta contexto para formular, no para registrar. **Cambios previstos:** editar la segunda celda de cuatro filas y crear commits `docs(open-questions): owner answers <ROW-ID>`. **Dependencias:** presencia del dueño, tabla existente, rama main. **Validaciones futuras:** prefijo correcto en cada fila, cita entre «», fecha, commits en main. No se ejecuta nada.

## FUTURO

El dueño responde o declina cada pregunta; cada respuesta habilita una tarea propia fuera de este nodo. La revisión humana decide si el registro y los commits son aceptables.
