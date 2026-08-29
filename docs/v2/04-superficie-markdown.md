# SLDB v2 — Superficie Markdown (hito 4)

> Primera superficie del kernel: texto Markdown → CST → AST estructural neutro → árbol
> de documento + hojas de texto en el pool, y de vuelta. Precisa `docs/v2/02 §7` con
> reglas cerradas para que un ejecutor pueda implementarla sin improvisar. Vive en el
> anillo 2 (`sldb.surface.markdown.*`), fuera del kernel.

## 1. Alcance: un perfil, no todo CommonMark

El hito 4 implementa el **perfil SLDB-MD**: un subconjunto de CommonMark con gramática
cerrada y **canónica** (una sola forma de escribir cada construcción). Lo que está fuera
del perfil no se rechaza ni se pierde: se conserva como región **opaca** (§6). La
conformidad CommonMark completa es un drawer posterior; el perfil es suficiente para que
`parse(render(A)) == A` sea una propiedad demostrable y para que el informe de
direccionabilidad diga con exactitud qué parte de un documento es operable.

## 2. Puerto de grafemas

`sldb.kernel.ports/TextSegmenter`: `(graphemes [this s])` → **vector de strings**, un
elemento por clúster de grafemas (UAX #29) de `s`, en orden; `(apply str v) == s`. La
longitud en grafemas es `(count v)`. Implementación: `java.text.BreakIterator`
(`getCharacterInstance`) en Babashka/JVM; `Intl.Segmenter` en Node (task de paridad).
Todos los offsets de este documento son índices en ese vector.

## 3. CST (invariante 8)

`cst/parse` es **total** y **lossless**: divide el texto en una secuencia de bloques con
sus líneas de origen, sin interpretar inlines.

```clojure
{:blocks [{:kind :heading|:paragraph|:code|:quote|:list|:thematic-break|:html|:table|:blank
           :lines ["…" …]            ; líneas tal cual, sin el salto de línea
           :start 0}                 ; índice de la primera línea
          …]
 :newline "\n"                        ; "\r\n" si la primera línea termina así, "\n" si no
 :trailing-newline? true}             ; si el texto termina en salto de línea
```

`(cst/text cst) == texto original` **siempre** (invariante 8): concatenar los `:lines` de
todos los bloques con `:newline` y añadir un `:newline` final si `:trailing-newline?`.
Un texto vacío da `{:blocks [] …}`. Un texto con saltos mezclados (`\r\n` y `\n`) se
segmenta por `\n` y cada línea conserva su `\r` final como parte de la línea, así que la
reconstrucción sigue siendo exacta.

### 3.1 Reglas de segmentación

Las líneas se recorren en orden. Un bloque "está abierto" hasta que una regla lo cierra.
Regla de **inicio de bloque** para una línea `L` (se aplica la primera que coincida; los
patrones son sobre `L` sin su `\r` final):

| # | patrón (regex) | bloque |
|---|---|---|
| 1 | `^\s*$` | `:blank` |
| 2 | `^ {0,3}(`{3,}\|~{3,})([^`]*)$` | `:code` (fence; el grupo 2 recortado es el *info string*) |
| 3 | `^ {0,3}#{1,6}( |$)` | `:heading` |
| 4 | `^ {0,3}([-*_])( *\1){2,} *$` | `:thematic-break` |
| 5 | `^ {0,3}>` | `:quote` |
| 6 | `^ {0,3}([-*+]\|\d{1,9}\.)( |$)` | `:list` |
| 7 | `^ {0,3}<[A-Za-z/!?]` | `:html` |
| 8 | `^ {0,3}\|` | `:table` |
| 9 | cualquier otra | `:paragraph` |

Reglas de **continuación/cierre** (qué líneas siguientes pertenecen al bloque abierto):

- `:blank`: líneas consecutivas que cumplen 1.
- `:code`: todas las líneas hasta la primera que cumple `^ {0,3}(C){n,} *$` donde `C` es el
  carácter del fence de apertura (`` ` `` o `~`) y `n` su longitud; esa línea de cierre
  pertenece al bloque. Sin cierre, hasta el final del texto. Un fence de `~` no cierra
  uno de `` ` `` ni al revés.
- `:heading`, `:thematic-break`: una sola línea.
- `:quote`: líneas consecutivas que cumplen 5. Una línea en blanco cierra el quote (dos
  quotes separados por blanco son dos bloques).
- `:list`: una línea siguiente pertenece a la lista si (a) cumple 6, o (b) es una línea
  en blanco **seguida** de una línea que cumple 6 o que empieza con ≥ `w` espacios,
  donde `w` es el ancho del marcador del primer item (`2` para `- `, `len("n. ")` para
  ordenadas), o (c) empieza con ≥ `w` espacios, o (d) es una línea no en blanco que no
  cumple ninguna regla 2–8 (continuación perezosa del párrafo del item). En cualquier
  otro caso la lista se cierra **antes** de esa línea (las líneas en blanco no absorbidas
  forman su propio `:blank`).
- `:html`, `:table`: hasta la siguiente línea en blanco (exclusive) o el fin.
- `:paragraph`: líneas consecutivas que no cumplen 1–8.

## 4. AST estructural neutro

```clojure
Doc     = {:type :document :attrs {} :children [Block …]}
Block   = {:type :heading   :attrs {:level 1..6 :marks Marks} :text Text}
        | {:type :paragraph :attrs {:marks Marks} :text Text}
        | {:type :code      :attrs {}  o {:lang "…"} :text Text}     ; :lang solo si hay info string
        | {:type :quote     :attrs {} :children [Block …]}
        | {:type :list      :attrs {:ordered false} o {:ordered true :start n} :children [Item …]}
        | {:type :item      :attrs {} :children [Block …]}
        | {:type :thematic-break :attrs {}}
        | {:type :opaque    :format "markdown/html"|"markdown/table"|"markdown/unparsed" :blob "…"}
Text    = string NFC sin marcas
Mark    = [:emphasis s e] | [:strong s e] | [:code s e] | [:link s e url]
```

- `s`, `e` son offsets en grafemas sobre `Text`, semiabiertos `[s, e)`, con
  `0 ≤ s < e ≤ (count graphemes)`. **Orden canónico** de `Marks`: por `s` ascendente,
  luego `e` **descendente** (la marca exterior antes que la interior), luego el nombre
  del kind ascendente; es decir, ordenadas por la clave `[s (- e) (name kind)]`.
- Dos marcas son **disjuntas** si `e₁ ≤ s₂` o `e₂ ≤ s₁`, **anidadas** si `s₁ ≤ s₂` y
  `e₂ ≤ e₁` (o al revés). Todo par debe ser disjunto o anidado; dos marcas del mismo
  kind no pueden anidarse ni tener el mismo rango; `:code` no puede contener otras marcas;
  `:link` no puede contener `:link`. `url` no contiene espacios, `(`, `)` ni saltos.
- `:code` (bloque) guarda el texto tal cual, sin marcas ni escapes; `Text` es el
  contenido entre fences unido con `\n`, sin salto final.
- Texto de un `:heading`/`:paragraph`: sus líneas fuente unidas por **un espacio**, tras
  quitar el marcador de heading (y un cierre opcional `#+` al final del heading),
  recortar espacios al inicio y al final de cada línea, aplicar el parser inline (§5) y
  normalizar a NFC.

### 4.1 ASTs canónicos

`render` (§6) es inyectivo sobre el conjunto **canónico** de ASTs, que es el que `gen-ast`
genera y el que `parse` siempre produce:

1. `Text` de heading/párrafo no empieza ni termina en espacio, no contiene `\n` ni `\t`, y
   el de párrafo no está vacío (el de heading puede estarlo).
2. `Marks` cumple §4 y ninguna marca tiene rango vacío.
3. `:attrs` tiene exactamente las claves de la tabla de §4 (`:lang` solo cuando no está
   vacío).
4. Dos hermanos consecutivos no son ambos `:list` con el mismo `:ordered`, ni ambos
   `:opaque`, ni ambos `:paragraph`… — **falso**: párrafos y opacos consecutivos sí son
   canónicos (una línea en blanco los separa); solo se prohíbe la pareja de listas del
   mismo tipo (se fusionarían al re-parsear).
5. El primer bloque de un `:item` es un `:paragraph`, `:heading`, `:code` u `:opaque`
   (nunca otra lista, quote o break: el marcador necesita contenido en su línea).
6. `:blob` de un opaco no contiene líneas en blanco ni empieza con espacios más allá de
   3; su primera línea cumple la regla 7 u 8 de §3.1 (html/table) o, para
   `markdown/unparsed`, no cumple 1–8.
7. `:code` `:text` no contiene una línea que sea solo su carácter de fence repetido ≥ el
   largo del fence elegido (el renderer garantiza esto eligiendo el largo).
8. `:list` no está vacía; cada `:item` tiene ≥ 1 bloque.

## 5. Parser inline (paragraph/heading → Text + Marks)

Entrada: la línea lógica del bloque (§4, líneas unidas por un espacio). Salida:
`{:text Text :marks Marks}` o `:unparsed` (el bloque se vuelve opaco). Algoritmo
determinista de una pasada con una pila de delimitadores:

1. **Escapes**: `\` seguido de un carácter de puntuación ASCII (`` !"#$%&'()*+,-./:;<=>?@[\]^_`{|}~ ``)
   emite ese carácter literal; `\` seguido de otra cosa es un `\` literal.
2. **Code span**: una racha de `n` backticks abre; se cierra en la siguiente racha de
   **exactamente** `n` backticks; el contenido es literal (sin escapes ni marcas); si no
   hay cierre, `:unparsed`. Genera `[:code s e]` sobre el contenido (sin los backticks).
   Un code span vacío (` `` `) es `:unparsed`.
3. **Énfasis**: una racha de `*` de longitud 1 es un delimitador de `:emphasis`; de 2, de
   `:strong`; de 3, `:strong` + `:emphasis` (exterior strong); ≥ 4, `:unparsed`. Un
   delimitador **abre** si el carácter siguiente no es espacio ni fin; **cierra** si el
   anterior no es espacio ni inicio. Al cerrar, el kind debe coincidir con la cima de la
   pila; si no coincide (p. ej. `*a **b* c**`), o queda un delimitador abierto al final,
   `:unparsed`. `_` nunca es delimitador: un `_` con letra/dígito solo a un lado (que en
   CommonMark sería énfasis) es `:unparsed`; entre letras (`snake_case`) o entre espacios
   es literal.
4. **Link**: `[` abre; se cierra con `](url)` donde `url` = `[^\s()]+`; el texto entre
   corchetes se parsea con estas mismas reglas (puede contener énfasis/code, no links).
   `[` sin esa forma exacta (referencias `[x][r]`, `[x]`, footnotes `[^1]`, wikilinks
   `[[x]]`, imágenes `![x](u)`) ⇒ `:unparsed`. `]` suelto ⇒ `:unparsed`.
5. **HTML inline y autolinks**: `<` seguido de letra, `/`, `!` o `?` ⇒ `:unparsed`; otro
   `<` es literal.
6. Todo lo demás es literal. Al final: `Text` = los literales concatenados (NFC);
   `Marks` = las marcas cerradas, con offsets calculados sobre los grafemas de `Text`,
   ordenadas por la clave canónica. Si `Marks` viola §4 (p. ej. dos `:emphasis` anidados
   por `*a *b* c*`), `:unparsed`.

## 6. Render canónico (invariante 9)

`render(ast)` produce **una sola** forma, con `\n` como salto y salto final:

- Bloques hermanos separados por **una** línea en blanco; un `:item` renderiza su primer
  bloque en la línea del marcador y los siguientes indentados `w` espacios (§3.1), sin
  línea en blanco entre items de la misma lista.
- heading: `#` × level + ` ` + inline (o solo `#` × level si el texto es vacío);
  párrafo: inline en **una línea**.
- inline: se recorre `Text` por grafemas; en cada offset se emiten primero los cierres
  de las marcas que terminan ahí (interiores antes que exteriores, es decir en orden
  inverso de apertura) y luego las aperturas de las que empiezan ahí (orden canónico).
  `:emphasis` → `*…*`, `:strong` → `**…**`, `:code` → `` `…` `` con tantos backticks como
  la racha máxima interior + 1, `:link` → `[…](url)`. Dentro de un `:code` el texto va tal
  cual; fuera, cada grafema que sea `\`, `*`, `_`, `[`, `]`, `` ` ``, `<` o `|` se emite
  precedido de `\`. Además, si la línea resultante empezara cumpliendo alguna regla 2–8
  de §3.1 (`#`, fence, break, `>`, marcador de lista, `<`, `|`), se escapa su primer
  carácter no-espacio (`\#`, `\-`, `1\.`, `\>`, …).
- code: fence de `` ` `` × `max(3, racha máxima de backticks al inicio de línea en el
  texto + 1)`, `:lang` pegado al fence de apertura, texto, fence de cierre.
- lista: `- ` para no ordenadas; `<n>. ` con `n` desde `:start` incrementando 1 por item
  para ordenadas; `w` = 2 o `len("<n>. ")` del item mayor de la lista (así la
  indentación es uniforme).
- quote: cada línea del render de sus hijos prefijada con `> ` (`>` si la línea es vacía).
- thematic break: `---`.
- opaco: `:blob` tal cual.

`parse(render(A)) == A` para todo `A` canónico, y `render(parse(s))` es idempotente.

## 7. Regiones opacas (docs/v2/01 §7, addressability)

Se conservan como `:sign/:opaque`, hash sobre el blob, y no se descienden:

- bloques `:html` → `"markdown/html"`; bloques `:table` → `"markdown/table"`;
- párrafos y headings cuyo inline devuelve `:unparsed` → `"markdown/unparsed"`, blob =
  sus líneas fuente unidas con `\n` (sin el marcador de heading procesado: la línea tal
  cual);
- listas con marcadores mezclados (cambio entre `-`, `*`, `+`, o entre ordenada y no
  ordenada dentro del mismo bloque `:list`) o cuyo dedentado deja un item sin bloque
  inicial válido → todo el bloque `:list` opaco `"markdown/unparsed"`.

Las regiones opacas se re-emiten byte a byte, así que un documento fuera del perfil
sigue cumpliendo `parse(render(parse(s))) == parse(s)`.

## 8. Mapeo al pool (docs/v2/02 §2.1)

| AST | nodo del pool |
|---|---|
| `:document`, `:quote`, `:list`, `:item`, `:thematic-break` | `:sign/:block {:format :markdown :type <type> :attrs <attrs>}` |
| `:heading`, `:paragraph`, `:code` | `:sign/:block {:format :markdown :type <type> :attrs <attrs>}` con **exactamente un hijo** `:sign/:text {:text Text}` |
| `:opaque` | `:sign/:opaque {:format <format> :blob <blob>}` |

El árbol es `:kind :document`, `:name` = el nombre que pasa el llamante; el orden de
hermanos es el del documento. Las marcas viven en `:attrs` del bloque padre y **entran
en su hash** (son contenido canónico, no una capa derivada: no se pueden recomputar
desde el texto plano); las hojas `:text` son compartibles entre documentos (mismo texto
⇒ mismo nodo). `docs/v2/02 §4` queda precisado así: la capa "markup inline" del stand-off
es **almacenada** en el bloque; las capas UAX #29 sí son derivadas.

Funciones (todas puras, en `sldb.surface.markdown.plan`):

- `(ast->plan host ast {:tree-id ulid|nil :name str :actor str :timestamp str :base rev|nil})`
  → `TransactionPlan` con un `:add-node` por nodo distinto (un mismo texto ⇒ un alias
  reutilizado), un `:new-tree` y un `:add-edge :ownership` por arista, `:order` = índice
  del hijo. Alias: `:n<i>` en orden de primera aparición; el árbol `:t`.
- `(store->ast store tree-id)` → AST: parte de `(get-in store [:trees tree-id])`, recorre
  `:children` desde el root; cada id se resuelve con `revision/get-object`; un
  `:sign/:block` produce el bloque `{:type (:type content) :attrs (:attrs content)}` y, si
  su tipo es heading/paragraph/code, toma `:text` de su único hijo `:sign/:text`; un
  `:sign/:opaque` produce `{:type :opaque :format :blob}`; los demás bloques toman sus
  hijos recursivamente. Un árbol que no cumpla esa forma (p. ej. un párrafo con dos
  hijos) lanza `:markdown/not-a-document`.
- `(markdown->plan host text opts)` = `ast->plan` ∘ `parse`; `(store->markdown store tree-id)`
  = `render` ∘ `store->ast`.

## 9. Informe de direccionabilidad

```clojure
(report host ast)
=> {:blocks n                      ; bloques de cualquier tipo, contando anidados
    :structural n :opaque n
    :coverage 0.0..1.0            ; grafemas en :text (heading, paragraph, code) / (esos + grafemas de los :blob)
    :opaque-regions [{:path [i j …] :format "…" :graphemes n} …]}   ; path = índices de hijo desde el documento
```

`coverage` = 1.0 para un documento sin opacos; los marcadores y escapes del render no
cuentan (no son contenido).

## 10. Garantías y tests (docs/v2/03 §5)

Filas a añadir en `docs/v2/tests/promises.md` (sección "§04 — superficie Markdown") con
estos nombres de test:

| promesa | test |
|---|---|
| inv. 8: `(cst/text (cst/parse s)) == s` ∀ `s` (incl. `\r\n`, tabs, blancos finales, sin salto final) | `cst_test/lossless-for-any-string` |
| reglas de segmentación §3.1 (una por fila, incl. fence `~` vs `` ` ``, cierre de listas por blanco, continuación perezosa, quote cortado por blanco) | `cst_test/segmentation-rules` |
| inv. 9: `(parse (render A)) == A` ∀ `A` canónico de `gen-ast` | `roundtrip_test/parse-render-identity` |
| `render` idempotente: `(render (parse (render A))) == (render A)` | `roundtrip_test/render-is-idempotent` |
| escapes: todo grafema escapable y todo inicio de línea peligroso vuelven literal | `roundtrip_test/escapes-round-trip` |
| inline §5: cada regla de `:unparsed` produce opaco; `snake_case` es literal; code span con backticks internos | `inline_test/profile-rules` |
| orden canónico y validez de marcas §4 | `inline_test/marks-canonical-order` |
| fixture dorado `profile.md` ⇄ `profile.ast.edn`, render byte a byte | `roundtrip_test/golden-profile` |
| fixture `outside-profile.md`: informe congelado y opacos re-emitidos verbatim | `report_test/golden-outside-profile` |
| mapeo §8: `markdown->plan` sobre store vacío, `store->ast` == `parse`, `store->markdown` == `render(parse)`; hoja compartida entre dos documentos | `plan_test/store-round-trip`, `plan_test/text-leaves-are-shared` |
| `store->ast` rechaza árboles que no son documentos | `plan_test/not-a-document` |
| informe §9: coverage 1.0 sin opacos; paths y grafemas de cada región | `report_test/coverage` |

`gen-ast` (en `test/sldb/kernel/generators.cljc`) genera solo ASTs canónicos (§4.1):
headings 1–6 con texto vacío o no, párrafos con marcas anidadas/disjuntas y texto que
contiene cada carácter escapable y palabras con `_` interno, code con backticks al
inicio de línea, listas anidadas hasta 3 niveles con `:start` variados, quotes con
bloques dentro, breaks y opacos válidos; nunca dos listas del mismo tipo consecutivas.

## 11. Fuera del hito 4

Conformidad CommonMark completa, `_` como énfasis, tablas, footnotes, imágenes,
referencias, setext headings, tree-sitter, paridad Node, capas UAX #29 más allá de los
grafemas, edición fina vía planes parciales (hoy un documento se crea entero; editar =
recrear el árbol o `:replace` de nodos por parte del llamante).
