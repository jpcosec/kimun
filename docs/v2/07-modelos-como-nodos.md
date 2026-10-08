# kimun — Modelos como nodos (pista S, §C del plan)

> Cómo un modelo de documento (lo que v1 llamaba `StructuredNLDoc`) deja de ser una clase
> Python y pasa a ser un nodo del pool, sin tocar el kernel. Requisito del usuario, textual:
> **"los modelos siguen siendo elementos en el árbol, pero tienen que poder definirse y
> proyectarse para poder auditarse."** Sustrato: `docs/v2/02`; perfil Markdown: `04`;
> CLI: `06`. Hito S1.

## 1. Tesis

Un modelo es **datos**: un descriptor EDN, content-addressed como cualquier signo, colocado
en un árbol de taxonomía llamado `models`. Definirlo es una transacción; versionarlo es
`:replace`; proyectarlo es leer el nodo y renderizarlo. Nada de esto exige un kind nuevo, una
clase de arista nueva ni una regla nueva de validación: el kernel de `02` ya lo cubre.

| necesidad | con qué se cubre en `02` |
|---|---|
| el modelo es un elemento del árbol | nodo `:sign/:opaque` en una posición del árbol `{:kind :taxonomy :name "models"}` (`02 §3.1`) |
| se define | `TransactionPlan` con `:add-node` + `:add-edge :ownership` (`02 §5.1`) |
| se versiona y los docs siguen a la versión nueva | `:replace` ⇒ `supersedes` ⇒ las `reference` doc→modelo se re-anclan al sucesor (`02 §6.1`) |
| se proyecta y audita | `models show --format md` es una vista derivada, sin autoridad (`02 §10` inv. 10) |
| un doc declara su modelo | arista `reference` root→modelo con `:ref-hash` = id del descriptor (`02 §3.2`) |
| detección de deriva | estados de anclaje `intact/superseded/orphan` sobre esa `reference` (`02 §6.2`) |

## 2. El descriptor

```clojure
{:model/name    "TaskDoc"
 :model/version 1
 :base          ["OperationalArtifactDoc" "PrimitiveDoc"]  ; MRO exportado; [] si no hereda
 :semantics     {"type" ["workflow" "task"] "workspace" ["desk"]}
 :fields        [{:name "id"          :type :string        :required true  :description "…"
                  :py/annotation "<class 'str'>"}
                 {:name "tags"        :type [:list :string] :required false :description "…"
                  :py/annotation "list[str]" :default []}
                 {:name "frontmatter" :type :yaml          :required false :description "…"
                  :py/annotation "dict | None" :exclude true}]
 :template      "# ⸢rev•title⸥\n\n⸢rev,dict•frontmatter⸥\n\n⸢render•step_details⸥\n"
 :py/references  ["references" "depends_on"]
 :py/containment {"steps" ["StepDoc"]}
 :py/compositions {"step_details" {"source_field" "steps"
                                   "model" "deskops.models:StepDoc"
                                   "template" "1. {title}: {action} -> {outcome}"}}
 :state         {:field "status" :order ["open" "doing" "done"] :terminal ["done"]}}
```

Las claves `:py/*` las escribe el exportador de v1 (`python -m kimun.models export module:Class`,
§7); el kernel y las superficies las leen como metadatos de procedencia. Un descriptor escrito a
mano puede omitirlas.

| clave | tipo | obligatoria | nota |
|---|---|---|---|
| `:model/name` | string, `[A-Z_][A-Za-z0-9_]*` | sí | único dentro del árbol `models` de un store |
| `:model/version` | entero ≥ 1 | sí | lo incrementa `models define` sobre un nombre existente |
| `:base` | `[string …]` | sí (puede ser `[]`) | linealización MRO exportada; orden de resolución de campos: los del modelo, luego cada base en orden |
| `:semantics` | `{string → string \| [string …]}` | no | tags de dominio (`se.<tag>`); `model-tags` los aplana sobre `root-semantics` |
| `:fields` | `[field …]` | sí | `field = {:name :type :required :description :default}`; `:default` debe ser del `:type` |
| `:template` | string | sí | Markdown del perfil SLDB-MD con marcadores (§4) |
| `:state` | `{:field :order :terminal}` | no | opcional; `models check` exige que `:field` sea un `[:enum …]` cuyos valores son exactamente `:order` y que `:terminal` sea subconjunto de `:order` |
| `:py/annotation` | string | no (exportador) | texto de la anotación Python del campo |
| `:exclude` | `true` | no (exportador) | campo portador (el `exclude=True` de v1): sus claves se elevan a los campos declarados sin valor y el portador no aparece en el payload |
| `:py/references` | `[string …]` | no (exportador) | campos cuyos valores son referencias a otros documentos |
| `:py/containment` | `{string → [string …]}` | no (exportador) | campo → modelos contenidos |
| `:py/compositions` | `{string → {string → string}}` | no (exportador) | composición; la clave de v1, también aceptada como `:compositions` |
| `:py/family` | string | no (exportador) | familia de la clase de v1 |

**Composición.** Una composición es `{nombre {"source_field" "model" "template" ["separator"]}}`:
por cada valor de `source_field` se extrae el documento del `model` referenciado y se rellena
`template` (marcadores `{campo}`, `{{`/`}}` escapan); el resultado sustituye un marcador
`⸢render•nombre⸥`. El exportador de v1 la escribe bajo `:py/compositions`; render y aristas la leen
de `:py/compositions` o de `:compositions`. Los documentos referenciados se resuelven por índice,
no por embebido.

Tipos de campo: `:string :int :bool :text :yaml [:list T] [:map T] [:enum v…] [:ref Model]`.
`:text` es Markdown del perfil (se ingesta como bloques); `:yaml` es un opaco
`markdown/frontmatter` parcial; `[:ref M]` valida que el valor nombre un doc de `M` en el
índice. Los valores del descriptor son contenido canónico (`02 §2.1`): sin floats ni
`#inst`; un default numérico no entero se escribe como string.

## 3. El nodo y el árbol `models`

```clojure
{:class :sign :kind :opaque
 :content {:format "edn/model" :blob "<canonical-bytes del descriptor, como string>"}}
```

- `:format "edn/model"` distingue el nodo de cualquier otro opaco; el blob entero entra al
  hash, así que dos descriptores iguales son el mismo nodo (dos stores que definen el
  mismo `TaskDoc` comparten id).
- Solo se cambia por `:replace` completo (`02 §5.1` chequeo 6, inv. 12): exactamente lo que
  queremos para un modelo.
- El árbol `models` es `{:kind :taxonomy :name "models" :root <nodo :block {:type :document}>}`;
  cada descriptor es una posición hija del root, en orden de definición. `models list`
  recorre ese árbol; `models show` lee la posición por nombre desde el índice `:models`.
- Por qué **no** hace falta tocar el kernel: `:opaque` ya existe, `:taxonomy` ya existe,
  `edge/validate` no restringe las clases de los extremos, y `:replace` ya registra
  sucesión. El descriptor podrá convertirse en símbolos `M` con `binding` cuando llegue el
  hito 7, sin migrar datos: el `edn/model` seguirá en el pool.

## 4. El template como documento, la receta como vista

El `:template` se ingesta **además** como un documento del perfil SLDB-MD llamado
`model/<Name>/template` (árbol `:document`, `04 §8`). Cada marcador `⸢…⸥` es texto de una
hoja `:text`, y por tanto una **posición direccionable** con path: se puede anclar, citar y
seguir bajo sucesión como cualquier signo.

Marcadores:

| marcador | significado |
|---|---|
| `⸢rev•campo⸥` | campo obligatorio, extraíble e inyectable |
| `⸢optrev•campo⸥` | opcional: ausente ⇒ la línea/bloque que lo contiene se omite al renderizar |
| `⸢render•campo⸥` | solo salida: se inyecta pero no se extrae (o se extrae y se ignora en `validate`) |
| traits `\|list`, `\|table`, `\|dict` | forma del valor: lista de items, tabla del perfil (opaco `markdown/table`), lista clave: valor |
| `⸢py•…⸥` | **no migra** (§8): un define con `py•` se rechaza (`models/invalid`, exit 3) con la lista de marcadores |

La **receta** (`kimun.surface.models.recipe`) es una vista derivada del template ya
parseado: `[{:path [..] :kind :rev|:optrev|:render :field "status" :trait :list} …]` en
orden de documento. No se persiste: se recalcula del árbol `model/<Name>/template`.

## 5. Documento → modelo; frontmatter

- El root de todo documento del perfil es el **mismo** nodo content-addressed
  (`{:format :markdown :type :document :attrs {}}`, `04 §8`). Para que un documento tenga
  identidad por encima de su árbol nominal, `ast->plan` acepta la opción
  `{:doc <ulid>}`, que emite el root como `{:type :document :attrs {:doc "<ulid>"}}`. Un
  nodo más por documento; `render` ignora `:attrs :doc`. Es lo que permite la arista
  `reference root→modelo` con `:ref-hash` = id del descriptor y `:actor` del plan.
- **Frontmatter**: el bloque `---…---` inicial está fuera del perfil (`04 §1`). Se añade a
  `sldb.surface.markdown.cst` como **extensión opaca**: un solo bloque
  `:sign/:opaque {:format "markdown/frontmatter" :blob "<bytes exactos>"}` re-emitido byte
  a byte (`04 §7`). Invariantes 8 y 9 intactos: el CST sigue concatenando al texto original
  y `parse(render(A)) == A` porque el opaco se copia tal cual. La superficie de modelos lo
  parsea con `clj-yaml` **fuera** del kernel; el kernel solo ve un opaco.

## 6. `extract`, `render`, `validate`

Puros, sobre CST/AST (`04 §3–§6`), sin store en modo directo:

```
extract(md, descriptor) → {:fields {…} :frontmatter {…} :missing [..] :unknown [..]}
  1. cst/parse → ast; separar el opaco markdown/frontmatter y parsearlo con clj-yaml
  2. alinear el AST del doc con la receta del template (LCS por bloques; los marcadores
     son huecos que capturan el bloque/inline que ocupa su posición)
  3. por cada hueco: coerción al :type del campo; traits list/table/dict → EDN
  4. rev ausente ⇒ :missing; bloque que no cae en ningún hueco ⇒ :unknown (nunca se descarta)
render(fields, descriptor)  → Markdown canónico (04 §6): inyecta cada marcador, omite
                              optrev ausentes, serializa el frontmatter con clj-yaml
validate(md, descriptor)    → {:ok bool :errors [{:field :type :message}]}:
                              extract + tipos + required + enum + refs (en modo store)
```

Propiedad que S1 prueba: `extract(render(d)) == d` para todo `d` válido generado; y
roundtrip sobre `desk/tasks/*.md` reales de deskops. Un campo `rev` ausente es un error
explícito con nombre de campo y path del hueco: el "vacío válido" de v1 no existe.

## 7. Comandos

La sintaxis y las opciones exactas de cada subcomando las posee `06`; aquí va el efecto.

| comando | efecto |
|---|---|
| `models define -f X.edn [--from-python MOD:CLASS] [--dry-run \| --backfill]` | valida el descriptor; si el nombre no existe: `:add-node` + posición en `models` + ingesta de `model/<Name>/template`; si existe: `:model/version`+1 y `:replace` de la posición ⇒ `supersedes` ⇒ los docs se re-anclan (`02 §6.1`). `--dry-run` lista los documentos de un modelo redefinido sin escribir; `--backfill` los reescribe bajo el contrato nuevo |
| `models define --from-python mod:Class` | invoca `python -m kimun.models export mod:Class` (`06`) y sigue como `-f`; la MRO de la clase se exporta como `:base` |
| `models show NAME [--format md\|json\|edn]` | `md` = proyección auditable: campos con tipo y default, template con marcadores resaltados, bases resueltas, versión e historial, docs que lo referencian y el estado de anclaje de cada `reference` (`intact`/`superseded`/`orphan`) |
| `models list` | nombre, versión, nº de docs, nº de docs con anclaje no `intact` |
| `models check [NAME] [-f DRAFT.edn]` | valida descriptores (tipos, bases existentes, `:state` coherente, template parseable) y todos los docs de cada modelo con `validate` |
| `models history NAME` | cadena de `supersedes` del descriptor: versiones, actor, revisión |
| `models field-add NAME --field F --type T [--default D] [--required]` | añade un campo y su sección al template (`optrev` si tiene default o no es requerido; `rev` si no) y define una versión nueva |
| `models field-remove NAME --field F` | quita el campo y sus marcadores del template y define una versión nueva |
| `models template NAME [-f FILE]` | sin `-f` imprime el template; con `-f` lo sustituye y define una versión nueva |

## 8. Lo que no migra: `py•`

Los marcadores `py•` de v1 ejecutaban Python en el render. `models define` los rechaza
(`:models/invalid`, salida 3) con la lista de marcadores. El diseño de derivados que los
sustituiría (un motor de s-expressions y un grupo `derive`) está especificado pero **no
implementado**: ver `docs/archive/v2/09-evaluador-anclado.md`.

## 9. Migración de los modelos existentes

- **deskops, 23 modelos** con herencia: `StructuredNLDoc` → `PrimitiveDoc` →
  `OperationalArtifactDoc`; hojas `AtomDoc BoardDoc ChecklistDoc ConditionDoc CrossroadDoc
  EdgeDoc FAQDoc HookDoc InboxNoteDoc MaterializationContractDoc OperatorDoc PillDoc
  ProtoAtomDoc RepositoryDoc RitualDoc RoleDoc RoutineDoc RunDoc RuntimeProfileDoc StepDoc
  TaskDoc` más las dos intermedias. Cada una se exporta como descriptor con `--from-python`;
  las clases intermedias también se definen (son `:base` de otras); las composiciones pasan a
  `:py/compositions` por índice. Goldens: los 23 descriptores congelados en
  `test/fixtures/models/deskops/*.edn`.
- **hum-scrapper, 6 modelos** (`AtomDoc BoardDoc PillDoc RitualDoc StepDoc TaskDoc`, todos
  directos de `StructuredNLDoc`): mismo camino, sin herencia intermedia. Congelados en
  `test/fixtures/models/hum-scrapper/*.edn`.
- Un modelo con el mismo nombre en dos stores federados no se espeja (`06`): cada doc
  referencia el descriptor del store que lo posee.
