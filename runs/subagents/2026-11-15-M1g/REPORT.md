# M1g — documentos tipados con una clase spec 16 (ancla compuesta)

Worktree `/tmp/kimun-m1g-wt`, sin commit.

## Archivos tocados
- `src/kimun/surface/docs.cljc`: `extract-under` (v1 -> `extract/extract` sin cambios; spec 16 -> selector y campos compuestos con `subgraph/compose-*`, modo = `:substrate :compat`); `current-lineage` (ancestros vigentes, ancestor-first; exige que sean spec 16); `track-plan`/`update-plan` extraen con la cadena vigente y pasan `:ancestors` a `registry`/`binding-ops`, que lo guardan en la evidencia de la arista ancla (junto a `:contract`), solo si no está vacío; `describe` lee `:ancestors` y marca `:superseded` si la cadena registrada ya no es la vigente (`chain-moved?`); `payload-of` lee bajo la cadena registrada (`recorded-ancestors`, descriptores del CAS); `document` marca `:spec16`.
- `src/kimun/cli/commands/docs.cljc`: `doc-data` añade `:class` y `:ancestors` solo para spec 16; `docs track` no hace ida-vuelta/ruido para spec 16 (no hay `:template`); `docs create` y `docs update --data/--set` se rechazan con `cli/usage` (generar es M2); `docs update` y `reanchor` no corren `refuse-noise!` ni `move-text` para spec 16.
- AMPLIACION (pedida por el coordinador): `src/kimun/surface/docs.cljc` gana `extract-doc` (misma ruta que `payload-of`, con la cadena registrada). `src/kimun/surface/diagnostics.cljc` (`try-extract` usa `extract-doc`; sin chequeo de ruido para spec 16, que sin esto daba una señal `:noise` falsa) y `src/kimun/surface/index/build.cljc` (`extraction` usa `extract-doc`; `model-sections` tolera `:template` nil) dejan de extraer con `extract/extract` directo; v1 sin cambios. `src/kimun/cli/commands/fields.cljc` (mutaciones) y `src/kimun/cli/commands/sections.cljc` (`sections fields`) rechazan spec 16 con `cli/usage`.
- `docs/v2/06-superficies-y-cli.md`: filas `docs track` y `docs show`.
- `test/kimun/cli/spec16_docs_test.cljc` (nuevo, 8 pruebas, 86 aserciones). No se tocó `store.cljc`, ni selector, subgraph, capture, include, anchors, parse_contract, kernel.
- Ninguna prueba existente se ajustó.

## Decisión [inferencia del equipo, marcada en el código]
Cadena fijada por revisión exacta: `:contract` (clase) + `:ancestors` (ids de descriptores, ancestor-first) en la evidencia de la arista ancla. No cambia el formato del store ni entra en `merkle-root`, `projection-hash` ni en la ruta (solo evidencia de arista, como `:contract`). Un documento v1 o una clase sin bases no registra `:ancestors`: ids y respuestas v1 idénticos (las 16 suites v1 verdes). Lectura del spec §4.3 (L214-216): "la versión previa continúa existiendo... un documento ya clasificado conserva en su evidencia `:contract` la referencia a la clase exacta"; como `:base` es un nombre, la referencia a los ancestros es lo que falta para que "clase exacta" incluya el selector heredado. No encontré en §4.3 nada que contradiga esto.
Otras inferencias mías: (a) una clase spec 16 que hereda de un modelo v1 se rechaza al anclar (`:models/invalid`: no hay selector que componer); (b) si el registro falta pero la clase tiene bases, se usan los ancestros vigentes.

## Números
- `bb lint`: ok, 110 archivos (`lint.txt`). `bb oracle`: 9 filas, 0 diferencias (`oracle.txt`).
- Dirigidas (`directed.txt`), 0 fallos y 0 errores en tres tandas (no corrí `bb test` completo): A 70 pruebas/644 aserciones (typed_docs, docs, docs_parity, cli.corpus, index.corpus, golden, field_units, cli.marks, surface.marks, reanchor, identity_carry, model_carry); B 37/361 (parity [2384 pass], cold_cache, fault_injection, tour, contract, sense, spec16_docs); C 142/1612 (cli.diagnostics, parity.diagnostics, surface.diagnostics, find, heartbeat, index, stores, index.{address,build,cache,search,where}, fields, sections).

## Las pruebas
1. `a-document-extracts-own-and-inherited-fields`: payload con campo propio (`summary`) y heredado (`title`), `:intact`, ambos campos reciben unidad (K1c).
2. `the-compat-mode-of-the-class-decides-how-it-extracts`: el mismo archivo sin `## Summary`: clase `:strict` -> `validate/missing`; clase `:legacy-v1-template` -> captura el cuerpo.
3. `editing-the-parent-does-not-change-an-anchored-child-silently`: tras redefinir el padre, el hijo anclado conserva payload y `:ancestors`, pasa a `:superseded`; un documento nuevo (control) sí lee con el padre nuevo.
4. `an-update-after-redefining-the-parent-reanchors-the-child`: `docs update` -> `:intact`, payload bajo el padre nuevo, `:ancestors` cambia, `:class` no.
5. `an-unselected-paragraph-moves-neither-the-hash-nor-the-route`: un párrafo no seleccionado no cambia `:content-hash` ni el `:content-hash` de la ruta; control: una edición seleccionada sí.
6. `show-reports-the-exact-class-of-a-spec16-document`: `docs show` da `:class` = id del descriptor y `:ancestors` = [id del padre], presentes en el store.
7. (extra, pedida por el encargo) `a-v1-document-is-untouched-and-answers-as-before`: GUARDIA, pasa también sin el cambio; v1 sin `:class`/`:ancestors`, `sense` 0 señales.
El plan pedía 6; la de `:compat` es extra (pedida en el encargo) y la guardia 7 cubre el caso 5 del plan; el sensor sobre un documento spec 16 lo cubre la 8.

8. `readers-and-sensors-answer-on-a-store-with-a-spec16-document`: tabla de 15 comandos de lectura/sensor sin `cli/unexpected`; `fields set`, `sections fields` y `docs create` dan `cli/usage` que nombra «spec 16»; en reposo `sense` 0 señales y `health` `:ok`; editar el campo seleccionado `title` da `source/mutated` con `:fields ["title"]`.

## Mutación (`mutation.txt`)
- A: `src/` a HEAD: 28 fallos en las 6 pruebas nuevas; solo la guardia 7 pasa (es guardia).
- C (ampliación): con diagnostics, index/build, fields y sections a HEAD fallan 13 aserciones de la prueba 8.
- B: `chain-moved?` siempre falso: falla la 3. `src/` restaurado (diff -r vacío, sin `git stash`).

## Estado de cada comando con un documento spec 16 (reproducción: `repro-stores-npe.txt` antes, `repro-after.txt` después)
Antes reventaban (`cli/unexpected`, NPE): `stores sense`, `stores health`, `stores check`, `stores update`, `stores heartbeat --beats 1` (beat con error, el comando salía ok), `find` y `sections fields`. Causas: `diagnostics/try-extract` y `index/build/extraction` (extraían con `:template`), `index/build/model-sections` (`field-owners` con plantilla nil), y `sections fields`.
Ahora (0 `cli/unexpected` en `repro-after.txt`):
- Responden: `stores sense|health|check|verify|update|heartbeat`, `find`, `fields get` (devuelve `owning_section nil`: no hay plantilla de la que sacar secciones), `sections list`, `docs anchors|marks|list|show|status`, `journal show`, `models check`, `models define` (redefinir una clase deja sus documentos `:superseded`; no reescribe).
- Rechazo declarado `cli/usage` que dice «spec 16»: `docs create`, `docs update --data|--set`, `fields set|append|clear|remove|create|clean` (toda mutación), `sections fields`.
- `stores update` sobre un documento spec 16 responde; no probé su efecto sobre uno `:superseded` (no hay renderizador, no debería reescribir).
- `include build`: falla declarado en mi repro solo porque el proyecto temporal no es un repo git; NO probé `include build` con una clase spec 16 en un repo real: sigue fuera de este corte, límite abierto.
- `fields schema|query`, `models list|show` y las vistas HTTP (`http/views.cljc`, `routes.cljc` leen `:template`): no los probé con spec 16.

## Límites
1. `docs create` / `docs update --data|--set` y las mutaciones de `fields`: sin renderizador (M2).
2. K6 del selector spec 16 (M1h) no se hizo: un cambio del padre se ve por `:superseded`, no por `contract/changed`.
3. `docs update` sin cambios de texto sobre un documento `:superseded` por un ancestro sí reescribe; es lo buscado.
4. El chequeo de ruido (`noise`) no se aplica a spec 16 en `docs track|update|reanchor` ni en `sense`.

## Propuesta fila M1 de `docs/v2/12`
M1g (2026-11-15): `docs track|update` aceptan clases spec 16: extraen con el selector compuesto (propio + heredado) en el `:compat` declarado; el documento registra en la evidencia de su ancla la clase exacta y los ancestros con los que se compuso; redefinir la clase o un ancestro lo deja `:superseded` sin cambiar su payload hasta `docs update`; `docs show` da `:class` y `:ancestors`. `stores health|sense|check|update`, `find` y el índice leen un documento spec 16; las mutaciones de `fields`/`docs create`/`sections fields` se rechazan declaradas (M2). Evidencia `runs/subagents/2026-11-15-M1g/`.

## Para el dueño: puntos del spec interpretados
- `docs/v2/16` §4.3: "Una clase cambiada es otra clase... Un documento ya clasificado conserva en su evidencia `:contract` la referencia a la clase exacta con la que fue anclado". Interpretado como: la clase exacta incluye la revisión de cada ancestro con el que se compuso el selector (§4.2 "ancestor-first"), porque `:base` es un nombre. Alternativa del plan §3.4 (b): resolver al leer y que K6 lo vea como `contract/changed` en los hijos; no es incompatible y es M1h.
- `docs/v2/16` §4.2: un ancestro v1 no tiene selector; no está dicho si es válido. Lo rechazo al anclar.
