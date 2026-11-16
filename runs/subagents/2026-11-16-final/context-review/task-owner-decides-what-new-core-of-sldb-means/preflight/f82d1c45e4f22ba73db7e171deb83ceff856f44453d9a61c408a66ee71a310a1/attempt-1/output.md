## REFORMULACION

**Propósito:** obtener del dueño (JP) qué significa «núcleo nuevo de sldb», criterio 1 que hoy bloquea el diseño. **Resultado:** (1) sección fechada nueva en `docs/v2/drafts/sesion-2026-11-15.md` con la respuesta entre «»; (2) en `docs/v2/drafts/diseno-leer.md` §6, el párrafo que empieza «Mi lectura, sin confirmar por el dueño» sustituido por la respuesta y lo que se sigue de ella; (3) en `docs/v2/drafts/traspaso-2026-11-17.md` §6 ítem 1, prefijo `**Respondida (dueño, YYYY-MM-DD):**`. **Repo:** kimun. **Límites:** una sola pregunta en prosa, fuera de listas y de diálogos de opción múltiple; no diseñar ni implementar; la tarea no se cierra sin respuesta del dueño ni por lectura propia del coordinador.

## AMBIGUEDADES

1. «asked to the owner (JP) by whoever is coordinating the work on kimun (the agent session he is talking to)» — no fija si el ejecutor *es* esa sesión o debe identificarla/esperar turno: obliga a inventar el canal y el interlocutor.
2. «its last paragraph, which starts «Mi lectura, sin confirmar por el dueño», is replaced by the owner's answer and what follows from it» — «what follows from it» no acota extensión ni contenido: obliga a inventar cuánta inferencia añadir.
3. «If 'deskops show task task-finish-wip-leer-readers-by-class' says it is closed» — no indica cómo se invoca ni qué hacer si falla o no responde: obliga a inventar el procedimiento de consulta.
4. «the newest folder runs/subagents/*-leer/» y «the REPORT.md» — «newest» sin criterio (mtime vs. nombre) y sin qué hacer si hay varios REPORT.md: obliga a inventar el desempate.
5. «a new section titled with the date of the answer» — no fija formato de título: obliga a inventar encabezado.
6. «holds in a commit on main» — no dice quién commitea ni si el ejecutor puede: obliga a inventar autoría y firma.

## DRY-RUN

**Lecturas:** `sesion-2026-11-15.md`, `diseno-leer.md` (§6), `traspaso-2026-11-17.md` (§6), `runs/research/2026-11-15/reemplazo-sldb`, y `REPORT.md` de la carpeta `*-leer` más reciente. **Cambios previstos:** tres ediciones de texto, ninguna de código. **Dependencias:** respuesta del dueño, estado de `deskops show task ...`, existencia de `runs/subagents/*-leer/`. **Validaciones futuras:** los tres checks de §Validation y el commit en main.

## FUTURO

La respuesta del dueño (contenido no inferible), el commit y cierre en main, y la decisión de diseño posterior sobre el criterio 1.
