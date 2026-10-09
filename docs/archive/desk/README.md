# Archivo: desk

Material de `desk/` que ya no forma parte del store `.kimun`, congelado el 2026-10-08.
`kimun.include.edn` define lo que el repo versiona; todo lo que queda bajo `desk/` entra al
store al construir con `bin/kimun include build`. Lo que está aquí no lo declara
`kimun.include.edn`, no es un `desk/models/*.edn` y no lo lee ningún documento del desk.

## Contenido

### `desk/config.json`

Era el descriptor de proyecto de la etapa deskops (`project_identity`, `versions.desk_format`,
bloque `sandbox`). Se archiva el 2026-10-08 porque ningún consumidor lo lee: `grep` sobre
`src/`, `scripts/` y `bb.edn` no lo referencia, y el store se construye hoy desde
`kimun.include.edn`, no desde este fichero. Superado por `kimun.include.edn` + `bin/kimun`.

### `desk/atoms/tag-namespaces.yaml`

Era el registro jerárquico de espacios de nombres de tags de deskops
(`system.sldb`, `domain.architecture.*`, con `spec2viz_nodes`). No es Markdown, así que el glob
`desk/atoms/**/*.md` nunca lo incluyó en el store; `grep` lo encuentra solo en el inbox
`desk/inbox/20260829-154745-unclear-deskops-atoms-add-namespace-overwrites-...md` y en material
ya archivado (`docs/archive/architecture/...`). Se archiva el 2026-10-08 por ser andamiaje de
`deskops atoms add-namespace`, sin lector en el kimun actual (el CLI no lee YAML de tags).

### `desk/roles/` (vacío)

Directorio vacío del andamiaje deskops (roles de workers), nunca declarado por
`kimun.include.edn` ni con contenido. Se mueve al archivo el 2026-10-08 para que `desk/` solo
contenga lo que kimun usa.

### `desk/primitives/hooks/` (vacío)

Subdirectorio vacío bajo `desk/primitives/`: a diferencia de `checklists/`, `conditions/`,
`edges/` y `operators/`, no está declarado por `kimun.include.edn` (no hay modelo `HookDoc`) y
no contiene ficheros. Se mueve al archivo el 2026-10-08 como resto del andamiaje deskops.

### `desk/contexts/vistas-uml.annotations.json`

Eran las marcas persistentes del servidor local de anotaciones de las vistas UML
(`scripts/annotate_server.py`, etapa pre-v2). No es Markdown, así que el glob
`desk/contexts/**/*.md` nunca lo incluyó en el store. Se archiva el 2026-10-08 junto al script
que lo lee (`docs/archive/scripts/annotate_server.py`), ya retirado de `scripts/`: con el script
bajo `docs/archive/scripts/`, su `ROOT` pasa a ser `docs/archive/`, así que la copia archivada en
`docs/archive/desk/contexts/` es la que el servidor archivado resuelve. Sin lector activo en el
kimun actual.

## Retirada del legado pre-v2 (2026-10-09)

Auditoría L4 (`/home/jp/.cache/coord-briefs/AUDIT-L4-report.md`). Se retira lo que el store
versionaba pero contradecía `docs/v2/` o ya no tenía lector:

### `desk/atoms/*.md` (186 atoms)

Todos los atoms sin tag `epoch:v2` (186 de 219); los 33 restantes son los únicos vigentes.
Llevan `provenance: raw/…`, una ruta que ya no existe (el material está en
`docs/archive/raw/source/`), y su vocabulario es pre-v2: "canonical AST is the structural source
of truth" (`canonical-ast.md`), "redb default, Cozo" (`storage-backend.md`), "tree-sitter WASM",
"model = template". Por qué: `docs/archive/README.md` declara `docs/v2/` autoridad única y la
misma operación se hizo con los 16 retirados el 2026-08-29. Al moverse, se vacía el conjunto
`:force` de `kimun.include.edn` (185 entradas que existían solo para versionarlos).

### `desk/primitives/checklists/*.md` (168 checklists)

Todos los checklists salvo los 21 que referencian los 7 tasks activos (execution/testing/closeout
de cada uno). Los 168 restantes son de slugs `checklist-task-implement-*` (53) y de tres tasks
pre-v2 (`importers-emitters-and-semantic-indexing`, `replicate-v1-cli-workflow-capabilities`,
`track-atom-work`) que no tienen `desk/tasks/task-<slug>.md`; los tasks pre-v2 vivían en
`desk/drawer/`, archivado el 2026-10-08.

### `desk/tasks/task-{freeze,specify}-*.md` (10 tasks)

Los 10 tasks planning-era en estado `closed`; sus `references`/`files` apuntan a
`raw/source/architecture-retired/…`, `docs/architecture/contracts/…` y
`desk/contexts/pill-planning-contracts.md`, todo archivado y sin ruta viva. Sus contratos y pills
ya estaban en `docs/archive/architecture/` y `docs/archive/desk/contexts/`.

### `desk/models/*.edn` (todos, 2026-10-09)

Primero se retiraron los 6 modelos sin ningún documento y sin `:glob` en `kimun.include.edn`:
`BoardDoc` (solo del `desk/tasks/Board.md` legado, excluido del store),
`CrossroadDoc`, `FAQDoc`, `ProtoAtomDoc`, `RoleDoc` y `StepDoc`. `StepDoc` queda referenciado por
`:py/containment` de `RoutineDoc`, composición que resuelve el módulo Python heredado de v1, no el
modelo del desk.

El mismo 2026-10-09 el dueño ordenó **borrar los modelos del desk** en vez de arreglarlos
("lo que importa es dejar una versión estable; los podemos generar después"): se retiran los 5
restantes (`AtomDoc`, `ChecklistDoc`, `ConditionDoc`, `EdgeDoc`, `InboxNoteDoc`, `OperatorDoc`,
`PillDoc`, `RepositoryDoc`, `RitualDoc`, `RoutineDoc`, `TaskDoc` y las bases `PrimitiveDoc` /
`OperationalArtifactDoc`) y `kimun.include.edn` queda **sin `:models` y sin `:documents`**: el
formato 1 exige `:model` en cada regla de documento y no admite documentos sin modelo
(`src/kimun/surface/include.cljc`), así que la versión estable es un store vacío. El porqué de
los modelos que se retiraron se conserva en la tabla de arriba; los `.md` de `desk/` siguen en git
y se re-declarán cuando spec 16 los regenere (`docs/v2/12 §1`, Funciones/Modelos).

### `desk/inbox/*.md` (8 notas procesadas)

Notas `open` de la etapa deskops CLI (`next task`/`atoms`/`edit atom`/`doctor`) y del Board legado,
que citan artefactos archivados (`raw/source/atoms-retired/`, `desk/config.json`,
`tag-namespaces.yaml`). Superadas por el flujo kimun + `docs/v2/`.

## Lo que NO se archiva

- Los `.md` que quedan bajo `desk/{atoms,primitives,contexts,registry,rituals,routines,tasks}/`: siguen
  en git como material del desk (33 atoms `epoch:v2`, 21 checklists del los 7 tasks activos, 35
  conditions, 42 edges, 21 operators, 7 tasks, 7 routines, 1 pill, 1 ritual, 1 registry).
  `kimun.include.edn` ya no declara ningún modelo ni documento, así que ninguno entra al store
  hasta que spec 16 regenere los modelos.
- `desk/tasks/Board.md`: legado conservado en `desk/` (`docs/v2/12 §1`: no editable por CLI).
