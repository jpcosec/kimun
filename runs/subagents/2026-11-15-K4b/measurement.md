# K4b — pack de `cache/objects`: medición

Copia del store de banco de 600 documentos (`~/.cache/kimun-bench/k4b-copy`,
copiada de `k4-600`; el original no se toca), con `objects/` ya empaquetado por
K4. `memcap 3`; sistema de ficheros ext2/ext3, bloque 4096 B.

## Bloques y ficheros (antes / después de `stores pack --cache`)

| medida | antes | después | diferencia |
|---|---:|---:|---:|
| `.kimun` aparente (`du -sb`) | 95.251.426 B | 95.785.050 B | +533.624 |
| `.kimun` en bloques (`du -s --block-size=1`) | **143.675.392 B** | **101.765.120 B** | **−41.910.272 (−29,2 %)** |
| `cache/objects` aparente | 3.075.193 B | 0 | −3,1 MB |
| `cache/objects` en bloques | 46.686.208 B | 1.155.072 B (dir) | −45,5 MB |
| `cache/packs` aparente | — | 3.608.817 B | pack 3.119.713 + índice 489.104 |
| ficheros del store | 12.953 | **1.839** | −11.114 |
| ficheros en `cache/` | 12.344 | 1.230 | −11.114 |
| objetos empaquetados | — | 11.116 | `:kept-loose 0` |

`:dropped-damaged 0`, `:deduplicated 0`, `:dropped-packs 0`,
`:indexes-repaired 0` en esa corrida (caché sana).

## Tiempos (CLI completa, 3 corridas; `docs list` 1)

| comando | caché suelta | caché empaquetada |
|---|---:|---:|
| `docs render note-00500` | 0,54 · 0,54 · 0,60 s | 0,55 · 0,55 · 0,58 s |
| `docs show note-00500` | 0,54 · 0,55 · 0,55 s | 0,55 · 0,55 · 0,55 s |
| `docs list` | 1,01 s | 1,01 s |
| `stores pack --cache` | — | **1,09 s** (11.116 objetos) |

La diferencia está dentro del ruido: un acierto de caché empaquetado paga una
lectura de pack (abrir/leer/cerrar, ~4 µs) más el SHA-256 de verificación, y el
proceso de la CLI pesa ~0,5 s. No se atribuye nada más sin medirlo.

Crudos: `state-before.txt`, `state-after.txt`, `times.txt`,
`pack-cache.json`, `lint.txt`, `targeted-tests.txt`.

## Comprobación de la revisión (Claude): `docs list` no empeora con K4b

`docs list` con el store empaquetado dio aquí 1,01 s; la medición de K4 daba 0,84–0,86 s. Para
descartar una regresión de este corte se corrió `docs list` intercalado sobre una copia del
bench (store empaquetado) con tres árboles: el commit de K4 (`d2dae59d`), `main` (`5d69f5c2`)
y este corte.

| árbol | corridas (s) |
|---|---|
| K4 `d2dae59d` | 0,94 · 1,02 · 0,94 · 1,35 · 0,89 · 1,07 |
| `main` `5d69f5c2` | 0,91 · 1,09 · 1,31 · 1,00 · 0,97 · 0,99 |
| K4b | 1,21 · 1,05 · 1,36 · 0,95 · 0,99 · 0,94 |

Los tres se solapan (load average 1,5 durante la corrida): no hay diferencia atribuible a
K4b. La diferencia con el 0,85 de la medición de K4 es compatible con ese ruido, pero **no se
aisló**: no se afirma que sea solo ruido.
