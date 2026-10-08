# kimun — Superficies y CLI

> Inventario real de la CLI de `kimun`: grupos, subcomandos, opciones, forma de `:data` y
> códigos de salida. Cada afirmación de aquí corresponde a `src/kimun/cli/**`
> (`main.cljc`, `args.cljc`, `out.cljc`, `errors.cljc`, `store.cljc`, `commands/*`).
> Autoridad según `docs/v2/README.md`. El kernel es `docs/v2/02`; los modelos, `07`; la
> distribución, `08`; el formato 2 y la escala, `10`; los adaptadores, `11`; la inclusión en
> git, `13`. Los anillos y reglas de dependencia son `03`. La paridad medida con sldb v1 es
> `docs/v2/tests/parity-v1.md`.

## 1. Envelope, opciones globales y códigos de salida

Todo comando escribe **un** valor en stdout, construido por `kimun.cli.out`:

```clojure
{:ok true  :command "stores check" :store {:name "kimun" :path ".kimun"}
 :revision "<rev-id>|nil" :data {…} :warnings [{:type … :message …}]}
{:ok false :error {:type "store/corrupt" :message "…" :data {…}} :exit 5}
```

- `:type` es el keyword del error (`03 §2`) impreso como `"ns/name"`; el stacktrace solo con `--debug` (a stderr).
- Un comando con `:warnings` sigue en `:ok true`.
- Un exit distinto de 0 siempre lleva `:ok false` y `:exit` repetido en el envelope.

Opciones globales (`kimun.cli.args`):

| opción | significado |
|---|---|
| `--store PATH` | directorio del store (`.kimun` o su proyecto); si falta, `$KIMUN_STORE`; si falta, walk-up |
| `--format FMT` | `json` (defecto) · `edn` · `text` · `md` · `yaml` · `ast` |
| `--actor NAME` | actor de las transacciones (defecto `human/$USER`) |
| `--debug` | imprime el stacktrace de un error inesperado en stderr |
| `--wait` | si otro proceso escribe, espera en vez de fallar con `store/busy` |
| `--help`, `--version` | uso y versión |

Un primer token que no sea grupo conocido devuelve `eval/not-available` (exit 6) nombrando los grupos; el evaluador anclado no existe todavía (ver §6).

Códigos de salida (`kimun.cli.errors`): 0 ok, 1 `missing`/`empty`, 2 `ambiguous`, 3 rechazo de entrada, 4 `conflict`/`stale`, 5 store/journal/migrate/serve/adapters (runtime), 6 uso, 70 inesperado. La tabla explícita:

| exit | cuándo |
|---|---|
| 0 | ok |
| 1 | `*/missing` o `*/empty` por nombre (`find/empty`, `graph/empty`, `links/missing`, `docs/missing`, `models/no-file`, `eval/missing`) |
| 2 | `*/ambiguous` |
| 3 | namespaces `query` `models` `validate` `docs` `fields` `sections` `find` `graph` `edges` `semantic` `include`; `:plan/rejected` salvo chequeo de capability |
| 4 | `*/conflict`, `*/stale`, `plan/conflict`, `store/stale`, `store/busy` |
| 5 | namespaces `store` `markdown` `index` `journal` `migrate` `serve` `adapters`; `cli/no-store`, `cli/store-exists` |
| 6 | `cli/usage`, `org.babashka/cli`, `eval/not-available` |
| 7 | `plan/rejected` con `{:check :capability}` |
| 70 | cualquier otra cosa |

## 2. El store `.kimun/` (formato 2)

Descubrimiento (`kimun.cli.store/resolve-store`): `--store` > `$KIMUN_STORE` > walk-up buscando `.kimun/store.edn` desde el cwd. Un valor de `--store` que no es ruta existente y parece nombre de enlace se busca como enlace del store actual o de un ancestro; si tampoco, `cli/no-store` (exit 5). Sin store, todo comando que lo exige falla con `cli/no-store` (exit 5).

Layout de un store formato 2 (`kimun.kernel.store`; detalle en `10`):

```
.kimun/
  store.edn      ; {:format-version 2 :hash-alg :sha-256 :capabilities {…} :name :links}
  objects/<id>   ; CAS: EDN canónico del objeto; H(fichero) == id
  log.edn        ; una Transaction por línea, append-only
  heads.edn      ; {:head rev-id :heads-root <id-mmap>}  (commit point)
  lock           ; fichero vacío; lock exclusivo de commit
  index/<head>.edn   ; índice derivado por cabecera (`rebuild-indexes`), gitignored
```

- `stores upgrade` reescribe un store formato 1 como formato 2 (backup `<store>.format-1-<marca>`, mapa `upgrade-format-1.edn`); un store formato 2 no se toca.
- Los enlaces (`:links`) son `{:name :path}` con `:path` **relativo al store**; un enlace absoluto o duplicado es `store/invalid-links` (exit 5). `stores link NAME PATH` enlaza un `.kimun` o su proyecto; `stores links` informa `ok | missing | not-a-store`.
- Capacidades por defecto al crear: `{actor :all "kimun/derive" :all "kimun/materialize" :all}`.
- `index/` es derivado: leer nunca escribe; `rebuild-indexes` lo reconstruye bajo orden.

## 3. Grupos de la CLI

### stores

| subcomando | opciones | `:data` y notas |
|---|---|---|
| `stores init` | `--name NAME` | `{:path :name :actor :capabilities}`; falla `cli/store-exists` (5) si ya hay store |
| `stores check` | — | `{:name :path :hash-alg :revisions :trees :objects :head :links}` + `:documents` si hay findings; abre por replay; store inválido ⇒ `store/invalid` (5) |
| `stores verify` | — | `{:checked :bad}`; verify-full (replay + hash); corrupción ⇒ `store/corrupt` (5) |
| `stores upgrade` | — | `{:upgraded :format-version :path :transactions :backup :map …}`; carrera de escritura ⇒ `store/stale` (4) |
| `stores show` | — | `{:name :path :hash-alg :links :head :capabilities}`; sin replay |
| `stores health` | — | `{:status :summary :findings :actions :next}`; `ok | degraded | needs-human` |
| `stores update` | — | `{:updated [name] :skipped [{:document :category :action}] :status}` |
| `stores reconcile` | `[ROOT] [--apply]` | `{:root :discovered :missing :stale :applied}`; sin `--apply` no escribe |
| `stores link` | `NAME PATH` | `{:link {:name :path} :resolved}` |
| `stores unlink` | `NAME` | `{:unlinked {:name :path}}` |
| `stores links` | — | `{:links [{:name :path :resolved :status}]}`; enlace roto es warning |

### docs

| subcomando | opciones | `:data` y notas |
|---|---|---|
| `docs ingest FILE` | `--name N` | `{:name :tree :path :blocks}`; `docs/exists` (3) si el nombre ya existe |
| `docs update FILE --name N` | — | reingesta de un documento no tipado (edición externa) |
| `docs update NAME [FILE]` | `--data F`, `--set k=v` | reingesta/edición parcial de un documento tipado; `:unchanged?` |
| `docs render NAME\|TREE` | — | Markdown canónico como string de `:data` (`--format text` lo imprime crudo) |
| `docs list` | `--model M` | untyped `{:name :tree :root}`; typed añade `:model :model-version :state :path` |
| `docs create MODEL NAME` | `--data F` \| `--set k=v…`, `--path P` | mapa del documento (con payload, hashes, path) |
| `docs track MODEL FILE` | `--name N`, `--force` | mapa del documento; `--force` para un fichero que no ida-vuelta |
| `docs show NAME` | `--format md`, `--ast` (equivale a `--format ast`) | mapa con `:markdown`; `--ast` ⇒ `{:name :tree :ast}` |
| `docs status` | — | `[{:name :path :status}]` con `status` `ok | modified | missing` |
| `docs untrack NAME` | — | desrastrea |
| `docs delete NAME` | `--yes` | desrastrea y borra el fichero; sin `--yes` ⇒ `docs/confirmation-required` (3) |
| `docs recover DOC` | `--depth N`, `--links-only`, `--include-transclusions` (aceptada e ignorada) | `{:root :path :links [...] :unresolved [...]}`; enlaces sin resolver ⇒ `links/missing` (1) |
| `docs compose DOC` | `-o FILE\|-` | Markdown con transclusiones expandidas; sin resolver ⇒ `links/missing` (1) |

### include

Declaración en `kimun.include.edn`; detalle en `13`.

| subcomando | opciones | notas |
|---|---|---|
| `include check` | `--staged` | deriva entre declaración, git y store; `include/drift` (3) |
| `include gitignore` | `--write` | sin `--write` imprime el bloque gestionado en vez de escribirlo |
| `include hook` | `--install` \| `--uninstall` | hook pre-commit; encadena uno ajeno |
| `include build` | `--rebuild` | construye el store; `--rebuild` mueve el existente a `<store>.bak-<marca>` |
| `include status` | — | recuentos de la declaración |

### models

Validación del descriptor (tipos, bases, `:state`, template, `py•` rechazado) en `07`.

| subcomando | opciones | notas |
|---|---|---|
| `models define` | `-f X.edn` \| `--from-python MOD:CLASS` `[--pythonpath P]`, `--dry-run`, `--backfill` | nombre nuevo ⇒ `:add-node`; existente ⇒ versión+1 y `supersedes` |
| `models field-add` | `NAME --field F --type T --description TEXT [--default D] [--required]` | nueva versión del descriptor |
| `models field-remove` | `NAME --field F` | nueva versión del descriptor |
| `models template` | `NAME [-f FILE]` | sin `-f` muestra el template; con `-f` lo reemplaza (nueva versión) |
| `models list` | — | `[{:name :version :id :fields :documents :not-intact}]` |
| `models show` | `NAME [--format md]` | descriptor, bases, template, historial, documentos que lo referencian |
| `models history` | `NAME` | versiones (cadena `supersedes`) |
| `models check` | `[NAME] [-f DRAFT.edn]` | valida descriptores y documentos; `models/*` (3) |

### extract · render · validate (modo directo, sin store)

El modelo es `--model X.edn | NAME del store | module:Class`, o el primer token posicional.

| comando | opciones | `:data` |
|---|---|---|
| `extract [MODEL]` | `--input FILE`, `-o FILE`, `--allow-empty` | `{:model :payload}` (+`:output` si se escribió) |
| `render [MODEL]` | `--data FILE`, `-o FILE` | Markdown (o `{:model :output}`) |
| `validate [MODEL]` | `--input FILE` \| `--data FILE`, `--mode render-validity\|strict-roundtrip` | `{:ok true :model :mode :errors []}`; no valida ⇒ `validate/invalid` (3) |

Un campo `rev` ausente es `validate/missing` (3), nunca vacío silencioso.

### find

`find TERM` | `--in physical|semantic|all` (v1 `both` se acepta) | `--type KIND[,KIND]` (`store model doc section field tag`) | `--global` | `--regex` | `--fuzzy` (Dice ≥ 0.7) | `--where EXPR` | `--select K,…` | `--limit N`.

`:data` es `[{:store :tree :name :model :path :kind :matches …}]`, ordenado por store y tree-id. Sin resultados ⇒ `find/empty` (1); `--where` inválido ⇒ `query/bad-where` (3); `--regex` y `--fuzzy` se excluyen.

Formas de `--where`: `[:has f]`, `[:in x f]`, `[:re f pat]`, `[:model<= Base]`, `[:cmp op f v]` con `op ∈ = != >= <=`, combinadas con `:and`/`:or`/`:not`. `--global` recorre los `:links` y cada resultado lleva su `(store, tree-id)`.

Direcciones de `TERM`: `st.{Store}.{Model}.doc.field`, `st.{Base+}`, `se.<tag>`, `gse.<tag>`.

### corpus

| subcomando | opciones | `:data` |
|---|---|---|
| `corpus rank QUERY…` | `--k N` (5), `--threshold X`, `--among ID,…`, `--fields`, `--refresh` | `{:query :fields :hits [{:id :model :name :score :payload :tags …}]}` |
| `corpus refresh` | — | `{:stores [{:store :head :documents :fields}]}` |

### rebuild-indexes

Sin opciones ni subcomandos. Escribe el índice derivado de la cabecera bajo `.kimun/index/<head>.edn`. `:data` incluye `bytes cache complete documents head models pruned skipped tags revision`.

### edges

El grafo es la vista derivada del store en su cabecera; `init` y `rebuild` solo informan.

| subcomando | opciones | `:data` |
|---|---|---|
| `edges show` | `[ID \| --from ID \| --to ID] [--relation R] [--no-links] [--exclude-tag TAG]` | `{:from :to :relation :links :count :edges …}` |
| `edges check` | mismas | validación del grafo |
| `edges node ID` | — | `{:id :type :semantics :facets :edges}` |
| `edges nodes` | `--type T` | `{:type :count :nodes [{:id :type}]}` |
| `edges types` | — | `{:count :types …}` |
| `edges relate` | `FROM TO --relation R` | `{:changed :relation :from :to :anchor}` |
| `edges unrelate` | `FROM TO --relation R` | igual |
| `edges type-define NAME` | `--direction --cardinality --source-types --target-types --condition --description` | `{:changed :type}` |
| `edges init` \| `edges rebuild` | — | no-op informado: `{:changed false :derived true :message … :nodes :edges …}` |

### predicates

| subcomando | opciones | `:data` |
|---|---|---|
| `predicates add NAME` | `--axis A [--description D]` | `{:changed :predicate {:name :axis :description}}` |
| `predicates list` | — | `{:count :predicates […]}` |
| `predicates show NAME` | — | la definición |
| `predicates validate [NAME]` | — | validación |
| `predicates remove NAME` | — | `{:changed …}` |

### graph

Lecturas puras sobre el grafo (`--snapshot FILE` lee un snapshot en vez del store). Nada se escribe salvo el fichero de `snapshot save`, `export -o` e `ingest-sldb -o`; las rutas de salida no son absolutas.

| subcomando | opciones | `:data` |
|---|---|---|
| `graph get ID` | `--snapshot F` | `{:id :type :semantics :facets :edges}`; desconocido ⇒ `graph/missing` (1) |
| `graph list` | `--type T` | `{:count :nodes [id]}` |
| `graph query` | `-f FILE.json` | `{:count :nodes […]}`; consulta malformada ⇒ `graph/bad-query` (3) |
| `graph neighborhood ID` | `--depth N` (1), `--direction both\|incoming\|outgoing`, `--relation R` | `{:node :depth :direction :nodes [id]}` |
| `graph path FROM TO` | `--directed`, `--relation R \| --all-relations` | `{:from :to :length :path [id]}`; sin ruta ⇒ `graph/empty` (1) |
| `graph cycles` | `--limit N` (10), `--relation R` | `{:count :cycles [[id]]}` |
| `graph order` | `--relation R` | `{:order [id] :layers [[id]]}`; ciclo ⇒ `graph/cycle` (3) |
| `graph components` | `--type T`, `--relation R` | `{:count :components [[id]]}` |
| `graph central` | `--metric pagerank\|betweenness\|degree\|in_degree\|out_degree` (pagerank), `--limit N` (20), `--kind` (nombre v1 de `--metric`), `--type T` | `{:metric :nodes [{:id :score}]}` |
| `graph isolated` | `--type T`, `--relation R` | `{:count :nodes [id]}` |
| `graph similar ID` | `--limit N` (10), `--type T`, `--relation R` (por defecto `tagged_as`) | `{:node :nodes [{:id :shared}]}` |
| `graph snapshot save\|load FILE` | — | `{:path :nodes :edges :types :relations}` |
| `graph export` | `-o FILE` | node-link (`{"directed" "multigraph" "graph" "nodes" "links"}`) o resumen |
| `graph ingest-sldb FILE` | `-o OUT` | convierte un export semántico (contrato `sldb_kgdb_semantic_export` v2) |

### adaptadores

Diseño, protocolo JSON y adaptadores incluidos en `11`.

| subcomando | opciones | notas |
|---|---|---|
| `adapters list` | — | `[{:name :file :available? :profile :languages :options :missing}]` |
| `adapters scan` | `ADAPTER PATH --system S [--factory M:A] [--language L] [--output DIR] [--architecture-spec FILE] [-- opciones]` | planes, sin escribir ni store |
| `adapters sync` | idem | define modelos ausentes y aplica create/update/untrack |
| `adapters check` | idem | como `sync` sin escribir; deriva ⇒ `adapters/missing` (1) |
| `adapters python-scan\|python-sync\|python-check` | `--source-root DIR` (`src`), `--package P`, `--system sldb` | alias v1; adaptador `python-symbols` |
| `adapters selfdoc-scan\|selfdoc-sync\|selfdoc-check` | `--factory M:A` (obligatoria), `--pythonpath P`, `--include-hidden` | alias v1; adaptador `argparse-cli` sobre la raíz |

Errores: `adapters/missing` (1, con `:reason "drift"` para `check`), `adapters/conflict` (4), `adapters/runtime|failed|invalid-output` (5), `cli/usage` (6). Las opciones del adaptador no declaradas van tras `--`.

### migrate

`migrate --from-v1 [PATH] [--pythonpath P] [--dry-run] [--only MODEL] [--limit N] [--force]`. Trae un store sldb v1 (`.sldb`) al store kimun en un proceso y un open; idempotente, cada ítem en un bucket. `PATH` por defecto es el `.sldb` junto al store. Fallos: `migrate/not-v1-store` (5), `cli/usage` (6), `migrate/nothing-migrated` (5).

### fields

Campos de documentos tipados. `DOC` es nombre, tree-id o `Model:doc`; un campo de modelo se direcciona `models/Model[/field]`.

| subcomando | opciones | notas |
|---|---|---|
| `fields get DOC PATH` | — | valor con su sección (alias `show`) |
| `fields schema MODEL [FIELD]` | — | esquema del campo |
| `fields query FIELD` | `--where EXPR`, `--model M` | un campo a través de los documentos |
| `fields set DOC PATH VALUE` | — | crea si falta (alias `create`; `update` exige existir) |
| `fields unset DOC PATH` | — | elimina campo o elemento de lista (alias `remove`) |
| `fields append DOC PATH VALUE` | — | añade a una lista |
| `fields clean DOC PATH` | `--dedupe`, `--drop-empty` | limpia una lista |
| `fields clear DOC PATH` | — | vacía una lista |

### sections

| subcomando | opciones | `:data` |
|---|---|---|
| `sections list DOC` (alias `show`) | — | `{:document :model :path :sections [{:path :title …}]}` |
| `sections find [TERM]` | `--in physical\|semantic\|both` (both), `--regex`, `--fuzzy`, `--where EXPR` | `{:term :results [{:kind :store :name …}]}` |
| `sections fields DOC SECTION` | — | `{:document :section :fields [{:field_path :value …}]}` |

### semantic

| subcomando | opciones | `:data` |
|---|---|---|
| `semantic show [TAG]` | — | un tag (`{:tag :parents :children :below :above :equivalents :documents}`) o el DAG (`{:roots :nodes :equivalences}`) |
| `semantic tags [PREFIX]` | — | `{:prefix :tags :documents}` |
| `semantic parent-add CHILD PARENT` | — | `{:changed …}` |
| `semantic parent-remove CHILD PARENT` | — | `{:changed …}` |
| `semantic equivalent-add A B` | — | `{:changed …}` |
| `semantic export` | `-o FILE` | payload (productor `kimun`, rutas relativas) |

### journal

| subcomando | opciones | `:data` |
|---|---|---|
| `journal show` | `--limit N`, `--by ACTOR`, `--tree NAME\|ID` | entradas, la más nueva primero |
| `journal verify` | — | `{:revisions :checked :objects :bad}`; problema ⇒ `store/corrupt` (5) |

### serve

`serve [start] [--port N] [--host H] [--token T] [--cors]`. Bloquea hasta interrupción; `--token` o `$KIMUN_SERVE_TOKEN` exige `Authorization: Bearer TOKEN`; def. `127.0.0.1:8787`.

## 4. Rutas de `serve`

Tabla de rutas como datos en `src/kimun/surface/http/routes.cljc`; cada ruta delega en un comando de §3. Familias:

- `GET /health /schema /stores /stores/check /stores/links /docs /document /document/ir /document/segments /ast /lint /fields /sections /find /extract /models /models/detail /render /graph /edges /edges/node /edges/nodes /journal /journal/verify /corpus/rank`.
- `GET /graph/{cycles,components,central,isolated,neighborhood,path}` y `GET /similar`.
- `POST /save /docs /docs/create /docs/ingest /docs/track /docs/untrack /extract/segments /models/define /models/fields-add /models/fields-remove /models/template-edit /models/validate`.

## 5. Mapeo sldb v1 → kimun

| v1 (sldb) | kimun | nota |
|---|---|---|
| `stores init` | `stores init [--name]` | `init` es explícito y falla si ya existe (`cli/store-exists`, 5) |
| `stores add`, `store update` | `stores link NAME PATH` | enlaces federados con ruta relativa al store |
| `stores list`, `store show` | `stores show` | descriptor y cabeceras sin replay |
| `stores check` | `stores check` | abre por replay; findings como warnings |
| — | `stores verify` | nuevo: recomputa hashes y verifica el replay |
| — | `stores health`, `stores update`, `stores reconcile [--apply]`, `stores upgrade` | nuevos: diagnóstico, reparación sin decisión, federación, formato 2 |
| `stores semantic-map` | `rebuild-indexes` | solo reconstruye bajo orden; leer nunca escribe |
| `stores semantic-export`, `query` | `graph export\|snapshot\|query`, `semantic export` | export con rutas relativas, `producer kimun`, contrato v2 |
| `models add\|update` | `models define` | modelo = datos en el árbol, no clase Python |
| `models list\|show\|validate\|template\|schema\|fields` | `models list\|show\|check\|history\|template\|field-add\|field-remove` | |
| `extract`, `render`, `validate` | mismos | CST/AST, sin store en modo directo |
| `docs create\|track\|update\|untrack\|show\|list\|recover\|compose` | mismos | más `docs ingest\|render\|delete\|status` |
| `fields show\|query\|clean`, `doc … fields` | `fields get\|schema\|query\|set\|append\|unset\|clean\|clear` con alias `show\|create\|update\|remove` | un solo parser de `--where` |
| `sections show\|find\|fields` | `sections list\|find\|fields` | las secciones salen del Markdown, sin índice persistido |
| `predicates add\|list\|show\|validate\|remove` | `predicates` (mismos) | predicados como nodos del árbol `relations` |
| `edges …` | `edges …` | grafo como vista derivada |
| `graph …` | `graph …` | |
| `journal`/log | `journal show\|verify` | |
| `selfdoc scan\|sync\|check`, `selfdoc python-*` | `adapters scan\|sync\|check`, `adapters python-*` y alias `selfdoc-*` | adaptador fuera de proceso, protocolo 1 |
| `find`, `serve` | `find`, `serve` | |

Retirados, no portados: `explore`, `faq`, `inbox`, `ast show|schema` (→ `docs show --ast`), `lint`, `legacy ls|get|glob|raw-find`, textos de `help` hardcodeados (→ `--help` genérico), `project init|example`, la materialización de kgdb (`project`), y las banderas `--kgdb-output`/`--kgdb-command`.

## 6. No implementado

- **Evaluador anclado** (S4/S5 de la planificación retirada): un primer token desconocido devuelve `eval/not-available` (6). No existen `eval`, `next`, `check`, `rel`, `anchors`, `anchor add`, ni el grupo `derive`; tampoco hay `.kimun/session.edn` ni outbox. Diseño en `docs/archive/v2/09-evaluador-anclado.md`.
- **Cliente Python y wheels**: no existen `python/kimun/client.py`, `python/kimun/errors.py` ni la task `bb wheel`. El único puente Python es `python -m kimun.models export mod:Class` para `models define --from-python`.
- **Motores de derivados**: las capacidades `kimun/derive` y `kimun/materialize` se registran al crear el store, pero ningún comando las ejercita todavía.
