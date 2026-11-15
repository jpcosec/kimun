# Brief K1b — re-anclaje y cierre declarados a nivel documento (segundo corte de K1)

Para: Pi (worker). De: Claude (diseño y revisión). Un commit (lo hago yo tras revisar).

## Reglas

- Worktree: `/tmp/kimun-k1b-wt` (detached, base `aac04b59`). No toques
  `/home/jp/proyectos/kimun` ni ningún store real.
- No commitees. Todo `bb` por `~/bin/memcap`. Dirigidas sí; **no** `bb test` completo.
  Antes de cada `bb`: `pgrep -x bb` debe estar vacío (otra sesión puede tener el slot).
- Código y docstrings en inglés; documentos en español.
- **Pocas pruebas**: las cinco de abajo, que son las que discriminan. Sin variantes.

## Contexto (corto)

Contrato D0 S–M, `docs/v2/14-juego-del-lenguaje.md §21` reglas 3 y 6:

- El anclaje (la ruta del archivo) es la referencia vigente de la unidad; **no** es su identidad.
- **La ausencia no termina una unidad.** Si el archivo ya no está, el sensor dice `missing` y
  nada más. Cerrar la unidad o re-anclarla en otro lugar son **declaraciones**, con actor, que
  **nombran lo que pasó**.
- El sensor propone; el cierre y la continuidad se declaran.

**El defecto.** Hoy mover el archivo de un documento rastreado no tiene declaración: la única
salida es `docs untrack` + `docs track`/`ingest`, que acuña ids nuevos (pierde la unidad). Y
`docs untrack|delete` cierra sin registrar qué pasó. La superficie ya sabe cambiar de ruta
conservando ids (`tdocs/update-plan` y `tdocs/generic-update-plan` aceptan `:path` y
`:operation`, y retiran la ruta vieja con `leave-route-ops`); falta el comando y el registro
de la declaración.

## Qué NO es este corte

- Nada de G ni D-G; nada debajo del documento; ningún formato de id nuevo.
- **Sin cambios en el kernel** (`src/kimun/kernel/*`). Si crees que hace falta, para y dime.
- kimun **no mueve ni borra archivos** en el re-anclaje: solo registra la declaración.
- No detectes movimientos automáticamente (nada de «parece que se movió a X»): el sensor no
  decide continuidad.
- No toques `include.cljc` (K1a) ni los docs que digan «D0 abierta» (saneamiento aparte).

## Qué cambia

### 1. `docs reanchor NAME FILE --reason TEXT` (comando nuevo, `src/kimun/cli/commands/docs.cljc`)

Declara que el documento NAME (tipado o genérico; resuélvelo como hace `untrack-plan`, con
`tdocs/resolve-any`) ahora está anclado en FILE.

- `--reason` es **obligatorio** y no vacío: sin él `cli/usage` (nada se toca).
- FILE debe existir y estar dentro del proyecto del store (usa los mismos helpers que
  `track`/`update-doc`: `read-file!`, `relative-path`/`inside-path`); fuera ⇒
  `docs/outside-project`.
- FILE igual a la ruta vigente ⇒ `cli/usage` («already anchored there»), sin revisión.
- FILE ya rastreado por otro documento ⇒ `docs/exists` (ya lo hacen `refuse-anchored!` /
  `refuse-routed!`; verifica que el camino tipado también lo rechaza y, si no, llama a
  `tdocs/refuse-anchored!` antes).
- Un documento sin archivo (`:path` nil, p. ej. inline sin ruta) ⇒ `docs/not-anchored`
  (exit 3 como los demás `docs/*` de rechazo; registra el código donde corresponda en
  `kimun.cli.errors` si hace falta).
- El plan sale de `tdocs/update-plan` (tipado) o `tdocs/generic-update-plan` (genérico) con
  `:path <nueva>` y el texto leído de FILE, **una sola revisión**. Se conservan id de
  documento, id de árbol y nombre; la ruta vieja se libera y la nueva queda tomada. Si el
  contenido además cambió, es la misma revisión (mutación de la misma unidad).
- Data: lo que devuelve `docs update` para ese tipo de documento, más
  `:reanchored {:from "<ruta vieja>" :to "<ruta nueva>" :previous-file "missing"|"present"
  :reason "…"}`.
- Si el archivo viejo **sigue existiendo** (`"present"`): warning
  `docs/previous-file-present` (el archivo viejo queda sin rastrear; si era una copia, lo
  que corresponde es otra unidad). No es error.

### 2. La declaración queda en el journal

- `meta-engines` recibe una clave más, `:declared`, con
  `{:kind "reanchor" :from … :to … :previous-file … :reason …}`. Para eso `update-plan` y
  `generic-update-plan` aceptan en `opts` un `:declared` opcional que copian tal cual al meta
  (sin `:declared` el meta queda byte a byte como hoy: **no cambies ids de planes existentes**).
- `src/kimun/surface/journal.cljc`, `entry`: añade `:declared` al `select-keys`; actualiza el
  docstring de `entries`.

### 3. Cierre declarado: `docs untrack|delete NAME [--reason TEXT]`

- `--reason` opcional (compatibilidad). `untrack-plan` acepta `:declared` y el comando pasa
  siempre `{:kind "closure" :file "missing"|"present"|"none" :reason <texto o nil>
  :file-deleted? bool}`; `:file` es el estado del archivo **antes** de actuar (`"none"` si el
  documento no tiene ruta).
- Ojo: esto añade `:declared` al meta de todo untrack/delete; es intencional. Si algún test
  existente compara el meta entero de un untrack, ajústalo y dímelo.
- Data gana `:closure {:file … :reason …}`.

### 4. Spec de opciones

`:reason {:ref "TEXT" :desc "What happened (docs reanchor, docs untrack, docs delete)"}` en
`spec`; `"reanchor"` en `commands`. Revisa si algún test lista los subcomandos conocidos de
`docs` (como pasó con `stores`) y ajústalo.

## Pruebas (cinco, en `test/kimun/cli/reanchor_test.cljc`; mira `identity_carry_test.cljc` y
`generic_docs_test.cljc` para los helpers de store temporal)

1. **Tipado**: track `a.md`, mover el archivo a `b/c.md` en disco, `docs reanchor` ⇒ mismo
   `:tree` y mismo id de documento que antes; `docs list`/`show` da la ruta nueva; `docs
   status` `ok`; la ruta vieja queda libre (se puede hacer `docs track` de otro archivo puesto
   en `a.md` y recibe ids **distintos**); `:previous-file "missing"`.
2. **Genérico**: lo mismo con `docs ingest` (mismo `:tree`, ancla nueva, `stores sense` sin
   `docs/file-missing`).
3. **Rechazos sin revisión** (el head no cambia): sin `--reason`; FILE inexistente; FILE ya
   rastreado por otro documento; FILE igual al vigente.
4. **Journal**: tras el re-anclaje, la entrada más nueva de `journal` trae `:operation "docs
   reanchor"` y `:declared` con `:from`, `:to`, `:reason`; tras `docs untrack NAME --reason
   "…"`, `:declared {:kind "closure" … :reason "…"}`.
5. **Compone con K1a**: store hecho con `include build` que declara un documento en `a.md`;
   mover a `b.md`, `docs reanchor`, cambiar el include a `b.md`, `include build --rebuild` ⇒
   el documento conserva `:tree` y `:identities :carried 1`, `:not-carried []`. (Si el
   fixture de include de `identity_carry_test` no se deja reutilizar en ≤30 líneas, omite
   esta prueba y dímelo; no inventes infraestructura.)

Comprueba que muerden: con `git stash push -- src/` las cinco fallan; `git stash pop`.

## Documentos (español, mínimos)

- `docs/v2/06-superficies-y-cli.md`: fila `docs reanchor NAME FILE` en la tabla de `docs`
  (junto a `docs untrack`), `--reason` en `untrack`/`delete`, y dos o tres líneas donde hoy
  se explica `docs untrack|delete` y la ruta (≈ línea 224).
- `docs/v2/12-ecosistema-y-pendientes.md §1`, fila `K1`: añade «K1b (2026-11-15): re-anclaje
  y cierre declarados a nivel documento (`docs reanchor`, `--reason` en `untrack|delete`; la
  declaración queda en el journal)»; quita «re-anclaje y cierre declarados» de pendientes;
  añade evidencia `runs/subagents/2026-11-15-K1b/`.
- `docs/v2/14-juego-del-lenguaje.md §21`, donde lista lo pendiente de K1 (≈ líneas 89–90 de
  la sección): mismo ajuste, una frase.
- Límites que deben quedar escritos (en 06 o 14): kimun no mueve el archivo; no detecta
  movimientos; `--reason` es opcional en el cierre; dividir/fusionar/copiar con linaje
  declarado no existen todavía; un `include` que declara la ruta vieja hay que cambiarlo a mano.

## Entrega

- Dirigidas en verde: `reanchor_test`, `docs_test`, `typed_docs_test`, `generic_docs_test`,
  `journal_test`, `identity_carry_test`, `routes_test`, `doc14_contract_test`, y `bb lint`.
  Guarda las salidas en `/tmp/kimun-k1b-wt/runs/subagents/2026-11-15-K1b/`
  (`lint.txt`, `directed.txt`, `mutation.txt`).
- Reporta: archivos tocados, salida de las dirigidas (números), resultado de la mutación,
  cualquier test existente que hayas tenido que ajustar y por qué, y cualquier punto donde te
  apartaste del brief.
