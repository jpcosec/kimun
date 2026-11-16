## REFORMULACION
Propósito: devolver a robocop2 la capacidad de verificar kimun. Resultado: en `robocop2` (usuario `robocop`), dentro de `~/proyectos/kimun` —clon de `https://github.com/jpcosec/kimun`, rama `main`— instalar Babashka 1.13.219, Rust/cargo, `~/bin/memcap` y el pod tree-sitter; ejecutar lint, suite, oracle e include check; commitear y pushear cuatro evidencias. Repo: kimun. Límites: sin sudo, sin cambiar código del repo; `docs/coordinacion/validate.sh` y sus rutas `/home/jp/...` quedan fuera. Nodo ejecutable, no agregado.

## AMBIGUEDADES
1. «rustup adds cargo to PATH through ~/.cargo/env, which ~/.profile and ~/.bashrc must source»: no dice si hay que añadir la línea, crearla o solo verificar; obliga a inventar el método de edición y el contenido exacto.
2. «bin/kimun include check prints "ok" : true with every finding at 0»: no fija formato de salida ni cómo contar «every finding»; obliga a inventar el criterio de parseo/aceptación.
3. «Put the four files in runs/subagents/YYYY-MM-DD-toolchain-robocop2/ (the day of the run)»: no aclara si la fecha es la de inicio, fin o commit; obliga a inventar la fecha si el trabajo cruza medianoche.
4. «in the clone ~/proyectos/kimun»: presupone que existe y es reutilizable; no dice qué hacer si falta, tiene cambios locales o el remoto no es `origin`; obliga a inventar el procedimiento.
5. «con el TaskDoc, los rituales enrutados por el Board, las pills ligadas, los átomos ligados y los archivos que el TaskDoc nombra»: el bundle no aporta esos artefactos; obliga a inventar su contenido o a ignorarlos.

## DRY-RUN
Lecturas: `release.edn`, `bb.edn`, baseline `full-test.txt`, `docs/v2/drafts/traspaso-2026-11-17.md`, `AGENTS.md`. Comprobaciones: ssh a robocop2, clon y rama, herramientas ausentes. Cambios previstos: instalar bb en `~/.local/bin/bb`; instalar rustup/cargo; crear `~/bin/memcap` exacto y ejecutable; asegurar `source ~/.cargo/env`; `bb pod` construye `pods/treesitter/target/release/pod-kimun-treesitter`; ejecutar los cuatro comandos a `lint.txt`, `full-test.txt`, `oracle.txt`, `include-check.txt`; crear `runs/subagents/2026-11-16-toolchain-robocop2/`; commit y push. Dependencias: red, GitHub, crates.io, python3, `systemd-run --user`, cargo. Validaciones futuras: exits 0, línea 2 exacta, última línea con `:fail 0`, `:error 0`, `:test >= 1081`, include check `ok: true` y findings 0. Sin ejecutar.

## FUTURO
Humano u otro nodo: aportar rituales/pills/átomos/archivos ausentes; decidir la fecha si cruza el día; ajustar `validate.sh` en la tarea siguiente; aprobar commit/push y aceptar la evidencia; decidir si se reutiliza o recrea el clon.
