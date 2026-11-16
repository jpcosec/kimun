# Integración I — dos cortes recompuestos tras un cruce de worktrees

| corte | evidencia propia |
|---|---|
| K1d-fix — un modelo declarado por dos archivos cuenta una vez (Pi) | `runs/subagents/2026-11-15-K1d-fix/` (con nota del coordinador) |
| cold-cache-2 — archivo ausente con caché fría es un error declarado; una sola comprobación uno a uno | `runs/subagents/2026-11-15-cold-cache-2/` |

Los dos ejecutores usaron `git stash` a la vez (lo pedían las reglas del coordinador) y, como
el stash es común a todos los worktrees, terminaron con los cambios del otro. Se recompuso
por rutas en un worktree limpio sobre `ed9514e2`: dos commits, sin archivos en común. La
regla se corrigió (mutación con parche propio en `/tmp`, sin stash).

**Validación del árbol recompuesto**, con pod: `bb lint` ok; dirigidas (8 namespaces) 0
fallos; `bb test`: `{:test 1223, :pass 14434, :fail 0, :error 0, :namespaces 151}`; `bb oracle` 9 filas, 0 diferencias; `include check` ok.
Las corridas dirigidas y de mutación de cada ejecutor pudieron incluir cambios del otro corte:
no se toman como evidencia de cada corte por separado.

Pendiente menor: `docs/v2/06` no lista `docs/file-missing` en su tabla de códigos de salida.
