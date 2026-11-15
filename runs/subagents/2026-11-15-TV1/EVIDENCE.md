# T-V1 — recorrido observable de una mutación

Base `e2b0eb1b`. Diseño y revisión: Claude. Implementación: Pi (brief y adenda en `brief.md`).
Sin cambios en `src/`: un script, una prueba, documentos.

## Qué entra

- `scripts/tour_mutation.sh`: reposo → archivo editado (`source/mutated`, health
  `needs-human`) → consulta y decisión **simulada por el guion** → ok → otro parser
  (`contract/changed`, reparación sin decisión `stores update`) → archivo movido
  (`source/missing`) → `docs reanchor` → dos latidos.
- `transcript.txt`: su salida real, con el tramo K6 (pod disponible). `script-exit.txt`: 0.
- `test/kimun/cli/tour_test.cljc`: las mismas transiciones en proceso; no depende del pod.
- `findings.md`: `:next` frente a `:actions` por estado.

## Validación (árbol final, turno exclusivo, memcap, **con pod**)

| paso | archivo | resultado |
|---|---|---|
| recorrido | `transcript.txt`, `script-exit.txt` | exit 0; ninguna observación cambió el sha256 del store |
| `bb lint` | `lint.txt` | ok |
| `bb test` | `full-test.txt`, `full-exit.txt` | 1130 tests, 12520 passes, 0 fallos, 0 errores, 136 namespaces, pod disponible |
| `bb oracle` | `oracle.txt` | 9 filas, 0 diferencias |

Este full, con pod, cubre también K1b (`648adb02`), cuyo full propio corrió **sin pod** (las
pruebas de pod se saltaron) y así se reportó por error como equivalente a los anteriores.

## Lo que el recorrido no afirma

- La decisión del paso 3 es del guion: ninguna persona revisó el cambio.
- `changed` del latido es memoria del proceso; la hora es del registro.
- kimun no detecta movimientos: informa ausencia; el re-anclaje se declara.
- Solo lectura está medido para `stores sense|health|heartbeat`, no para `docs show|render`.
- Alcance: nivel documento Markdown.

## Notas

- En un worktree el pod no se encuentra solo (se busca en `pods/treesitter/target/release/`
  del propio árbol): hay que exportar `KIMUN_POD`. Sin pod el script omite el tramo K6 y lo dice.
- `--actor tour` no se usa: un store nuevo solo da capacidades a `human/$USER`.
- `docs show` sin `--ast` no resuelve un documento genérico (limitación declarada en `06`);
  el recorrido usa `docs list`.
