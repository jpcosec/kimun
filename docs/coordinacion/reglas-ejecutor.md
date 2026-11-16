# Reglas comunes para ejecutores de kimun (léelas antes de tu brief)

Repo: `/home/jp/proyectos/kimun` (rama `main`, **no lo edites**: hay otras sesiones). Trabajas
en el worktree que te indique tu tarea.

1. Lee primero `AGENTS.md`, `docs/v2/12-ecosistema-y-pendientes.md §1` y los documentos que
   tu tarea cita. **Sigue el spec lo más fielmente posible.** Si el spec es ambiguo o se
   contradice en algo que cambia el diseño, **no decidas**: sigue con lo que no dependa de
   eso y reporta la ambigüedad con cita (archivo y línea) y las opciones.
2. **Un solo `bb` a la vez en toda la máquina** (dos OOM tumbaron el escritorio). Todo `bb`
   o `bin/kimun` va así, sin excepción:
   `flock /tmp/kimun-bb.lock env KIMUN_POD=/home/jp/proyectos/kimun/pods/treesitter/target/release/pod-kimun-treesitter /home/jp/bin/memcap 3 bb …`
   El `flock` espera su turno solo. **No corras `bb test` completo** (lo hace el coordinador);
   solo pruebas dirigidas, con algo como
   `bb -e "(require 'clojure.test) (def nss '[ns1 ns2]) (apply require nss) (let [r (apply clojure.test/run-tests nss)] (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))"`
   y `bb lint`.
3. No commitees, no hagas push, no toques `/home/jp/proyectos/kimun`, ningún store real
   (`.kimun/` de ningún proyecto) ni archivos fuera de tu worktree y de `/tmp`. Experimentos
   solo en directorios temporales. Nada de red ni servicios externos.
4. **La paridad con v1 está suspendida** (dueño, 2026-11-15): no conserves un quirk de v1 a costa del diseño; si tu corte cambia a propósito un comportamiento que una prueba de paridad fija, ajusta o retira esa prueba y dilo en tu REPORT.
4b. Código y docstrings en inglés; documentos en español. Respeta los anillos de `bb lint`
   (`docs/v2/03-estandares-de-codigo.md §1`). No toques `src/kimun/kernel/*` salvo que tu
   tarea lo diga.
5. **Pocas pruebas, que discriminen**: cada una debe fallar sin tu cambio. Compruébalo así,
   **sin `git stash`** (el stash es común a todos los worktrees del repo: dos ejecutores a la
   vez se pisan y terminan con los cambios del otro):
   `git diff -- src/ > /tmp/<TAREA>-src.patch && cp -r src /tmp/<TAREA>-src-backup && git checkout -- src/`
   (y aparta a mano los archivos nuevos de `src/` que no estén en git), corre las pruebas,
   y restaura con `git apply /tmp/<TAREA>-src.patch` (más los archivos nuevos). Verifica con
   `diff -r src /tmp/<TAREA>-src-backup` que quedó idéntico. Ninguna prueba con ramas
   `(is true …)` ni que compare dos valores vacíos.
6. Entrega en `<worktree>/runs/subagents/2026-11-15-<TAREA>/`: `lint.txt`, `directed.txt`,
   `mutation.txt` y un `REPORT.md` con: archivos tocados, números, qué prueba cada test,
   tests existentes ajustados y por qué, límites que quedan, ambigüedades del spec, y
   cualquier desvío. No afirmes más de lo verificado.
