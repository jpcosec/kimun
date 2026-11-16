# K1e — unidades de sección y bloque marcadas por declaración

## Dónde quedó cada cosa

- `src/kimun/surface/markdown/anchors.cljc` (puro, junto a S2a): `mark-states` y
  `advance-marks`. Es el único sitio con la tabla de estados y la regla de escritura.
- `src/kimun/surface/markdown/plan.cljc`: `ast->tree` acepta `:marks`.
- `src/kimun/surface/docs.cljc`: `registry`/`generic-ops`/`track-plan`/`update-plan`/
  `generic-update-plan` pasan `:marks`; `describe` y `describe-generic` lo exponen;
  `marks-of` y `mark-plan` (marca y cierre, reutilizando los planes de update con `:marks` y
  `:declared`); `base-same?` cuenta un cambio solo de `:marks` como movimiento.
- `src/kimun/cli/commands/docs.cljc`: `docs mark`, `docs unmark`, `docs marks`; `--locator`
  (EDN o JSON, claves a keyword), `--unit`, `--label`; `docs show` añade `:marks`.
- `src/kimun/cli/commands/include.cljc`: arrastre de `:marks` y `:identities :marks-carried`.

## Pruebas

Puras (3, 38 aserciones): tabla de estados con las cinco filas; 1:n con **cuatro** candidatos
(1 `:locator` + 3 `:hash`) y `advance-marks` que no la toca; escritura normal que solo avanza
la huella de la `mutated`, sin cambiar ids, localizadores ni quitar marcas.

CLI (5, 80 aserciones): marcar sección y párrafo y editar; insertar desplaza y no se infiere
(`:ambiguous` con 2 candidatos, `--unit` declara y el journal trae `:declared`); borrar no
termina (`:missing`, la id sigue) y `unmark` cierra con `unit-closure`; las marcas sobreviven a
`docs reanchor` y a `include build --rebuild` (`:marks-carried 1`); `:marks` no entran en el
Merkle, marcar no mueve las huellas de ruta y `docs marks` no escribe (sha256 y head iguales).

## Números

- Dirigidas: 66 tests / 911 aserciones, 0 fallos (incluye `anchors_test`, `docs_anchors_test`,
  `field_units`, `reanchor`, `identity_carry`, `generic_docs`, `typed_docs`, `docs`, `tour`,
  `doc14_contract`).
- `bb oracle`: 9 filas, 0 diferencias. `bb lint`: 107 ficheros.
- Mutación: con `src/` en stash las ocho no cargan (`anchors/mark-states` inexistente).

## Ajuste de un test existente

`identity_carry_test`: su mapa exacto de `:identities` gana `:marks-carried 0`.

## Límites y notas de contrato

- Una sección con el título cambiado queda `:missing` sin candidatos (su huella incluye el
  encabezado): se re-ancla a mano.
- `docs marks` no expone `:hash` (la forma del brief no lo lleva); el hash vive en el
  descriptor. Las pruebas lo comprueban por comportamiento (una segunda edición vuelve a dar
  `:mutated`), no leyendo el campo.
- El contrato de lectura en vigor se informa con `:parse-contract`; si cambió (K6), una marca
  puede verse `:mutated` sin que el texto cambie, y eso no se resuelve aquí.
- Fuera de alcance según el brief: mover entre documentos, copiar/dividir/fusionar con linaje,
  marcas dentro de un bloque (`S2b`), `:inline` y formato < 4, código, y estados de marcas en
  el sensor.
- No se encontró contradicción entre `14 §20–§21` y la tabla del brief.

## Revisión e integración (coordinador)

- Rebasado sobre `d4eb21f8` sin conflictos; lo validado es el árbol rebasado, con pod:
  `bb lint` ok; dirigidas 75 tests, 945 aserciones, 0 fallos; `bb test` 1220 tests, 14372
  passes, 0 fallos, 0 errores, 151 namespaces; `bb oracle` 9 filas, 0 diferencias;
  `include check` ok (`full-test.txt`, `full-exit.txt`, `oracle.txt`, `include-check.txt`).
- Ajuste de revisión: una marca `:anchored` cuyo contenido tiene un gemelo idéntico listaba
  al gemelo como candidato. Una marca anclada no propone nada. Prueba añadida
  (`an-anchored-mark-has-no-candidates-even-with-a-twin`).
- Después de la corrida completa se escaparon `|` que partían la fila `K1` de `12 §1` y la
  fila `stores health` de `06` (esta última, defecto previo); sobre ese árbol corrieron las
  pruebas documentales y `marks_test` (`directed-final.txt`).

## Límites declarados

- El sensor (`stores sense|health`) no informa estados de marcas: solo `docs marks`.
- No se puede mover una unidad a otro documento, ni copiar, dividir o fusionar con linaje.
- Cambiar el título de una sección marcada la deja `:missing` sin candidatos.
- Con el contrato de lectura cambiado (K6) una marca puede verse `:mutated` sin que el texto
  cambie; `docs marks` lleva `:parse-contract` para que se vea.
- Sin marcas dentro de un bloque (`S2b`), en documentos `:inline` ni en código.
- K1 no está cerrado.
