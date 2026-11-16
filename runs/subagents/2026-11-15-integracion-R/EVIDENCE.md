# Integración R — formatos viejos, código muerto y copias, borrados

Corte `sin-legado` (evidencia propia en `runs/subagents/2026-11-15-sin-legado/`) rebasado
sobre `a4abd403` sin conflictos y validado ahí, con pod: `bb lint` ok; `bb test`: `{:test 1176, :pass 12521, :fail 0, :error 0, :namespaces 148}`;
`bb oracle` 9 filas, 0 diferencias; `include check` ok (en el worktree, sin store).

Al revisar se quitaron además cuatro menciones de `stores upgrade` que quedaban (la ayuda de
la CLI, `02`, `05` y `docs/v2/README.md`).

Consecuencia para stores reales: un store que no sea de formato 4 con esquema de rutas 2 ya
no abre. El `.kimun/` del checkout principal era de formato 4 sin esquema de rutas, creado
el 2026-10-09 y **vacío** (0 objetos, 0 revisiones); hay que reconstruirlo con
`include build --rebuild`, que lo mueve a un respaldo.
