# K4b — packs de `cache/objects` (propuesta de diseño, sin implementar)

Fecha: 2026-11-15. Autor: Pi (worker). Estado: **propuesta**, solo lectura; no hay
código, tests ni commit. Depende de que K4 (`stores pack|unpack` sobre
`objects/`) esté integrado. Autoridad: `docs/v2/14 §19` (caché vs store
estable), `14 §18.6` (clave `(fingerprint, clase, parser)`), `10 §11`
(formato 4), `10 §11.4` (packs de K4), `10 §9` (objetivo de bloques).

> **Correcciones de la revisión (Claude, 2026-11-15; ver `K4b-revision-contrato-claude.md`).**
> El texto de abajo se deja como lo escribió Pi; estas tres afirmaciones suyas no se sostienen:
>
> 1. «Sin tocar `cache/` el objetivo de 150 MB en bloques de `10 §9` no se alcanza». El objetivo
>    de `10 §9` es del store **real** de 1.551 documentos (364 MB en bloques). El store de bench
>    de 600 documentos queda en **145,6 MB** ya con K4, por debajo de 150. Si el store real
>    cumple el objetivo con K4 solo **no está medido**. Lo medido es que `cache/` es el 36 % de
>    los bloques del store de bench empaquetado.
> 2. «≈ 4 µs por lectura empaquetada». Eso es solo abrir, leer y cerrar el canal. La lectura
>    empaquetada medida en K4 cuesta **≈ 29 µs** más que la suelta (`docs list` ×1,16).
> 3. «El control real es la raíz registrada» y «un fallo devuelve nil y el consumidor
>    reconstruye». Hoy ninguna de las dos es cierta para la caché suelta (probado; ver la
>    revisión): un objeto de caché alterado se sirve, y uno ausente da error en vez de
>    reconstruir.

## Problema

En el store de bench de 600 documentos el 28,8 % de los bloques se recupera
empaquetando `objects/`, pero `cache/` —12.344 ficheros, 5,7 MB aparentes,
**52,8 MB en bloques**— queda intacto y es el **36 % de los bloques del store
empaquetado**. Sin tocar `cache/` el objetivo de 150 MB en bloques de `10 §9` no
se alcanza. La pregunta es si `cache/objects` admite el mismo empaquetado **sin
convertir caché derivada en store estable**.

## Contrato propuesto: el pack es caché, no store

Dos ámbitos distintos, con el mismo formato de pack y políticas distintas:

| | `objects/` + `packs/` (K4) | `cache/objects` + `cache/packs` (K4b) |
|---|---|---|
| naturaleza | **store estable**: única copia | **derivado**: recalcular desde archivo + clase + parser |
| `verify` / `verify-full` | rehashea y exige `H == id` | **no entra**: no se verifica como store |
| `read-stored-object` (lo que rehashea `verify`) | lee suelto y pack | **nunca**: la caché no puede presentarse como store |
| `object-ids` (Backend) | sueltos + pack | **no**: `object-ids` sigue siendo solo store |
| corrupción | `:store/corrupt-pack`; recuperación `stores unpack` | **descartar y reconstruir**: se borra/renombra el pack roto y se recalcula del archivo; nunca falla el comando |
| borrado | nunca por caché | `rm -rf cache/` sigue siendo válido y total (packs incluidos) |
| fuente de verdad | el propio objeto | el archivo + `cache/docs/<key>.edn` + raíz registrada |

Reglas concretas de lectura:

1. `cache/read-object` y el fallback del backend consultan, en orden: suelto en
   `cache/objects/<id>` → pack de caché → nil.
2. Un objeto leído de un pack de caché **no** se rehashea por lectura (el pack
   es contenido direccionado por hash; el control real es la raíz registrada).
3. Un fallo al leer el pack de caché (magic, longitud, pie, `:store/corrupt-pack`
   del lector) devuelve nil y **no** propaga error: el consumidor existente
   (`kimun.surface.docs/doc-tree`) reconstruye y repuebla. El pack roto se
   renombra a `.bad-<stamp>` (o se borra) y se ignora hasta el siguiente
   `pack-cache`.
4. `doc-tree` sigue comprobando `merkle-root == root-hash registrado` y sigue sin
   escribir caché si el archivo está desactualizado o hay `:rebuild-mismatch`
   (K6): el pack no cambia esa decisión, solo el medio donde vive el objeto.

## Invalidación / recálculo

- El pack **no** se invalida por contenido: es content-addressed, igual que la
  caché suelta.
- Se invalida al subir `store-cache/parser-version` (ya invalida entradas) o al
  borrar `cache/`. Las entradas huérfanas que queden tras un cambio de parser son
  inertes: nadie las pide por id; no hacen falta GC ni reempaquetado en el
  primer corte.
- `cache/docs/<key>.edn` (fingerprint, clase, parser, tree, root-hash) no se
  empaqueta: son pocos ficheros, pequeños y se reescriben por documento.

## Superficies a tocar (mínimo)

1. `src/kimun/host/fs_store.cljc`: reutilizar `scan-pack`, pie + digest y
   búsqueda binaria sobre un **directorio de pack distinto** (`cache/packs`),
   con el lector tolerante descrito; un `read-cached-object` y un
   `write-cached-object!` que consulte el índice antes de escribir.
2. `src/kimun/host/store_cache.cljc`: `read-object`/`object-ids` con fallback al
   pack de caché; `.idx` derivado con la misma forma (44 B por entrada) y
   reconstruible.
3. CLI: `stores pack [--cache]` / `stores unpack [--cache]` (o
   `docs cache pack|unpack`), ámbito explícito en `:data` (`:scope :store|:cache`),
   solo formato 4.
4. `docs/v2/10 §11.4` (segundo ámbito, corrupción y coste) y `14 §19` (caché
   empaquetada sigue siendo borrable).
5. `include gitignore`: `cache/` ya está ignorado entero; el pack no añade reglas.

## Medición esperada

- Bloques: hasta **−52,8 MB** en el store de 600 docs (36 % del store
  empaquetado), lo que deja el objetivo de `10 §9` al alcance del store completo.
- Ficheros: `cache/` pasa de 12.344 a 2–4 ficheros.
- Tiempo: `docs list` en frío puede empeorar (una lectura de pack por objeto de
  caché); en caliente no cambia (la caché de objetos en memoria responde
  primero). Medir antes/después, no asumir.

## Riesgos

- **Confundir ámbitos (el riesgo principal).** Si un lector usa el pack de caché
  para `read-stored-object`, `verify` rehashearía copias derivadas como si fueran
  store y un caché restaurado de otra rama pasaría por store. Mitigación: dos
  directorios, dos funciones, y `object-ids`/`verify` sin conocimiento del pack de
  caché (probar que `stores verify` no cambia de resultado con `cache/packs`
  presente y dañado).
- **Caché envenenada.** Un pack de caché con objetos de otro contrato de parse es
  indistinguible por hash. La protección no es el pack: es la ya existente
  (clave con `parser-version`, raíz registrada, `:stale?`). Debe quedar escrito
  que K4b no añade verificación de contrato; si hace falta, sería otro corte.
- **Corrupción silenciosa.** Un fallo del pack debe ser «caché fría», no error:
  hay que decidir dónde se informa. Propuesta: `stores check|health` no cambian
  de categoría (`:missing`/`:edited` son del archivo); el pack roto se reporta
  como warning propio (`cache/corrupt-pack`) y se descarta, sin `exit 5`.
- **Coste de lectura.** Añade una lectura de pack por objeto de caché que no esté
  suelto. Con las cifras de K4 (≈4 µs por lectura empaquetada, más ~1 µs de
  conversión de id con `HexFormat`) el efecto debería ser pequeño, pero el store
  real tiene 12.344 objetos de caché en 600 docs: medir 3 corridas de `docs list`
  y `docs show` antes/después; si supera ~1,3× el suelto, reportar el desglose
  antes de aceptar.
- **Sin GC ni reempaquetado.** Borrar `cache/` deja objetos inertes; el borrado
  recomendado es el directorio completo. No se compacta ni se reescribe.
- **Descriptor por latido.** R1 de K4 aplica también aquí: el lector de caché no
  debe cachear canales en el backend (un `open!` por latido de
  `stores heartbeat`).
- **Recuperación parcial.** Igual que en K4: `unpack --cache` necesita el armazón
  del pack (magia, longitudes, magia y cuenta del pie); un pack truncado no se
  recupera por partes, y la salida es borrar `cache/` y recalcular.

## Preguntas abiertas (para el dueño del diseño)

1. ¿Un solo verbo `stores pack --cache` o un grupo `docs cache pack|unpack`?
2. ¿`cache/packs/` dentro de `cache/` (lo más simple, borrado total) o
   `packs/cache/` junto a los de store (más visible, más fácil de confundir)?
3. ¿Se informa el pack de caché dañado como warning en `stores check|health` o
   solo en el comando de caché?
4. ¿Umbral `--max-bytes` propio para caché? Los objetos de caché son pequeños
   (nodos block/text/opaque y tree objects), pero el valor de 4096 debería
   medirse en el store real antes de fijarlo.
