# Fuentes para decidir la identidad de los nodos en kimun (2026-10-09)

> Documento de fuentes para la decisión 46 (`docs/v2/05-estado.md`, `docs/v2/14-juego-del-lenguaje.md §21`):
> ¿qué es la identidad de un nodo, qué papel cumple el hash y cómo se generan los IDs?
> No decide: reúne lo que dicen las fuentes, con su dirección exacta para leerlas, y contrasta con lo
> que kimun hace hoy. Todas las rutas son absolutas.

## 1. Las fuentes, por orden de autoridad

| # | fuente | dirección |
|---|---|---|
| F1 | *Tractatus Logico-Philosophicus* (texto alemán completo, 526 proposiciones, JSON) | `/home/jp/proyectos/wittgenstein/Matrix/Old/tractatus/truth_machine/tractatus_tree_de.json` |
| F2 | Paper del dueño — fundamento filosófico | `/home/jp/proyectos/wittgenstein/Matrix/paper_v2/sections/02_philosophical_foundation.md` |
| F3 | Paper del dueño — representación propuesta (`W_i`, `L_i`, `V_i`, `S_i`) | `/home/jp/proyectos/wittgenstein/Matrix/paper_v2/sections/03_proposed_representation.md` |
| F4 | Paper completo (composición) | `/home/jp/proyectos/wittgenstein/Matrix/paper_v2/manuscript/paper.md` y `/home/jp/proyectos/wittgenstein/Matrix/paper_v2/sections/` |
| F5 | Matrix — contexto, WiGame y desambiguación | `/home/jp/proyectos/wittgenstein/Matrix/Matrix/docs/context_and_wigame.md` |
| F6 | Matrix — conceptos | `/home/jp/proyectos/wittgenstein/Matrix/Matrix/docs/concepts.md` |
| F7 | Matrix — política de símbolos del kernel (`instance`, `equivalent`, `kern:`) | `/home/jp/proyectos/wittgenstein/Matrix/Matrix/docs/kernel_symbol_policy.md` |
| F8 | Matrix — campos semánticos (orden, término medio, reflexión `rel:`/`ctx:`, etiquetas calificadas) | `/home/jp/proyectos/wittgenstein/Matrix/Matrix/docs/semantic_fields.md` |
| F9 | Matrix — forma canónica `(R a b)` e ingesta | `/home/jp/proyectos/wittgenstein/Matrix/Matrix/docs/canonical_forms_and_ingestion.md` |
| F10 | Spec de convergencia (identidad entre contextos) | `/home/jp/proyectos/wittgenstein/matrix-shrdlu-spec/specs/convergence.md` |
| F11 | Spec de grounding (`projection`, `equivalent`, referentes `ref:<k>`) | `/home/jp/proyectos/wittgenstein/matrix-shrdlu-spec/specs/grounding.md` |
| F12 | Átomos del TractatusKnowledgeMachine (signo/símbolo, representación flotante, minimalidad, aRb) | `/home/jp/proyectos/wittgenstein/Matrix/TractatusKnowledgeMachine/atoms/Filosofia/Tractatus_y_Ontologia/` |
| F13 | Código de Matrix (núcleo: `Symbol`, `Name`, `Thing`, `Relation`, `LiSpace`, `Proposition`, `Fact`) | `/home/jp/proyectos/wittgenstein/matrix-shrdlu-spec/matrix/src/operational_model/core/` y `…/routing/context.py` |

## 2. Qué dicen

### 2.1 El mundo son hechos, no cosas (F1, F2, F12)

- **TLP 1.1** — "Die Welt ist die Gesamtheit der Tatsachen, nicht der Dinge." (El mundo es la totalidad
  de los hechos, no de las cosas.)
- **TLP 2.0272** — "Die Konfiguration der Gegenstände bildet den Sachverhalt." (La configuración de los
  objetos forma el estado de cosas.)
- **TLP 3.3** — "Nur der Satz hat Sinn; nur im Zusammenhange des Satzes hat ein Name Bedeutung." (Solo la
  proposición tiene sentido; solo en el contexto de la proposición tiene un nombre significado.)
- **F2** — "things in the abstract mean nothing; they only acquire meaning through their relationships."
- **F12, Representación flotante** — "un signo no tiene un significado absoluto guardado en una celda de
  memoria; su significado es únicamente la intersección de sus proyecciones contextuales."

**Consecuencia para identidad:** un nodo no "es" algo por sí mismo; lo que se identifica son lugares en
una red de relaciones.

### 2.2 Signo y símbolo (F1, F2, F12)

- **TLP 3.32** — "Das Zeichen ist das sinnlich Wahrnehmbare am Symbol." (El signo es lo perceptible del
  símbolo.)
- **TLP 3.326** — "Um das Symbol am Zeichen zu erkennen, muss man auf den sinnvollen Gebrauch achten."
  (Para reconocer el símbolo en el signo hay que atender al uso con sentido.)
- **TLP 3.327** — el signo, junto con su uso lógico-sintáctico, determina una forma lógica.
- **F2** — "A symbol is a sign and a structure within a logical space, used within a context. A name is a
  reference to this logical structure, that is, a mapping between sign and symbol, sufficiently
  determined by the context to distinguish it from other symbols."
- **F12, Signo vs Símbolo** — "Símbolo: la posición en el espacio lógico"; "perro", "dog", "0101" pueden
  mapear al mismo símbolo.

**Consecuencia:** el texto (signo) no es la identidad. La identidad semántica es la posición del símbolo
en un espacio lógico dentro de un contexto.

### 2.3 La identidad es un lugar en el árbol de contextos (F3, F5, F8, F10)

- **F5** — "A symbol does not carry meaning by itself: what it denotes is the **path/leaf** where its
  occurrences converge." Árbol: `ctx:kb › ctx:marie_curie › wigame:marie_curie:es`.
- **F3** — desambiguar es agregar dimensiones que separan: `leaf` → `(leaf) rough` / `(leaf) smooth`;
  "the introduction of hierarchies of contexts enables the use of this logic as a tree search index".
- **F8 §5** — etiquetas calificadas `"<wigame_id>::<symbol>"`: "`hoja` can be a subject in two
  different worlds, and collapsing them would destroy exactly the distinction that contexts exist to
  preserve. The hypertensor must not lose the indexing of symbols."
- **F10** — "El referente es la intersección que sobrevive: la ruta del árbol de contextos donde las
  ocurrencias coinciden. Identidad entre contextos = proyección no vacía, propuesta por convergencia y
  confirmada por regla o curado; **nunca coincidencia de strings**."

### 2.4 El mismo hecho en dos fuentes son dos hechos (F5)

- "Facts are context-scoped coordinates. The same `(R a b)` asserted in two articles yields two facts in
  two contexts, and may be truth-linked by a projection. Collapsing them into one global cell destroys
  exactly the structure that disambiguates."

### 2.5 Identidad entre contextos = curaduría (F5, F7, F11)

- **F5** — "Deciding that `she@marie_curie` and `curie@marie_curie` denote the same referent is a
  **reviewed decision**, not an automatic normalization."
- **F7** — `instance` y `equivalent` son meta-relaciones del kernel: `equivalent` "supports
  canonicalization, alias folding, and cross-source symbol unification".
- **F11** — `equivalent` si `φ ≥ 0,95` en ambos sentidos: "los dos símbolos comparten referente (`ref:<k>`)".

### 2.6 Relaciones y contextos también tienen lugar (F8)

- Reificación con namespaces reversibles: una relación tiene su espejo `rel:<id>`, un contexto `ctx:<id>`,
  para poder predicar sobre ellos sin colapsar los registros.

### 2.7 El código de Matrix va atrás de sus docs (F13)

- `Symbol.symbol_id` es un string libre; `Proposition.proposition_id` y `Fact.fact_id` son **aleatorios**
  (`new_id("prop")`, `new_id("fact")`). Para kimun hay que seguir los docs (F5, F8, F10), no el código.

## 3. Qué hace kimun hoy (contraste)

| aspecto | kimun hoy | dónde | qué dicen las fuentes |
|---|---|---|---|
| identidad de un nodo | `id = H(bytes canónicos {:class :kind :content})` | `/home/jp/proyectos/kimun/docs/v2/02-sustrato-computacional.md §2.1`; `/home/jp/proyectos/kimun/src/kimun/kernel/node.cljc` | identidad = lugar (estructural o semántico); el hash no aparece como identidad en ninguna fuente |
| mismo texto en dos lugares | un solo nodo (content-addressing) | `02 §2` | dos ocurrencias distintas; si significan lo mismo, se ve en el símbolo que anclan (F5, F8) |
| mismo `(R a b)` en dos fuentes | un nodo `:fact/:triple` si el contenido coincide | `02 §2.1` | dos hechos en dos contextos (F5) |
| enlaces | por hash (`:from`, `:to`, `:ref-hash`) | `/home/jp/proyectos/kimun/src/kimun/kernel/edge.cljc` | por lugar/nombre (F8: etiquetas calificadas) |
| cambio de contenido | cambia el id ⇒ aristas `supersedes` y reconciliación heurística | `02 §6` | el lugar no cambia; el hash detecta que mutó |
| contextos | `:fact/:context {:name}` + árbol-índice | `02 §2`; `/home/jp/proyectos/kimun/desk/atoms/atom-w-i-context-node-and-index.md` | árbol de enrutamiento; la ruta es la identidad (F5) |

## 4. Lo que el dueño ya fijó (2026-10-09)

1. "La identidad de un nodo es su contenido, pero en el sentido **semántico**, NO en el sentido hash. El
   hash es una especie de resumen matemático que permite verificar identidad, pero no tiene ningún
   significado."
2. "Los IDs deberían ser una organización taxonómica **semántica** por un lado y **estructural** por el
   otro." El nodo que apunta al contenido (S) tiene su ID según su lugar taxonómico estructural, referido
   a lo que está anclando; los nodos M y G, según su taxonomía semántica.
3. Los IDs estructurales cumplen la función de **anclaje** de contenido semántico hacia afuera, en
   ingesta o en proyección (que al fin son lo mismo). Un ID estructural que se rompe es **un borrado o
   una mutación**: si es borrado, no tiene sentido mantener el nodo (no apunta a nada); el conocimiento
   podría perdurar, pero sería otro nodo en otra capa y depende de lo anclado. Lo importante es la
   **transparencia**: si se borra un ID estructural que soporta un claim de conocimiento, se declara.
   Si es mutación, el hash la detecta.
4. Un símbolo nuevo nace en el contexto del documento; subirlo de contexto o declararlo `equivalent` es
   curaduría.

## 5. La decisión que queda por tomar

**¿Se adopta para el kernel de kimun "identidad = lugar (estructural para S, semántico para M/G); hash =
verificación de mutación"?** Dos formas de hacerlo:

- **(a) IDs taxonómicos puros:** nada usa el hash como identidad; el CAS deja de identificar.
- **(b) CAS para contenido + capa de identidades:** los objetos de contenido siguen deduplicados por
  digest, y se añaden ocurrencias S (source + locator), símbolos M contextuales y hechos G en contexto
  con IDs propios que registran el hash esperado. Propuesta P2 de la revisión de codex:
  `/home/jp/proyectos/kimun/docs/reviews/wittgenstein-superset/proposals/02-identity-and-anchors.md`,
  con los casos reproducidos F02 y F07 en
  `/home/jp/proyectos/kimun/docs/reviews/wittgenstein-superset/reviews/02-findings.md`.

En cualquiera de las dos, se revisa:

- `01 §4` principio 3 (`/home/jp/proyectos/kimun/docs/v2/01-orden-filosofico.md`): se mantiene en sentido
  semántico, se retira "id = hash".
- decisiones 1, 3 y 4 (`/home/jp/proyectos/kimun/docs/v2/05-estado.md §3`): `class`/`kind` al hash,
  id de arista sin timestamp, ids de árbol ULID y revisión de 8 campos.
- el kernel entero (`/home/jp/proyectos/kimun/src/kimun/kernel/`): nodos, aristas, árboles, revisiones,
  `bb oracle` (ids canónicos).

Y deja abierto, para después de decidir, **cómo se escribe un ID** (sintaxis de rutas estructurales y
semánticas, p. ej. `src:desk/atoms/x.md › section:Answer › para:2` y `ctx:kb › ctx:cocina › hoja`; ver
`/home/jp/proyectos/kimun/docs/v2/drafts/ids-desde-matrix.md`).
