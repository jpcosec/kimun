## REFORMULACION
- **Propósito:** decidir si hace falta una caché de lecturas y, solo entonces, implementarla; si no, cerrar como no necesaria documentando la medición.
- **Resultado:** que las lecturas de un archivo sin cambios no se recalculen, o un cierre con el número que lo haga innecesario.
- **Repo:** workspace `desk`; tarea en `desk/tasks/task-cache-of-readings-by-git-blob-hash.md`, con referencias a documentos de diseño.
- **Límites:** depende de `task-finish-wip-leer-readers-by-class`; el gate es la medición de `wip/leer`; el alcance es una caché comprimida según `diseno-declaraciones.md §6`, indexada por el hash git. El nodo es una tarea condicional, no un agregado ni paquete ejecutable; no tiene hijos.

## AMBIGUEDADES
1. «if the measurement of the first cut asks for it»: no fija umbral ni criterio; obligaría a inventar cuándo la medición “pide” la caché.
2. «The compressed cache of diseno-declaraciones.md §6»: el contenido de §6 no está en el input; obligaría a inventar formato, compresión y ubicación.
3. «keyed by the hash git already has»: no aclara si es hash de blob, árbol o commit, ni algoritmo/alcance; obligaría a inventar la clave.
4. «Readings of an unchanged file are not recomputed»: no define “reading”, unidad ni dónde se almacena; obligaría a inventar el modelo de datos.
5. «Only if the wip/leer measurement shows the need; otherwise close as not needed with the number»: no define la medición ni qué número registrar; obligaría a inventar métrica y formato de cierre.
6. «Measured before and after»: no especifica métricas ni método; obligaría a inventar el benchmark.
7. «Validation» seguido de «- »: no hay checks; obligaría a inventar validaciones.

## DRY-RUN
Lecturas hipotéticas: TaskDoc, salida de la dependencia y documentos §4/§6. Cambios previstos: evaluar el gate; si procede, implementar caché comprimida keyed por blob hash y pruebas; si no, registrar cierre con el número. Dependencias: medición de `wip/leer` y diseño §6. Validaciones futuras: comparar antes/después, comprobar no recómputo de archivo sin cambios, invalidación al cambiar el blob y documentar el número. Sin ejecutar nada.

## FUTURO
Otro nodo debe producir la medición de `wip/leer`; el documento de diseño debe aportar §6; un humano o revisor decide el umbral y acepta el cierre como implementado o no necesario.
