# K1b — re-anclaje y cierre declarados a nivel documento

Base `aac04b59`. Diseño y revisión: Claude. Implementación: Pi (worker herdr), brief en `brief.md`.

## Qué cambia

- `docs reanchor NAME FILE --reason TEXT`: declara el ancla nueva de un documento tipado o
  genérico; conserva id de documento, id de árbol y nombre; una revisión. kimun no mueve ni
  borra archivos y no detecta movimientos.
- `docs untrack|delete NAME [--reason TEXT]`: cierre declarado; `:closure {:file :reason}`.
- La declaración queda en el journal (`:declared`). Sin `:declared` el meta de un plan es el
  de antes. Sin cambios en el kernel.

## Validación (árbol final, turno exclusivo, todo bajo memcap)

| paso | archivo | resultado |
|---|---|---|
| `bb lint` | `lint.txt` | ok |
| dirigidas (6 namespaces) | `directed.txt` | 51 tests, 464 aserciones, 0 fallos, 0 errores |
| `bb test` | `full-test.txt`, `full-exit.txt` | 1129 tests, 12473 passes, 0 fallos, 0 errores, 135 namespaces, exit 0 |
| `bb oracle` | `oracle.txt` | 9 filas, 0 diferencias |
| `bin/kimun include check` | `include-check.txt` | ok, sin warnings |
| mutación (`git stash push -- src/`) | `mutation.txt` | las 5 pruebas de `reanchor_test` fallan (23 fallos); corrida por Pi **antes** de los dos ajustes de revisión, que no tocan las pruebas |

## Ajustes de revisión (Claude, sobre la entrega de Pi)

- Tipado: la data es el mapa de documento de `docs update` (antes un mapa parcial con
  `:blocks` leído de un árbol que puede no estar cargado) y se aplica el mismo rechazo de
  ruido estructural que `docs update`, antes de escribir.
- Genérico: `:blocks` salía nil; ahora es el conteo que devuelve el plan.

## Tests existentes ajustados (a propósito)

- `docs_test`: `reanchor` en la lista de subcomandos conocidos.
- `generic_docs_test`: la data de `untrack` lleva `:closure`.

## Límites declarados

- `--reason` es opcional en el cierre (compatibilidad); obligatorio en el re-anclaje.
- No hay copiar/dividir/fusionar con linaje declarado.
- Un `kimun.include.edn` que declara la ruta vieja se cambia a mano.
- Re-anclar un documento `:superseded` re-extrae el texto bajo el contrato vigente, como
  `docs update NAME FILE`; no hay prueba de ese caso.
- `docs/not-anchored` (documento sin archivo) no tiene prueba.
