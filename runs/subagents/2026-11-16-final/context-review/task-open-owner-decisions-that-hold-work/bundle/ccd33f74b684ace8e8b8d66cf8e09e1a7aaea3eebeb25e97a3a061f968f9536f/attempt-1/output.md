## REFORMULACION
Propósito: obtener del dueño JP respuestas o rechazos explícitos a cuatro preguntas abiertas (GRAMATICA-CLJ, SERVE-4, RAZON, PRUEBAS-ALMACEN) y registrarlos en `docs/v2/drafts/preguntas-abiertas.md`, sin implementar ninguna respuesta. Resultado: cada fila empieza su segunda celda con `**Respondida (dueño, YYYY-MM-DD):**` o `**Sin decidir (dueño, YYYY-MM-DD):**`, con cita textual entre «» cuando corresponda, y un commit por fila en `main`. Repo: kimun. Límites: preguntar una por mensaje, en orden, solo si JP está presente; no implementar; no modificar otras filas. Es una tarea de decisión/coordinación dependiente de humano, no ejecutable de forma autónoma.

## AMBIGUEDADES
1. «whenever the owner is present»: no define “presente” ni ventana; obligaría a inventar cuándo preguntar y qué hacer si no responde.
2. «the agent session the owner, JP, is talking to»: no identifica conversación/sesión concreta; obligaría a inventar el canal.
3. Validación: «starts its second cell with **Respondida (dueño, or **Sin decidir (dueño,» frente a Goal: «(dueño, YYYY-MM-DD):**»; obligaría a inventar si la fecha y los dos puntos son obligatorios para cerrar.
4. «as soon as it is given, one commit per row»: no cubre respuestas parciales, correcciones o respuestas fuera de orden; obligaría a inventar política de commits.
5. «a question he declines is prefixed...» y «Each answered row quotes the owner between «»»: no aclara si una fila declinada también exige cita textual; obligaría a inventar ese requisito.

## DRY-RUN
Lecturas: TaskDoc, `docs/v2/drafts/preguntas-abiertas.md`, los tres `REPORT.md` referenciados y `pods/treesitter/Cargo.toml`. Cambios previstos: editar las cuatro filas y crear un commit por fila: `docs(open-questions): owner answers <ROW-ID>` o `docs(open-questions): owner leaves <ROW-ID> undecided`. Dependencias: JP disponible, sesión de conversación identificada, acceso a git y `main`. Validaciones futuras: prefijo correcto con fecha, cita entre «» en respondidas, una fila por commit, ninguna implementación. Sin ejecutar.

## FUTURO
JP decide o declina cada pregunta; otro nodo convierte cada respuesta en tarea propia; un humano revisa y valida los commits en `main`. No es ambigüedad del encargo.
