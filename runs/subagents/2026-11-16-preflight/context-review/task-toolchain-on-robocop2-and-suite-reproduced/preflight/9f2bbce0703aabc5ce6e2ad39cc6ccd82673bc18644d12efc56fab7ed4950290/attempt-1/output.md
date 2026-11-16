REFORMULACION
Propósito: restaurar en robocop2 la toolchain necesaria (bb, cargo, memcap) y reproducir la suite con el pod tree-sitter. Resultado: `bb test` con pod tree-sitter arroja las mismas cifras que el último commit `chore(evidence)` de `main`; cierre observable: 1081 tests, 0 fallos, pod disponible en robocop2. Repo: no se nombra; solo se citan `release.edn`, `main` y `docs/v2/drafts/traspaso-2026-11-17.md §10`. Límites: instalar bb (versión de `release.edn`), cargo y memcap; `bb pod`; `bb test`; comprobar `tree-sitter pod: available`. No es agregado ni paquete: es `task.external` ejecutable.

AMBIGUEDADES
1. “same numbers as the last chore(evidence) commit of main”: no se identifica repo, remoto, rama concreta ni SHA; obligaría a inventar dónde está `main` y cuál es el commit/base.
2. “Install bb (version in release.edn), cargo and memcap”: no hay versiones de cargo/memcap, gestor de paquetes, rutas ni permisos; obligaría a inventar el procedimiento.
3. “## Validation” seguido de “-”: no hay checks; obligaría a inventar validaciones distintas de Done When.
4. “1081 tests, 0 failures” frente a “same numbers”: no aclara si 1081 es el baseline exacto o solo un ejemplo; obligaría a fijar el conteo esperado.
5. “bb pod” y “bb test with the tree-sitter pod”: no se da sintaxis, entorno ni forma de seleccionar el pod; obligaría a inventar comandos.

DRY-RUN
Lecturas: TaskDoc, `release.edn`, `docs/v2/drafts/traspaso-2026-11-17.md §10`, estado de robocop2 y baseline del commit citado. Cambios previstos: instalar bb/cargo/memcap, construir pod y ejecutar suite. Dependencias: acceso a robocop2, red/repositorios, permisos de instalación, toolchain y baseline recuperable. Validaciones futuras: `bb pod` deja el pod construido; `bb test` muestra `tree-sitter pod: available`, 1081 tests y 0 fallos, comparados con el baseline. Sin ejecutar.

FUTURO
Un humano debe confirmar repo/commit base, versiones y método de instalación, y el baseline numérico. El ejecutor futuro aportará log de instalación, salida de `bb pod`/`bb test` y comparación; la aceptación final corresponde al revisor del cierre.
