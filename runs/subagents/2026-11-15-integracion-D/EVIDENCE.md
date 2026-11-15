# Integración D — validación completa de dos cortes apilados

Apilados sobre `35eecca1` y validados juntos, una vez, en el árbol final:

| commit | corte | evidencia propia |
|---|---|---|
| `cb3911ac` | M1c — el intérprete de selectores en su propio namespace (sin cambio de comportamiento) | `runs/subagents/2026-11-15-M1c/` |
| `102c157c` | fix — `fields set` repetido reescribe un archivo atrasado respecto del store | `runs/subagents/2026-11-15-F1-fix/` |

| paso | archivo | resultado |
|---|---|---|
| `bb lint` | `lint.txt` | ok |
| dirigidas (4 namespaces) | `directed.txt` | 27 tests, 1346 aserciones, 0 fallos |
| `bb test` | `full-test.txt`, `full-exit.txt` | 1181 tests, 14102 passes, 0 fallos, 0 errores, 146 namespaces, pod disponible |
| `bb oracle` | `oracle.txt` | 9 filas, 0 diferencias |
| `bin/kimun include check` | `include-check.txt` | ok |

Abierto: el quirk de `TaskDoc` del spec 16 (l. 182) no tiene prueba; cuando el store va por
delante del archivo, `stores health` lo sigue llamando `:edited` (opciones en
`runs/subagents/2026-11-15-F1-fix/REPORT.md`, decisión del dueño).
