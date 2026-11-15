# M1f — descriptores spec 16 en el store y jerarquía consultable

Worktree `/tmp/kimun-m1f-wt` (base `876ba5a8`), sin commit.

## Archivos tocados
- `src/kimun/surface/models/descriptor.cljc`: `validate!` rechaza `:template` + `:anchoring` juntos.
- `src/kimun/surface/models/store.cljc`: `spec16?`, `validate-descriptor!` (v1 XOR spec 16; `:document-role :generated` se rechaza con mensaje que nombra F10 vía `subgraph/blocked-by`); `define-plan` valida con él; `same-contract?` ignora, solo para spec 16, `:contract/digest` declarado (spec 16 §3.3: K6 lo recalcula). Para v1 la noción de «igual» no cambia. `serialize` y `node-id` no se tocaron.
- `src/kimun/cli/commands/models.cljc`: `define` acepta spec 16; `models list --isa BASE` (opción `:isa`); `models check` valida spec 16 (forma, F10, base ausente, ciclo, campo heredado debilitado con `subgraph/check-composition!`); `list` cuenta `:anchoring :fields`; `show` proyecta selector en vez de template para spec 16.
- `docs/v2/06-superficies-y-cli.md`: filas `models define` y `models list` (`--isa`). `cli/main.cljc` no se tocó (la opción entra por `models/spec`).
- Tests: `test/kimun/cli/models_test.cljc` (+5), `test/kimun/surface/models/store_test.cljc` (+1).

## Números
- Dirigidas (`directed.txt`): models-test, store-test, subgraph-test, compile-test, model-carry-test, identity-carry-test, include-test, linked-models-test, migrate-test: 60 tests, 525 aserciones, 0 fallos, 0 errores (un SKIPPED preexistente de migrate: falta el store real de hum-ecosystem).
- `bb lint`: ok, 106 archivos. `bb oracle`: 9 filas, 0 diferencias (`oracle.txt`).
- `model_carry_test` e `identity_carry_test` verdes sin tocarlas. Ningún test existente se ajustó.

## Las 6 pruebas
1. `spec16-hierarchy-is-queryable`: `list --isa PrimitiveDoc` trae Primitive, Resumen, ResumenTecnico (transitivo, incluye la propia base); un v1 es su propia clase; base inexistente = `models/missing`.
2. `spec16-redefine-is-a-new-version-only-when-the-selector-changes`: mismo contrato (aunque cambien versión declarada y digest) = `:unchanged`; otro selector = v2 con cadena `supersedes` y la v1 conserva su dirección.
3. `spec16-check-reports-missing-base-and-cycles`: base ausente y ciclo hacen fallar `models check` (exit 3); `list --isa` con ciclo falla con `models/invalid`, no se cuelga.
4. `v1-descriptor-keeps-its-address-byte-for-byte` (store_test): `node-id` de `ticket.edn` v1 = `838f817f…e4d4`, calculado en `876ba5a8` antes de cambiar nada; `serialize` no agrega claves.
5. `spec16-generated-role-is-blocked-by-f10`: exit 3, mensaje con «F10», nada escrito.
6. `descriptor-with-template-and-anchoring-is-rejected`: exit 3, «mutually exclusive», nada escrito.

## Mutación (`mutation.txt`)
- A (`git stash push -- src/`): 17 fallos + 2 errores en las 5 pruebas de CLI. La prueba 4 es guardia y pasa con el código previo, como corresponde.
- B (`serialize` agrega una clave): falla la prueba 4 y `plans-define-list-and-keep-history`. Restaurado.

## Decisiones e inferencias
- **[inf]** `--isa` es reflexivo (BASE se incluye), por el test 1 del plan y porque `isa?` de Clojure lo es. La forma de la respuesta es la de `models list` sin campos nuevos. El spec no la fija: §4.1 (spec 16, L195-200 aprox.) solo habla de «filtros, consultas e índices transversales (P13)».
- **[inf]** `--isa` ignora bases no definidas en la jerarquía (las reporta `models check`) para no romper `list` por un v1 con base suelta; un ciclo sí falla.
- `define` no exige bases definidas (el docstring de `define-plan` lo permite y el plan §3.4 recomendaba exigirlas para spec 16): lo reporta `check`. Decisión del plan pendiente del dueño/M1g.
- No se acuñan ids de modelo (M1k). `:id` en las respuestas sigue siendo la dirección del valor.

## Límites y desvíos
- Con documentos existentes, redefinir a/desde spec 16 no reescribe documentos (`rewrites` solo corre si ambos lados son v1): no hay template que renderizar; la extracción con la clase nueva es M1g. `models check` tampoco extrae documentos contra clases spec 16.
- Mensaje F10: «`:document-role :generated` is blocked by open decision F10 (docs/v2/16 §2.3): not implemented». `blocked-by` de M1a decide el código F10.
- La composición se valida solo en `check`, no al definir.
- Rama sin commit; hay que resolver el `Board`/`docs/v2/12` fuera de este corte.

## Línea propuesta para la fila M1 de `docs/v2/12`
M1f (2026-11-15): `models define` acepta descriptores spec 16 (XOR v1; `:generated` rechazado por F10), `models check` valida bases y ciclos, `models list --isa BASE` consulta la jerarquía; el v1 conserva su dirección; evidencia `runs/subagents/2026-11-15-M1f/`.
