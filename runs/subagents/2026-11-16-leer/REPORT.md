# REPORT — leer (lectores por clase), corte 1 terminado

Fecha: 2026-11-16. Rama `wip/leer` rebasada sobre `origin/main` sin conflictos. No hay merge, push ni borrado (brief, punto 4).

## Qué se hizo

- Rebase sobre `origin/main` (limpio; `src/kimun/cli/main.cljc` no tuvo conflicto).
- Las tres pruebas viejas (`declare_test` l.16, `declare_project_test` l.29, `mcp_test` l.141): **tras el rebase pasan sin cambiarlas**. El código ya añade `:readings :read-propositions :reading-errors` al resumen solo si el proyecto declara lectores (`src/kimun/cli/commands/declare.cljc:162`, `(seq readings)`), como dice el docstring. El `directed.txt` anterior (en esta carpeta) muestra los fallos de una versión previa; no los pude reproducir. Esas tres pruebas discriminan: si el resumen añadiera las claves siempre, fallan (así fallaron en ese `directed.txt`). No volví a mutar el código para comprobarlo ahora.
- Prueba de realidad sobre una copia del desk (abajo), con los cuatro borradores de lector sin cambios.
- `docs/coordinacion/validate.sh`: `/home/jp` → `/home/robocop`.
- Documentos: `README.md` (viñeta de lectores) y `docs/v2/drafts/diseno-leer.md` (§7 «Decidido al implementar»).
- Pruebas nuevas o ajustadas en esta pasada: ninguna. Las 22 del corte (`kimun.surface.reading-test`, `kimun.cli.reading-test`) traen la mutación de `mutation.txt` (M1–M15 detectadas; M10 y M11 no aplicables, según allí). La hizo el ejecutor anterior; no la repetí.

## Archivos tocados en esta pasada

`docs/coordinacion/validate.sh`, `README.md`, `docs/v2/drafts/diseno-leer.md`, `runs/subagents/2026-11-16-leer/{REPORT.md,coordinator/*}`. El código y las pruebas del corte son los del commit WIP `83379739` (rebasado), sin cambios míos.

## Medido sobre la copia del desk

Copia de `SLDB_1/deskops/desk` en un `mktemp -d` con `git init`, más `kimun/{base,atoms,tasks,materializations}.clj` (los borradores). La copia tiene 920 `.md` (el diseño decía 675; no investigué la diferencia).

| lector (patrón) | archivos que calzan | proposiciones |
|---|---|---|
| `desk/atoms/**/*.md` | 172 | 1031 |
| `desk/materializations/**/*.md` | 113 | 811 |
| `desk/**/task-*.md` | 125 | 839 |

- `:summary` = `{:files 4 :relations 14 :propositions 0 :readings 3 :read-propositions 2679 :reading-errors 0}`.
- `:reading-errors`: **0**; no hay causas que contar.
- `declare read --format edn`: **2,2 s** reales (user 1,4 s), con `memcap` y `flock`, una corrida. Está bajo 10 s, así que no empuja a `task-cache-of-readings-by-git-blob-hash`.
- Preguntar: `declare terms` da 828 términos (136 implícitos), con ids de tareas y átomos; `declare about task-board-no-refleja-deskops-edit-task-goal` devuelve filas (`isa Task`, `has-status`, …) con `:source {:path "desk/tasks/…"}`.
- No comparé con el índice de aristas de sldb (es el corte 2); solo mido que lee y responde.

## Validación (`coordinator/`)

`validate.sh` con 17 espacios de nombres dirigidos (lectura, mcp, todos los `declare*` de cli y surface, `stores`, generate, propositions): lint ok; dirigido 140 pruebas, 1103 aserciones, 0 fallos; `bb test` línea 2 `tree-sitter pod: available`, última línea `{:test 1103, :pass 10395, :fail 0, :error 0, :namespaces 134}`; `bb oracle` 0 (9 filas, 0 diferencias); `include check` 0.

## Ambigüedades del diseño

- `diseno-leer.md §3` no dice si un archivo ilegible (p. ej. no UTF-8) es error del par o se omite. No lo probé: el desk dio 0 errores.
- `desk/**/task-*.md` alcanza también `desk/drawer/tasks/` (hay tareas allí); el diseño no dice si eso es lo deseado. Lo dejo anotado, no cambia el diseño.
- El diseño no fija el límite por archivo de un lector; se usa el de las declaraciones (10 s, `$KIMUN_DECLARE_TIMEOUT_MS`).

## Límites

- Sin caché: relee todo en cada pregunta (2,2 s hoy).
- Solo Markdown con frontmatter YAML; no se leen otras clases del desk (pills, reviews, inbox, relaciones).
- Los lectores de la copia son los borradores sin ampliar.
- La prueba de realidad es una sola corrida en una sola máquina.

## Desvíos

- Los commits quedan en `wip/leer`, no en `main`, por el brief; la tarea decía merge y push.
- El commit WIP se reescribió (reset suave) en dos: `feat(declare): readers by class` y `chore(evidence)`.
