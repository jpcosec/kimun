# MED1 — medición del árbol de rutas (K2a) y del latido

Datos crudos: `raw/` (`cost-*.txt` = una línea por repetición; `gen-*.txt` = salida del generador;
`failed/` = el intento que se desbordó; `progress.txt`). Base: worktree `/tmp/kimun-med1-wt` @ `102c157c`,
`src/` y `test/` sin tocar. Stores sintéticos en `/tmp/med1-s/`, borrados al terminar.

## Cómo se generaron

`scripts/bench/gen_store.clj <dir> <N> [layout [esquema]]`: modelo `BenchNote` (10 campos, plantilla de 5
secciones), un commit por documento, nombre `note-NNNNN`, archivo `.md` generado de la plantilla.
Distribución de rutas (sintética, **no** la del desk real):
- `flat`: todos en la raíz (`note-00007.md`).
- `dirs20`: 20 directorios `dir-00..dir-19`, reparto round-robin (N/20 por directorio: 25 a N=500, 100 a N=2000).
- `nested`: `dA/dB/dC/note-N.md`, tres niveles de 10 directorios (1000 directorios hoja; ≤1 archivo por hoja a N=500, ≈2 a N=2000).
Esquema 1 = store creado con `kimun.kernel.store/init! … {:route-schema 1}` (sin `:route-schema` en `store.edn`);
esquema 2 = el valor por omisión de hoy.

## Cómo se midió

`scripts/bench/route_cost.sh <dir> <N> 3`: 3 veces `kimun docs update note-<N/2> --set owner=benchK`
(un cambio real de contenido, sobre un documento de la mitad), comparando la lista de ficheros de
`.kimun/objects/` antes y después; un objeto es «nodo de ruta» si contiene `kimun.route/1|2`. Luego 3 veces
`stores sense --once` con `.kimun/cache/` apartado («frío») y 3 con él puesto («caliente», tras una pasada).
Los tiempos se toman **dentro** del candado (excluye la espera). Máquina: carga media ≈ 4 durante toda la
corrida (otros ejecutores/`bb test` con el mismo candado; el candado serializa `bb`, pero no el resto de la CPU).
Los tiempos de `docs update` incluyen ~0,7 s de arranque de bb+memcap (el esquema 2 anidado a N=500, con 4 nodos
de ≈1 KB, tarda 0,71 s: es el suelo observable).

Reproducir (todo con candado y memcap; ver los scripts):
```
# generar (un store a la vez; bajo flock + memcap 3)
flock /tmp/kimun-bb.lock env KIMUN_POD=/home/jp/proyectos/kimun/pods/treesitter/target/release/pod-kimun-treesitter \
  /home/jp/bin/memcap 3 bb scripts/bench/gen_store.clj /tmp/med1-s/n500-flat-s2 500 flat 2
# medir (cada llamada a kimun toma su propio flock y va bajo memcap 3)
scripts/bench/route_cost.sh /tmp/med1-s/n500-flat-s2 500 3
rm -rf /tmp/med1-s/n500-flat-s2
# la matriz entera (genera, mide, borra): scripts/bench/route_matrix.sh <out> 3 500   (ONLY=regex filtra etiquetas)
```
Los N=500 se midieron con el candado tomado una sola vez por medición completa (≈4 min); el N=2000 de `flat-s1`
también; `flat-s2` a 2000 con un candado por llamada (tras la petición del coordinador).

## Tabla 1 — `docs update` de un documento (3 repeticiones; min / mediana de segundos)

Bytes de nodos de ruta reescritos en un `docs update` (idénticos en las 3 repeticiones). «Objetos» = ficheros nuevos
en `objects/` (rango en las 3 repeticiones; el resto son objetos del documento, no de ruta). `log` crece 151 B siempre.

| N | forma | esquema | nodos de ruta reescritos | bytes raíz | bytes por nodo del camino | total bytes ruta | objetos nuevos | tiempo min / med |
|---:|---|:-:|:-:|---:|---|---:|:-:|---|
| 500 | plano | 1 | 1 (el mapa) | 202.543 | 202.543 | 202.543 | 18–22 | 1,05 / 1,08 |
| 500 | plano | 2 | 1 | 196.044 | 196.044 | 196.044 | 19–22 | 1,34 / 1,40 |
| 500 | 20 dirs | 1 | 1 | 209.543 | 209.543 | 209.543 | 19–21 | 1,14 / 1,34 |
| 500 | 20 dirs | 2 | 2 | 3.564 | 3.564 + 9.844 | 13.408 | 20–23 | 0,87 / 1,03 |
| 500 | anidado | 1 | 1 | 211.543 | 211.543 | 211.543 | 19–23 | 1,38 / 1,41 |
| 500 | anidado | 2 | 4 | 1.764 | 1.764 + 1.754 + 899 + 436 | 4.853 | 22–24 | 0,71 / 0,72 |
| 2000 | plano | 1 | 1 | 810.043 | 810.043 | 810.043 | 20–31 | 2,70 / 2,73 |
| 2000 | plano | 2 | 1 | 784.044 | 784.044 | 784.044 | 21–27 | 2,22 / 2,30 |
| 2000 | 20 dirs | 1 y 2 | — | no medido | | | | |
| 2000 | anidado | 1 y 2 | — | no medido | | | | |
| 10000 | todas | 1 y 2 | — | no medido (ver «Recortes») | | | | |

(En `2000 plano s1` y `s2` los 3 tiempos son 3,08/2,73/2,70 y 2,30/2,22/2,35.) Total de bytes nuevos por `docs update`:
500 plano s2 ≈ 213–216 KB (91 % es el nodo de ruta); 500 anidado s2 ≈ 20–22 KB (22 % ruta); 2000 plano s2 ≈ 802–806 KB.

## Tabla 2 — `stores sense --once` (un latido), segundos, min / mediana de 3

| N | forma | esquema | caché fría | caché caliente |
|---:|---|:-:|---|---|
| 500 | plano | 1 | 9,01 / 9,03 | 10,54 / 10,58 |
| 500 | plano | 2 | 10,38 / 10,83 | 11,00 / 13,17 |
| 500 | 20 dirs | 1 | 12,56 / 12,95 | 15,51 / 28,39 (ruido: 15,5 / 28,4 / 31,4) |
| 500 | 20 dirs | 2 | 12,52 / 14,85 | 12,84 / 15,37 |
| 500 | anidado | 1 | 12,79 / 13,69 | 11,86 / 12,88 |
| 500 | anidado | 2 | 9,62 / 9,66 | 9,60 / 9,62 |
| 2000 | plano | 1 | 37,74 / 37,75 | 37,80 / 38,38 |
| 2000 | plano | 2 | 38,54 / 38,65 | 38,44 / 39,95 |

«Frío» = `.kimun/cache/` apartado (la caché derivada no está); no es frío de caché de disco del SO (no se pudo vaciar sin privilegios).
Frío y caliente no se distinguen dentro del ruido (los rangos se solapan; el único caso distinto, 500 20 dirs s1, es la carga de la máquina).

## Crecimiento con N (solo dos puntos con datos completos: 500 y 2000; para tres puntos faltó 10000)

| medida (plano) | 500 | 2000 | cociente (N ×4) |
|---|---:|---:|---:|
| bytes del nodo de ruta, esquema 2 | 196.044 | 784.044 | ×4,00 (392 B por ruta) |
| bytes del mapa, esquema 1 | 202.543 | 810.043 | ×4,00 (405 B por ruta) |
| `docs update`, esquema 2 (mediana) | 1,40 s | 2,30 s | ×1,6 |
| `docs update`, esquema 1 (mediana) | 1,08 s | 2,73 s | ×2,5 |
| `stores sense` (mediana, fría) | 9,0–10,8 s | 37,7–38,7 s | ×3,6–4,2 |

Dos puntos no dan una ley. Lo que sí se sostiene: los bytes del nodo plano son exactamente proporcionales al número
de hijos (392–405 B por entrada, en ambos esquemas) y el latido crece ≈ ×4 con ×4 en N. Para `docs update` el
tiempo tiene un suelo fijo (~0,7 s) y no se separó la parte que depende de N con dos puntos.

## Otros datos

Tiempo de generar cada store (ms por commit crecen con N: ~0,4–0,55 s/commit a 1500 docs; incluye CPU compartida):
500: flat-s1 63 s, flat-s2 71 s, dirs20-s1 84 s, dirs20-s2 52 s, nested-s1 99 s, nested-s2 41 s; 2000: flat-s1 549 s, flat-s2 765 s.
Tamaño del store: 500 plano s1 120 MB; 2000 plano s1 1,72 GB, s2 0,87 GB (`du -sb`; el esquema 1 pesa el doble, causa no investigada).
Hallazgo del generador: encadenar el valor de store a lo largo de >1200 commits sin reabrir da `StackOverflowError`
en `canon/print-canon` (esquema 1, plano; `raw/failed/`). Se evitó reabriendo el store cada 400 documentos en `gen_store.clj`.
No se investigó si afecta a un uso real (la CLI reabre en cada invocación).
