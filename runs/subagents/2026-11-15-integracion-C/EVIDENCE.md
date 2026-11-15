# Integración C — validación completa de dos cortes apilados

Apilados sobre `876ba5a8` y validados juntos, una vez, en el árbol final:

| commit | corte | evidencia propia |
|---|---|---|
| `17c45a0f` | DOC-C2 — C2 retirado (decisión del dueño, 2026-11-15) y dos afirmaciones viejas | `runs/subagents/2026-11-15-DOC-C2/` |
| `63952267` | S2a — anclas estructurales como evidencia, sin identidad (Pi) | `runs/subagents/2026-11-15-S2a/` |

S2a se hizo sobre `3ce5ebc5` y se rebasó aquí sin conflictos; lo validado es el árbol rebasado.

| paso | archivo | resultado |
|---|---|---|
| `bb lint` | `lint.txt` | ok |
| dirigidas (6 namespaces) | `directed.txt` | 0 fallos |
| `bb test` | `full-test.txt`, `full-exit.txt` | 1174 tests, 12931 passes, 0 fallos, 0 errores, 145 namespaces, pod disponible |
| `bb oracle` | `oracle.txt` | 9 filas, 0 diferencias |
| `bin/kimun include check` | `include-check.txt` | ok |

Este commit además escapa un `|` de la fila `K3` de `12 §1` que partía la tabla (defecto
previo; cambio de un carácter hecho después de la corrida, solo documentación).

Límite de S2a a tener presente: un documento con una cita de continuación perezosa no tiene
correspondencia uno a uno entre la capa de bloques y el AST, y sale con localizadores y
huellas pero sin rangos de líneas ni bytes.
