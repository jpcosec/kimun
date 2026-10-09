# S1 — Formato 3 del store (etapa S de `docs/v2/14-juego-del-lenguaje.md`)

Rama `close/S1` en `/home/jp/proyectos/kimun-worktrees/S1`. Sin push.
Autoridad: `14` §2, §10, §13 (etapa S) y `05` decisiones 21/27/33/37/39. `10-escala.md` pasa a
ser dueño de los formatos 2 y 3. `14 §10` no se tocó (era el principio); lo inviable se reporta
(§4). No se tomó nada de `close/A4`.

Integrado con `main` (merge de S2, `86cb0c85`: pod tree-sitter, fases 0–2). El pod se construyó
con `memcap 4 bb pod` (`pods/treesitter/target/release/pod-kimun-treesitter`, 16 s). Toda la
batería de abajo es sobre la rama integrada.

## 1. Qué cambió, por paso

### Paso 1 — sin copia verbatim (`a484c348`)

- `binding-ops` deja de escribir el signo `markdown/source` y la arista `derived root→source`.
- El `:contract` (decisión 40/A1) pasa a la evidencia de `reference root→external` y, para
  documentos sin archivo, de `reference root→model`. Todos los lectores de `:contract`
  (`payload-of`, `document`, `fields`, `sections`, índice, `edges`, `diagnostics`) siguen igual.
- `describe` resuelve el texto rastreado: signo verbatim si lo hay (store formato 2 o `:inline`)
  → archivo si su fingerprint coincide con el registrado → render canónico del árbol. La
  evidencia del anchor registra además `:content-hash` y `:field-hashes` del payload, que es
  como `diagnostics` distingue `:reformatted` de `:edited` sin guardar el texto.
- `kimun.cli.store/open!` inyecta `:read-file` (relativo a la raíz del proyecto) en el valor del
  store; el kernel no hace I/O.

### Paso 5 — el plan no se guarda dos veces (`00769a8f`)

- Formato 3 del store. La línea del log es `{:id tx-id :revision rev-id}`; el plan resuelto sigue
  en el CAS bajo `tx-id` (invariante 17) y `replay-open`/`verify-full` lo leen de ahí. Las líneas
  de formato 2 (con `:plan`) se siguen leyendo (`transaction?` acepta ambas formas; `plan-of`
  prefiere `:plan`).
- `kimun.surface.journal/entry` lee el plan del CAS cuando la línea no lo trae.
- El store por defecto pasa a formato 3. La estructura de revisión no cambia: mismos ids de nodo,
  arista, árbol, transacción y revisión (el `tx-id` sigue siendo `H(plan resuelto)`).
- Tests ajustados (justificado): `revision_format2_test` lee la versión por defecto de
  `store/format-version`; los tests que alteraban una línea buscando `human/tester` ahora alteran
  `:id`; `stores_test` excluye el plan de la cabeza al elegir un objeto a corromper (en formato 3
  el replay lo lee del CAS y su corrupción es `:replay-failed`, cubierto aparte).

### Paso 4 — una revisión por `build` (`70603107`)

- `kimun.kernel.plan/compose-plans`: concatena los ops de varios planes renombrando aliases con
  `substitute-aliases` (nunca claves de mapa, para no romper `:root :root`), con `:base`/`:actor`/
  `:timestamp`/`:engines` del llamante.
- `include build` compone los 17 modelos + 539 documentos en UN `TransactionPlan` y hace un solo
  `commit!` (los planes de modelo se aplican en memoria para que los documentos enlacen con
  ellos). Conserva las comprobaciones de `docs track` (noise y round-trip salvo `--force`).
- `stores update` **no** se compuso: sigue una transacción por documento reparado (ver §4).

### Paso 3 — `inline` por caso (`1909cf06`)

- `store.edn :content :inline` (ausente = `:by-reference`), `stores init --content inline`,
  `:store {:content :inline}` en `kimun.include.edn`, y `docs create|track --inline` por
  documento.
- Con `:inline` se escribe el signo verbatim y su arista `derived root→source` (el documento no
  depende del archivo); por referencia no se escribe nada. `describe` expone `:inline` y
  `update-plan` conserva el modo.
- Sin el paso 2 (árbol como caché) el único efecto observable de `inline` es el texto
  autocontenido; ver §4.

### Paso 6 — `stores upgrade 2→3` (`a2e07c81`)

- Sobre un store formato 2, convierte **en el sitio**: reescribe cada línea a `{:id :revision}` y
  `store.edn` a `:format-version 3`, y verifica el store entero después (restaura si falla). El
  1→2 sigue por `kimun.kernel.upgrade/upgrade!` (reconstrucción en directorio hermano).
- El camino fuerte para `:git :ignore` sigue siendo `include build --rebuild`.

### Paso 7 — medición (`a2e07c81`)

- `scripts/store_size.clj` y `bb store-size [DIR] [--json]`: bytes por clase, log, total aparente
  y en disco. Streaming, un objeto a la vez.

### Paso 8 — docs (`8443ef9c`)

- `10-escala.md` (dueño de los formatos 2 y 3: nuevo §10 con layout, qué se persiste, upgrade y
  medidas), `04 §8.1`, `06`, `13`, `05-estado.md`, `12 §1`, `tests/promises.md`.

## 2. Las dos tablas de tamaño

Desk de este repo: 543 ficheros `.md`, **443.598 B (433 KB)**. Antes = formato 2 en `main`
(`b4e02790`), 539 documentos, 556 revisiones. Después = formato 3, 1 revisión. Tabla completa en
`runs/bench/format3-store-size.txt` y `runs/subagents/S1-format3-2026-10-08/store-size.txt`.

Antes (formato 2):

| clase | count | bytes |
|---|---:|---:|
| mmap/branch | 6832 | 7.591.985 |
| plan | 556 | 5.676.855 |
| mmap/leaf | 7122 | 4.746.678 |
| tree-object | 5494 | 1.670.339 |
| revision | 556 | 808.657 |
| sign/opaque (texto verbatim) | 1404 | 798.693 |
| edge/derived | 1078 | 360.052 |
| edge/reference | 1078 | 289.982 |
| sign/external | 539 | 171.995 |
| sign/text | 1278 | 168.272 |
| descriptor | 540 | 94.859 |
| sign/block | 691 | 80.062 |
| **objetos** | **27.168** | **22.458.429** |
| log.edn | | 5.891.029 |
| **aparente** | | **28.349.458 (27.685 KB)** |
| en disco | | 119 MB |

Después (formato 3, medido tras el merge+S2):

| clase | count | bytes |
|---|---:|---:|
| plan | 1 | 4.712.584 |
| tree-object | 5478 | 1.650.467 |
| edge/reference | 1078 | 701.496 |
| mmap/leaf | 1387 | 597.487 |
| sign/opaque | 865 | 303.151 |
| sign/external | 539 | 171.995 |
| sign/text | 1278 | 168.272 |
| edge/derived | 539 | 157.927 |
| mmap/branch | 103 | 109.052 |
| descriptor | 540 | 94.859 |
| sign/block | 691 | 80.062 |
| revision | 1 | 488 |
| **objetos** | **12.500** | **8.747.840** |
| log.edn | | 151 |
| **aparente** | | **8.747.991 (8.543 KB)** |
| en disco | | 55,9 MB |

`include build` 38 s → 13 s; revisiones 556 → 1.

**La promesa del 14 no se cumple:** 8,54 MB aparentes > 433 KB del desk. El orden de magnitud lo
fijan el plan resuelto (4,71 MB) y el árbol persistido (tree-object 1,65 MB + nodos ≤ 0,3 MB); el
detalle y el porqué, en §4.

## 3. Salidas de la verificación (rama integrada, pod construido)

En `runs/subagents/S1-format3-2026-10-08/`.

- `bb test` (exit 0): `{:test 886, :pass 9561, :fail 0, :error 0, :namespaces 109}` (109 s).
- `bb lint` (exit 0): `rings and docstrings ok: 98 files`.
- `bb oracle` (exit 0): `9 rows, 0 mismatches` (kernel tocado en 5 y 6).
- `bin/kimun include check` (exit 0): los seis `findings` a 0.
- Marcador `memcap python3 scripts/parity/coverage.py /home/jp/proyectos/hum-ecosystem/tools/sldb .
  --strict --allow-open-prefix selfdoc/ --allow-gaps` (exit 0):
  `strict: ok - 0 blocking, 0 errors, 2 unclassified under allowed prefixes`.
- `include build --rebuild` sobre este repo: 17 modelos + 539 documentos, 0 fallos, una revisión
  (`79434d5b…`).

## 4. Desviaciones del 14 y lo que quedó fuera

1. **Paso 2 (el árbol del documento es caché) NO se implementó.** Es el bloqueante de la promesa
   de tamaño. Los nodos `block`/`text`/`opaque` y los tree objects de un documento con anchor
   siguen escribiéndose al CAS, y no hay resolver que reconstruya el árbol desde el archivo
   verificando `merkle-root == root registrado`. Motivos: (a) el texto rastreado y el payload
   grabado sólo son recuperables por parse determinista del archivo si su fingerprint coincide;
   con el archivo editado o ausente el store pierde lo que respondía (se resolvió con el árbol,
   que es justo lo que el paso 2 quita) — sin capa G (`etapa A`) no hay dónde persistir los
   hechos/payload; (b) `ast->update-plan` diffea contra el árbol del CAS; (c) el plan resuelto
   del build contiene el contenido de todos los nodos (4,71 MB) y el paso 5 lo mantiene en el CAS
   a propósito, así que ni quitando el árbol se baja de 433 KB. Se dejó el principio intacto y se
   registró en `12 §1`.
2. **El plan del build sigue en el CAS** (paso 5, invariante 17). Para que el store baje de los
   433 KB haría falta no persistir el plan en un store `:git :ignore` (o que el árbol sea caché),
   lo que rompería `replay-open`/`verify-full` tal como están hoy. No se hizo.
3. **`stores update` no compone una sola revisión.** `compose-plans` existe y `include build` lo
   usa; aplicarlo a `stores update` exige componer los `update-plan` calculados en memoria sobre
   el estado que cada reparación deja, con `:base` original. Quedó fuera por tiempo.
4. **`inline` no cambia los nodos/tree objects persistidos** (el paso 2 no está), sólo el signo
   verbatim. Cuando el paso 2 exista, `:inline` debe además persistir nodos y árbol.
5. **`stores upgrade 2→3` no purga copias verbatim** de documentos con anchor: sólo reescribe el
   log y el descriptor. La purga real (quedar no alcanzables, informe sin borrado) la hace
   `include build --rebuild`. Documentado en `10 §10`.
6. **No se tocó** `kimun.kernel.mmap` ni el modelo de revisión más allá del punto 5 (decisión 39
   queda para la etapa A); `14 §10` no se editó.
7. De `close/A4` no se copió nada; su `:names`/packfiles/GC están superados. Único punto
   reutilizable observado: el empaquetado binario en streaming de objetos pequeños (packfile)
   sigue siendo la palanca obvia para el bloque de 4 KB por objeto (55,9 MB en disco para 8,7 MB
   aparentes), pero no forma parte de esta etapa.

## 5. Lo que quedó demostrado

- El formato 3 es compatible hacia atrás (formato 2 se abre y se convierte) y no cambia ningún id
  canónico (`bb oracle` 9/9, paridad v1 518/518 con 0 bloqueantes).
- `include build` reconstruye el desk en una revisión reproducible (`build-reproduces-the-store`).
- La copia verbatim del texto desapareció del camino por defecto; el texto se lee del archivo y
  `:inline` lo hace autocontenido.
- La medición es reproducible con `bb store-size`.
