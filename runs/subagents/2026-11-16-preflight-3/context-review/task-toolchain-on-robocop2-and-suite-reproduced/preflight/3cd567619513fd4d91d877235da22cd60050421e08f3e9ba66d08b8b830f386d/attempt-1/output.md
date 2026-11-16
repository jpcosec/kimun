REFORMULACION

Tarea externa ejecutable, no agregado. Propósito: dejar robocop2 capaz de ejecutar la suite de kimun con el pod tree-sitter. Resultado: en host robocop2, usuario robocop, clon ~/proyectos/kimun de https://github.com/jpcosec/kimun (main), `bb test` sin fallos y con >=1081 tests; cuatro evidencias en `runs/subagents/YYYY-MM-DD-toolchain-robocop2/` commiteadas y pusheadas a origin main. Límites: sin sudo, sin cambiar código; instalar bb 1.13.219 en `~/.local/bin/bb`, Rust stable/cargo, `~/bin/memcap` exacto y construir el pod; ejecutar lint, test con `memcap 3`, oracle e include check. Fuera de alcance: rutas `/home/jp/...` de `docs/coordinacion/validate.sh`.

AMBIGUEDADES

1. “Install the three tools” enumera después cuatro elementos, incluido el pod. Obligaría a inventar si el pod se “instala”, se construye o solo se verifica.
2. “Rust stable through rustup” no fija comando, perfil ni target; obligaría a inventar flags y configuración de instalación.
3. “~/.cargo/env, which ~/.profile and ~/.bashrc must source” no da líneas exactas ni orden/idempotencia; obligaría a inventar la edición de esos archivos.
4. “commit them with the message” y “push to origin main” no definen identidad Git ni autenticación si no están configuradas; obligaría a inventar configuración o pedir credenciales.

DRY-RUN

Leería el task doc, `AGENTS.md`, `release.edn` y `bb.edn` tras `git pull --ff-only`, y el full-test baseline. Crearía `~/.local/bin/bb`, instalaría rustup/cargo, `~/bin/memcap`, construiría `pods/treesitter/target/release/pod-kimun-treesitter` con `bb pod`, generaría `lint.txt`, `full-test.txt`, `oracle.txt`, `include-check.txt` y haría commit/push. Dependencias: SSH, red para archive/crates, `systemd --user`, python3, permisos de push. Validaría exit 0 de lint/oracle; línea 2 “tree-sitter pod: available”; última línea fail 0, error 0, test >=1081; include “ok” true y hallazgos 0; evidencias en main.

FUTURO

Un nodo posterior ajusta las rutas `/home/jp/...` de `validate.sh`. El coordinador o un humano verifica las cinco validaciones y acepta el commit; si falta autenticación SSH/Git, la habilita.
