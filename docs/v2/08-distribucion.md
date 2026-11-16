# kimun — Distribución y topología

> Cómo se construye, empaqueta, publica e instala `kimun`; y la topología de repos que lo
> hace posible. CLI y envelope: `06`; estado y decisiones: `05`. Lo previsto pero no
> implementado se marca como pendiente, sin verbos en presente.

## 0. Tres cosas distintas, tres nombres (2026-09-07)

| nombre | qué es | repo | pip / binario |
|---|---|---|---|
| **`kimun`** (Mapudungun *kimün*, saber) | este producto: kernel SLDB v2 y modelos, en Clojure/bb | `jpcosec/kimun`, local `~/proyectos/kimun` | binario `kimun`, store `.kimun/`, `$KIMUN_STORE`; no hay cliente pip `kimun` (`C2` retirada el 2026-11-15) |
| **`pron`** (Mapudungun, el cordel anudado de registro) | la base de conocimiento de provenance **y** el evaluador que la opera, en un paquete Python que depende de `sldb` y `kgdb`. **Vivo**: lo importa `kinesis` | `jpcosec/pron`, local `legos/pron` | pip `pron`, binario `pron` |
| **`knowledge`** | congelado 2026-09-07: solo el CLI de julio sobre archivos (`knowledge_legacy.py`) y la rama `iso-lab/knowledge` | `jpcosec/knowledge` (`master`), local `hum-ecosystem/tools/knowledge` | ninguno |

Reglas: `knowledge` no es un nombre de producto en ningún sitio (el kernel se llama
`kimun.kernel.*` —antes `sldb.*`—, la superficie `kimun.*`); el paquete Python instalable de v1 es `pron`. No hay cliente
Python `kimun` todavía (`C2` diferida el 2026-11-15 hasta que kimun esté probado, decisión 47): el repositorio no contiene código Python
(salvo el oráculo `scripts/canon_oracle.py`).

Lo que pasó el 2026-09-07: el producto se llamó `knowledge` durante la fase de empaquetado y
se pusheó a `jpcosec/knowledge`, que ya era el remoto de v1 (rama `master`). Se deshizo el
mismo día: tags y environment eliminados, rama por defecto de vuelta a `master`, la rama
`main` de v2 se borra a mano y v2 vive en `jpcosec/kimun`.

## 1. Topología de repositorios (procedimiento)

| paso | acción | estado |
|---|---|---|
| A1 | Un humano crea `github.com/jpcosec/kimun` vacío (gate de publicación, §6). `git clone -b refactor-target git@github.com:jpcosec/sldb.git tools/kimun && git branch -m main && git remote set-url origin git@github.com:jpcosec/kimun.git && git push -u origin main --tags`. La rama era un worktree del mismo repo: clonarla **preserva los 140 commits** sin `filter-repo`. Tras el push: environment `release` con *required reviewer* humano | hecho (S0) |
| A2 | En `tools/sldb`: tags `v1-frozen` (sobre `main`) y `v2-seed-2026-09` (sobre `refactor-target`); banner en su README: *frozen, superseded by kimun*; `git worktree remove tools/sldb-refactor-worktree` tras confirmar que `tools/kimun` tiene todo. `iso-lab/worktrees/sldb` se mantiene (pina v1). No se borra ninguna rama | hecho (S0) |
| A3 | kgdb: tag `v1-frozen`, CLI deprecado. Previsto: copiar los contratos pydantic (`contracts/{base,node,io}.py`, `query/language.py`) a `python/kimun/graph/` y reimplementar `graph.py` sin networkx. Hoy no existe `python/`. Lo muerto no se copia | pendiente (S6) |
| A4 | Previsto: copiar los 13 anchors de `pron` a `resources/grammar/` como gramática por defecto y traer `pron/.sldb` (el comando `migrate --from-v1` se borró el 2026-11-15). Hoy `resources/` no tiene `grammar/`. **No se borra código de `pron`**: sigue vivo | pendiente (S7) |

Regla: `pron` **no se toca desde aquí** (se desarrolla en paralelo, con `kinesis` como
consumidor); la migración se prueba sobre una copia en un directorio temporal, nunca in place.

## 2. Toolchain

| hecho | valor |
|---|---|
| host del CLI | **Babashka, definitivo** (`05` decisión 14). El kernel sigue siendo `.cljc`; la paridad Node queda en el drawer para la UI |
| política de lenguajes | decisión 19 (`05`): la lógica de alto nivel en Clojure (kernel `.cljc`, superficies, CLI); servicios nativos (Rust, C, Zig, Go…) solo si son portables y empaquetables por plataforma; plugins en cualquier lenguaje. Sustituye la decisión 8 («cero Rust», `02 §8`). Frontera documentada: adaptadores fuera de proceso (`11`) |
| versión pinada | `bb 1.13.219` (la instalada localmente; `release.edn` la fija) |
| JVM | **no necesaria** ni para construir ni para ejecutar: `bb uberjar` y `bb --jar` existen en esa versión y están verificados localmente |
| dependencias externas | ninguna: `babashka.cli`, `cheshire`, `clj-yaml`, `http-kit` vienen embebidos en bb |
| Python | solo para `scripts/canon_oracle.py` (`bb oracle`); no hay cliente Python todavía (`C2` diferida el 2026-11-15, decisión 47) |

## 3. Tareas de `bb.edn`

| task | qué hace |
|---|---|
| `bb lint` | anillos + docstrings + regla "sin `def` mutable en anillo 0" (`scripts/check_rings.clj`) |
| `bb test` | suite del kernel en Babashka (clojure.test + test.check); depende de `lint` |
| `bb oracle` | oráculo independiente en Python (`scripts/canon_oracle.py`) |
| `bb store-size [DIR]` | bytes de objeto por clase, log y total de un store (`scripts/store_size.clj`) |
| `bb cli <args>` | ejecuta `kimun.cli.main/-main` desde `src/` (desarrollo; `bin/kimun` es su envoltorio) |
| `bb jar` | llama al `bb uberjar` **nativo** (`--classpath src:resources`, main `kimun.cli.main`) y estampa `resources/git-sha`; la task no puede llamarse `uberjar`: sombrearía el builtin y recursaría |
| `bb desk` | alias fino de `kimun include build`: construye `.kimun/` desde `kimun.include.edn` |
| `bb pod` | `cargo build --release` del pod tree-sitter en `pods/treesitter/` (capa M, decisión 29); el pod se descubre en runtime (`KIMUN_POD`, `lib/`, dev) |
| `bb release [--platforms p…] [--out d] [--jar j] [--local-bb] [--pin]` | `scripts/release.clj`: reconstruye el jar (`bb jar`) salvo con `--jar`; descarga el `bb` pinado por plataforma y comprueba su sha256 (o usa el local con `--local-bb`, para tests), monta `dist/`, comprime y firma con `SHA256SUMS`; `--pin` reescribe los sha256 de `release.edn` |

## 4. Layout de release y launcher

```
dist/kimun-2.0.0-alpha.1-linux-amd64/
  bin/kimun          ; launcher POSIX
  bin/kimun.cmd      ; launcher Windows
  lib/bb                 ; babashka pinado (bb.exe en windows)
  lib/kimun.jar      ; uberjar
  lib/pod-kimun-treesitter   ; pod tree-sitter de la capa M (.exe en windows)
  VERSION                ; 2.0.0-alpha.1
  LICENSE
dist/kimun-2.0.0-alpha.1-linux-amd64.tar.gz     (.zip en windows)
dist/SHA256SUMS
```

```sh
#!/bin/sh
SELF="$0"                                   # sigue symlinks: ~/.local/bin/kimun -> <bundle>/bin/kimun
while [ -h "$SELF" ]; do
  LINKDIR="$(cd "$(dirname "$SELF")" && pwd)"
  SELF="$(readlink "$SELF")"
  case "$SELF" in /*) ;; *) SELF="$LINKDIR/$SELF" ;; esac
done
DIR="$(cd "$(dirname "$SELF")/.." && pwd)"
KIMUN_POD="$DIR/lib/pod-kimun-treesitter"; export KIMUN_POD
exec "$DIR/lib/bb" --jar "$DIR/lib/kimun.jar" -- "$@"
```

- El pod se resuelve por `KIMUN_POD` (el launcher lo apunta a `lib/`). Un bundle lleva el pod
  compilado **para su plataforma** o el build falla (exit 2, dice qué falta): las únicas fuentes
  son `target/pods/<plataforma>/` (staging de CI) y, solo si la plataforma es la de esta máquina,
  el build de desarrollo; nunca el binario de otra plataforma. `--without-pod` construye un
  bundle sin pod a propósito: su `MANIFEST.edn` dice `:pod nil` y su launcher se niega a correr
  con `KIMUN_PARSER=treesitter`. `MANIFEST.edn` registra siempre el sha256 de lo empaquetado;
  `release.edn` `:pods` `:sha256` se verifica solo si no es `nil` (hoy lo es: ningún pod
  publicado). `bb release` hace smoke de `version` a través del pod empaquetado (solo en la
  plataforma de la máquina). `--local-bb` solo vale para la plataforma de la máquina.

- Instalación típica: `ln -s <bundle>/bin/kimun ~/.local/bin/kimun`; el launcher resuelve el enlace (test `release_test`, caso symlink).
- El `--` es obligatorio: sin él `bb` se queda con `--version`, `--help` y `version` y responde por sí mismo.
- El launcher no toca `PATH` ni variables: un tarball descomprimido en cualquier ruta
  funciona en una máquina sin `bb` (test `release_test/tarball-runs-without-bb-on-path`).
- `resources/VERSION` es la única fuente de la versión: `kimun --version` la lee del
  classpath y `bb release` la copia a `VERSION`; un desajuste con `release.edn` aborta el release.
- Arranque medido en `release_test`: < 500 ms para `--version`.

## 5. `release.edn` y matriz de plataformas

```clojure
{:version   "2.0.0-alpha.1"
 :bb        "1.13.219"
 :platforms [:linux-amd64 :linux-aarch64 :macos-amd64 :macos-aarch64 :windows-amd64]
 :sha256    {:linux-amd64 "…" :linux-aarch64 "…" :macos-amd64 "…" :macos-aarch64 "…" :windows-amd64 "…"}}
```

| plataforma | asset de bb | archivo | notas |
|---|---|---|---|
| linux-amd64 | `babashka-<v>-linux-amd64-static.tar.gz` | tar.gz | la que prueba CI |
| linux-aarch64 | `…-linux-aarch64-static.tar.gz` | tar.gz | |
| macos-amd64 | `…-macos-amd64.tar.gz` | tar.gz | |
| macos-aarch64 | `…-macos-aarch64.tar.gz` | tar.gz | |
| windows-amd64 | `…-windows-amd64.zip` | zip | launcher `bin/kimun.cmd` |

Los sha256 se fijan una vez con `bb release --pin` contra los assets de GitHub de babashka y
se revisan en el diff del commit; un asset cuyo digest no coincide aborta el build.

## 6. Workflows y gobernanza

- `.github/workflows/ci.yml` (push y PR): `bb lint`, `bb test`, `bb oracle`, smoke del
  uberjar (`bb jar` y `bb --jar target/kimun.jar --version`) y smoke del bundle local
  (`bb release --local-bb --platforms linux-amd64`, descomprimir y correr `--version`).
- `.github/workflows/release.yml` (tag `v*`): job `build` corre `bb lint && bb test` (sin
  oracle), exige que el tag sea `v$(resources/VERSION)`, compila el pod linux-amd64 (job `pod-linux-amd64`), construye el bundle linux-amd64 (las demás
  plataformas no se publican: sin pod ni prueba) y
  `SHA256SUMS` con `bb release --platforms linux-amd64 --out dist` y los sube como artefacto; job `publish` crea la
  GitHub Release con `bundles` y `SHA256SUMS`, y está protegido con `environment: release`,
  cuya única regla es un *required reviewer* humano.
- Por qué el gate: la regla ID-5 de `hum-ecosystem`
  (`core/knowledge/protocols/house_rules.md:63`) exige aprobación
  humana para lo que sale de la topología —*published artifacts* incluidos—; el build es
  reversible, la publicación no.
- El repo nuevo también es un artefacto publicado: A1 lo crea un humano.

## 7. Instalación local y pendientes

**Local (hoy).** Descomprimir el tarball y enlazar `bin/kimun` en `~/.local/bin`, o usar
`~/proyectos/kimun/bin/kimun` (dev, requiere `bb` local). El editable pip `pron` (desde
`legos/pron`) **se queda**: es otra herramienta (§0) y no choca con nada de `kimun`.
`sldb` v1 **sigue en PATH** y `.sldb/` coexiste con `.kimun/`.

**iso-lab (repo aparte).** Previsto: el `bb` pinado por `release.edn` (mismo sha256) en la
imagen base y `kimun` en su `manifest.yaml`; este checkout no tiene `docker/` ni
`manifest.yaml`.

**Diferido (2026-11-15, decisión 47 del dueño; `C2` en `12 §1`), no implementado.** Va después de `RS1` y `RS2`, y su forma la fija `RS2`, no este párrafo. El diseño anterior de un cliente Python `kimun` era: wheels por plataforma con el
bundle en `kimun/_bin/<plat>/{bb, kimun.jar}`, más un wheel `any` que exige `kimun` en
PATH; un `_launcher.py` que resuelve `$KIMUN_BIN` > `_bin/<plat>/` > PATH; y
`client.py`/`errors.py`/`models.py`/`graph/` (contratos de kgdb). Nada de eso existe
(el directorio `python/` se borró el 2026-11-15). Hasta entonces el contrato es
`subprocess` + envelope (`06`).
