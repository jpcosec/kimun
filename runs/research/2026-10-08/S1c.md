# S1c — Store estable sin contenido: registro por documento + caché por hash (14 §18–§19)

Lee primero `/home/jp/.cache/coord-briefs/close-common.md` (regla de memoria: todo `bb` con `memcap`).
Worktree `/home/jp/proyectos/kimun-worktrees/S1c`, rama `close/S1c` desde `main`. Construye el pod con
`memcap 4 bb pod`. Autoridad: `docs/v2/14-juego-del-lenguaje.md` **§18 y §19** (léelos enteros: son el
diseño), `10-escala.md §10` (formato 3; dueño del formato en disco: actualízalo), `S1-report.md` y
`S1b-report.md` en `~/.cache/coord-briefs/` (lo ya hecho).

## Diagnóstico medido (store de este repo, 539 docs, 8,07 MB)

El plan resuelto del build pesa 4 MB: 12.841 ops `:ownership` (2,2 MB = las instrucciones para armar
cada árbol), 9.932 `:add-node`, y aristas cuya evidencia lleva `:field-hashes`/`:content-hash` (~1 MB
entre plan y objetos). Además 5.583 tree objects (1,8 MB) y nodos de bloques/texto. TODO eso se
recalcula del archivo: es parse + extracción, no algo que alguien escribió.

## Qué construir

1. **Registro por documento (store estable).** Un documento trackeado con archivo se registra como:
   anchor externo (ruta + fingerprint, ya existe), **clase** de la que es instancia (id del descriptor de
   modelo; documentos sin modelo: un id de "clase genérica" del perfil de su tipo de archivo, p. ej.
   `generic/markdown`), y **hash raíz** de su estructura indexada (merkle-root del árbol, sin persistir
   el árbol). Más **un** hash de contenido extraído (`:content-hash`, para distinguir reformateo de
   edición) en el registro, no en las aristas. Elimina `:field-hashes` de la evidencia de aristas; si
   algún lector los necesita, que los obtenga del caché.
2. **Nada de árbol en el store para documentos con archivo.** El plan de track/build ya no emite
   `:ownership` ni `:add-node` de bloques/texto ni tree objects. Excepción (14 §10, regla de spans):
   nodos y posiciones **referenciados por alguna arista** desde fuera (links, `edges`, spans, hechos)
   se persisten. Documentos sin archivo (`docs create` sin ruta) o `:inline` (S1): conservan su árbol
   como hoy.
3. **Caché por hash** en `<store>/cache/` (gitignored, borrable): clave = `(fingerprint, clase,
   versión del parser)` → `{árbol, nodos, payload extraído, secciones}`. Lectura en anillo 1 (host);
   lo inyecta `kimun.cli.store/open!` como hace S1 con `:read-file`. Un único resolver en superficie
   (`kimun.surface.docs/doc-tree` o similar) devuelve el árbol/payload de un documento: del store si
   está persistido, si no del caché, y si no recomputando del archivo (y escribiendo el caché).
   Verifica `merkle-root == hash raíz registrado`; si no coincide, el documento está **desactualizado**
   (ver 4). Todos los lectores (`docs show|render|list`, `fields`, `sections`, índice/`find`, `edges`,
   `links`, `diagnostics`, `journal`, HTTP) pasan por el resolver.
4. **Detección y reconciliación.** `fingerprint(archivo) ≠ registrado` ⇒ `:stale` (cambió el ambiente);
   archivo ausente ⇒ `:missing`; clase cambiada (descriptor sucedido, 14 §18.3) ⇒ el documento sigue
   siendo instancia de su clase original (su descriptor sigue en el CAS) y se lee bajo ella; `stores
   check|health|status` lo reportan con la acción (`docs update NAME`). `docs update` / `stores update`
   reconcilian (re-registran fingerprint, clase, hash raíz). Ningún cambio silencioso.
5. **`include build --rebuild`** produce el store nuevo; `stores upgrade` 3→4 (o la versión que
   corresponda) = reconstruir. Sube `:format-version` y documéntalo en `10`.
6. **Medir**: `bb store-size` antes (main) y después, tabla por clase. El caché se mide aparte.

## Límites
- No muevas código a un paquete `kimun.graph.*` (eso es el paso 3, otra tarea).
- No toques la capa G ni `docs/v2/15`.
- Si un test de paridad v1 depende de que el árbol esté en el store, adapta el test al resolver, no al
  revés; si una promesa de v1 deja de poder cumplirse, NO la debilites: repórtala.
- Batería obligatoria al final: `bb lint`, `bb test`, `bb oracle`, `bin/kimun include check`, marcador
  `memcap python3 scripts/parity/coverage.py /home/jp/proyectos/hum-ecosystem/tools/sldb . --strict --allow-open-prefix selfdoc/`
  exit 0, `include build --rebuild` + `bb store-size`. Prueba también: borrar `<store>/cache/` y que
  todo siga funcionando (se recalcula).
- Commits por paso, trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Sin push.

## Reporte
`/home/jp/.cache/coord-briefs/S1c-report.md`: qué cambió por paso, tabla de tamaño antes/después
(store y caché por separado), batería, qué ids cambiaron, y desviaciones de 14 §18–§19 con su porqué.
