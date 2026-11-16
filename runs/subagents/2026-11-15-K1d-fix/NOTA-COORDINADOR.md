# Nota del coordinador

Este corte y `cold-cache-2` corrieron a la vez y los dos usaron `git stash` para la
comprobación de mutación, como pedían las reglas del coordinador. El stash es común a todos
los worktrees del repositorio: cada ejecutor recuperó el del otro y los cambios quedaron
mezclados en ambos worktrees. Se recompuso por rutas (los dos cortes no comparten archivos)
en un worktree limpio sobre `ed9514e2`; las cuatro versiones de `src/` eran idénticas en los
dos worktrees. Las salidas `directed.txt`/`mutation.txt` de este directorio pudieron correr con
cambios del otro corte presentes: **la validación que vale es la de
`runs/subagents/2026-11-15-integracion-I/`**, sobre el árbol recompuesto.

Regla corregida: la mutación ya no usa `git stash`.

Hallazgo de Pi (punto 3 del brief): dos archivos que declaran el mismo nombre con contratos
distintos hacen que el segundo redefina al primero dentro del mismo build (el store termina en
la versión 2); `13-include.md` no dice nada sobre nombres duplicados. No se cambió.
