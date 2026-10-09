# S2 — Parser de la capa M por pod tree-sitter (fases 0–2 de R2)

Rama `close/S2` (3 commits, sin push). Worktree `/home/jp/proyectos/kimun-worktrees/S2`.
Autoridad: decisión 29 (`docs/v2/05-estado.md`, `14 §11`, `14 §13` etapa S.2). El pod alimenta
la capa M; `docs/v2/04` sigue siendo el dueño del perfil SLDB-MD y del render canónico.

## Resumen

- **Fase 0 — el pod entra al repo**: hecho. `pods/treesitter/`, `kimun.host.treesitter`,
  `bb pod`, descubrimiento en runtime, empaquetado + smoke del bundle.
- **Fase 1 — segmentación por el pod**: hecho. `blocks/parse` usa el pod; paridad de kinds y
  spans **1099/1099** con el oráculo `parse-native`.
- **Fase 2 — SLDB-MD como vista sobre el CST**: hecho tras bandera. AST idéntico **1099/1099**
  en corpus + desk; el default **sigue `native`** porque en Markdown libre (fuzz de roundtrip)
  quedan ~3 % de divergencias. Se reportan las clases.

## Fase 0 — el pod entra al repo (`4cf4eb22`)

- `pods/treesitter/`: `Cargo.toml`, `Cargo.lock`, `src/` (bencode + main), `rust-toolchain.toml`
  (`stable`, doc de rustc 1.95.0), `README.md` (build dinámico y `+crt-static`, layout del
  bundle, vars). `target/` ignorado por el `.gitignore` raíz.
- `kimun.host.treesitter` (anillo 1, único ns que importa el pod): descubrimiento
  `KIMUN_POD` → `<dir de bb>/../lib/pod-kimun-treesitter` → `pods/treesitter/target/release/…`
  (dev); carga perezosa una vez por proceso; `available?` + `version`/`blocks`/`parse-block`/
  `parse-inline`/`parse-lang`/`query`.
- `bb.edn`: task `bb pod` (`cargo build --release`); `bb test` imprime `tree-sitter pod:
  available|NOT available` y los tests que lo necesitan hacen `println "skipped …"` sin fallar.
- `release.edn :pods` (asset/bin/sha256 por plataforma) y `scripts/release.clj`: copia el pod de
  `target/pods/<plataforma>/` (staging CI) o del build de desarrollo a `lib/`, le da `rwxr-xr-x`,
  verifica sha256 cuando está pinnado y hace smoke de `version` a través del pod del bundle;
  `--pin` calcula también los digests de pod. Los launchers POSIX/Windows exportan `KIMUN_POD`.
- Probado: `memcap bb release --local-bb --platforms linux-amd64` → bundle con
  `lib/pod-kimun-treesitter` (3,24 MB, ejecutable), smoke `{:pod "pod-kimun-treesitter" …}` OK.
- Fix menor: `name` local sombreaba `clojure.core/name` en el aviso de pod ausente de
  `release.clj` (solo se dispara sin pod; por eso no salió hasta probar el caso sin pod).

## Fase 1 — segmentación por el pod (`41d0e5a2`)

`kimun.surface.models.blocks/parse` llama al pod (var `blocks`, sin materializar el CST, ~15×
la entrada) con las dos normalizaciones de R2: `\n` final si falta y recorte de la línea en
blanco que el nodo `list` absorbe. Mapea tipos al vocabulario actual y `:map` a líneas
`[start end)`. `parse-native` conserva la implementación in-process como **oráculo**; `parse`
cae a `parse-native` si el pod no está.

Paridad (`test/kimun/surface/models/blocks_parity_test.cljc`, streaming, corpus + desk):

| comparación | resultado |
|---|---|
| kinds idénticos | **1099/1099** |
| kinds **y** spans idénticos | **1099/1099** |
| divergencias / sin contrapartida / error | 0 / 0 / 0 |

Casos dirigidos (decisión documentada en `04 §11.1`):

| caso | pod (`blocks`) | oráculo | decisión |
|---|---|---|---|
| setext `T\n===` | `heading` | `paragraph` (no lo detecta, R2 §6) | fuera del perfil; el test fija que el pod lo ve |
| html block `<div>x</div>` | `html_block` → `paragraph` | `paragraph` | región opaca en el perfil (`04 §7`) |

## Fase 2 — SLDB-MD como vista sobre el CST (`66ff1e9c`)

- `kimun.surface.markdown.cst-ast`: `parse-block` (CST de bloques, sin inline) → bloques con el
  perfil cerrado → se reutilizan las reglas de `kimun.surface.markdown.ast` (así `_` nunca es
  énfasis, lo fuera de perfil cae a `:opaque` byte a byte y la continuación perezosa se aplica
  *después* del parse). El render canónico sigue siendo función del AST neutro.
- `ast/parse` despacha: default **`native`** (`ast/parse-native`, el oráculo);
  `KIMUN_PARSER=treesitter` usa la vista. La vista cae a `parse-native` ante ERROR del pod o
  cualquier error interno (la bandera no puede romper nada).

Paridad de AST (`test/kimun/surface/markdown/cst_ast_parity_test.cljc`, corpus + desk):

| comparación | resultado |
|---|---|
| AST idéntico (`=`) | **1099/1099** |
| divergencias / errores | 0 / 0 |
| por clase | `{}` |

Es decir, en corpus + desk la vista reproduce el AST nativo al 100 % ⇒ mismos ids de nodo y
mismo merkle-root. R2 §3.1 partía de 358 documentos por `:unparsed`; desaparecen porque la
vista comparte con el nativo la fase de perfil (el `:opaque` lo decide la misma función).

**Por qué el default sigue `native`.** El fuzz de propiedad del propio repo
(`roundtrip_test`, ASTs generados → render → parse) sí encuentra divergencias fuera de
corpus/desk. Muestreo de 3000 documentos generados (memcap 3, streaming):

| clase (tipo nativo vs pod, primer bloque divergente) | n |
|---|---|
| mismo tipo de bloque, contenido distinto (`:content-only`) | 94 |
| `[:list]` vs `[:code]` | 1 |
| `[:quote]` vs `[:code]` | 1 |
| errores/excepciones | 0 |

Total 96/3000 ≈ 3,2 %. Con el default `treesitter` esos casos rompen los tests de propiedad, así
que — aunque el criterio literal de la fase (corpus + desk) da 100 % — la bandera queda opt-in y
se lista lo que falta (clases de arriba). Lo pendiente es hacer que la vista respete el corte de
bloques del perfil para listas anidadas y fences con indentación, donde el pod agrupa distinto
(p. ej. un `  ````` ` cierra un fence para tree-sitter-md y no para el perfil; `indented_code_block`
agrupa a través de líneas en blanco). Ver `04 §11.2` y `12`.

## Tiempos de `bb test`

Medidos con `memcap timeout 900 bb test`, mismo equipo; "sin pod" = binario del pod movido a un
lado (los tests de pod se saltan y `blocks/parse` cae al oráculo).

| escenario | tiempo total | núcleos (ns) | resultado |
|---|---|---|---|
| sin pod (≈ pre-S2) | **2m01,3s** | 119 s | `{:test 884 :pass 9530 :fail 0 :error 0}` |
| con pod (S2) | **2m06,2s** | 124 s | `{:test 884 :pass 9539 :fail 0 :error 0}` |

Coste ≈ 5 s: las dos suites de paridad (1099 documentos, ~4–5 s) y la carga perezosa del pod una
vez por proceso (18,8 ms de R2). Dentro del presupuesto (~2–5 min) y las suites de paridad hacen
streaming (un documento a la vez, solo contadores y ≤ 20 ejemplos).

## Validación

- `memcap bb lint` → `rings and docstrings ok: 98 files`.
- `memcap timeout 900 bb test` → `{:test 884, :pass 9539, :fail 0, :error 0, :namespaces 109}`
  (verde también sin pod: `run-bundle` del release incluido).
- `memcap timeout 600 bin/kimun include check` → `data.ok true`, todos los `findings` a 0.
- `memcap timeout 600 python3 scripts/parity/coverage.py /home/jp/proyectos/hum-ecosystem/tools/sldb . --strict --allow-open-prefix selfdoc/ --allow-gaps` → `strict: ok - 0 blocking, 0 errors, 2 unclassified under allowed prefixes`, **exit 0**.
- Release: `memcap bb release --local-bb --platforms linux-amd64` con smoke de `version` por el
  pod empaquetado.

## Hallazgos sobre el pod / la gramática

- **tree-sitter-md 0.5.3**: documento cuyo único contenido es un ATX sin `\n` final → raíz
  **ERROR** (se normaliza con `\n`); el nodo `list` (y también `html_block`) absorbe la línea en
  blanco posterior; `html_block` incluye el blanco final.
- **El pod parte una línea entre dos nodos**: un nodo puede acabar a media línea (`list` hasta
  col 3) y el siguiente empezar en esa misma línea; la regla de spans de R2
  (`(if (zero? ec) er (inc er))`) incluiría una línea de más. La vista lo corrige con
  `line-spans`.
- **`fenced_code_block` y fences indentados**: tree-sitter-md cierra un fence en una línea que el
  perfil no considera cierre (p. ej. `  > ```` `); es la fuente del residual `[:content-only]`.
- **`indented_code_block`** agrupa a través de líneas en blanco; el perfil no lo soporta
  (se re-segmenta con las reglas del perfil).
- **`minus_metadata`** acepta un cierre `---` tras línea en blanco; el perfil no (línea 0 era un
  thematic break). La vista aplica la regla del perfil.
- **Bugs preexistentes destapados** (no del pod): `ast/list-block` reventaba
  (`IndexOutOfBounds`) con un item indentado (indent ≥ anchura del marcador); `ast/code-block`
  construía un regex inválido si faltaba `:n`. Ambos corregidos; sin case existente que los
  cubriera.

## Límites / no hecho

- No se tocó el kernel ni el store (S1 en paralelo); no hizo falta nada de S1.
- **Fase 3** (rangos de bytes e ids de nodo del CST como anclas) no está hecha: no era el
  encargo.
- El parseo de **código** como superficie de modelos sigue pendiente (el pod ya expone
  `parse-lang`/`query`); hoy solo lo cubre el adaptador `treesitter-symbols`.

## Archivos

- `pods/treesitter/{Cargo.toml,Cargo.lock,rust-toolchain.toml,README.md,src/*.rs}`
- `src/kimun/host/treesitter.cljc`
- `src/kimun/surface/models/blocks.cljc`, `test/kimun/surface/models/blocks_parity_test.cljc`
- `src/kimun/surface/markdown/cst_ast.cljc`, `src/kimun/surface/markdown/ast.cljc`,
  `test/kimun/surface/markdown/cst_ast_parity_test.cljc`
- `bb.edn`, `release.edn`, `scripts/release.clj`
- `docs/v2/{04,08,11,12}`
