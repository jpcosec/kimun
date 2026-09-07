# kimun — Superficies y CLI (pista S, §B del plan)

> Qué expone el producto por encima del kernel: el binario `kimun`, su envelope, sus
> códigos de salida, el directorio `.kimun/` y el mapeo completo de la CLI v1 de sldb a
> la v2. El sustrato está en `docs/v2/02`; los modelos en `07`; la distribución en `08`; el
> evaluador en `09`. Autoridad: por debajo de `01`–`04`, por encima de `05` y de cualquier
> contrato de `docs/architecture/` (que este documento supersede: `cli-parity-contract`,
> `query-and-index-parity-contract`).

## 1. Enunciado del producto

`kimun` es **un solo producto** que reúne tres cosas que hoy viven separadas: el
evaluador de s-expressions anclado (`pron`, Python, solo lectura), la CLI de sldb
v1 (Python, markdown como verdad) y el grafo de kgdb (networkx). El kernel es SLDB v2
(`docs/v2/02`): pool content-addressed, sucesión, aristas con evidencia, planes EDN.

Dos superficies, un solo camino:

```
kimun next task --summary          ← evaluador anclado (arriba, docs/v2/09)
kimun docs track desk/tasks/x.md   ← vocabulario v1 (abajo, este documento)
              │ ambas producen un TransactionPlan o una consulta sobre el índice
              ▼
        sldb.kernel.*  (docs/v2/02)     ← sin puerta lateral
```

- **No hay puerta lateral.** Ningún comando escribe Markdown, YAML o el índice sin pasar
  por `store/commit!`; los ficheros proyectados salen por el outbox (`02 §5.3`, `09 §7`).
  Es la respuesta a la motivación (1) del plan: los agentes editaban Markdown a mano porque
  no existía un camino de escritura.
- Si el primer token de la línea no es un grupo conocido (§7), `-main` delega al
  desugarer del evaluador. El evaluador es la superficie de arriba; los grupos v1 son la
  de abajo. Hasta S4 la delegación es un stub que devuelve `cli/usage` (exit 6).

## 2. Anillo y mapa de namespaces

`kimun.*` es **anillo 2**, al lado de `sldb.surface.*` (`03 §1`). Reglas que
`scripts/check_rings.clj` hace cumplir:

| regla | detalle |
|---|---|
| `kimun.*` puede requerir | `clojure.*`, `sldb.kernel.*`, `sldb.host.*`, `sldb.surface.*`, `kimun.*`, `babashka.*`, `cheshire.*`, `clj-yaml.*` |
| nunca al revés | ningún `sldb.*` requiere `kimun.*` |
| reader conditionals e I/O | permitidos en anillo 2 (la CLI es un host) |
| anillo 0 | nuevo: **ningún `def` global mutable** (`atom`/`ref`/`agent`/`volatile!`) en `sldb.kernel.*`; la caché de `sldb.kernel.standoff` la aporta el llamante y la compone `sldb.host.default/layer-cache` |

Se conservan `sldb.kernel.*`, `sldb.host.*` y `sldb.surface.markdown.*` con sus nombres:
el kernel no se renombra al renombrar el producto.

```
kimun.cli.{main,args,out,errors,store}                         ; S0
kimun.cli.commands.{stores}                                    ; S0
kimun.cli.commands.{models,docs,fields,sections,find,graph,
                        serve,derive,status,migrate,anchors}       ; S1–S6
kimun.surface.models.{descriptor,recipe,extract,render,validate}   ; S1 (07)
kimun.surface.edit                                             ; S2 (09 §7)
kimun.surface.index.{build,cache,where,address,search,sections,graph} ; S3
kimun.surface.effects.{outbox,runner,sci}                      ; S5
kimun.surface.eval.{reader,anchors,desugar,resolve,core,read,write,session} ; S4/S5 (09)
kimun.surface.http                                             ; S6
```

`kimun.cli.main/-main` solo despacha; cada `commands.*` recibe `{:store :args :opts}` y
devuelve datos; `cli.out` los imprime; `cli.errors` traduce `ex-info` a envelope y exit.

## 3. Envelope y códigos de salida

Todo comando escribe **un** valor a stdout con `--format json|edn|text` (default `json`):

```clojure
{:ok true  :command "stores/check" :store {:name "legos" :path ".kimun"}
 :revision "<rev-id>|nil" :data {...} :warnings []}
{:ok false :error {:type "store/corrupt-object" :message "…" :data {...}} :exit 5}
```

- `:type` es el keyword del `ex-info` (`03 §2`) impreso como `"ns/name"`; `:data` es su
  `ex-data` serializable. El stacktrace solo con `--debug`, y a stderr.
- `text` es una proyección legible del mismo mapa; nunca lleva más información que `json`.
- `:warnings` es un vector de mapas `{:type :message}`; un comando con warnings sigue en `:ok true`.

| exit | significado | tipos |
|---|---|---|
| 0 | ok | — |
| 1 | resultado vacío / referente ausente | `eval/missing`, `find` sin resultados |
| 2 | ambigüedad (diálogo pendiente, `09 §4`) | `eval/ambiguous` |
| 3 | rechazo de la entrada | `plan/rejected`, `query/*`, `models/*`, `validate/*` |
| 4 | conflicto | `plan/conflict`, `store/stale` |
| 5 | store | `store/*`, `cli/no-store`, `cli/store-exists`, `markdown/*`, `index/*` |
| 6 | uso | `cli/usage`, opción desconocida, grupo desconocido (stub del evaluador hasta S4) |
| 7 | capability denegada | `plan/rejected` con `:check 5` |
| 70 | inesperado | cualquier `Throwable` no tipado (`--debug` para el stacktrace) |

Un exit distinto de 0 siempre lleva `:ok false` y el `:exit` repetido en el envelope, de modo
que un cliente que solo lee stdout no necesita el código del proceso.

## 4. El store `.kimun/`

```
.kimun/
  store.edn      ; descriptor del kernel (02 §8.1) + :name + :links + :capabilities   canónico
  objects/       ; CAS                                                               canónico
  log.edn        ; una Transaction por línea                                          canónico
  heads.edn      ; {tree-id revision-id}                                              canónico
  index/<rev>.edn   ; índice derivado por revisión (S3)                               derivado, gitignored
  session.edn       ; diálogo pendiente del evaluador (09 §4)                          derivado, gitignored
  outbox/           ; EffectPlans pendientes + outbox/log.edn (09 §7)                  cola
```

```clojure
;; store.edn
{:format-version 1 :hash-alg "sha-256" :name "legos"
 :links [{:name "hum" :path "../hum-ecosystem/.kimun"}]        ; rutas RELATIVAS al store
 :capabilities {"human/jp" :all
                "agent/claude" #{{:op :add-node} {:op :add-edge} {:op :new-tree}}
                "kimun/derive" #{{:op :add-node} {:op :add-edge} {:op :replace}}
                "kimun/materialize" #{}}}
```

- **Discovery**: `--store <dir>` > `KIMUN_STORE` > walk-up desde el cwd buscando
  `.kimun/`. Sin store ⇒ `cli/no-store` (exit 5) con el path desde el que se buscó.
- **Coexistencia**: `.kimun/` convive con `.sldb/` v1 hasta `migrate --from-v1`
  (S2). Nada de v2 lee `.sldb/`.
- **Links** (`stores link`): un store federado se nombra y se localiza por ruta relativa;
  un documento federado es el par `(store, tree-id)`. No hay espejos de modelos: el modelo
  del doc federado se lee del store que lo posee.
- **Capabilities por actor**: `human/<user>`, `agent/<name>`, y los actores internos
  `kimun/derive` (motor de derivados) y `kimun/materialize` (runner del outbox, que
  no escribe en el grafo: `02 §10` inv. 11). El chequeo 5 del plan (`02 §5.1`) decide; la
  CLI solo lo traduce a exit 7.

## 5. Mapeo v1 → v2

Piso de paridad fijado por el usuario: store core + modo directo + `find`/`--where`/
direcciones/`--global` + links/transclusión/predicates + `serve` + export/query de grafo.
La columna "vicio eliminado" nombra el defecto de v1 que **no** se copia y cómo.

| v1 (sldb) | v2 (kimun) | hito | vicio eliminado / cómo |
|---|---|---|---|
| `stores init` | `stores init [--name] [--actor]` | S0 | migración implícita al abrir → `init` es explícito y falla si ya existe (`cli/store-exists`, 5) |
| `stores add`, `store update` | `stores link NAME PATH` | S3 | rutas absolutas en `store.json` → ruta relativa al store, validada al abrir |
| `stores check` | `stores check` (`:revisions`, `:trees`, heads) | S0 | `except Exception` ⇒ "vacío válido" → todo fallo es `store/*` tipado, exit 5 |
| — | `stores verify` (recomputa hashes alcanzables, `02 §3.1`) | S0 | nuevo: corrupción nombrada por objeto (`:bad`, exit 5) |
| `stores list`, `store show` | `stores show` | S0 | — |
| `stores semantic-map` | `rebuild-indexes` | S3 | índices que se escribían en cada lectura → solo reconstruye bajo orden; leer nunca escribe |
| `stores semantic-export`, `query` | `graph export\|snapshot\|query\|edges` | S3 | rutas absolutas en el export → `store` relativo, `producer kimun`, contrato `sldb_kgdb_semantic_export` **v2**; `direction` estaba declarado y sin implementar → implementado; absorbe kgdb (`--id-scheme sldb` para ontology) |
| `models add\|update` (Python `StructuredNLDoc`) | `models define -f X.edn` / `--from-python mod:Class` | S1 | modelo = clase Python importada → modelo = **datos** en el árbol (`07`); `update` = `:replace` ⇒ `supersedes` |
| `models list\|show\|validate\|template\|schema\|fields` | `models list`, `models show [--format md]`, `models check`, `models history` | S1 | `show` era código → proyección auditable (`07 §6`) |
| `extract`, `render`, `validate` (directo) | `extract`, `render`, `validate` (directo, sin store) | S1 | regex a mano sobre YAML → CST/AST + `clj-yaml`; campo `rev` ausente ⇒ error explícito, nunca `None` silencioso |
| `docs create\|track\|update\|untrack\|show\|list\|recover\|compose` | mismos nombres | S2 | `update` reescribía el árbol entero → edición parcial por LCS de bloques (`:replace` ⇒ `supersedes`, `09 §7`) |
| `fields show\|query\|clean`, `doc … fields` | `fields get\|set\|unset\|append` | S2 | filtros duplicados en tres módulos → un solo parser de `--where` (§6) |
| `sections show\|find\|fields` | `sections list\|find` | S2/S3 | — |
| `predicates add\|list\|show\|validate\|remove` | `links list\|add` (predicate = `reference` con evidencia) | S3 | predicates en YAML aparte → aristas del pool con `:ref-hash` y actor |
| `find` (`--in --global --regex --fuzzy --where --select`) | `find` con las mismas opciones; exit 1 si vacío | S3 | `--where` no reconocida ⇒ `False` silencioso → `query/bad-where`, exit 3 |
| `serve` | `serve` (`/health /schema /graph /snapshot /query` POST `/eval` POST `/plan`) | S6 | imports privados entre módulos → una sola API HTTP sobre `commands.*` |
| `explore`, `faq`, `inbox`, `ast`, `lint`, `legacy` (`ls get glob raw-find`), `help` textos, `project init\|example` | **se retiran** | — | ver §8 |

## 6. Consultas: `--where`, direcciones, `--global`

Supersede `query-and-index-parity-contract.md`. El índice (S3) es un conjunto de mapas
Clojure reconstruible desde `(store, head)` y cacheado en `index/<rev>.edn`; nunca tiene
autoridad (`02 §10` inv. 10). Forma EAV-compatible para migrar a Datascript sin tocar
comandos:

```clojure
{:models {...} :docs {tree-id {:name :model :payload :tags :hash-d :path :hash-c}}
 :by-model {} :by-name {} :tags {} :tag-dag {} :equivalences {}
 :sections {} :links {} :anchors {}}
```

- `--where` se parsea a un AST cerrado: `[:has f]`, `[:in x f]`, `[:re f pat]`,
  `[:model<= Base]`, `[:cmp op f v]`, combinados con `and`/`or`/`not`. Cualquier otra
  forma ⇒ `query/bad-where` (exit 3) con la posición del token.
- Direcciones: `st.<store>.<Model>[.<doc>[.<field>]]`, `se.<tag>`, `gse.<tag>` (global).
  `--global` recorre los `:links` del store; cada resultado lleva su `(store, tree-id)`.
- `--fuzzy` = `reconcile/dice ≥ 0.7` (`02 §6.5`), el mismo coeficiente del kernel.
- `find` devuelve `:data [{:store :tree :name :model :path :matches}]`, ordenado por
  store y tree-id ascendente; vacío ⇒ exit 1 con `:ok false` y `:error {:type "find/empty"}`.

## 7. Comandos S0 y nuevos

S0 entrega `kimun --version`, `kimun stores init|check|verify|show`, `--format`,
`--store`, `--debug`. Cualquier primer token que no sea grupo conocido delega al evaluador
(stub exit 6 hasta S4).

Nuevos respecto a v1, todos por el kernel: `status` (S2), `reconcile [--accept]
[--min-confidence]` (S2), `migrate --from-v1 [.sldb]` (S2), `rebuild-indexes` (S3),
`derive define|status|run` (S5), `eval '<sexpr>'` y `<tokens>` (S4), `anchors [sym]`,
`anchor add` (S4). Sus semánticas están en `09`.

## 8. Superficies v1 que se retiran, y por qué

| superficie | motivo |
|---|---|
| `explore` (docs/code/hit) | navegación interactiva sobre índices con rutas absolutas; su función la cubre `find` + `graph query` |
| `faq`, `inbox` | eran modelos de deskops disfrazados de comandos; deskops los declara como descriptores (`07 §9`) |
| `ast show\|schema` | exponía el AST interno de v1; en v2 el AST es `04` y se consulta con `docs show --format ast` |
| `lint`, `legacy ls\|get\|glob\|raw-find` | ~900 líneas muertas o duplicadas de `find` |
| `project init\|example`, `help` hardcodeado | `--help` se deriva de los anchors (`09 §3`) |
| `kimun project` (materializar kgdb) | desaparece: no hay grafo aparte que proyectar (`09 §10`) |

## 9. Cómo habla una herramienta Python con `kimun`

Siempre **subprocess + JSON**, nunca FFI ni import del kernel:

```python
from kimun import Client                      # S6, python/kimun/client.py
kb = Client(store=".kimun", actor="agent/deskops")
r = kb.run("docs", "list", "--where", "[:in \"open\" status]")   # → dict del envelope
r["ok"], r["data"], r["revision"]
```

- `Client.run` localiza el binario (`$KIMUN_BIN` > wheel `_bin/<plat>/` > PATH, `08 §7`),
  añade `--format json`, y convierte `:exit` en la excepción de `kimun.errors` que le
  corresponde (`MissingError` 1, `AmbiguousError` 2, `RejectedError` 3, …).
- `python -m kimun.models export mod:Class` es el **único** punto bb→Python, opcional,
  y solo para `models define --from-python` (`07 §8`).
- Hasta S6 el contrato es el envelope de §3 llamado con `subprocess.run(["kimun", …])`.
