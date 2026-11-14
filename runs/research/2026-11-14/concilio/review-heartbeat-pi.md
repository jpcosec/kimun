# Revisión de `86a0ba42` — heartbeat de Claude

> Read-only. Revisé el diff del commit, sus tests y la evidencia de
> `runs/subagents/2026-11-14-heartbeat/`. No edité código ni ejecuté la suite. No ejecuté checks
> adicionales: los riesgos encontrados son de contrato y cobertura de tests, no reproducibles sin
> forzar un fallo fatal.

## Veredicto

Cumple el mandato principal: **solo recalcula señales**, reutiliza la observación de `stores sense`,
emite NDJSON, trata errores como transitorios y no escribe en el store. SIGTERM y SIGINT están
probados manualmente con `stopped/cancelled` y exit 143/130; stdout cerrado termina sin más salida.
La evidencia de hashes antes/después no muestra escrituras.

## Hallazgos

### Media — el `stopped` fatal no respeta el contrato documentado

- Ruta: `src/kimun/cli/commands/stores.cljc`, `heartbeat` (catch `Throwable`), y
  `src/kimun/surface/heartbeat.cljc` (`stop-reasons`, `stopped`).
- El catch emite `{:kind "stopped" :at … :reason "fatal" :error …}`. El contrato de
  `docs/v2/06-superficies-y-cli.md` §3 dice que `stopped` lleva `at`, `reason`, `beats` y `errors`;
  aquí faltan `beats` y `errors`, y `"fatal"` no pertenece a `stop-reasons`
  (`#{:beats :cancelled :stdout-closed}`).
- Efecto: un consumidor que parsea todos los `stopped` con el mismo esquema puede romper justo en el
  camino de error. También queda una contradicción entre el docstring de `stopped` y el flujo real.
- Corrección sugerida a Claude: añadir `:fatal` a `stop-reasons`, emitir el `stopped` fatal con
  `:beats`/`:errors` (o cambiar el contrato y documentar el registro de error fatal como excepción),
  y cubrirlo con test.

### Media — SIGINT no tiene test automatizado

- Ruta: `test/kimun/cli/heartbeat_test.cljc`; evidencia `runs/subagents/2026-11-14-heartbeat/06-*`.
- Solo hay test de SIGTERM. El SIGINT aparece en evidencia manual (`exit=130`, `stopped/cancelled`),
  pero no en la suite. El mandato pide ambas señales; una regresión futura en la ruta SIGINT no la
  detectaría CI.
- Corrección: test de subproceso equivalente al de SIGTERM, con `kill -INT` y `stopped/cancelled`.

### Baja — `stop-reasons` declara un motivo que nunca se emite

- Ruta: `src/kimun/surface/heartbeat.cljc`, `stop-reasons` y `finish`.
- `:stdout-closed` está en el conjunto, pero `finish` suprime el `stopped` cuando el consumidor se
  fue; el docstring de `stopped` dice que el `reason` es uno de `stop-reasons`. El contrato de
  `06` tampoco lista `stdout-closed` en la tabla de `stopped`.
- Efecto: vocabulario ambiguo; no hay registro `stopped` con ese motivo. Alinear el conjunto con los
  motivos realmente emitidos (`beats`, `cancelled`, `fatal`) o documentar que `stdout-closed` es un
  resultado del bucle, no un `reason` de `stopped`.

### Baja — detección de stdout cerrado depende de `System/out`, no del `*out*` usado al escribir

- Ruta: `src/kimun/cli/commands/stores.cljc`, `emit-line!`.
- Escribe con `println`/`flush` (que usan `*out*`) y luego comprueba `(.checkError System/out)`. En el
  CLI normal coinciden y la evidencia con `head -2` funciona. Si `*out*` se rebindea (tests
  in-process, embedding, otro host), la comprobación no garantiza detectar el cierre del flujo que
  realmente se usó.
- No es un fallo del camino CLI actual; es un límite de contrato si el bucle se reutiliza como
  librería. Alternativa: escribir sobre un `PrintStream`/writer explícito inyectado y comprobar ese
  mismo objeto, o documentar que el bucle solo soporta el stdout del proceso.

### Baja — el camino fatal no tiene test

- Ruta: `src/kimun/cli/commands/stores.cljc`, `heartbeat` catch; tests en
  `test/kimun/cli/heartbeat_test.cljc`.
- No hay test que provoque una excepción después de `started` y verifique: último registro, ausencia
  de envelope extra, exit 70 y metadatos `:streamed`. El camino está escrito, pero no cubierto.

## Qué sí cumple

- **Solo sensar:** `observe` es la misma función que `stores sense`; no hay acciones, LLM, embeddings
  ni mutación del grafo. `src/kimun/cli/commands/stores.cljc` (`observe`, `heartbeat`).
- **CLI/NDJSON:** `started`/`beat`/`error`/`stopped`, JSON compacto, flush por línea; `-main`
  suprime el envelope cuando el meta `:streamed` está presente (`src/kimun/cli/main.cljc`).
- **Error y continuación:** un fallo de observación produce `error`, incrementa
  `consecutive-errors` y el siguiente latido reintenta; evidencia `05-stream-sigterm.ndjson`.
- **Cancelación:** SIGTERM en `05`, SIGINT en `06`; `stopped/cancelled`, exits 143/130; el hook espera
  hasta 2 s y el límite está documentado.
- **Sin escrituras:** `03-store-files-before.sha256` y `09-store-files-after.sha256` son idénticos;
  el test `the-heartbeat-writes-nothing-in-the-store` hashea todos los archivos del store.
- **Fallos previos al flujo:** opciones inválidas y store ausente devuelven el envelope habitual,
  exit 6/5, sin NDJSON (`08-bad-interval.json`).

## Prioridad para Claude

1. Corregir el `stopped` fatal y su esquema (`beats`/`errors`, `fatal` en el vocabulario).
2. Añadir test automatizado de SIGINT.
3. Alinear `stop-reasons`/docs sobre `stdout-closed`.
4. Opcional: hacer explícito el writer de `emit-line!` y añadir test de stdout cerrado real en la suite.
