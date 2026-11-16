REFORMULACION

Nodo ejecutable (`task.code`), no agregado. Propósito: completar “readers by class” sobre la rama `wip/leer`. Resultado: rama rebasada sobre `origin/main`; tests y suite completos verdes; lectores ejecutados sobre una copia del desk real de deskops con métricas registradas; `main` fast-forward y push; `REPORT.md` con resultados y límites. Repo: kimun. Límites: no escribir bajo `~/proyectos/SLDB_1` (solo copiar); fuera de alcance readers de otras clases y cualquier caché; `src/kimun/cli/main.cljc` solo para conflicto de help; depende de `task-toolchain-on-robocop2-and-suite-reproduced`.

AMBIGUEDADES

1. “replace its /home/jp paths by this machine's”: no define “this machine” ni las rutas concretas; obligaría a inventar sustituciones.
2. “bin/kimun include check is clean”: no indica invocación exacta ni salida esperada; obligaría a inventar comando y criterio de limpieza.
3. “every other namespace under test/kimun/cli/ whose name starts with declare or stores”: no aclara si “name” es namespace o archivo; obligaría a inventar el conjunto exacto.
4. “each distinct cause is either fixed in the reader or named in the report with its count”: no define “distinct cause” ni umbral de agrupación; obligaría a inventar una taxonomía.

DRY-RUN

Lecturas previstas: TaskDoc, `diseno-leer.md`, `diseno-evaluar.md`, `diseno-generar.md`, `traspaso-2026-11-17.md`, `reglas-ejecutor.md`, `validate.sh`, `AGENTS.md`, archivos y tests de la rama, drafts de readers. Cambios previstos: worktree desde `origin/wip/leer`; rebase; resolver conflicto en `main.cljc` conservando ambos help; corregir los tres tests o el bug de `:summary`; copiar desk y readers a un temporal; ejecutar `declare read` y sanity checks; crear `REPORT.md`; validar; commits de feature y evidencia; ff de `main`; push; limpiar worktree/rama. Dependencias: toolchain cerrada, `bb`, pod tree-sitter, `origin/wip/leer`, deskops legible, `validate.sh` adaptado. Validaciones futuras: `bb lint`, run dirigido de namespaces, `bb test` con pod disponible y `:fail 0 :error 0`, `bb oracle`, `include check`, y números del informe.

FUTURO

Otro nodo debe cerrar `task-toolchain-on-robocop2-and-suite-reproduced`; un humano decide causas de error no resueltas y autoriza el push; la duración de `declare read` alimenta la decisión de `task-cache-of-readings-by-git-blob-hash`; `validate.sh` produce salidas para `coordinator/`.
