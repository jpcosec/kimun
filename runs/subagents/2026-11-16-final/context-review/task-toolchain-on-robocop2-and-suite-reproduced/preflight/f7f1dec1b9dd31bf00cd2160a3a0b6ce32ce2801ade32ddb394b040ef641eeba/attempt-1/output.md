## REFORMULACION

Encargo: preparar la máquina robocop2 (ssh `robocop2`, usuario `robocop`) para ejecutar la suite de kimun y reproducirla. Resultado: en `~/proyectos/kimun` (clon de https://github.com/jpcosec/kimun, rama main), `bb test` pasa con el pod tree-sitter y sin fallos, con al menos los 1081 tests de la baseline (commit e84869af), y las cuatro evidencias (`lint.txt`, `full-test.txt`, `oracle.txt`, `include-check.txt`) quedan commiteadas y pusheadas en `runs/subagents/YYYY-MM-DD-toolchain-robocop2/`. Límites: sin sudo; instalar Babashka 1.13.219, rustup/cargo estable, `~/bin/memcap` y el binario del pod; no modificar código del repositorio; fuera de alcance las rutas `/home/jp/...` de `docs/coordinacion/validate.sh`.

## AMBIGUEDADES

1. "install the bb version that release.edn names AFTER the pull" frente a "Babashka 1.13.219 (the value of :bb in release.edn)": si tras el pull `release.edn` nombra otra versión, no se sabe cuál prevalece; obligaría a inventar la prioridad.
2. "with exactly: '#!/usr/bin/env bash' / 'gb=3; ...'": el entrecomillado y los saltos de línea del contenido literal de `memcap` no están definidos; obligaría a inventar los bytes exactos.
3. "runs/subagents/YYYY-MM-DD-toolchain-robocop2/ (the day of the run)": no se fija fecha ni zona horaria; obligaría a inventar la fecha de la carpeta.
4. "if it has local changes, stop and report": no dice dónde ni a quién reportar; obligaría a inventar canal y formato.
5. "prints "ok" : true with every finding at 0": no se especifica el formato de salida de `include check`; obligaría a inventar el criterio de comprobación.
6. "at least the 1081 tests" frente a la baseline `:pass 10286`: no aclara si 1081 es `:test` o el total de assertions; obligaría a inventar la métrica si falta la línea de baseline.

## DRY-RUN

Lecturas: `release.edn`, `bb.edn`, `AGENTS.md`, `runs/subagents/2026-11-16-find-genericos/coordinator/full-test.txt`, estado del clon y cambios locales. Cambios previstos: instalar bb en `~/.local/bin`, rustup/cargo, crear `~/bin/memcap`, ejecutar `bb pod` (compilación cargo), lanzar los cuatro comandos a sus archivos, crear el directorio de evidencias, commit y push. Dependencias: red hacia GitHub, crates.io y rustup; `systemd-run --user`; `python3`; credenciales de push. Validaciones futuras: las cinco comprobaciones de Validation sobre los archivos commiteados.

## FUTURO

Otro nodo o un humano decide el ajuste de rutas en `docs/coordinacion/validate.sh` (siguiente tarea), la intervención ante cambios locales o fallo de push, y la revisión final de la evidencia; un nodo posterior valida los cinco checks sobre los archivos ya commiteados.
