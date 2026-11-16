## REFORMULACION

Propósito: obtener del owner (JP) qué significa «núcleo nuevo de sldb» y registrarlo sin interpretarlo. Resultado: respuesta literal, fechada, incorporada a `docs/v2/drafts/sesion-2026-11-15.md`; sustitución del párrafo correspondiente en `docs/v2/drafts/diseno-leer.md` §6; y actualización del ítem 1 de `docs/v2/drafts/traspaso-2026-11-17.md` §6. Repo: `/home/jp/proyectos/kimun`. Límites: solo preguntar y documentar; no diseñar ni implementar. Es una `task.decision`, no un agregado ni un paquete ejecutable: no hay hijos que recorrer. Permanece abierta hasta que el owner responda.

## AMBIGUEDADES

1. «is replaced by the owner's answer and what follows from it»: no se define el alcance de «what follows». Obligaría a inventar qué consecuencias o texto adicional escribir.
2. «the newest folder runs/subagents/*-leer/»: no se fija criterio de “newest” ni conducta si no existe o hay empate. Obligaría a inventar orden y fallback.
3. «If 'deskops show task task-finish-wip-leer-readers-by-class' says it is closed»: no se indica qué hacer si el comando falla, no existe o no devuelve estado reconocible. Obligaría a inventar el tratamiento de errores.

## DRY-RUN

Lecturas: TaskDoc, los tres documentos citados, el directorio `runs/research/2026-11-15/reemplazo-sldb` y, condicionalmente, el `REPORT.md` del directorio `runs/subagents/*-leer/` más reciente. Cambios previstos: enviar un único mensaje en prosa al owner con la pregunta del Scope; si responde, añadir sección fechada en `sesion-2026-11-15.md`, reemplazar el párrafo de `diseno-leer.md` §6 y actualizar `traspaso-2026-11-17.md` §6 ítem 1. Dependencias: respuesta del owner; opcionalmente estado de `task-finish-wip-leer-readers-by-class` y métricas del `REPORT.md`. Validaciones futuras: comprobar sección fechada con respuesta entre «», párrafo sustituido y prefijo **Respondida (dueño, YYYY-MM-DD):**; commit en `main`; no cerrar sin respuesta.

## FUTURO

El owner responde; el coordinador registra literalmente y prepara el commit. Otro nodo o un humano valida las tres comprobaciones y decide el cierre.
