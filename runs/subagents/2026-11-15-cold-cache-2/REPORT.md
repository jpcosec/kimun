# cold-cache-2 — fase 2 (puntos A y B)

## Archivos tocados
- `src/kimun/surface/docs.cljc`: nuevo `file-missing!` (raise `:docs/file-missing`, datos `{:document :path}`); `document` lo lanza cuando el documento tiene ruta y `:source` nil.
- `src/kimun/cli/commands/docs.cljc`: `render` y `show --ast` lanzan `file-missing!` (si el documento, tipado o genérico, existe) antes del fallback `md/store->*` que producía `not-a-document {:id nil}`; se borró `aligned-spans` privada y sus requires `canon`/`ast`/`blocks` (ahora requiere `capture`).
- `src/kimun/surface/models/capture.cljc`: `aligned-spans` queda como la única; docstring ampliado (solo docstring).
- `test/kimun/cli/cold_cache_test.cljc`: +2 `deftest`.
- `docs/v2/10-escala.md`: +1 párrafo en §11 (el error de este caso).
- No toqué `fields.cljc` (el NPE nacía en `surface/docs/document`: `extract` sobre fuente nil), ni `anchors.cljc`, ni kernel, ni `docs/v2/12`.

## Tipo de error
`docs/file-missing` ya existía como nombre de señal en `cli/commands/stores.cljc` (`warning-type` de `:missing`); como error `*/missing` da exit 1 por `kimun.cli.errors` (sufijo). No hay otro tipo declarado de error para «falta el archivo» (`docs/missing` es «no hay documento con ese nombre»). Ambigüedad menor: el nombre pasa de solo-señal a también-error; `06` no lo lista en la tabla de exit 1 (no lo edité).

## Antes (repro-before.txt, proyecto temporal, `cache/` borrada + archivos borrados)
`docs show a --ast`/`docs render a` (y genérico) → `markdown/not-a-document` «missing node {:id nil}»; `docs show a`, `fields get a title` → `cli/unexpected` NPE. Después: los seis dan `:ok false`, `docs/file-missing`, `:data {:document :path}`.
`repro-before.txt` contiene la salida de antes del cambio (la de después la vi en consola y está reflejada en las pruebas).

## Caso no tocado: caché caliente + archivo ausente (verificado)
`docs show a --ast`, `docs render a`, `docs show a`, `fields get a title`, `docs show plain --ast`, `docs render plain` responden `ok` desde la caché/store (texto canónico); `docs status` da `:missing`. Sin cambios.

## Pruebas
1. `absent-file-and-cold-cache-is-a-declared-error`: 6 comandos (show, show --ast, render, fields get typed; show --ast, render generic) con `cache/` y archivo borrados ⇒ `:ok false`, `:error :type "docs/file-missing"`, `:data :path` y `:document` correctos, mensaje con el nombre.
2. `present-file-and-cold-cache-is-rebuilt`: los mismos 6 con archivo presente y `cache/` borrada responden idéntico a la caliente. Es de regresión: pasa también sin el cambio (no discrimina; la 1 sí).
Mutación (`mutation.txt`, `src/` al estado base): 24 fallos, 0 con el cambio. Pruebas de brechas (`known-cold-cache-gaps`, journal) intactas y verdes.

## Punto B
Única `capture/aligned-spans` (surface, `[text]`, host por defecto); `docs anchors` la usa. Anillos: cli→surface permitido, `bb lint` ok (110 archivos). Cuerpo idéntico al de M1j; `docs_anchors_test`, `anchors_test`, `capture_test` sin tocar y verdes.

## Validación
`directed.txt`: 12 namespaces (cold_cache, docs, typed_docs, generic_docs, fields, docs_anchors, anchors, capture, marks, field_units, tour, fault_injection): 75 tests, 843 aserciones, 0 fallos. `lint.txt`: ok. (Tras restaurar, repetí cold_cache+docs_anchors+capture+anchors: 188 aserciones, 0 fallos, y lint ok.)

## Incidente y límites
- El worktree `/tmp/kimun-docsui-wt` es compartido con otro ejecutor (hay cambios suyos en `src/kimun/cli/commands/include.cljc`, no míos). Mi `git stash push -- src/` para la mutación desapareció del stash antes de mi `pop` (otro proceso lo sacó); recuperé mis cambios del commit colgante y los reaplicé solo en mis 3 archivos de `src/`. Verifiqué después. No usar stash en un worktree compartido.
- `journal show`/`journal verify` sin tocar. `docs show` genérico por nombre sigue sin resolver (`docs/missing`).
- Mi corrida de `directed.txt` fue antes de la mutación; `include.cljc` ajeno estaba sin modificar entonces o no entraba en estas pruebas (no lo verifiqué).
- No se corrió `bb test` completo ni se commiteó.
