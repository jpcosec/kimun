# Brief T-V1 — recorrido observable de una mutación

Para: Pi (worker). De: Claude (diseño y revisión). Un commit (lo hago yo tras revisar).

## Reglas

- Worktree: `/tmp/kimun-tv1-wt` (detached, base `e2b0eb1b`). No toques
  `/home/jp/proyectos/kimun` ni ningún store real: el recorrido crea su proyecto en un
  directorio temporal (`mktemp -d`) y lo borra al salir.
- No commitees. Todo `bb`/`bin/kimun` pesado por `~/bin/memcap`. Dirigidas sí; **no** `bb test`
  completo. Antes de cada `bb`: `pgrep -x bb` vacío.
- **Sin comando nuevo, sin cambios en `src/`.** Si el recorrido no se puede hacer con los
  comandos que existen, para y dime qué falta; no lo arregles.
- Código en inglés; documentos y la narración del transcript en español.
- **Una sola prueba** (un `deftest` con las transiciones). Sin variantes.

## Qué es

Una demostración reproducible de que kimun ve una mutación y la lleva hasta quedar
reconciliada, usando solo la CLI. Es la «vista del circuito» de `docs/v2/12 §1` (frente
«Operación y vista»). Tres entregables:

1. `scripts/tour_mutation.sh` — el recorrido, ejecutable (`bash scripts/tour_mutation.sh`).
2. `runs/subagents/2026-11-15-TV1/transcript.txt` — su salida real.
3. `test/kimun/cli/tour_test.cljc` — la prueba de las transiciones, en proceso.

## El recorrido (pasos; en cada uno imprime el comando y lo relevante de la respuesta)

0. Proyecto temporal: `stores init`, un modelo (`models define`), `docs track` de un documento
   tipado y `docs ingest` de uno genérico. Mira `test/kimun/cli/reanchor_test.cljc` (`project`)
   para un modelo mínimo que funciona.
1. **Reposo**: `stores sense` sin señales; `stores health` ok.
2. **Mutación de la fuente**: edita el archivo tipado en disco. `stores sense` ⇒ la señal de
   fuente mutada; `stores health` ⇒ degradado, con su `:next`.
3. **Reconciliar**: ejecuta **la acción que `:next` nombra** (léela de la respuesta; no la fijes
   de antemano). `stores health` ⇒ ok. `journal` muestra la revisión con los campos que cambiaron.
4. **Mutación del contrato de lectura (K6)**: lee la misma fuente con otro parser
   (`KIMUN_PARSER=treesitter`) ⇒ `contract/changed`. La reparación sin decisión es
   **`stores update`** (no `docs update`); confirma con `:next` y ejecuta esa. **Este tramo
   requiere el pod tree-sitter**: el script lo detecta; sin pod imprime «tramo K6 omitido: pod
   no disponible» y sigue. No falla por eso.
5. **Mover (K1b)**: mueve el archivo genérico en disco. `stores sense` ⇒ `docs/file-missing`
   (la ausencia no termina la unidad). `docs reanchor NAME FILE --reason "…"` ⇒ mismo `:tree`;
   `stores sense` limpio; `journal` muestra `:declared`.
6. **Latido**: `stores heartbeat --beats 2` ⇒ dos líneas NDJSON; el segundo latido sin cambio.

## Las cuatro correcciones que este brief ya incorpora (no las deshagas)

- K6 depende del pod **en el script**; **en la prueba** se cambia de parser dentro del proceso
  (`binding [kimun.surface.markdown.ast/*parser* …]`), como hace
  `test/kimun/cli/contract_test.cljc`. La prueba no depende del pod.
- La reparación de `contract/changed` es `stores update`, leída de `:next`.
- **Solo lectura, medido y acotado**: antes y después de cada `stores sense`, `stores health`
  y `stores heartbeat` el script calcula un sha256 del árbol del store (`find .kimun -type f |
  sort | xargs sha256sum | sha256sum`) y afirma que es idéntico; si difiere, el script falla
  con exit ≠ 0. **No** afirmes eso de `docs show`/`docs render` (con caché fría escriben caché
  derivada); si los usas, dilo en la narración. Si `stores health` resulta escribir algo, **no
  lo tapes**: repórtamelo con el diff de archivos.
- El transcript y la prueba **no fijan revisiones ni ids literales**: comparan transiciones
  (tipo de señal, estado de health, igualdad de `:tree` antes/después). En el transcript
  recorta los ids a 8 caracteres o imprímelos como `<rev>`.

## Lo que el transcript NO debe decir

- `changed` del latido es memoria del proceso, no un evento persistido; la hora es del
  registro, no una observación guardada. No presentes ninguna de las dos como historia.
- No digas que kimun «detecta que el archivo se movió»: informa ausencia; el movimiento se declara.
- Alcance: nivel documento Markdown. No hay unidades debajo del documento, código ni desk.

## La prueba (`tour_test.cljc`, un deftest)

Las mismas transiciones en proceso, con `main/run` (helpers como en `reanchor_test`):
reposo limpio → fuente mutada (señal + health degradado) → acción de `:next` → ok →
contrato cambiado (binding del parser) → `stores update` → ok → mover → `docs/file-missing`
→ `docs reanchor` → limpio y mismo `:tree` → dos latidos, segundo sin cambio (si el latido en
proceso es difícil de capturar, usa la función que `stores heartbeat` usa por dentro, como
hace `test/kimun/cli/heartbeat_test.cljc`; si aun así pasa de 20 líneas, omite el latido en la
prueba y dímelo). Y **el head del store no cambia** en ningún paso de observación.

## Documentos

- `docs/v2/06-superficies-y-cli.md`: una sección corta «Recorrido observable de una mutación»
  (qué muestra, cómo correrlo, qué requiere el pod, qué no promete).
- `docs/v2/12-ecosistema-y-pendientes.md §1`, frente «Operación y vista»: anota que existe el
  recorrido (script + transcript) a nivel documento; la vista del circuito más allá de eso
  sigue pendiente. No marques el frente como cerrado.

## Entrega

`lint.txt`, `directed.txt` (la prueba nueva + `sense_test`, `heartbeat_test`,
`contract_test`), `transcript.txt` y el exit del script en
`/tmp/kimun-tv1-wt/runs/subagents/2026-11-15-TV1/`. Reporta: archivos tocados, números, si el
tramo K6 corrió con pod, cualquier comando de observación que haya escrito en el store, y
desvíos del brief.

## Adenda 1 (2026-11-15) — `:next` no es ejecutable tras una mutación tipada

Hallazgo de Pi: tras editar el archivo tipado, `stores health` da `:status :needs-human`,
`:next "inspect: docs/a.md"` y `:actions ["kimun docs update a"]`. `:next` nombra una
**inspección**, no un comando. Corrige el paso 3 (y el 4 si pasa lo mismo) así:

1. Muestra `:status`, `:next` y `:actions` tal como salen.
2. **Inspecciona**: muestra qué cambió (el diff del archivo contra lo registrado: `docs status`
   y/o los campos que `stores sense`/`health` informan). Eso es lo que `:next` pide.
3. **Declara** la decisión en la narración: «un actor revisó el cambio y elige aplicar
   `<acción>`», y ejecuta una de las `:actions` **leída de la respuesta** (no fijada de
   antemano), con `--actor tour` si el comando lo acepta. No la presentes como automática:
   kimun **propone**, el actor **decide**. Si `:status` es `:needs-human`, el transcript debe
   decir que el circuito se detiene ahí hasta que alguien decide.
4. En el tramo K6, si `stores health` distingue una reparación sin decisión (`stores update`)
   de una que la necesita, muéstralo: es justo la diferencia que el recorrido debe hacer visible.
5. La prueba afirma: `:status :needs-human` tras la mutación tipada, que `:next` **no** es un
   comando de `:actions`, y que ejecutar la acción de `:actions` deja health ok.
6. Anota el hallazgo en `runs/subagents/2026-11-15-TV1/findings.md`: qué dice `:next`, qué
   dice `:actions`, para cada estado que el recorrido atraviesa (tabla corta), sin cambiar `src/`.
