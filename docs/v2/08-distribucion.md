# kimun — Distribución y topología (pista S, §H y §A del plan)

> Cómo se construye, empaqueta, publica e instala `kimun`; y el procedimiento de
> topología de repos que lo hace posible (repo propio, congelación de v1, deprecación de
> kgdb, split futuro de `pron`). CLI y envelope en `06`; estado en `05`. Hito S0
> (empaquetado) con lo que llega en S6 (wheels y cliente Python) marcado como tal.

## 0. Tres cosas distintas, tres nombres (2026-09-07)

| nombre | qué es | repo | pip / binario |
|---|---|---|---|
| **`kimun`** (Mapudungun *kimün*, saber) | este producto: kernel SLDB v2 + modelos + evaluador anclado, en Clojure/bb | `jpcosec/kimun`, local `~/proyectos/kimun` | binario `kimun`, store `.kimun/`, `$KIMUN_STORE`; pip `kimun` (S6) |
| **`knowledge`** v1 | herramienta Python: evaluador anclado s-expr sobre `.sldb` v1 + kgdb, con sus ops de escritura. **Viva**, se desarrolla en paralelo | `jpcosec/knowledge` (rama `master`), local `legos/knowledge` | pip `knowledge` (editable), script `knowledge-cli` |
| **`pron`** (Mapudungun, el cordel anudado de registro) | la base de conocimiento de provenance: 296 atoms, specs de dominio, `source/knar`, `reviews`, `views`, `desk`, su `.sldb/`. Datos, no herramienta: hoy la opera `knowledge` v1, después `kimun` | `jpcosec/pron`; hoy comparte repo con `knowledge` v1 hasta S7 | ninguno |

Reglas: `knowledge` no es un nombre de producto de v2 en ningún sitio (el kernel se llama
`sldb.*`, la superficie `kimun.*`); el paquete Python `knowledge` es de v1 mientras v1 viva,
así que el cliente Python de S6 se llama `kimun`; S4 porta la **lectura** de v1 a `kimun`,
pero v1 no se congela por eso: coexisten sobre `pron` hasta que el usuario decida.

Lo que pasó el 2026-09-07: el producto se llamó `knowledge` durante S0 y se pusheó a
`jpcosec/knowledge`, que ya era el remoto de v1 (rama `master`). Se deshizo el mismo día:
tags y environment eliminados, rama por defecto de vuelta a `master`, la rama `main` de v2
se borra a mano y v2 vive en `jpcosec/kimun`.

## 1. Topología de repositorios (procedimiento)

| paso | acción | estado |
|---|---|---|
| A1 | Un humano crea `github.com/jpcosec/kimun` vacío (gate de publicación, §6). `git clone -b refactor-target git@github.com:jpcosec/sldb.git tools/kimun && git branch -m main && git remote set-url origin git@github.com:jpcosec/kimun.git && git push -u origin main --tags`. La rama era un worktree del mismo repo: clonarla **preserva los 140 commits** sin `filter-repo`. Tras el push: environment `release` con *required reviewer* humano | S0 |
| A2 | En `tools/sldb`: tags `v1-frozen` (sobre `main`) y `v2-seed-2026-09` (sobre `refactor-target`); banner en su README: *frozen, superseded by kimun*; `git worktree remove tools/sldb-refactor-worktree` tras confirmar que `tools/kimun` tiene todo. `iso-lab/worktrees/sldb` se mantiene (pina v1). No se borra ninguna rama | S0 |
| A3 | kgdb: tag `v1-frozen`, CLI deprecado. Los contratos pydantic (`contracts/{base,node,io}.py`, `query/language.py`) se copian **verbatim** a `python/kimun/graph/`; `graph.py` se reimplementa sin networkx (extra opcional `kimun[graph]`). Lo muerto no se copia | S6 |
| A4 | split de `legos/knowledge` en dos repos (§0): la herramienta `knowledge` v1 (`src/knowledge`, `tests/`, `pyproject.toml`, sus specs del core anclado) conserva `jpcosec/knowledge`; la KB va a `jpcosec/pron` (296 atoms con `impl:here` → `impl:kimun`, specs de dominio, `source/knar`, `reviews`, `views/`, `desk/`, `.sldb/` v1 hasta `kimun migrate --from-v1`). Los 4 specs del core anclado se condensan en `09` y los 13 anchors se copian a `resources/grammar/` de este repo como gramática por defecto; **no se borra código de v1**: v1 sigue viva | S7 |

Regla: `legos/knowledge` **no se toca desde aquí** durante S0–S6 (v1 se desarrolla en
paralelo por su cuenta); S2 y S4 se prueban sobre una migración de ese repo a un directorio
temporal (309 docs), nunca in place.

## 2. Toolchain

| hecho | valor |
|---|---|
| host del CLI | **Babashka, definitivo** (`05 §3` decisión 14). El kernel sigue siendo `.cljc`; la paridad Node queda en el drawer para la UI |
| versión pinada | `bb 1.13.219` (la instalada localmente; `release.edn` la fija) |
| JVM | **no necesaria** ni para construir ni para ejecutar: `bb uberjar` y `bb --jar` existen en esa versión y están verificados localmente |
| dependencias externas | ninguna en S0–S3: `babashka.cli`, `cheshire`, `clj-yaml`, `http-kit` vienen embebidos en bb |
| Python | solo para `scripts/canon_oracle.py` (`bb oracle`) y, desde S6, el cliente |

## 3. Tareas de `bb.edn`

| task | qué hace |
|---|---|
| `bb lint` | anillos + docstrings + regla "sin `def` mutable en anillo 0" (`06 §2`) |
| `bb test` / `bb oracle` | suite y oráculo (`05 §2`) |
| `bb cli -- <args>` | ejecuta `kimun.cli.main/-main` desde `src/` (desarrollo; `bin/kimun` es su envoltorio) |
| `bb jar` | llama al `bb uberjar` **nativo** (`--classpath src:resources`, main `kimun.cli.main`); la task no puede llamarse `uberjar`: sombrearía el builtin y recursaría |
| `bb release [--platforms p…] [--out d] [--jar j] [--local-bb] [--pin]` | `scripts/release.clj`: reconstruye el jar (`bb jar`) salvo con `--jar`; descarga el `bb` pinado por plataforma y comprueba su sha256 (o usa el local con `--local-bb`, para tests), monta `dist/`, comprime y firma con `SHA256SUMS`; `--pin` reescribe los sha256 de `release.edn` |
| `bb wheel` | S6: construye los wheels de §7 |

## 4. Layout de release y launcher

```
dist/kimun-2.0.0-alpha.1-linux-amd64/
  bin/kimun          ; launcher POSIX
  bin/kimun.cmd      ; launcher Windows
  lib/bb                 ; babashka pinado (bb.exe en windows)
  lib/kimun.jar      ; uberjar
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
exec "$DIR/lib/bb" --jar "$DIR/lib/kimun.jar" -- "$@"
```

- Instalación típica: `ln -s <bundle>/bin/kimun ~/.local/bin/kimun`; el launcher resuelve el enlace (test `release_test`, caso symlink).
- El `--` es obligatorio: sin él `bb` se queda con `--version`, `--help` y `version` y responde por sí mismo.
- El launcher no toca `PATH` ni variables: un tarball descomprimido en cualquier ruta
  funciona en una máquina sin `bb` (DoD de S0; test `release_test/tarball-runs-without-bb-on-path`).
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

- `.github/workflows/ci.yml` (push y PR): `bb lint && bb test && bb oracle`; desde S6,
  además `pytest python/` contra el jar recién construido.
- `.github/workflows/release.yml` (tag `v*`): job `build` construye los cinco tarballs y
  `SHA256SUMS` como artefactos **automáticamente**; job `publish` crea la GitHub Release y
  (S6) sube los wheels, y está protegido con `environment: release`, cuya única regla es un
  *required reviewer* humano.
- Por qué el gate: `core/kimun/protocols/house_rules.md:63` (ID-5) exige aprobación
  humana para lo que sale de la topología —*published artifacts* incluidos—; el build es
  reversible, la publicación no.
- El repo nuevo también es un artefacto publicado: A1 lo crea un humano.

## 7. Instalación local, iso-lab y lo que llega en S6

**Local (hoy).** El editable pip `knowledge` (v1, desde `legos/knowledge`) **se queda**: es
otra herramienta (§0) y no choca con nada de `kimun`. Descomprimir el tarball y enlazar
`bin/kimun` en `~/.local/bin` o usar `~/proyectos/kimun/bin/kimun` (dev, requiere `bb` local).
`sldb` v1 **sigue en PATH** y `.sldb/` coexiste con `.kimun/` hasta S7.

**iso-lab.** `docker/` instala el `bb` pinado por `release.edn` (mismo sha256) en la imagen
base; `manifest.yaml` lista hoy `knowledge` (v1, baseline 0) junto a sldb 426, kgdb 28 y
deskops 210; S6 añade `kimun` con el número de tests del cliente y sustituye el editable
Python de sldb por el tarball. El repo vive fuera de `hum-ecosystem` (`~/proyectos/kimun`).

**S6.** Wheels por plataforma (`kimun-2.0.0a1-py3-none-manylinux_x86_64.whl`, …) con el
bundle en `kimun/_bin/<plat>/{bb, kimun.jar}` (precedente: `ruff`, `nodejs-bin`)
más un wheel `any` que exige `kimun` en PATH. `_launcher.py` resuelve
`$KIMUN_BIN` > `_bin/<plat>/` > PATH; `client.py`, `errors.py`, `models.py`
(`StructuredNLDoc` compatible + `descriptor()` + `python -m kimun.models export`),
`graph/` (contratos de kgdb). Hasta entonces el contrato es `subprocess` + envelope (`06 §9`).
