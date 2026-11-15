# K4 — packs de objetos CAS pequeños: medición

Store sintético `~/.cache/kimun-bench/k4-600` (**N = 600 documentos**, 614
revisiones, generado con `scripts/bench/gen_store.clj`; la corrida a 2000 se
interrumpió a propósito en el checkpoint 600 y el directorio se renombró).
Sistema de ficheros `ext2/ext3`, bloque 4096 B. Todo con `memcap 3`.

## Estados

| medida | suelto | empaquetado | diferencia |
|---|---:|---:|---:|
| `.kimun` aparente (`du -sb`) | 94.377.050 B | 95.251.426 B | +874.376 B |
| `.kimun` en bloques (`du -s --block-size=1`) | **204.603.392 B** | **145.641.472 B** | **−58.961.920 B (−28,8 %)` |
| `objects/` aparente | 88.539.797 B | 73.775.844 B | |
| `objects/` en bloques | 151.674.880 B | 77.066.240 B | −74.608.640 B |
| `packs/` aparente | — | 15.638.329 B | pack 14.836.869 + índice 801.460 |
| `cache/` aparente | 5.744.059 B | 5.744.059 B | no se empaqueta |
| `cache/` en bloques | 52.817.920 B | 52.817.920 B | no se empaqueta |
| ficheros en `objects/` | 18.818 | 603 | |
| ficheros en `packs/` | 0 | 2 (`.pack` + `.idx`) | |
| ficheros en `cache/` | 12.344 | 12.344 | |
| ficheros en el store | 31.166 | 12.953 | |
| `log.edn` | 92.714 B | 92.714 B | intacto |

Histograma antes de empaquetar: **18.215 objetos < 4096 B**, **603 ≥ 4096 B**
(estos quedan sueltos). Empaquetados: 18.215; `:kept-loose` 603;
`:skipped-damaged` 0; `:bytes` de contenido 14.763.953.

Sobrecoste del formato: **48 B por pack** (pie) + el `.idx` (44 B/objeto,
801.460 B aquí, 5,4 % del pack). `.kimun` aparente crece 874 KB por eso; en
bloques baja 58,96 MB.

**Qué queda fuera del pack**: `cache/` (12.344 ficheros, 52,8 MB en bloques =
**36 % de los bloques del store empaquetado**), `log.edn` (92,7 KB), los 603
objetos ≥ 4096 B y `heads.edn`/`store.edn`. Un objeto empaquetado que deje de
ser alcanzable sigue en el pack: no hay GC ni reempaquetado en este corte.

## Tiempos (600 docs)

| comando | suelto | empaquetado | diferencia |
|---|---:|---:|---:|
| `stores verify` | 75,68 s / 76,07 s | 77,53 s | **+2,4 %** |
| `docs list` | 0,72 · 0,73 · 0,73 s | 0,84 · 0,85 · 0,86 s | **×1,16** |
| `stores pack` | — | 4,54 s (tras el cambio a `HexFormat`; antes 12,3 s) | |
| `stores unpack` | — | 7,53 s (×2) | |
| `stores show` (empaquetado) | — | 0,32 s | |

`docs list` empaquetado y suelto dan la **misma salida** (comparada byte a byte).
La primera medición empaquetada (2,86 s) era con el diseño que releía el
directorio `packs/` por lectura; con `lastModifiedTime` + canal abierto por pack
bajó a 1,02–1,12 s. `stores verify` no cambia de orden: rehashea todo.

Índice: 801.460 B, 18.215 entradas (3,7 ms leer + SHA-256); reconstrucción
completa con `scan-pack` (rehashear los 14,8 MB del pack): 2,66 s. Comparación
de ids: bucle sin reservas, 0,41 µs; `ByteBuffer.mismatch` 1,78 µs;
`Arrays/compareUnsigned` no es invocable desde la imagen nativa. El microbench de
1000 búsquedas por el script auxiliar no dio un número fiable (resolución de la
var privada bajo `sci`); no se usa como evidencia.

## Perfil histórico del ×1,55 de `docs list` (formato intermedio; atribución corregida)

Este perfil es **anterior** al cambio de la conversión de ids y no describe el estado final
(×1,16, tabla de arriba). `docs list` hace **4.202 lecturas de objeto**; empaquetado, **4.198
fallan en suelto** y se leen del pack:

| | suelto | empaquetado (antes del cambio) |
|---|---:|---:|
| `docs list` total, en proceso | 418 ms | 759 ms |
| lecturas de pack (4.198) | 0 | 337 ms (≈ 80 µs/lectura) |
| intentos de suelto fallidos | 0 | 4.198 |

El perfil atribuyó primero esos 337 ms a abrir, leer y cerrar el `.pack` por lectura y a la
comprobación de frescura. **Esa atribución era incorrecta.** Medido aparte:

- abrir + leer + cerrar: ≈ 4 µs por lectura (20.000 lecturas de 400 B en 84 ms, frente a 37 ms
  con un canal compartido);
- la conversión del id de hex a bytes en cada búsqueda, un bucle interpretado: 217 ms para
  4.200 ids (≈ 52 µs cada uno); con `java.util.HexFormat`, 3 ms.

Es decir, unos dos tercios de los 337 ms eran la conversión del id. Con `HexFormat` el
comando queda en 0,84–0,86 s (×1,16). De lo que resta (≈ 0,12 s, ≈ 29 µs por lectura de pack),
unos 4 µs son abrir/leer/cerrar; el resto (el `stat` de frescura del directorio y la búsqueda
binaria interpretada) **no está desglosado**. No se cachea el canal a propósito: un backend
por latido de `stores heartbeat` filtraría un descriptor por pack.

## Integridad

- `sha256sum` de los 18.818 ficheros de `objects/` antes de empaquetar y después
  de desempaquetar: **idénticos** (dos ciclos pack/unpack).
- `verify` verde en suelto y empaquetado (`checked` 16.723 objetos alcanzables).
- `objects/` tras `unpack`: mismo conjunto de nombres y mismos contenidos; `packs/`
  deja de existir.

## Reproducir

```
~/bin/memcap 3 bb -cp src:resources scripts/bench/gen_store.clj <dir> 600
~/bin/memcap 3 bb -m kimun.cli.main stores pack   --store <dir>/.kimun
~/bin/memcap 3 bb -m kimun.cli.main stores unpack --store <dir>/.kimun
du -s --block-size=1 <dir>/.kimun
```

Crudos: `state-loose.txt`, `state-packed-final.txt`, `state-loose-final.txt`,
`times.txt`, `pack.json`, `pack2.json`, `unpack.json`, `unpack2.json`,
`verify-loose.json`, `verify-packed.json`, `store-check.txt`, `lint.txt`,
`targeted-tests.txt`.

## Correcciones de la revisión (R1–R7)

- **R1** sin canales cacheados: `load-pack` y `read-packed` abren, leen y cierran;
  `close-packs!` solo olvida estado. Prueba `no-descriptor-is-leaked-per-open-and-read`:
  200 ciclos de `fs/backend` + `store/open` + lectura empaquetada no hacen crecer
  `/proc/self/fd` (`<= antes + 8`).
- **R2** el `.idx` no se lee antes de comprobar tamaño: se exige
  `0 <= claimed` y `claimed <= (tamaño_pack - 56) / 4` (un registro ocupa al menos sus
  4 bytes de longitud; la revisión final corrigió el divisor, que era 44 y hacía reconstruir
  en cada carga un pack de objetos muy pequeños) y
  `tamaño(.idx) == claimed × 44`. Prueba `a-huge-index-is-never-read`: un `.idx`
  disperso de 1 GiB no se lee, se reconstruye y todo verifica.
- **R3** `cmp-id` sin reservas: `Arrays/compareUnsigned` **no es invocable desde la
  imagen nativa** (sin registro de reflexión) y `ByteBuffer.mismatch` cuesta
  1,78 µs frente a 0,41 µs del bucle sin reservas, así que se mantiene el bucle y
  se quita el `re-matches` por lectura. La causa real del sobrecoste era la
  conversión de id (`HexFormat`, adenda 6): `docs list` empaquetado queda en
  0,84 · 0,85 · 0,86 s frente a 0,72–0,73 s suelto (**×1,16**).
- **R4** lectura suelta tolerante: `read-text` devuelve nil ante
  `NoSuchFileException` (fichero empaquetado por otro proceso entre `exists?` y la
  lectura) y el llamante cae a los packs. Sin prueba de concurrencia: simular la
  ventana no es determinista y la prueba unitaria equivaldría a comprobar que un
  fichero ausente da nil, que ya cubre el camino normal.
- **R5** tras el `ATOMIC_MOVE` del `.pack` se fuerza el directorio `packs/`
  (`FileChannel` READ + `.force true`, fallo ignorado) antes de borrar los sueltos.
  Documentado en `10 §11.4` (qué cubre y qué no).
- **R6** `pack!` barre bajo el lock los `*.pack.tmp-*`/`*.idx.tmp-*` que dejó una
  corrida interrumpida. Prueba `an-interrupted-pack-leaves-no-temporaries`.
- **R7** `10 §11.4`: `unpack` exige el armazón del pack intacto; cifra de `docs
  list` empaquetado; `cache/` = 36 % de los bloques y no se toca.

## Ajustes de la revisión final (Claude, después del reporte de Pi)

- Cota de `claimed` corregida a `/ 4` (arriba).
- `read-packed` ya no hace un `Files/size` por lectura y, si el pack desapareció entre la
  búsqueda y la lectura (otro proceso hizo `unpack`), olvida los packs y lee el suelto.
- Frescura del directorio: el `lastModifiedTime` de `packs/` no distingue dos cambios dentro
  de la misma marca (puede ser de un segundo). Un sello igual solo se cree cuando tiene más de
  2 s; antes de eso se relista. Prueba `a-second-pack-in-the-same-timestamp-tick-is-still-seen`
  (falla sin la regla).
- Docstrings de `cmp-id` y `packs-of` puestos de acuerdo con el código.
- Las cifras de `docs list` de este documento son **anteriores** a estos tres ajustes; no se
  volvieron a medir. Los dos primeros quitan trabajo por lectura; el tercero solo añade un
  listado por lectura durante los 2 s siguientes a un `pack`/`unpack`.
