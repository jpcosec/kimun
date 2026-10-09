# R2 — tree-sitter como pod de Babashka para kimun (Markdown + código)

Estado: **completo**. Spike de investigación, sin cambios en ningún repo; todo vive en
`/home/jp/.cache/spikes/R2/`. Entorno: bb 1.13.219, rustc 1.95.0, tree-sitter 0.26.13,
tree-sitter-md 0.5.3, tree-sitter-python 0.25.0, linux-amd64.

Respuesta corta: **sí, viable; adoptar de forma incremental, no como "parser único".** El pod
es barato (19 ms de carga, 1,2 ms/documento, 3–4 MB, sin runtime externo) y tras una
normalización trivial (añadir `\n` final) segmenta **igual que `blocks.cljc` en 1099/1099
documentos**. Frente al AST SLDB-MD difiere solo donde SLDB-MD decide caer a `:opaque`. La
recomendación es que tree-sitter sea el parser de la capa **M** (segmentación de bloques y de
código), manteniendo el perfil cerrado SLDB-MD como una capa por encima; no que sustituya la
semántica de perfil ni el render canónico.

---

## 1. Qué se construyó

- **Pod** `pod-kimun-treesitter` (Rust, `pod/`): binario único que enlaza `tree-sitter` +
  `tree-sitter-md` (block **e** inline) + `tree-sitter-python`. Protocolo pod de babashka
  (bencode por stdin/stdout, payloads JSON) implementado a mano (`src/bencode.rs`, ~180 líneas);
  vars: `version`, `parse` (CST combinado block+inline), `parse-block`, `parse-inline`,
  `parse-lang`, `blocks` (lista plana de bloques de nivel superior, sin texto), `query`,
  `bench`. Los nodos salen como `{type,start_byte,end_byte,start_point,end_point,named,field?,missing?,error?,text?,children?}`.
- **Estáticas**: `RUSTFLAGS="-C target-feature=+crt-static"` produce un **ELF static-pie de
  4,1 MB** (probado: `version`, `blocks`, `parse-lang` funcionan). Sin flags, 3,1 MB dinámico
  contra libc/libgcc.
- **Elección de lenguaje: Rust.** Justificación: el crate `tree-sitter` 0.26 da el binding
  directo y los crates de gramática (`tree-sitter-md`, `tree-sitter-python`) **traen y compilan
  el C generado** (`parser.c`, scanner), así que no hay que vendorizar gramáticas a mano y
  `Cargo.lock` las pina; el resultado es un único ejecutable sin runtime. C/Zig obligarían a
  vendorizar y compilar a mano cada `parser.c`; Go usa cgo con bindings menos mantenidos.

### 1.1 Cómo se construye

```
cd pod && cargo build --release                     # 3,1 MB, dinámico
RUSTFLAGS="-C target-feature=+crt-static" CARGO_TARGET_DIR=target-static cargo build --release   # 4,1 MB, static-pie
```

## 2. Medidas (bb 1.13.219, linux-amd64)

| medida | valor | cómo |
|---|---|---|
| carga del pod (`pods/load-pod` + `describe`) | **18,8 ms** | `measure.clj` |
| llamada `version` (round-trip) | 0,26 ms | media de 200 |
| doc de 55 915 B — solo `parse` dentro del pod | **39,0 ms** | `bench.clj` |
| doc de 55 915 B — `parse` + CST JSON | 32,5 ms/pod | `bench.clj` |
| doc de 55 915 B — `blocks` end-to-end (bb→pod) | 44,3 ms | `measure2.clj` |
| 1000 documentos pequeños (471 696 B totales) | **1241 ms → 1,24 ms/doc** | `measure.clj` |
| RSS pico del pod, doc 56,8 KB `parse-block` + JSON | **13,3 MB** | `/usr/bin/time -v` |
| RSS pico del pod, doc 56,8 KB `blocks` | 6,4 MB (20 ms) | `/usr/bin/time -v` |
| RSS del pod arrancando (`version`) | 1,7 MB, <10 ms | `/usr/bin/time -v` |
| tamaño del JSON del CST para 56,8 KB | **838 KB (~15× la entrada)** | salida del pod |

Nota de memoria para la integración: el CST completo serializado es ~15× la entrada; para
segmentación usar `blocks` (60 KB de salida) y **no materializar el CST entero en bb**.

## 3. Paridad (corpus + desk)

Corpus: `test/fixtures/parity/engine.cases.jsonl.gz` (2408 líneas, **556** con `:input`).
Desk: 543 `*.md`. **Total 1099 documentos.** `analyze` normaliza tipos a
`:heading/:paragraph/:code/:quote/:list/:hr/:html/:table/:frontmatter/:link-ref/:unparsed/:other`.

| comparación (kinds) | crudo | con `\n` final normalizado |
|---|---|---|
| tree-sitter vs `blocks.cljc` — kinds idénticos | **1074 / 1099** (97,7 %) | **1099 / 1099** (100 %) |
| tree-sitter vs `blocks.cljc` — kinds **y** spans idénticos | 804 | 829 |
| tree-sitter vs AST SLDB-MD — kinds idénticos | 716 / 1099 | 741 / 1099 |

Sin errores ni timeouts en ningún documento (`:errors {:ts 0 :blocks 0 :ast 0}`, `:slow []`).
Cómputo total: corpus ~1,3 s + desk ~3,0 s. La corrida **no** usa más de 3 GB (streaming).

### 3.1 Clasificación de diferencias

**tree-sitter vs `blocks.cljc`** (solo 25 divergencias):

- **ATX sin `\n` final** — 25 docs (`# S3`, `# Fine`, …; total no-trailing-nl = **270/1099**).
  La gramática block de tree-sitter-md da **raíz ERROR** y `flat_blocks` devuelve
  `atx_h1_marker` como bloque (`:other`), mientras `blocks.cljc` y el AST dan `:heading`.
  *Mitigación trivial*: añadir `\n` si falta → **1099/1099**.
- **Spans**: 270 docs con kinds idénticos pero span distinto; **el 100 % son `:list`** y la
  única diferencia es **+1 línea al final** (`ts [54 56)` vs `blk [54 55)`): el nodo `list` de
  tree-sitter absorbe la línea en blanco posterior; `blocks.cljc` corta antes. Todo lo demás
  (heading, paragraph, code, quote, table, hr, frontmatter) tiene spans idénticos.

**tree-sitter vs AST SLDB-MD** (383 divergencias crudas):

- `[:paragraph :unparsed]` **356** + `[:list :unparsed]` **2** = **358 docs** con
  `:opaque "markdown/unparsed"`. No es un fallo de tree-sitter sino el *perfil cerrado*
  SLDB-MD: cae a opaco ante construcciones fuera de perfil (`_cursiva_`, `[[wiki]]`,
  `![[embed]]`, `[clave:: valor]`, definiciones de nota, items vacíos). tree-sitter lo
  parsea como texto/`paragraph`.
- `[:other :heading]` 25 = el bug del `\n` final.

**Casos dirigidos** (construidos, no presentes en el corpus salvo donde se indica):

| caso | tree-sitter | `blocks.cljc` | AST SLDB-MD |
|---|---|---|---|
| setext `T\n===\n` | `:heading` (setext_heading) | **`:paragraph`** ← gap | `:paragraph` + opaco |
| html block `<div>x</div>` | `:html` | **`:paragraph`** ← gap | `:html` (opaco) |
| link-ref `[l]: url` | `:link-ref` | `:paragraph` | `:unparsed` |
| footnote `[^1]: n` | `:link-ref` | `:paragraph` | `:unparsed` |
| lazy continuation `> a\nb\n` | `:quote` | `:quote` | `:quote` + `:paragraph` ← AST parte |
| `_` énfasis `a _b_ c` | emphasis inline | — (no inline) | `:opaque` |
| tabla, hr, frontmatter, lista anidada | iguales los tres | iguales | iguales |
| **sin `\n` final** `# H` | `:other` (ERROR) | `:heading` | `:heading` |

Recuentos sobre los 1099: docs con `_` **614**, de los que tree-sitter produce énfasis con `_`
**335** (conflicto directo con la política SLDB-MD "`_` nunca es énfasis"); frontmatter 626;
listas 561; headings 1068; tablas 5 (los tres coinciden); `html_block` top-level 0 y
`setext_heading` 0 en el corpus/desk → esos dos gaps **no están ejercitados** por el corpus.

### 3.2 Causa del OOM de `parity.clj` (ya corregida en `parity2.clj`)

Confirmado el diagnóstico: `sldb-ast` devuelve **keywords sueltos**, no mapas, así que
`(same-kinds? T A)` comparaba contra `(nil nil …)` y daba `false` **siempre**; entonces
`first-div` se llamaba con dos listas iguales y `(map vector (concat x (repeat eof)) …)`
iteraba sin fin realizando una secuencia infinita → RAM creciendo hasta ~2–3,7 GB. `parity2.clj`
(1) compara `(mapv :kind T)` contra los keywords del AST directamente y (2) `first-div` usa un
bucle indexado finito `(get a i ::eof)`, nunca `repeat`/`concat`. Corrido en streaming con
`memcap 3` termina en ~4–5 s y <3 GB.

Guarda por documento: `parity2.clj` mide el tiempo de cada documento y registra en `:slow`
cualquiera que supere `per-doc-budget-ms` (5 s). No hay interrupción dura —bb no puede matar
limpiamente un parse en curso— pero con `first-div` finito, `memcap 3` y `timeout` como red de
seguridad no hizo falta: `:slow []` en corpus y desk.

## 4. Empaquetado por plataforma

Layout propuesto (junto a `bb`, en `lib/`):

```
dist/kimun-<v>-<platform>/
  bin/kimun, bin/kimun.cmd
  lib/bb
  lib/kimun.jar
  lib/pod-kimun-treesitter[.exe]      ← nuevo, 3–4 MB
  VERSION, LICENSE
```

- **`release.edn`**: añadir un mapa `:pods` análogo a `:sha256` del `bb`, p. ej.
  `:pods {:linux-amd64 {:asset "pod-kimun-treesitter-linux-amd64" :sha256 "…"} …}`; `--pin`
  verifica digests de pod igual que hoy con el asset de babashka.
- **`scripts/release.clj`**: en `bundle!`, tras copiar el jar, copiar el pod de
  `target/pods/<platform>/pod-kimun-treesitter[.exe]` a `lib/`, con permisos `rwxr-xr-x`
  (POSIX) — el bloque de permisos ya existe; y en `pinned-bb!`/`pin!` añadir la descarga y
  digest del pod. Las gramáticas van **compiladas dentro** del binario (crates), así que no hay
  `.so` por gramática que empaquetar.
- **Build por plataforma**: matrix de CI en runners nativos (ubuntu/macos/windows) con
  `cargo build --release`; alternativamente `cross`/`cargo-zigbuild`. Linux: `+crt-static`
  (o target musl) para binario estático, como el asset de bb. `Cargo.lock` fija versiones;
  para builds offline, `cargo vendor`. Añadir `rust-toolchain.toml` con la versión.
- **Descubrimiento en runtime**: `KIMUN_POD` (override) → por defecto
  `<dir de bb>/../lib/pod-kimun-treesitter` resuelto desde el lanzador (como hace `bin/kimun`
  con `$DIR`); en desarrollo, `pod/target/release/…`. No meterlo en el uberjar: hay que
  ejecutarlo como fichero en disco.
- CI de release: `bb release --local-bb --platforms linux-amd64` ya prueba el bundle; añadir un
  smoke de `ts/version` a través del pod del bundle.

## 5. Recomendación y camino de migración

**Adoptar, por fases**, como parser de la **capa M**; no como sustituto del perfil SLDB-MD.

1. **Fase 0 (ahora)**: pod sólo en desarrollo/pruebas; `blocks.cljc` y SLDB-MD siguen siendo la
   verdad. El adaptador `treesitter-symbols` (protocolo 1, `11 §9`) ya cubre código.
2. **Fase 1 — segmentación de bloques**: el pod sustituye a `blocks.cljc` (que hoy imita
   markdown-it a mano, pendiente en `12`). Reglas de integración obligatorias: **normalizar
   entrada** (añadir `\n` final) y **recortar la línea en blanco final del nodo `list`** para
   reproducir los `:map` de v1. Tras esto la paridad de kinds es 1099/1099 y la de spans,
   1099/1099 salvo la lista (que se corrige con ese recorte). Mantener `blocks.cljc` como
   oráculo diferencial sobre el corpus.
3. **Fase 2 — SLDB-MD como vista sobre el CST**: mapear nodos del CST al AST neutro
   (`:heading/:paragraph/:code/:quote/:list/:opaque`), re-derivando el `:opaque` del perfil
   cerrado (`_`, tablas, footnotes, imágenes fuera de perfil). El **render canónico sigue
   siendo función del AST neutro**, no del CST, así que el round-trip byte a byte se preserva
   sólo si las reglas de perfil (`_` nunca énfasis: 335/614 docs afectados) se aplican *después*
   del parse. Prestar atención a lazy continuation (el AST parte `> a\nb` en cita + párrafo) y a
   setext (SLDB-MD no lo soporta).
4. **Fase 3 — modelos anclados al CST**: cambiar los `:map [start end)` por líneas de
   `blocks.cljc` a **rangos de bytes** + ids de nodo del CST (estables, agnósticos de lenguaje).
   Entonces el mismo pod parsea Markdown **y código**, cumpliendo la decisión 19.
5. `blocks.cljc` se retira cuando el corpus + desk den paridad byte a byte con el recorte y la
   normalización; hasta entonces conviven. El pod se añade a `bb lint`/`bb test` como
   dependencia opcional (skip si falta, como hace el adaptador tree-sitter).

Riesgos a integrar: los dos gaps de `blocks.cljc` frente a CommonMark que el pod **sí**
implementa (setext `===`, html blocks) y los dos comportamientos del pod frente a v1
(`list` con línea en blanco; ERROR sin `\n` final). Ninguno aparece hoy en el corpus excepto el
`\n` final (270 docs).

## 6. Hallazgos / bugs

- **`parity.clj` OOM**: `first-div` no terminaba con secuencias iguales por comparar keywords
  contra mapas; corregido en `parity2.clj`. (Diagnóstico confirmado.)
- **tree-sitter-md 0.5.3**: documento cuyo único contenido es un ATX sin `\n` final → raíz
  **ERROR**; normalizar con `\n`.
- **tree-sitter-md 0.5.3**: el nodo `list` absorbe la línea en blanco posterior (+1 línea).
- **`blocks.cljc` gap**: setext con `===` no se detecta (`---` sí). No lo cubre el corpus.
- **`blocks.cljc` gap**: no reconoce html blocks ni link-reference definitions (no hay tipo en
  su vocabulario). No cubierto por el corpus.
- **AST SLDB-MD vs tree-sitter**: 358/1099 docs divergen sólo porque SLDB-MD cae a
  `:opaque "markdown/unparsed"` (perfil cerrado), no por desacuerdo estructural.

## 7. Reproducir

```sh
# pod
cd /home/jp/.cache/spikes/R2/pod && cargo build --release

# medidas
memcap 3 bb --classpath /home/jp/.cache/spikes/R2 measure.clj
memcap 3 bb --classpath /home/jp/.cache/spikes/R2 measure2.clj
memcap 3 bb --classpath /home/jp/.cache/spikes/R2 bench.clj

# paridad (streaming; escribe parity2-results.edn / parity2-norm-results.edn)
memcap 3 bb --classpath /home/jp/.cache/spikes/R2:/home/jp/proyectos/kimun/src parity2.clj
memcap 3 bb --classpath /home/jp/.cache/spikes/R2:/home/jp/proyectos/kimun/src parity2.clj norm

# recuentos por categoría
memcap 3 bb --classpath /home/jp/.cache/spikes/R2:/home/jp/proyectos/kimun/src classify.clj
```

## 8. Archivos

- `pod/` — fuente Rust y `Cargo.toml`/`Cargo.lock`; `target/release/` y `target-static/release/`.
- `parity2.clj` — paridad streaming (crudo y normalizado); `parity-results`→`parity2-results.edn`,
  `parity2-norm-results.edn`.
- `measure.clj`, `measure2.clj`, `bench.clj`, `dump.clj`, `probe.clj`, `classify.clj`.
- `parity.clj` — original (no usar; contenía el bucle infinito).
