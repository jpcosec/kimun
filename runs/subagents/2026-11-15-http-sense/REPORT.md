# REPORT · http-sense (corte V1)

## Archivos tocados
- `src/kimun/surface/http/routes.cljc`: 2 entradas `command-route` (`GET /stores/sense`, `GET /stores/health`), sin handler nuevo.
- `test/kimun/surface/http/circuit_test.cljc` (nuevo, 5 pruebas, 18 aserciones).
- `docs/v2/06-superficies-y-cli.md §4`: rutas añadidas a la lista y una nota (lecturas puras; `heartbeat` sin ruta).
- `docs/v2/12-ecosistema-y-pendientes.md`: solo la nota de avance de la fila «Operación y vista».
- No toqué `surface/docs.cljc`, `commands/docs.cljc` ni `include.cljc`. No hay commit.

## Pruebas (cada una discrimina)
1. `sense-over-http-is-the-cli-data-and-sees-the-mutation`: 200, `:command "stores sense"`, señal `source/mutated`, y `:data` igual al de la CLI (vía JSON) salvo `:observed-at`.
2. `health-over-http-reports-degraded-with-its-next-step`: archivo reformateado, `degraded`, `:next "run: kimun docs update doc.md --name doc"`, `:data` igual a la CLI.
3. `observing-over-http-writes-nothing`: sha256 de cada archivo bajo `.kimun` (incluye `cache/`) idéntico antes y después de ambas rutas, sobre un proyecto temporal con documento mutado.
4. `the-circuit-routes-honor-the-token`: sin Authorization 401, con Bearer 200, en ambas rutas.
5. `the-circuit-routes-are-get-only`: POST da 405 en ambas.
Las seis pruebas del plan quedaron en cinco (tope): mutación e igualdad con la CLI van juntas.

## Números
- Dirigidas: `circuit-test`, `http-test`, `models-fields-test`, `heartbeat-test`: 43 pruebas, 254 aserciones, 0 fallas (`directed.txt`).
- `bb lint`: ok, 104 archivos (`lint.txt`).
- Mutación (`git stash push -- src/`): las 5 pruebas nuevas fallan, 14 fallos, 0 errores (`mutation.txt`). Stash restaurado.
- No corrí `bb test` completo (regla).

## Tests existentes ajustados
Ninguno. `http_test` y `models_fields_test` pasan sin cambio.

## Respuestas a las preguntas del brief
- `command-route` no filtra ni transforma campos: devuelve el envelope del comando (`run-command`). Prueba 1 y 2 lo verifican en `:data` salvo `:observed-at`. Los `:warnings` los emite el comando y salen igual (no los comparé aparte).
- `:mode :once` aparece también por HTTP (es parte del `:data` de la CLI).

## Ambigüedades e inferencias
- Nombre de ruta: `/stores/sense` frente a `/sense`. El spec no fija convención para rutas nuevas: `docs/v2/06-superficies-y-cli.md:529-533` solo dice que «cada ruta delega en un comando de §3» y lista `/stores`, `/stores/check`, `/stores/links`. Inferencia: se sigue el patrón comando `stores X` -> `/stores/X` de esas tres rutas (`routes.cljc:182-184`). Si el dueño prefiere otra, es cambiar una cadena.
- `06 §3 serve` (l. 508-510) no dice cómo se exponen comandos de lectura: la convención sale de las rutas existentes, no de texto normativo.
- `/stores/health` se parece a `/health` (`routes.cljc:166-177`, que es `stores check` resumido con otra forma `{:status :findings :action}`). Dos «health» con forma distinta; no lo decidí, solo lo señalo.

## Límites
- `stores heartbeat` sin ruta (flujo NDJSON; `06 §3 «El latido»`).
- No probé el fallo del comando (store inválido) para estas dos rutas; por construcción usan el mismo `command-route` que `/stores/check` (no verificado).
- No probé sense con pod tree-sitter ausente/presente (los documentos de la prueba son Markdown genéricos).
- Prueba de sha cubre un store formato vigente con documento ingestado; no un `cache/` poblado explícitamente más allá de lo que crea `docs ingest`.
- Ningún servidor quedó corriendo (`with-server` los detiene; solo `127.0.0.1`, puerto 0).
