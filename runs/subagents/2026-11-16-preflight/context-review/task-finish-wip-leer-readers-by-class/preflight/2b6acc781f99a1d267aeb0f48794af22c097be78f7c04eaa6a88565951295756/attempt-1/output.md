## REFORMULACION

Encargo del nodo: completar la tarea `task-finish-wip-leer-readers-by-class` en el repo `kimun`. Propósito: cerrar el corte 1 de `docs/v2/drafts/diseno-leer.md §5`, validando la rama `wip/leer` y avanzándola a `main`. Resultado: rama validada y fast-forwarded a `main`, con métricas medidas sobre una copia real del desk de deskops. Límites: tocar `src/kimun/surface/reading.cljc`, sus tests y los tres tests que comparan el `:summary` completo (`cli/declare_test`, `cli/declare_project_test`, `cli/mcp_test`); depende de `task-toolchain-on-robocop2-and-suite-reproduced`. No ejecuta nada en esta revisión.

## AMBIGUEDADES

1. «adjust the 3 tests»: no se indican aserciones ni valores esperados; obligaría a inventar el cambio concreto en cada test.
2. «Rebase on main» / «fast-forwarded into main»: no hay remoto, commit base ni política de conflicto; obligaría a inventar la estrategia git.
3. «copy SLDB_1/deskops/desk to a temp dir»: falta la ruta base de `SLDB_1`; obligaría a inventar su ubicación.
4. «run readers for atoms, tasks and materialization contracts»: no se dan comandos, entradas ni salidas; obligaría a inventar la invocación.
5. «REPORT»: no se define plantilla ni ruta; obligaría a inventar formato y destino.
6. «files, propositions, errors and times»: no se definen métricas ni unidades; obligaría a inventar qué contar y cómo.
7. «draft in runs/subagents/2026-11-16-leer/deskops-readers/»: no consta si existe ni su nombre; obligaría a inventar el artefacto de partida.
8. «State in docs/v2/drafts/traspaso-2026-11-17.md §3»: no se especifica contenido ni si el archivo existe; obligaría a inventar la redacción.
9. «Full suite with pod green»: no se da comando de pod ni criterio de verde; obligaría a inventar la validación.

## DRY-RUN

Lecturas: TaskDoc, `reading.cljc`, los tres tests citados, el borrador en `runs/subagents/.../deskops-readers/`, `traspaso-2026-11-17.md §3` y el resultado de la dependencia. Cambios previstos: rebase; ajuste de los tres tests; copia temporal del desk; ejecución de readers; REPORT; suite completa; nota en el traspaso. Dependencias: `task-toolchain-on-robocop2-and-suite-reproduced`. Validaciones futuras: `bb test`, suite completa con pod verde y métricas registradas en `runs/subagents/`.

## FUTURO

Un humano decide la aceptación del criterio de done 2 y autoriza el fast-forward a `main`. Otro nodo entrega la dependencia de toolchain. Un revisor valida el REPORT y las métricas antes del cierre.
