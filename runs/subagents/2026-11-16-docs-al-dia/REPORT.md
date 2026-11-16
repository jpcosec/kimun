# docs-al-dia (2026-11-16) — informe

Worktree `/tmp/kimun-docs0-wt` (sin commit). Solo documentos, más una prueba documental. Nada de `src/`.

## Por archivo (antes -> ahora)

- `README.md`: antes, caja «D0 ya no está abierta, la única decisión abierta es D-G» y la implementación descrita como producto («knowledge store with a CLI and a content-addressed kernel»). Ahora: qué es kimun (ve, sintetiza, genera; proposiciones en contexto; realidad en las fuentes; cita del dueño), sección Status (en rearquitectura, sin release publicada, qué hay, qué no, paridad suspendida, git para el nivel de archivo y rehacer git diferido), punteros al plan y a la sesión, `bb oracle` necesita Python 3, instalación sin insinuar release. Tour: se quitó `journal show`; los otros 5 comandos se comprobaron contra `main.cljc` y los mapas `commands`.
- `AGENTS.md`: «Resuming work» apuntaba a la sesión 2026-10-08/09. Ahora el orden de lectura empieza por `00-nucleo`, `sesion-2026-11-15`, `diseno-declaraciones`, `plan-producto`; párrafo de dirección con invariantes, paridad suspendida y rehacer git diferido; en Workers: un `bb` a la vez con `flock`, `KIMUN_POD`, nunca `git stash`. Quitado: `journal` del desk, formatos viejos, `python/`/adaptadores (el texto de formato 4 se generalizó). Trailer y validación conservados (se añadió «needs Python 3» en `bb oracle`).
- `docs/faq.md`: «paridad cerrada y medida» -> suspendida (cita del dueño); nueva lista «Deferred by the owner» (rehacer git, capa G hasta `D-G`, reemplazo de sldb); el desk ya no cita `desk/models/*.edn`.
- `docs/v2/README.md`: quitada la referencia al `11` (cabecera, autoridad `14` y el «no existe 11»); los tres borradores entran como punto 0 de la autoridad, de la lectura y de la tabla de documentos (dicho que son borradores y lo vigente); nueva sección «Mecanismos diferidos o reemplazados» con una frase por `02 §5`, `10`, `14 §20`, `16`, `07`, parity-v1.
- `docs/v2/05-estado.md`: nota fechada 2026-11-16 (estado real y puntero al plan); «formatos 2, 3 y 4» -> formato 4; paridad marcada suspendida; decisión 19 sin `11-adaptadores.md`; filas 48–56 con citas textuales y `sesion §n` (ve/sintetiza/genera; declaraciones Clojure sin journal/planes; dos niveles y git; clases como etiquetas; dos cachés; materialización desde modelos; paridad suspendida; no borrar sin marcar; borrar la grasa). La 47 ya estaba. Arreglada de paso la fila 40, que tenía `|` sin escapar dentro de backticks (7 columnas).
- `docs/v2/12-ecosistema-y-pendientes.md`: nota de cabecera 2026-11-16 (orden = plan); la frase «En paralelo hoy: K2a» corregida; `RS1` (a): «migrate --from-v1 se borró: hay que rehacer la migración»; `C2`: sin `python/README.md` (no existe); `M2` redefinida con la cita de `sesion §4`. Una frase por corrección, filas no reescritas.
- `docs/v2/drafts/preguntas-abiertas.md`: poda (abajo). Nota de poda arriba.
- `docs/v2/drafts/plan-producto.md`: copia tal cual de `/tmp/kimun-planes/plan-producto.md` con una línea de cabecera.
- `docs/v2/tests/wittgenstein-manifest.md` y `test/fixtures/wittgenstein/manifest.edn`: ver «Pruebas».

## Preguntas cerradas (tabla «Cerradas el 2026-11-15/16»)

- `GC`: respondida en dirección. «no deberíamos borrar nada así automáticamente, algo que nada referencia se marca como "borrado"…» y «eso debería poder declararse por cada proyecto y según cada tipo de dato.» (§5)
- `PLANES`: pierde objeto. «Así es.» (§6)
- `F10` (parte a de `F10 / A1.1`): respondida en dirección. «TODOS los documentos que genera SLDB … MATERIALIZACIONES DESDE MODELOS.» (§4). La fila queda como `A1.1` (parte b abierta).
- `LINAJE`: respondida en dirección. «lo importante no es el nodo mismo, son sus relaciones» (§2), «las relaciones (que a su vez son nodos…)» (§3). Marcado que «linaje = relación más» es lectura del equipo.
- `M2-modo`, `M2-derivar`, `M2-desk`: pierden objeto. «Los documentos fuentes son los documentos fuentes de cualquier otro proyecto…» (§4).
- `K2b`, `K2a-fanout`, `K5-nombres`: pierden objeto. «el "rehacer git" déjalo EXPLÍCITAMENTE diferido» (§11).
- `16§3.5`: pierde objeto cuando se borre el modo de compatibilidad con v1 (**en curso**). (§8, §9)
- Siguen en la tabla activa: `LATIDO` (con nota de respuesta parcial: git detecta qué cambió, §11) y `RENOMBRAR` (reformulada sin «identidad», no respondida, cita §11). Sin tocar: `D-G`, `M1e`, `F1-H3`, `SERVE`, `16-det`, `U1`, `P1-linea`, `K1f-campo`, `S2b-anclas`, `RS1`.

## Pruebas ajustadas

- `test/kimun/wittgenstein_manifest_test.cljc` (`exclusions-and-markdown-doc-match-manifest`): fijaba en el README la caja «`D0` is no longer an open decision», que quité a propósito. La cambié por tres comprobaciones del README nuevo: cita `docs/v2/drafts/plan-producto.md`, dice «no published release» y nombra `D-G`. Se conserva «not yet implemented» (la frase está en el README nuevo).
- Manifiesto (pedido: `C03`): la fila `C03` citaba `11-adaptadores.md` y «adapters actuales son symbols|cli»; ahora «Solo intención en `14 §7`; no hay adaptadores (el subsistema se borró el 2026-11-15)». En `manifest.edn`, `:current-evidence` de `C03` sin `11-adaptadores.md`. Mismo defecto, fuera de lo pedido y con el mismo mínimo cambio: `C16` (md y edn, cita `11-adaptadores.md` que no existe) y `C28` (md y edn, «solo migración sldb v1 implementada», que se borró).

## Validación

`lint.txt`: `rings and docstrings ok: 103 files`. `directed.txt`: `kimun.doc14-contract-test`, `kimun.wittgenstein-manifest-test`, `kimun.spec16-models-contract-test`, `kimun.lint-test`: 18 tests, 1235 aserciones, 0 fallos, 0 errores (la primera corrida falló 2 por el README, ya ajustado). Tablas Markdown: script que cuenta columnas por tabla en los 7 archivos tocados; todas consistentes tras escapar la fila 40 de `05`.

## No verificado

- Que `stores changed --since`, el borrado de paridad v1 y del modo `:legacy-v1-template` estén «en curso»: lo tomé del plan; en este worktree `:legacy-v1-template` todavía existe en `src/kimun/surface/models/selector.cljc`.
- Las frases de `docs/v2/README.md` sobre qué mecanismos de `02 §5`, `10` y `14 §20` describen «rehacer git» se basan en la lista del encargo y en `12 §1`; no releí esos documentos.
- No corrí `bb test` completo (norma) ni `bb oracle`. No re-medí la cifra de pruebas (cito 1 176 del plan). Los comandos del tour se comprobaron en el código, no ejecutándolos.
