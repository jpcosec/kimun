# K1f — cierre y traslado declarados de unidades

## Archivos tocados
- `src/kimun/surface/docs.cljc`: `update-plan` acepta `:units` explícito (declaración, sin acuñar); nuevos `unit-field`, `close-unit-plan`, `move-unit-plan` (usa `kernel.plan/compose-plans`: una sola revisión con los dos árboles; el kernel no se tocó).
- `src/kimun/cli/commands/docs.cljc`: `docs unmark` acepta id de unidad de campo (`--reason` obligatorio); `docs mark OTHER --unit ID --from NAME (--locator L | --field F) --reason R` (`move-unit`). `--from` y `--field` ya existían como opciones compartidas de otros grupos (mismo significado de cadena); no se redeclaran.
- `docs/v2/06` (dos filas de comandos), `docs/v2/14 §21` (párrafo K1f rotulado inferencia del equipo; «Quedan fuera» ajustado; no toca frases fijadas por `doc14_contract_test`). `12 §1` no se tocó.
- Prueba nueva `test/kimun/cli/unit_close_move_test.cljc` (7 deftests, 158 aserciones). Ningún test existente se ajustó.

## Qué prueba cada test
1. Cerrar campo presente: sin `--reason` se rechaza sin mover el head; con él, la id sale, journal `unit-closure` (`:status :anchored :field :reason`); `docs update` acuña id **distinta**.
2. Cerrar campo ausente (`:missing`): sale de `:units`; tras `include build --rebuild` no vuelve (la id cerrada no reaparece, `title` se arrastra).
3. Trasladar marca g1→g2 (genéricos): misma id y etiqueta en g2, ausente en g1, **+1 entrada de journal**, `:declared unit-move` con origen, destino, razón y localizador.
4. Trasladar unidad de campo a→b: rechazada (`docs/exists`) mientras b tiene unidad en el campo; tras cerrarla, mueve con la misma id, +1 revisión.
5. Ocho rechazos con head igual (sin reason, localizador tomado, unidad inexistente, locator+field, campo fuera del payload, campo hacia genérico, mismo documento, unmark de id desconocida).
6. Sobrevive a `docs reanchor` y `include build --rebuild` (`:marks-carried 1`, mismas ids).
7. `:units`/`:marks` no cambian `merkle-root`/objetos; tras un traslado, `docs marks`/`docs show` no cambian head ni sha256 de `.kimun`.

## Números
- Mutación (`mutation.txt`, src revertido a la base, restaurado idéntico): los 7 fallan (37 fallos). Con el cambio: 0 fallos.
- Dirigidas (`directed.txt`): 77 tests, 1083 aserciones, 0 fallos (incluye field_units, marks cli, anchors_test, reanchor, identity_carry, typed_docs, generic_docs, docs, spec16_docs, tour, doc14_contract).
- `bb lint`: 110 ficheros ok. `bb oracle`: 9 filas, 0 diferencias.

## Interpretaciones (citas)
- «Cerrar» un campo presente no acuña en la misma escritura; la id nueva llega en la siguiente (brief D1; `14 §21` regla 5: el reemplazo se declara). Cualquier `docs update` posterior acuña.
- Trasladar un campo exige que el campo de destino **no tenga unidad**; como K1c acuña una unidad para todo campo presente, en la práctica hay que cerrar antes la del destino (brief: «no se fusiona», regla 4).
- `--field F` en el destino puede tener otro nombre que el campo de origen (traslado entre documentos, I7). No es renombrar dentro del documento (Q3, no implementado). Si el dueño prefiere exigir el mismo nombre, es una línea.
- La etiqueta de una marca trasladada se conserva salvo `--label`.
- Un cierre/traslado en documento sin descriptor persistente (inline/legacy) se rechaza con `docs/not-anchored`.

## Límites
Sin rename en el documento (Q3), sin linaje/copiar/dividir/fusionar (Q2), sin anidados/ítems (K1h), sin sensor (U1), sin modelos como unidades. Journal: `:declared` de cierre/traslado; `:units`/`:marks` siguen sin entrar en huellas. Ningún desvío del brief salvo que son 7 tests (la prueba 6 del brief se partió en dos: «no es huella» y «solo lectura» van juntas, y el traslado de campo se separó del de marca). El brief tiene una incoherencia menor: dice que `include.cljc` solo se verifica; no se modificó.

## Línea propuesta para la fila K1 de `12 §1`
K1f (2026-11-15): cierre de unidades de campo (`docs unmark ID --reason`) y traslado declarado de unidades (marca o campo) a otro documento con la misma id en una revisión (`docs mark OTHER --unit ID --from NAME`); pendientes: rename, linaje, anidados e ítems, copiar/dividir/fusionar, sensor, modelos. K1 no está cerrado.
