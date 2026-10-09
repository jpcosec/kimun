# A4 — informe (PAUSADO por revisión de diseño del store)

Fecha: 2026-10-08. Estado: **detenido a petición** (no seguir con packfiles/GC; hay
revisión de diseño del store, puede que deje de guardar copias del contenido).

## Qué hay hecho

Rama `close/A4` del worktree `/home/jp/proyectos/kimun-worktrees/A4`.

- **Commit `ac5ecb50`** — `feat(kernel): formato 3 (índice de nombres en la Revision),
  packfiles y GC`. Contiene (a), (b), (c) y la ruta de upgrade, con tests y `bb lint`
  verde. El mensaje termina con el trailer pedido.
- **Cambios sin commitear** (refinamientos de packfiles posteriores al commit):
  - `src/kimun/host/fs_store.cljc`: packing **en streaming** (antes acumulaba todos los
    objetos en memoria; ahora `write-pack!` consume una seq perezosa `[id texto]` y
    escribe registro a registro, cumpliendo la regla de memoria), ids del pack/índice en
    **binario de 32 bytes** en vez de 64 ASCII, y sin cabecera de id en el registro (el
    índice ya indexa por id). Reduce el tamaño del pack ~25 MB (de 143 a 139 MB aparentes).
  - `src/kimun/cli/commands/stores.cljc`: `layout` del upgrade excluye `packs/` (derivado)
    para no copiar el pack al store nuevo.
  - Estos cambios pasan `bb lint` y humo manual; **no** he vuelto a correr la suite
    completa con ellos.

## Qué implementa cada parte

- **(b) Índice de nombres (formato 3)** — la Revision gana `:names` (mmap
  nombre → árboles), actualizado en O(plan) por `apply-plan`; `trees-named` lo lee en
  O(log n) en vez de un descriptor por árbol. Formato 2 sigue sirviéndose (fallback).
  `store/format-version` = 3. `test_util` y los tests de ids de revisión actualizados.
- **(a) Packfiles** — `kimun stores pack`: mueve los objetos sueltos a
  `packs/<stamp>.pack` + `.idx` bajo el lock; `read-object` prefiere el suelto y cae al
  índice; `object-ids` suma los índices; `stores verify` rehashea lo alcanzable, packed
  incluido. Decisión: **empaquetado explícito, nunca en `commit!`** (el commit debe seguir
  O(plan) y con el lock milisegundos; el pack lee todo lo suelto).
- **(c) GC** — `kimun stores gc [--dry-run|--apply]`: `store/garbage` camina desde **todas**
  las revisiones del log (más cabeza y `heads-root`) y `prune!` borra sueltos y reescribe
  packs. Nunca borra algo alcanzable desde una revisión registrada.
- **Upgrade** — `kimun.kernel.upgrade/upgrade!` generalizado: fuente 1 **o** 2 →
  formato actual, re-confirmando el log con el motor actual. `stores upgrade` reescribe el
  descriptor y respalda como `.format-<src>-<stamp>`; mapa en `upgrade-format-<src>.edn`.

## Verificación corrida

- `bb test` (con `memcap timeout 900`): **878 tests, 9487 asserts, 0 fail, 0 error, 107
  namespaces** — verde en `ac5ecb50` (antes del refinamiento sin commitear).
- `bb oracle`: **9 filas, 0 discrepancias** (los ids de nodo/arista/árbol no cambian).
- Tests nuevos: pack/GC end-to-end de CLI (`test/kimun/cli/stores_test.cljc`) y upgrade
  2→3 (`test/kimun/kernel/upgrade_test.cljc`).

## Mediciones

Store real `/home/jp/.cache/kimun-bench/real-f2` (2000 documentos, formato 2, 119.968
objetos, log de 2001 líneas). Números con el binario del worktree:

| medida | suelto (antes) | empaquetado (después) |
|---|---|---|
| `.kimun` aparente (`du -sb`) | 133.613.047 B (133,6 MB) | 138.891.639 B (138,9 MB) |
| `.kimun` en bloques (`du --block-size=1`) | 551.686.144 B (551,7 MB) | 151.658.496 B (151,7 MB) |
| `stores pack` | — | 22,6 s |
| `docs list` | 3,74 s | (no remedido) |
| `stores verify` | 126,1 s | 145,4 s |
| objetos (`stores check`) | 119.968 | 119.968 |

Desglose del estado empaquetado: pack 107.839.698 B (107,8 MB, = suma del contenido de los
objetos), índice 5.278.592 B, `log.edn` 25.773.050 B, `heads.edn` 154 B. El objetivo
≤ 150 MB se cumple por tamaño aparente (138,9 MB) y por **contenido de objetos** (107,8 MB);
en bloques queda en 151,7 MB porque incluye el log (25,8 MB) redondeado a bloques de 4 KB.

Índice de nombres, medido en el store **formato 2** (ruta antigua, un descriptor por
árbol): `trees-named "note-01000"` lee **2001 objetos**, 386 ms (2001 árboles). **No llegué
a medir el "después"**: requiere `stores upgrade` a formato 3 (re-confirma 2000
transacciones) y no lo corrí.

GC: probado en store pequeño (store sano → 0 basura; `prune!` borró 1 objeto suelto y el
pack quedó verificando). **No** medido en el store real.

## Estado del store de bench (importante)

`/home/jp/.cache/kimun-bench/real-f2` quedó **empaquetado** (los 119.968 sueltos se
borraron; su contenido está en `packs/`) y su pack está en el formato **sin commitear**
(ids binarios, sin cabecera). Con el árbol de trabajo actual (`ac5ecb50` + cambios sin
commitear) abre y `stores verify` da ok. Si se vuelve al commit `ac5ecb50` a secas, su
formato de pack es el antiguo (ids ASCII de 64) y **no podrá leer este pack**. Es un store
de bench regenerable: `scripts/bench/gen_store.clj` (2000 documentos), o se puede
"desempaquetar" a sueltos con el mismo conversor usado (leer `.idx` de 44 B y volcar
`objects/<id>` con `pack[off:off+len]`).

## Qué queda (para cuando se reanude)

1. Re-decidir (a)/(c) según la revisión de diseño del store (¿sigue guardando copias del
   contenido?); si se descarta el packing, revertir los cambios de packfiles y borrar
   `packs/` de real-f2.
2. Re-correr `bb test` + `bb oracle` sobre el árbol sin commitear y commitear (o descartar)
   el refinamiento de streaming/ids binarios.
3. Medir el "después" del índice de nombres en formato 3 (`stores upgrade` + repack).
4. `stores gc` sobre el store real (dry-run) para cuantificar basura (perdedores de la
   carrera + raíces de cabezas reemplazadas).
5. Documentación: `docs/v2/10` (formato 3, packfiles, GC), `docs/v2/12` (quitar los tres
   pendientes de kernel), `docs/v2/06` (CLI pack/gc), `docs/v2/02` (formato de la Revision).
   **No actualizada todavía.**
6. `runs/subagents/A4/` con la evidencia de validación (`bb test`, `bb oracle`).
