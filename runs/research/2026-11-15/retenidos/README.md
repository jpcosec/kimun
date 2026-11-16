# Cortes hechos y retenidos (2026-11-15)

Tres cortes están implementados y validados por su ejecutor, pero **no integrados en
`main`**: cada uno espera una decisión del dueño (`docs/v2/drafts/preguntas-abiertas.md`).
Vivían solo como commits en worktrees bajo `/tmp`, que un reinicio borra; aquí queda cada uno
como parche (`git am <archivo>` sobre su base) para no perderlo. No son autoritativos ni
están en el producto.

| parche | corte | base | espera (fila de `preguntas-abiertas.md`) | validación hecha |
|---|---|---|---|---|
| `k5b.patch` | K5, segundo corte: `stores gc` (simulacro por defecto; borra con `--yes`; ventana de gracia de una hora) | `ad3b2541` | `GC` — el lock de escritura no protege a un escritor que ya guardó sus objetos | suite completa con pod sobre `ad3b2541`: 1148 tests, 0 fallos (no se guardó la salida en el repo) |
| `m1e.patch` | M1e: proyecciones de generación nombradas (`kimun.surface.models.generate/project`) | `946573a9` | `M1e` — `:mode :sections` y `:sugar` sin definir; el formato Markdown por campos es elección del ejecutor | solo dirigidas del ejecutor; sin suite completa |
| `m2a.patch` | M2a: descriptores spec 16 de anclaje para 3 de los 13 modelos del desk y `kimun.include.edn` | `c8ca2f25` | `M2-desk` (activar el include exige reconstruir el store real del desk) y `M2-derivar` (los 10 modelos con base no se pueden derivar) | solo dirigidas del ejecutor; sin suite completa |

Cada parche trae su directorio de evidencia (`runs/subagents/2026-11-15-…/`) con el informe del
ejecutor. Las bases son anteriores al `main` actual: al retomarlos hay que rebasarlos y
validarlos de nuevo con la suite completa; `k5b` y `m1e` tocan archivos que cambiaron después.
