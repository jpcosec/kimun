# DOC-preguntas (2026-11-15)

Solo documentos. Sin `src/`, sin pruebas tocadas, sin commit. Worktree `/tmp/kimun-docq-wt` (base `6cf18730`).

## Archivos
- `docs/v2/drafts/preguntas-abiertas.md` (+28 líneas): 3 notas fechadas y 9 filas nuevas en la tabla activa.
- `docs/v2/12-ecosistema-y-pendientes.md`: solo la fila `M1` y una frase en la fila «Operación y vista». No se tocaron `K1`, `S2`, `K5`.
- `AGENTS.md`: «Resuming work», punto 2.

## preguntas-abiertas.md
Notas (antes de «Lo que bloquea seguir»): (1) las filas nuevas salen de cortes del 2026-11-15, no las planteó el dueño, ninguna está respondida ni recomendada; GC y M1e no llevan ruta porque su evidencia vive en worktrees sin integrar; (2) lo ya decidido por el dueño el 2026-11-15: «producto funcional» = terminar todo lo planificado en `12 §1`; C2 retirado (fila `C2` de `12 §1`); (3) la identidad de una clase entre revisiones no es pregunta abierta (`14 §18` punto 3, l. 243; `16 §4.3`, l. 216-218), con «conversación pendiente» sobre el malentendido, sin contenido inventado.
Filas (ids): `GC`, `PLANES`, `M1e`, `F1-H3` (las 3 opciones copiadas del informe F1-fix), `SERVE` (las 4 sub-preguntas del REPORT de S1), `16§3.5`, `16-det` (diamante; fixture vs. ejemplo §3.4; `models define` y bases), `U1`, `F10 / A1.1` (no estaban en la tabla). Cada fila: pregunta, opciones, evidencia, qué bloquea.

## 12 §1
Fila `M1` antes: «…Pendiente: motor de selector, strict, proyecciones, store, K6, ids de modelo». Ahora añade M1c (`selector/extract-selector`, modo legacy, paso sin `:outer-type` falla), M1d (`:strict`; default sigue legacy; quirk de `TaskDoc` no reproducido), M1f (`models define` spec 16, `models check`, `models list --isa`; v1 conserva su dirección) y deja en «Pendiente»: M1e (retenido por la fila `M1e`), M1g, M1h, M1i, M1j, M1k, M1l; evidencia amplía con los tres directorios. Fila «Operación y vista»: frase «Avance (S1…)»: auditoría de `serve` solo como evidencia, 5 pruebas y tabla de vectores, decisión pendiente en la fila `SERVE`.

## AGENTS.md
Antes: «decisions only the owner can make (today: D-G, … D0 … is no longer one of them … what remains is its implementation, K1. Do not implement anything that depends on an open decision.»
Ahora: remite a la tabla y a las notas fechadas de `preguntas-abiertas.md`, sin enumerar, y dice no implementar lo que dependa de una decisión abierta y reportar la ambigüedad. Ninguna prueba fija ese texto (grep en `test/`: no).

## Citas
Verificadas abriendo el archivo: líneas de `10 §7`, `§11.3` (l. 593-654, 612, 636, 385), `16` (l. 81, 134-166, 172-188, 198-207, 216-218, 259-266), `14` (l. 191, 243, 276-279, 452-453), fixture (l. 86, 119, 147, 208-210), `frentes-plan.md` (l. 81, 196-206, 252, 310), `06` (§3 serve l. 517-519), informes M1a/M1c/M1d/M1f, F1, cold-cache, S1.
Corregidas respecto de los informes: S1 cita `06:508-510` (ahora l. 517-519); F1 cita `10` l. 387 para «borrable» (es l. 385); el brief decía `14 §19.2` y `14 §18.9`: no existen como encabezados; se cita `14 §19` (l. 191) y `14 §18` punto 9 (l. 276).
No pude verificar y por tanto no puse ruta: el informe de `stores gc` (GC) ni el del corte M1e (no están en el repo). «Producto funcional» no figura en ningún documento del repo: se registra por lo dicho en el encargo.
Los `|` y el conteo de columnas de cada tabla tocada se comprobaron con un script: 0 filas desalineadas.

## Validación
`lint.txt`: `rings and docstrings ok: 108 files`. `directed.txt`: doc14-contract, wittgenstein-manifest, spec16-models-contract, lint-test: 17 tests, 1237 aserciones, 0 fallos.
