# serve-seguro · `kimun serve` seguro por defecto

Worktree `/tmp/kimun-serve-wt` (sobre `main`, sin commit). Evidencia en este directorio: `vectors-after.md`, `lint.txt`, `directed.txt`, `joint.txt`, `mutation.txt`, `oracle.txt`.

## Archivos
- Nuevo `src/kimun/surface/http/guard.cljc`: el único punto de validación (arranque, rutas confinadas, `Host`, `Origin`, `Content-Type`).
- `src/kimun/surface/http.cljc`: el handler llama a `guard`; `start!` valida la config antes de abrir el socket; `resolve-token` y `constant-time=`.
- `src/kimun/surface/http/routes.cljc`: `:paths [:path]` en `/docs`, `/docs/create`, `/docs/track` (la ruta declara qué parámetros son archivos).
- `src/kimun/cli/commands/serve.cljc` y `src/kimun/cli/main.cljc` (solo la línea de `serve`): ayuda.
- Docs: `docs/v2/06-superficies-y-cli.md` (solo `### serve`), `README.md` (una frase), `docs/v2/drafts/preguntas-abiertas.md` (la fila `SERVE` pasa a cerradas).
- Pruebas: nuevo `test/kimun/surface/http/serve_safe_test.cljc` (14 pruebas, 133 aserciones); `test/kimun/surface/http/support.cljc` (+`req*`, peticion con cabeceras); ajustes en `test/kimun/surface/http_test.cljc`.

## Contrato nuevo
- **Arranque** (`guard/check-config!`, antes de abrir socket): `--host` no loopback (`127.0.0.0/8`, `::1`, `localhost`) sin token, `--token ""`/`KIMUN_SERVE_TOKEN=` vacío, o `--cors` sin token: `cli/usage` con el arreglo en el mensaje. Sin `--insecure`. Token en tiempo constante (se comparan los SHA-256: tampoco filtra la longitud).
- **Rutas confinadas** (solo HTTP): raíz = real path del cwd del servidor. Parámetros declarados `:paths` se rechazan si son absolutos (`/`, `\`, `~`, `C:`), traen `..` o NUL, nombran un segmento `.git`/`.kimun`, o —siguiendo enlaces simbólicos, también rotos— salen de la raíz. En rutas `:write?`, `name`/`id`/`doc`/`model` (a cualquier profundidad del cuerpo) no admiten `/`, `\`, `.` ni `..`. Rechazo: 400 `serve/path-outside-project` (exit 6, el código de los errores de uso); nada se lee ni escribe.
- **Sin token (solo loopback)**: `Host` debe ser loopback con el puerto del servidor, si no 403 `serve/host-not-allowed`; `Origin` ajeno 403 `serve/origin-not-allowed` en toda ruta salvo `/health`; todo `POST` con cuerpo exige `application/json`, si no 415 `serve/unsupported-media-type`.
- **Decisión del coordinador, punto 3, y cómo la apliqué con token**: con token no se mira `Host` (hay proxies/nombre público y el atacante no tiene el token); `Origin` ajeno sigue rechazado salvo con `--cors` (única relajación); el `Content-Type` JSON se exige siempre. `/health` queda exento solo del `Origin`.
- Orden: OPTIONS, Host, Origin, 401, 405/404, 415, 400.

## Vectores (detalle y pruebas en `vectors-after.md`)
| # | Estado |
|---|---|
| 1 cuerpo enorme / costo del parser | **abierto** (punto 4 de `SERVE`, sin decidir; no hay tope barato sin cambiar contrato) |
| 2a, 2b, 2c, 2d | cerrados (confinamiento) |
| 3 `--host 0.0.0.0` sin token | cerrado (no arranca) |
| 4 token vacío | cerrado (`cli/usage`) |
| 5 Authorization mal formada | ya cerrado (falla cerrado); quedan sin riesgo el esquema sensible a mayúsculas y `Bearer tok ` con espacio |
| 6 timing | cerrado (digests; la longitud ya no se filtra) |
| 7a CORS sin flag | ya cerrado; ahora `--cors` exige token |
| 7b CSRF | cerrado (`Origin` + `Content-Type`) |
| 7c `Host` | cerrado sin token |
| 8 symlink | cerrado en HTTP; la CLI queda igual |
| extra: `path` hacia `.git/` o `.kimun/` | cerrado (no estaba en S1) |

## Qué quedó abierto y por qué
- Vector 1 (tope de cuerpo/timeout/costo del parser): decisión pendiente (4 de `SERVE`).
- La raíz es el cwd del servidor; con `--store ALIAS` de un store enlazado, `docs create` escribe relativo a ese otro proyecto (función existente del CLI): el confinamiento comprueba contra el cwd, no contra esa raíz. El alias lo fija quien arranca el servidor, no la petición.
- Un `GET` `no-cors` de otro origen puede llegar sin `Origin`; no se lee la respuesta y el único GET con efecto es `corpus rank --refresh` (caché derivada).
- TOCTOU: el chequeo de enlaces y la escritura de la CLI son dos pasos; un proceso local con permisos de escritura en el proyecto podría cambiar un enlace entre ambos. Fuera de alcance del modelo (HTTP, no proceso local).
- Un `Host` ausente (HTTP/1.0) se acepta; los navegadores siempre lo envían.

## Pruebas existentes ajustadas
- `http_test.cljc`: `env-var-configures-the-token…` esperaba `KIMUN_SERVE_TOKEN=""` -> nil (auth apagada); ahora esperamos `cli/usage` (decisión 2). `cors-headers-when-enabled…` arrancaba `--cors` sin token; ahora lleva token (decisión 3). Dos pruebas de handler directo con `POST` y cuerpo sin cabecera ganaron `content-type: application/json` (regla 3c).
- Ninguna prueba de `test/kimun/surface/http/` usó rutas absolutas fuera del proyecto; ninguna otra se relajó.

## Verificación
- `lint.txt`: `rings and docstrings ok: 106 files`. `oracle.txt`: 9 filas, 0 discrepancias.
- `directed.txt` (todo `test/kimun/surface/http/`, `http-test`, `spec16-readers-test` (la única de `cli` que toca http), `main-test`, `golden-test`, `lint-test`): 124 pruebas, 943 aserciones, 0 fallos.
- `joint.txt`: mis 8 namespaces + 20 de `test/kimun/cli/` en una invocación: 251 pruebas, 2866 aserciones, 0 fallos (corrida antes de reescribir el helper `dispatch-failure` de mi prueba; `directed.txt` es posterior).
- `mutation.txt` (parche propio en /tmp, sin stash; `src/` restaurado, `diff -r` idéntico): sin confinamiento (32 fallos), sin chequeo de nombres (10), sin seguir symlinks (10), `.git`/`.kimun` permitidos (4), sin token fuera de loopback (7), `--cors` sin token (2), token vacío (1; y 3 en `resolve-token`), sin `Host` (11), sin `Origin` (16), sin `Content-Type` (5), comparación de token degradada (3). Línea base: 0 fallos.

## Límites
- Servidor real solo en `127.0.0.1` puerto efímero (se cierra siempre); el rechazo de `0.0.0.0` se prueba sobre `check-config!` y sobre `serve/dispatch` con `192.0.2.1` (no local), sin abrir interfaz externa. Una primera mutación (B) sin la guarda hizo que `serve/dispatch` arrancara y bloqueara; lo maté yo y rehice la prueba para acotar con `future` y evitar ese cuelgue.
- El tiempo constante se verifica por corrección, no midiendo tiempos.
- El `bb test` completo lo corre el coordinador; no lo corrí.
