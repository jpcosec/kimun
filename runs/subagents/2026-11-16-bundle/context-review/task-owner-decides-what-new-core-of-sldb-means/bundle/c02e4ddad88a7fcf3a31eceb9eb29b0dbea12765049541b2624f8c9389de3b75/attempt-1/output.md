REFORMULACION
Propósito: obtener de JP qué significa «núcleo nuevo de sldb», registrar su respuesta literal y reflejarla en la documentación. Resultado: sección fechada en `docs/v2/drafts/sesion-2026-11-15.md` con la respuesta entre «», reescritura de `docs/v2/drafts/diseno-leer.md` §6 y marca **Respondida (dueño, YYYY-MM-DD):** en `docs/v2/drafts/traspaso-2026-11-17.md` §6.1. Repo: `/home/jp/proyectos/kimun`. Límites: una sola pregunta en prosa, sin diálogo de opciones múltiples; no diseñar ni implementar; no cerrar sin respuesta del dueño. No es agregado ni paquete: es `task.decision` y no es ejecutable autónomamente, pues exige respuesta humana.

AMBIGUEDADES
1. «by whoever is coordinating the work on kimun (the agent session he is talking to)»: no concreta un ejecutor; obliga a inventar quién pregunta.
2. «the four numbers of runs/subagents/<its date>-leer/REPORT.md»: «<its date>» no se resuelve; obliga a inventar la fecha.
3. Goal dice «'Mi lectura, sin confirmar'» y Validación ««Mi lectura, sin confirmar por el dueño»»: no coincide el texto exacto a reemplazar; obliga a elegir uno.
4. La instrucción promete «rituales enrutados por el Board, las pills ligadas, los átomos ligados y los archivos», pero el input solo trae TaskDoc e instrucción; obliga a inventar esos artefactos.
5. «If 'deskops show task task-finish-wip-leer-readers-by-class' says it is closed»: no se aporta esa salida ni `REPORT.md`; obliga a inventar estado y cifras.

DRY-RUN
Lecturas: TaskDoc, `sesion-2026-11-15.md`, `diseno-leer.md`, `traspaso-2026-11-17.md`, `runs/research/2026-11-15/reemplazo-sldb`; opcionalmente la tarea de deskops y su `REPORT.md`. Cambios previstos: añadir sección fechada con la cita; sustituir el párrafo indicado de §6; editar §6.1. Dependencias: respuesta del dueño, estado de `task-finish-wip-leer-readers-by-class` y cifras del reporte. Validaciones futuras: los tres checks del TaskDoc, sin ejecutar ahora.

FUTURO
JP responde; el coordinador registra la respuesta literal y hace commit en `main`; otro nodo o humano verifica los tres checks y decide el cierre.
