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

## Lo que NO se archiva

- `desk/primitives/{checklists,conditions,edges,operators}/**/*.md`: están declarados por
  `kimun.include.edn` y son los documentos de `ChecklistDoc`, `ConditionDoc`, `EdgeDoc` y
  `OperatorDoc` (287 documentos del store).
- `desk/registry/repo-sldb-refactor-worktree.md`: declarado por `desk/registry/**/*.md`, es el
  único `RepositoryDoc` del store.
