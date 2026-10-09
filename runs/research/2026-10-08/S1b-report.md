# S1b — Formato 3, segunda vuelta: plan resuelto por id y `stores update` en una revisión

Worktree `/home/jp/proyectos/kimun-worktrees/S1b`, rama `close/S1b` desde `main` `eea1839d`
(y ya con S1+S2). Sin push. Pod `memcap 4 bb pod`. Autoridad: `14 §10`, `10-escala.md §10`,
`05` decisiones 27/33. Todo `bb` bajo `memcap` (default 3 GB; el pod y la suite a 4 GB).

## 1. Qué cambió, por paso

### Paso 1 — el plan resuelto referencia nodos por id (`9d0c86cd`)

- `kimun.kernel.revision/resolved-op`: un `:add-node` que declara alias (`:as`) se resuelve a
  `{:op :add-node :id <node-id>}`; el contenido del nodo queda solo como objeto del CAS (ya
  iba en `:new-objects`). Un op sin alias se deja como está: es un plan ya resuelto (sin `:as`)
  y **reproduce su propio `tx-id`**.
- `kimun.kernel.revision/add-node-op`: acepta el `:id` sin `:node`; si el objeto no está en el
  store, el nodo se "declara" (no tumba el replay) y lo informa la comprobación de hashes.
- `replay-log` (`kimun.kernel.store`): antes de aplicar un plan, carga del CAS los nodos que el
  plan nombra por id (`load-plan-objects`; rescata objetos ilegibles). `replay-open` y
  `verify-full` pasan por ahí.
- Los productores que no daban alias ahora lo dan para que el plan de las superficies no lleve
  contenido de nodo: `:as` en las anclas de `kimun.surface.docs/binding-ops`, en el contexto de
  `kimun.surface.edges/relate-plan` y en los nodos de `kimun.surface.semantic/relation-ops`.
- El plan de ENTRADA no cambia (sigue con `:node` inline). Planes almacenados de formato 2 o de
  un formato 3 anterior (resueltos, sin `:as`) se leen igual y **conservan su id** (verificado).

### Paso 2 — `stores update` compone una sola revisión (`97e144ef`)

- `cli/commands/stores/update-store`: los `update-plan` de cada documento reparado se calculan en
  memoria sobre el estado que deja el anterior (`revision/apply-plan`, sin persistir) y se
  componen con `plan/compose-plans` con `:base` = cabeza original; un solo `commit!`.
- La revisión compuesta lleva `:engines` con `kimun/meta` = `{:operation "stores update"
  :address "N documents" :fields {} :documents [{:address :fields} …]}`; `journal/entry` expone
  `:documents`. El envelope (`:updated`/`:skipped`/`:status`) no cambia.

### Paso 3 — docs y promesas (`703a0a32`, `c3d09114`)

`02 §5.1` (el plan resuelto referencia nodos por id), `10 §10` (forma, tx/revision id, tabla),
`05` (cifras y suite), `06` (`:documents` en el journal; `stores update` en una revisión),
`12 §1`, `tests/promises.md` (dos promesas nuevas). Evidencia en
`runs/subagents/S1b-2026-10-08/`.

## 2. Tamaño antes/después (este repo, 539 documentos, 17 modelos)

Baseline = formato 3 de S1 en este worktree; después = tras S1b. `include build --rebuild` en
ambos casos. Medido con `bb store-size` (`runs/subagents/S1b-2026-10-08/store-size-{before,after}.txt`).

| medida | antes | después | Δ |
|---|---:|---:|---:|
| plan resuelto | 4.712.584 B (1 obj) | 4.031.322 B (1 obj) | **−681.262** |
| tree-object (5.478) | 1.650.467 | 1.650.467 | 0 |
| edge/reference (1.078) | 701.496 | 701.496 | 0 |
| mmap/leaf | 598.587 (1.439) | 597.011 (1.398) | −1.576 |
| sign/opaque (865) | 303.151 | 303.151 | 0 |
| sign/external (539) | 171.995 | 171.995 | 0 |
| sign/text (1.278) | 168.272 | 168.272 | 0 |
| edge/derived (539) | 157.927 | 157.927 | 0 |
| mmap/branch | 112.958 (105) | 110.403 (105) | −2.555 |
| descriptor (540) | 94.859 | 94.859 | 0 |
| sign/block (691) | 80.062 | 80.062 | 0 |
| revision (1) | 488 | 488 | 0 |
| **objetos** | **8.752.846 (12.554)** | **8.068.928 (12.527)** | −683.918 |
| log.edn | 151 | 151 | 0 |
| **aparente** | **8.752.997 (8.547,8 KB)** | **8.069.079 (7.880,0 KB)** | −683.918 |
| en disco | 57.475.072 (56.128 KB) | 56.672.256 (55.344 KB) | −802.816 |

`include build` 14,1 s → 13,3 s. La revisión del build pasa de `5a79af1a…` a `8436ae74…`.

Por qué la reducción del plan es de ~0,68 MB y no más: el plan ya no embebe el contenido de
nodos (nodos `sign/*` = 723 KB), pero cada `:add-node` conserva ahora su id (64 hex) y el bloque
mayor del plan son las **aristas** (≈3,1 MB), sobre todo la evidencia `:field-hashes` de las
anclas — que el brief prohíbe tocar. La promesa nueva ("el plan resuelto no contiene el contenido
de ningún nodo") **sí** se cumple: `:add-node` con `:node` = 0. El store sigue por encima de los
433 KB del desk; el resto de la promesa es el árbol como caché (S1c) y no persistir el plan.

## 3. Qué ids cambiaron

- **Cambian**: `tx-id` y `revision-id` para el mismo plan de entrada (el plan resuelto tiene otra
  forma y es lo que se hashea). El fixture congelado `test/fixtures/tx-001.edn` de formato 2 pasa
  de `8ee69393…` a `2594415e…` (`test_util/tx-001-revision-ids`); el `:tx` de formato 2 ya no
  coincide con el de formato 1 (el motor legacy conserva el contenido inline).
- **No cambian**: ids de nodo (`bb oracle` 9/9: las 9 filas son ids de nodo, no de transacción ni
  revisión, así que el oráculo no se tocó), de arista, tree object, descriptor y mmap; tampoco
  los ids de formato 1 (`93fd0bca…` sigue). Comprobado: el apply de un plan resuelto sin `:as`
  (log de formato 2/3 anterior) reproduce `H(plan)` (script de verificación durante el desarrollo).

## 4. Batería (salidas en `runs/subagents/S1b-2026-10-08/`)

- `bb lint` (exit 0): `rings and docstrings ok: 98 files`.
- `bb test` (exit 0): `{:test 888, :pass 9577, :fail 0, :error 0, :namespaces 109}` (110 s, `test.txt`).
  (Una corrida intermedia falló con `No space left on device` al llenarse `/`; se limpiaron
  temporales de test huérfanos de esa corrida y se repitió verde. No fue memoria.)
- `bb oracle` (exit 0): `9 rows, 0 mismatches`.
- `bin/kimun include check` (exit 0): los seis findings a 0 (`include-check.txt`).
- Marcador `memcap python3 scripts/parity/coverage.py /home/jp/proyectos/hum-ecosystem/tools/sldb .
  --strict --allow-open-prefix selfdoc/` (exit 0): `strict: ok - 0 blocking, 0 errors, 2
  unclassified under allowed prefixes` (`coverage.txt`).
- `include build --rebuild` (exit 0): 17 modelos + 539 documentos, 0 fallos, revisión
  `8436ae7459652e84c47170380f382aa49eaabde10b26d2e22f54bcccba0d708b` (`build.txt`); `bb
  store-size` en `store-size-after.txt`.

## 5. Tests ajustados (y por qué)

- `kernel/revision_format2_test`:
  - `format-2-and-legacy-engines-agree-on-random-histories` comparaba los ops resueltos literales;
    ahora normaliza cada `:add-node` a `{:op :add-node :id …}` (formato 2 deja el id, legacy el
    contenido). El resto de la comparación (árboles, aristas, nodos, `:aliases`, …) no cambia.
  - Nuevo `the-resolved-plan-of-a-revision-references-nodes-by-id`: el test que rompe la promesa.
- `kernel/revision_test/fixture-tx-001…`: se separa la comparación de `:tx` (formato 2 ≠ formato 1
  por el plan resuelto) y se sigue fijando el `revision-id` de formato 2.
- `kernel/test_util/tx-001-revision-ids` formato 2 recomputado.
- `cli/diagnostics_test`: nuevo `update-composes-one-revision-for-every-document-it-repairs`.
- `kernel/store_test` y `cli/diagnostics_test` (objetos dañados): sin cambios; el replay tolera un
  nodo ausente/ilegible y lo reporta `verify` con `:missing`/`:hash-mismatch` como antes.

Ningún test se debilitó: sólo cambian expectativas que el brief declara consecuencia del cambio
(forma del plan resuelto) y se añaden dos tests.

## 6. Fuera de alcance (no tocado)

El paso 2 de S1 (árbol como caché), `mmap`, y `:field-hashes`/`:content-hash` de la evidencia.
