# K1d — un rebuild conserva el árbol de modelos y la versión de los modelos

Base `3ce5ebc5`. Diseño y revisión: Claude. Implementación: Pi (brief y adendas en `brief.md`).
Sin cambios en el kernel.

## Qué cambia

`include build --rebuild` devolvía toda versión de modelo a 1 y daba un id nuevo al árbol
`models`. Ahora, leyendo el store viejo antes de moverlo: mismo nombre y mismo contrato ⇒
misma versión (y por tanto la misma dirección del descriptor); contrato distinto ⇒ versión
vieja + 1; nombre nuevo ⇒ 1; el árbol `models` conserva su id. `:identities` gana
`:models {:tree-carried :kept :advanced :new}` y `:models-not-carried`, que entra al warning
`include/identities-not-carried`.

## Qué NO decide (corrección conceptual del coordinador)

El hash del descriptor es la **dirección de un valor** (el contrato de una versión de la
clase), no la identidad de una unidad. K1d conserva versiones y contratos; no decide si un
modelo es una unidad del grafo. Si en `M1` los modelos pasan a serlo, deberán recibir
identidad propia según D0. Queda escrito como cuestión abierta en `14 §21` y `12 §1`.

## Validación (árbol de este commit, turno exclusivo, memcap, con pod)

| paso | archivo | resultado |
|---|---|---|
| `bb lint` | `lint.txt` | ok |
| dirigidas (6 namespaces) | `directed.txt` | 40 tests, 443 aserciones, 0 fallos |
| mutación (sin el cambio de `src/`) | `mutation.txt` | las 4 pruebas fallan (11 fallos) |
| `bb test` | `full-test.txt`, `full-exit.txt` | 1141 tests, 12627 passes, 0 fallos, 0 errores, 138 namespaces, pod disponible |
| `bb oracle` | `oracle.txt` | 9 filas, 0 diferencias |
| `bin/kimun include check` | `include-check.txt` | ok |

## Test existente ajustado

`identity_carry_test`: el mapa exacto de `:identities` lleva `:models` y `:models-not-carried`.

## Límites declarados

- El historial de versiones de un modelo no se arrastra; solo la versión vigente.
- Un modelo declarado dos veces en el mismo build se cuenta como `kept`; sin prueba.
- La prueba 1 duerme 1,1 s entre rebuilds por un defecto previo de `include build` (dos
  respaldos en el mismo segundo colisionan); se corrige en el commit siguiente.
- K1 no está cerrado: faltan unidades de sección y bloque (`S2a` → `K1e` → `S2b`), campos
  anidados e ítems, cierre y linaje de unidades de campo, copiar/dividir/fusionar, y la
  identidad de modelos como unidades (decisión de `M1`).
