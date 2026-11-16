REFORMULACION
Propósito: cerrar la feature “readers by class” de kimun: una declaración `(reading (files "glob") reader-fn)` que recibe una vista de cada Markdown (`:path :front :sections :text`) y declara proposiciones. Resultado: rama `wip/leer` rebasada sobre `origin/main`, tests y suite verdes, lectores ejecutados sobre una copia del desk real con métricas registradas, `main` fast-forwarded y pushed, con commit `feat(declare): readers by class` y otro `chore(evidence)`, más `REPORT.md`. Repo: kimun (`/home/jp/proyectos/kimun`), rama `wip/leer`. Límites: no escribir bajo `~/proyectos/SLDB_1`; copiar el desk y no ejecutar `sldb` ni `deskops` allí; fuera de alcance otros lectores del desk y cualquier caché.

AMBIGUEDADES
1. “copy the four reader drafts into $D/kimun/”: no fija ruta ni nombres destino; obliga a inventar el layout dentro de `$D/kimun/`.
2. “together with twenty other namespaces”: no identifica cuáles; obliga a inventar la lista del run dirigido.
3. “on conflict keep both sides' help lines in src/kimun/cli/main.cljc”: toca un archivo no listado en Scope/files; obliga a decidir si se edita fuera de alcance.
4. “declare terms”, “declare about <one task id>”: no definen sintaxis, salida ni criterio de éxito; obliga a inventar comandos y aceptación.
5. “Fix readers until the reading errors are explained”: no aclara si exige cero errores o solo documentar causas; Validation apunta a documentar, pero hay que decidirlo.
6. “runs/subagents/<date>-leer/REPORT.md”: deja `<date>` sin resolver; obliga a elegir fecha.

DRY-RUN
Lecturas: taskdoc, `diseno-leer.md`, `diseno-evaluar.md`, `diseno-generar.md`, traspaso, reglas de ejecutor, `validate.sh`, `AGENTS.md`, código y tests en `wip/leer`, drafts de readers. Cambios previstos: worktree, rebase, corregir los tres tests —código si las claves aparecen sin readers; si no, expectativas—, ejecutar la prueba real sobre la copia, ajustar readers, escribir `REPORT.md`, validar, commitear, ff/push y limpiar. Dependencias: `task-toolchain-on-robocop2-and-suite-reproduced`, `bin/kimun`, Babashka, tree-sitter pod. Validaciones futuras: `bb lint`; run dirigido de cinco namespaces más veinte; `bb test` con `:fail 0 :error 0`; `bb oracle` e `include check`; métricas de `REPORT.md`. Sin ejecutar nada.

FUTURO
Otro ejecutor produce worktree, commits y métricas; el coordinador recoge las cinco salidas en `runs/subagents/<date>-leer/coordinator/`; un humano decide si el tiempo >10 s dispara `task-cache-of-readings-by-git-blob-hash` y aprueba el push.
