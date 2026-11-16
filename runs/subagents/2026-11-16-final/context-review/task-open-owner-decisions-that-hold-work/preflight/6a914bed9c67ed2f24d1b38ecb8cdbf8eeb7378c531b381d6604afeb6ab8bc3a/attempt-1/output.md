## REFORMULACION
**Propósito:** obtener de JP respuesta o rechazo explícito a cuatro preguntas abiertas de `docs/v2/drafts/preguntas-abiertas.md` —GRAMATICA-CLJ, SERVE-4, RAZON y PRUEBAS-ALMACEN— que condicionan trabajo posterior. **Resultado:** cada fila con su segunda celda iniciada por `**Respondida (dueño, YYYY-MM-DD):**` y la respuesta literal entre «», o por `**Sin decidir (dueño, YYYY-MM-DD):**`; un commit por fila en `main`. **Repo:** kimun. **Límites:** no implementar ninguna respuesta; cada respuesta será otra tarea. El ejecutor pregunta de una en una, en el orden dado, en la conversación con JP, y registra cada respuesta en cuanto se produzca. No es un agregado ni un paquete: es una tarea de decisión asistida por humano.

## AMBIGUEDADES
1. «at the start of its second cell»: no se define la sintaxis exacta de la tabla ni si el prefijo sustituye o precede al contenido previo. Obligaría a inventar el formato si no se lee el archivo.
2. «Each answered row quotes the owner between «»» frente a «verbatim»: no aclara si se cita el mensaje completo o solo la decisión, ni cómo recortar respuestas largas. Obligaría a inventar un criterio de cita.
3. «a question he declines is prefixed by **Sin decidir (dueño, YYYY-MM-DD):**»: no indica si una fila declinada debe llevar cita, motivo o solo el prefijo. Obligaría a inventar contenido adicional.
4. «whenever the owner is present»: no define presencia, espera, timeout ni qué hacer si JP no responde. Obligaría a inventar una política de bloqueo o escalado.

## DRY-RUN
**Lecturas:** TaskDoc, `docs/v2/drafts/preguntas-abiertas.md` y, solo para contraste, los REPORT.md y `pods/treesitter/Cargo.toml` referenciados. **Cambios previstos:** editar cuatro filas de la tabla y crear cuatro commits con los mensajes indicados; ningún cambio de código. **Dependencias:** disponibilidad de JP, tabla existente y permisos para commitear en `main`. **Validaciones futuras:** comprobar que las cuatro filas empiezan por el prefijo correcto, que las respondidas citan a JP entre «» y que existen los cuatro commits. No se ejecuta nada.

## FUTURO
JP decide cada respuesta o rechazo; el coordinador conduce la conversación y redacta los commits; otro nodo o persona crea las tareas de implementación derivadas; la revisión o merge de los commits queda fuera de este encargo.
