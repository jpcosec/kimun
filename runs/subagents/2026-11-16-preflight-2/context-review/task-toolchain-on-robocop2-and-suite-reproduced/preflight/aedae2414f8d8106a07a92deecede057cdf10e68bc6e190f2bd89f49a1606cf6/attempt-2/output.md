REFORMULACION

Encargo: dejar operativa la máquina remota robocop2 (ssh host robocop2, usuario robocop) para ejecutar la suite de kimun. Propósito: ese host carece de bb, cargo y memcap, por lo que hoy no corre ningún test. Resultado: en el clon ~/proyectos/kimun de https://github.com/jpcosec/kimun (rama main), 'bb test' corre con el pod tree-sitter disponible, sin fallos, con ≥1081 tests; además se commitean y pushean cuatro salidas de evidencia en runs/subagents/<fecha>-toolchain-robocop2/. Se instalan sin sudo: Babashka 1.13.219 en ~/.local/bin/bb, Rust stable vía rustup, ~/bin/memcap con el script dado, y el binario pods/treesitter/target/release/pod-kimun-treesitter. Límites: no se modifica código del repo; quedan fuera los paths /home/jp/... de docs/coordinacion/validate.sh.

AMBIGUEDADES

1. "Copy the four outputs" — solo se nombran tres salidas (bb test, bb oracle, bin/kimun include check). Obliga a inventar cuál es la cuarta.
2. "Rust stable through rustup, so cargo is on PATH" — no dice cómo persistir el PATH (¿~/.profile, ~/.bashrc?). Obliga a inventar el mecanismo.
3. "memcap is a 5-line wrapper" frente al script de 3 líneas dado. Obliga a decidir cuál prevalece o si faltan líneas.
4. "at least the 1081 tests of the baseline ... commit e84869af" junto a "git pull --ff-only": no define qué hacer si HEAD difiere del baseline. Obliga a decidir el criterio.
5. "runs/subagents/<today>-toolchain-robocop2/" — formato de fecha no especificado. Obliga a elegir uno (p. ej. 2026-11-16).
6. "the value of :bb in release.edn" — si el pull cambia release.edn, versión y archivo pueden diferir. Obliga a decidir qué instalar.

DRY-RUN

Lecturas: release.edn, bb.edn, baseline full-test.txt, AGENTS.md, docs de traspaso. Cambios previstos: instalar bb, rustup, crear memcap, compilar pod, generar /tmp/full-test.txt y salidas oracle/include, crear dir de evidencias, commit + push a origin main. Dependencias: acceso ssh, red para descargas/crates, python3, credenciales git. Validaciones futuras: línea 2 "tree-sitter pod: available"; última línea :fail 0, :error 0, :test ≥1081; bb oracle exit 0; include check "ok": true.

FUTURO

Decide un humano u otro nodo: qué es la cuarta salida; si se edita el perfil para el PATH; commit de HEAD frente a e84869af; autorización del push a main; qué tarea posterior ajusta validate.sh.
