# SLDB v2 — Sustrato computacional

> Qué construye la arquitectura. El "por qué" está en `01-orden-filosofico.md`; aquí
> están las estructuras de datos, las invariantes operativas, las decisiones de runtime
> y el roadmap. El hilo conductor es una analogía deliberada con el modelo de objetos
> de Git, porque resuelve el mismo problema: objetos inmutables compartidos, indexados
> por muchos árboles, con historial reproducible.

---

## 1. El paralelo con Git

| Git | SLDB v2 | Nota |
|---|---|---|
| blob (content-addressed, inmutable) | **nodo** del pool (signo, símbolo o hecho) | identidad = hash del contenido canónico |
| tree (lista de entradas → blobs/trees) | **árbol-índice** (documento, sección, taxonomía, sintaxis, `W_i`) | un blob en muchos trees ⇔ un nodo en muchos árboles |
| commit (tree raíz + parents + autor + mensaje) | **revisión** (raíces de árboles + revisiones padre + transacción + actor + motor) | historial reproducible |
| ref / HEAD | **document head** y heads de índice | CAS para commits concurrentes |
| index / staging | **TransactionPlan** validado antes de aplicar | intención ≠ aplicación |
| packfile, GC | content-addressed store, retención, GC de nodos inalcanzables | inmutabilidad ≠ conservar todo |
| remote, fetch/push | sincronización de pools entre stores | fuera del primer slice |
| `git blame` | provenance por nodo y por arista | quién, cuándo, con qué motor |

Lo que Git **no** tiene y v2 sí: tipos de arista con semántica distinta, aristas que
cruzan capas (S→M→G) con evidencia, y una regla de sucesión explícita entre nodos.

## 2. El pool de nodos

```
Node {
  id        : hash(contenido canónico)          ; BLAKE3 o SHA-256 del host
  class     : :sign | :symbol | :fact
  kind      : tipo dentro de la clase            ; :paragraph, :cst-block, :proposition, :triple …
  content   : datos canónicos del nodo           ; texto plano + offsets, forma lógica, (s p o)
  address   : :structural | :opaque | :external  ; clase de direccionabilidad (01 §4.7)
}
```

- **Signos (S)**: hojas de texto plano de un párrafo (la secuencia de símbolos con
  offsets), bloques del árbol sintáctico concreto (CST), regiones opacas (blob + formato
  de origen), anclajes externos (locator + sample + fingerprint).
- **Símbolos (M)**: proposiciones en forma canónica; cada una es un nodo único aunque
  la expresen mil signos.
- **Hechos (G)**: `(sujeto, predicado, objeto)` validados en un contexto `W_i`, con su
  estado sinnvoll / sinnlos / unsinnig.
- **`W_i`** (contexto lógico, de Matrix): un conjunto nombrado de términos y relaciones
  admisibles bajo el cual una proposición tiene sentido o no lo tiene. En el kernel es a
  la vez un nodo G de kind `:context` y un árbol-índice de kind `:context` sobre el pool
  (sus términos y relaciones admisibles son sus hijos); en los hitos 0–3 solo existe como
  esos datos, sin evaluación. Un binding S→M o una proyección M→G nombran el `W_i` en su evidencia
  mediante **el id del nodo `:fact/:context`** (`:context <node-id>`); el descriptor del
  árbol `:context` lleva ese mismo id en `:root`. El mismo signo puede tener un binding
  distinto por cada `W_i` (una arista por contexto).
- Vocabulario: *motor* y *engine* son sinónimos. El origen de una arista es exactamente
  uno de `:actor` (humano o agente) o `:engine` + `:version` (proceso determinista).

El nodo no sabe en qué árboles está. Esa información vive en las aristas.

### 2.1 Contenido canónico y hashing

```
id(nodo) = H( canonical-bytes( {:class c  :kind k  :content content} ) )
```

- `H` es la función de hash del host (§8.1). Un store usa un solo algoritmo, registrado
  en su descriptor (`:hash-alg`).
- `canonical-bytes` = serialización EDN canónica, definida como: (1) normalizar toda
  string a NFC; (2) imprimir con `pr-str` (sin `*print-meta*`, sin `*print-namespace-maps*`)
  donde cada mapa se imprime con sus entradas ordenadas por `compare` de la forma impresa
  de la clave, cada set como `#{...}` con sus elementos ordenados por `compare` de su forma
  canónica impresa, y cada vector/lista en su orden; un solo espacio entre elementos,
  ninguno junto a los delimitadores; (3) codificar el string resultante en UTF-8. Solo se
  admiten en contenido canónico: strings, enteros (impresos sin signo `+` ni ceros de
  relleno), booleanos, `nil`, keywords, símbolos, vectores, mapas y sets; **no** flotantes
  ni `#inst`/`#uuid`: el llamante debe convertirlos antes a string (un flotante como su
  representación decimal, p. ej. `"3.14"`; una fecha como ISO-8601; un uuid como sus 36
  caracteres) y `canonical-bytes` **rechaza** cualquier valor de otro tipo. Los
  **literales** de `:proposition` y `:triple` son solo los valores EDN atómicos: string
  NFC, entero, booleano, `nil`, keyword. `sldb.kernel.canon` es la implementación de
  referencia; la igualdad de ids sobre los fixtures dorados entre hosts es la prueba de
  conformidad.
- `class` y `kind` **entran** al hash: el mismo texto como `:text` y como `:opaque` son
  dos signos distintos. `address` no entra: es derivable de `class`/`kind`.
- Provenance, timestamps y evidencia **no** entran al hash de un nodo: viven en aristas
  y revisiones.

| class | kind | content |
|---|---|---|
| `:sign` | `:text` | `{:text "<texto plano NFC>"}` — hoja; sin offsets (las capas stand-off se derivan) |
| `:sign` | `:block` | `{:format :markdown :type :heading :attrs {:level 2 :marks [...]}}` — nodo del AST neutro; no lleva texto propio, sus hojas `:text` son hijas en el árbol; las marcas inline viven en `:attrs` (docs/v2/04 §4, §8) |
| `:sign` | `:opaque` | `{:format "html" :blob "<bytes como string>"}` — el blob completo entra al hash; drift ⇔ cambio de id |
| `:sign` | `:external` | `{:locator {:kind :file|:url|:pdf-page|... :path "..."} :sample "<texto>" :fingerprint "<hash>"}` — `:locator` es un mapa abierto cuyo único campo obligatorio es `:kind`; `:sample` = los primeros 200 grafemas NFC del texto referido (o `""`); `:fingerprint` = `H` (el mismo algoritmo del store) de los bytes referidos; el fixture usa `{:kind :file :path "docs/x.pdf" :page 3}` |
| `:sign` | `:span` | `{:leaf <id> :range [inicio fin]}` — se materializa solo cuando algo lo referencia (§4.1) |
| `:symbol` | `:term` | `{:name "<NFC>" :lang "es"}` — símbolo atómico (cosa o predicado) |
| `:symbol` | `:proposition` | `{:form [<id-predicado> <arg> ...]}` — forma lógica como datos EDN; args son ids de símbolos o literales |
| `:fact` | `:triple` | `{:subject <id> :predicate <id> :object <id|literal> :context <id de W_i>}` |
| `:fact` | `:context` | `{:name "<NFC>"}` — un `W_i`; sus términos y relaciones cuelgan de su árbol |

En los hitos 0–3 las clases M y G son **solo formas de datos**: se pueden crear, hashear
y enlazar; no hay evaluación lógica (eso es el hito 7).

## 3. Árboles como índices; aristas con tipo

```
Edge {
  tree     : id del árbol-índice al que pertenece      ; nil para aristas entre capas
  type     : :ownership | :binding | :projection | :reference | :semantic | :derived | :supersedes
  from, to : node-id
  order    : posición entre hermanos (solo :ownership)
  evidence : Evidence (§3.2: ref-hash, origen, contexto, status; sin timestamp)
}
```

| Tipo | Capa | Entra al Merkle | Semántica |
|---|---|---|---|
| `ownership` | dentro de un árbol | **sí** (del árbol) | estructura: posición padre (path) → nodo hijo, ordenada |
| `binding` | S → M | no | este signo expresa este símbolo, en este contexto |
| `projection` | M → G | no | este símbolo se consolidó en este hecho |
| `reference` | S ↔ S | no | anclaje entre signos (links, transclusiones) |
| `semantic` | M ↔ M | no | relación de significado afirmada |
| `derived` | cualquiera | no | salida de un motor (embeddings, inferencia) |
| `supersedes` | mismo nivel | no | `X'` reemplaza a `X` (sucesión) |

Reglas:

- **Un árbol es un árbol.** Sus aristas `ownership` forman un árbol dirigido sin ciclos;
  el Merkle del árbol hashea solo esas aristas y los ids de nodo. Un nodo tiene un padre
  *por árbol*. No hay riesgo de ciclos en el hash porque las demás aristas no participan.
- **Toda arista que cruza capas o anida lo opaco lleva evidencia**: el hash del referente
  en el momento de crearla, más actor o motor, versión y contexto. Es el anclaje
  universal: `(referente, hash, provenance)`.
- El Merkle es **perezoso**: al cambiar un nodo se marca sucia la rama de cada árbol que
  lo contiene; se recalcula al consultar, exportar o confirmar revisión.

### 3.1 Identidad de árboles, objetos de árbol y heads

- Un árbol tiene un **id nominal**, no content-addressed: un ULID (identificador de 26
  caracteres, único y ordenable por tiempo) acuñado al crearlo (`:new-tree`). Su
  **descriptor** `{:tree <id> :kind :document|:section-index|:taxonomy|:syntax|:context :name "..." :root <node-id>}`
  es un objeto content-addressed más en el CAS; la revisión referencia el conjunto de
  descriptores vivos mediante `:trees` (§5), igual que referencia las aristas mediante
  `:edges`. Es el análogo de un *ref* de Git; un id por contenido sería absurdo porque
  cada edición lo cambiaría.
- **Posiciones**: un árbol es un árbol de *posiciones*, no de nodos. Un nodo puede
  ocurrir en varias posiciones del mismo árbol (todos los items de una lista son el
  mismo nodo `{:type :item}`; un párrafo puede repetirse). La identidad de una posición
  es su **path**: el vector de índices de hermano desde la raíz (`[]` = raíz,
  `[0 2]` = tercer hijo del primer hijo). Los planes direccionan la propiedad
  (`ownership`) por paths, nunca por id de nodo (§5.1).
- **Objeto de árbol** (análogo al *tree object* de Git): para cada posición,
  `{:node <id> :children [[<hijo-1> <tree-hash-1>] [<hijo-2> <tree-hash-2>] ...]}` en el
  orden de hermanos. `tree-hash(pos) = H(canonical-bytes(objeto))`; una hoja tiene
  `:children []`. **El orden de hermanos entra al hash por construcción.**
  `merkle-root(árbol) = tree-hash(raíz)`. Dos subárboles idénticos (mismo nodo, mismos
  hijos) tienen el mismo objeto y lo comparten, dentro de un árbol y entre árboles
  (como Git comparte blobs y trees).
- Los objetos de árbol se guardan en el CAS como cualquier blob. Recalcular el Merkle
  perezoso = recalcular los objetos del camino sucio hasta la raíz; el resto se comparte
  entre revisiones (compartición estructural, como en Git).
- Las aristas `ownership` de un árbol en una revisión **son** su conjunto de objetos de
  árbol; el índice inverso nodo → (árbol, paths) es una vista derivada (Datascript),
  reconstruible, sin autoridad. Las aristas de los demás tipos son
  **objetos de arista** persistidos en el CAS (§3.2); una revisión referencia el conjunto
  de aristas activas mediante `:edges` (§5).
- **Marcado sucio** (Merkle perezoso): cada posición guarda el `tree-hash` de su
  subárbol; una posición sin hash está *sucia*. Solo las ops que cambian hijos
  **ownership** (add/detach/move/replace en ese árbol) borran el hash de la posición
  tocada y de todos sus ancestros; `:add-edge`/`:remove-edge` de otros tipos **no**
  tocan el Merkle. Al confirmar, se recomputan en post-orden solo las posiciones sin
  hash; las demás conservan el suyo (compartición estructural entre la revisión base y
  la nueva). Un árbol recién creado tiene su raíz sin hash, así que la primera revisión
  construye todos sus objetos.
- **Alcanzabilidad** (`verify`, hito 3; GC y retención, hito 8): el conjunto alcanzable es
  la clausura transitiva de las referencias por id partiendo de los ids de revisión que
  hay en `heads` (revisión → `parents`, tree-set → descriptores, `roots` → objetos de
  árbol → nodos, edge-set → aristas → nodos). Como todo id es un hash de contenido, el
  grafo de referencias es acíclico y el recorrido termina. Forma parte de `verify` desde
  el hito 3; la *política* de retención
  es el hito 8, y los estados `orphan`/`drifted` de anclajes son el hito 5a/5b. Los nodos
  `:span` y los símbolos sin árbol propio se conservan por esta vía.
- **Heads y CAS**: `heads.edn` es un solo mapa `{tree-id revision-id}` escrito de forma
  atómica; el CAS es lógico y por *entrada*: la escritura se acepta solo si, para cada
  árbol tocado por el plan, el valor actual coincide con `:base`. Una revisión mueve en
  una sola escritura todas las entradas de los árboles que toca.
- **Heads**: `heads: {tree-id → revision-id}` en el head store, actualizado por CAS
  **por árbol**. Un documento es un árbol de kind `:document`; "document head" es el head
  de ese árbol. Una revisión puede mover varios heads (uno por árbol en `roots`). En el
  primer slice no hay ramas: un head por árbol; CAS fallido ⇒ §5.2.

### 3.2 Evidencia por tipo de arista

```
Edge     {:type t  :from <id>  :to <id>  :evidence Evidence}                       ; tipos no ownership
Edge     {:type :ownership  :tree <tree-id>  :parent <path>  :to <id>  :order <n>}  ; una posición
Evidence {:ref-hash <id>  :actor <str> | :engine <str> :version <str>  :context <node-id>  :status <kw>}
id(arista) = H(canonical-bytes(Edge))
```

El **timestamp no forma parte de la arista**: queda registrado en la transacción que la
creó. Así la misma afirmación, hecha por el mismo origen, es la misma arista (idempotente:
un `:add-edge` cuyo id ya existe en el edge-set es un no-op, no un error), de modo que
"un binding por `W_i`" se cumple por construcción para un mismo origen, y
`:remove-edge <edge-id>` es determinista: el id se obtiene consultando el store o del
resultado de la transacción que añadió la arista. Dos orígenes distintos (actores o
motores) que afirman lo mismo producen dos aristas, y eso es deliberado: son dos
evidencias.

| tipo | campos obligatorios de `Evidence` |
|---|---|
| `ownership` | ninguna: se representa por objetos de árbol; su provenance es la transacción |
| `supersedes` | `:actor` (lo toma del `:actor` del plan que ejecuta `:replace`) |
| `reference` (S↔S) | `:ref-hash`, origen (`:actor` o `:engine`+`:version`) |
| `binding` (S→M) | `:ref-hash`, origen, `:context` |
| `projection` (M→G) | `:ref-hash`, origen, `:context`, `:status ∈ {:sinnvoll :sinnlos :unsinnig}` |
| `semantic` (M↔M) | `:ref-hash`, origen, `:context` |
| `derived` | `:ref-hash`, `:engine`, `:version` (`:context` opcional) |

`:ref-hash` es el id del nodo referido (`:to`) en el momento de crear la arista: es el hash
que se compara para detectar mutación. `:context` es el id del nodo `:fact/:context`
(§2). Un binding distinto por cada `W_i`. Cada arista se persiste **individualmente**
como objeto `objects/<edge-id>`; el *edge-set* de una revisión (§5) es otro objeto que
solo lista ids ordenados, de modo que `get-object edge-id` devuelve la arista tras un
replay.

## 4. Stand-off por debajo del párrafo

La hoja canónica de un documento es el **texto plano de cada párrafo** con offsets de
grafema. Sobre ella:

```
hoja (texto plano, hash)
 ├─ capa markup inline     spans {kind, range}            almacenada en el bloque padre (04 §8)
 ├─ capa oraciones         spans UAX #29                  determinista
 ├─ capa tokens/palabras   spans UAX #29                  determinista
 ├─ capa sintaxis          árbol de spans, por motor      proyección (motor, versión)
 └─ capa menciones         spans → binding a símbolos     proyección o transacción
```

- Las capas deterministas (UAX #29) se **derivan bajo demanda** y se cachean por hash de
  hoja; no son nodos del grafo hasta que algo las referencia. La capa de markup inline
  **no** es derivable del texto plano: se guarda como `:marks` en los `:attrs` del bloque
  padre (docs/v2/04 §8) y entra en el hash de ese bloque, no en el de la hoja.
- Una dirección hasta el símbolo **existe** (`hoja#hash @ offset`) sin que exista un
  nodo: materialización perezosa. Un corpus de 10⁴ documentos tiene 10⁸ grafemas; no
  pueden ser nodos.
- El anclaje primitivo `(hoja, rango, hash)` unifica oración, sintagma, mención,
  argumento de un hecho y región opaca. Los ids de nodo del árbol de markup son el caso
  privilegiado donde además hay identidad estructural.

### 4.1 Direcciones stand-off y materialización

- Una dirección es `[leaf-id inicio fin]` con offsets en grafemas (UAX #29) sobre el
  `:text` NFC de la hoja. Es **virtual**: se resuelve sin que exista ningún nodo.
- Se materializa un nodo `:span` (`{:leaf :range}`) solo cuando una arista necesita un
  extremo ahí (binding, semantic, reference). Es un nodo ordinario: lo crea el propio
  plan con `:add-node` (normalmente vía alias) antes de la arista que lo usa; el kernel
  nunca crea spans implícitamente durante la validación. Es content-addressed: el mismo
  span pedido dos veces es el mismo nodo.
- Las capas deterministas (grafemas, palabras, oraciones) son arrays de offsets
  cacheados por `leaf-id`, no nodos ni aristas.
- Si la hoja cambia (nuevo id), los spans sobre la hoja vieja siguen §6: `superseded`
  si la transacción registró sucesión, `drifted` si el cambio fue externo.

## 5. Revisiones y transacciones

```
Revision {
  roots     : {tree-id → merkle-root}      ; todos los árboles vivos (los no tocados copian el root de la base)
  trees     : <id del objeto tree-set>     ; H(canonical-bytes([[tree-id descriptor-id] ...] ordenado)) (§3.1)
  edges     : <id del objeto edge-set>     ; H(canonical-bytes([edge-id ...] ordenado)) de las aristas activas
  parents   : [revision-id]
  tx        : transaction-id
  actor     : <string>
  engines   : {<nombre> <versión>}         ; los motores usados por ESTA transacción; {} = ninguno; no se hereda de la base
  timestamp : "YYYY-MM-DDTHH:MM:SS.mmmZ"   ; UTC, milisegundos, siempre 24 caracteres; lo aporta el plan o el host; entra al hash
}
revision-id = H(canonical-bytes(Revision))  ; exactamente los ocho campos anteriores; provenance = tx + actor, derivado

Transaction = {:id H(plan-resuelto) :plan plan-resuelto :revision revision-id}
TransactionPlan = datos EDN (§5.1)
```

Operaciones primitivas: `new-tree`, `add-node`, `add-edge`, `remove-edge`, `replace`
(el nodo de una posición por otro + `supersedes`), `move` (una posición a otra dentro
de un árbol), `detach` (quita una posición con su subárbol). No hay `update`: los nodos
no cambian. Las ops de propiedad direccionan **posiciones** por path.

Dos de estas ops registran sucesión y por tanto **re-anclan** dentro de la misma
transacción, según la tabla de §6.1: `replace` siempre, y `add-edge` cuando la arista es
una `supersedes` que todavía no estaba activa. Ninguna otra op re-ancla.

- El plan se **valida** (tipos, capabilities, invariantes de árbol, evidencia) antes de
  aplicarse. Código no confiable (scripts de usuario, hooks, agentes) produce planes;
  nunca toca el store.
- Commit por **compare-and-swap** sobre el head. Conflicto → reconciliación por árbol
  (cambios en árboles distintos son independientes por construcción) o `ConflictSet`
  explícito.
- Efectos externos (git, filesystem, APIs, subagentes) salen como `EffectPlan` a un
  outbox persistente; su resultado entra como una transacción nueva.

### 5.1 TransactionPlan: esquema EDN

```clojure
{:plan/version 1
 :base    <revision-id | nil>            ; nil solo para la primera revisión del store
 :actor   "<string>"
 :engines {<nombre> <versión>}            ; opcional
 :ops [{:op :new-tree    :tree {:kind :document :name "..."} :as :t1}   ; :as = alias local al plan
       {:op :add-node    :node {:class :sign :kind :text :content {:text "..."}} :as :n1}
       {:op :add-edge    :edge {:type :ownership :tree :t1 :parent [] :to :n1 :order 0}}   ; posición padre por path
       {:op :add-edge    :edge {:type :binding :from :n1 :to <id> :evidence {...}} :as :e1}
       {:op :remove-edge :edge <edge-id | alias de un :add-edge anterior del mismo plan>}
       {:op :replace     :tree <tree-id> :at [<path>] :new <id|alias>}       ; el nodo de esa posición
       {:op :move        :tree <tree-id> :from [<path>] :to [<path-padre>] :order <n>}
       {:op :detach      :tree <tree-id> :at [<path>]}]}
```

Validación antes de aplicar (rechazo total si falla cualquiera):

1. `:base` coincide con el head actual de cada árbol **tocado** (CAS), donde tocado =
   creado por el plan o con cambios `ownership`; las aristas de otros tipos no tocan
   árboles (viven en el edge-set de la revisión y se fusionan por unión al rebasar, §5.2).
2. Todo id referenciado existe en el pool o se crea en el mismo plan (aliases `:as`).
3. Tras aplicar, cada árbol sigue siendo árbol: cada posición tiene un padre, sin
   ciclos (un `move` a su propio subárbol se rechaza), `order` dentro de `0..count`; el
   mismo nodo puede ocurrir en varias posiciones.
4. La evidencia obligatoria (§3.2) está presente y su `:ref-hash` coincide con el id de
   `:to` **en el estado resuelto del plan** (pool de la revisión base más los nodos que
   el propio plan añade; los aliases ya sustituidos).
5. El actor tiene capability para cada op: una entrada `{:op <op>}` autoriza la op en
   cualquier árbol; `{:op <op> :tree <id>}` solo en ese árbol. Las ops que llevan `:tree`
   (`:new-tree`, `:replace`, `:move`, y `:add-edge`/`:remove-edge` de tipo `ownership`) se
   autorizan por árbol; `:add-node` y las aristas de los demás tipos no llevan árbol y se
   autorizan con `{:op ...}` a secas; `:all` autoriza todo.
6. Un nodo `:opaque` solo puede cambiar por `:replace` completo (nunca se edita su
   contenido por partes); las aristas hacia o desde un nodo opaco se añaden y quitan
   como cualquier otra.
7. El plan es datos puros: sin funciones, sin I/O, serializable en EDN.

Las strings de un plan **no** se rechazan por no estar en NFC: `canonical-bytes` las
normaliza, el nodo se almacena ya normalizado y su id se calcula sobre la forma NFC; dos
planes que difieren solo en normalización producen el mismo nodo. Los chequeos 2 y 3 se
evalúan sobre el estado resultante de aplicar **todas** las ops del plan (no op por op);
el error de rechazo nombra la primera op que viola el chequeo. `:move` cambia hijos
`ownership` y por tanto marca `dirty` igual que `:replace`.

Resultado: `Transaction {:id H(plan-resuelto) :plan plan :revision <id>}`, donde el plan
resuelto tiene los aliases sustituidos por ids, más el mapa `{alias → id}` y los ids de
las aristas creadas, para que el autor pueda referirlas después.

Semántica exacta de `:replace {:tree t :at p :new X'}`, con `X` = nodo en la posición `p`:

1. `X'` ocupa **la misma posición** `p` (mismo padre, mismo orden); el subárbol de la
   posición pasa a colgar de `X'` salvo que el plan lo mueva. Otras ocurrencias de `X`
   en `t` **no** cambian: el reemplazo es por posición.
2. Se crea la arista `supersedes X' → X` con `:actor` = `:actor` del plan.
3. Se aplican las reglas de §6.1 **dentro de la misma transacción**: las aristas
   `reference` y `binding` con `:to X` se duplican hacia `X'` (evidencia nueva con
   `:ref-hash X'`), y las originales quedan marcadas `superseded`; `semantic` y
   `projection` quedan `superseded` sin seguir; `derived` se invalidan.
4. Si `X` estaba en otros árboles, esos árboles **no** cambian: la sucesión es por
   nodo, pero el reemplazo estructural es por árbol.

**Capabilities** (regla 5) en el primer slice: el descriptor del store lleva
`:capabilities {actor → :all | #{{:op .. :tree ..}}}`; un actor ausente del mapa es
rechazado (así se prueba la denegación); el fixture usa un actor con `:all`.

### 5.2 ConflictSet

```clojure
{:base <rev> :head <rev> :plan <plan>
 :conflicts [{:tree <id> :path [<path>] :kind :same-parent-edit}
             {:tree <id> :path [<path>] :node <id> :kind :superseded-target | :removed-target}
             {:tree nil :node <edge-id> :kind :removed-target}]}
```

Al fallar el CAS se calcula el delta `base → head` y se compara con el plan. Hay
conflicto si ambos editan los hijos de la misma posición padre `(árbol, path)`, o si el
plan direcciona una posición cuyo nodo `head` reemplazó o quitó. Si los árboles tocados son disjuntos, el
plan se **rebasa** automáticamente sobre `head` y se reintenta el CAS. **Rebase** = el
mismo plan con `:base` sustituido por `head` y re-validado (§5.1); las ops no cambian
porque no tocan nada que `head` haya cambiado; `roots` y tree-set de la nueva revisión
copian de `head` los árboles que el plan no toca, y el edge-set nuevo = edge-set de
`head` ∪ aristas añadidas por el plan − aristas quitadas por el plan. El rebase solo
tiene éxito si la re-validación pasa; si un `:remove-edge` del plan apunta a una arista
que `head` ya quitó, es conflicto `:removed-target`. No hay merge semántico en el primer slice: el
`ConflictSet` se devuelve al autor.

**Diff entre revisiones** (`diff r1 r2`), criterio de salida del hito 2:

```clojure
{:trees {<tree-id> {:added [<node-id> ...] :removed [...]
                    :moved [{:node :from-paths :to-paths}]      ; nodos cuyas posiciones cambiaron
                    :parents-changed [<path> ...]}}              ; posiciones cuyos hijos cambiaron
 :edges {:added [<edge-id> ...] :removed [...]}
 :superseded [[<old> <new>] ...]}
```

Se calcula comparando objetos de árbol (solo se desciende por hashes distintos) y los
edge-sets de ambas revisiones.

### 5.3 EffectPlan (forma mínima)

```clojure
{:effects [{:effect :fs/write :path "..." :content-ref <node-id>}
           {:effect :git/commit :message "..."}]
 :on-result <plantilla de TransactionPlan>}
```

Fuera del primer slice; se fija solo la forma: sale al outbox persistente y su resultado
entra al kernel como un `TransactionPlan` nuevo. Nunca muta el grafo directamente.

## 6. Identidad, sucesión y detección de mutaciones

**Identidad** = hash. Trivial y exacta. Dos nodos con el mismo contenido canónico son el
mismo nodo, en cualquier árbol, en cualquier revisión.

**Sucesión** = arista `supersedes`, registrada por la transacción que reemplaza `X` por
`X'`. La transacción *sabe* que lo hizo; no hay que adivinarlo. Las aristas colgadas de
`X` se re-anclan a `X'` por regla explícita (según tipo) o quedan marcadas para revisión.

**Mutación** = un árbol apunta a otro nodo donde antes apuntaba a éste. Se detecta
comparando cada anclaje con la revisión actual:

| Estado del anclaje | Condición | Cómo se decide |
|---|---|---|
| `intact` | el referente sigue resuelto en la revisión y su hash es el de la evidencia | determinista (§6.2) |
| `superseded` | el referente fue reemplazado y existe `supersedes` hacia el nuevo | determinista (§6.2) |
| `orphan` | el referente ya no resuelve en la revisión y no hay sucesión registrada | determinista (§6.2) |
| `drifted` | un `orphan` para el que la reconciliación encontró un candidato: fue reemplazado **sin** sucesión (cambio externo) | heurístico (§6.5) |

`drifted` es un **refinamiento de `orphan`**, no un cuarto estado paralelo: el kernel
calcula `intact | superseded | orphan` sin heurística ninguna (hito 5a), y la
reconciliación reclasifica un `orphan` como `drifted` cuando puede nombrar al sustituto
con una confianza (hito 5b). Es el caso de un `.md` editado fuera del kernel: no hubo
transacción, así que no hay `supersedes`. Los estados son consultables por nodo ("qué
está anclado en qué") y por revisión (diff de anclajes), y `drifted`/`orphan` alimentan
el bucle autopoiético (`01` §8).

### 6.1 Reglas de re-anclaje cuando `X'` sucede a `X`

| arista que apunta a `X` | regla |
|---|---|
| `ownership` | la reescribe la propia transacción (es parte de `:replace`) |
| `reference`, `binding` | **se siguen** al sucesor en cualquiera de sus dos extremos: se crea una arista nueva con `X'` en el lugar de `X` (y `:ref-hash` = `X'` si `X` era el `:to`); la vieja queda `superseded`, no se borra |
| `semantic` | **no se siguen**: quedan `superseded` para revisión humana o de agente (el significado puede haber cambiado) |
| `projection` | quedan `superseded` pendientes de revalidación (el hecho puede dejar de ser sinnvoll) |
| `derived` | se invalidan: se recomputan bajo demanda |
| `supersedes` | encadena: `X'' ⇒ X' ⇒ X` |

El re-anclaje siempre apunta al **sucesor directo** en el momento del `:replace`; cuando
más tarde `X''` sucede a `X'`, ese segundo `:replace` vuelve a aplicar las reglas sobre
las aristas que apuntan a `X'`, de modo que la cadena se sigue paso a paso.

**Disparadores del re-anclaje.** La sucesión se registra de dos maneras y las dos aplican
esta tabla dentro de la misma transacción:

1. `:replace` — la transacción acuña la arista `supersedes` con el `:actor` del plan.
2. `:add-edge` de una arista `:supersedes` **nueva** — es la forma de aceptar una
   propuesta de reconciliación (§6.5) y de registrar una sucesión que ninguna operación
   de árbol expresa. Un `:add-edge` cuya arista ya está activa es el no-op de §3.2 y **no**
   vuelve a re-anclar (el re-anclaje ya ocurrió cuando se añadió).

En una arista `supersedes`, `:from` es el **sucesor** y `:to` es el nodo **reemplazado**
(§3.2). El re-anclaje corre **después** de que la arista entre en el conjunto activo, de
modo que la propia `supersedes` ya está ahí cuando se recorren las aristas que tocan a
`old`; ella misma cae en el caso por defecto de la tabla (no se re-ancla a sí misma).
Ambos caminos añaden el par `[old new]` = `[(:to e) (:from e)]` al `:superseded` del
resultado de la transacción, que es lo que lee el `diff` de §5.2. El no-op idempotente
**nunca** re-ancla: si la arista ya estaba activa, el re-anclaje ocurrió cuando se añadió,
y repetirlo duplicaría aristas de re-anclaje que ya existen.

### 6.2 Estados de anclaje (hito 5a)

**Anclaje** = arista activa en la revisión `R` de tipo `reference`, `binding`,
`projection`, `semantic` o `derived`. `ownership` no es anclaje (es estructura; su
provenance es la transacción) y `supersedes` tampoco (es el registro de sucesión que los
demás consultan).

El estado se calcula **por extremo** y luego se combina, porque §6.1 sigue al sucesor en
cualquiera de los dos extremos.

**Sucesión activa.** `successors(R, X) = {(:from e) | e ∈ edges(R), (:type e) = :supersedes,
(:to e) = X}`. Es un *conjunto*: dos actores pueden afirmar sucesores distintos para el
mismo nodo y las dos aristas son legítimas (§3.2). La cadena se sigue mientras el conjunto
del paso actual tenga exactamente un elemento, empezando por el propio `X`. Los tres casos,
sin más:

- `successors(R, X) = ∅` → `:successors []`, `:latest nil`, `:ambiguous? false`; el nodo
  no está sucedido.
- algún paso (el primero incluido) tiene más de un sucesor → `:latest nil`,
  `:ambiguous? true`.
- si no → `:latest` es el último nodo alcanzado, `:ambiguous? false`.

El recorrido lleva un conjunto de visitados: un ciclo (imposible por construcción, porque
`X'` es un nodo que no existía) **corta la cadena y se comporta como su final**, es decir
`:latest` es el último nodo alcanzado antes de repetirse y `:ambiguous?` sigue siendo
`false`. Nunca cuelga y nunca es un error.

**Resolución de un extremo.** `resolves?(R, X)`, por clase y kind de `X`:

| `X` | resuelve cuando |
|---|---|
| `:sign/:span` con `{:leaf L :range [a b]}` | `resolves?(R, L)` **y** `b ≤` número de grafemas del `:text` de `L`; si la hoja no resuelve, el span tampoco (la dirección stand-off hereda el suelo de su hoja, §4.1) |
| cualquier otro `:sign` | `X` ocupa al menos una posición de algún árbol de `R` |
| `:symbol`, `:fact` | `X` existe en el CAS |
| un id que no está en el CAS | nunca resuelve |

La última fila no puede darse por `:add-edge` (que exige que los dos extremos existan,
§5.1), pero `replay` y `verify` no pueden depender de eso: un extremo ausente del pool es
`orphan`, no un error.

La fila de M/G es una **concesión explícita del primer slice**: los símbolos y los hechos
todavía no viven en árboles (`02 §8.1`), así que exigirles posición los volvería todos
`orphan`. El hito 7 la endurece a "pertenece a algún árbol `:context`". Los nodos `:span`
tampoco ocupan posiciones (§4.1): su ancla es la hoja, y por eso recursan.

**Estado de un extremo**, evaluado en este orden:

| condición | estado |
|---|---|
| `successors(R, X) ≠ ∅` | `superseded` |
| `resolves?(R, X)` | `intact` |
| resto | `orphan` |

El orden importa: un nodo puede estar sucedido y seguir presente en otro árbol; gana
`superseded`, porque hay una sucesión registrada que dice qué hacer con él.

**Estado de la arista** = el peor de sus dos extremos, con el orden
`intact < superseded < orphan`. Esa es la única regla. Como observación: §6.1 invalida las
aristas `derived` que tocan al nodo sucedido, así que lo normal es que una `derived` activa
solo aparezca `intact` u `orphan`; si aun así una `derived` tiene un extremo `superseded`
—porque la sucesión ya estaba registrada antes de crearla— su estado es `superseded`, por
la regla, sin excepción.

### 6.3 Consultas de anclaje (hito 5a)

`sldb.kernel.anchor`, todas puras sobre `(store, rev-id)` y sin autoridad (invariante 10):

```
(anchor/anchor-types)         ; #{:reference :binding :projection :semantic :derived}

(anchor/placements store rev-id)
  → {<node-id> [[<tree-id> <path>] …]}      ; el índice inverso nodo → posiciones

(anchor/endpoint-state store rev-id node-id)
  → {:node <id> :state :intact|:superseded|:orphan
     :successors [<id> …] :latest <id>|nil :ambiguous? bool
     :positions [[<tree-id> <path>] …]}

(anchor/state store rev-id edge-id)
  → {:edge <id> :type <kw> :state <kw> :from {…} :to {…}}   ; :from/:to = endpoint-state

(anchor/states store rev-id)          ; un mapa por anclaje activo, ordenado por edge-id
(anchor/anchored-in store rev-id node-id)
  → {:node <id> :positions [[tree path] …] :as-from [state …] :as-to [state …]}
(anchor/report store rev-id)
  → {:revision <id> :anchors n :by-state {:intact n :superseded n :orphan n}
     :by-type {<kw> {:intact n :superseded n :orphan n}} :orphans [<edge-id> …]}
(anchor/diff store r1 r2)
  → {:changed [{:edge <id> :type <kw> :before <estado> :after <estado>} …]
     :added [state …] :removed [<edge-id> …]}
```

**Formas de retorno.**

- `:positions` lista **todas** las posiciones del nodo en **todos** los árboles de la
  revisión (los de `:roots`), los árboles en orden ascendente de id y, dentro de cada
  árbol, los paths en orden de documento (pre-orden). Los paths **no se ordenan**: salen
  en el orden del recorrido, que ya es determinista. (El orden natural de Clojure sobre
  vectores compara primero la longitud, así que "orden lexicográfico" no sería lo mismo;
  por eso se fija el recorrido y no un comparador.)
- `:successors` son los sucesores **directos**, sin repetidos, en orden ascendente de id, y
  `[]` cuando no hay ninguno. `:latest` es `nil` cuando no hay sucesores o cuando la cadena
  es ambigua; `:ambiguous?` es cierto sii algún paso de la cadena tuvo más de un sucesor.
- `:as-from` y `:as-to` son **vectores** de mapas `anchor/state`, en orden **ascendente**
  de id de arista (no están indexados por id: el llamante recorre). Una arista cuyos dos
  extremos son el mismo nodo aparece en los dos vectores.
- `:by-type` lleva **las cinco** claves de `anchor-types` siempre, con ceros cuando no hay
  anclajes de ese tipo; `:by-state`, las tres.
- `anchor/diff` compara los conjuntos de anclajes de `r1` y `r2`: `:added` son los activos
  solo en `r2` (mapas de estado completos), `:removed` los ids de los activos solo en `r1`,
  y `:changed` los activos en ambos cuyo estado de arista difiere. Las tres, por id de
  arista ascendente. En `:changed`, `:before` y `:after` son los **estados de la arista**
  (una de las tres keywords) en `r1` y en `r2`; se llaman así, y no `:from`/`:to`, para no
  confundirlos con los dos extremos de la arista.

**Determinismo.** Toda secuencia devuelta es determinista y **ninguna** se devuelve como
conjunto o mapa cuando el orden importa: los ids —de arista, de nodo y de árbol— se ordenan
siempre por el orden **ascendente** de string, y las posiciones salen en el orden de
recorrido descrito arriba. Cómo
se indexe por dentro (de una vez por revisión, con caché o recomputando) es elección del
implementador: no es observable, porque el resultado está fijado.

**Errores** (`ex-info` con `:type`, docs/v2/03 §2; ninguna consulta devuelve un valor de
error):

- `:anchor/unknown-revision` `{:revision r}` — lo lanza cualquiera de las siete consultas
  cuando `rev-id` (o `r1`/`r2`) no está en `(:revisions store)`.
- `:anchor/not-an-anchor` `{:edge e :revision r}` o `{:edge e :type t}` — solo
  `anchor/state`, cuando el id no es una arista activa de la revisión o cuando lo es pero
  su tipo no está en `anchor-types`.

### 6.4 Fingerprint de los anclajes externos (hito 5a)

Un nodo `:sign/:external` es `{:locator {:kind k …} :sample "…" :fingerprint "<alg>:<hex>"}`.

- `alg` es el nombre del algoritmo del store (`sha-256` en el primer slice, §8.1) y `hex`
  va en minúsculas. El kernel **valida la forma y compara por igualdad**; nunca recomputa
  el digest, porque no hace I/O.
- Qué bytes se digieren depende del `:kind` del locator y lo produce el **motor** que
  emite el anclaje, que queda registrado en `:engine`/`:version` de la evidencia:

| `:kind` | bytes |
|---|---|
| `:file` | el contenido del fichero, tal cual |
| `:url` | el cuerpo de la respuesta, tal cual |
| `:text` | UTF-8 del `:sample` en NFC |

- La comprobación vive en `node/validate`, junto a las demás de forma, de modo que
  `node/make` levanta `:node/invalid` (docs/v2/02 §2.1) ante un `:fingerprint` mal formado
  o con un algoritmo que no es el del store — el que devuelve `(algorithm (hasher host))`.
  Un locator de otro `:kind` es admisible: el kernel no cierra la lista, solo exige la forma.
- El estado de un anclaje externo se calcula como el de cualquier otro (§6.2). El nodo
  `:external` es content-addressed, así que un recurso que cambia produce un nodo
  distinto; si ninguna transacción registró la sucesión, el viejo queda `orphan` y es
  candidato de §6.5 por `:fingerprint` (mismo locator, huella distinta).

### 6.5 Reconciliación de `drifted` (hito 5b)

`sldb.kernel.reconcile`, sobre los extremos `orphan` de la revisión:

```
(reconcile/orphans store rev-id)
  → {<node-id> [<edge-id> …]}      ; un extremo orphan → los anclajes que lo tocan

(reconcile/proposals store rev-id opts)
  → [{:old <id> :candidate <id> :confidence <0..1> :method <kw>
      :edges [<edge-id> …] :evidence {…}} …]
  opts {:base <rev-id>            ; por defecto, el único padre de rev-id
        :min-confidence 0.6
        :all? false}
```

`:base` solo tiene defecto cuando la revisión tiene **exactamente un padre**. Con cero
padres (la revisión raíz) o con varios (cuando haya merges), pasarlo es obligatorio y su
ausencia levanta `:reconcile/base-required`; el método `:position` es el único que lo usa,
y adivinar un padre sería inventar la historia contra la que se compara.

La entrada es `reconcile/orphans`, **no** los ids de arista de `anchor/report`: los
métodos hablan de nodos, no de aristas. Se reconcilia **cada extremo `orphan` de cada
anclaje activo**, de modo que una arista cuyos dos extremos son `orphan` produce dos
entradas independientes; los ids de arista van en el `:edges` de la propuesta solo para
que el llamante sepa a qué anclajes afecta aceptarla. Los nodos ordenados por id
ascendente.

Una **propuesta vive fuera del pool**: no es nodo ni arista, no se persiste, no cambia
nada del store. Es un valor devuelto que un actor acepta o descarta.

Métodos, en orden de precedencia; cada uno da como mucho un candidato por `(old, método)`:

| método | condición | confianza |
|---|---|---|
| `:position` | `X` ocupaba el path `p` del árbol `T` en la revisión `:base`, en `R` ese `p` de `T` lo ocupa `Y ≠ X`, y `Y` tiene la misma `class` y el mismo `kind` que `X` | `1.0` si `Y` no ocurría en `T` en `:base`; `0.9` si ya ocurría |
| `:fingerprint` | `X` e `Y` son `:sign/:external` con el mismo `:locator` y `:fingerprint` distinto, e `Y` resuelve en `R` | `1.0` |
| `:sample` | `X` e `Y` tienen el mismo `kind` y texto comparable (`:text`→`:text`, `:opaque`→`:blob`, `:external`→`:sample`), `Y` resuelve en `R`, y `dice(X,Y) ≥ :min-confidence` | `dice(X,Y)` |

Un método que no aplica al nodo simplemente **no produce candidato**; nunca es un error.
En particular `:position` no aplica a `:symbol` ni a `:fact`, que en el primer slice no
ocupan posiciones (§6.2), ni a un `:span`, que tampoco: para todos ellos el conjunto de
posiciones en `:base` es vacío y el método no dice nada.

Si `X` ocupaba **varias** posiciones en `:base`, se examinan en el orden que ya fija
`:positions` (§6.3: árboles por id ascendente y, dentro de cada árbol, paths en orden de
documento) y gana la **primera** que produce candidato; la confianza la decide ese
candidato, no los demás. Así el método sigue dando como mucho un candidato y el resultado
no depende del orden de recorrido de ningún mapa.

El `:evidence` de una propuesta es informativo —no llega nunca a la arista ni se
persiste— y lleva, por método: `:position` → `{:tree <id> :path <path> :base <rev-id>}`;
`:fingerprint` → `{:locator <locator>}`; `:sample` → `{:dice <0..1>}`.

`dice(a,b)` sobre el **multiconjunto de trigramas de grafema** del texto en NFC:
`2·|A ∩ B| / (|A| + |B|)`, con la intersección tomada como mínimo de multiplicidades. Un
texto de menos de tres grafemas aporta un único gramo, el texto entero. Dos textos vacíos
dan `1.0`; un vacío contra uno no vacío da `0.0`.

Determinismo: numerador y denominador son enteros y hay una sola división
(`(/ (double num) den)`), sin redondeo, así que el valor es idéntico en cualquier host
IEEE-754. Las propuestas se ordenan por confianza descendente y, a igualdad, por id de
candidato ascendente. Ese orden es el mismo con `:all?` y sin él: `:all?` solo cambia **qué
se conserva** —todas las que encontró cada método, o únicamente la mejor por `old`—, nunca
cómo se ordena la lista resultante.

**Aceptación.** Aceptar es una transacción ordinaria y explícita:

```
(reconcile/accept-plan store rev-id proposal {:actor a :timestamp t})
  → TransactionPlan con un solo :add-edge {:type :supersedes :from candidate :to old
                                           :evidence {:actor a}}
```

`accept-plan` comprueba que la revisión existe (`:anchor/unknown-revision`) y que los dos
nodos de la propuesta están en el pool (`:reconcile/unknown-node`); no comprueba nada más,
porque aceptar una propuesta que ya no vale la rechazará el propio plan. El kernel no
aplica nada: lo aplica el actor con `store/commit!`. Al aplicarse, §6.1
re-ancla `reference` y `binding` al sucesor, invalida `derived`, deja `semantic` y
`projection` para revisión, y el anclaje pasa de `orphan` a `superseded`. La confianza y
el método **no** entran en la arista: la evidencia de un `supersedes` es su `:actor`
(§3.2), porque quien responde de la sucesión es quien la aceptó, no el heurístico que la
propuso.

## 7. Capas de superficie: CST y modelo estructural neutro

```
texto de cualquier formato
   │  tree-sitter (inyección por rangos) / parser conforme (micromark, commonmark)
   ▼
CST por formato                     invariante: concat(tokens) == texto
   │  mapeo estructural, lossy, explícito, por formato
   ▼
AST estructural neutro              invariante: parse_f(render_f(A)) == A  para A canónico
   │
   ▼
árbol-índice de documento (ownership) + hojas de texto plano
```

- El orden importa: **no se puede trocear en secciones antes de parsear** (un `# ` dentro
  de un fence o de un bloque HTML no es heading). El chunk es salida del parse de bloques;
  los sub-parsers se inyectan en los rangos resultantes.
- El modelo neutro es pequeño (referencia: pandoc, ~30 tipos) y tiene `Raw` como
  escotilla. Lo que no mapea se conserva como nodo `:opaque` con hash; sigue siendo
  idempotente, deja de ser direccionable por dentro.
- Lo que se pierde al canonicalizar (delimitador de énfasis, marcador de lista, estilo
  de heading, líneas en blanco) es pérdida de **estilo**, aceptable porque el kernel es
  dueño del formato (modelo `prettier`). Lo que se pierde en `Raw` es **direccionabilidad**,
  y es lo único que importa reportar.
- El emisor de cada formato debe ser inverso-correcto (escapes, colisión de fences,
  fusión de listas y énfasis adyacentes). Se verifica con property-based testing sobre
  ASTs generados, no con fixtures a mano.

## 8. Runtime

| Decisión | Elección | Motivo |
|---|---|---|
| Lenguaje del kernel | **Clojure, `.cljc` puro** (datos + funciones, sin I/O) | inmutabilidad y estructuras persistentes son el modelo; el host es un adaptador |
| Lisp separado | **no** | Clojure es el Lisp; se conserva el boundary *planes como datos EDN* |
| Scripting no confiable | **SCI** con allowlist de namespaces | es el `capability-model`; lo que usa Babashka |
| Rust | **cero en el repo** | se consumen artefactos WASM (tree-sitter, BLAKE3) hechos en Rust |
| Host CLI | **Babashka** | binario nativo, arranque instantáneo, sin JVM para el usuario |
| Host UI / editor / browser | **ClojureScript sobre Node** | datascript, `web-tree-sitter` (WASM), ProseMirror, `sql.js`/`better-sqlite3` |
| Clojure → WASM | **no todavía** | GraalVM native-image WASM es experimental; jank es temprano |
| Índice de consulta | **Datascript** (reconstruible) sobre log append-only | "el log es inmutable; los índices no" |
| Persistencia | log de transacciones + CAS de nodos (archivos o SQLite vía pod / WASM) | reconstruible; sin dependencia de backend |
| Hashing | BLAKE3 si el host lo ofrece; SHA-256 como fallback | el hash es campo canónico, no metadato de adapter |
| Python | adaptador opcional (git, orquestación) vía subprocess/HTTP | no hay FFI; no es centro arquitectónico |

La decisión bb vs Node como host *definitivo* **no se toma en el primer slice**: el kernel
`.cljc` está escrito para correr en ambos, pero los hitos 0–3 se validan **solo en
Babashka** (§8.1); la paridad Node es una task posterior, y la UI o tree-sitter forzarán
la elección hacia Node + WASM cuando toque.

### 8.1 Decisiones fijadas para el primer slice (hitos 0–3)

| tema | decisión |
|---|---|
| hash | SHA-256 a través del protocolo `Hasher` del host (bb: `java.security.MessageDigest`; cljs/Node: `crypto`). BLAKE3 cuando exista en ambos hosts. El algoritmo queda registrado en el descriptor del store |
| backend del hito 3 | solo archivos, con la semántica de ficheros de **Babashka/JVM** (Node llega con la task de paridad de host): `<store>/objects/<hash>` (CAS; cada objeto —nodo, objeto de árbol, arista, edge-set, revisión— se escribe como **texto UTF-8 con sus canonical-bytes EDN**, de modo que `H(contenido del fichero) == nombre`), `<store>/log.edn` (append-only, una línea por transacción con el mapa `Transaction {:id :plan :revision}`), `<store>/heads.edn` (mapa `{tree-id revision-id}` escrito a fichero temporal y renombrado; CAS por comparación con el contenido leído), `<store>/store.edn` (descriptor: `:hash-alg`, `:capabilities`, `:format-version`). `open` = leer descriptor + heads y **replay**: re-aplicar cada plan del log en orden y comprobar que los ids de revisión obtenidos coinciden con los registrados. `verify` = recomputar el hash de cada objeto alcanzable desde los heads y compararlo con su nombre. `rebuild-indexes` queda para cuando existan índices derivados (hito 4+) |
| NFC | `java.text.Normalizer/normalize` con `Form/NFC` en Babashka/JVM; `String.prototype.normalize("NFC")` en cljs; detrás del mismo protocolo de host que el `Hasher` |
| tests | `clojure.test` + `clojure.test.check` (incluido en Babashka). "Tests generativos" = propiedades test.check sobre generadores que cubren **todas las filas class/kind de §2.1**, árboles y planes; propiedad mínima: para todo plan válido, aplicar es determinista y `verify` sobre el store recargado reproduce los mismos hashes |
| fixtures dorados | `test/fixtures/nodes.edn`: vector de `{:class :kind :content :expected-id}`, una entrada por fila de §2.1. `test/fixtures/trees.edn`: `{:nodes [...] :tree {:kind :document :root <alias> :children {<alias> [<alias> ...]}} :expected {:objects {<alias> <tree-hash>} :root <merkle-root>}}`. `test/fixtures/tx-<n>.edn`: `{:store {:capabilities {...}} :plan {...} :expected {:revision {:roots :trees :edges :parents :tx :actor :engines :timestamp} :revision-id ...}}` con `:timestamp` fijado en el plan para que el id sea reproducible; el test compara **los ocho campos y el id**. Generadores compartidos en `test/sldb/kernel/generators.cljc` (`gen-node`, `gen-tree`, `gen-valid-plan`, `gen-plan-sequence`), creados en el hito 0 y ampliados en cada hito. Los ids esperados se calculan una vez con la implementación, se revisan a mano y se congelan |
| hosts | el primer slice se valida en Babashka (`bb test`). ClojureScript/Node es una task propia posterior ("host parity"). Todo es `.cljc`; los reader conditionals solo en `sldb.host.*` |
| M/G en hitos 0–3 | solo formas de datos (§2.1); sin evaluación lógica; el adaptador Matrix llega en el hito 7 |
| evidencia de ejecución | salida de `bb test` en `runs/subagents/<run>/validation.log` + commit |
| toolchain | Babashka ≥ 1.3 en el host del ejecutor (`bb --version`); `bb.edn` con task `test` |

## 9. Roadmap (por analogía con Git)

Cada hito es un vertical slice verificable, no una capa de abstracción.

| # | Hito | Análogo Git | Criterio de salida |
|---|---|---|---|
| 0 | **Blobs**: pool de nodos content-addressed en memoria; hash canónico; clases S/M/G | `hash-object`, `cat-file` | mismo contenido ⇒ mismo id; suite de conformidad de hashing = fixture `nodes.edn` + propiedades de §8.1 (insensibilidad al orden de claves y sets, sensibilidad al orden de vectores, NFC, class/kind en el id, put/get idempotente) |
| 1 | **Trees**: árboles-índice con `ownership` ordenado; Merkle perezoso; un nodo en N árboles | `write-tree`, `ls-tree` | cambiar un nodo invalida solo las ramas correctas de cada árbol |
| 2 | **Commits**: revisiones, transacciones, `TransactionPlan` EDN validado, `supersedes`, CAS de heads | `commit`, `log`, `diff` | describir a mano una transacción y obtener exactamente su revisión; diff entre revisiones |
| 3 | **Persistencia**: log append-only + CAS en disco; reload reproduce estado e historial | `.git/objects`, refs | cerrar y abrir el proceso ⇒ mismo estado, mismos hashes |
| 4 | **Primera superficie**: Markdown → CST → AST neutro → árbol-índice + hojas; emisor inverso-correcto; regiones `opaque` | `add`, `checkout` | `parse(render(A)) == A` para todo `A` canónico, por property testing; informe de direccionabilidad por documento |
| 5a | **Estados de anclaje**: estados deterministas `intact/superseded/orphan` por extremo y por arista; consultas "qué está anclado en qué", informe y diff de anclajes; forma del `:fingerprint` externo | `blame` | detachar el nodo anclado deja el anclaje `orphan`; registrar `supersedes` lo deja `superseded` y re-ancla según §6.1; toda consulta es derivada y reproducible desde el log |
| 5b | **Reconciliación de `drifted`**: propuestas `{old candidate confidence method}` fuera del pool por posición, fingerprint y muestra; aceptación como transacción `supersedes` | — | un `.md` editado fuera del kernel deja anclajes `orphan`, la reconciliación los reclasifica `drifted` nombrando al sustituto, y aceptar la propuesta los deja `superseded` y re-anclados |
| 6 | **Stand-off**: hojas con offsets, capas UAX #29 bajo demanda, anclaje `(hoja, rango, hash)` | — | dirección hasta el grafema sin nodos materializados |
| 7 | **M y G**: símbolos canónicos, `binding` bajo `W_i`, hechos con estado de sentido; adaptador Matrix | — | un símbolo referenciado por N signos; consolidación validada |
| 8 | **Retención y GC**: alcanzabilidad desde heads, pins, nodos huérfanos | `gc`, `prune` | export/import sin backend; `verify` y `rebuild-indexes` |
| 9 | **Sync**: pools entre stores | `fetch`/`push` | fuera de alcance hasta cerrar 0–8 |

Los hitos 0–3 son el **primer slice** y son `.cljc` puro sin I/O salvo el 3. Embeddings,
proveedores semánticos y superficies visuales quedan explícitamente fuera hasta el 7.

## 10. Invariantes

```
1.  Ningún nodo ni revisión existente se modifica.
2.  id(nodo) = hash(contenido canónico); mismo contenido ⇒ mismo id.
3.  Toda mutación produce una revisión nueva que apunta a una transacción válida.
4.  Las aristas ownership de un árbol forman un árbol; el Merkle de un árbol
    hashea solo esas aristas.
5.  Un nodo puede ocurrir en N posiciones de N árboles; cada posición tiene un padre.
6.  Toda arista entre capas o hacia lo opaco/externo lleva evidencia (hash + provenance).
7.  La sucesión se registra con `supersedes`; nunca se infiere dentro de una transacción.
8.  concat(tokens(parse(s))) == s para todo CST.
9.  parse_f(render_f(A)) == A para todo AST estructural canónico A y formato f.
10. Lo derivado se reconstruye desde log + pool; no tiene autoridad.
11. Un efecto externo nunca muta el grafo directamente.
12. Toda operación sobre un nodo :opaque es reemplazo del blob completo, o se rechaza.
13. Los ids de árbol son nominales (refs); los ids de nodo, arista y objeto de árbol
    son content-addressed.
14. El orden de hermanos forma parte del hash de cada objeto de árbol.
15. Un TransactionPlan se aplica entero o se rechaza entero.
16. El timestamp no entra en el id de ninguna arista; entra en el id de la revisión.
17. Todo objeto persistido (nodo, objeto de árbol, arista, edge-set, revisión) cumple
    H(bytes del objeto) == su nombre en el CAS.
18. El estado de un anclaje es derivado: se recomputa desde revisión + pool, no se
    persiste, y una propuesta de reconciliación no es nodo ni arista hasta que un
    actor la acepta como `supersedes`.
```
