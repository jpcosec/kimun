## REFORMULACION

**Propósito:** obtener del owner (JP) qué significa «núcleo nuevo de sldb», pregunta hecha dos veces el 2026-11-16 sin respuesta, porque sin ella no puede diseñarse el criterio 1.

**Resultado:** respuesta literal del owner, entre «» y con fecha, añadida a `docs/v2/drafts/sesion-2026-11-15.md`; `docs/v2/drafts/diseno-leer.md` §6 reescrito desde «Mi lectura, sin confirmar» a lo dicho; fila de la pregunta en `docs/v2/drafts/traspaso-2026-11-17.md` §6 item 1 marcada como respondida; todo en un commit en `main`.

**Repo:** `/home/jp/proyectos/kimun` (directorio de trabajo actual; el input no lo declara).

**Límites:** una sola pregunta, formulada sola, sin lista ni formulario; se registra cualquier respuesta (mezcla o tercera opción) tal cual; no se diseña ni implementa nada. La tarea no se cierra con la lectura del coordinador.

## AMBIGUEDADES

1. «asked by the coordinator to the owner in conversation»: no define quién es el coordinador ni cómo se le entrega la pregunta. Obligaría a inventar rol, canal y responsable de preguntar.
2. «If the task task-finish-wip-leer-readers-by-class is closed»: no indica dónde consultar su estado. Obligaría a inventar ruta o mecanismo de comprobación.
3. «the four numbers of its REPORT.md»: no da ubicación del REPORT.md. Obligaría a inventar la ruta.
4. «docs/v2/drafts/diseno-leer.md §6 is rewritten»: no reproduce el contenido actual de §6 ni su formato. Obligaría a inventar el texto de partida y el reemplazo.
5. «The row of this question ... is marked answered»: no especifica sintaxis de la fila ni marca. Obligaría a inventar el formato de anotación.
6. «forms went wrong twice»: no aclara qué se considera formulario. Obligaría a inventar el límite de formato permitido.

## DRY-RUN

**Lecturas:** TaskDoc; `sesion-2026-11-15.md`; `diseno-leer.md` §6; `traspaso-2026-11-17.md` §6; `runs/research/2026-11-15/reemplazo-sldb`; estado de `task-finish-wip-leer-readers-by-class` y su `REPORT.md` si está cerrada.

**Cambios previstos:** sección fechada con la respuesta entre «»; reescritura de §6; marca en la fila del traspaso; commit en `main`.

**Dependencias:** respuesta del owner; estado de la otra tarea.

**Validaciones futuras:** las tres comprobaciones del TaskDoc; commit en `main`; la tarea permanece abierta hasta que el owner responda.

No es un agregado ni paquete: es una tarea de decisión; su ejecución real depende de un humano, no de un ejecutor autónomo.

## FUTURO

El owner responde; el coordinador transcribe literalmente y actualiza los tres documentos; un revisor verifica el commit. Nada de esto se ejecuta aquí.
