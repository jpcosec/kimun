# Inventario sin-paridad (2026-11-16)

## Borrado (git rm)
- `test/kimun/parity/` (20 archivos, 2168 líneas). Incluye pruebas que no son v1-vs-kimun sino promesas propias del
  almacén (durability, merkle_promises, shards_promises, cache_promises, closed_gaps: carrera de escritores, lock
  entre procesos, optional_field_hash, hashing): se fueron por la orden «entero».
- `*_parity_test` fuera de la carpeta (6 archivos, 639 líneas): `cli/docs_parity_test`, `graph/edges_parity_test`,
  `surface/models/{blocks,segments,}_parity_test` (el último es `parity_test`), `surface/markdown/cst_ast_parity_test`
  (comparaba CST vs AST de kimun sobre el corpus grabado de v1; el motivo de borrarlo es que su insumo es el corpus de v1).
- `scripts/parity/` (10 archivos, 1190 líneas; incluye los 6 `.py`/`.sh`). `scripts/canon_oracle.py` intacto.
- `test/fixtures/parity/` (185 archivos, 64187 líneas): docs/, edges/, engine/, engine.cases.jsonl.gz, segments/, semantic/.
  Cada subdirectorio solo lo usaban pruebas que se fueron, salvo `segments.cases.jsonl.gz` (ver «Se queda»).
- Ledgers: `docs/v2/tests/parity-{gaps,map,na,oracle}.edn` y `parity-v1.md` (5 archivos, 318 líneas).

## Pruebas y casos borrados dentro de archivos que se quedan
- `selector_test`: `extract-and-selector-agree` (corpus v1 + doble modo), `r3-quirk-of-v1-is-kept`,
  `an-unknown-mode-fails-naming-it`, `strict-equals-legacy-on-a-document-without-quirks`; el round trip EDN ya no usa el corpus v1.
- `spec16_docs_test/the-compat-mode-of-the-class-decides-how-it-extracts` (comparaba dos modos) -> `a-missing-required-section-is-reported-missing`.
- `routes_test/a-legacy-second-document-on-one-file-is-kept-without-a-route-and-declared` (`:shared-path :allow`).
- `semantic_test/tags-of-reproduces-the-tags-v1-recorded` (diferencial contra tags grabados de v1).
- `segments_test/unknown-field-logs-warning` (solo fijaba el parámetro `known-fields`, aceptado «por paridad con v1»).

## Revisadas una por una y se quedan
- `cli/golden_test` (+ `fixtures/cli/s0`): sobres JSON de kimun congelados; no es v1.
- `cli/corpus_test`, `surface/index/corpus_test`: prueban el comando/motor `corpus` de kimun (el «port» es de tests, no de datos grabados).
- `fixtures/models/{deskops,hum-scrapper}`: descriptores usados como entrada por ~40 pruebas propias.
- `segments_test/instrumented-render-matches-canonical-render-for-every-document`: compara dos renders de kimun entre sí con
  descriptores/payloads que casualmente vienen de v1; el output grabado de v1 nunca se lee. Su corpus se movió a
  `test/fixtures/models/segments.cases.jsonl.gz` (único sobreviviente de `fixtures/parity`).
- Pruebas «port de test de v1» (`# v1: test_…`) sobre datos escritos a mano: son comportamiento propio.

## Modo `:legacy-v1-template`
- `selector.cljc`: sin parámetro de modo, sin `modes`/`check-mode!`; comportamiento = el de `:strict`.
- `extract.cljc`, `capture.cljc`, `docs.cljc/extract-under`: sin aridad de modo.
- `subgraph.cljc`: ya no valida `:substrate :compat` (la clave se ignora). `compile.cljc` ya no emite `:compat`.
- `parse_contract.cljc`: `:compat` sale de los componentes del contrato versión 2 (cambia el digest de contratos v2 existentes; son derivados).
- `direct.cljc`: se borra `--allow-empty` («as v1 did»), que solo funcionaba por el quirk legado.

## `:shared-path :allow` y `:shared`
- `docs.cljc`: sin opción `:shared-path` en `track-plan`; `route-state` ya no devuelve `:shared`; `route-coverage` ya no tiene `:shared`.

## Sin uso tras lo anterior
- `segments/field-lines` y `field-owners`: parámetro `known-fields` (solo «por paridad con v1»).
- Requires `json/edn/str` de `semantic_test`.
