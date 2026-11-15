# Integración B — validación completa de tres cortes apilados

Apilados sobre `946573a9` como tres commits y validados juntos, una vez, en el árbol final:

| commit | corte | evidencia propia |
|---|---|---|
| `e38caea2` | C1 fase 1 — matriz de lectores con la caché fría o dañada (solo prueba) | `runs/subagents/2026-11-15-cold-cache/` |
| `1a9a525f` | F1 — fallos entre el commit y la escritura del archivo (solo prueba) | `runs/subagents/2026-11-15-F1-fault-injection/` |
| `966e9555` | M1b — compilar el `:template` v1 a selector, como vista | `runs/subagents/2026-11-15-M1b/` |

| paso | archivo | resultado |
|---|---|---|
| `bb lint` | `lint.txt` | ok |
| dirigidas (4 namespaces) | `directed.txt` | 0 fallos |
| `bb test` | `full-test.txt`, `full-exit.txt` | 1168 tests, 12834 passes, 0 fallos, 0 errores, 143 namespaces, pod disponible |
| `bb oracle` | `oracle.txt` | 9 filas, 0 diferencias |
| `bin/kimun include check` | `include-check.txt` | ok |

La suite completa no se corrió sobre cada commit por separado.

## Hallazgos abiertos que dejan estos cortes (no arreglados)

- `journal verify` falla con `store/corrupt` sin `cache/plans`; `journal show` pierde `:text`
  y `:ops` en silencio con la caché fría o un objeto dañado (C1, confirmado por F1). Si los
  planes resueltos son store o caché es una pregunta para el dueño.
- El hallazgo de `stores health` tras un fallo de escritura se llama `:edited` aunque el store
  va por delante del archivo: un `docs update` sin datos revertiría el cambio (F1).
- `fields set` repetido no reescribe el archivo (F1, leído en el código, no ejecutado).
- Con la caché borrada y el archivo ausente, `docs show` y `fields get` terminan en un error
  interno en vez de uno declarado (C1, medido aparte).
