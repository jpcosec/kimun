# K1c — unidades de campo: identidad debajo del documento

Base `6a1c7ba0`. Diseño y revisión: Claude. Implementación: Pi (brief y adendas en `brief.md`).
Sin cambios en el kernel.

## Qué cambia

- Cada campo de primer nivel que el modelo de un documento tipado selecciona es una unidad con
  id propio (ULID), acuñado la primera vez que el campo se registra y guardado en el
  descriptor del árbol (`:units {campo id}`): store, no caché.
- El id se conserva al editar, al re-anclar (`docs reanchor`) y en `include build --rebuild`
  (`:identities :units-carried`). Un campo que deja de extraerse conserva su id y pasa a
  `:missing`; si vuelve, es la misma unidad.
- `docs show NAME` trae `:units [{:field :id :status}]`.
- Un documento anterior a este corte recibe sus unidades en su siguiente escritura.

Las decisiones (qué es una unidad de campo, su anclaje, la ausencia, el renombrado) son
inferencias del equipo a partir de `14 §20–§21`, no palabras del dueño.

## Validación (árbol final, turno exclusivo, memcap, con pod)

| paso | archivo | resultado |
|---|---|---|
| `bb lint` | `lint.txt` | ok |
| dirigidas (5 namespaces) | `directed.txt` | 41 tests, 594 aserciones, 0 fallos |
| mutación (`git stash push -- src/`) | `mutation.txt` | `field_units_test`: 12 fallos en 7 pruebas |
| `bb test` | `full-test.txt`, `full-exit.txt` | 1137 tests, 12590 passes, 0 fallos, 0 errores, 137 namespaces, pod disponible |
| `bb oracle` | `oracle.txt` | 9 filas, 0 diferencias |
| `bin/kimun include check` | `include-check.txt` | ok |

## Invariante de huellas (corregida durante la revisión)

`:units` no añade identidad a ninguna huella. La raíz Merkle del árbol ya difería entre dos
stores con el mismo archivo antes de este corte (lleva el id de documento); eso no se tocó.
La prueba comprueba: mismo AST con `:units` distintos o sin ellas ⇒ misma raíz Merkle, mismo
nodo raíz, mismos objetos y nodos; dos stores ⇒ misma huella de archivo, hash de proyección y
hash de contenido en la entrada de ruta (una entrada, hashes no vacíos).

## Ajustes de revisión (Claude)

- Un documento `:inline` (sin descriptor de registro) calculaba unidades que no se guardan y
  por eso nunca habría salido `:unchanged?`: ahora solo se calculan para documentos por
  referencia. Prueba añadida.
- Fila `S2` de `12 §1` y `14 §13`: `S2a` → `K1e` → `S2b`, para que no haya ciclo con `K1`.

## Tests existentes ajustados

- `identity_carry_test`: `:identities` lleva `:units-carried`.
- `routes_test`: la llamada directa a la superficie pasa `:mint`, como hace la CLI.

## Límites declarados

- Sin unidades: documentos genéricos, `:inline` o de formato < 4, campos anidados, ítems de lista.
- No hay cierre ni linaje declarables para una unidad de campo: las `:missing` se acumulan, y
  renombrar un campo da una `:missing` más una nueva.
- Secciones y bloques esperan `S2a` → `K1e`. K1 **no** está cerrado.
- No hay medición del coste de un descriptor con muchas unidades.
