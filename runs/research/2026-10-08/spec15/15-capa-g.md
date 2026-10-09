# kimun — La capa G: hechos, contextos, verdad, conflicto y consulta

> Despliega `14-juego-del-lenguaje.md`; no lo repite ni lo contradice: lo cita. Donde `14`
> decidió (decisiones 21–39), aquí solo se fija la forma exacta. Donde `14` dejó un hueco,
> se resuelve y se marca "**revisa**" o se lleva a §12 (preguntas al dueño). Autoridad:
> por debajo de `14` (ver `docs/v2/README.md`); por encima de `12`. Contrato del kernel:
> `02`; formato en disco: `10`; protocolo de adaptadores: `11`. Este documento es el dueño
> de la **capa G**: `(R A B) @ W_i` con evidencia, verdad, sentido, conflicto, anclaje,
> índice y consulta.
>
> Insumos: `14` completo; `05` decisiones 21–26, 30–38, 40; `02 §2`, `§3.2`, `§6`;
> `11 §1`; `12 §3`; atoms `atom-w-i-context-node-and-index`,
> `atom-evidence-required-per-edge-type`, `atom-canonical-projected-or-asserted`,
> `atom-drifted-is-a-reconciled-orphan`; spikes R1 (Datalog bajo Babashka) y R3 §5
> (Datalevin 1.2.0 no está en el registry; in-process es el camino estable); specs de
> wittgenstein (`SPEC.md`, `matrix.md`, `grounding.md`, `provenance.md`, `convergence.md`).
> Fixtures: `test/fixtures/g/` (anexo de §13).

> **Estado (2026-10-08): BORRADOR NO CERRADO.** Pasó seis rondas del gate de cero contexto
> (informes en `runs/gates/spec15/`). La ronda 6 dejó hallazgos abiertos, listados en §14 como
> deuda conocida. No es autoridad hasta cerrarlos y hasta que el dueño responda §12.

## 1. Qué es un hecho en kimun

Un hecho es un nodo G con la forma de `02 §2.1`. `15` la fija así:

```clojure
{:class :fact :kind :triple
 :content {:subject   <id-de-:symbol/:term>
           :predicate <id-de-:symbol/:term>
           :object    <id-de-:symbol/:term>
           :context   <id-de-:fact/:context>}}
```

- `R`, `A` y `B` son **ids de nodos `:symbol/:term`**, nunca strings ni literales crudos
  (`14 §4`). Un literal se representa **siempre** como un término de la taxonomía `literals`
  cuyo `:name` es su forma canónica (p. ej. `"3.14"`, `"2026-10-09"`); el tipo lo da su
  posición en esa taxonomía. Un literal es un término también dentro de `:form`
  (no hay literales crudos en las hojas de una proposición). **Revisa** `02 §2.1`
  (`:object <id|literal>` pasa a `:object <id>`).
- `:context` es el id del nodo `:fact/:context` de su `W_i` (`atom-w-i-context-node-and-index`).
  Dos `(R A B)` en contextos distintos son dos nodos distintos (el contexto entra al hash).
- El término atómico:

```clojure
{:class :symbol :kind :term :content {:name "<NFC>" :lang "es"}}
```

- La proposición que un hecho instancia es un símbolo M (`14 §4`):

```clojure
{:class :symbol :kind :proposition :content {:form [<pred-id> <subj-id> <obj-id>]}}
```

  `:form` admite formas anidadas (reglas `(if …)`, `14 §4`) para otras M; un **hecho** proyecta
  siempre una forma **plana** `(R A B)` de tres hojas (fija la ambigüedad «forma de la
  proposición»). Sus hojas son **ids de término** (un literal es un término de `literals`,
  § arriba). Un hecho siempre tiene al menos una proposición de la que es
  proyección (§3): el hecho es el **estado** de la proposición en un `W_i`. Es una
  **invariante del pool** (no solo del plan): un `:add-node` de `:fact/:triple` sin una arista
  `:projection` hacia él **en el mismo plan** se rechaza (`:plan/rejected`), y un
  `:remove-edge` de la **última `projection` activa** de un hecho se rechaza **salvo** que el
  mismo plan retracte el hecho (sucesión + `:status :retracted`): un hecho nunca queda sin
  proyección activa. `02 §2.1` describe la forma del nodo, no su admisión al pool
  (lectura (b) de la ambigüedad «hecho sin proyección»).

### `R`, `A`, `B` en árboles de taxonomía

- Un término ocupa una posición en un árbol-índice `{:kind :taxonomy}` (`02 §3.1`). La
  **ascendencia es la posición**: los ancestros de un término son los de su path en cada
  taxonomía; `isa?` es la relación "prefijo de path" en el árbol. El valor `hierarchy`
  (`make-hierarchy`/`derive`/`isa?`, `14 §4`) es su **vista derivada** (cache), no un
  segundo registro: misma regla que "canónico ≠ persistido" (`14 §2`); decidido por `05`
  decisión 23 (`R`/`A`/`B` son posiciones en taxonomías con herencia).
- Taxonomías de primera clase que este documento fija, con nombre y forma (**revisa**
  `02 §2.1`, que no las nombra: los nombres son una propuesta de `15`, §11):
  `{:kind :taxonomy :name "terms"}` (raíz `thing`), `{:kind :taxonomy :name "relations"}`
  (raíz `relation`), `{:kind :taxonomy :name "literals"}` (raíz `literal`),
  `{:kind :taxonomy :name "facts"}` (raíz `fact`) y `{:kind :taxonomy :name "sense"}`
  (raíz `sense`). Una taxonomía
  admite cada nodo en **una** posición; un término puede vivir en varias taxonomías (un padre
  por árbol, `02 §3.1`). El `:root` de una taxonomía de vocabulario es un
  **nombre** (la etiqueta de la raíz, p. ej. `"thing"`), mientras que el `:root` del
  descriptor de árbol (`02 §3.1`, `{:tree <id> :kind … :root <node-id>}`) es un **id** de
  nodo; son campos homónimos de objetos distintos.

### El vocabulario `L_i`

El vocabulario admisible de un contexto (`14 §4`) es un **documento `source` EDN** (decisión
27). Forma exacta:

```clojure
{:vocabulary/version 1
 :context {:name "<NFC>" :granularity :document|:section|:paragraph|:corpus|:transversal
           :of {...}}                                  ; el mismo mapa de §2
 :languages ["es" "en"]
 :taxonomies [{:name "terms" :root "thing"}
              {:name "relations" :root "relation"}
              {:name "literals" :root "literal"}
              {:name "facts" :root "fact"}
              {:name "sense" :root "sense"}]
 :relations [{:name "comparte"
              :axis ["thing" "thing"]                  ; raíces de taxonomía: A bajo la 1ª, B bajo la 2ª
              :tiers #{:observed :confirmed :inferred :statistical}}]
 :promotions [{:rule "equivalent"
               :when {:phi "0.95" :directions :both :min-configs 2 :min-contexts 2}
               :to :confirmed :actor "policy/equivalence"}]}
```

- Todos los valores entran a `canonical-bytes` (`02 §2.1`): **sin flotantes**; un umbral se
  escribe como string decimal (`:phi "0.95"`) o entero de milésimas. `NFC` siempre.
- **`:relations` no lleva `:properties`.** El álgebra de `R` en un contexto tiene una sola
  fuente: los hechos `(property R p) @ W_i` (abajo). El vocabulario solo declara qué
  relaciones se admiten, sus ejes y sus `:tiers`; no hay tabla aparte.
- **`:axis [t1 t2]`** cita la **raíz** de una taxonomía declarada en `:taxonomies`: A ocupa
  una posición bajo la primera, B bajo la segunda. No admite nombres de taxonomía, sub-paths
  ni strings libres (fija la ambigüedad de `:axis`).
- **`:tiers`** es el subconjunto admisible en ese contexto; el orden global de §3 solo ordena
  los admisibles (es la precedencia de deducción). Afirmar un tier fuera de `:tiers` se
  rechaza.
- **Vocabulario base.** Todo `L_i` declara, además de las del producto, dos taxonomías y una
  relación. La taxonomía `facts` (raíz `fact`) hace admisible el **término-hecho**: un término
  suyo designa el nodo `:fact/:triple` que se marca. La taxonomía `sense` (raíz `sense`) lleva
  los términos `sinnvoll`, `sinnlos` y `unsinnig`. La relación `sense`:
  `{:name "sense" :axis ["fact" "sense"] :tiers #{:observed :confirmed}}`; un humano la usa para
  marcar el sentido de un hecho. Su extensión son hechos `(sense <fact-term> <sense-term>) @ W_i`,
  igual que las propiedades (abajo): el sujeto ocupa una posición bajo `fact` y el objeto una
  bajo `sense`, así que ambos extremos son admisibles.
- Ingestar el vocabulario (`contexts define`, §9) crea, en una transacción: el nodo
  `:fact/:context`, su árbol `{:kind :context}`, los términos y relaciones admisibles como
  hijos y un signo `:sign/:opaque {:format "edn/vocabulary" :blob "<canonical-bytes>"}` con
  el texto del documento como evidencia. **`contexts define` no crea hechos de propiedad**:
  las propiedades `(property R p) @ W_i` se afirman como hechos con su propia
  evidencia (actor o motor), igual que cualquier otro hecho del `W_i`. El documento vive en
  el repo; el store se reconstruye desde él (decisión 27).
- Las propiedades algebraicas de `R` son **hechos** de la forma
  `{:subject <R> :predicate <"property"> :object <p> :context <W_i>}` (`14 §4`), afirmados
  con su propia evidencia (`:actor` o `:engine`+`:version`) y su `:status`/`:curation`/`:tier`. El
  evaluador booleano (§8) las lee de ahí; no hay tabla aparte ni las deriva el vocabulario.
  Fixture: los hechos de propiedad `:fact/f2`, `:fact/f2b` y `:fact/f4` (`facts.edn`).

## 2. Contextos `W_i`

Un `W_i` es un nodo `:fact/:context` **y** un árbol-índice `{:kind :context}` cuyos hijos son
los términos y relaciones admisibles (`atom-w-i-context-node-and-index`). `14 §3`. **El árbol
`:context` no cuelga hechos** (ni proposiciones): sus hijos son solo vocabulario; la pertenencia
de un hecho a su `W_i` se reconstruye por el nodo `:fact/:context` que el hecho cita en
`:content :context` (§4, §6). Forma
exacta del nodo (**revisa** `02 §2.1`):

```clojure
{:class :fact :kind :context
 :content {:name "<NFC>"
           :granularity :document|:section|:paragraph|:corpus|:transversal
           :of {:path "desk/atoms/atom-x.md"}                 ; :document, :section, :paragraph
           ;  {:path "…" :section [1 0]}                       ; :section, :paragraph
           ;  {:globs ["desk/atoms/**/*.md"]}                  ; :corpus
           ;  {:transversal "data-class"}                      ; :transversal (§2, decisión 34)
           }}
```

- `:of` referencia **rutas y spans relativos**, nunca ids nominales del store: un `rebuild`
  con los mismos archivos produce el mismo contexto y los mismos hechos (decisión 27).
  Excepción: la forma de transversal `{:transversal "data-class"}` nombra el
  transversal (un valor del pack de decisión 34), no una ruta; es la única forma de `:of`
  que no es ruta/span.
- **Contexto por defecto:** cada documento `source` abre su propio `W_i` (decisión 22),
  `:granularity :document`, `:name "doc:<path-relativo>"`. `source` es el rol del archivo raíz
  (`14` decisiones 22/23/27); su ingesta crea el `W_i` sin instrucción extra.
- **Más fino:** `:section`/`:paragraph` con `:of {:path … :section [i j …]}` (el path del
  bloque en el árbol del documento, `02 §3.1`), `:name "doc:<ruta-del-archivo>#<path-del-bloque>"`
  (las dos ranuras no se confunden: la ruta del fichero y el path del bloque dentro de él).
  **Más grueso:** `:corpus` con `:of {:globs […]}`. Los contextos `:section`/`:corpus`/`:transversal`
  se declaran por instrucción (`contexts create`, §9): la instrucción se materializa como
  documento de decisión `:action :create-context` (§4) con el nodo `:fact/:context` completo (no
  solo su id), de modo que `include build` lo reconstruye desde archivos + decisiones.
- Un hecho nombra su `W_i` por el id del nodo en `:content :context` y por `:context` en la
  evidencia de sus aristas; no por el nombre. El mismo signo puede ligarse a símbolos
  distintos en `W_i` distintos, una arista por contexto (`02 §3.2`).
- **Ruteo entre contextos.** Una pregunta cruza mundos solo por aristas **promovidas**
  (`14 §3`): `equivalent` (bidireccional, `φ ≥ 0,95` en ambos sentidos) y `projection`
  (unidireccional), nunca por coincidencia de cadenas. La identidad se propone por
  convergencia (CHIC/SIA, wittgenstein `convergence.md`) y se confirma por decisión
  registrada (decisión 35). La promoción del vocabulario promueve **hechos** (su
  `:status` a `:confirmed`); «arista promovida» significa que la arista `:semantic` de ruteo
  lleva el registro de la confirmación por decisión (decisión 35) en su evidencia, no que la
  arista tenga un tier propio que promover. La política `:promotions` del vocabulario (§1) es
  la que decide qué hechos promocionan (`:to :confirmed`) cuando la equivalencia se confirma,
  y esa promoción es una transacción registrada (decisión 35): no son lo mismo ni se
  sustituyen. Forma de la arista de ruteo: §12 Q6 (default (b)); `15` propone, coherente con
  el default,
  `{:type :semantic :evidence {:relation :equivalent|:projection :context <W_i>
  :context-target <W_j> :phi "<str>" :ref-hash <:to>}}`. La relación de ruteo
  es un **valor** de `:relation` (`:equivalent|:projection`) dentro de la evidencia de una
  arista `:semantic` (no un tipo de arista propio ni el tipo de arista `projection` M→G de
  §3); `:ref-hash` sigue siendo el `:to` de la arista, y `:context-target` identifica el `W_j`
  destino. Esa ampliación de la evidencia de `semantic` es una **revisión de `02 §3.2`**,
  listada en §11, mientras el dueño no fije un tipo propio.
- **Transversales de primera clase** (decisión 34): el pack mínimo de autodescripción. Se
  materializa como contextos `:granularity :transversal` y sus taxonomías:
  `data-class` (`text`/`code`/`other`), `provenance` (`source`/`tier`), `context`
  (los propios `W_i`) y `self` (la autodescripción de kimun: el store y sus modelos,
  decisión 34). La pertenencia es un hecho `(member X <term>) @ W_transversal`, propuesta por
  el selector (`:curation :proposed`) o asentada por un actor (`:status :observed`, gana;
  decisión Q3/35). Los contextos
  `:granularity :transversal` tienen su propio vocabulario `L_transversal` (§1) que
  **declara la relación `member`** (eje y `:tiers` propios), de modo que `member` es
  admisible; el vocabulario del contexto transversal no
  hereda el del `W_i` que clasifica. Los transversales son árboles persistidos porque son
  conocimiento, no estructura derivable (`14 §16 P13`). La lista concreta de términos de
  `self` (`store`, `model`, `context`, `fact`, `edge`, `sign`, `tree`) es una **ampliación** de
  decisión 34 (que nombra el transversal, no sus términos) y queda como §12 Q8.

## 3. Afirmación, verdad y sentido

Se separan tres cosas que `12 §3` tenía mezcladas (decisión 24, `14 §17` Q7):

| qué | dónde | valores |
|---|---|---|
| **afirmación** (quién dijo qué) | evidencia de la arista `projection` proposición→hecho | `:status`, `:curation`, `:tier`, un origen (`:actor` o `:engine`+`:version`), `:context`, `:prov` |
| **valor** `V` | índice derivado por `(hecho, W_i)` (§8) | `TRUE` \| `FALSE` \| `UNKNOWN` |
| **sentido** `S` | índice derivado por `(hecho, W_i)` (§8) | `sinnvoll` \| `sinnlos` \| `unsinnig` |

- **Afirmación.** Evidencia de la `projection` (M→G, `02 §3.2`):

```clojure
{:ref-hash <fact-id>
 :context  <W_i>
 :status   :observed|:confirmed|:rejected|:retracted   ; enum de `14 §5`; nil sin decisión
 :curation :proposed|:accepted
 :tier     :observed|:confirmed|:inferred|:statistical
 :actor    "human/jp"                       ; o :engine + :version, exactamente uno
 :prov     [{:path "…" :source-hash "sha-256:…" :span {:leaf <id> :range [a b]}
             :extractor "descompositor/heuristic@0.1" :confidence "0.97"}]}
```

  `:status` es el **estado de decisión** —el enum de `14 §5`—, `:curation` la **curación**
  (`:proposed` sin decisión, `:accepted` con ella) y `:tier` el **nivel epistémico**: tres ejes
  independientes. El nivel se ordena `observed > confirmed > inferred > statistical`, y
  **`statistical` nunca deduce**. La decisión registrada fija `:status` (observación de un
  actor, promoción, rechazo, retracción) y `:curation :accepted`; **la salida de un motor es
  siempre `:curation :proposed` y `:status` nulo** (`14 §7`, decisión 31): un motor nunca lleva
  a `TRUE` por sí solo. `:tier` y `:curation` son obligatorios y **no nulos** en la evidencia de
  `projection`; `:status` es obligatorio-presente y no nulo en cuanto hay decisión (enum de
  `14 §5`). El **sentido** (`sinnvoll|sinnlos|unsinnig`) no vive en la arista: es el índice
  derivado `S` de §8; por eso `02 §3.2` **revisa** (`projection :status` pasa de sentido a
  estado de decisión, con `:tier` y `:curation` obligatorios). Pregunta §12 Q1: si en su lugar
  se amplía `:status` con `:proposed`; **hasta que se responda, el default de `15` no revisa el
  14** —`proposed` vive en `:curation`— (así va marcado en §3, §7 y los fixtures). La clave de
  la procedencia es **`:source-hash`** y su etiqueta **interna `sha-256:<hex>`** (`02 §6.4`,
  decisión 11) en todos los sitios EDN de `15` y en los fixtures; el protocolo JSON usa la
  clave `source_hash` con etiqueta **`sha256:<hex>`** (`11 §4.1`) y kimun **convierte la
  etiqueta en la frontera** al construir la evidencia (mapeo en §7).
- **`V`/`S` derivados.** Se calculan por el evaluador (§8) y viven en `index/graph/`, **nunca
  como nodo** (decisión 26). Un `:sign/:opaque` no lleva verdad.
- **Reglas duras** (todas de `14 §5`):
  - Nada pasa de `UNKNOWN` a `TRUE` sin una decisión registrada (`:status :confirmed`
    —promoción— o `:status :observed` —aserción de un actor—, con actor, evidencia y
    sucesión), igual que aceptar una propuesta `drifted` (decisión 10). Un motor **nunca**
    lleva a `TRUE` por sí solo: su salida es `:curation :proposed` (`14 §5`, decisión 24/35).
  - `unsinnig` solo lo marca un humano; el silencio del corpus no es absurdo
    (`grounding.md §4`). La marca es un **hecho del pool**:
    `(sense <fact-term> unsinnig) @ W_i` con evidencia de actor (`:status :confirmed`,
    `:curation :accepted`) —`sense` es vocabulario base, §1— además de su documento de
    decisión `:action :unsinnig` (§4). `S` **deriva `unsinnig` de ese hecho**, nunca de la
    prosa ni de un archivo.
  - La promoción automática solo existe por **política declarada** del store o del contexto
    (decisión 31, harness): el mapa `:promotions` del vocabulario (§1) o el descriptor del
    store. Sin política, todo motor queda `:curation :proposed` hasta decisión.
  - Un `:rejected` se conserva (no se re-propone lo mismo, `provenance.md §2`); `:retracted`
    conserva la historia (`14 §6`).
  - La **detección de contradicción no es deducción**: el evaluador ve todos los
    hechos del `W_i`, incluidos los `:tier :statistical`; lo que `statistical` no hace es
    alimentar la deducción (`I`, cierre). Por eso dos hechos `:curation :proposed`/`:statistical` ya
    presentes en un `W_i` producen informe sin promoción (§5).

## 4. Escritura: revisión por acción y log de hechos en flujo

Decisión 33 (Q1=b): la unidad de escritura es la **acción** (build, assert, decisión), y
además hay un **log de hechos append-only en flujo** que se consolida en revisiones.

### El log de hechos

El flujo vive en `<store>/facts.log.edn`, append-only, **una línea por hecho** con su
procedencia. Su **forma en disco** (ubicación, gitignore, caché, la forma exacta de cada
línea, `:fact/entry`) la posee `10 §11`; aquí solo se fija su semántica. Bajo `:git :ignore`
es caché (decisión 27); el hecho reconstruible vive como documento de decisión o como
documento `source` de vocabulario.

- Una línea no es una arista ni un nodo: es una **intención de escritura** con aliases. Se
  consolida en un `TransactionPlan` de `02 §5.1` (`:add-node` + `:add-edge`, `:base` = head)
  y un `commit!`. Consolidar es determinista **en nodos y aristas**: los ids de nodo y de
  arista salen del contenido y el timestamp no entra al id de arista (`02 §3.2`), así que
  reintentar la consolidación es idempotente sobre ellos. Los **árboles** no: un árbol
  (incluido el de un contexto, `{:kind :context}`) tiene un **id nominal**, un ULID acuñado
  al crearlo (`02 §3.1`, inv. 13), así que re-crear un árbol de contexto **no es
  idempotente**. La pertenencia de un hecho a su `W_i` no depende de ese ULID: la reconstruye
  el **nodo `:fact/:context`**, content-addressed (`02 §2.1`), que es lo que el hecho cita en
  `:content :context` y en la evidencia de sus aristas; al reconstruir se reencuentra el
  contexto por ese nodo, no por el id del árbol. Dos revisiones con el mismo contenido y
  distinto timestamp no son la misma revisión (el timestamp entra al id de la revisión,
  `02 §10` inv. 16).
- **Cuándo se consolida** (pregunta §12 Q2): por defecto, en cada acción explícita
  (`facts consolidate`, `assert`, `contexts define`, `include build`) y al **abrir** un store
  con entradas pendientes. Al abrir, las entradas del flujo no consolidadas se informan y
  **nunca se descartan en silencio** (regla de `14 §7`); el aviso lo emite **la CLI tras
  `open`**, leyendo `facts.log.edn` por el host, no el puerto `Backend`: `open` no cambia y
  sigue leyendo exactamente `:head` y `:heads-root` (`10 §6`, `02 §8.1`). El aviso es una
  `:warnings` del envelope de `06 §1` (tipo `facts/pending-upstream`, `:data {:pending n}`),
  y un `--strict` las rechaza.
- El log de transacciones (`log.edn`) sigue siendo la autoridad de lo consolidado
  (`02 §8.1`); `facts.log.edn` es el flujo, no la historia.

### Decisiones humanas como documentos

Decisión 27/Q6: confirmar, desambiguar, retractar, promover, marcar `unsinnig`, asentar
(`assert`) y separar contextos se materializan como documentos `source` en el repo por
**composición grafo→documento** (la transacción se proyecta a un documento). Forma mínima:

```clojure
{:decision/version 1
 :action  :confirm|:reject|:retract|:promote|:separate-contexts|:wrong-source|:unsinnig|:assert|:create-context
 :target  {:fact <id> :edge <projection-edge-id>}     ; hechos: un hecho y su arista
          ; {:context <nodo completo> :tree <descriptor>}  ; :create-context
 :context <W_i>
 :actor   "human/jp" :timestamp "…" :note "…"
 :fact/entry {…}}                                     ; solo `:assert`/`--write`; contenido completo
```

- **`facts assert` siempre materializa un documento de decisión `:action :assert`**, con el
  **`:fact/entry` canónico completo**, no solo ids: los términos, la proposición,
  el hecho, la arista `binding`, la `projection`, los `:sign/:span` y la referencia a la hoja
  (`path` + rango + `fingerprint`). Un id es un hash no invertible: con `facts.log.edn`
  descartado (`:git :ignore`), `include build` no podría rehacer el nodo `:fact/:triple`, su
  proposición, `binding`, `span` ni hoja desde un `:target` de ids. Por la composición
  grafo→documento (decisión 27) el hecho asertado es reconstruible desde archivos + decisiones
  (`14 §2`); sin este documento, un `include build` limpio perdería el hecho.
- **Toda escritura de un `:fact/entry` deja documento de decisión.** `facts assert` y
  cualquier `--write` que aplique un `:fact/entry` (`facts plan --write`, §9) materializan el
  documento `:action :assert` con el mismo `:fact/entry` completo: no hay ruta de escritura de
  hechos que no sea reconstruible desde archivos + decisiones. La alternativa —declarar el
  `FILE` un documento `source` rastreado— no se fija; `15` fija la primera.
- **Contextos declarados.** La instrucción `contexts create` (§2, §9) que materializa un
  contexto `:section`/`:corpus`/`:transversal` deja un documento `:action :create-context` con
  el nodo `:fact/:context` completo (`:name`, `:granularity`, `:of`) y su descriptor de árbol
  `{:kind :context}`: `include build` lo reconstruye desde archivos + decisiones sin depender de
  una instrucción imperativa no versionada. Misma regla de reconstrucción que `:assert`.

- **Forma del nodo y perfil de ingesta.** El documento de decisión se ingiere como documento
  `source`: un nodo `:sign/:opaque {:format "edn/decision" :blob "<canonical-bytes>"}`, análogo
  al vocabulario (`:format "edn/vocabulary"`, §1) y a los modelos (`edn/model`, `07 §3`). Su
  perfil de ingesta es `edn/decision` (el perfil EDN de `source`, no el Markdown de `04`). En
  `include build` lo lee la superficie (ring 3): la fachada de inclusión `kimun.surface.include`
  lo despacha a su lector EDN `kimun.surface.decisions` (nuevo, análogo a
  `kimun.surface.models.store` para `edn/model`); el kernel no lo toca. §11 declara la revisión
  de `04` y `13`.
- El archivo vive bajo el directorio declarado en `kimun.include.edn` (propuesta: una clave
  `:decisions`; `13` es el dueño de la declaración). El formato lo fija `14 §12` decisión 27
  (Q6 respondida): **según el caso** —EDN para transacciones, Markdown para narrativa—; no es
  una pregunta abierta (Q3 eliminada).
- La composición es determinista y reversible: `include build` reaplica las decisiones y
  reproduce los mismos hechos afirmados (decisión 27, autopoiesis G/M→S).

## 5. Conflicto lógico

P4 y decisión 25. Es el **tercer** conflicto, junto al estructural (`ConflictSet`, `02 §5.2`)
y al de anclaje (`drifted`, decisiones 9–10).

- **Forma del informe:**

```clojure
{:conflict/id "<H(canonical-bytes del informe sin :conflict/id)>"
 :kind    :contradiction|:ambiguous
 :context <W_i>
 :facts   [<fact-id> …]
 :edges   [<projection-edge-id> …]
 :candidates [{:fact <id> :context <W_j> :phi "0.97"} …]}   ; solo :ambiguous
```

`:candidates` existe **solo** en `:kind :ambiguous`: en `:contradiction` la clave
está **ausente** (no `[]`). En un **fixture de aliases** `:conflict/id` puede ser `nil` como
placeholder explícito (el id real es el hash de los bytes canónicos y depende de los ids
definitivos); §13 lo usa así.

- **Dónde vive:** fuera del pool, como toda propuesta (decisión 10). Es caché derivada en
  `<store>/index/graph/conflicts/<conflict-id>.edn` (gitignored, reconstruible por cabecera;
  forma en disco en `10 §11`); no es nodo ni arista hasta que un actor lo resuelve. El caché
  solo se reconstruye **re-ejecutando el motor de convergencia** para los candidatos no
  promovidos: su `:phi` no vive en aristas del pool (solo el `:phi` de aristas `:semantic`
  promovidas, §2), así que no basta log + pool (inv. 10 de `02 §10`).
- **Quién lo produce:** el evaluador de sentido (§8), al unir contextos por ruteo promovido
  o al promover un hecho que vuelve inconsistente un `W_i` (`14 §6`). La contradicción es
  detectable **solo dentro de un `W_i`**; entre fuentes aparece al unir contextos (`14 §3`).
- **Tres resoluciones**, cada una una transacción registrada (documento de §4):
  (a) **separar contextos** — nuevo `W_i`, los hechos se re-anclan;
  (b) **retractar** — sucesión + `:status :retracted`, `V = FALSE|UNKNOWN`, con historia;
  (c) **marcar una fuente equivocada en ese contexto** — un hecho sobre el **anchor del
  documento**, no sobre el hecho. El anchor se nombra por un término (`:symbol/:term`, p. ej.
  `external:<path>`) que designa el `:sign/:external` (`02 §2.1`); `R`/`A`/`B` siguen siendo
  ids de término, nunca el id crudo del signo.
- **Consultas.** Un `W_i` con conflicto abierto **no bloquea** la KB: toda consulta que lo toca
  devuelve `:kind :ambiguous` con `:candidates` y **nunca elige** (`14 §6`), exit 2 (§9). Los
  candidatos se leen del informe: en una contradicción **interna** al `W_i` son sus hechos en
  conflicto, `{:fact <id> :context <W_i>}` y **sin** `:phi`; al unir contextos por ruteo
  promovido (`14 §3`) llevan `:phi`. El informe de detección sigue siendo `:kind :contradiction`
  internamente (con `:facts` y `:edges`, arriba); `ask` lo devuelve como `:ambiguous`.

## 6. Anchors de hechos

P5 y decisión 32. Procedencia `prov {path :source-hash span extractor confidence}` del
protocolo `propositions` (§7) → **aristas con evidencia hacia `:sign/:span` sobre hojas**.
El anchor de un hecho es un anclaje **estructural** (`14 §7`); los *anchors de evaluación*
(P8: `:sign/:opaque {:format "edn/value"}` con aristas `derived`, motores `sci`/`shell:`/`llm:`)
**no son anchors de hechos** y `15` no fija su forma: siguen en `14 §7` y
`docs/archive/v2/09 §4`, y se declaran fuera de alcance en §11.

- El motor emite spans en `code points` sobre el documento entero (wittgenstein,
  `populator.md`) — esa es la unidad del `span` del **protocolo** (§7, `"unit": "codepoints"`).
  kimun los mapea al entrar a la unidad de anclaje de kimun (decisión 32):
  `(hoja :sign/:text, rango de grafemas NFC)`. El mapeo es determinista: code points → hoja
  → grafema (`02 §4`, `§4.1`). El `:span` **interno** de la evidencia de una arista (§3) y el
  `range` de un nodo `:sign/:span` van en **grafemas de su hoja**, no en code points del
  documento; son dos formas distintas y no se mezclan.
- Un `[inicio fin]` (de `:sign/:span`, `02 §4.1`) es **semiabierto**: `inicio ≤ i < fin`, con
  `fin` exclusivo y `0 ≤ inicio ≤ fin ≤ n` grafemas. Así `02 §6.2` («`b ≤` número de
grafemas») es exacto y un span vacío (`inicio = fin`) es válido.
- Cada cita produce un nodo `:sign/:span {:leaf <id> :range [a b]}` materializado por el plan
  (`atom-stand-off-addresses-are-virtual-until-referenced`, `02 §4.1`) y una arista
  `binding` S→M (span → término/proposición) con **la clave `:prov` obligatoria** —un vector
  de spans, ≥ 1— y `:context` en su evidencia (la clave `:prov` es requisito de
  todo `binding`; los `binding` del fixture la llevan). La arista `projection` M→G lleva la
  misma `:prov` (§3). Anclar un hecho = su cadena de aristas hasta las hojas.
- **Hojas referenciadas por hechos.** La regla de `14 §10` («posiciones y nodos referenciados
  por alguna arista sí se persisten», forma 3, `10 §10`). Las hojas
  `:sign/:text` que son extremo de una cadena de anclaje de un hecho se persisten **con su
  texto** (inline obligatorio para ellas) aunque el store sea `:content :by-reference`. Así
  `anchor/resolves?` sigue siendo **puro, sin I/O** (`03 §1`: lee `[:content :text]` y cuenta
  grafemas), con texto en la hoja; una hoja que ningún hecho referencia no necesita inline.
- **Qué resuelve un hecho.** El endurecimiento del hito 7 (`02 §6.2`) aplica **solo a
  `:symbol/*`**: un símbolo resuelve si ocupa una posición en un árbol `:context` (los términos
  y relaciones admisibles, §2) o en una taxonomía. El árbol `:context` **no cuelga hechos**: un
  **hecho** resuelve por (a) su `:content :context` apuntando a un nodo `:fact/:context`
  existente en el CAS **y** (b) existir él mismo en el CAS. La cadena de anclaje
  `binding`/`projection` **no entra en `resolves?`** (sería circular: la `projection` tiene el
  hecho por extremo y su estado depende del hecho): la evalúa aparte la regla de estado, que
  toma el **peor** de la cadena **excluyendo el extremo del propio hecho**. Endurecer
  `resolves?` a «pertenece a un árbol `:context`» para hechos los dejaría a todos `orphan`; no
  es el caso (`02 §6.2`).
- **Estado de un hecho.** El estado de cada ancla es el de `02 §6.2` (por extremo, peor
  extremo). El estado de un hecho es el **peor** de las aristas de su cadena de anclaje,
  **excluyendo el extremo del propio hecho** (una `projection` M→G se evalúa por su extremo M y
  los demás eslabones hasta las hojas):
  `intact < superseded < orphan` (`atom-drifted-is-a-reconciled-orphan`). Un `.md` editado
  por fuera deja `orphan` (y `drifted` tras reconciliación, `02 §6.5`); aceptar la propuesta
  re-ancla (`supersedes`) y el hecho pasa a `superseded`.
- **Nunca descarte silencioso** (`14 §7`): un hecho que cruza hojas o depende del tipado del
  documento entero queda `orphan` y entra al bucle de reconciliación; no se borra ni se
  reescribe sin transacción.
- Un hecho cuya hoja no cambió conserva `intact` su ancla tras un `rebuild` (hoja igual ⇒
  mismo id), porque la identidad es el hash (`14 §7`).

## 7. Protocolo `propositions`

Extiende el protocolo 1 de `11`. El perfil nuevo se llama `propositions` y comparte
invocación (`11 §2`), `--describe` (`11 §3`), stdout JSON determinista y códigos de salida
(`11 §5`).

- `--describe` responde `profile: "propositions"` y `options`; sale 0 aunque falte el runtime.
- **Salida:** un objeto JSON UTF-8, claves ordenadas, hechos ordenados por `id`:

```json
{"protocol": 1, "adapter": "descompositor", "profile": "propositions",
 "language": "es",
 "facts": [{"id": "…",
            "subject":   {"name": "agua",   "lang": "es", "taxonomy": "terms"},
            "predicate": {"name": "comparte", "lang": "es", "taxonomy": "relations"},
            "object":    {"name": "liquidez", "lang": "es", "taxonomy": "terms"},
            "context": {"name": "doc:docs/x.md", "granularity": "document",
                        "of": {"path": "docs/x.md"}},
            "tier": "observed", "status": null, "curation": "proposed",
            "prov": [{"path": "docs/x.md", "source_hash": "sha256:…",
                      "span": {"start": 412, "end": 468, "unit": "codepoints"},
                      "extractor": "descompositor/heuristic@0.1", "confidence": "0.97"}],
            "attrs": {}}]}
```

- Todas las claves están siempre presentes; valen `null` o `[]` cuando no aplican (regla de
  `11 §4.1`). `source_hash` es la clave JSON del `:source-hash` EDN de §3 (convención
  snake_case del protocolo) y su etiqueta en el protocolo es **`sha256:<hex>`** (`11 §4.1`,
  lo que valida el regex de `kimun.surface.adapters`); kimun la **convierte en la frontera** a
  la forma del store **`sha-256:<hex>`** (`02 §6.4`, decisión 11) al construir la evidencia. Regla de uso: `sha256:` solo en JSON; `sha-256:` solo en EDN interno y
  fixtures. `tier` y `curation` son obligatorios (presentes y no nulos): `curation` es
  `proposed|accepted` y **la salida de motor es siempre `"curation": "proposed"`** (`14 §7`,
  decisión 31). `status` lleva el enum de `14 §5` (`observed|confirmed|rejected|retracted`) y es
  `null` hasta que una decisión lo fija. Pregunta §12 Q1 sobre ampliar `:status`; default de
  `15`: no revisar `14`. `attrs` es opaco
  para el protocolo (lo propio del motor).
- **Determinismo:** misma entrada ⇒ mismos bytes; `id` único y ordenado.
- **Cómo kimun lo convierte en plan.** `facts plan` (o `contexts define` para el
  vocabulario) resuelve/crea los términos y la proposición, materializa las hojas y
  `:sign/:span` de cada `prov`, y compone un `TransactionPlan` (`02 §5.1`) con
  `:add-node`/`:add-edge` de §3 y §6; los hechos entran `:curation :proposed` (sin decisión).
  Si el `W_i` de la salida no existe, `plan-propositions` **crea en el mismo plan** el nodo
  `:fact/:context` y su árbol `{:kind :context}` a partir del `context` de la salida; si la
  salida no trae `context`, falla con `:facts/missing-context` (no puede referenciar un nodo
  inexistente ni inventar el contexto). Nada del motor
  toca el store: el CLI aplica el plan.
- **Validación y plan.** `kimun.surface.adapters/validate` **ya rechaza un perfil
  desconocido** (`:adapters/profile`; comprueba `(contains? profile-models …)`) y se extiende
  con el perfil `propositions` (`11 §4.3`): protocolo, perfil, campos de hecho, `prov` con
  span y `source_hash` bien formados, taxonomías y contextos declarados. `plan-documents`
  (contrato `{:documents [PLANNED] :problems [..]}`) es para **perfiles de documento** y no
  sirve aquí: `propositions` no produce documentos. El planificador nuevo,
  `kimun.surface.adapters/plan-propositions`, devuelve las **ops de un `TransactionPlan`**
  (`02 §5.1`): nodos `:fact/:triple` y `:symbol/*`, aristas `binding`/`projection` con su
  evidencia. Hoy `plan-documents` ramifica
  `(if (= profile "cli") (plan-cli …) (plan-symbols …))`; `propositions` no entra por ahí. El
  despacho por perfil en `validate` (y el que elige el planificador) sigue siendo necesario.
- **Punto de entrada.** `facts plan --adapter NAME ROOT [--write]` (§9): corre el adaptador
  (como `adapters scan`), toma su JSON, lo pasa por `validate` (perfil `propositions`) y entrega
  el resultado a `plan-propositions`; con `--write` aplica el plan y materializa el documento
  `:action :assert` (§4). Es el camino de ejecución del perfil `propositions`: el adaptador no
  toca el store.
- Errores y exits como `11 §7.3`: `adapters/missing` (1), `adapters/conflict` (4),
  `adapters/runtime|failed|invalid-output` (5), `cli/usage` (6).

## 8. Consulta y evaluación en dos etapas

P7, P9 y decisiones 26/30.

```
consulta (EDN)
  → resolver símbolos   cascada 09 §2: :resolved | :ambiguous (exit 2) | :missing (exit 1)
  → recorrer el grafo   Datalog sobre triples con procedencia (índice derivado)
  → evaluar en el W_i   álgebra booleana: V_i, S_i, I; cierre por propiedades de R (§1)
  → respuesta           {:answer :truth :sense :justification :citations}
```

### Índice G derivado

- Vive en `<store>/index/graph/`, **derivado y reconstruible por cabecera** (decisión 30,
  `14 §8`); gitignored. Su **forma en disco y el esquema de datoms los posee `10 §11`**
  (`{:e <id> :a <id> :v <id> :prov {…}}`: `:a` es el id del término predicado `R`, `:v` el
  del término objeto `B`; sin literales crudos); `15` no los redefine. Se
  invalida/reconstruye por cambio de head, como el índice EAV (`02 §10` inv. 10).
  `graph rebuild` lo reconstruye; leer nunca escribe. Lo lee y escribe el host (ring 1) y lo
  orquesta la CLI (ring 3); el kernel no toca `index/graph/`. **Ninguna arista entra al
  índice sin procedencia** (regla de "toda arista que cruza capas lleva evidencia",
  `02 §3.2`).

### Motor

- **Motor candidato: Datalevin** (pod, decisión 30, `14 §8`), documentando sus límites
  **medidos**: solo el pod 1.1.0 está en el registry (`1.2.0` no; `R3 §5`), binario ~278 MB por
  plataforma, y en 1.1.0 un NPE/`ClassCastException` con reglas sembradas (`R1 §2`). El motor
  definitivo se confirma en la etapa A con el índice real (`14 §8`).
- **Respaldo offline: evaluador Datalog semi-naive in-process** sobre el índice, sin pod
  (`R1 §3–4`: 47 349 triples del desk real con el Datalog in-process; `R3 §5`: el respaldo
  in-process no tiene los bugs del pod). Es además lo único que corre **sin el pod** (una
  consulta funciona sin Datalevin). El evaluador es **ring 0 puro** (`kimun.kernel.datalog`,
  sobre valores) alimentado por el host; el índice `index/graph/` lo lee y escribe el host
  (ring 1) y lo orquesta la CLI (ring 3).

### Etapa 2: matrices dispersas por contexto

- **Qué prohíbe `14 §8`.** El veto `p×p` es contra matrices **densas sobre el corpus o la
  taxonomía entera** (el límite que wittgenstein aprendió con tres OOM, `matrix.md §4`).
- **Representación vinculante de P9: dispersa** (decisión 26). Por cada `W_i` y relación `R`,
  la matriz booleana por relación es **dispersa**: filas = sujetos observados de `R` en ese
  `W_i`, cada fila un bitset o lista ordenada de objetos observados, más `V_i`, `S_i` e `I`
  sobre las celdas presentes. Memoria `O(hechos de R en W_i)`, **nunca** el cruce
  `|sujetos|·|objetos|`. Son índices derivados, no nodos.
- **Densa solo bajo umbral declarado (propuesta, §12 Q9).** La materialización **densa** se
  permite solo si el número de términos de `R` en ese `W_i` está bajo un umbral declarado:
  parámetro `:graph/dense-terms` de `store.edn` (default **propuesto 4096** términos por
  relación por `W_i`). El umbral no está medido: `14 §8` deja el motor y su tope al índice real
  de la etapa A y es la única fuente del veto; el valor vive como propuesta en §12. Por encima
  del umbral, la representación es la dispersa. La forma densa queda
  **prohibida** para todo `W_i :granularity :corpus`, cualquiera sea su tamaño: un `W_i` de
  corpus *es* «el corpus entero» y su densidad choca con `14 §8` (`05` decisión 26, «nunca
  `p×p` global»). Así se cumple `14 §8` también cuando el contexto es el corpus.
- **Candidato.** Un candidato de `R` en un `W_i` es un par `(A, B)` que
  **aparece en un hecho de `R` en ese `W_i`** (asertado o consultado) **o que el cierre
  alcanza** (abajo); **nunca** el cruce de los conjuntos bajo los ejes (§1).
- **Cierre por alcanzabilidad.** El cierre `I* = ⋁_k I^k` solo se aplica si `R` es transitiva
  en ese `W_i` (las propiedades son hechos `(property R p) @ W_i`, §1). Fuera de
  `:granularity :corpus` se **materializa** por alcanzabilidad (BFS / semi-naive) sobre la
  forma dispersa, con memoria `O(pares alcanzables)`; el *squaring* denso solo se usa bajo el
  umbral (arriba). En un `W_i :granularity :corpus` **`I*` no se materializa**: se calcula bajo
  demanda por consulta (BFS acotado desde los términos de la pregunta), con memoria
  `O(aristas)`, nunca `O(pares)`; así el cierre de un corpus no crece `p×p` con los símbolos
  (`14 §8`). **Sí añade pares** `(A, C)` que no aparecen en ningún hecho (cuando se calcula).
  Usa las propiedades de `R` **de ese contexto** (§1), no una tabla global.
- **Evaluación offline.** El **evaluador in-process
  (ring 0)** calcula `V`, `S`, `I` y el cierre sobre la forma dispersa: hay evaluación offline
  completa, sin pod. **Matrix/wittgenstein** entra como motor externo **opcional** por
  protocolo (convergencia, GPU): productor (emite `(R A B)` y candidatos de convergencia) y
  evaluador alternativo (`V_i`, `S_i`, `I`, conflicto), **no almacén** ni requisito (`12 §3`,
  R1 §5).
- **Contrato de respuesta:**

```clojure
{:answer  <id|valor>
 :truth   :TRUE|:FALSE|:UNKNOWN
 :sense   :sinnvoll|:sinnlos|:unsinnig
 :justification [{:edge <edge-id> :type <kw> :evidence {…}} …]
 :citations [{:path "…" :span {:leaf <id> :range [a b]}} …]}
```

- Nada de lo que produce una consulta se persiste, **salvo que alguien lo `assert`e** (vuelve
  por `TransactionPlan`, decisión 16).

## 9. CLI (propuesta)

`06` es el dueño del inventario; aquí solo la propuesta de los grupos de G. Envelope y
códigos de salida consistentes con `06 §1` y `06 §3`.

| grupo | subcomandos |
|---|---|
| `facts` | `list [--context C] [--relation R] [--status S] [--tier T]` · `show ID` · `plan [FILE\|-] [--write] [--adapter NAME ROOT]` · `assert FILE\|-` · `retract ID [--note N]` · `consolidate [--dry-run] [--strict]` · `anchors ID` |
| `contexts` | `list` · `show NAME\|ID` · `create FILE` · `define FILE` (vocabulario `L_i`) · `route FROM TO --as equivalent\|projection` |
| `ask` | `QUERY [-f FILE.edn] [--context C]` (contrato de `§8`) |
| `conflicts` | `list [--all]` · `show ID` · `resolve ID --as separate-contexts\|retract\|wrong-source` |
| `graph` | `query -f FILE.json` (ya existe) · `rule -f FILE.json --rules R.edn` (Datalog con `:in $ %`) · `rebuild` (propuesta nueva; la nota de `06` la registra en el inventario) |

- Errores por nombre (convención de `errors.cljc`): `ask/ambiguous` → 2, `ask/empty` y
  `*/missing` → 1, `conflicts/conflict` → 4. Por namespace, `facts` y `contexts` heredan el 3
  de rechazo de entrada; `graph` ya mapea a 3.
- **`--strict`** no es exclusivo de `facts consolidate`: es una **opción compartida de los
  comandos que abren el store** (`open`, y cada acción que consolida o escribe: `facts
  consolidate`, `facts plan --write`, `facts assert`, `contexts create|define`, `include
  build`). Con `--strict`, el aviso `facts/pending-upstream` de §4 se convierte en rechazo.
- `contexts create` deja el documento `:action :create-context` (§2, §4); `contexts define`
  ingesta el vocabulario `L_i`; **no crea los hechos de propiedad**, que se afirman con su
  propia evidencia (§1). Su deriva con el repo es un fallo (`contexts/conflict`, 4).
- `facts plan [FILE\|-] [--write]` lee un `:fact/entry` (§4; forma en disco en `10 §11`) y
  muestra el `TransactionPlan` planificado; con `--write` lo aplica (espejo de
  `adapters scan`/`sync`) y **materializa el documento de decisión `:action :assert`**
  (todo `--write` de un `:fact/entry`, §4). `facts assert FILE\|-` consolida y **materializa
  el mismo documento** (§4).
- `facts plan --adapter NAME ROOT [--write]` es el camino del perfil `propositions` (§7): corre
  el adaptador (como `adapters scan`), valida su JSON con el perfil `propositions` y planifica
  con `plan-propositions`; con `--write` aplica el plan (y materializa el documento
  `:action :assert` de cada hecho asertado, §4). Sin `--write` muestra el plan.

## 10. Promesas → tests

Formato de `tests/promises.md`. Todo `deferred` hasta la etapa A/C (implementación).
Cada fila es un enunciado **normativo** del 15; el test debe **fallar si la promesa se
rompe** (`03 §5`), no comparar la función consigo misma.

| promesa | test que la rompería |
|---|---|
| `:fact/:triple` con `R/A/B` como ids de término: un literal crudo no es un hecho válido | `g/fact-object-must-be-a-term` |
| un `:add-node :fact/:triple` sin su `:projection` en el mismo plan se rechaza (`:plan/rejected`) | `g/fact-without-projection-is-rejected` |
| un `:remove-edge` de la última `projection` activa de un hecho se rechaza salvo que el mismo plan retracte el hecho (invariante del pool) | `g/fact-cannot-lose-its-last-active-projection` |
| `:symbol/:proposition` admite formas anidadas (`if`, `14 §4`) | `g/proposition-form-admits-nested` |
| las hojas de `:form` son ids de término (un literal es un término de `literals`, también en `:form`) | `g/proposition-form-leaves-are-terms` |
| las propiedades algebraicas de `R` son hechos `(property R p) @ W_i`, no un campo del vocabulario ni los crea `contexts define` | `g/properties-are-facts-not-fields` |
| dos `(R A B)` en `W_i` distintos son nodos distintos (el contexto entra al hash) | `g/same-triple-in-two-contexts-is-two-nodes` |
| una taxonomía admite cada nodo en **una** posición | `g/taxonomy-admits-one-position-per-node` |
| cada documento `source` abre su propio `W_i` (`:granularity :document`) | `g/source-document-opens-its-own-context` |
| el vocabulario `L_i` es canónico: sin flotantes; `:phi "0.95"` y `0.95` no son el mismo documento | `g/vocabulary-canonical-rejects-float` |
| `:axis` cita raíces de taxonomías declaradas y los hechos usan términos bajo ellas | `g/vocabulary-axis-respects-taxonomies` |
| `:granularity` es un solo enum (incluye `:transversal`) | `g/granularity-enum` |
| `:source-hash` es una sola clave: `sha-256:<hex>` en EDN interno y fixtures, `sha256:<hex>` en el JSON del protocolo, con la conversión en la frontera | `g/source-hash-is-single-key` |
| un `rebuild` con los mismos archivos y decisiones produce el mismo `W_i` y los mismos hechos | `g/rebuild-reproduces-contexts-and-facts` |
| `equivalent` solo si `φ ≥ 0,95` en ambos sentidos; nunca por string | `g/equivalent-requires-both-directions` |
| el ruteo cruza contextos solo por aristas promovidas, nunca por coincidencia de cadenas | `g/routing-never-by-string` |
| la pertenencia a un transversal es un hecho `(member X <term>) @ W_transversal`, admisible por el vocabulario del transversal | `g/transversal-membership-is-a-fact` |
| `V`/`S` son índice derivado: no crean nodo ni arista | `g/truth-and-sense-are-derived-not-nodes` |
| un `:sign/:opaque` no lleva verdad | `g/opaque-signs-have-no-truth` |
| nada pasa a `TRUE` sin una decisión registrada (`:status :observed`/`:confirmed`); un motor produce `:curation :proposed` y `statistical` nunca deduce | `g/nothing-true-without-a-registered-decision` |
| `unsinnig` solo lo marca un humano, y la marca es un hecho `(sense <fact-term> unsinnig) @ W_i` con actor (`:status :confirmed`), del que `S` la deriva | `g/unsinnig-is-human-only` |
| la promoción automática solo existe por política declarada (store o contexto) | `g/promotion-requires-declared-policy` |
| `:tier` y `:curation` son obligatorios (no nulos) en la evidencia de `projection`; `status` es el enum de `14 §5` (nulo sin decisión) y el sentido no vive en la arista | `g/projection-requires-tier-and-curation` |
| un `:rejected` se conserva y no se re-propone lo mismo | `g/rejected-is-preserved` |
| consolidar el log es idempotente sobre nodos y aristas (no sobre revisiones: el timestamp entra a su id) | `g/facts-log-consolidation-is-idempotent` |
| `facts assert` (y todo `--write`) materializa un documento `:action :assert` con el `:fact/entry` canónico completo (términos, proposición, hecho, `binding`, `projection`, spans y hoja), reconstruible desde archivos + decisiones | `g/assert-materializes-a-decision-document` |
| `contexts create` materializa un documento `:action :create-context` con el nodo `:fact/:context` completo, reconstruible por `include build` | `g/create-context-materializes-a-decision-document` |
| abrir con entradas no consolidadas las informa (`:warnings`) y no las descarta | `g/unconsolidated-entries-are-reported-not-dropped` |
| dos hechos contradictorios en un `W_i` producen informe; en `W_i` distintos no | `g/contradiction-only-within-a-context` |
| la detección de contradicción ve los hechos `:tier :statistical` (no es deducción); `statistical` no alimenta la deducción | `g/contradiction-sees-statistical-facts` |
| un conflicto abierto no bloquea: toda consulta que toca el `W_i` devuelve `:kind :ambiguous` con `:candidates` (en contradicción interna, los hechos en conflicto `{:fact :context}`, sin `:phi`) y nunca elige | `g/ask-on-open-conflict-returns-ambiguous` |
| resolver un conflicto es una transacción con documento de decisión | `g/conflict-resolution-is-a-registered-transaction` |
| cada resolución de conflicto (separar contextos, retractar, fuente equivocada) es su propia transacción | `g/conflict-resolutions-are-three-transactions` |
| la unidad de anclaje interna es hoja + grafemas NFC; el `span` del protocolo va en code points y se mapea al entrar | `g/prov-codepoints-map-to-leaf-graphemes` |
| toda arista `binding` lleva `:prov` en su evidencia | `g/binding-requires-prov` |
| las hojas `:sign/:text` referenciadas por hechos se persisten con su texto (inline), sin I/O en el kernel (`14 §10`, `10 §10`) | `g/leaves-referenced-by-facts-are-persisted` |
| `[inicio fin]` de un span es semiabierto (`fin` exclusivo) | `g/span-range-is-half-open` |
| cambiar una hoja deja `orphan` solo los hechos anclados a ella; el resto `intact` | `g/changing-a-leaf-orphans-only-its-facts` |
| un hecho `orphan` nunca se descarta en silencio | `g/orphan-facts-enter-reconciliation` |
| aceptar la propuesta re-ancla (`supersedes`) y deja el hecho `superseded` | `g/accepting-a-proposal-reanchors-and-supersedes` |
| el perfil `propositions` es determinista, valida `prov`/`source_hash` y despacha `validate` **y** el plan por perfil | `g/propositions-output-is-deterministic-and-validated` |
| `plan-propositions` crea el nodo `:fact/:context` y su árbol si el `W_i` no existe, o falla `:facts/missing-context` si la salida no trae `context` | `g/plan-propositions-creates-or-requires-context` |
| el índice G es derivado y reconstruible por cabecera | `g/graph-index-is-derived-and-rebuildable` |
| el cierre usa las propiedades de `R` de ese `W_i`, no una tabla global | `g/closure-follows-the-contexts-properties` |
| el cierre (`I*`) solo si `R` es transitiva en el `W_i`; fuera de `:corpus` se materializa por alcanzabilidad (memoria `O(pares alcanzables)`) y añade pares `(A,C)` que no aparecen en hechos; en `:corpus` no se materializa (bajo demanda, memoria `O(aristas)`) | `g/closure-adds-pairs-only-for-transitive-relations` |
| los candidatos son los pares `(A, B)` que aparecen en hechos de `R` o que su cierre alcanza; **no se materializa estructura densa** por encima de `:graph/dense-terms`, y la densa se **rechaza** en `:granularity :corpus` | `g/candidate-pairs-are-sparse-not-dense` |
| `:candidates` existe **solo** en `:kind :ambiguous` (el informe de detección puede ser `:contradiction`); en `:kind :contradiction` la clave está **ausente** (no `[]`) | `g/candidates-only-in-ambiguous` |
| `adapters --describe` sale 0 aunque falte el runtime del motor (test de integración de subproceso) | `g/describe-exits-zero-without-runtime` |
| ninguna arista entra al índice G sin procedencia | `g/no-edge-enters-index-without-provenance` |
| una consulta funciona sin el pod Datalevin (el evaluador Datalog in-process es suficiente) | `g/query-works-without-datalevin-pod` |
| la deriva del vocabulario con el repo es un fallo `contexts/conflict` (exit 4) | `g/contexts-derive-is-conflict-exit-4` |
| una consulta no persiste nada salvo que se `assert`e | `g/ask-persists-nothing-without-assert` |
| un hecho anclado a una hoja intacta conserva `intact` tras `rebuild` | `g/intact-facts-survive-rebuild` |
| el mapa de exits de §9: `ask/ambiguous`→2, `conflicts/conflict`→4, `*/missing`→1 | `g/cli-exit-codes` |

Los nombres de la columna «test» son los `deftest` reales y viven en ficheros `*_test.cljc`
(`03 §5`): `test/kimun/g/<área>_test.cljc` (p. ej. `fixtures_test.cljc` para las filas
verificables desde los fixtures, y los tests de kernel/superficie en sus propios ficheros).
El prefijo `g/` de la tabla es de la promesa, no un namespace de test.

## 11. Qué revisa y qué no toca

**Revisa:**

| documento | qué cambia |
|---|---|
| `02 §2` | la prosa «con su estado sinnvoll/sinnlos/unsinnig» se revisa: el hecho **no** lleva estado de sentido; `V`/`S` son índices derivados `(hecho, W_i)` (§3, §8) |
| `02 §2.1` | `:fact/:context` gana `:granularity` y `:of`; `:fact/:triple :object` pasa a id de término (un literal es un término de `literals`); `:symbol/:proposition` admite formas anidadas y sus **hojas son solo ids de término**; `:sign/:opaque` admite los formatos nuevos `:format "edn/vocabulary"` (documento de vocabulario, §1) y `:format "edn/decision"` (documento de decisión, §4); taxonomías `terms`/`relations`/`literals`/`facts`/`sense` (nombres propuestos, revisión de `02 §2.1`) |
| `02 §3.2` | la evidencia de `projection` cambia `:status` (enum de decisión de `14 §5`, no el sentido) con `:tier` **obligatorio** y `:curation :proposed|:accepted` (la salida de motor, `:proposed`); `binding` lleva la clave `:prov` **obligatoria** (§6) y `projection` la lleva; `semantic` (aristas de ruteo, §2) gana las claves de evidencia `:relation`/`:context-target`/`:phi` |
| `02 §4.1` | la convención del span es **semiabierta**: `[inicio fin]` con `fin` exclusivo (un span vacío `inicio = fin` es válido), §6 |
| `02 §6.2` | el endurecimiento del hito 7 aplica **solo a `:symbol/*`**: un símbolo resuelve por ocupar posición en un árbol `:context` (o una taxonomía). Un **hecho** resuelve por su `:content :context` (nodo `:fact/:context` existente) **y** por existir en el CAS; su cadena de anclaje la evalúa aparte la regla de estado, excluyendo el extremo del propio hecho (no circular); el árbol `:context` no cuelga hechos (§2, §6) |
| `02 §10` | invariante nueva: el estado de anclaje de un hecho es derivado de su cadena de aristas; `V`/`S` nunca son nodos |
| kernel (código) | `kimun.kernel.node/shapes` (`:proposition` con formas anidadas/planas y hojas solo ids de término), `kimun.kernel.edge/statuses` (enum de decisión de `14 §5`) y `kimun.kernel.edge/required-evidence` (`projection` con `:tier` y `:curation` obligatorios; `binding` con `:prov`; `semantic` con `:relation`/`:context-target`/`:phi`), `kimun.kernel.plan` (el chequeo «`:object`/hojas de `:form` son ids de término» vive aquí, **no** en `node/shapes`, que no tiene pool; `:plan/rejected` de un `:add-node :fact/:triple` sin su `:projection` en el mismo plan; `:facts/missing-context`) y `kimun.kernel.revision/apply-plan` (el `:plan/rejected` de un `:remove-edge` de la última `projection` activa: aplica contra el store y ve las aristas activas; `plan` es puro y no las tiene), `kimun.kernel.datalog` (evaluador semi-naive, **ring 0 puro** sobre valores; lo alimenta el host) y `kimun.kernel.anchor/resolves?` (endurecer **solo `:symbol/*`** a "pertenece a un árbol `:context`"; los hechos resuelven por su `:content :context` y su existencia en el CAS, y su cadena la evalúa aparte la regla de estado excluyendo el extremo del propio hecho, §6; sigue **sin I/O**: las hojas referenciadas por hechos se persisten inline). `open` y el puerto `Backend` **no cambian**: los pendientes los informa la CLI tras `open` (§4, `10 §6`, `02 §8.1`) |
| `10` | nueva sección §11: forma en disco de `facts.log.edn` y de `index/graph/` (lo que `15 §4`/`§8` usa, definido por el dueño del formato); `10 §11` es el **único dueño del esquema de datoms** (`15 §8` solo lo cita) y declara la clave `:graph/dense-terms` de `store.edn` (umbral de la matriz densa, default propuesto 4096, §8/§12) y la clave de versión `:fact-log/version` de cada línea del flujo; `10 §10`: el **inline forzado** de las hojas `:sign/:text` referenciadas por hechos se registra como revisión de la política `inline` por caso (decisión 27) |
| `11` | perfil `propositions` (invocación/describe/salida/validación), el punto de entrada `facts plan --adapter NAME ROOT [--write]` (§7, §9) y despacho por perfil en `validate` y en el plan (`plan-propositions`, §7). `11 §4.1` se revisa **como aclaración, no cambio**: `source_hash` sigue siendo `sha256:<hex>`; `15 §7` documenta la conversión a `sha-256:<hex>` (forma del store) en la frontera |
| `06` | inventario de los grupos nuevos (`facts`, `contexts`, `ask`, `conflicts`, `graph rule` y `graph rebuild`), el `--write` de `facts plan`, su variante `--adapter NAME ROOT` (perfil `propositions`), `--strict` como opción compartida y el warning `facts/pending-upstream` (§4, §7, §9) |
| `04` | perfil EDN de `source`: el vocabulario (`edn/vocabulary`, §1) y el documento de decisión (`edn/decision`, §4) se ingieren como signos `:sign/:opaque` por su perfil, no por el perfil Markdown; `04` sigue poseyendo solo SLDB-MD |
| `13` | `kimun.include.edn` declara dónde viven los documentos `source` de vocabulario y de decisión (propuesta de claves `:vocabularies`/`:decisions`); la ingesta y la reconstrucción las fijan `15 §1`/`15 §4` |
| `12 §1` | se quitan del inventario de pendientes el diseño de la capa G y el lenguaje de consulta; quedan como implementación |
| `12 §3` | wittgenstein/Matrix = productor y evaluador por protocolo; el adaptador que lee S/M y escribe G lo fija `15 §7` |

**No toca:** `07` (modelos; su reescritura es la etapa A de `16`),
`02 §5` (semántica del plan), `02 §6.5` (reconciliación de `drifted`) ni la
decisión 39 (lenguaje de selección: es de `16`). Tampoco fija: los *anchors de evaluación*
(P8, `14 §7`, `docs/archive/v2/09 §4`), P11/`index/vectors/` (`14 §1`/`§10`: co-ocurrencia ≠
`(R A B)`, los embeddings no son de la capa G), ni P14 (diccionario corto, decisión 36) ni P15
(embeddings, decisión 38): son de `14 §16`/etapa C.

## 12. Preguntas al dueño

Solo lo que `14` no decidió. Mismo formato que `14 §17`.

| # | pregunta | opciones | default |
|---|---|---|---|
| Q1 | colocación del `proposed` de un motor (la semántica ya la fijan `14 §5`/decisión 31: la salida de motor es `proposed`) | (a) `:status` mantiene el enum de `14 §5` y `proposed` va en `:curation ∈ {proposed accepted}` aparte (default, no revisa `14`); (b) ampliar `:status` con `:proposed` (revisa `14 §5`) | (a) |
| Q2 | cuándo se consolida el log de hechos (decisión 33, Q1=b) | (a) solo `facts consolidate`; (b) automático en cada acción y al abrir (informando pendientes); (c) por umbral de N entradas | (b) |
| Q4 | precedencia de las políticas de promoción automática (decisión 31) | (a) store > contexto; (b) contexto > store; (c) solo store; (d) solo contexto | (a) |
| Q6 | aristas de ruteo `equivalent`/`projection` entre contextos (14 §3) | (a) tipo de arista propio (nuevo en `02`); (b) `semantic` con `:relation` en evidencia; (c) `:symbol/:term` con `:context` en evidencia | (b) |
| Q8 | lista concreta de términos del transversal `self` (ampliación de decisión 34, que nombra el transversal pero no sus términos) | (a) `store`, `model`, `context`, `fact`, `edge`, `sign`, `tree`; (b) solo `store`, `model` | (a) |
| Q9 | umbral de la matriz densa (`:graph/dense-terms`, §8); `14 §8` es la única fuente del veto y deja el tope al índice real de la etapa A | (a) default fijo 4096 (propuesta actual); (b) medir en la etapa A y fijar; (c) sin densa nunca | (a) |

Q1: hasta que se responda, el **default** de `15` es el que **no revisa `14 §5`**: `:status`
mantiene `{observed confirmed rejected retracted}` y `:proposed` vive en `:curation` (§3, §7,
fixtures). Q3 (formato de los documentos de
decisión) **eliminada**: la decide `14 §12`
decisión 27 («según el caso»), confirmada por `14 §17` Q6. Q5 (jerarquía de `R`/`A`/`B`)
**eliminada**: decidida por `05` decisión 23 (`R`/`A`/`B` son posiciones en taxonomías con
herencia) y `14 §4`/`§2` (el valor `hierarchy` es **vista derivada**, no autoridad;
«canónico ≠ persistido»); no se persiste como registro. Q7 (declaración del vocabulario
`L_i` en el repo) **remitida al dueño de `13`** (`kimun.include.edn`): que el vocabulario es un
**documento `source`** ya está fijado por `14 §4` y `05` decisión 23; cómo lo declara
`kimun.include.edn` es de `13`, no una pregunta al dueño del producto.

## 13. Anexo: fixtures EDN

En `test/fixtures/g/`: el texto `source.md`; un contexto (`context.edn`, con `:ctx/doc`
declarado, el descriptor `{:tree :kind :name :root}` de `02 §3.1` de su árbol `{:kind
:context}`, los **cinco descriptores `{:kind :taxonomy}`** de `terms`/`relations`/`literals`/
`facts`/`sense` **con sus posiciones `ownership`** —una por nodo en cada taxonomía— y las
**aristas `ownership`** del árbol de contexto como posiciones `{:type :ownership :tree :parent
:to :order}` de `02 §3.2`); su
vocabulario `L_i` (`vocabulary.edn`, sin `:properties`, con la relación base `sense` y los
`:tiers` de cada relación incluyendo los que sus hechos usan); cinco hechos con sus
aristas (`facts.edn`), **una proyección por hecho con su span propio**, cada `binding` con su
clave `:prov`, `:curation` en cada `projection` (`:proposed` en salida de motor) y los
**hechos de propiedad** `(property R p)` (plural) como única fuente
del álgebra; un conflicto (`conflict.edn`, `:conflict/id nil` como placeholder explícito de
fixture, `:kind :ambiguous` con `:candidates` de hechos `{:fact :context}` sin `:phi` —lo que
devuelve `ask` sobre la contradicción interna, §5—) y una decisión
(`decision.edn`, `:action :assert` con `:target` y el `:fact/entry` canónico completo). Los ids se muestran como aliases (`:term/…`, `:fact/…`, `:edge/…`); el id
real de cada nodo y arista es `H(canonical-bytes …)` (`02 §2.1`) y el de cada árbol es un ULID
nominal (inv. 13). `test/kimun/g/fixtures_test.cljc` comprueba que parsean y cumplen campo a
campo la forma que este documento declara.

## 14. Deuda conocida (ronda 6 del gate, sin resolver)

Las rondas 1–5 cerraron sus hallazgos; la ronda 6 abrió los siguientes. Se dejan aquí en vez de
seguir iterando, porque varios dependen de respuestas del dueño (§12) o de decisiones sobre el
kernel que conviene tomar con el código delante (etapa A de implementación).

| # | origen | hallazgo | depende de |
|---|---|---|---|
| D1 | A6-1 | `:status nil` en la salida de motor contradice el enum de `14 §5` (§3, §7) | §12 Q1 |
| D2 | A6-2, B6-9 | la arista de ruteo `:semantic` no lleva origen obligatorio y cambia de capa (M↔M → G↔G) sin declararlo en §11 | §12 Q6 |
| D3 | A6-3, B6-2 | el caché de conflictos (`index/graph/conflicts/`) no es reconstruible desde log+pool cuando hay candidatos de convergencia no promovidos (`02` inv. 10) | decidir si `:phi` entra al pool |
| D4 | B6-1 | la forma "bitset" por fila de §8 es en el peor caso `|sujetos|·|objetos|`; falta acotarla por hechos (lista ordenada) | §12 Q9 |
| D5 | B6-4 | el veto de densidad solo cubre `:corpus`; un `:transversal` también puede abarcar el store | §12 Q9 |
| D6 | A6-4, A6-5, B6-5, B6-6 | §11 incompleto o contradictorio sobre qué módulo del kernel cambia (`02 §5` validaciones, `02 §6.2` resolución de símbolos, detección de conflicto, chequeo de términos) | implementación |
| D7 | B6-3 | `validate`/`profile-models` de `adapters` mezcla perfiles conocidos con perfil → modelos | implementación |
| D8 | B6-7 | las sustituciones de alias no cubren el contenido de `:node` ni `:prov` | implementación |
| D9 | B6-8 | un contexto `source` no reproduce "el mismo `W_i`" tras rebuild porque su árbol es un ULID nominal; §4 lo reencuentra por el nodo, pero las promesas hablan del árbol | redacción |

Detalle completo y citas: `runs/gates/spec15/GATE15-A6.md`, `GATE15-B6.md`.
