# REPORT sin-rehacer-git-1

Worktree `/tmp/kimun-nogit1-wt` (detached sobre f6d3cd8b), sin commit. Evidencia en este directorio:
`inventory.md`, `lint.txt` (ok, 104 archivos), `oracle.txt` (9 filas, 0 desajustes), `directed.txt`
(todas las tandas, 0 fallos, 0 errores), `tour.txt` (corrida de `scripts/tour_mutation.sh`, exit 0).

## Borrado (src + test + scripts + docs): 59 archivos, +253 / -3472 líneas
Por pieza (líneas borradas): `host/fs_store.cljc` -716 (+25 que quedan); `cli/commands/stores.cljc` -196;
`kernel/store.cljc` -91 (`unreachable`, `object-class`, `mentioned-ids`); `surface/journal.cljc` -131 y
`cli/commands/journal.cljc` -96 (archivos enteros); `surface/docs.cljc` -47 (`sync-report`, meta);
`cli/commands/docs.cljc` -42 (`docs status`, `:operation`/`:declared`); resto pequeño en `main.cljc`,
`errors.cljc`, `routes.cljc`, `views.cljc`, `semantic.cljc`, `include.cljc`, `linked.cljc`, `fields.cljc`,
`models.cljc`, `graph.cljc`. Docs: `10-escala.md` -198 (§11.3, §11.4, §11.5), `06`, `12`, `05`, `14`,
`docs/v2/README.md`, `tests/promises.md`, `tests/wittgenstein-manifest.md`. `scripts/tour_mutation.sh`: sin
`journal show` ni `docs status`.

## Pruebas que se fueron
Archivos enteros: `test/kimun/host/pack_test` (615), `test/kimun/cli/pack_test` (137), `cache_pack_test` (218),
`kernel/unreachable_test` (223), `cli/unreachable_test` (96), `cli/journal_test` (243), `surface/journal_test`
(150); en `http_test`, `journal-routes`. Aserciones sueltas de journal / `docs status` / packs retiradas o
reescritas en: cold_cache (la matriz pierde 4 filas; los tres conjuntos de gaps quedan `#{}`), reanchor
(se va `the-declaration-is-in-the-journal`), marks, nested_units, range_marks, unit_close_move (ahora cuenta
revisiones y mira `:moved` de la respuesta), field_units, fields (`the-journal-names…` pasa a
`a-mutation-names-operation-address-and-field` sobre `:data`), typed_docs, diagnostics, generic_docs, index,
models, semantic, linked_models, spec16_docs, spec16_readers, stores (lista de subcomandos), docs, fault_injection.
Dos pruebas de `journal_test` que no eran de journal (`superseded route map`, `losing writer`: cobertura de
`stores verify`) se **portaron** a `stores_test` con `stores verify`. Las demás (corrupt-object, corrupt-log,
truncated-log, heads) ya existían duplicadas en `stores_test`.
Pruebas documentales: `doc14_contract`, `spec16_models_contract` sin cambios; `wittgenstein_manifest`:
`test/fixtures/wittgenstein/manifest.edn` C11 `"kimun journal"` -> `"log.edn"` (+ su fila en el `.md`).

## Comandos y rutas que desaparecen
CLI: `stores unreachable [--ids]`, `stores pack [--max-bytes N] [--cache]`, `stores unpack`, `journal show`,
`journal verify`, `docs status`; opciones `--ids --max-bytes --cache --limit(journal) --by --tree`
(`--limit` y `--tree` siguen donde otro grupo las declara). HTTP: `GET /journal`, `GET /journal/verify`
(no había ruta de `docs status`). Warning `cache/corrupt-pack` de `stores check`. Error exit-group `journal`.

## Qué NO se borró y por qué
- Árbol de rutas, revisiones, log, planes, `mmap`, huella por archivo, `include build --rebuild`,
  `stores verify|check`, `models history`: según el encargo. `reachable`/`references`/`object-ids`
  se quedan (los usan `verify` y `open`). `changed-fields` se queda (lo usa `surface/diagnostics.cljc`).
- La caché derivada sigue existiendo como objetos sueltos verificados por hash (`read-cached-verified`).
- `docs/v2/10` §11.1–11.2, `05 #49`, `drafts/**`, y el resto de referencias históricas a K4/K5 en `12` (solo
  retoqué las frases que quedaban falsas).

## Decisiones y desvíos que hay que conocer
1. **`kimun/meta` dejó de escribirse** (nadie más lo leía). Consecuencia: los planes de `docs *`, `semantic *`,
   `include build`, `models import` y `stores update` ya no llevan `:operation/:address/:fields/:declared`
   en `:engines`, así que cambian los ids de transacción/revisión que producen (sin prueba que los fije; el
   oracle y los contratos pasan). Los stores existentes siguen abriendo (el campo extra es inocuo).
   **Pérdida real:** `--reason` de `docs reanchor|untrack|delete|mark|unmark` y el `:origin` de `models import`
   ya no quedan registrados en ningún sitio (siguen en la respuesta del comando; `--reason` sigue exigido donde
   ya lo era). Si el dueño quiere esa razón persistida, hace falta un lugar nuevo: no lo decidí.
2. Se quitó de `docs.cljc` (surface) los parámetros `:operation`/`:declared` de `track-plan`, `update-plan`,
   `generic-update-plan`, `untrack-plan`, `move-unit-plan` y los callers que los pasaban.
3. Quitar la aserción de `generic_docs_test` (`:ops` del último journal: root, anclaje, registro) no tiene
   reemplazo; las de `typed_docs` (`:fields` por documento) tampoco.
4. `bb test` completo no se corrió (regla). Corrí además, por tocar su fixture/aserciones: reanchor, marks,
   nested_units, range_marks, unit_close_move, field_units, fields, semantic, linked_models, index, spec16_*,
   docs_anchors (todo en `directed.txt`).
