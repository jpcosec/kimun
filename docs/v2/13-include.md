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
| `:store` | `{:path "…" :git :ignore}`: dónde vive el store y si git lo versiona. `:ignore` = se construye en la corrida y nunca se commitea; `:track` = el repo persiste su store. Admite `:content :inline` (docs/v2/10 §10): el store guarda el texto de cada documento en vez de leerlo del archivo. |
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
`# <<< kimun include <<<` la ruta del store (cuando `:ignore`), la ruta de su
caché derivado (`<store>/cache/`, nunca versionado: `10 §11`) y un
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
:models []      ; los modelos del desk se retiraron el 2026-10-09 (docs/archive/desk/README.md)
:documents []
```

El 2026-10-09 el dueño ordenó borrar los modelos del desk en vez de arreglarlos ("lo que
importa es dejar una versión estable; los podemos generar después"): `desk/models/*.edn` ya no
existe y spec 16 los regenerará (`docs/v2/12 §1`, Funciones/Modelos). El formato 1 exige `:model`
en cada regla de `:documents` y no admite documentos sin modelo
(`src/kimun/surface/include.cljc`), así que la versión estable es un include vacío: sin modelos y
sin documentos, con los `.md` de `desk/` todavía en git. Los 33 atoms `epoch:v2`, los 21
checklists de los 7 tasks activos y el resto de `desk/` no entran al store hasta que vuelva a
haber modelos.

El store es `.kimun/`, ignorado (`.gitignore`, bloque gestionado) y construido
al vuelo:

```bash
bb desk            # alias fino de `kimun include build`
bin/kimun include check   # limpio en este repo
```
