# Inventario: lo borrado y la evidencia de que nada lo usa

Base: `main` ec901ac7. Todo vive en el historial git. Líneas de `src/`: +299 / −1.524 (neto −1.225).

## 1. Formatos de store anteriores al 4 y esquema de rutas 1

Evidencia de que nada del presente los crea: `store/init!` escribe siempre `:format-version 4` y
`:route-schema 2` (`store-compare.txt`: store nuevo byte a byte igual al de `main`); las únicas
rutas a formato 1/3 o esquema 1 eran las opciones `{:format-version n}` / `{:route-schema 1}` de
`init!`, de uso exclusivo de pruebas y de `stores upgrade`. `migrate --from-v1` NO depende de
`kernel.legacy` (`grep -rn "kernel.legacy" src` solo daba `kernel.revision` y `kernel.store`).

| Qué | Dónde | Líneas |
|---|---|---|
| namespace `kimun.kernel.legacy.revision` (motor del formato 1; 16 funciones privadas idénticas a `kernel.revision`) | `src/kimun/kernel/legacy/revision.cljc` | −408 |
| `kimun.kernel.upgrade` (`upgrade!`, formato 1→2) | `src/kimun/kernel/upgrade.cljc` | −96 |
| 18 ramas `legacy?` (`revision/legacy?`, `revision/route-schema`, 12 accesores, `diff`, `tree-at`, `apply-plan`, `lazy-store`; `store/heads-record`, `commit!`; `surface/docs` ×4) | `kernel/revision.cljc`, `kernel/store.cljc`, `surface/docs.cljc` | incluidas abajo |
| rama de esquema 1 de `apply-plan` (mapa plano, `route/root`, `map-object`, `entries-digest`, `:routes-write?`) | `kernel/revision.cljc` | |
| `store/upgrade-routes!`, protocolo `HeadsSwap` (+ `swap-heads!` en `fs-store`), protocolo `RouteCache` (+ `read/write-routes!` en `fs-store` y `store-cache`, `routes-dir`, `route-path`, `cache/routes/`) | `kernel/store.cljc`, `host/fs_store.cljc`, `host/store_cache.cljc` | |
| `store/open-from-heads`: cachés/mapa plano de rutas (`flat-route-object?`, `route-object?`, `route-ok?`, `cached-routes`, `cas-routes`) y el parámetro de formato/esquema | `kernel/store.cljc` | |
| `store/init!` aridad 4 (`opts`), `descriptor-route-schema`, `empty-store` por formato, `tx-line` por formato, `plan-of` leyendo `:plan` de la línea, `well-formed-heads?` de formato 1 | `kernel/store.cljc` | |
| `route.cljc`: `schema`, `hashed-entry`, `root`, `canonical-map`, `map-object`, `entries-digest`, `lookup`, `diff` (flat) | `kernel/route.cljc` | −66 |
| `references` de `reachable`: tres formas de formato 1 (`:roots`, vectores de vectores, vectores de strings) y el recorrido por `(:heads store)` | `kernel/store.cljc` | |
| `stores upgrade` entero (`upgrade-store`, `upgrade-routes`, `layout`, `stamp`; ramas 1→2, 2→3, 3→4, esquema 1→2, `:needs-rebuild`), `pack-only!` (la comprobación «formato 4» ya es invariante), `:routes-schema` de `stores check` | `cli/commands/stores.cljc` | −170 |
| `:held` / clase `:plan` del inventario de no alcanzables (planes en el CAS «formatos 1–3»; un store fs de formato 4 nunca los deja en `objects/`) | `kernel/store.cljc`, `cli/commands/stores.cljc` | |

Reemplazo: `store/open`, `replay`, `replay-open` llaman a `supported!`: un store que no sea formato 4 con
`:route-schema 2` falla con **un** error, `:store/unsupported-format` (exit 5):
«store: format N is no longer supported: rebuild with `include build --rebuild`» (con «, route schema 1»
para un formato 4 sin `:route-schema`). Prueba: `kimun.kernel.hardening-test/store-failure-modes`
(formatos 1, 2, 3 y 4/esquema 1 por `open`, `replay-open` y `replay`) y
`kimun.cli.route-schema-test/a-store-without-a-route-schema-is-refused-with-the-declared-error`
(por la CLI) y `kimun.cli.pack-test/a-store-of-another-format-is-refused-by-pack-and-unpack`.

## 2. Código copiado

| Par | Resolución |
|---|---|
| 16 privadas idénticas `kernel.legacy.revision` / `kernel.revision` | desaparecen con el namespace legado (sin extraer módulo común: ya no hay dos motores) |
| `sha256-hex` en `host.store-cache` (string) y `host.fs-store` (bytes), mismo algoritmo | una sola, `store-cache/sha256-hex` (bytes, pública); `fs-store` la usa; `cache-key` convierte el string. Se quitó el import `MessageDigest` |
| `path?` en `kernel.edge` (privada) y `kernel.tree` (pública), mismo cuerpo | `kernel.edge` usa `tree/path?` (no hay ciclo: `tree` no requiere `edge`) |
| `removal` en `kernel.mmap` y `kernel.route-tree` | NO unidos: los valores son `::remove` de dos namespaces (`:kimun.kernel.mmap/remove` ≠ `:kimun.kernel.route-tree/remove`); el informe los llamó idénticos por el texto. Son centinelas de dos espacios distintos de mapas; unirlos haría que un centinela de rutas valga como borrado de mmap. Reportado |
| `dirname` en `host.store-cache` (`"cache"`), `cli.store` (`".kimun"`), `surface.index.cache` (`"index"`) | NO unidos: valores distintos (falso positivo del informe) |

Los demás idénticos del informe (`cp-count`, `id-of`, `tag-id`, `tree-name`, `write-file!`, `python-dir`…) están fuera de kernel/host.

## 3. Código muerto en `kernel/**` y `host/**`

Método: para cada `def`/`defn` público, referencias en todo `src/` por alias resuelto desde el
`:require` de cada archivo (script de /tmp, descartado), con las cadenas y comentarios quitados, y
revisión a mano con `grep` de cada candidato. Borrados (cero usos en `src/`; solo los llamaban sus pruebas):

| Qué | Evidencia | Pruebas que se fueron |
|---|---|---|
| namespace `kimun.kernel.heads` (`commit!` CAS sobre un atom, `cas`, `heads`, `head`, `max-attempts`; 56 líneas) | ningún `require` en `src/`; lo reemplaza `store/commit!` sobre un `Backend` | `heads_test.cljc`; `concurrent-commits-on-disjoint-trees-all-land` de `hardening_test` |
| namespace `kimun.kernel.pool` (`empty-pool`, `put`, `get-node`, `has?`, `size`; 34 líneas) | ningún `require` en `src/` | `pool_test.cljc` |
| namespace `kimun.kernel.standoff` (capas UAX #29 sobre una hoja, 88 líneas) + `host.default/layer-cache` | ningún `require` en `src/`; solo un docstring de `host.default` | `standoff_test.cljc` |
| `node/classes`, `node/kinds` | cero referencias en `src/` ni en pruebas | — |
| `store-cache/read-object`, `store-cache/object-ids` | cero referencias; `fs-store` lee la caché con sus propias funciones | — |
| `:require` sin uso: `clojure.string` en `kernel.plan`, `clojure.java.io` en `host.fs-store`, `java.security.MessageDigest` en `host.fs-store` | script de alias; `bb lint` sigue verde | — |

NO borrados aunque nada de `src/` los llama (decisión del dueño, ver REPORT): `kernel.reconcile`
(`orphans`, `proposals`, `accept-plan`, `default-min-confidence`; solo `dice` se usa) y de
`kernel.anchor` `endpoint-state`, `anchored-in`, `report`, `diff` (la reconciliación de §6.5 de
`docs/v2/02`, con pruebas en `test/kimun/surface/markdown/drift_test.cljc`, fuera de mi alcance);
`tree/paths-of`, `tree/hash-at`, `route-tree/from-entries`, `route-tree/diff`, `store/replay`,
`fs-store/cached-ids` (solo pruebas, pero son los accesores con que esas pruebas inspeccionan hashes/ramas;
borrarlos obliga a reescribir ~40 aserciones sin ganancia).

Ramas inalcanzables quitadas: `pack-only!`, `well-formed-heads?` de formato 1, `(or fv 1)`/`(>= fv n)`, `refresh-views`
sigue (host sin `LazyMaps`, no es un formato).

## 4. Ciclos ocultos por `requiring-resolve`

`grep -rn requiring-resolve src/kimun/kernel src/kimun/host` no da nada; el informe da 0 ciclos
(`kimun-graph-cycles.json`) y `bb lint` pasa. Los dos señalados (`markdown.ast ⇄ cst-ast`,
`cli.commands.include ⇄ cli.main`) quedan fuera de kernel/host y no se tocaron.
