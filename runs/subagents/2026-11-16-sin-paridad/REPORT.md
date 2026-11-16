# REPORT sin-paridad (worktree /tmp/kimun-noparity-wt, sin commit; cambios en el índice)

## Números
Borrado por `git rm`: 226 archivos. Líneas: `test/kimun/parity/` 20 archivos / 2168; otros `*parity_test` 6 / 639;
`scripts/parity/` 10 / 1190; `test/fixtures/parity/` 185 / 64187; ledgers de paridad 5 / 318. Total del diff (sin la
evidencia nueva): ~68 850 líneas borradas, ~170 añadidas/reescritas en 36 archivos. Detalle y motivos: `inventory.md`.

## Pruebas que se fueron
Las de `test/kimun/parity/` y los 6 `*_parity_test` (listados en inventory.md), más los casos: `selector_test`
(4 deftest), `spec16_docs_test` (modo compat), `routes_test` (`:shared-path`), `semantic_test` (diferencial v1),
`segments_test/unknown-field-logs-warning`.
Advertencia: `test/kimun/parity/` (por orden «entero») incluía promesas propias del almacén, no solo v1: `durability`,
`merkle_promises`, `shards_promises`, `cache_promises`, `closed_gaps` (escritores concurrentes y lock entre procesos),
`optional_field_hash`. `promises.md` y `14` ya no las citan. Conviene decidir si algunas se reponen sin el marco «paridad».
También se fue `cst_ast_parity_test` (CST nativo vs pod sobre el corpus de v1): la comparación nativo/pod sobre corpus ya no se prueba.

## Comportamiento que cambió
Selector con un solo modo (el de `:strict`, `docs/v2/16 §3.5`): un campo cuya sección falta es `:missing` (error
`validate/missing` al tipar); una sección presente y vacía vale `""` (antes un opcional vacío quedaba ausente).
Ajustes de pruebas: `engine_test/section-body-keeps-line-breaks-and-blocks` (notes vacío ahora `""`),
`engine_test/extract-of-render-is-identity` (render imprime `## Notes` vacío, que se extrae como `""`; la propiedad compara contra eso),
`models_test/direct-failures-are-typed` (el error por sección ausente trae `:field`, no `:fields`; se añade el caso de sección vacía
que sí trae `:fields`; se retira `--allow-empty`).
Descriptor con `:substrate :compat`: la clave se ignora (más simple); un descriptor que decía `:legacy-v1-template`
ahora extrae con el comportamiento único. Contratos v2 ya registrados cambian de digest (derivado, se recomputa).
Fixture `spec16_examples.edn`: se quitan `:R3-quirks` (decisión abierta, ya resuelta), `:legacy-v1-compat`, `:compat` y `:legacy-v1-quirk`;
`spec16_models_contract_test` ajustado (`[:A1.1 :F10]`).
`:shared`: ya no existe en `route-state`/`route-coverage`; `--allow-empty` de `extract` borrado.

## Documentos tocados
03, 04, 06, 07, 10, 14, 16 y `tests/promises.md`: solo frases que quedaron falsas (referencias a paridad/ledgers, `:compat`,
`:shared-path`, tests borrados). `test/fixtures/wittgenstein/manifest.edn` ya no cita `scripts/parity/coverage.py`.
`promises.md` y `wittgenstein-manifest.md` se conservan (mezclan otras promesas). No toqué los archivos vedados.

## Pendiente para otros ejecutores (archivos vedados)
- `src/kimun/cli/main.cljc:101`: el help de `extract` aún dice `[--allow-empty]`.
- `src/kimun/cli/commands/stores.cljc`: `:routes-shared`/`routes/shared-path` y `(:shared coverage)` quedan como código muerto (siempre vacío); `docs/v2/06` líneas 82 y 267 aún los mencionan.
- `docs/v2/05-estado.md`, `README.md`, `docs/faq.md`, `docs/v2/README.md`, `12` aún hablan de `parity-v1.md`/`coverage.py`.
- `docs/v2/16 §3.5` conserva el relato histórico de las 5 divergencias de R3.

## Qué NO borré
`scripts/canon_oracle.py` (es `bb oracle`); `golden_test`, `corpus_test` x2, `fixtures/models/{deskops,hum-scrapper}`; el
corpus de `segments_test` (movido a `test/fixtures/models/segments.cases.jsonl.gz`); `runs/` y `docs/archive/` (historial).

## Verificación (evidencia en esta carpeta)
- `lint.txt`: `rings and docstrings ok: 103 files`.
- `oracle.txt`: 9 rows, 0 mismatches.
- `directed.txt` (concatena directed-1..9): surface/models, surface/*, surface/http, y cli: typed_docs, docs, generic_docs,
  spec16_docs/contract/readers, models, fields, sections, find, include, field_units, marks, nested_units, range_marks,
  unit_close_move, tour, cold_cache, fault_injection, routes, golden, corpus, graph, stores, y el resto de cli; graph/*,
  doc14_contract, spec16_models_contract, wittgenstein_manifest, lint: todas 0 failures, 0 errors (directed.txt conserva la primera corrida de surface/models con 2 fallas de engine_test y, al final, su rerun verde tras el ajuste; models_test también falló antes de su ajuste, pero solo se conserva su corrida verde, que reemplazó a la primera).
- No corrí `bb test` completo ni `mutation`. Sin procesos en segundo plano.
