# REPORT latido-git

`stores sense`, `stores health` y `stores heartbeat` le preguntan a git qué rutas cambiaron entre el puntero (`seen.edn`) y el árbol de trabajo, y solo abren los documentos cuya ruta está en ese conjunto. Sin commitear, ni commits, ni puntero, o con `--full`: camino de antes. Nadie mueve el puntero.

## Archivos (worktree /tmp/kimun-beat-wt, sin commitear)
- `src/kimun/surface/diagnostics.cljc`: `report` recibe `:changed` (conjunto de rutas; nil = todas). Un documento con ruta fuera del conjunto no se lee ni se hashea (`unread?`, `classify-unread`; igual en `diagnose-generic`).
- `src/kimun/cli/commands/stores.cljc`: `change-scope` (habla con `kimun.host.git`/`seen`; el renombre cuenta en la ruta vieja y la nueva), `scoped-report` (cuenta `:opened`), `project-of` movido arriba, `observe` con `full?`, `:scope` en `sense`/`health`/cada latido, opción `--full` en el spec; docstrings.
- `src/kimun/cli/main.cljc` (3 líneas de ayuda), `docs/v2/06-superficies-y-cli.md` (filas `health`, `sense`, `heartbeat` y el campo de `beat`).
- `test/kimun/cli/beat_git_test.cljc` (nueva, 11 pruebas, 207 aserciones); `test/kimun/cli/diagnostics_test.cljc` (una línea: ver "Pruebas existentes").
- `kimun.surface.heartbeat` no cambió (sigue puro). `check`, `update` y `reconcile` siguen por el camino completo.
- Evidencia: `lint.txt`, `directed.txt`, `oracle.txt`, `mutation.txt`, `measurement.md` en `runs/subagents/2026-11-16-latido-git/`.

`:scope {:mode :git|:full :since :head :changed n :opened n}`; en `:full` `:since :head :changed` son nil (`:opened` = archivos leídos, contados en el lector de archivos).

## Clasificación de las comprobaciones del diagnóstico
Documento tipado (`classify`):
| comprobación | lee el archivo |
|---|---|
| `:missing` (ruta sin archivo) | sí: es el `nil` de la lectura; en git un borrado/movido está en el conjunto y se lee |
| `:orphan` (modelo desaparecido) | no: sale del store; se hace para todos |
| `:contract-inconsistent`, `:parser-unavailable`, `:contract-moved` (incluye `:superseded` y contrato registrado distinto del vigente) | no: comparación del contrato registrado con el vigente; se hacen para todos |
| `content-ok` (huella del texto) | sí |
| `:unextractable` (con el texto del archivo si cambió, o el registrado) | sí |
| `:noise` | sí |
| `:reformatted` / `:edited` | sí |
| `:rebuild-mismatch` (raíz o hash reconstruido) | sí (reconstruye desde el texto) |
Genérico (`diagnose-generic`): `:missing`, `content-ok`, `:reformatted`/`:edited`, `:rebuild-mismatch` leen; contract-inconsistent/parser-unavailable/contract-moved no. Modelos (`model-finding`) y `:corrupt` (verificación del kernel): no leen archivos de documentos, se hacen siempre.
Un documento fuera del conjunto recibe solo: orphan > inconsistent > parser-unavailable > contract-moved > ok.

## Medición (detalle y método en `measurement.md`; mínimo / mediana de 3 repeticiones, segundos de proceso completo; carga 1,3–2,2)
| N | caso | arranque `stores show` | `docs list` | sense git | sense --full | health git | health --full |
|---|---|---|---|---|---|---|---|
| 500 | nada | 0,31 / 0,32 | 0,61 / 0,64 | 1,14 / 1,16 | 1,92 / 1,94 | 1,12 / 1,15 | 1,95 / 1,99 |
| 500 | 1 cambiado | 0,31 / 0,32 | 0,61 / 0,61 | 1,12 / 1,13 | 1,94 / 1,95 | 1,14 / 1,14 | 1,95 / 1,97 |
| 2000 | nada | 0,32 / 0,32 | 1,55 / 1,56 | 3,50 / 3,56 | 6,95 / 7,08 | 3,54 / 3,62 | 6,75 / 6,89 |
| 2000 | 1 cambiado | 0,32 / 0,33 | 1,57 / 1,58 | 3,49 / 3,65 | 6,74 / 6,96 | 3,45 / 3,47 | 6,60 / 6,66 |
Ahorro: ≈ 0,8 s (41 %) con N=500 y ≈ 3,5 s (50 %) con N=2000. El camino por git sigue siendo O(N) (1,15 → 3,56 s): quedan abrir el store y listar (≈ 0,3 / 1,25 s), la verificación de objetos (`store/verify`, igual en ambos caminos), el contrato por documento y las señales; esa parte restante (≈ 0,5 / 2,0 s) es una resta, no la medí por separado. Un archivo cambiado no se distingue de ninguno dentro del ruido.

## Qué cubren las pruebas (`beat_git_test`)
- nada cambiado: `:mode :git`, `:opened 0`, `:changed 0`, lector inyectado sin lecturas, y la respuesta (sense/health) igual a `--full`.
- editado (sin commitear y luego commiteado, con el puntero atrás), tipado editado, reformateado, borrado, movido (`git mv`): se lee exactamente el conjunto esperado de rutas y el `:data` (señales, hallazgos, veredicto, warnings) es igual al de `--full` sin `:scope`/`:observed-at`; las señales no están vacías.
- renombre cuenta en ruta vieja y nueva (`change-scope`); contrato movido (`field-add` sin `--backfill`) visto sin que cambie ningún archivo y sin leer ninguno; huérfano (con `tdocs/documents` redefinido a `:state :orphan`, no hay forma de crear uno por CLI) visto sin leer; sin puntero / git sin commits / fuera de git: `:mode :full`, `:opened` = todos; `--full` con puntero: `:full`; puntero dañado: `git/bad-pointer` (y `--full` no lo consulta).
- latido: mismos registros git y `--full` (sin `:at`, `:elapsed-ms`, `:scope`, `:observed-at`) en tres estados (limpio, editado, borrado); el `beat` lleva `:scope :git`.
- sha256 de todo `.kimun` (con `seen.edn`) idéntico tras sense/health/heartbeat, git y `--full`.
- Mutación (`mutation.txt`, parches propios sin stash, restaurado con `diff -r`): M1 conjunto ignorado → 17 fallos; M2 renombre solo en la ruta nueva → 5; M3 contrato no comprobado en los no leídos → 4; M4 `--full` ignorado → 28; M5 huérfano no comprobado → 1. (Revertir todo `src/` hace que la prueba ni cargue: no cuenta como mutación.)
Directas: 48 + 53 + 17 pruebas; `bb lint` ok; `bb oracle` 9 filas, 0 diferencias. Todas verdes tras el ajuste de abajo.

## Pruebas existentes
`kimun.cli.diagnostics-test/health-names-the-document-the-category-and-the-command` comparaba `:data` de `stores health` entero con `{:status :findings :actions :next}`; ahora `:data` lleva `:scope` (lo pide el diseño), así que la prueba lo quita (`:summary :scope`). Ninguna otra cambió. Las demás de `sense`/`heartbeat` no fijaban la forma exacta de `:data`.

## Límites
- Un archivo reportado como no cambiado no se lee: **no se detectan** en él `:unextractable`, `:rebuild-mismatch` ni `:noise` (los tres necesitan el texto). Si el store ya tenía ese hallazgo antes del puntero, `--full` lo ve y el camino git no: los veredictos de ambos caminos solo son idénticos cuando el store no tiene esos hallazgos en archivos sin cambios (este es el problema de diseño principal; ver abajo).
- Puntero movido con el store desfasado: git no ve el desfase (aceptado por diseño; en docstring y fila 06).
- Un documento en un archivo ignorado por `.gitignore` (git no reporta cambios en él) nunca se ve cambiar por git; `--full` sí. Un archivo cuyo contenido cambia sin que git lo note (p.ej. marcado `assume-unchanged`) igual.
- El diagnóstico sigue O(N) en abrir el store, `store/verify` y el contrato por documento (ver medición); solo se evita leer/hashear/clasificar archivos.
- Un latido consulta git en cada latido (dos procesos `git`).
- Rutas de documentos que no son relativas al proyecto (p.ej. si el store no está en la raíz del repo) usan `git --relative`; no probado con el proyecto en un subdirectorio del repo.
- La medición usa documentos pequeños de un modelo de dos campos, una sola máquina con carga; no mide muchos archivos cambiados.

## Problemas del diseño
1. **Hallazgos que necesitan el texto desaparecen en archivos sin cambios.** `:noise` ya registrado (que el diagnóstico completo reporta como aviso sin bloquear), `:rebuild-mismatch` y `:unextractable` por parser/extractor nuevo no se ven en modo git; el verdicto puede ser `ok` en git y `degraded|needs-human` con `--full` en el mismo estado. El contrato movido sí se ve (no necesita el texto), pero un `rebuild-mismatch` provocado por un cambio del parser dentro del mismo contrato no. Opciones para el coordinador: dejarlo y decirlo (como está, con `--full`); o que el modo git guarde/recuerde esos hallazgos del último diagnóstico completo (necesitaría estado nuevo).
2. `:since`, `:head`, `:changed` valen nil en `:scope` de `:full` en vez de ceros; si se prefiere que `:changed` sea un número siempre, es un cambio de una línea.
3. `store/verify` sigue recorriendo todos los objetos en ambos caminos; es una parte grande del residuo O(N) del camino git y no se tocó (cambiaría la señal `:corrupt`).
