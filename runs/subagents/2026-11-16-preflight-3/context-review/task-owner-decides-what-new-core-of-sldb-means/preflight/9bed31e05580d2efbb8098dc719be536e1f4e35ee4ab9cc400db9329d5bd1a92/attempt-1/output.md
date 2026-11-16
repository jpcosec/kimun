## REFORMULACION

Tarea de decisión (no agregado ni paquete): obtener del dueño (JP), en una sola pregunta en prosa, qué significa «núcleo nuevo de sldb» —(a) sldb sigue existiendo y kimun hace el trabajo por debajo, o (b) kimun lo reemplaza y los consumidores se reescriben—, admitiendo cualquier otra respuesta. Resultado: la respuesta literal entre «», con fecha, registrada en tres archivos del repo kimun: sección nueva en `docs/v2/drafts/sesion-2026-11-15.md`, reescritura del último párrafo de §6 de `docs/v2/drafts/diseno-leer.md`, y §6 ítem 1 de `docs/v2/drafts/traspaso-2026-11-17.md` con **Respondida (dueño, YYYY-MM-DD):**. Límites: no diseñar ni implementar nada; no cerrar la tarea sin respuesta del dueño; la pregunta no va en lista ni en diálogo de opciones.

## AMBIGUEDADES

1. «give with the question the four numbers of runs/subagents/<its date>-leer/REPORT.md» — «<its date>» no está definido ni la ruta existe en el input; obligaría a inventar la fecha y el contenido del REPORT.
2. «If 'deskops show task task-finish-wip-leer-readers-by-class' says it is closed» — no se especifica cómo se consulta ese estado ni qué cuenta como «closed»; obligaría a inventar el mecanismo.
3. «rewritten from 'Mi lectura, sin confirmar'» / «its last paragraph, which starts «Mi lectura, sin confirmar por el dueño»» — no se da el texto de §6 ni garantía de que ese párrafo sea el último; obligaría a inventar el contenido previo.
4. «The three validation checks hold in a commit on main» — no se indica quién commitea ni en qué repo/branch; obligaría a inventar el flujo git.
5. «the agent session he is talking to» — no identifica la sesión concreta; obligaría a inventar el canal.

## DRY-RUN

Lecturas: los cuatro references y los tres archivos destino; condicionalmente `runs/subagents/*-leer/REPORT.md`. Cambios previstos: tres ediciones textuales (sección fechada, §6, traspaso §6.1). Dependencias: respuesta del dueño; estado de la tarea deskops. Validaciones futuras: los tres checks y su presencia en un commit de main; la tarea permanece abierta hasta la respuesta.

## FUTURO

El dueño responde (decisión humana); el coordinador formula la pregunta y registra; otro nodo commitea y valida. No es ambigüedad: es trabajo fuera de este nodo.
