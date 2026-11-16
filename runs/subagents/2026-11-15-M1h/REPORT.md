# M1h — K6 para selectores spec 16

Worktree `/tmp/kimun-m1h-wt`, sin commit.

## Archivos tocados
- `src/kimun/surface/parse_contract.cljc`: contrato versión 2 solo si el llamador pasa un `reading` (`components-spec16` = los 6 + `:substrate :profile :compat :ancestors :selector`; `components-of` elige por `:contract/version`; `reading`; `record`/`contract`/`compare-recorded` ganan una aridad opcional). Sin `reading` todo es la versión 1 de siempre. `consistent?`, `digest` y `:changed` usan los componentes de la versión del registro. Un registro con `:substrate` `treesitter:…` y sin pod da `:parser-unavailable`.
- `src/kimun/surface/docs.cljc`: `reading-of` (privada: sustrato, `:compat`, ancestros y texto canónico del selector y campos COMPUESTOS, vía `mstore/serialize`), `current-reading` (pública: lectura vigente de una clase por id, con el linaje de hoy; un linaje que ya no resuelve da una lectura propia, no un error); `track-plan`/`update-plan` pasan `:reading` a `registry`/`binding-ops` y a `compare-recorded` (decide `base-same?`). `move-text`: ver «Desvío».
- `src/kimun/surface/diagnostics.cljc`: una línea, `compare-recorded` recibe `(tdocs/current-reading store (:model-id doc))` (nil para v1, así que idéntico).
- `docs/v2/10-escala.md §11.1`: un párrafo («Clases spec 16 (M1h)»).
- `test/kimun/cli/spec16_contract_test.cljc` (nuevo, 8 pruebas, 55 aserciones). Ninguna prueba existente se ajustó. No toqué `cli/commands/*`, `selector*`, `subgraph`, `anchors`, kernel, ni el formato del store.

## Qué registra y cómo se compara
Un documento spec 16 registra en `:parse-contract` (evidencia de la arista ancla, como `:contract`): versión 2, `:substrate "sldb-md/blocks:markdown"`, `:profile`, `:compat "strict"`, `:ancestors [ids]` y `:selector "sha-256:…"` (digest de selector+campos compuestos). Al sensar se recalcula contra el linaje vigente: `:changed` nombra `:class` (redefinió su clase), `:ancestors`/`:selector` (cambió un ancestro). El `:contract/digest` del descriptor no se lee en ningún sitio: el digest se calcula siempre.

## Coherencia con M1g (una sola señal)
Antes: redefinir la clase o un ancestro dejaba al documento `:superseded`, y `diagnostics` ya lo convertía en categoría `:contract-moved` (`contract/changed`), pero con `:contract {:status :same :changed []}` para el caso del ancestro (la señal decía «cambió» y el detalle «igual»). Ahora es la misma señal `contract/changed` y el detalle dice qué: `[:class :selector]` o `[:ancestors :selector]`. No hay una segunda señal: `:superseded` es el estado, `contract/changed` la señal, un solo hecho. Si cambian fuente y selector, manda la categoría de la fuente (regla vigente de `10 §11.1`).

## Pruebas (una función por caso)
1. `the-contract-of-v1-and-generic-documents-is-byte-for-byte-the-old-one` — GUARDIA (pasa también sin el cambio, y lo dice): digests fijados calculados en `main` antes de tocar nada: `class-a` -> `sha-256:c263d6ab5357de523bc5de02f848442195bf3bfd374b3af090d0f5c28ac4e742`, `generic/md` -> `sha-256:516e55e2a1008cc0f3edd5a958de201c0638bae67014bf28b86145c39e27d077` (parser nativo, versión 1); un documento v1 de punta a punta registra exactamente `components`+`:digest`, versión 1, y `sense` da 0 señales.
2. `a-spec16-document-records-substrate-profile-compat-ancestors-and-selector`: forma del registro v2 y que sensa limpio.
3. `editing-a-step-of-the-own-selector-is-a-contract-change-not-a-source-change`: redefinir el paso -> una señal `contract/changed` con `content-ok` true y `[:class :selector]`, el sensor no escribe, `stores update` repara y queda sin señales.
4. `editing-an-inherited-step-is-the-same-single-contract-change`: editar el padre -> UNA señal `contract/changed` `[:ancestors :selector]`, y a la vez el documento está `:superseded`; `stores update` repara y cambian los `:ancestors` registrados.
5. `editing-the-file-is-a-source-change-not-a-contract-change`: `source/mutated`, `contract-ok` true, `[]`. (Regresión: también pasa sin el cambio.)
6. `a-tree-sitter-substrate-without-the-pod-is-parser-unavailable`: nivel `parse_contract` con `:pod? false` en el entorno (no con el pod real); con pod, `:same`.
7. `rebuild-mismatch-works-for-a-spec16-class`: raíz registrada falsa + caché fría -> `contract/rebuild-mismatch` con raíz registrada y reconstruida, `docs update` repara. (Regresión: el comportamiento ya existía; la prueba fija que el documento es de contrato v2.)
8. `a-declared-contract-digest-is-ignored-and-recomputed`: una clase con `:contract/digest` declarado registra un digest distinto, recalculado y consistente; dos clases que solo difieren en el digest declarado tienen el mismo texto de selector.

## Números
- `bb lint`: ok, 110 archivos (`lint.txt`). `bb oracle`: 9 filas, 0 diferencias (`oracle.txt`).
- Dirigidas (`directed.txt`), 0 fallos, 0 errores, en cuatro tandas: contract, sense, heartbeat, tour, spec16_docs, spec16_contract, parse_contract, surface.diagnostics (58 pruebas/522 aserciones); typed_docs, generic_docs, field_units, marks, cold_cache, stores (68/683); cli.diagnostics, parity.diagnostics, docs, docs_anchors, docs_parity, routes, find, reanchor, fields (59/961); models.parity, golden, corpus, spec16_models_contract (12/340 la última). No corrí `bb test` completo.
- Mutación (`mutation.txt`, `src/` restaurado, `diff -r` idéntico, sin `git stash`): A (lectura spec 16 apagada) 8 fallos + 1 error en 3 pruebas (la 2, la 3 y la 4); B (sin cláusula tree-sitter) falla la 6; C (`move-text` sin la rama spec 16) falla 3 y 4. Con `src/` en HEAD el archivo de pruebas ni compila (`pc/reading` no existe). Las pruebas 1, 5 y 7 son guardias/regresión y no discriminan por el cambio (la 8 sí: asevera versión 2).

## Desvío (para revisar)
`stores update` sobre un documento spec 16 `:superseded` daba `cli/unexpected` (NPE: `stores.cljc` llama `tdocs/move-text`, que renderiza con `:template`; M1g lo dejó sin probar). Sin arreglarlo la reparación «sin decisión» no existía. Cambié `move-text` en `docs.cljc` (fuera de la lista que me diste): para una clase spec 16 devuelve el texto que el documento ya tiene (`:source`), porque no hay renderizador (M2); `stores.cljc` solo lo reescribe igual. No toqué `cli/commands/*`.

## Límites
1. `docs anchors` y `docs marks` (`cli/commands/docs.cljc`, líneas 573 y 714) muestran un `:parse-contract` v1 recalculado con `(:class d)`, no el registrado; no lo toqué (otro ejecutor). Es solo la presentación.
2. Un documento spec 16 anclado con M1g (contrato v1) se compara como v1 (nada que informar) y no avisa de cambios de selector hasta su próxima escritura (`docs update`/`stores update`). Es la regla «sin registro, no se afirma» de `10 §11.1`; no hay migración.
3. El sustrato `treesitter` solo afecta la señal `parser-unavailable`: el extractor lee bloques SLDB-MD sea cual sea el sustrato (CST es M1i). Un store con clase `treesitter` y sin pod dará `contract/parser-unavailable` aunque el extractor no use el pod.
4. Un `:ancestors` más el digest hace que cambiar solo las proyecciones de un padre (no el selector) también sea `contract/changed` en los hijos: coincide con `:superseded` de M1g (por id), no con «solo el selector».

## Interpretaciones (para el dueño)
- `16 §3.3`, L146: «`:contract/digest "sha256:…"` ; digest K6: gramática+perfil+NFC+selector+clase» y L166-168: «un cambio en el descriptor produce `:contract-changed`». El descriptor no puede contener su propio digest: lo ignoro y lo recalculo (plan M1 §3.8). Incluyo además `:compat`, `:ancestors` y la versión en el digest (el spec solo dice «perfil»).
- `16 §4.3` L221-222: «conserva en su evidencia `:contract` la referencia a la clase exacta» frente a `10 §11.1` «cambió el contrato (… o clase) ⇒ `contract/changed`, reparación segura». [inferencia] Mantengo ambos: la clase exacta queda fijada (M1g) y que ya no sea la vigente se informa como `contract/changed`, no como edición. Si el dueño quiere que un padre editado NO cambie la señal de los hijos ya anclados (§4.3 «clase inmutable»), hay que decidirlo; hoy se reporta (M1g ya lo hacía con `:superseded`).
- Versión 2 del contrato en vez de añadir claves condicionales a la v1: así `consistent?` y los digests v1 no cambian por construcción (plan §4, riesgo `contract/changed` en masa).

## Propuesta fila M1 de `docs/v2/12`
M1h (2026-11-15): el contrato de lectura (K6) de un documento de una clase spec 16 registra sustrato, perfil, `:compat`, ancestros y el digest del selector compuesto (versión 2; v1 y genéricos intactos, byte a byte); editar un selector propio o heredado se sensa como `contract/changed` (una sola señal con `:superseded`), editar el archivo como `source/*`, la reparación es `stores update`; el `:contract/digest` declarado se ignora; `treesitter` sin pod da `parser-unavailable`. Evidencia `runs/subagents/2026-11-15-M1h/`.
