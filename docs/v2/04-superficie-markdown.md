# SLDB v2 — Superficie Markdown (hito 4)

> Primera superficie del kernel: texto Markdown → CST → AST estructural neutro → árbol
> de documento + hojas de texto en el pool, y de vuelta. Precisa `docs/v2/02 §7` para
> que un ejecutor pueda implementarla sin improvisar. Vive en el anillo 2
> (`sldb.surface.markdown`), fuera del kernel.

## 1. Alcance: un perfil, no todo CommonMark

El hito 4 implementa el **perfil SLDB-MD**: un subconjunto de CommonMark con gramática
cerrada y **canónica** (una sola forma de escribir cada construcción). Lo que está fuera
del perfil no se rechaza ni se pierde: se conserva como región **opaca** (§6). La
conformidad CommonMark completa es un drawer posterior; el perfil es suficiente para que
`parse(render(A)) == A` sea una propiedad demostrable y para que el informe de
direccionabilidad diga con exactitud qué parte de un documento es operable.

## 2. CST (invariante 8)

`cst/parse` es **total** y **lossless**: divide el texto en una secuencia de bloques con
sus líneas de origen.

```clojure
{:blocks [{:kind :heading|:paragraph|:code|:quote|:list|:thematic-break|:html|:blank
           :lines ["…" …]            ; líneas tal cual, sin el salto final
           :start 0}                 ; índice de la primera línea
          …]
 :newline "\n"                        ; "\n" o "\r\n" detectado (el primero que aparece; "\n" si no hay)
 :trailing-newline? true}
```

`(cst/text cst) == texto original` **siempre** (invariante 8): concatenar `:lines` con
`:newline` y añadir el salto final si lo había reproduce los bytes. El CST no interpreta
inlines. Reglas de segmentación (en orden, por línea):

| línea | bloque |
|---|---|
| vacía (solo espacios) | `:blank` (agrupa consecutivas) |
| ` ``` ` o `~~~` al inicio | `:code` hasta la línea de cierre igual (o fin de texto) |
| `#{1,6} ` al inicio | `:heading` (una línea) |
| `> ` o `>` al inicio | `:quote` (líneas consecutivas) |
| `- `, `* `, `+ ` o `\d+\. ` al inicio, o línea que empieza con 2+ espacios dentro de una lista | `:list` (líneas consecutivas) |
| `---`, `***`, `___` (3+ iguales, solo eso) | `:thematic-break` |
| `<` al inicio | `:html` hasta la siguiente línea vacía |
| cualquier otra | `:paragraph` (líneas consecutivas) |

## 3. AST estructural neutro

```clojure
Doc     = {:type :document :children [Block …]}
Block   = {:type :heading :attrs {:level 1..6} :text Text :marks Marks}
        | {:type :paragraph :text Text :marks Marks}
        | {:type :code :attrs {:lang "…"|nil} :text Text}
        | {:type :quote :children [Block …]}
        | {:type :list :attrs {:ordered bool :start int} :children [Item …]}
        | {:type :item :children [Block …]}
        | {:type :thematic-break}
        | {:type :opaque :format "markdown/html"|"markdown/unparsed" :blob "…"}
Text    = string NFC; texto plano sin marcas
Marks   = [[kind start end] | [:link start end url] …]   ; kind ∈ #{:emphasis :strong :code}
```

- Las **marcas inline** son spans sobre `Text` en **grafemas** (UAX #29, puerto
  `TextSegmenter`), semiabiertos `[start end)`, ordenadas por `[start end kind]`.
  Solo se admiten spans **anidados o disjuntos**; dos marcas del mismo kind no se anidan.
  Un párrafo cuyo inline no cumpla esto se degrada a `:opaque "markdown/unparsed"`.
- `:code` guarda el texto tal cual (sin marcas, sin escapes).
- Un `:item` contiene bloques; su primer bloque es el que va en la línea del marcador.

## 4. Mapeo al pool (docs/v2/02 §2.1)

| AST | nodo del pool |
|---|---|
| `:document`, `:quote`, `:list`, `:item`, `:thematic-break` | `:sign/:block {:format :markdown :type <type> :attrs <attrs o {}>}` |
| `:heading`, `:paragraph` | `:sign/:block {:format :markdown :type <type> :attrs {… :marks Marks}}` con **un hijo** `:sign/:text {:text Text}` |
| `:code` | `:sign/:block {:format :markdown :type :code :attrs {:lang …}}` con un hijo `:sign/:text` |
| `:opaque` | `:sign/:opaque {:format … :blob …}` |

El árbol es `:kind :document`; el orden de hermanos es el orden del documento. Las hojas
`:text` son compartibles entre documentos (mismo texto ⇒ mismo nodo); las marcas viven
en el bloque padre, así que "*cielo*" y "cielo" comparten hoja y difieren en el bloque.

`ast->plan` produce un `TransactionPlan` con aliases; `store->ast` reconstruye el AST
desde un árbol y sus nodos.

## 5. Render canónico (invariante 9)

`render(ast)` produce **una sola** forma:

- heading: `#{level} ` + inline; párrafo: inline; bloques separados por **una** línea
  vacía; el documento termina con un salto de línea.
- inline: marcas reinsertadas — `*x*` emphasis, `**x**` strong, `` `x` `` code,
  `[x](url)` link; las marcas anidadas se cierran de dentro afuera.
- **escapes** en texto plano (no en código): `\ * _ [ ] ` < ` van precedidos de `\`;
  si la primera línea de un párrafo empezaría con `#`, `>`, `-`, `+`, `*`, `\d+.`, ` ``` `,
  `~~~` o `<`, se escapa su primer carácter; `---`/`***`/`___` como párrafo se escapan.
- código: fence de tantos backticks como el máximo run del contenido + 1 (mínimo 3),
  `lang` pegado al fence de apertura.
- lista: `- ` para no ordenadas, `<n>. ` con numeración creciente desde `:start` para
  ordenadas; bloques siguientes del item indentados con 2 espacios (4 si ordenada con
  número ≥ 10); items separados sin línea vacía; dos listas adyacentes se separan con
  un párrafo vacío escapado (`\`) para que no se fusionen.
- quote: cada línea prefijada con `> `.
- thematic break: `---`.
- opaco: `:blob` tal cual, separado como cualquier bloque.
- el texto de salida está en NFC (las hojas ya lo están).

## 6. Regiones opacas (docs/v2/01 §7, addressability)

Se conservan como `:sign/:opaque`, hash sobre el blob, y no se descienden:

- bloques `:html` del CST (`"markdown/html"`);
- párrafos con inlines no representables en el perfil: marcas cruzadas, HTML inline
  (`<span>`), autolinks `<http…>`, imágenes, referencias `[x][ref]`, footnotes,
  extensiones (`[[wikilink]]`, `==mark==`, tablas) → `"markdown/unparsed"`, blob = líneas
  originales;
- un bloque `:list` con marcadores mezclados o indentación irregular → opaco entero.

Las regiones opacas **se re-emiten byte a byte**, así que un documento fuera del perfil
sigue cumpliendo `parse(render(parse(s)))` == `parse(s)`.

## 7. Informe de direccionabilidad

```clojure
(report ast) => {:blocks n :structural n :opaque n
                 :coverage 0.0..1.0            ; grafemas en hojas :text / grafemas totales del render
                 :opaque-regions [{:index i :format "…" :lines [start end]}]}
```

## 8. Garantías y tests (docs/v2/03 §5)

- **inv. 8**: `(cst/text (cst/parse s)) == s` para todo string `s` (propiedad sobre
  strings arbitrarios, incluidos `\r\n`, tabs, líneas vacías al final).
- **inv. 9**: `(parse (render A)) == A` para todo AST canónico `A` generado por
  `gen-ast` (headings 1–6, párrafos con marcas anidadas/disjuntas y texto con caracteres
  a escapar, código con backticks internos, listas anidadas, quotes, breaks, opacos).
- estabilidad: `(render (parse (render A))) == (render A)`.
- fixture dorado `test/fixtures/markdown/profile.md` + `profile.ast.edn` + su plan y los
  ids de nodos que produce; y `outside-profile.md` con el informe esperado.
- store: `markdown->plan` aplicado en un store, luego `store->markdown` reproduce
  `render(parse(s))` (round-trip a través del kernel).
- todo en `sldb.surface.markdown.*` (anillo 2): `cst`, `ast`, `inline`, `render`,
  `plan`, `report`; solo requiere kernel y host. Puerto nuevo en el kernel:
  `TextSegmenter (graphemes [this s])` implementado en `sldb.host.text` con
  `java.text.BreakIterator`.

## 9. Fuera del hito 4

Conformidad CommonMark completa, tablas, footnotes, imágenes, referencias, tree-sitter,
paridad Node, capas UAX #29 más allá de los grafemas de las marcas, edición de
documentos vía planes parciales (hoy un documento se reescribe entero por `:replace` del
árbol o se recrea; los planes finos llegan con el editor).
