# K1h — campos anidados e ítems de lista como unidades, por declaración

Base `3fcf6b91` (con K1c, K1e, K1f), worktree `/tmp/kimun-k1h-wt`, sin commit. Kernel y `store` sin tocar.

## Archivos tocados
- `src/kimun/surface/markdown/anchors.cljc`: `field-anchors [host payload]` (puro) da `{:items [{:locator {:field F :index I} :hash}] :nested [{:locator {:field F :path [k …]} :hash}]}`; `resolve-anchor`, `mark-states` y `advance-marks` aceptan los `:kind` nuevos `:item` y `:nested` (tabla de estados idéntica a K1e; sin mecanismo paralelo). Los candidatos de un ítem o una clave anidada se buscan solo dentro del mismo campo.
- `src/kimun/surface/docs.cljc`: `doc-update` avanza las marcas con las anclas de texto más `field-anchors` del payload extraído; `marks-of` gana una aridad con payload; nuevo `mark-anchors`.
- `src/kimun/cli/commands/docs.cljc`: `docs mark NAME --field F --index N` o `--field F --path a.b` (o `--locator` con `{"field":…,"index":…}` / `{…,"path":[…]}`); `locator-kind` reconoce `:item`/`:nested`; la misma ruta sirve para marcar, re-anclar (`--unit ID --reason`), cerrar (`docs unmark`) y trasladar (`docs mark OTHER --unit ID --from NAME --field F --index N`). Opción nueva `--index` (cadena, se convierte a entero: el parser global exige valores de cadena). `docs marks` y `docs show` pasan el payload. `--field` sin `--index`/`--path` sigue siendo la unidad de campo de K1f.
- `docs/v2/06` (fila de `docs mark`), `docs/v2/14 §21` (párrafo K1h, rotulado inferencia del equipo; no toca frases fijadas por `doc14_contract_test`). `12 §1` no se tocó.
- Pruebas: `test/kimun/cli/nested_units_test.cljc` (5) y 2 añadidas a `test/kimun/surface/marks_test.cljc` (7 en total). Ningún test existente se ajustó.

## Qué prueba cada test
Puras: (1) tabla de estados para ítem y clave anidada: anchored; inserción antes ⇒ `:ambiguous` con candidatos `[:locator, :hash]` exactos; edición ⇒ `:mutated`; campo padre ausente ⇒ todas `:missing`, ninguna quitada, sin candidatos; el mismo valor en otro campo no es candidato. (2) ítems idénticos son dos unidades; 1:n lista los tres candidatos (índices 0, 1, 3) y `advance-marks` no elige; avance solo de las `:mutated`.
CLI: (1) marcar ítem y clave anidada: ids nuevas distintas de las unidades de campo, nada se acuña solo debajo del primer nivel (`docs marks` vacío antes), edición ajena `:anchored`, edición propia `:mutated` y tras `docs update` `:anchored` con la misma id y las `:units` intactas. (2) insertar ⇒ `:ambiguous` con dos candidatos, un `docs update` no la mueve, re-anclar sin `--reason` es `cli/usage` sin mover el head, con `--reason` queda `:anchored`, misma id, journal `unit-reanchor`; borrar ⇒ `:missing` y la id sobrevive al write; `docs unmark` ⇒ journal `unit-closure` con estado. (3) dos ítems iguales ⇒ dos ids; cambiar uno ⇒ `:ambiguous` con candidatos `[0 1 3]` (`:locator :hash :hash`) y un write no elige. (4) marcar no mueve `projection-hash`/`content-hash`/`fingerprint` de la entrada de ruta (se afirma que hay hashes no vacíos); leer no mueve el head; sobreviven a `docs reanchor`; si el campo padre deja de extraerse (campo opcional) las marcas siguen, `:missing`, sin candidatos, tras un `docs update` exitoso. (5) rechazos sin escribir (head igual): documento genérico (`--field` y `--locator` con campo) ⇒ `docs/not-anchored`; índice o ruta inexistente ⇒ `docs/anchor-unresolved`; `--index` con `--path` y `--index` sin `--field` ⇒ `cli/usage`; ítem ya marcado ⇒ `docs/exists`; unidad desconocida ⇒ `docs/missing`; traslado de una marca de ítem a otro documento tipado con la misma id y journal `unit-move`; y un documento de clase spec 16 con campo `:list` marca ítems igual (anchored, y `ambiguous` al insertar).

## Números
- Dirigidas (`directed.txt`): 76 tests, 1086 aserciones, 0 fallos (nested_units, marks surface y cli, unit_close_move, field_units, reanchor, identity_carry, typed_docs, docs, spec16_docs, anchors, doc14_contract). `generic_docs` y `tour` no se pidieron y no se corrieron.
- `bb lint`: 112 ficheros ok. `bb oracle`: 9 filas, 0 diferencias.
- Mutación (`mutation.txt`, `src/` revertido sin `git stash`, restaurado idéntico con `diff -r`): las 5 CLI fallan (46 fallos, 3 errores); las 2 puras no cargan (`anchors/field-anchors` no existe). Con el cambio, 0 fallos.

## Interpretaciones (citas)
- Por declaración y no acuñar solo: `14 §20` («los anclajes más finos… se declaran por instrucción», l. 531 del plan) y regla 5 de `§21`; lectura del equipo, no del dueño. K1c sigue acuñando la unidad del campo de primer nivel.
- Ausencia (regla «la ausencia no la termina», `§21`): si el campo padre deja de extraerse, **todas** sus marcas pasan a `:missing` conservando id. Cerrar la unidad del campo padre (`docs unmark ID` de K1f) **no** cierra las marcas de sus ítems: cada marca tiene su propia id y su cierre es declarado. Esto es lo que se sigue de la regla; si el dueño quiere que sigan al cierre del campo, es otra decisión.
- Candidatos solo dentro del mismo campo (el mismo valor en otro campo es otra cosa).
- Localizador de ítem = posición (`{:field :index}`); de clave anidada = ruta de claves (`{:field :path}`). Clave anidada incluye cada ruta de claves de mapas (no atraviesa listas); ítem es cada elemento de un valor lista (sin ítems dentro de ítems).
- `--path a.b` separa por `.`: una clave con punto se da con `--locator`.
- Una marca de ítem en un documento tipado cuyo payload deja de extraerse (error de extracción) se lee `:missing`, y una escritura normal no la toca.

## Límites
Sin linaje ni renombrado (Q2/Q3), sin sensor (`stores sense|health` no informa estados de marcas), sin ítems dentro de ítems, sin documentos genéricos (rechazo declarado), el traslado de una marca entre documentos exige que el campo/índice resuelva en el destino. `12 §1` sin tocar. K1 no está cerrado.

## Línea propuesta para la fila K1 de `12 §1`
K1h (2026-11-15): claves anidadas de campos estructurados e ítems de campos lista de documentos tipados se marcan por declaración (`docs mark NAME --field F --index N | --path a.b`), con los estados, candidatos, re-anclaje, cierre y traslado de K1e/K1f; nada se acuña solo debajo del primer nivel; pendientes: rename, linaje, copiar/dividir/fusionar, sensor, modelos como unidades. K1 no está cerrado.
