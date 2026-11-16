<!-- Informe de un agente de exploración de solo lectura (2026-11-16). Es salida de un modelo: verifica antes de apoyarte en una cifra. -->

# Inventario: el código de kimun que hoy hace el trabajo de git a nivel de archivo

Rama `main` en `/home/jp/proyectos/kimun`. No ejecuté `bb` ni los tests y no modifiqué nada.

**Respuesta corta:**
- **Partes enteras que sobrarían:** el mapa de rutas (`route`, `route_tree`), los packs de `fs_store`, el inventario de objetos inalcanzables, el journal y `docs status`. Suman unas 1.600 líneas de 21.223.
- **El núcleo transaccional no se puede borrar.** `kernel/revision` y `kernel/store` los usan 12 namespaces cada uno, porque guardan el grafo de nodos y anclas, no solo la historia de archivos.
- **`surface/docs` mezcla los dos niveles en las mismas funciones.** Separarlo es el trabajo más grande.

Leyenda: **[V]** lo verifiqué leyendo código o con grep; **[I]** es inferencia mía.

---

## 1. Namespaces de `src/kimun/` y sus líneas [V]

Totales por grupo, con `wc -l` por directorio sin subdirectorios. La suma da 21.223, el total del repositorio.

| grupo | archivos | líneas |
|---|---|---|
| `kernel` | 14 | 3.470 |
| `host` | 8 | 1.373 |
| `graph` | 8 | 1.576 |
| `cli` (raíz) | 7 | 934 |
| `cli/commands` | 17 | 4.507 |
| `surface` (raíz) | 12 | 3.449 |
| `surface/markdown` | 9 | 1.479 |
| `surface/models` | 16 | 2.422 |
| `surface/index` | 6 | 1.275 |
| `surface/http` (incluye `http.cljc` de la raíz de surface: no, solo `routes` y `views`) | 2 | 525 |
| `surface/links` | 4 | 213 |
| **Total** | 103 | **21.223** |

No hay namespaces fuera de estos directorios.

**kernel:**

| archivo | líneas |
|---|---|
| `store` | 848 |
| `revision` | 822 |
| `mmap` | 345 |
| `route_tree` | 262 |
| `tree` | 247 |
| `anchor` | 186 |
| `plan` | 169 |
| `reconcile` | 167 |
| `canon` | 86 |
| `edge` | 83 |
| `node` | 81 |
| `route` | 81 |
| `ports` | 65 |
| `err` | 28 |

**host:**

| archivo | líneas |
|---|---|
| `fs_store` | 929 |
| `treesitter` | 111 |
| `store_cache` | 102 |
| `text` | 91 |
| `lazy` | 47 |
| `hash` | 38 |
| `ulid` | 37 |
| `default` | 18 |

**graph:**

| archivo | líneas |
|---|---|
| `edges` | 589 |
| `analysis` | 260 |
| `io` | 176 |
| `query` | 155 |
| `model` | 150 |
| `traversal` | 109 |
| `store` | 88 |
| `export` | 49 |

**cli (raíz):**

| archivo | líneas |
|---|---|
| `store` | 293 |
| `main` | 200 |
| `linked` | 135 |
| `out` | 90 |
| `errors` | 79 |
| `compose` | 75 |
| `args` | 62 |

**cli/commands:**

| archivo | líneas |
|---|---|
| `docs` | 939 |
| `stores` | 652 |
| `include` | 614 |
| `models` | 342 |
| `edges` | 314 |
| `graph` | 297 |
| `fields` | 229 |
| `links` | 172 |
| `sections` | 144 |
| `corpus` | 136 |
| `predicates` | 127 |
| `semantic` | 123 |
| `direct` | 118 |
| `find` | 104 |
| `journal` | 97 |
| `serve` | 52 |
| `index` | 47 |

**surface (raíz):**

| archivo | líneas |
|---|---|
| `docs` | 1.317 |
| `diagnostics` | 347 |
| `fields` | 299 |
| `semantic` | 264 |
| `http` | 227 |
| `parse_contract` | 211 |
| `include` | 197 |
| `heartbeat` | 171 |
| `sections` | 149 |
| `journal` | 131 |
| `signals` | 105 |
| `links` | 31 |

**surface/markdown:**

| archivo | líneas |
|---|---|
| `anchors` | 370 |
| `cst_ast` | 240 |
| `plan` | 175 |
| `cst` | 164 |
| `inline` | 156 |
| `ast` | 142 |
| `render` | 111 |
| `offsets` | 95 |
| `report` | 26 |

**surface/models:**

| archivo | líneas |
|---|---|
| `segments` | 438 |
| `selector` | 276 |
| `blocks` | 265 |
| `render` | 242 |
| `subgraph` | 205 |
| `store` | 188 |
| `selector_cst` | 158 |
| `recipe` | 116 |
| `descriptor` | 114 |
| `edit` | 99 |
| `capture` | 84 |
| `noise` | 84 |
| `marker` | 72 |
| `compile` | 30 |
| `validate` | 28 |
| `extract` | 23 |

**surface/index:**

| archivo | líneas |
|---|---|
| `address` | 318 |
| `where` | 301 |
| `corpus` | 200 |
| `search` | 180 |
| `build` | 148 |
| `cache` | 128 |

**surface/http:**

| archivo | líneas |
|---|---|
| `views` | 265 |
| `routes` | 260 |

**surface/links:**

| archivo | líneas |
|---|---|
| `parser` | 63 |
| `composer` | 59 |
| `recovery` | 56 |
| `resolver` | 35 |

---

## 2. Las partes que hacen seguimiento a nivel de archivo o historia propia

La columna "quién lo requiere" sale de un grep de `[kimun.X` sobre los `:require` en `src/`; los requerimientos desde tests van en la §5. **[V]** salvo donde diga otra cosa.

### Huella por archivo y ancla externa (dentro de `surface/docs`)

- **Qué hace:** `fingerprint` (`surface/docs.cljc:97`) es el hash `<alg>:<hex>` del texto completo del archivo. `external-node` (102) crea el signo `:external {:locator {:kind :file :path} :fingerprint}`, unido a la raíz por un borde `:reference`. Este es el "¿cambió el archivo?" que git ya responde.
- **Quién la consume:**
  - `sync-report` (1175–1185), detrás de `docs status`.
  - `diagnostics/classify` (`surface/diagnostics.cljc:128`, `content-ok`).
  - `track-plan`, `update-plan`, `generic-plan` y `generic-update-plan`.
- **Caché derivada:** `host/store_cache.cljc` (102 líneas) es la caché en `<store>/cache/`, con clave `(fingerprint, clase, parser)`. La requieren `host/fs_store`, `surface/parse_contract` y `surface/docs`.

### Mapa de rutas (árbol Merkle por ruta)

**`kernel/route.cljc` (81 líneas).**
- **Qué hace:** entradas puras `{:path :fingerprint :class :projection-hash :content-hash :tree-id}` y normalización de rutas.
- **Quién lo requiere:** `kernel/route_tree`, `kernel/revision`, `surface/docs` y `cli/commands/docs`. Los dos últimos solo usan `normalize-path` y `path-key`.

**`kernel/route_tree.cljc` (262 líneas).**
- **Qué hace:** el mapa de rutas como árbol Merkle con un nodo por directorio, con `update`, `lookup` y `diff`. Es esencialmente el modelo de árbol de git.
- **Quién lo requiere:** solo `kernel/revision` y `kernel/store`.

**Lo que tocan de rutas `revision` y `store`:**
- `revision.cljc`:
  - estado de rutas en `empty-store` (52) y `ws-init` (293–295);
  - `register-tree-op` (413–424);
  - `remove-route-op` (512–529);
  - reescritura del árbol de rutas en `apply-plan` (795–817).
  - En total, unas 60 líneas.
- `store.cljc`:
  - `route-schema` (253–266);
  - `:routes-root` y `:routes-object` en `heads.edn` (184–191 y 331–341);
  - la carga del árbol en `open-from-heads` (373–413);
  - `route-coverage` (489–524).
  - En total, unas 90 líneas.

**Lo que usa de rutas `surface/docs`:**
- `projection-hash`, `route-entry`, `route-owner`, `route-state` y `routed?` (634–694);
- `leave-route-ops` y `refuse-routed!` (695–717);
- `refuse-anchored!` (1057–1071);
- `route-coverage` (1132–1153).

**Lo que usa de rutas `cli/commands/stores`:** `check` muestra `:routes-root` y la cobertura (`stores.cljc:98`), y `verify` también (400).

### Revisiones, log, heads, planes y replay

**`kernel/plan.cljc` (169 líneas).**
- **Qué hace:** el esquema de `TransactionPlan`, los alias y las siete validaciones.
- **Quién lo requiere:** `kernel/revision`, `surface/docs`, `surface/journal`, `cli/commands/include` y `cli/commands/stores`.

**`kernel/revision.cljc` (822 líneas).**
- **Qué hace:** aplica un plan y produce una revisión inmutable con padres (un DAG tipo commit). Contiene:
  - los heads por árbol;
  - `diff` entre revisiones (543–603);
  - `check-base` y conflictos o rebase (604–683);
  - la incidencia (689–725).
- **Quién lo requiere (12 namespaces de src):** `kernel/anchor`, `kernel/reconcile`, `kernel/store`, `graph/edges`, `surface/docs`, `surface/journal`, `surface/markdown/plan`, `surface/models/store`, y en `cli/commands`: `docs`, `edges`, `include` y `stores`.
- **Ojo:** también es el núcleo de nodos, bordes y anclas. No es solo historia.

**`kernel/store.cljc` (848 líneas).**

| bloque | líneas | qué hace |
|---|---|---|
| puertos | 58–112 | `Backend`, `PlanCache`, `StoredObjects`, `ObjectCache` |
| log | 174–244 | `log.edn` append-only, `log-tail`, `committed-log` |
| `heads.edn` | — | registro de heads |
| `init!`, `replay`, `replay-open`, `open` | 245–449 | apertura y reconstrucción desde el log |
| `commit!` | 450–488 | escritura de una transacción |
| `reachable`, `references` | 525–613 | alcanzabilidad de objetos |
| `unreachable` | 614–699 | inventario de objetos sueltos |
| `verify`, `verify-full` | 700–848 | replay completo más rehash de objetos (equivale a `git fsck`) |

- **Quién lo requiere (12 namespaces):** `host/fs_store`, `cli/store`, `surface/docs`, y en `cli/commands`: `docs`, `edges`, `fields`, `include`, `journal`, `models`, `predicates`, `semantic` y `stores`.

### Índices mmap

**`kernel/mmap.cljc` (345 líneas).**
- **Qué hace:** un trie Merkle persistente (HAMT direccionado por contenido) con el que cada revisión guarda `:trees`, `:edges`, `:incidence` y `:heads-root`.
- **Quién lo requiere:** solo `kernel/revision` y `kernel/store`.
- **Ojo:** no indexa por ruta. Indexa el grafo por revisión.

### Packs e inventario de inalcanzables

**`host/fs_store.cljc` (929 líneas).**

| bloque | líneas | qué hace |
|---|---|---|
| backend básico | aprox. 61–160 y 828–929 | objetos sueltos, log, `heads.edn` atómico, lock de commit |
| packs | aprox. 163–827 (unas 660 líneas) | formato `.pack` / `.idx`, `pack!` (659), `pack-cache!` (697), `unpack!` (754), `pack-stats` y `stored-size` (797–827). Es el equivalente de los packfiles de git |

- **Quién lo requiere:** `cli/store` y `cli/commands/stores`.

**Los comandos que lo exponen, en `cli/commands/stores.cljc`:**
- `stores unreachable` (423–476), que usa `store/unreachable`;
- `stores pack` (477–542);
- `stores unpack` (543–563).

### Journal

**`surface/journal.cljc` (131 líneas).**
- **Qué hace:** una vista de solo lectura sobre el log y las revisiones. Su docstring dice "like `git log` / `git diff`". Tiene `entries` (81) y `verify` del DAG (105).
- **Quién lo requiere:** solo `cli/commands/journal`.

**`cli/commands/journal.cljc` (97 líneas).**
- **Qué hace:** `journal show` (28) y `journal verify` (44, que hace replay más `store/verify`).
- **Quién lo requiere:** `cli/main`. También lo expone HTTP: `/journal` y `/journal/verify` en `surface/http/routes.cljc:259–260`.

**Relacionado:**
- `surface/docs/meta-key` (75) escribe metadatos para el journal en `:engines` del plan.
- `models history` (`surface/models/store.cljc:173`) también recorre revisiones.

### `stores sense|health|heartbeat|check|verify|update`, diagnostics, signals y heartbeat

**Los comandos, en `cli/commands/stores.cljc` (652 líneas):**

| comando | líneas | qué hace |
|---|---|---|
| `check` | 73–162 | replay, conteos y cobertura de rutas |
| `sense` | 163–193 | diagnóstico más señales |
| `heartbeat` | 194–262 | bucle de `sense` |
| `health` | 263–275 | veredicto del diagnóstico |
| `update-store` | 276–355 | repara lo reformateado o con contrato movido |
| `verify` | 384–409 | `verify-full` |

**`surface/diagnostics.cljc` (347 líneas).**
- **Qué hace:** una categoría por documento. Mezcla tres ejes:
  - **nivel archivo:** `content-ok`, que compara la huella (130); `:missing`;
  - **nivel campo:** `:reformatted` frente a `:edited` por hashes de campo; `:rebuild-mismatch` por raíz Merkle;
  - **nivel modelo:** `:contract-moved`, `:orphan`, `:noise`.
- **Quién lo requiere:** solo `cli/commands/stores`.

**`surface/signals.cljc` (105 líneas).**
- **Qué hace:** traduce los hallazgos del diagnóstico a señales.
- **Quién lo requiere:** solo `cli/commands/stores`.

**`surface/heartbeat.cljc` (171 líneas).**
- **Qué hace:** un bucle puro con efectos inyectados.
- **Quién lo requiere:** solo `cli/commands/stores`.

**HTTP:** `/stores/check`, `/stores/sense` y `/stores/health` (`routes.cljc:187–190`).

### `include build --rebuild` y el traspaso de identidades

**Dónde está:** `cli/commands/include.cljc`.
- `identity-registry` (306–339) lee el store viejo: `{qualified-path → {:doc :tree :units :marks}}` más las versiones de los modelos.
- `carried-ids` (340–345).
- `build-plan!` (346–490) traspasa los modelos (363–379).
- `build` (497–) mueve el store a `.bak-<ts>-<ulid>`.

**Qué hace en una frase:** reconstruye el store desde la declaración conservando las identidades por ruta. Es un "rename tracking" casero: la identidad viaja por `:qualified` (la ruta).

**Quién lo requiere:** `cli/main`.

**`surface/include.cljc` (197 líneas):** es la parte pura (validación, globs y la comparación de la declaración con git y el store). La requiere solo `cli/commands/include`. **[I]** No rehace git; ya convive con él.

### Otros namespaces de nivel archivo que no estaban en tu lista

**`kernel/tree.cljc` (247 líneas).**
- **Qué hace:** el árbol Merkle de **posiciones dentro** de un documento.
- **[I]** Esto es nivel 2, no hace lo de git.
- **Quién lo requiere:** `kernel/anchor`, `kernel/edge`, `kernel/reconcile`, `kernel/revision`, `surface/docs`, `surface/markdown/plan` y `cli/commands/docs`.

**`kernel/anchor.cljc` y `kernel/reconcile.cljc` (186 y 167 líneas).**
- **Qué hacen:** estados de ancla (intacta, reemplazada, huérfana) y propuestas de reemplazo.
- **[I]** Son el corazón del nivel 2.
- **Quién los requiere:** `anchor` lo requieren `graph/edges` y `kernel/reconcile`; `reconcile` lo requieren `surface/index/corpus` y `surface/index/search`.

---

## 3. Superficie de documentos

Categorías: **(a)** registro o seguimiento de archivo y huella; **(b)** estructura, anclas o marcas dentro del archivo; **(c)** modelos y extracción; **(d)** escritura de archivos; **(e)** historia o journal. **[V]** para rangos y docstrings; **[I]** para la clasificación.

### `src/kimun/surface/docs.cljc` (1.317 líneas)

| líneas | funciones públicas | categoría |
|---|---|---|
| 75 | `meta-key` | e (metadatos para el journal) |
| 94–97 | `text-hash`, `content-hash`, `field-hashes`, `fingerprint` | `fingerprint` es **a**; los hashes de payload son c |
| 156–160 | `qualified-path` | a |
| 161–170 | `generic-class`, `generic-class?` | a/c |
| 217–230 | `extract-under` | c |
| 246–260 | `current-reading` | c |
| 304–317 | `documents` (incluye `:path :fingerprint`) | a+c |
| 318–329 | `hidden-trees` (los no seguidos que quedan en la historia) | a+e |
| 330–397 | `candidates`, `resolve-any`, `find-document` (por id, nombre o ruta) | a |
| 398–413 | `refuse-collision!` | a |
| 414–453 | `contract-descriptor`, `extract-doc`, `payload-of` | c |
| 454–466 | `unit-states` | b |
| 467–475 | `file-missing!` | a |
| 476–491 | `document`, `of-model` | c |
| 496–596 | `cached-tree?`, `rebuild-tree`, `rebuilt-root`, `doc-tree`, `document-markdown`, `document-ast` | b (estructura vía caché) |
| 599–607 | `changed-fields` | c |
| 634–717 | `projection-hash`, `route-entry`, `route-owner`, `route-state`, `routed?` y helpers de ruta | **a** (mapa de rutas) |
| 718–831 | registro de unidades y ops de binding (privadas) | b/c |
| 832–846 | `move-text` | c/d |
| 847–891 | `track-plan` | **a+b+c** (ancla externa, huella, ruta, árbol, extracción, todo junto) |
| 892–977 | `update-plan` | a+b+c |
| 980–1056 | `generic-registrable?`, `generic-documents`, `generic-document` | **a** (registrar el archivo entero sin modelo) |
| 1057–1071 | `refuse-anchored!` | a |
| 1072–1079 | `generic-root` | b (raíz Merkle del archivo) |
| 1080–1131 | `generic-plan`, `generic-update-plan` | **a** |
| 1132–1153 | `route-coverage` | **a** |
| 1154–1174 | `untrack-plan` | a |
| 1175–1185 | `sync-report` (`ok`, `modified`, `missing` por huella) | **a** (es `git status`) |
| 1188–1233 | `marks-of`, `mark-anchors`, `mark-plan` | **b** |
| 1244–1317 | `unit-field`, `close-unit-plan`, `move-unit-plan` | **b** |

### `src/kimun/cli/commands/docs.cljc` (939 líneas)

Tabla de comandos: 922–927.

| líneas | comando | categoría |
|---|---|---|
| 113–151 | `docs ingest FILE` | **a** (registra el archivo entero) |
| 152–187 | `docs update FILE --name` (sin tipo) | a |
| 188–202 | `docs render` | b/d (Markdown canónico por stdout) |
| 203–217 | `docs list` | a/c |
| 307–340 | `docs create` | **d**+a+c (escribe el archivo y lo sigue) |
| 341–372 | `docs track MODEL FILE` | a+c |
| 373–405 | `docs update NAME` (tipado; puede re-renderizar y escribir) | a+c+d |
| 414–479 | `docs reanchor NAME FILE --reason` | a (declara un movimiento de ruta, lo que git detecta como rename) |
| 480–507 | `docs untrack` / `docs delete` | a (`delete` además borra el archivo) |
| 554–589 | `docs anchors` | **b** |
| 590–795 | `docs mark` (helpers 592–777) | **b** |
| 796–832 | `docs unmark` | **b** |
| 833–852 | `docs marks` | **b** |
| 853–878 | `docs show [--ast]` | b/c |
| 879–887 | `docs status` | **a** (`git status` sobre huellas) |
| 888–902 | `update-any` (despachador) | — |

Las funciones auxiliares públicas `documents` (44–61) y `resolve-document` (62–81) son a. `docs recover` y `docs compose` viven en `cli/commands/links.cljc`.

---

## 4. Qué habla hoy con git [V]

**En `src/`, un solo namespace ejecuta git:** `cli/commands/include.cljc`.
- Requiere `babashka.process` (11).
- `git` (44–47) llama a `process/shell … "git"`.
- Las llamadas concretas:
  - `rev-parse --show-toplevel` (61);
  - `ls-files -z` (80);
  - `diff --cached --name-only --diff-filter=ACMR -z` (83);
  - `rev-parse --git-path hooks/pre-commit` (223);
  - el script del hook (228–236).
- **Ningún código lee contenido ni historia de git.** No hay `git show`, `git diff <commit>`, `git log` ni `cat-file`.

**Otras menciones en `src/` que no son llamadas:**
- `cli/main.cljc:140–146` lee `resources/git-sha`.
- `surface/journal.cljc:3` menciona git solo en el docstring.
- `host/treesitter.cljc:56` carga un pod; no es git.

**En `scripts/`:**
- `scripts/bench/k2b_bulk_store.clj:16` ejecuta git, para un benchmark.
- `scripts/release.clj` usa `shell` para `bb jar` y `tar`, no git.
- `scripts/store_size.clj` y `scripts/bench/gen_store.clj` usan `shell` para `du`.
- `scripts/parity/compare_store.clj` requiere `babashka.process`.

**Tests que ejecutan git o process:** `cli/include_test`, `c4_route_rebuild_test`, `identity_carry_test`, `model_carry_test`, `reanchor_test`, `marks_test`, `field_units_test`, `unit_close_move_test`, `spec16_readers_test`, `heartbeat_test`, `release_test`, `kernel/store_test`, `parity/closed_gaps_test` y `lint_test`.

---

## 5. Tests por área [V]

| directorio `test/kimun/` | archivos | `deftest` | líneas |
|---|---|---|---|
| raíz | 5 | 18 | 570 |
| `cli` | 51 | 363 | 9.338 |
| `kernel` | 19 | 168 | 3.740 |
| `parity` | 20 | 116 | 2.168 |
| `surface` | 12 | 123 | 1.969 |
| `surface/models` | 14 | 108 | 1.728 |
| `surface/http` | 7 | 67 | 1.339 |
| `surface/index` | 6 | 63 | 1.017 |
| `graph` | 8 | 62 | 959 |
| `surface/markdown` | 9 | 31 | 730 |
| `host` | 1 | 23 | 615 |
| **Total** | **152** | **1.142** | |

**Peso de los tests sobre lo que rehace git.** La agrupación es **[I]**; los conteos son **[V]**.

**Claramente de "rehacer git": unos 249 `deftest`.**
- kernel (92):
  - `route_test` (5);
  - `route_tree_test` (16);
  - `route_tree_store_test` (13);
  - `route_store_test` (6);
  - `mmap_test` (12);
  - `unreachable_test` (10);
  - `store_test` (25);
  - `revision_format2_test` (5).
- host (23): `pack_test`.
- cli (73):
  - `pack_test` (6);
  - `cache_pack_test` (7);
  - `unreachable_test` (5);
  - `journal_test` (12);
  - `routes_test` (11);
  - `route_schema_test` (3);
  - `c4_route_rebuild_test` (1);
  - `sense_test` (4);
  - `heartbeat_test` (7);
  - `identity_carry_test` (5);
  - `model_carry_test` (5);
  - `cold_cache_test` (4);
  - `cache_verified_test` (5).
- surface (23): `journal_test` (8), `signals_test` (5), `heartbeat_test` (10).
- parity (38):
  - `merkle_promises_test` (9);
  - `cache_promises_test` (11);
  - `shards_promises_test` (5);
  - `durability_test` (3);
  - `journal_models_test` (2);
  - `cli_store_test` (8 de 11).

**Mixtos: unos 89.**
- `cli/stores_test` (26): la mitad son enlaces o federación de stores, que se quedan.
- `cli/diagnostics_test` (13) y `surface/diagnostics_test` (11).
- `kernel/hardening_test` (21): plan, rebase y conflictos.
- `kernel/revision_test` (7) y `kernel/plan_test` (4).
- `cli/fault_injection_test` (7).

**Conclusión de los tests:** cerca del 22 % de los 1.142 `deftest` se apoya directamente en las piezas que rehacen git, y otro 8 % en piezas mixtas.

---

## Tabla final: si git llevara el nivel de archivo y kimun solo lo anclado

| destino | namespace o pieza | líneas | evidencia de quién lo requiere [V] | razonamiento [I] |
|---|---|---|---|---|
| **Sobra** | `kernel/route` + `kernel/route_tree` | 343 | solo `revision` y `store` (más `normalize-path` en docs) | es el árbol por ruta de git |
| **Sobra** | packs de `host/fs_store` (aprox. 163–827), `stores pack` y `stores unpack` | unas 700 | solo `cli/store` y `cli/commands/stores` | son los packfiles de git |
| **Sobra** | `kernel/store/unreachable` (614–699) y `stores unreachable` | unas 140 | solo `stores` | es `git gc` / `fsck --unreachable` |
| **Sobra** | `surface/journal` + `cli/commands/journal` + `meta-key` | 228 | solo `cli/main` y HTTP `/journal` | es `git log`; además el dueño ya dijo «Así es» a dejarlo (sesión §6) |
| **Sobra** | `docs status` / `sync-report` | unas 20 | `cli/commands/docs` | es `git status` |
| **Sobra o se reduce mucho** | documentos genéricos (K3): `docs ingest`, `generic-*` (docs 980–1131) | unas 150 + 40 | `cli/commands/docs` | registran archivos enteros sin nada anclado, y lo no anclado «no existe para kimun» |
| **Se reduce** | `kernel/store` (log, heads, replay, `verify-full`, `route-coverage`) | 848 → ? | 12 namespaces | el backend de objetos y el commit siguen; el log y el replay como historia propia dependen del diseño de §6, que **no está diseñado** |
| **Se reduce** | `kernel/revision` (rutas, unas 60 líneas; `diff`, `check-base`, rebase y heads por árbol) | 822 → ? | 12 namespaces | sigue siendo el que aplica nodos, bordes y anclas |
| **Se reduce o es dudoso** | `kernel/mmap` y `kernel/plan` | 345 + 169 | `mmap`: solo `revision` y `store`; `plan`: 5 namespaces | si las declaraciones pasan a ser formas en archivos versionados en git, el snapshot por revisión y los planes pierden su objeto (sesión §6); hasta entonces, el kernel los necesita |
| **Se reduce** | `surface/docs` (huella, ruta, `track-plan` y `update-plan` mezclados) | 1.317 | 17 namespaces | hay que separar a de b/c; la parte b (marcas, unidades, anclas, 1188–1317) se queda |
| **Se reduce** | `cli/commands/docs` (`ingest`, `update-doc`, `status`, `reanchor`, `untrack`) | 939 | `cli/main` y 6 comandos | `reanchor` es un rename que git ya detecta, aunque la declaración del porqué sigue valiendo |
| **Se reduce** | `surface/diagnostics` (eje `content-ok` / `:missing`), `signals`, `heartbeat` y `stores sense|health|update` | 347 + 105 + 171 | solo `stores` | el "qué archivo cambió" lo responde git; quedan los ejes de campo, contrato y ruido (es el primer corte despachado en diseño §9) |
| **Se reduce** | `host/store_cache` | 102 | `fs_store`, `parse_contract` y `docs` | encaja con la "caché comprimida" (§6), pero ya no hace falta como guardián de huella |
| **Se reduce** | `include build --rebuild` (traspaso de identidades por ruta) | unas 200 de 614 | `cli/main` | si la verdad vive en archivos y git, reconstruir es re-indexar; el traspaso por ruta pierde sentido |
| **Se queda** | `kernel/tree`, `anchor`, `reconcile`, `node`, `edge`, `canon` | unos 850 | `anchor` y `reconcile` los usan `graph/edges` y `surface/index` | es el nivel 2: Merkle dentro del archivo y estado de lo anclado |
| **Se queda** | `surface/markdown/*`, `surface/models/*`, `parse_contract`, `graph/*`, `surface/index/*`, `links`, `fields`, `sections`, `semantic` | unos 9.000 | — | estructura, extracción, modelos y grafo |
| **Se queda y crece** | `cli/commands/include` y `surface/include` (las únicas que hablan con git) | 811 | — | hoy solo consultan archivos rastreados y el área de staging; el nuevo sensor necesita `git diff <commit>` y `git show <commit>:<path>`, que **no existen en el código** |

**Advertencias:**
1. `kernel/revision` y `kernel/store` no se pueden quitar sin rediseñar el núcleo. Las dos frases "historia del grafo" e "historia de archivos" comparten las mismas estructuras: `:objects`, `:heads` y `log.edn`.
2. El propio borrador (§8) dice «se borra cuando el camino por git lo reemplace, no antes». Todo lo marcado "sobra" es candidato, no está listo para borrar.
3. Lo verifiqué todo leyendo código y con grep salvo la columna "razonamiento" y la clasificación a–e, que son inferencia mía. Los rangos de las funciones privadas en `fs_store` y `store` son aproximados: van desde la primera definición del bloque hasta la siguiente sección.
