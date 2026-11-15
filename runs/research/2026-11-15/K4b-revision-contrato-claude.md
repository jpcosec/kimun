# K4b — revisión del contrato antes de habilitar la implementación

Fecha: 2026-11-15. Autor: Claude. Revisa `K4b-cache-pack-propuesta.md` (Pi). Sin código.

## Veredicto

**No habilitar K4b todavía.** La separación store/caché de la propuesta es correcta, pero se
apoya en dos garantías de la caché que hoy no existen. Empaquetar la caché sin ellas multiplica
un defecto que ya está. Hace falta un corte previo, pequeño, de **caché verificable**.

## Lo que probé (caché suelta, binario de `main` en `d2dae59d`, proyecto temporal)

Un documento genérico `a.md` ingerido; su archivo no se toca en ningún momento.

1. **Objeto de caché alterado, mismo nombre.** Cambié el texto dentro de un objeto de
   `cache/objects/<id>` (EDN válido, otro contenido):
   - `docs render a` devuelve el texto **alterado**; `docs show a --ast` también.
   - `stores sense`: `valid: true`, sin señales. `stores verify`: ok. `stores health`: ok.
   - Nada lo detecta: en un acierto de caché, `kimun.surface.docs/doc-tree` carga el árbol y
     responde `:stale? false` sin rehashear nada (`src/kimun/surface/docs.cljc`, `doc-tree`:
     `ok?` solo comprueba que haya nodo raíz). La raíz registrada se usa como **nombre** del
     objeto raíz, no como comprobación de su contenido.
2. **Objeto de caché ausente** (borré ese mismo fichero): `docs render a` falla con
   `store->ast: missing node`. No reconstruye desde el archivo: la reconstrucción solo ocurre
   cuando falta el objeto **raíz** del árbol.

Consecuencias para la propuesta:

- «No se rehashea por lectura; el control real es la raíz registrada» (regla 2): falso hoy.
- «Un fallo devuelve nil y el consumidor reconstruye» (regla 3): solo si falta la raíz. Un pack
  de caché dañado que deje de responder por **algunos** objetos produciría `missing node`, no
  caché fría.
- Caso pedido, **bytes corruptos con `.idx` válido**: el pie + digest de K4 garantiza que el
  índice es el que derivó quien escribió el pack, no que los registros sigan intactos. Un
  registro alterado después se serviría igual que el fichero suelto alterado de la prueba 1.

## Condiciones para habilitar K4b

Corte previo (independiente del pack, vale también para la caché suelta):

- **C1. La lectura de caché se verifica.** Un objeto leído de `cache/` (suelto o empaquetado)
  cuyo contenido no hashea a su id es un fallo de caché, no un objeto. Coste: un SHA-256 nativo
  por lectura de caché; medirlo en `docs show`/`docs render`.
- **C2. Un fallo parcial es caché fría.** Si falta o no verifica **cualquier** objeto del árbol
  de un documento con archivo, `doc-tree` reconstruye desde el archivo (y repuebla solo si la
  raíz recomputada coincide con la registrada, como hoy).
- Pruebas: las dos de arriba, invertidas (el objeto alterado ya no se sirve; el ausente
  reconstruye).

Dentro de K4b:

- **C3. Un lector no escribe.** Nada de renombrar a `.bad-<marca>` desde la lectura: `stores
  sense` y `stores heartbeat` prometen no escribir. El lector ignora el pack roto en memoria y lo
  informa; lo retira el comando explícito.
- **C4. Dueño del índice.** `kimun.host.store-cache` no tiene estado y la superficie lo llama
  con el directorio. El índice del pack de caché vive en el backend y la superficie pasa por él;
  no un memo global.

## Decisiones (criterio del concilio; el contrato vigente basta)

Las cuatro «preguntas para el dueño» son decisiones de diseño rutinarias, no de dueño:

| pregunta | decisión | por qué |
|---|---|---|
| verbo | `stores pack --cache`; `:scope` en la respuesta | un solo verbo, ámbito explícito |
| directorio | `cache/packs/` | `rm -rf cache/` sigue borrando todo (`14 §19`) |
| pack dañado | warning `cache/corrupt-pack` en el comando y en `stores check`; nunca exit 5 | es caché: no es corrupción del store |
| umbral | 4096 por defecto, confirmado con el histograma del bench | misma razón que K4 (tamaño de bloque) |

Además: **sin `unpack --cache`** (la caché se recupera borrándola) y medir en `docs show` /
`docs render`, no en `docs list` (en K4, `docs list` leyó 4.198 de 4.202 objetos del pack de
store: casi no toca caché).

## Correcciones a la propuesta

Anotadas al inicio de `K4b-cache-pack-propuesta.md`: el objetivo de 150 MB (es del store real;
el bench ya queda en 145,6 MB con K4 y el real no está medido), el coste por lectura (≈ 29 µs,
no ≈ 4) y las dos garantías de la caché que no existen.

## Lo que no revisé

- No medí el coste de C1.
- No busqué otros consumidores de la caché además de `doc-tree` que dependan de un acierto
  parcial.
- La prueba es con un documento genérico; no la repetí con uno tipado.
