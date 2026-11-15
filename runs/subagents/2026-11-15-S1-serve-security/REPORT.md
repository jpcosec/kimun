# S1-serve-security · auditoría de seguridad de `serve` (solo evidencia)

Base `ad3b2541`, worktree `/tmp/kimun-s1sec-wt`. **No se tocó `src/`** (`git status`: solo `?? test/.../security_test.cljc` y `?? runs/.../`).

## Archivos
- `test/kimun/surface/http/security_test.cljc` (nuevo, 5 pruebas, 25 aserciones).
- `runs/subagents/2026-11-15-S1-serve-security/`: `lint.txt`, `directed.txt`, `mutation.txt`, `vectors.md` (tabla completa con citas),
  `probe.clj`, `probe2.clj`, `probe-head.clj` y sus salidas (`probe-output.txt`, `probe2-output.txt`).

## Pruebas (solo lo ya especificado)
1. `unauthenticated-write-is-401-and-leaks-nothing`: POST sin token -> 401, `WWW-Authenticate: Bearer`, el cuerpo no contiene el token ni la ruta del proyecto, y el árbol de archivos (tamaño y mtime) no cambia.
2. `only-the-bearer-scheme-with-the-exact-token-passes`: `Bearer <tok>` 200; `Basic`, sin esquema, más largo, más corto, sin cabecera -> 401.
3. `a-wrong-method-is-405-with-allow`: POST a ruta GET -> 405 con `Allow: GET`; GET a `/save` -> `Allow: POST`; PUT/DELETE/PATCH -> 405.
4. `cors-preflight-exists-only-with-the-flag`: sin `--cors`, OPTIONS 404 y sin cabeceras; con `--cors`, 204, `Allow-Headers` incluye `Authorization` y hasta el 401 lleva `Allow-Origin: *`.
5. `the-default-bind-is-loopback-only`: `start!` sin host -> `:host "127.0.0.1"`, responde en loopback; si la máquina tiene una IP no-loopback, esta no debe alcanzar el servidor.

Resultado: `directed.txt` = 5 pruebas, 25 aserciones, 0 fallos. `lint.txt` = `rings and docstrings ok: 104 files`. Ninguna prueba de lo especificado falló: no hay hallazgo de «el código no cumple el spec». (Mi primer intento falló por un error mío al llamar `with-server` con la aridad equivocada y por reflexión no disponible en bb; corregido en la prueba, no en `src/`.)

Mutación (`mutation.txt`, `src/` restaurado con `git checkout`, `git status --short src` vacío): (A) `authorized?` acepta el token crudo si no hay esquema Bearer -> falla la prueba 2; (B) bind por defecto `0.0.0.0` -> falla la prueba 5 (por la aserción de `:host`).

Límites de las pruebas: la rama «IP no-loopback no alcanza» de la prueba 5 **no corre aquí**: `getLocalHost` da `127.0.1.1` (la prueba la salta con `127.`), así que en esta máquina solo discrimina la aserción de `:host`. Las 5 no se verificaron sin el cambio con `git stash` porque no hay cambio de `src/`: la discriminación se demostró por mutación. Las pruebas de `test/kimun/surface/http_test.cljc` ya cubrían parte de 401/CORS/405; estas pruebas añaden el cuerpo del 401 sin fugas, el árbol intacto, esquemas y longitudes, `Allow` y el bind real.

## Tabla de vectores (detalle, comandos y citas en `vectors.md`)

| # | Vector | Hoy | Spec | Gravedad |
|---|---|---|---|---|
| 1 | Cuerpo enorme | 413 a >8 MB (default de http-kit, no decisión); bajo el tope, `/docs/ingest` de una línea de 100/200/400 KB tarda 2,5/16/82 s con la cola de escritura bloqueada; sin timeouts | no lo dice | media sin token, baja con token |
| 2a/b | `path` absoluto o `..` en `/docs/create` | 200, archivo creado fuera del proyecto | no lo dice | alta sin token / media con token |
| 2c | `path` fuera en `/docs/track` | 200 y `/render`, `/document` devuelven el texto del archivo exterior | no lo dice | alta sin token / media con token |
| 2d | `id` con `..` en `/save` create | 200 y archivo creado fuera (el nombre se usa como ruta) | no lo dice | alta |
| 3 | `--host 0.0.0.0` sin token | banner `auth=off`, sin aviso; atiende sin auth por dirección no-loopback | no lo dice (`06:510` solo el default) | alta si se expone |
| 4 | Token vacío | `--token ""` apaga la auth, incluso con `KIMUN_SERVE_TOKEN` definido | no lo dice (solo el docstring de `resolve-token`) | media (falla abierta) |
| 5 | Authorization mal formada | todo falla cerrado (401), salvo `Bearer tok ` con espacio final (200); esquema sensible a mayúsculas | `06:510` | baja |
| 6 | Timing | `MessageDigest/isEqual` (contenido constante; la longitud se filtra); leído, no atacado | no lo dice | baja |
| 7a | CORS sin `--cors` | OPTIONS 404, sin cabeceras | docstring `handler` | OK |
| 7b | POST `text/plain` cross-origin (CSRF) | 200, el documento queda escrito | no lo dice | alta sin token, baja con token |
| 7c | `Host` arbitrario (rebinding) | 200, no se valida | no lo dice | media sin token |
| 8 | Symlink fuera del proyecto | CLI `docs ingest` lo sigue; HTTP `docs/track` lo sigue y se puede leer (HTTP `ingest` no lee rutas) | `06:316` sin confinamiento | CLI baja / HTTP como 2c |

## Ambigüedades y observaciones
- El spec de `serve` es una línea (`06:508-510`); 401, 405, CORS y el orden de las comprobaciones solo están en el docstring de `handler` en `http.cljc`. Las pruebas fijan ese docstring, no un texto de `06`.
- `06:278` y `06:316` permiten rutas fuera del proyecto en CLI. Si la superficie HTTP debe heredar esa libertad es justo Q8 (`frentes-plan.md`): no lo decido.
- No verifiqué si `docs create --path` sobrescribe un archivo existente (el comando documenta un rechazo; no lo probé).
- «Bearer tok » con espacio final da 200; no distinguí si lo recorta el cliente de JDK o http-kit.
- Las sondas de bind a `0.0.0.0` se hicieron en `127.0.0.1`/`127.0.1.1`; nada salió de la máquina. Ningún servidor quedó corriendo (verificado con `ps`). Todo exterior vivió en `/tmp`.

## Preguntas para el dueño
1. (Q8) ¿Las rutas de escritura y de track por HTTP deben confinarse al proyecto (rechazar `path` absoluto, `..`, symlinks que salen y `id` con separadores en `/save`), manteniendo la CLI como está? Es el hallazgo de mayor peso (2a-2d, 8).
2. ¿`--host` distinto de loopback sin token debe negarse a arrancar (o exigir `--insecure`)? Y `--token ""`: ¿error de uso o auth apagada como hoy?
3. Sin token en loopback: ¿se acepta el riesgo CSRF/rebinding (7b/7c)? Opciones: exigir `Content-Type: application/json` y validar `Host`/`Origin` en las rutas de escritura, o hacer el token obligatorio por defecto.
4. ¿Se quiere un tope de cuerpo y un timeout propios (hoy 8 MB de la librería) y/o un límite de costo del parser en `/docs/ingest`?
