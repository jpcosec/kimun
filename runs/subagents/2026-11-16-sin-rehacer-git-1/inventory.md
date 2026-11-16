# Inventario: piezas hoja que rehacen git (sin-rehacer-git-1)

Todo vive en el historial git (HEAD f6d3cd8b). Evidencia de que nada vivo lo usa: tras borrar,
`bb lint` pasa y las pruebas dirigidas (directed.txt) pasan; los símbolos borrados ya no aparecen en
`src/`, `scripts/` ni `bb.edn` (grep en REPORT.md).

## Código
- `src/kimun/host/fs_store.cljc` (-716/+25): formato `.pack`/`.idx`, `pack!`, `pack-cache!`, `unpack!`,
  `pack-stats`, `stored-size`, `cache-bad-packs`, lectura desde packs (store y caché), campos
  `packs/packs-stamp/cache-packs/cache-stamp/cache-bad` del record. `pack-cache!` solo lo llamaba
  `stores pack --cache`; `cache-bad-packs` solo `stores check`. Queda: objetos sueltos, lock, log, heads,
  caché derivada suelta verificada por hash.
- `src/kimun/kernel/store.cljc` (-91): `unreachable`, `object-class`, `mentioned-ids` (solo los usaba
  `unreachable`). Se quedan `reachable`/`references` (los usa `verify`) y `object-ids` (lo usa `open`).
- `src/kimun/cli/commands/stores.cljc`: comandos `stores pack`, `stores unpack`, `stores unreachable`,
  opciones `--ids`, `--max-bytes`, `--cache`; el warning `cache/corrupt-pack` de `stores check`;
  `update-meta` y la lectura de `kimun/meta` de `stores update`.
- `src/kimun/surface/journal.cljc`, `src/kimun/cli/commands/journal.cljc` (archivos enteros); grupo
  `journal` y opciones `--limit/--by/--tree` de `main.cljc`; código de salida `journal` de `errors.cljc`;
  rutas HTTP `/journal` y `/journal/verify` de `routes.cljc` (no hay nada de journal en `views.cljc`
  salvo un ejemplo de docstring, ajustado).
- `docs status` (`cli/commands/docs.cljc`) y `sync-report` (`surface/docs.cljc`). No existe ruta HTTP de status.
- Metadatos del journal: `meta-key` ("kimun/meta"), `meta-engines` y los campos `:operation`/`:declared`
  que los planes llevaban. Nadie más los lee (solo journal y 2 pruebas); se dejó de escribirlos en
  `surface/docs.cljc`, `surface/semantic.cljc`, `cli/commands/include.cljc`, `cli/linked.cljc`,
  `cli/commands/stores.cljc`, `cli/commands/docs.cljc`, `cli/commands/fields.cljc`, `cli/commands/models.cljc`.
  `changed-fields` se QUEDA (lo usa `surface/diagnostics.cljc`).

## Pruebas borradas (archivos)
`test/kimun/host/pack_test.cljc`, `test/kimun/cli/pack_test.cljc`, `test/kimun/cli/cache_pack_test.cljc`,
`test/kimun/kernel/unreachable_test.cljc`, `test/kimun/cli/unreachable_test.cljc`,
`test/kimun/cli/journal_test.cljc`, `test/kimun/surface/journal_test.cljc`, y en `http_test`
`journal-routes`. Portadas a `stores_test` (con `stores verify`, no eran de journal):
`verify-does-not-answer-for-a-superseded-route-map`, `a-losing-writer-is-stale-and-leaves-the-store-readable`.
Aserciones de journal/`docs status` retiradas de: cold_cache, reanchor, marks, nested_units, range_marks,
unit_close_move, field_units, fields, typed_docs, diagnostics, generic_docs, index, models, semantic,
linked_models, spec16_docs, spec16_readers, stores, docs, fault_injection, http.

## Fixtures
No había envelopes dorados de `test/fixtures/cli/` de comandos borrados. `test/fixtures/wittgenstein/manifest.edn`:
`"kimun journal"` -> `"log.edn"` (C11).

## Documentos
`docs/v2/06` (filas `stores unreachable|pack|unpack`, `docs status`, sección `journal`, rutas, menciones),
`docs/v2/10` (§11.3, §11.4, §11.5 borradas; frases de journal), `12` (fila K4, K5, notas), `05`, `14` (2
frases), `docs/v2/README.md`, `docs/v2/tests/{promises,wittgenstein-manifest}.md`, `scripts/tour_mutation.sh`
(sin `journal show` ni `docs status`).
