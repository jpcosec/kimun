# knowledge — Distribución y topología (pista S, §H y §A del plan)

> Cómo se construye, empaqueta, publica e instala `knowledge`; y el procedimiento de
> topología de repos que lo hace posible (repo propio, congelación de v1, deprecación de
> kgdb, split futuro de `provenance`). CLI y envelope en `06`; estado en `05`. Hito S0
> (empaquetado) con lo que llega en S6 (wheels y cliente Python) marcado como tal.

## 1. Topología de repositorios (procedimiento)

| paso | acción | estado |
|---|---|---|
| A1 | Un humano crea `github.com/jpcosec/knowledge` vacío (gate de publicación, §6). `git clone -b refactor-target git@github.com:jpcosec/sldb.git tools/knowledge && git branch -m main && git remote set-url origin git@github.com:jpcosec/knowledge.git && git push -u origin main --tags`. La rama era un worktree del mismo repo: clonarla **preserva los 140 commits** sin `filter-repo` | S0 |
| A2 | En `tools/sldb`: tags `v1-frozen` (sobre `main`) y `v2-seed-2026-09` (sobre `refactor-target`); banner en su README: *frozen, superseded by knowledge*; `git worktree remove tools/sldb-refactor-worktree` tras confirmar que `tools/knowledge` tiene todo. `iso-lab/worktrees/sldb` se mantiene (pina v1). No se borra ninguna rama | S0 |
| A3 | kgdb: tag `v1-frozen`, CLI deprecado. Los contratos pydantic (`contracts/{base,node,io}.py`, `query/language.py`) se copian **verbatim** a `python/knowledge/graph/`; `graph.py` se reimplementa sin networkx (extra opcional `knowledge[graph]`). Lo muerto no se copia | S6 |
| A4 | `legos/knowledge` → `provenance`: se van `src/knowledge`, `tests/`, `pyproject.toml`, los 4 specs del core anclado (condensados en `09`) y los 13 anchors (→ `resources/grammar/` de este repo, gramática por defecto). Se quedan los 296 atoms (`atoms/`, `impl:here` → `impl:knowledge`), specs de dominio, `source/knar`, `reviews`, `views/`, `desk/`. Su `.sldb/` v1 se conserva hasta `knowledge migrate --from-v1` | S7 |

Regla: `legos/knowledge` **no se toca** durante S0–S6; S2 y S4 se prueban sobre una migración
de ese repo a un directorio temporal (309 docs), nunca in place.

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
| `bb cli -- <args>` | ejecuta `knowledge.cli.main/-main` desde `src/` (desarrollo; `bin/knowledge` es su envoltorio) |
| `bb jar` | llama al `bb uberjar` **nativo** (`--classpath src:resources`, main `knowledge.cli.main`); la task no puede llamarse `uberjar`: sombrearía el builtin y recursaría |
| `bb release [--platforms p…] [--local-bb] [--pin]` | `scripts/release.clj`: descarga el `bb` pinado por plataforma y comprueba su sha256 (o usa el local con `--local-bb`, para tests), monta `dist/`, comprime y firma con `SHA256SUMS`; `--pin` reescribe los sha256 de `release.edn` |
| `bb wheel` | S6: construye los wheels de §7 |

## 4. Layout de release y launcher

```
dist/knowledge-2.0.0-alpha.1-linux-amd64/
  bin/knowledge          ; launcher POSIX
  bin/knowledge.cmd      ; launcher Windows
  lib/bb                 ; babashka pinado (bb.exe en windows)
  lib/knowledge.jar      ; uberjar
  VERSION                ; 2.0.0-alpha.1
  LICENSE
dist/knowledge-2.0.0-alpha.1-linux-amd64.tar.gz     (.zip en windows)
dist/SHA256SUMS
```

```sh
#!/bin/sh
DIR="$(cd "$(dirname "$0")/.." && pwd)"
exec "$DIR/lib/bb" --jar "$DIR/lib/knowledge.jar" -- "$@"
```

- El `--` es obligatorio: sin él `bb` se queda con `--version`, `--help` y `version` y responde por sí mismo.
- El launcher no toca `PATH` ni variables: un tarball descomprimido en cualquier ruta
  funciona en una máquina sin `bb` (DoD de S0; test `release_test/tarball-runs-without-bb-on-path`).
- `resources/VERSION` es la única fuente de la versión: `knowledge --version` la lee del
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
| windows-amd64 | `…-windows-amd64.zip` | zip | launcher `bin/knowledge.cmd` |

Los sha256 se fijan una vez con `bb release --pin` contra los assets de GitHub de babashka y
se revisan en el diff del commit; un asset cuyo digest no coincide aborta el build.

## 6. Workflows y gobernanza

- `.github/workflows/ci.yml` (push y PR): `bb lint && bb test && bb oracle`; desde S6,
  además `pytest python/` contra el jar recién construido.
- `.github/workflows/release.yml` (tag `v*`): job `build` construye los cinco tarballs y
  `SHA256SUMS` como artefactos **automáticamente**; job `publish` crea la GitHub Release y
  (S6) sube los wheels, y está protegido con `environment: release`, cuya única regla es un
  *required reviewer* humano.
- Por qué el gate: `core/knowledge/protocols/house_rules.md:63` (ID-5) exige aprobación
  humana para lo que sale de la topología —*published artifacts* incluidos—; el build es
  reversible, la publicación no.
- El repo nuevo también es un artefacto publicado: A1 lo crea un humano.

## 7. Instalación local, iso-lab y lo que llega en S6

**Local (hoy).** `pip uninstall knowledge` quita el editable instalado desde
`iso-lab/worktrees/knowledge` (era el que dejaba `knowledge-cli` roto en PATH; el nombre
`knowledge` estaba libre). Descomprimir el tarball y enlazar `bin/knowledge` en `~/.local/bin`
o usar `tools/knowledge/bin/knowledge` (dev, requiere `bb` local). `sldb` v1 **sigue en
PATH** y `.sldb/` coexiste con `.knowledge/` hasta S7.

**iso-lab.** `docker/` instala el `bb` pinado por `release.edn` (mismo sha256) en la imagen
base; `manifest.yaml` ya lista `knowledge` con baseline 0 (sldb 426, kgdb 28, deskops 210
según el manifest actual); S6 eleva `knowledge` al número de tests del cliente y sustituye
el editable Python por el tarball.

**S6.** Wheels por plataforma (`knowledge-2.0.0a1-py3-none-manylinux_x86_64.whl`, …) con el
bundle en `knowledge/_bin/<plat>/{bb, knowledge.jar}` (precedente: `ruff`, `nodejs-bin`)
más un wheel `any` que exige `knowledge` en PATH. `_launcher.py` resuelve
`$KNOWLEDGE_BIN` > `_bin/<plat>/` > PATH; `client.py`, `errors.py`, `models.py`
(`StructuredNLDoc` compatible + `descriptor()` + `python -m knowledge.models export`),
`graph/` (contratos de kgdb). Hasta entonces el contrato es `subprocess` + envelope (`06 §9`).
