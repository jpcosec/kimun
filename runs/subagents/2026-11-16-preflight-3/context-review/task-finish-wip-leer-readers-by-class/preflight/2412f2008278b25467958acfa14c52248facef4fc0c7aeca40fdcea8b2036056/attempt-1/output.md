## REFORMULACION

**Propósito:** cerrar el corte 1 del diseño "readers by class" (`docs/v2/drafts/diseno-leer.md` §3-§5) sobre el código ya escrito en la rama `wip/leer` (commit 83379739 sobre main 1f933ab0), cuyo ejecutor quedó interrumpido con tres tests viejos en rojo y sin ensayo real.

**Resultado:** `wip/leer` rebasada sobre `origin/main`, tests y suite completos en verde, lectores ejecutados sobre copia del desk real de deskops con números registrados, y `main` fast-forwarded y pusheada con dos commits (feature + evidence).

**Repo:** `/home/jp/proyectos/kimun`. Fuera: escrituras en `~/proyectos/SLDB_1`, lectores de otras clases, caches.

**Límites:** in-scope `reading.cljc` y sus tests ya en rama; `main.cljc` solo para resolver conflicto de help; drafts `deskops-readers/{base,atoms,tasks,materializations}.clj`; README.md y el diseño.

## AMBIGUEDADES

1. "replace its /home/jp paths by this machine's" — exige inventar qué rutas de `validate.sh` sustituir.
2. "runs/subagents/YYYY-MM-DD-leer/" frente a "runs/subagents/2026-11-16-leer/" en Scope — exige decidir qué fecha fija usar (¿día de ejecución o 2026-11-16?).
3. "keep both sides' help lines in src/kimun/cli/main.cljc" — exige inventar el texto final.
4. "REPORT.md states the four measured numbers" vs. Validation que enumera "Markdown files matched by each reader" (plural por lector) — exige decidir si es un número por lector o agregado.
5. "every other namespace under test/kimun/cli/ whose name starts with declare or stores" — exige decidir si "name" es fichero o namespace Clojure.

## DRY-RUN (hipotético, sin ejecutar)

- **Lecturas:** diseño §3-§5, `reglas-ejecutor.md`, `validate.sh`, `AGENTS.md`, rama `wip/leer`, tests que fallan.
- **Cambios previstos:** rebase en worktree `/tmp/kimun-leer-wt`; posible fix de código en `declare.cljc` (si las claves extra aparecen sin lectores) o ajuste de mapas esperados en 3 tests; copia de desk a `mktemp -d`; copia de 4 drafts; commit de copia; ejecución `declare read`, `declare terms`, `declare about`; REPORT.md; validate.sh; dos commits; merge ff-only; push; limpieza worktree y rama.
- **Dependencias:** `depends_on` cumplido (`task-toolchain-on-robocop2-and-suite-reproduced`).
- **Validaciones futuras:** `bb lint`, run dirigida, `bb test` (`:fail 0 :error 0`), `bb oracle`, `include check`, cuatro números en REPORT.md.

## FUTURO

Humano confirma rutas de `validate.sh` y fecha de la carpeta evidence. REPORT.md con >10 s activa `task-cache-of-readings-by-git-blob-hash`. Revisión del merge en `origin/main`.
