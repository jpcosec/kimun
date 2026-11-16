# find-genericos: `find` y `sections` ven los documentos genéricos

## Causa
`src/kimun/surface/index/build.cljc` (`build`, líneas ~114-132 antes del parche): `:docs`, `:by-name`, `:tags`, `:sections` salían solo de `tdocs/documents` (tipados). Los genéricos (`tdocs/generic-documents`, `src/kimun/surface/docs.cljc:1015`) nunca entraban al índice, y `search/records` (`src/kimun/surface/index/search.cljc`, `doc-records` sobre `(vals (:docs index))`) solo genera registros de ahí. `sections` usaba `tdocs/find-document` (nil para genéricos, `docs.cljc:389`). `docs/v2/06` lo declaraba como límite (línea 303).

## Cambios (worktree /tmp/kimun-find-wt, sin commit)
- `build.cljc`: nueva clave `:generics {tree-id entry}` (versión del índice 1 -> 2; `contract-keys` la incluye). Entry: `{:tree :doc :name :class :path :preamble :sections [{:path :slug :title :level :line_start :line_end :text}]}` o `{... :error "msg"}` sin secciones si no se puede leer. El texto sale de `tdocs/document-markdown` (el mismo camino de `docs render`: caché de árboles, si no el archivo); `:text` = líneas propias de la sección (debajo del encabezado hasta el siguiente encabezado de cualquier nivel). `skipped` también incluye genéricos ilegibles. No toca `:docs`, `:by-name`, direcciones ni fingerprint.
- `cache.cljc`: `valid?` exige `:generics` (mapa); la versión 2 invalida cachés viejas (`:stale`); el reporte de `rebuild!` añade `:generic-documents`.
- `search.cljc`: `generic-records` (un `:doc` y un `:section` por encabezado, `:model nil`, `:class`, texto en `:body`); `matches-of` añade un calce de texto `{:in :physical :value <primera línea que calzó> :line N}`; `search/warnings` (`find/unreadable-document`). `public` quita `:body`.
- `find.cljc`: pone `:warnings` y, en `find/empty`, `:unreadable [nombres]` en `:error :data`.
- `index.cljc` (`rebuild-indexes`): el warning `index/skipped-document` resuelve también genéricos.
- `sections.cljc` (comando) y `surface/sections.cljc`: `sections list|show|find` aceptan genéricos (`:model nil`, `:class`; sin tags; `--model M` los excluye; ilegible -> `sections/unreadable`); `sections fields` sobre uno da `cli/usage` (6) "has no fields: it is a document without model". `sections find` busca por título/slug/ruta, no por texto (el texto es de `find`).
- Docs: `docs/v2/06-superficies-y-cli.md` (frase del límite, sección `find`, filas de `sections`) y `README.md` (una línea `kimun find cache --format text` en el tour, ejecutada tal cual con `bin/kimun` en un git temporal: salió exit 0 con el resultado de abajo; ver `repro.txt`).

## Forma de los resultados nuevos (real, `kimun find cache`, notes.md = "# Notas / Algo sobre el cache derivado. / ## Otra / Mas texto aqui.")
```
{:kind :section :doc "notes" :name "notas" :section "notas" :path "notes.md#notas" :title "Notas" :level 1 :line 1
 :class "generic/md" :model nil :breadcrumbs ["Notas"] :about ["Notas"] :store "my-kb" :tree "01M7…"
 :matches [{:in :physical :value "Algo sobre el cache derivado." :line 3}]}
```
`find ""` da `:store`, `:doc` (`{:kind :doc :name "notes" :path "notes.md" :class "generic/md" :model nil}`) y las dos `:section`. `--in semantic` no da nada (sin tags). Antes: `find/empty` exit 1 para todo esto (`repro.txt`, sección `orig`).

## Pruebas
Nuevo `test/kimun/cli/find_generic_test.cljc` (7 pruebas, 57 aserciones): reproducción exacta (3 capas), párrafo -> sección correcta y línea, título, tipado+genérico en un store (cada uno con su clase; `st.{Note}` no ve el genérico), `--type/--regex/--fuzzy/--limit/--where/--select` y `find ""`, archivo ausente (warning y `find/empty` con `:unreadable`), índice en caché (contiene `:generics`, `find` lo lee con el archivo borrado, uno de versión 1 se descarta y se reconstruye), `sections list/find` y `fields` con error claro.
Ajustada: `test/kimun/cli/index_test.cljc` línea 47 (el reporte de `rebuild-indexes` ahora trae `:generic-documents 0`). Ninguna prueba fijaba que `find`/`sections` no vieran genéricos.
- Mutación (`mutation.txt`): con `src/` original (git checkout de src, restaurado con parche, `diff -r` idéntico) fallan las 7 pruebas (34 fallos).
- `lint.txt` ok (109 archivos); `oracle.txt` 9 filas, 0 diferencias.
- `directed.txt`: 116 pruebas/1075 aserciones (índice x6, find, sections, fields, generic-docs, index, corpus + la nueva) y 65/545 (docs, typed-docs, golden, cold-cache, linked-models, http models-fields y documents, lint-test): 0 fallos.
- `joint.txt`: 22 namespaces (la nueva + 21 de `test/kimun/cli/`) en una invocación: 198 pruebas, 2404 aserciones, 0 fallos.

## Medición (`measurement.md`, load average ~100: ruido alto)
Tipados (200 docs): sin diferencia fuera del ruido (find 67/71 ms -> 71/74 mín/mediana; rebuild 158/168 -> 213/231 ms, dentro de la variación entre tandas). Genéricos (200 docs): antes `find` no veía nada (1 ms); ahora rebuild-indexes ~345 ms (antes 3 ms), `find` con índice en disco 15-27 ms, sin caché ~530-650 ms (antes ~50). Es el costo de leer cada árbol (~1.7 ms/doc); no afecta a stores sin genéricos.

## Límites
- `find` sin índice en disco lee el árbol de cada genérico en cada invocación (como `docs render`); si la caché de árboles está fría, `doc-tree` puede reescribir `<store>/cache/` (comportamiento existente del resolvedor, no del índice). `rebuild-indexes` lo evita.
- El texto es el Markdown canónico del árbol (números de línea de ese texto, que pueden diferir del archivo si no está en forma canónica).
- El `:doc` genérico solo lleva nombre, ruta, `generic/md/NAME` y el texto previo al primer encabezado; el cuerpo vive en las secciones. Un genérico no es alcanzable por direcciones (`st.`, `se.`, `gse.`), ni por `fields`. Un archivo sin ningún encabezado: su texto es el `:preamble` (calza en el `:doc`).
- `sections find` no busca por texto. `include` sigue sin admitir genéricos (fuera de este corte).
- No hay commit; el otro ejecutor no fue tocado (`declare*`, `generate`, `mcp`, `main.cljc`).
