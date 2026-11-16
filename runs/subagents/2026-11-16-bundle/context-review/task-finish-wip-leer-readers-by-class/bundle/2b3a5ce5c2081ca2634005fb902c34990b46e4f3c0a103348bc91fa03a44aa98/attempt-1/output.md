## REFORMULACION

Propósito: terminar la rama `wip/leer` de lectores por clase, cuyo ejecutor quedó cortado. Resultado: `wip/leer` rebasada sobre `origin/main`, tests y suite verdes, lectores ejecutados sobre una copia del desk real de deskops con números registrados, `main` fast-forwarded y pusheada, más `REPORT.md` con evidencia. Repo: `/home/jp/proyectos/kimun` (por cwd y rutas `bin/kimun`). Límites: tocar solo `src/kimun/surface/reading.cljc`, sus tests, los tres tests que fallan, `src/kimun/cli/main.cljc` solo para el conflicto del help, los cuatro drafts de readers, `README.md` y `docs/v2/drafts/diseno-leer.md`; prohibido escribir bajo `~/proyectos/SLDB_1`, hacer readers de otras clases o caché.

## AMBIGUEDADES

1. “the three tests that fail” / “first run them to see the actual map”: no identifica cuáles tres ni el mapa esperado; obliga a inventar el diagnóstico tras ejecutar.
2. “Fix the three tests” frente a “if the extra keys appear for a project WITHOUT readers that is a bug in the code (fix the code, not the test)”: no se sabe de antemano si falla código o expectativa; obliga a inventar la causa.
3. “copy the four reader drafts”: no consta su contenido ni formato; “never run”; obliga a inventar cómo tratarlos si fallan.
4. “replace its /home/jp paths by this machine's”: no define máquina ni rutas equivalentes; obliga a inventar el reemplazo.
5. “Write runs/subagents/YYYY-MM-DD-leer/REPORT.md” frente a “validation outputs are under runs/subagents/YYYY-MM-DD-leer/coordinator/”: no aclara dónde van validaciones y evidencia; obliga a inventar estructura.
6. “the four measured numbers”: la lista previa contiene más de cuatro magnitudes (archivos por reader, proposiciones, errores por causa, segundos); obliga a agruparlas.
7. `depends_on: task-toolchain-on-robocop2-and-suite-reproduced`: no aclara si debe estar cerrada; obliga a asumir que sí.

## DRY-RUN

Lecturas: TaskDoc, tests citados, `reading.cljc`, drafts, `diseno-leer.md`, reglas, `validate.sh`, `AGENTS.md`. Cambios previstos: worktree `/tmp/kimun-leer-wt`, rebase resolviendo help, corrección de tests o código, ejecución sobre copia del desk, `REPORT.md`, commits feature y evidencia, ff-only y push. Dependencias: tarea toolchain, git, bb, tree-sitter pod, copia de deskops. Validaciones futuras: `bb lint`; run dirigida; `bb test`; `bb oracle` e include check; conteos del REPORT. Sin ejecutar.

## FUTURO

Decide un humano u otro nodo: si el fallo es de código o de test; si los errores de lectura se aceptan o se corrigen; si >10 s activa `task-cache-of-readings-by-git-blob-hash`; y la revisión final del REPORT, merge y push.
