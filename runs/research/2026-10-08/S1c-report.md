# S1c — Store estable sin contenido: registro por documento + caché por hash (14 §18–§19)

Worktree `/home/jp/proyectos/kimun-worktrees/S1c`, rama `close/S1c` desde `main` (`52e29c53`).
Sin push. Pod `memcap 4 bb pod`. Todo `bb` bajo `memcap` (3 GB; pod/test/build a 4 GB).
Autoridad: `14 §18` y `§19` (leídos enteros), `10-escala.md` (dueño del formato, actualizado
con un §11), `S1-report.md` y `S1b-report.md`.

## 1. Qué cambió por paso

### Paso 1 — registro por documento + caché + `:register-tree` (`afdf3541`)

- **Descriptor del árbol = registro.** Un documento trackeado con archivo se registra con el
  anchor externo (ruta + fingerprint, ya existía), la **clase** (id del descriptor del modelo,
  o `generic/<ext>`) y el **hash de contenido** en el descriptor del árbol, y el **hash raíz**
  en el mmap `:trees` de la revisión (`tree-id → [descriptor-id merkle-root]`, ya existía).
  El descriptor lleva `:class`, `:content-hash` y `:cached? true`.
- **Op `:register-tree`** (kernel, aditivo): registra descriptor + `merkle-root` sin posiciones
  ni tree objects. `plan.cljc` (`ops`, `touched-trees`, `op-tree`), `revision.cljc`
  (`register-tree-op`), `plan.ops` documentado.
- **Caché por hash** (`kimun.host.store-cache`, ring 1): `<store>/cache/objects/<id>`
  (content-addressed: tree objects y nodos `sign/block|text|opaque`) y
  `<store>/cache/docs/<key>.edn` con `key = sha256(fingerprint, clase, versión del parser)`.
- **Fallback de lectura**: `kimun.host.fs-store/read-object` cae al caché si el objeto no está
  en `objects/`. `kimun.cli.store/open!` inyecta `:store-dir` en el valor del store.
- **`kimun.surface.markdown.plan/ast->tree`**: construye el árbol en memoria (posiciones +
  nodos), lo commitea y devuelve `{descriptor :root :merkle-root :objects :nodes}`.

### Paso 2 — el plan no emite árbol para documentos con archivo (`afdf3541`)

- `kimun.surface.docs/registry`/`registry-ops`: `track-plan`, `update-plan` y (vía
  `include build`) emiten `:register-tree` + nodo raíz + anchor externo + aristas
  (referencia al modelo, `derived` al modelo, `reference` al archivo con `:contract`) y la
  retirada de aristas obsoletas. **Sin `:ownership`**, sin `:add-node` de bloques/texto, sin
  tree objects. Nada de eso entra al plan.
- `sign/sign/block` queda en el **nodo raíz** (referenciado por aristas); los demás bloques,
  textos y opacos van al caché. Documentos sin archivo o `:inline` conservan su árbol en el
  CAS (camino de siempre). Los stores formato 1 (legacy) no usan el registro.
- La evidencia de aristas deja de llevar `:field-hashes` (en todos los documentos) y, para los
  documentos con archivo, tampoco `:content-hash` (vive en el descriptor). `describe` lee el
  `:content-hash` del descriptor como respaldo; `diagnostics` distingue reformateo de edición
  comparando payloads.

### Paso 3 — el resolver (`afdf3541`, `053defbb`)

- `kimun.surface.docs/doc-tree` (el resolver): árbol del store si está persistido, del caché si
  es derivado, **recalculado del archivo y reescrito al caché** si el caché no está; devuelve
  `:stale?` (fingerprint del archivo ≠ registrado, o raíz recomputada ≠ registrada).
- `document-markdown`/`document-ast` materializan el árbol resuelto en las vistas del store
  (`with-tree`), para no releer una vista que memoizó el fallo antes de repoblar el caché.
- `cli/commands/docs.cljc` (`docs render`, `docs show --ast`) pasan por el resolver.
  `find-document` se refactoriza para leer el **descriptor** (no el árbol): listar/resolver un
  documento ya no carga tree objects.
- `store/reachable`/`verify`: los tree objects de un árbol `:cached?` no se exigen en el CAS
  (son derivados; su integridad la comprueba el resolver). `revision` gana
  `cached-tree-roots`; `head-views` no mete en `:trees` un árbol cacheado y `lazy-store` cae a
  `load-tree` para un tid ausente de la vista.

### Paso 4 — detección y reconciliación (`afdf3541`)

- `stores check|health` clasifican como hoy: fingerprint ≠ registrado ⇒ `:edited` (campos
  cambiados) o `:reformatted` (mismo contenido), con acción `docs update NAME`; archivo ausente
  ⇒ `:missing`; contrato sucedido ⇒ `:contract-moved` (el documento se lee bajo su clase
  original). `docs update`/`stores update` re-registran fingerprint, clase y hash raíz con
  `:register-tree`.
- Desviación de nombres: el brief pide `:stale`; se mantienen `:edited`/`:reformatted`/
  `:missing`/`:contract-moved` (más ricos y con paridad v1). Ver §5.

### Paso 5 — formato 4 y upgrade (`053defbb`)

- `store/format-version` = 4; `empty-store` admite 2/3/4. `stores upgrade` 2→3 sigue en el
  sitio; **3→4 reporta `:needs-rebuild true :action "kimun include build --rebuild"`** (el
  registro como caché exige los archivos; no es reescritura en el sitio). `include build
  --rebuild` produce el store formato 4. `include gitignore` añade siempre `<store>/cache/`.

### Paso 6 — medida y batería (`4471ed4e`)

- `bb store-size [DIR]` (clasifica el descriptor — que ahora lleva `:class`/`:kind` — antes que
  los nodos, y mide el caché aparte). Tabla en §2; evidencia en
  `runs/subagents/S1c-2026-10-09/`.

## 2. Tamaño antes/después (este repo, 539 documentos, 17 modelos)

Antes = formato 3 de S1b (store apartado por `--rebuild`, `.kimun.bak-20261009-024834`;
medido con `bb store-size`); después = formato 4. `include build --rebuild` en ambos casos.

| clase | antes (formato 3) | después (formato 4) |
|---|---:|---:|
| plan (1 objeto) | 4.031.322 B | 907.465 B |
| tree-object | 1.650.467 B (5.478) | 3.877 B (18) |
| sign/sign/block | 80.062 B (691) | 62.067 B (540) |
| sign/sign/text | 168.272 B (1.278) | — (0) |
| sign/sign/opaque | 303.151 B (865) | 39.952 B (17) |
| descriptor | 94.859 B (540) | 185.950 B (540) |
| edge/reference | 701.496 B (1.078) | 331.485 B (1.078) |
| edge/derived | 157.927 B (539) | 157.927 B (539) |
| sign/sign/external | 171.995 B (539) | 171.995 B (539) |
| mmap/leaf + branch | 708.116 B (1.486) | 708.426 B (1.508) |
| revision | 488 B (1) | 488 B (1) |
| **objetos** | **8.065.855 B (12.494)** | **2.569.632 B (4.781)** |
| log.edn | 151 B | 151 B |
| **aparente** | **8.066.006 B (7,88 MB)** | **2.569.783 B (2,51 MB)** |
| en disco | 56.520.704 B | 58.077.184 B |
| **caché (derivado)** | — (0) | **2.315.429 B (8.815 ficheros)** |

- Store aparente **−68 %** (7,88 MB → 2,51 MB); objetos 12.494 → 4.781. El árbol del documento
  ya no está en el CAS (tree-object 1,65 MB → 3,9 KB; texto 168 KB → 0).
- El caché es **derivado**, está fuera del store (gitignored) y se mide aparte: 2,26 MB.
- Lo que fija el tamaño ahora: el plan resuelto (907 KB, sigue en el CAS por la invariante 17),
  el mmap (708 KB), las aristas (489 KB) y el registro (external 172 KB + descriptor 186 KB +
  nodo raíz 62 KB). El store **no baja de los 433 KB** del desk: el resto es el plan del build
  (`12 §1`).
- `include build --rebuild`: S1b midió 13,3 s (formato 3) y el formato 4 tarda **11 s**
  (`build-time.txt`).
- En disco sube ligeramente (58 MB): un bloque de 4 KB por objeto pequeño, y ahora hay también
  ~8,3 k ficheros de caché (packfiles, pendiente de `12 §1`).

## 3. Batería (salidas en `runs/subagents/S1c-2026-10-09/`)

- `memcap 3 bb lint` (exit 0): `rings and docstrings ok: 99 files` (`lint.txt`).
- `memcap 4 bb test` (exit 0): `{:test 888, :pass 9577, :fail 0, :error 0, :namespaces 109}` (`test.txt`).
- `memcap 3 bb oracle` (exit 0): `9 rows, 0 mismatches` (`oracle.txt`); kernel tocado.
- `bin/kimun include check` (exit 0): los seis findings a 0 (`include-check.txt`).
- Marcador `memcap python3 scripts/parity/coverage.py /home/jp/proyectos/hum-ecosystem/tools/sldb . --strict --allow-open-prefix selfdoc/` (exit 0):
  `strict: ok - 0 blocking, 0 errors, 2 unclassified under allowed prefixes` (`coverage.txt`).
- `memcap 4 bb desk --rebuild` (exit 0): 17 modelos + 539 documentos, 0 fallos, 1 revisión
  `77b2f952…`, 11 s (`build.txt`, `build-time.txt`); `bb store-size` (`store-size-after.txt`),
  antes en `store-size-before.txt`.
- **Borrar `<store>/cache/` y que todo siga**: `include check` (ok), `docs list` (540),
  `docs render` (recalcula del archivo y reescribe el caché), `docs show --ast`, `stores verify`
  (`:ok true`, 4.781 objetos) (`cache-deleted.txt`).

Incidente de entorno (no memoria): una corrida de `bb test` falló con `No space left on device`
al llenarse `/` (214 G/226 G); se limpiaron 1.418 directorios `kimun-*` huérfanos de `/tmp` y
los `.kimun.bak-*`, y la corrida verde se repitió. Los `memcap` no alcanzaron el límite.

## 4. Qué ids cambiaron

- **No cambian**: ids de nodo (`bb oracle` 9/9), de tree object y de mmap; el `tx-id` de un plan
  resuelto sin `:as` (stores formato 2/3 anteriores abren y reproducen su id).
- **Cambian**: `tx-id` y `revision-id` del mismo plan de entrada (el plan resuelto lleva
  `:register-tree`, nodos raíz/anchor y aristas sin `:field-hashes`, y para los documentos con
  archivo no lleva los `:add-node`/`:ownership` del árbol). La revisión del build pasa de
  `8436ae74…` (S1b) a `77b2f952…`. Cambian los ids de **descriptor** (el descriptor ahora
  lleva `:class`/`:content-hash`/`:cached?`) y los ids de **arista** de anchor y modelo
  (evidencia distinta). Los **tree-id** son ULIDs nuevos por build (ya era así).

## 5. Desviaciones y lo que quedó fuera

1. **`:stale` con otro nombre.** El brief pide `:stale` para fingerprint ≠ registrado; se
   mantiene `:edited`/`:reformatted` (reformateo vs edición, por payload) más `:missing` y
   `:contract-moved`, que es lo que `diagnostics` ya promete en `tests/parity-v1.md`. Añadir un
   `:stale` literal duplicaría el estado y rompería la paridad v1; el `:action` (`docs update
   NAME`) sí es el que pide el brief.
2. **`:content-hash` en el registro solo para documentos con archivo.** `:field-hashes` se
   quitó de todas las aristas; `:content-hash` vive en el descriptor (`:cached? true`) para los
   documentos con archivo. Un documento `:inline` o sin archivo conserva el `:content-hash` en
   la evidencia del anchor: su descriptor lo crea `tree/create` (no es un registro) y su árbol
   ES el store. Llevarlo también ahí es trabajo mecánico pendiente.
3. **Clase genérica declarada, no usada.** `kimun.surface.docs/generic-class` existe
   (`generic/markdown`), pero el único camino sin modelo (`docs ingest`, no tipado) sigue
   persistiendo su árbol. `docs track`/`create`/`update` exigen modelo desde antes. Registrar
   el no tipado con clase genérica es un paso pequeño pero toca `docs ingest` y sus tests; no se
   hizo. El desk (539 documentos tipados) es el caso que el brief mide.
4. **El plan resuelto del build sigue en el CAS** (invariancia 17; 907 KB). No se tocó: quitarlo
   rompe `replay-open`/`verify-full`. Es el grueso restante por encima de los 433 KB.
5. **Verificación de tree objects cacheados.** `stores verify` ya no rehashea los tree objects de
   un árbol `:cached?` (no están en el CAS). Su integridad pasa al resolver (raíz registrada vs
   recomputada del archivo). Los árboles persistidos (modelos, relaciones, `:inline`) se siguen
   rehasheando. **Es la única promesa de v1 que se mueve de sitio, no se debilita**, y se
   registra en `tests/promises.md`.
6. **`journal`/`diff` con caché borrado.** `revision/diff` de un par de revisiones lee los tree
   objects (caché o CAS). Con el caché presente funciona; con el caché borrado, `docs render`
   lo repuebla antes de diffear en los caminos que pasan por el resolver, pero `journal show`
   no pasa por el resolver. No se ejercitó en la batería; anotado.
7. **Tests adaptados (justificados, no debilitados)**: `cli/diagnostics_test`
   `check-reports-each-kind-of-damaged-object` elige víctimas presentes en `objects/` (los tree
   objects/nodos de un documento con archivo viven en el caché); `parity/durability_test`
   incluye `cache` en el layout del store; `cli/upgrade_test` espera `:needs-rebuild` en el
   3→4.
8. No se tocó `kimun.graph.*` (paso 3 de otra tarea), la capa G ni `docs/v2/15`. No se movió el
   índice de nombres ni los packfiles (siguen en `12 §1`).
