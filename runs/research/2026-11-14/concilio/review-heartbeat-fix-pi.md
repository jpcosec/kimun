# Revisión de `08e2bbb4` — cierre de hallazgos medios

> Read-only. Revisé el diff, tests y evidencia de `runs/subagents/2026-11-14-heartbeat-fix/`.
> No ejecuté la suite ni checks adicionales: no encontré un riesgo concreto que los exigiera.

## Veredicto

**Sí resuelve los dos hallazgos medios** de `review-heartbeat-pi.md`.

| Hallazgo medio | Estado | Evidencia |
|---|---|---|
| `stopped` fatal sin `:beats`/`:errors` y con `"fatal"` fuera de `stop-reasons` | **Resuelto** | `kimun.surface.heartbeat/stop-reasons` incluye `:fatal`; `stopped` emite siempre `:kind :at :reason :beats :errors` y añade `:error` en fatal; `run` captura fallos del bucle y emite el registro; CLI mapea a fallo `:heartbeat/fatal`, exit 70, `:streamed` |
| SIGINT sin test automatizado | **Resuelto** | `test/kimun/cli/heartbeat_test.cljc`: `signalled` prueba `TERM` (143) e `INT` (130), y verifica `stopped/cancelled` y el conjunto de campos |

También quedaron cerrados los hallazgos bajos relacionados:

- `stdout-closed` ya no es un `reason` de `stopped`: está en `outcomes`, no en `stop-reasons`; `docs/v2/06` lo explica.
- El límite de `System/out` frente a `*out*` está documentado en `emit-line!` y en `06`; no se cambió el writer.
- Hay tests de fatal en el bucle (fallo de `wait` y de `emit`) y test de comando con envelope `:heartbeat/fatal`, exit 70 y ausencia de salida extra.

## Residuales

- **Bajo:** el test de comando del fatal sustituye `heartbeat/run` por un stub; no fuerza un fallo fatal real en un proceso hijo. La evidencia lo declara en “Not covered”. El contrato y el camino de error sí están cubiertos a nivel de bucle.
- **Bajo:** stdout realmente cerrado sigue verificado solo manualmente (`07-*` de la corrida anterior); no hay test automatizado de pipe cerrado.
- **Nit:** el docstring de `run` dice `returns {:reason :beats :errors}` y luego devuelve también `:beats`; conviene corregir la redacción, no el comportamiento.

## Evidencia revisada

- `runs/subagents/2026-11-14-heartbeat-fix/lint.txt`: 101 ficheros.
- `runs/subagents/2026-11-14-heartbeat-fix/targeted-tests.txt`: 58 tests, 381 aserciones, 0 fallos.
- `runs/subagents/2026-11-14-heartbeat-fix/full-test.txt`: 915 tests, 9.726 aserciones, 0 fallos, exit 0.
- `01-beats.ndjson` / `01-exit.txt`: CLI sobre store temporal, NDJSON y exit 0.

No hace falta otra corrección para los medios. Los residuales son de cobertura/redacción, no del contrato del heartbeat.
