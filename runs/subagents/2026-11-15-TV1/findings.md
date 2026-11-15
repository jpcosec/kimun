# T-V1 — hallazgos: `:next` y `:actions` por estado

Los dos campos de `stores health` no son lo mismo: `:next` dice qué hacer con la
respuesta (a veces una inspección), `:actions` enumera comandos propuestos. El
recorrido los muestra tal cual y **no** ejecuta `:next`.

| estado | `:status` | `:next` | `:actions` | qué hace el recorrido |
|---|---|---|---|---|
| reposo | `ok` | `null` | — | nada |
| fuente editada (tipado) | `needs-human` | `"inspect: docs/a.md"` | `["kimun docs update a"]` | consulta `docs status` y los campos de la señal y **simula** la decisión explícita de un actor (nadie revisó el cambio): ejecuta la primera acción de `:actions` |
| contrato cambiado (K6, otro parser) | `degraded` | `"run: kimun stores update"` | `["kimun stores update"]` | aquí `:next` **sí** es una reparación sin decisión: se ejecuta `stores update` |
| archivo ausente (tras mover) | — | — | — | `stores sense` informa `source/missing`; la continuidad la declara `docs reanchor` |
| tras re-anclar | `ok` | `null` | — | nada |

Consecuencias:

- `:next` **no es un comando** en el caso `needs-human`; ejecutarlo como comando
  fallaría. El circuito se detiene hasta que alguien decide.
- Tras la mutación tipada el status es `needs-human` porque la acción cambia
  campos del payload; con `contract/changed` es `degraded` porque no decide nada.
- El actor por defecto es `human/$USER`: un store nuevo solo le da capacidades,
  así que `--actor tour` sería rechazado por el plan (`capability`) en la
  reparación; el recorrido usa el actor por defecto y lo dice.
- `docs show` sin `--ast` no resuelve un documento genérico; el id de árbol se
  lee de `docs list`.

Ninguna de estas notas cambia `src/`.
