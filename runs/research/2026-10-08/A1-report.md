# A1 — un documento se lee bajo el contrato con el que se escribió

Rama `close/A1` (worktree `/home/jp/proyectos/kimun-worktrees/A1`), 2 commits:
`09dc0428` (fix + tests + docs) y `a98e0e73` (evidencia en `runs/subagents/A1/`).

## Qué cambió y por qué

Antes, `payload-of`/`document` extraían siempre con el descriptor ACTUAL del modelo, así que
`models define`/`field-add` sin `--backfill` cambiaba el payload de un documento viejo en
silencio (una sección añadida tras el último hueco se tragaba el cuerpo). Ahora:

- La arista `:derived` root→fuente guarda `:contract` = id del descriptor con el que se
  extrajo. Esa arista apunta a la fuente, no al modelo, así que la sucesión del modelo
  (que re-ancla `reference` e invalida `derived` hacia el modelo) la deja intacta y el
  descriptor viejo sigue en el CAS.
- Leen ese descriptor: `payload-of`, `document`, `fields` (CLI y superficie), `sections`,
  `find`/índice (`index/build`), `edges` y `diagnostics`. El estado sigue siendo
  `:superseded`, y `stores check`/`health` lo reportan con la acción de actualizar.
- Escrituras que mueven al contrato actual re-renderizando el payload registrado (no
  re-leyendo el texto viejo bajo el template nuevo): `docs update NAME` (sin FILE/`--data`),
  `stores update` (y reescribe el fichero), `models define|field-add … --backfill`.
- Documento sin `:contract` (previo a A1 o migrado de v1): cae al contrato actual del modelo
  y ahí sí puede quedar `:error`/`skipped`.

Sin cambios en `src/kimun/kernel/`: el diseño usa claves de evidencia adicionales admitidas
por `kernel.edge/validate` (por eso no toca `bb oracle`).

## Pruebas

Nuevas:
- `test/kimun/cli/typed_docs_test.cljc`: contrato movido deja el payload idéntico hasta
  `docs update`; tras update sigue el contrato nuevo; `stores health` :degraded y el finding
  `:contract-moved` con la acción `kimun docs update login`.
- `test/kimun/parity/optional_field_hash_test.cljc`: `field-add-of-an-optional-field-leaves-existing-documents-unchanged`
  (antes era una brecha en `known_gaps_test`).
- `test/kimun/surface/index/build_test.cljc`: contrato movido conserva el documento bajo su
  contrato registrado; y el fallback sin `:contract` (legacy) sí se marca `:error`/`skipped`.
- `test/kimun/surface/http/documents_test.cljc`: el contrato movido no inhabilita el IR (200);
  el 422 queda para el documento sin `:contract`.
- `test/kimun/test_util.cljc`: helper `forget-contract!` (simula un documento sin `:contract`).

Adaptadas (expectativa vieja → nueva, con el porqué en el commit y en cada test):
`parity/diagnostics_test`, `parity/cache_promises_test` (tags y contrato),
`cli/index_test`, `surface/index/cache_test`.

## Verificación (memcap)

```
$ memcap timeout 300 bb lint
rings and docstrings ok: 96 files

$ memcap timeout 900 bb test
{:test 878, :pass 9499, :fail 0, :error 0, :namespaces 107}
Ran 107 namespaces in 141 s

$ memcap timeout 900 bin/kimun include build --rebuild
{ "ok": true, "data": { "models": 17, "documents": 539, "failed": [] }, "warnings": [] }

$ memcap timeout 900 bin/kimun include status
{ "data": { "models": 17, "tracked": 539, "forced": 209,
            "findings": { "declared-missing":0, "declared-not-in-store":0, "declared-not-tracked":0,
                          "store-tracked":0, "store-undeclared":0, "tracked-undeclared":0 } } }
```

Salidas completas en `runs/subagents/A1/` (`bb-lint.txt`, `bb-test.txt`,
`include-build.json`, `include-status.json`). No se corrió `bb oracle` (kernel intacto).

## Notas

- Una corrida anterior dio falsos fallos (y una `bb test` de A2 en el mismo /tmp) por
  “No space left on device”: limpié los `/tmp/kimun-test-*` colgados y repetí en limpio.
- `fields set` sobre un documento `:superseded` muta el payload registrado y renderiza con el
  contrato actual; si el contrato nuevo exige un campo sin default, el render falla (igual que
  el backfill), que es el comportamiento deseado.
