# M1j — C6: capturas con coordenadas (REPORT)

## Qué se hizo
- Nuevo `src/kimun/surface/models/capture.cljc`: `capture-selector steps text [mode]` devuelve
  `{:aligned? bool :captures {campo {:value :path :spans :leaf-hashes}}}`. `aligned-spans` es publica.
- `selector.cljc`: el motor de bloques lleva ahora un `:src` (rango de lineas por campo) en su estado;
  nueva `extract-located` (`{campo {:value :lines}}`). `extract-selector` devuelve lo mismo que antes
  (`(:out …)`); la validacion de modo se extrajo a `check-mode!`.
- `selector_cst.cljc`: `capture` devuelve `[valor [ini fin)]`; nueva `extract-located`; `extract` delega
  en ella (`update-vals … :value`). Salida de `extract` sin cambio.
- Test nuevo `test/kimun/surface/models/capture_test.cljc` (6 pruebas, 22 aserciones).
- No toque `docs.cljc`, `cli/`, `subgraph.cljc`, `anchors.cljc`, `extract.cljc`, `store.cljc`, kernel, `docs/v2/12|16`.

## Forma de una captura (vocabulario S2a, sin identidad)
- `:path`: localizadores de bloque (`{:section :type :n}`) de las hojas de las que se leyo el valor.
- `:spans`: un `{:lines [ini fin) :bytes [ini fin)}` por hoja, paralelo a `:path`.
- `:leaf-hashes`: el `:hash` del bloque en `anchors` por hoja, paralelo a `:path`.
- Hojas = bloques de nivel superior (anclas S2a) que solapan el rango de lineas de origen del valor.
- Sin `:id` ni nada acuñado. Anillos: `kimun.surface.models.*` cae en el anillo `kimun.`, que SI admite
  `kimun.surface.markdown.*` y `kimun.host.*`; no se relajo nada (`bb lint` ok).

## Ambiguedad del spec (no resuelta por mi, reportada)
- `docs/v2/16` L132 (§3.3, fila C6): `{:value … :path … :spans […] :leaf-hashes […]}`.
- `docs/v2/16` L256 (§6, punto 1): `{:value :path :spans :content-hash}`.
  Implemente `:leaf-hashes` (la definicion de la capacidad, y la unica con plural por hoja que las pruebas
  del plan piden). Si el dueño quiere `:content-hash`, es un solo digest (p. ej. de las hojas) y es un
  cambio de una linea en `capture.cljc`. No emiti ambos.
- `:path` tampoco esta definido: el spec no dice si es ruta de secciones o de bloques; tome los
  localizadores de bloque de S2a (el de seccion va dentro de cada uno en `:section`).

## Limites
- Sin rangos cuando la capa de bloques no casa 1:1 con el AST (S2a; p. ej. cita con continuacion
  perezosa): `:aligned?` false y la captura lleva solo `:value`. Idem un campo cuyo origen esta vacio
  (seccion vacia): solo `:value`.
- Los campos de `:yaml` salen del frontmatter completo (una hoja); varios campos comparten hoja.
- Las coordenadas de pasos CST (C3-C5: seccion/lista/tabla) se calculan (rango de filas del nodo) pero
  NO tienen prueba directa en este corte (las 6 pruebas usan pasos de bloques, sin pod); `selector_cst_test`
  sigue verde y cubre que los valores no cambian. Es un hueco declarado.
- La comprobacion de alineacion (`aligned-spans`) duplica la privada de `cli/commands/docs.cljc`
  (no se puede requerir desde `surface` y no se toco `docs.cljc`). Conviene unificarla en un corte posterior.
- `extract.cljc` sin cambios (no hizo falta aridad).

## Pruebas (capture_test)
1. edicion fuera de la captura (titulo `# T`→`# U`) no cambia `:leaf-hashes` (control: edicion dentro si).
2. edicion dentro cambia solo la hoja tocada; `:path` igual; otro campo del documento intacto.
3. `:spans` cortan el texto y reproducen `:value` (+ documento desalineado: `:aligned?` false, sin rangos).
4. ninguna clave `:id`/`:node-id`/`:identity` en la salida; valores = `extract-selector`.
5. las hojas se mueven exactamente como los bloques de `anchors/anchors` ante la misma edicion; cada hash
   es el de S2a en su localizador.
6. formato estable (golden: path, lines, bytes y hashes de un documento fijo).
Todas verifican contra el valor esperado, ninguna compara vacios.

## Evidencia
- `lint.txt`: `rings and docstrings ok: 110 files`.
- `directed.txt`: capture, selector, selector-cst, parity (2384 pass / 0 fail), anchors: 26 tests,
  1311 aserciones, 0 fallos; corpus, typed-docs, golden: 22 tests, 213 aserciones, 0 fallos.
  (el pod estaba disponible, pruebas CST corridas, no saltadas.)
- `oracle.txt`: 9 rows, 0 mismatches.
- `mutation.txt`: sin `capture.cljc`/`src` la suite no carga; con `:leaf-hashes` degradado a un hash
  repetido fallan 4 aserciones en 3 pruebas (1, 2, 5). No se mutaron expresamente las pruebas 3, 4 y 6.
- No se corrio `bb test` completo (lo hace el coordinador). No se commiteo.

## Linea propuesta para la fila M1 de `12 §1`
M1j (implementado): `capture/capture-selector` (`models/capture.cljc`) entrega `{:value :path :spans
:leaf-hashes}` por campo con localizadores y huellas de S2a, sin identidad; sin rangos si la capa de
bloques no casa 1:1 con el AST; abierto: `:leaf-hashes` (§3.3) frente a `:content-hash` (§6), y
pruebas directas de coordenadas CST.
