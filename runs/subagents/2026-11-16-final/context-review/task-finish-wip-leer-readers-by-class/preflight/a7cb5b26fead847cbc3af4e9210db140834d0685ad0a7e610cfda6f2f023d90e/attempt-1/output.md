## REFORMULACION
Propósito: terminar la funcionalidad “readers by class” de `wip/leer`. Resultado: rama rebasada sobre `origin/main`, tests y suite en verde, readers ejecutados sobre una copia del desk real de deskops con números registrados, `main` fast-forwarded y pusheado. Repo: `/home/jp/proyectos/kimun`. Límites: tocar solo los archivos enumerados; copiar el desk y no escribir ni ejecutar `sldb`/deskops bajo `~/proyectos/SLDB_1`; sin caché; sin readers de otras clases; empezar solo cuando cierre la dependencia indicada.

## AMBIGUEDADES
1. “the three tests that fail” y “test/kimun/cli/mcp_test.cljc does the same”: no se identifica el test exacto de `mcp_test`; obligaría a inventar qué aserción falla.
2. “copy the four reader drafts, with their names, to … base.clj, atoms.clj…”: “with their names” puede referirse a ficheros o a namespaces/declaraciones; obligaría a inventar el mapeo exacto.
3. “from $D run 'time <repo>/bin/kimun declare read…'” y luego “'bin/kimun declare terms'”: no queda claro si `bin/kimun` es relativo a `$D` o al repo; obligaría a inventar el path de ejecución.
4. “replace its /home/jp paths by this machine's”: no se listan las ocurrencias ni los valores sustitutos; obligaría a inventar el reemplazo.
5. “the outputs of validate.sh are in the subfolder coordinator/ next to it”: no dice si los escribe `validate.sh` o los copia el ejecutor; obligaría a inventar el mecanismo y la ruta exacta.
6. “commit ('feat(declare): readers by class' + a separate 'chore(evidence)' commit)”: no reparte qué archivos van en cada commit; obligaría a inventar el contenido del commit de evidencia.

## DRY-RUN
Leería el TaskDoc, referencias (`diseno-leer.md`, `reglas-ejecutor.md`, `validate.sh`, `AGENTS.md`), estado de ramas/worktree, código y tests afectados, drafts de readers y estructura de la copia del desk. Crearía un worktree temporal, rebasaría `wip/leer`, resolvería el conflicto de help, ejecutaría los tests dirigidos para observar mapas reales, corregiría código o expectativas, prepararía `$D`, copiaría readers, ejecutaría `declare read`, `terms` y `about`, registraría tiempos/errores, escribiría `REPORT.md`, correría `validate.sh`, haría los dos commits, haría `checkout main`, `merge --ff-only`, push y limpieza. Dependencias: dependencia cerrada, `bb` y pod disponibles, acceso git/push, desk fuente legible, rutas de `validate.sh` ajustadas. Validaciones futuras: `bb lint`, run dirigido sin fallos, `bb test` con `:fail 0 :error 0`, `bb oracle` e `include check`, números del REPORT y evidencias en `coordinator/`.

## FUTURO
Otro nodo o humano confirma la dependencia cerrada, resuelve las ambigüedades, revisa el REPORT/evidencias y decide si el tiempo >10 s activa la tarea de caché.
