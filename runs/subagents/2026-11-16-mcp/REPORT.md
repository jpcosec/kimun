# REPORT mcp

## Archivos
- Nuevos: `src/kimun/surface/mcp.cljc` (tabla de herramientas + manejador puro), `src/kimun/cli/commands/mcp.cljc` (bucle stdin/stdout), `test/kimun/cli/mcp_test.cljc`.
- Modificados: `src/kimun/cli/main.cljc` (require, `(declare run version-data)`, entrada `"mcp"` en `groups`, spec, una línea de ayuda), `docs/v2/06-superficies-y-cli.md` (sección `### mcp`), `README.md` (línea 24).
- `run` y la versión se inyectan desde la entrada de `groups` (cierre sobre `run` y `(:version (version-data))`); la superficie no requiere `cli.main` ni usa `requiring-resolve`. El envelope final del comando va marcado `:streamed` para que `-main` no imprima nada tras el fin de stdin. Mientras sirve, `*out*` está ligado a stderr (lo que imprima una herramienta no ensucia stdout).

## Herramientas
| Herramienta | Argumentos | argv | Escribe |
|---|---|---|---|
| kimun_declare_read | files? | declare read [FILE…] | no |
| kimun_declare_anchors | files?, since? | declare anchors [FILE…] [--since C] | no |
| kimun_declare_terms | files? | declare terms [FILE…] | no |
| kimun_declare_about | term xor path, exact?, files? | declare about TERM [FILE…] [--exact] / --path PATH [FILE…] | no |
| kimun_declare_members | pattern | declare members PATTERN | no |
| kimun_changed | since? | stores changed [--since C] | no |
| kimun_seen | — | stores seen | no |
| kimun_health | — | stores health | no |
| kimun_declare_add | to, form, context? | declare add --to FILE FORM [--context N] | si |
Añadí `files` (opcional) a read/anchors/terms/about: el brief no lo pedía, pero los comandos aceptan `[FILE…]`. `exact:false` no emite `--exact`.

## Lo que supuse del protocolo (para contrastar)
1. Transporte stdio: un mensaje JSON-RPC 2.0 por línea (sin framing Content-Length), UTF-8; respuestas una por línea en stdout.
2. `initialize`: params `{protocolVersion capabilities clientInfo}`; resultado `{protocolVersion capabilities {tools {}} serverInfo {name "kimun" version "2.0.0-alpha.1"}}`. Eco de `protocolVersion` si es `2024-11-05`, `2025-03-26` o `2025-06-18` (esa lista es de memoria); si no, `2024-11-05`. `initialize` sin params objeto: -32602.
3. `notifications/initialized` y cualquier mensaje sin `id`: sin respuesta (también si el método es desconocido). Respuestas del cliente (con result/error y sin method): ignoradas.
4. `ping` → `{}`.
5. `tools/list` → `{tools [{name description inputSchema annotations {readOnlyHint bool}}]}`, sin paginación (no emito `nextCursor`). `annotations.readOnlyHint` lo emito en todas (true/false); lo recuerdo de la 2025-03-26, un cliente 2024-11-05 puede ignorarlo. `inputSchema` es JSON Schema `{type "object" properties … required? additionalProperties false}`.
6. `tools/call`: params `{name arguments}` (`arguments` ausente = `{}`); resultado `{content [{type "text" text <envelope JSON>}] isError bool}`; `isError` siempre presente. El texto es el JSON "pretty" de `out/emit`.
7. Errores JSON-RPC: -32700 (id null), -32601 método desconocido, -32602 params inválidos (**incluye herramienta desconocida**, que supongo es -32602 y no un resultado con isError), -32600 petición inválida (no objeto, arreglo/lote, sin method), -32603 excepción interna. No hay soporte de lotes.
8. Sin `resources`, `prompts`, `sampling`, `logging`, `listChanged`, `notifications/cancelled`, progreso: `capabilities` solo declara `tools`.
9. (Corregido) Cualquier valor de cadena, suelto o dentro de un arreglo, que empiece por `-` es -32602 ("starts with `-`") y `run` no se llama; vale para todas las herramientas, `form` incluida. No añadí `--` como fin de opciones: no lo comprobé en la CLI.

## Pruebas (`test/kimun/cli/mcp_test.cljc`, 6 tests, 141 aserciones)
- methods-and-ids: initialize (versión conocida/desconocida, id numérico y de cadena), ping, notificaciones sin respuesta (conocida, desconocida, tools/call), -32601, -32700 con id null, -32602 sin params.
- the-tool-table-is-listed…: las 9 herramientas, `inputSchema` objeto con `properties`, `required` ⊂ properties, `readOnlyHint` solo falso en `declare_add`.
- each-tool-builds-its-argv: 15 casos con `run` inyectado: argv exacto, env/cwd pasados, envelope intacto en `content`, `isError false`.
- a-failed-command…: `:ok false` → `isError true`, sin error JSON-RPC, el error del envelope en el contenido.
- invalid-calls…: comando fuera de la tabla (también «stores check» y «declare read» como nombre), argumento fuera de esquema, falta requerido, tipo erróneo, term/path ausentes o ambos, arguments no objeto; `run` nunca se llama.
- the-process-speaks…: proceso real `bin/kimun mcp` en proyecto temporal con `kimun/a.clj`: initialize, initialized, tools/list, declare_read → una línea JSON por petición (ids 1,2,3), exit 0, archivo intacto; luego declare_add → exit 0, una línea, el archivo cambió. (El proceso hijo no usa flock propio: el candado lo toma quien corre las pruebas.)

## Evidencia
`lint.txt` (rings and docstrings ok: 108 files; primera corrida falló por un docstring faltante, corregido), `oracle.txt` (9 rows, 0 mismatches), `directed.txt` (mcp, main, golden, declare, lint: 23 tests 233 aserciones, 0 fallos), `joint.txt` (mcp + 20 de test/kimun/cli: 159 tests 1849 aserciones, 0 fallos), `session.txt` (sesión real de entrada/salida, incluye error de comando, -32602, -32601, notificación sin respuesta, -32700), `mutation.txt`.

## Mutación (parche propio, sin stash; src restaurado y verificado con diff)
- M1 «una notificación se responde»: 6 fallos (methods-and-ids y proceso real).
- M2 primera versión (comando fuera de la tabla solo con prefijo `raw:`): **sobrevivió** porque el test no probaba una línea de comandos como nombre; reforcé el test y M2b («un nombre fuera de la tabla se ejecuta como argv»): 4 fallos.
- M3 «el envelope fallido no marca isError»: 1 fallo.

## Límites y desvíos
- Sin contrastar con la especificación (no hay red): ver los supuestos arriba, especialmente 2, 5 y 7.
- No hay pruebas de `-32603` ni de líneas en blanco/CRLF (las líneas en blanco se ignoran; un `\r` final lo tolera el parser JSON).
- El servidor atiende en serie; una herramienta lenta bloquea las demás. No hay cancelación.
- Un valor legítimo que empiece por `-` (p. ej. un archivo `-x.clj`) no se puede pasar: se rechaza.
- No ejecuté `bb test` completo ni `bb oracle` más allá de lo indicado. Nada commiteado.

## Corrección posterior (valores que empiezan por `-`)
`option-like` en `src/kimun/surface/mcp.cljc` rechaza, antes de `run`, todo valor de cadena que empiece por `-`. Test nuevo en `invalid-calls-never-reach-the-cli` (9 casos: `term "--path"`, `files ["--since" ..]`, `to "--context"` y uno por herramienta restante). Mutación M4 (validación quitada): 10 fallos y 9 errores. lint ok; directed 23 tests/253 aserciones, 0 fallos; joint 159 tests/1869 aserciones, 0 fallos. Doc 06 actualizada (una frase).
