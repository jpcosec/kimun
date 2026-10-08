# SLDB v2 — Fichero de inclusión (`kimun.include.edn`)

> Un repo decide qué del mundo kimun versiona en git. `kimun.include.edn`, en su
> raíz, lo declara; `kimun include` hace que el repo y git concuerden con él.
> Autoridad: por debajo de `01` y `02`; describe la superficie, no la cambia.

## 1. Qué declara

Cada repo tiene su propio fichero: **nada del código fija la elección de este
repo**. Un repo puede ignorar su store (se construye al vuelo) o versionarlo
(`:git :track`), declarar tres documentos o quinientos.

```clojure
{:include/version 1
 :store {:path ".kimun" :git :ignore}
 :models ["desk/models/*.edn"]
 :documents [{:glob "desk/atoms/**/*.md" :model "AtomDoc"}
             {:glob "desk/tasks/**/*.md" :model "TaskDoc" :force true}]
 :force #{"desk/atoms/cache.md"}
 :exclude ["desk/inbox/archivo/**"]}
```

| clave | significado |
| --- | --- |
| `:include/version` | versión del formato; hoy `1`. |
| `:store` | `{:path "…" :git :ignore}`: dónde vive el store y si git lo versiona. `:ignore` = se construye en la corrida y nunca se commitea; `:track` = el repo persiste su store. |
| `:models` | globs de descriptores EDN (`desk/models/*.edn`) que se definen, en orden base-primero. |
| `:documents` | reglas `{:glob … :model …}`, **gana la primera coincidencia**. |
| `:force` | conjunto de rutas, o `:force true` en una regla: documentos que no ida-vuelta (no se puede re-renderizar lo extraído y obtener el mismo texto). |
| `:exclude` | globs que la declaración **no** gestiona (opcional). Un camino excluido es explícitamente "no un documento kimun": no produce ningún hallazgo, aunque git lo siga. |

### Globs

`**` cruza separadores (`desk/atoms/**/*.md` también casa `desk/atoms/x.md`),
`*` y `?` se quedan dentro de un segmento. El **nombre** de un documento es el
tallo del fichero (`desk/atoms/cache.md` → `cache`); dos documentos declarados
con el mismo nombre son el conflicto `:include/conflict` (salida 4).

## 2. Comandos

```bash
bin/kimun include check              # deriva entre la declaración, git y el store (salida 3 si la hay)
bin/kimun include check --staged     # solo lo preparado: lo que corre el hook pre-commit
bin/kimun include gitignore --write  # bloque gestionado en .gitignore (idempotente, no toca lo ajeno)
bin/kimun include hook --install     # hook pre-commit; encadena uno existente, no lo pisa
bin/kimun include build [--rebuild]  # construye el store desde el plan, en un solo proceso
bin/kimun include status             # recuentos: modelos, documentos por modelo, forzados, versionados, hallazgos
```

`check` compara contra `git ls-files` y el store y falla con `:include/drift`
(salida 3) si algo no cuadra. Con `--staged` solo mira el conjunto preparado:
rechaza ficheros del store cuando `:store :git` es `:ignore`, y documentos que
git sigue bajo un glob gestionado y que la declaración no declara ni excluye.

`gitignore` escribe entre `# >>> kimun include (managed) >>>` y
`# <<< kimun include <<<` la ruta del store (cuando `:ignore`) y un
`!`-re-include por cada glob declarado, de modo que ninguna regla más amplia
ignore un fichero declarado. Nunca toca líneas fuera del bloque.

`hook --install` escribe `.git/hooks/pre-commit` llamando a
`bin/kimun include check --staged`. Si ya hay un hook ajeno, lo copia a
`pre-commit.kimun-prev` y lo encadena; `--uninstall` lo restaura.

`build` es la operación que invoca la task `bb desk` (alias fino vigente): `stores init`,
los descriptores en
orden base-primero y luego cada documento (`docs track`, con `--force` donde la
declaración lo pida). Se niega a tocar un store existente salvo `--rebuild`,
que lo mueve a `<store>.bak-<marca>`.

## 3. Cómo lo usa este repo

```clojure
:store {:path ".kimun" :git :ignore}
:models ["desk/models/*.edn"]          ; 17 modelos
```

Declara 543 documentos con **una regla por directorio**: en cada directorio
todos los documentos comparten modelo, así que basta
`desk/atoms/**/*.md`, `desk/tasks/**/*.md`, etc. Donde solo parte del
directorio necesita `:force`, `:force` es el conjunto de rutas (185 átomos); donde todo lo
necesita, la regla lleva `:force true` (inbox y tasks).

Los ficheros que git sigue en esos directorios pero que **no** son documentos
del mundo kimun se enumeran en `:exclude` (`desk/atoms/testing.md`,
`desk/tasks/Board.md`, las notas viejas de `desk/inbox`, los rituales de ciclo,
`desk/contexts/pills.md`): excluidos, no producen hallazgo aunque sigan en git.
Los directorios sin ninguna regla (`desk/drawer`) no son área gestionada y no
necesitan exclusión.

El store es `.kimun/`, ignorado (`.gitignore`, bloque gestionado) y construido
al vuelo:

```bash
bb desk            # alias fino de `kimun include build`
bin/kimun include check   # limpio en este repo
```
