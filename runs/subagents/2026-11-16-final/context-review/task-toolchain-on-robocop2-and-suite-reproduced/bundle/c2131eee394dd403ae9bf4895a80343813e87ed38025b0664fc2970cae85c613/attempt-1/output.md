REFORMULACION
**Propósito:** preparar la máquina robocop2 para ejecutar kimun y reproducir su suite. Este nodo es una tarea ejecutable, no un agregado ni paquete.
**Resultado:** `bb test` corre con el pod tree-sitter, sin fallos y con al menos 1081 tests; además, cuatro archivos de evidencia quedan commiteados y pusheados.
**Repo:** clon `~/proyectos/kimun` de `https://github.com/jpcosec/kimun`, rama `main`, en host ssh `robocop2`, usuario `robocop`.
**Límites:** sin cambios al código del repositorio; sin sudo; fuera de alcance los paths `/home/jp/...` de `docs/coordinacion/validate.sh`; esta revisión no ejecuta nada.

AMBIGUEDADES
1. “Babashka 1.13.219 (the value of :bb in release.edn)” frente a “install the bb version that release.edn names AFTER the pull”: si `release.edn` cambia tras el pull, no se sabe si prevalece la versión fija o la leída. Obligaría a inventar precedencia.
2. “Rust stable through rustup”: “stable” no fija versión. Obligaría a inventar/aceptar la toolchain vigente.
3. “the day of the run” y `YYYY-MM-DD`: no define zona horaria ni fecha efectiva. Obligaría a inventar UTC/local.
4. “if it has local changes, stop and report”: no aclara si incluye untracked ni cómo reportar. Obligaría a inventar criterio y canal.
5. “ssh host 'robocop2', user robocop”: no define credenciales ni configuración SSH. Obligaría a inventar el método de acceso.

DRY-RUN
Un ejecutor futuro leería el TaskDoc, `release.edn`, `bb.edn`, `runs/subagents/2026-11-16-find-genericos/coordinator/full-test.txt`, `AGENTS.md` y referencias; comprobaría acceso ssh, `git status`, `python3`, `systemd-run --user` y red. Cambios previstos en robocop2: pull o clon, instalar `~/.local/bin/bb`, rustup/cargo, `~/bin/memcap`, construir el pod con `bb pod`, ejecutar los cuatro comandos hacia archivos, crear `runs/subagents/YYYY-MM-DD-toolchain-robocop2/`, commit y push. Dependencias: ssh, red hacia GitHub/crates, permisos de push y systemd de usuario. Validaciones futuras: las cinco del TaskDoc; además `cargo --version` en login shell, cuatro archivos versionados y commit en `main`. Sin ejecución.

FUTURO
El ejecutor materializa el entorno y publica la evidencia; un humano resuelve accesos/credenciales o autoriza la parada si hay cambios locales; el siguiente nodo ajusta `validate.sh`. No son ambigüedades.
