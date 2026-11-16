# release-pod — el pod entra en el bundle y la cadena de release dice la verdad

## Hallazgos de la exploración, verificados
- No existe ninguna release: CIERTO (el workflow corre solo con tag `v*`; la documentación y el README lo decían; nada que ejecutar aquí).
- `:sha256 nil` en las 5 plataformas de `release.edn`: CIERTO (sigue así a propósito, ver "Hash").
- Ningún job de CI compila el pod: CIERTO (`ci.yml` y `release.yml` no tenían cargo; `bb test` saltaba las pruebas de pod si faltaba).
- Los bundles publicados saldrían sin pod: CIERTO (`bb release` solo avisaba por stderr «bundled without it»).
- Respaldo que mete el pod de Linux en macOS/Windows: CIERTO y MATIZADO. `pod-source` caía al build de desarrollo para cualquier plataforma. Además había el mismo defecto con `bb`: `--local-bb` copiaba el `bb` de la máquina a cualquier plataforma pedida. Arreglado también.
- `bin/kimun.cmd` inconsistente: CIERTO, matizado: el release no usa ese archivo (escribe su propio `.cmd` con `KIMUN_POD`, que ya era consistente); el del repo era un huérfano sin `KIMUN_POD` que apuntaba a un `lib\bb.exe` inexistente en un checkout.
- Solo linux-amd64 probado: CIERTO.
- `release-test` ~55 s: CIERTO (ahora ~68 s: +1 build `--without-pod`, ~10 s, y la prueba de humo).
- Matiz importante: el pod es opcional en tiempo de ejecución (parser nativo por defecto; el pod solo se usa con `KIMUN_PARSER=treesitter`, o en `blocks` si está). Y `candidates` cae al build de desarrollo bajo el cwd/bb.edn: con `KIMUN_POD=/inexistente` y un pod de desarrollo presente, igual lo carga (respaldo silencioso de lectura; no lo toqué, el bundle corre sin `bb.edn`).

## Qué cambié
- `scripts/release.clj`: `host-platform`; `pod-source` devuelve `{:path :origin}` solo de `target/pods/<plat>/` (`:ci-staging`) o, únicamente si la plataforma es la de la máquina, del build de desarrollo (`:local-build`). Comprobación previa (antes de bajar o construir nada) con mensaje que dice qué falta (`bb pod` o staging). `--local-bb` solo vale para la plataforma de la máquina. Nuevo `--without-pod`. Nuevo `MANIFEST.edn` en el bundle (versión, plataforma, bb: versión/origen/sha256, pod: bin/sha256/origen/pinned/smoked, o `:pod nil`). Launchers parametrizados con/sin pod; el sin pod hace `unset KIMUN_POD` y sale con 2 y un mensaje claro si `KIMUN_PARSER=treesitter`. Smoke de `version` del pod solo en la plataforma de la máquina. `--pin` solo fija pods de CI-staging. Cabecera de `release.edn` unificada (R1 había anotado que `--pin` la acortaba).
- `release.edn`: solo el comentario de cabecera.
- `test/kimun/cli/release_test.cljc`: prueba de humo dentro de la prueba existente (reutiliza su bundle); 1 prueba nueva `a-bundle-never-carries-another-platforms-pod-nor-silently-no-pod` con raíz falsa. Total 5 pruebas, 79 aserciones, 0 fallos. Sin pod de desarrollo la parte de pod se salta con un mensaje impreso y el bundle se arma con `--without-pod` (en CI el pod se compila antes, así que corre).
- `.github/workflows/release.yml`: job `pod-linux-amd64` (cargo estático `--locked`, artefacto); `build` lo descarga a `target/pods/linux-amd64/` (y copia al camino de desarrollo para que `bb test` lo ejercite), construye solo `--platforms linux-amd64` y comprueba que el `MANIFEST.edn` declara el pod. `ci.yml`: paso `bb pod` antes de `bb lint` (el paso del bundle local ahora lo exige). Sin secretos ni destinos nuevos.
- `bin/kimun.cmd`: launcher de desarrollo equivalente a `bin/kimun`, con la nota «no probado».
- `README.md` (Install), `docs/v2/08-distribucion.md` (§4 viñeta del pod y §6 release.yml: eran falsos), `docs/v2/12` (una frase de avance).

## Regla única para plataformas sin pod
El bundle de una plataforma lleva el pod compilado para esa plataforma o el build falla (exit 2, nombra la ruta que falta). La única salida es `--without-pod`, explícito: el bundle lo declara (`MANIFEST.edn :pod nil`, mensaje en stderr) y su `bin/kimun` se niega (exit 2, mensaje) a correr con `KIMUN_PARSER=treesitter`. Ningún respaldo silencioso.

## Hash del pod
`release.edn :pods :sha256` queda `nil` (nadie ha publicado un pod; un binario compilado localmente no es reproducible entre máquinas, fijarlo sería mentir). Si estuviera fijado, se verifica (exit 3 si difiere). `MANIFEST.edn` registra siempre el sha256 de lo empaquetado (aquí `5ecf6172…c43809`, `bundle-contents.txt`). Determinismo R1: se mantiene (la prueba de dos corridas con jars distintos corre ahora con el pod dentro y pasa); pero el tarball depende de los bytes del pod, así que dos máquinas con pods distintos darán tarballs distintos. Para fijarlo hará falta publicar el pod de CI y correr `bb release --pin` con él en `target/pods/`.

## Evidencia
`bundle-contents.txt` (listado del tarball real, MANIFEST, sha256 del pod de origen y del empaquetado: iguales), `smoke.txt` (bundle extraído, `env -i`, sin `KIMUN_POD`: `stores init`, `docs ingest`, `docs render`, `docs marks` con grammar `pod-kimun-treesitter/0.1.0 tree-sitter/0.26`), `lint.txt` (105 archivos ok), `oracle.txt` (9 filas, 0 diferencias), `directed.txt` (release_test + lint_test: 11 pruebas, 114 aserciones, 0 fallos), `mutation.txt`: (m1) no empaquetar el pod → 3 fallos y 2 errores; (m2) pod de desarrollo para cualquier plataforma y sin la regla de `--local-bb` → 7 fallos. Script restaurado idéntico. Tarballs y temporales borrados; `dist/` está en `.gitignore`.

## No ejecutado / límites
- CI no ejecutado (YAML parseado con clj-yaml de bb: `ci` y `release` válidos). En la primera corrida mirar: que `cargo build --release --locked` con `RUSTFLAGS=+crt-static` y sin `--target` compile (proc-macros/build scripts con crt-static; es la receta del README del pod, no probada aquí), que `file` diga static-pie, que el toolchain `stable` se resuelva, la ruta del artefacto (`target-static/release/…`), que `bb test` en el job de release funcione con el pod estático copiado al camino de desarrollo (la prueba de release espera origen `:ci-staging`), y que el tiempo de `ci.yml` con `bb pod` sea aceptable.
- Pod usado aquí: copia del binario dinámico (libgcc/libc) de `/home/jp/proyectos/kimun/pods/treesitter/target/release/` al worktree (ignorado por git); el estático que publicaría CI no se ha construido ni probado.
- Solo linux-amd64 en la máquina; macOS, linux-aarch64 y Windows (zip, `.cmd`) sin construir ni probar. El zip de Windows sigue sin orden fijado (R1).
- `candidates` conserva su respaldo al build de desarrollo bajo el directorio de `bb.edn`/cwd (fuera de alcance).
- La prueba se salta la parte del pod si no existe el build de desarrollo.
- No commiteado; el worktree tiene el pod copiado en `pods/treesitter/target/release/` (ignorado).
