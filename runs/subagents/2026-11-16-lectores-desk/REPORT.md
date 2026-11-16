# Lectores para todas las clases del desk de deskops (corte 2 de `diseno-leer.md §5`)

Corrida del 2026-11-16, sobre una copia del desk (`/home/robocop/proyectos/SLDB_1/deskops/desk`, 929 Markdown) en un
`mktemp -d` con `git init`. Comando: `run.sh D declare read --format edn` (copia `readers/*.clj` a `D/kimun/`, hace
commit en la copia y llama a `bin/kimun` con `flock`, `KIMUN_POD` y `memcap 3`). Salida completa: `read.edn`.

## Resultado

- `:summary` de `declare read`: `{:files 14, :relations 58, :readings 13, :read-propositions 6133, :reading-errors 0}`.
- **Cobertura** (`coverage.txt`, generado con `coverage.py`): 929 Markdown, 874 con frontmatter, 55 sin él;
  **0 archivos con frontmatter y sin proposición**; 874 fuentes distintas en `read.edn`.
- **Tiempo de `declare read`**: 8.2 s, 4.3 s y 3.9 s en tres corridas seguidas (`timings.txt`; la primera incluye el
  arranque del pod). Menos de 10 s: **la caché por hash de git (`task-cache-of-readings-by-git-blob-hash`) no la pide
  este número**, aunque 4–8 s por pregunta no es poco. La salida de las tres corridas es idéntica (`cmp`) entre la 1ª y la 3ª.
- 0 errores de lectura, así que no hay causas que contar.

## Por lector (patrón, archivos que calzan, proposiciones leídas)

| lector | patrón | archivos | proposiciones |
|---|---|---:|---:|
| atoms | `desk/atoms/**/*.md` | 172 | 1031 |
| board | `desk/tasks/Board.md` | 1 | 45 |
| contexts (Pill) | `desk/contexts/**/*.md` | 31 | 178 |
| drawer-tasks (DrawerTask) | `desk/drawer/tasks/**/*.md` | 82 | 649 |
| inbox (InboxNote) | `desk/inbox/**/*.md` | 77 | 448 |
| lifecycle (LifecycleRule / LifecycleRelation) | `desk/lifecycle/**/*.md` | 22 | 79 |
| materializations | `desk/materializations/**/*.md` | 115 | 825 |
| registry (Repository) | `desk/registry/**/*.md` | 6 | 20 |
| reviews (Review) | `desk/reviews/**/*.md` | 315 | 2276 |
| rituals (Ritual) | `desk/rituals/**/*.md` | 7 | 36 |
| roles (Role) | `desk/roles/**/*.md` | 3 | 31 |
| runtimes (Runtime) | `desk/runtimes/**/*.md` | 3 | 51 |
| tasks | `desk/tasks/**/task-*.md` | 43 | 466 |
| **total** | | | **6133** |

(Las cifras por lector son las de `:readings` de `read.edn`; suman 6133 con las proposiciones repetidas contadas por lector.)
Cambios de glob respecto del corte 1: solo `tasks` (`desk/**/task-*.md` → `desk/tasks/**/task-*.md`), para que una tarea del
drawer sea `DrawerTask` y no también `Task`. Añadí `board.clj`, que el encargo no pedía: `desk/tasks/Board.md`
(frontmatter `id`, `scope`, `tasks`) era el único archivo con frontmatter sin lector; declara `isa Board`, `has-scope` y
`has-tasks` (cada ruta de tarea reducida a su nombre sin `.md`, que es el id de la tarea).

## Archivos sin frontmatter (no se leen por diseño)

`(raiz)` 1 (`README.md`), `contexts` 2 (`README.md`, `pills.md`), `drawer` 1 (`README.md`), `drawer/plans` 47,
`drawer/tasks` 1, `logbook` 3. Total 55. El resto de carpetas, 0.

## Referencias normalizadas

Un único `ref-id` convierte `doc:Model:id@sha256:HASH`, `doc:Model:id` y `Model:id` en el id desnudo; todo lo demás
queda igual. Medido en el desk (`reference-forms.txt`, con python/yaml aparte): **3 formas crudas distintas, 121 valores
crudos distintos** normalizados (3 `RitualDoc:…` en `source_id`, 3 en `target_id`, 37 `doc:Model:id` y 275 `doc:Model:id@sha256:…`
en `subject`; las cifras son apariciones). Tres ejemplos antes → después:

- `RitualDoc:ritual-closeout` (`source_id` de `closeout-task_completes`) → `ritual-closeout`
- `doc:TaskCodeDoc:task-cc35b-selectores-resolución-de-documentos-por-sldb-api-select-documents` → `task-cc35b-selectores-resolución-de-documentos-por-sldb-api-select-documents`
- `doc:TaskDocsDoc:task-manual-sin-atoms-split-en-el-ejemplo-ejecutable-sldb-rechaza-encabezados-en-el-field-desd@sha256:…` → `task-manual-sin-atoms-split-en-el-ejemplo-ejecutable-sldb-rechaza-encabezados-en-el-field-desd`

En `read.edn` se ve: `has-subject` de `review-task-add-task-y-el-doctor-…-bundle-1` tiene `:b` `task-add-task-y-el-doctor-…`.

## Claves omitidas (regla c: mapas, texto largo, no cadenas)

`sldb` (mapa, en casi todas las clases), `condition`, `where`, `message`, `attributes` (lifecycle), `validation`
(materializaciones), `reason`, `value`, `run_dir`, hashes y fechas (`created_at`, `acknowledged_at`, `disposed_at`,
`input_sha256`, `output_sha256`) en reviews/inbox, `steps` (prosa) en rituales, `description` en roles, `system_prompt`
en runtimes, `history`, `references`, `inherits_from`, `summary`, `from_drawer`, `scope`, `tasks`, `rituals` en tareas,
`provenance` y `path` en átomos, y los booleanos (`inherit_acceptance_context`, `inherit_project_context`,
`inherit_skills`). `value` de reviews no figuraba en la lista del encargo.

## Límites de kimun encontrados (no se tocó `src/`)

1. **Los helpers no se comparten entre lectores.** El encargo pedía un helper en `base.clj` usado por todos. `read-project`
   solo importa relaciones de otro contexto, nunca `def`s. Comando: `declare read` con `each` en el `:refer` →
   `{:type "declare/unknown-relation", :message "kimun/atoms.clj:1: context `desk.base` does not define a relation `each`"}`.
   Las relaciones además son macros: pasar una como valor da `declare/eval … Can't take value of a macro: #'desk.base/answers`,
   por eso `each` es un `defmacro`. Solución: el bloque de helpers vive canónico en `readers/base.clj` y
   `sync-helpers.py` lo copia, idéntico, en cada lector (entre `;; Referencias` y `;; --- fin de los helpers compartidos`).
   Todos usan el mismo texto, pero está copiado trece veces.
2. **Frontmatter con una línea en blanco adentro: kimun lo toma por no-frontmatter.** `frontmatter-end` en
   `src/kimun/surface/markdown/cst.cljc` devuelve nil si hay una línea en blanco antes del `---` de cierre. 44 reviews tienen
   un escalar entre comillas de varias líneas con líneas en blanco (`reason:`/`value:`). Para ellas la vista `doc` llega con
   `:front nil` y **sin error**, así que `declare read` daba 0 errores y esos 44 archivos quedaban sin proposición
   (primera medición: 829 de 874 cubiertos, 45 faltantes = 44 reviews + `Board.md`). Python (`yaml.safe_load`) sí los
   parsea. Ejemplo mínimo: `desk/reviews/task-desk-update-migra-a-draft-…/preflight-1.md` aislado en un proyecto con
   `reviews.clj` → `:matched 1 … :propositions 0, :errors 0`.
   Solución solo en el lector de reviews: si `:front` es nil y el texto empieza con `---`, `fallback-front` lee las claves
   de primer nivel de una línea (`clave: valor`) y las listas `- x`. `fallback-check.txt` compara, para los 44 archivos, las 9 claves
   que se leen (`id kind fact subject subject_ref engine actor verdict tags`) contra `yaml.safe_load`: 0 diferencias.
   Lo que no verifiqué: esa comparación la hice con un port a python del mismo algoritmo, no con el código Clojure; lo que
   sí comprobé en kimun es que los 44 archivos tienen ahora al menos una proposición.
   La solución verdadera está en `cst.cljc`; no está hecha.

## `declare about` (`about.txt`)

Un `declare about` por clase nueva, con el primer id alfabético de cada una, más `Board`: Pill `pill-001` (6 filas),
DrawerTask `task-atoms-split-no-se-puede-…` (5), InboxNote `20261019-112931-suggestion-…` (6), LifecycleRule
`rule-activation-task-aggregate` (4), LifecycleRelation `closeout-task_completes` (4), Repository `Chatbot_Infra` (5),
Review `review-deskops-como-config-cc51-node-1` (7), Ritual `ritual-closeout` (8), Role `role-deskops-executor` (7),
Runtime `runtime-claude` (14), Board `board-001` (45). Todas con `:ok true` y al menos una fila.

## Condiciones y lo que no puedo afirmar

- `git status` de `/home/robocop/proyectos/kimun`: solo la carpeta del run.
- **`git -C …/SLDB_1/deskops status --short` NO dio lo mismo antes y después** (`sldb-status-before.txt` 13 líneas,
  `sldb-status-after.txt` 15): aparecieron ` M deskops/services/review.py` y ` M tests/test_review_command.py`. Yo no escribí
  nada ahí (solo leí con `cp -r`, `python3` y `awk`, y no ejecuté `sldb` ni `deskops`); en ese lapso otra sesión editó esos
  archivos, y `ps` muestra procesos de deskops corriendo. No puedo demostrar que fueran de otro, solo que mis comandos no escriben.
- No comparé con el índice de aristas de sldb (fuera de alcance).
- Temporales en `mktemp -d` (copia del desk, `/tmp/tmp.s4cmzvvMIV`, y `/tmp/tmp.trcDOaY0Gk`) quedan sin borrar.
- No corrí `bb test`, `bb lint` ni `bb oracle`: no se tocó código de kimun.

## Archivos de la carpeta

`readers/*.clj` (13 lectores + `base.clj`), `read.edn`, `coverage.txt`, `coverage.py`, `about.txt`, `run.sh`,
`sync-helpers.py`, `timings.txt`, `reference-forms.txt`, `fallback-check.txt`, `sldb-status-before.txt`, `sldb-status-after.txt`.
