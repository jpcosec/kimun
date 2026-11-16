# K2b-0 — medición

Base `c8ca2f25`. Todo `bb` bajo `flock /tmp/kimun-bb.lock … memcap 3 bb …`. Los bytes son **aparentes**
(tamaño de archivo de los objetos sueltos), salvo donde se diga «en disco».

## 1. Qué se midió y con qué

Dos stores sintéticos de **N = 500** documentos tipados (modelo `BenchNote` de `scripts/bench/gen_store.clj`,
10 campos, un archivo `.md` por documento, ruta plana), formato 4, esquema de rutas 2. Mismos 500 archivos:

| store | cómo se construyó | tiempo | transacciones |
|---|---|---|---|
| **S-commits** (`/tmp/k2b0-s500`) | `bb scripts/bench/gen_store.clj /tmp/k2b0-s500 500` (un commit por documento: el uso de `docs track` uno a uno) | 58,5 s | 501 revisiones |
| **S-bulk** (`/tmp/k2b0-bulk500`) | `bb scripts/bench/k2b_bulk_store.clj /tmp/k2b0-bulk500 /tmp/k2b0-s500` (un solo `include build`, como se construyó el desk el 2026-10-09) | 12,0 s | 1 revisión |

**N = 2000 no se corrió.** El brief pedía hacerlo solo si cabía en 3 minutos. El propio N = 500 mostró
que el costo por commit crece: 47, 85, 122, 158 y 172 ms/commit en los tramos de 100 documentos
(`/tmp/k2b0-gen500.log`, copiado en `gen500.log`); sumado a los 9–13 minutos que otro ejecutor midió hoy
a 2000 (dato del brief, no medido por mí) quedaba fuera de presupuesto. Lo mantengo en 500.

Scripts (repetibles, en `scripts/bench/`, no tocan `src/` ni `test/`):

- `scripts/bench/k2b_breakdown.clj <.kimun>`: lee `objects/` y `heads.edn` (solo objetos sueltos),
  clasifica por forma, recorre cada mmap desde la revisión cabeza (**vivo**) y desde todas las
  revisiones (**historia**), y agrupa las aristas por (tipo, clase del destino).
- `scripts/bench/k2b_bulk_store.clj <proyecto> <dir con note-*.md> [n]`: arma el store en un solo `include build`.
- `bb store-size <.kimun>` (el script existente): clasifica `mmap/leaf`, `mmap/branch`, `edge/reference`,
  `edge/derived`, pero **no separa** cuál de los cuatro mmap ni a qué destino apunta la arista; por eso
  el desglose propio.

Salidas: `breakdown-n500.txt`, `breakdown-bulk500.txt`, `store-size-n500.txt`, `store-size-bulk500.txt`.

## 2. S-bulk (un `include build`): la comparable con `14 §20`

Objetos: 4.256, **1.999.824 B** (1,91 MiB). Cada fila de `bb store-size`:

| clase | n | bytes | % de objetos |
|---|---:|---:|---:|
| mmap/leaf + mmap/branch (**los cuatro mmap**) | 1.163 + 85 = 1.248 | 539.720 + 93.965 = **633.685** | 31,7 % |
| edge/reference (A1 + A3) | 1.000 | **471.000** | 23,6 % |
| edge/derived (A2) | 500 | **146.500** | 7,3 % |
| descriptor | 501 | 354.140 | 17,7 % |
| route/node (plano, un nodo de 98 KB por escritura; 2 objetos) | 2 | 196.263 | 9,8 % |
| sign/external | 500 | 138.500 | 6,9 % |
| sign/block (raíces) | 501 | 57.582 | 2,9 % |
| revision | 1 | 491 | — |

Desglose de las aristas (`k2b_breakdown`, §3 de su salida):

| arista (tipo → destino) | n | bytes | de ellos, evidencia | B/documento |
|---|---:|---:|---:|---:|
| A3 `reference` → archivo (`external`) | 500 | 335.000 | 253.000 | 670 |
| A1 `reference` → descriptor de clase | 500 | 136.000 | 50.000 | 272 |
| A2 `derived` → descriptor de clase | 500 | 146.500 | 62.000 | 293 |
| **las tres** | 1.500 | **617.500** | 365.000 | **1.235** |

(La columna «evidencia» es `(count (pr-str (:evidence e)))`, aproximada: no es el tamaño canónico
exacto.) Las 1.500 aristas son **todas** las activas de la revisión cabeza: en este store no hay otras
(ni `supersedes` ni semánticas), de modo que `:edges` e `:incidence` miden **solo** estas tres.

Los cuatro mmap, **vivos** en la cabeza (en S-bulk vivo = historia: una sola revisión):

| mmap | nodos | bytes | contenido |
|---|---:|---:|---|
| `:trees` | 238 | 106.428 | 501 entradas `ULID → [descriptor merkle]` |
| `:edges` | 271 | 136.900 | 1.500 ids → `true` |
| `:incidence` | 537 | 330.462 | 1.001 claves de nodo; **1 hub** (el descriptor de clase: su `{:set …}` guarda 1.000 ids, 2 por documento: A1 y A2) y 1.000 entradas del conjunto anidado |
| `:heads-root` (`heads.edn`, fuera de la Revisión) | 238 | 71.859 | 501 entradas árbol → revisión |

La suma por mapa (645.649 B) excede los 633.685 B de objetos mmap **distintos** en 11.964 B: nodos de
contenido idéntico compartidos entre mapas (mismo id). Atribuible a las tres aristas: `:edges` +
`:incidence` = **467.362 B** (≈ 934 B/documento) **más** los 617.500 B de las tres aristas
= **1.084.862 B** (54,2 % de los objetos del store; 2.170 B/documento). `:trees` + `:heads-root` =
178.287 B (8,9 %) **no** dependen de las aristas.

Nota sobre la incidencia: A1 y A2 apuntan **las dos** al descriptor de clase, por lo que ese nodo es un
hub de 2·N ids; el costo por commit de un hub es O(log N) nodos mmap por arista (`incidence-value`,
`revision.cljc` l. 730–751). En S-bulk eso se paga una sola vez.

## 3. S-commits (un commit por documento): lo que cambia la historia

Mismos 500 documentos, 501 revisiones. **Vivo** idéntico a S-bulk (106.618 / 137.092 / 330.937 B para
`:trees`/`:edges`/`:incidence`). La **historia** de los tres mmap de la Revisión (nodos distintos
alcanzables desde alguna revisión, `breakdown-n500.txt` §2):

| mmap | nodos | bytes (historia) | frente a vivo |
|---|---:|---:|---:|
| `:trees` | 1.396 | 1.331.931 | ×12,5 |
| `:edges` | 3.288 | 2.471.756 | ×18,0 |
| `:incidence` | 5.466 (492 raíces de conjunto-hub) | 4.842.472 | ×14,6 |
| **tres mmap de la Revisión** | | **8.646.159** | ×15,0 |

La clase `mmap/*` completa (con la historia de `:heads-root`, no medida aparte) es 9.679.859 B de
15.195 objetos, **16 % del total de objetos**; el total (60.751.743 B) lo domina `route/node`
(49.120.000 B, 500 versiones del único nodo de 98 KB de una ruta plana, el límite de `K2a` ya documentado
en `10 §11.2`, no de `K2b`). Las aristas son las mismas 1.500 (**614.000 B**: referencia 468.000 +
derived 146.500; ninguna arista se reescribe por commit porque son inmutables y direccionadas por
contenido; `active == stored`).

Lectura honesta: el costo de los mmap depende de **cómo se escribió el store** (cuántas revisiones
acumuló), el de las aristas **no**. En S-commits cada commit reescribe los nodos del camino de `:edges`
y de `:incidence` (y el hub). No medí un store `docs track`-ado real (el desk del repo tiene
`kimun.include.edn` vacío; `AGENTS.md`).

## 4. Disco real (K4)

`du -s` de `objects/` de S-bulk: 18.079.744 B (cada objeto suelto ocupa su bloque de 4 KiB). Tras
`stores pack` sobre una **copia** (`bb -m kimun.cli.main -- stores pack --store /tmp/k2b0-pk/.kimun`):
`packs/` 2.015.232 B, 1 objeto suelto. O sea, con packs el costo de las ~2.750 archivos de mmap y
aristas pasa de bloques sueltos a bytes aparentes. Para decidir K2b esto cuenta: los bytes aparentes de
§2 son el costo **después** de K4; antes de empaquetar el costo son archivos (2.748 de 4.256 objetos
= 65 % de los archivos son mmap o aristas por documento).

## 5. Comparación con `14 §20` / `10 §9` (formato 4, desk de 539 documentos, 2026-10-09)

Cifras citadas de `docs/v2/10-escala.md` l. 485–497 (columna «formato 4»):

| clase | desk 2026-10-09 (539 docs) | por doc | S-bulk hoy (500 docs sintéticos) | por doc |
|---|---:|---:|---:|---:|
| mmap (leaf+branch) | 708.426 B (1.508 obj) | 1.314 B, 2,80 obj | 633.685 B (1.248 obj) | 1.267 B, 2,50 obj |
| edge/reference | 331.485 B (1.078) | 615 B | 471.000 B (1.000) | 942 B |
| edge/derived | 157.927 B (539) | 293 B | 146.500 B (500) | 293 B |
| descriptor | 185.950 B (540) | 344 B | 354.140 B (501) | 707 B |
| sign/external | 171.995 B (539) | 319 B | 138.500 B (500) | 277 B |

¿Siguen valiendo los 708 KB y 489 KB de `14 §20`? Medido por documento, **sí en orden de magnitud, con
tres matices** (las bases no son iguales: el desk era 13 modelos y la medida sintética es un modelo; no
es una comparación controlada):

1. **mmap**: 1.267 B/doc hoy frente a 1.314 B/doc entonces (−3,6 %); absolutos 633 KB vs 708 KB.
2. **Aristas** (referencia + derived): 1.235 B/doc hoy frente a 908 B/doc entonces (+36 %). Lo medido:
   la evidencia de A3 pesa ~506 B por arista y lleva `:contract`, `:parse-contract` (digest + 6
   componentes) y, para clases spec 16, `:ancestors`. **[inf]** que la diferencia viene del `:parse-contract`
   de K6, posterior al 2026-10-09; no lo comprobé contra el commit.
3. **Descriptor**: se duplicó (344 → 707 B) porque lleva `:units` (10 ULIDs por documento, K1c). Es dato
   nuevo de hoy: el registro por documento creció en K1c–K1e en una clase que **no** es de K2b.

Sobre qué cortes de hoy afectaron estas clases (según lo medido):

- **K2a** (plan fuera del CAS, `f837069d`): quitó los 907 KB del plan (aquí no hay objeto `plan`). Cambió
  el total, no las clases de K2b.
- **K4** (packs): no cambia bytes aparentes; cambia el disco (§4).
- **K1c–K1e** (unidades): ensanchan el descriptor (medido), **no** tocan mmap ni aristas en el store
  sintético (todas las aristas siguen siendo las tres).
- **M1g/M1h** (clases spec 16): añaden `:ancestors` y la versión 2 del `:parse-contract` a la evidencia
  de A3. **No medido**: el modelo sintético es v1. El código lo hace (`registry` l. 750–751; `binding-ops`
  l. 807–808; `parse_contract.cljc` l. 60–62).

## 6. Lo que no medí

- N = 2000 (ver §1), ni N = 10.000.
- El desk de este repo: `kimun.include.edn` sigue vacío (M2a no está), así que no hay store real del desk.
- Un store con clases spec 16 (con `:ancestors`) ni con documentos genéricos (una sola arista).
- Un store de varios directorios (esquema de rutas con fan-out): todo es ruta plana.
- El tiempo de las operaciones (`docs show`, `describe`) con y sin mmap: no hay variante que medir sin
  cambiar `src/`.
- El costo de cargar cada mmap al abrir el store (las lecturas de `:edges` por `edges-by-from`):
  no medido; solo la lista de quién lo hace (`readers.md` §1–§2).
