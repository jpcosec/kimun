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

El nodo no sabe en qué árboles está. Esa información vive en las aristas.

## 3. Árboles como índices; aristas con tipo

```
Edge {
  tree     : id del árbol-índice al que pertenece      ; nil para aristas entre capas
  type     : :ownership | :binding | :projection | :reference | :semantic | :derived | :supersedes
  from, to : node-id
  order    : posición entre hermanos (solo :ownership)
  evidence : {hash-del-referente, actor|motor, versión, contexto W_i, timestamp}
}
```

| Tipo | Capa | Entra al Merkle | Semántica |
|---|---|---|---|
| `ownership` | dentro de un árbol | **sí** (del árbol) | estructura: padre → hijo, ordenada |
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

## 4. Stand-off por debajo del párrafo

La hoja canónica de un documento es el **texto plano de cada párrafo** con offsets de
grafema. Sobre ella:

```
hoja (texto plano, hash)
 ├─ capa markup inline     spans {kind, range}            determinista (CST)
 ├─ capa oraciones         spans UAX #29                  determinista
 ├─ capa tokens/palabras   spans UAX #29                  determinista
 ├─ capa sintaxis          árbol de spans, por motor      proyección (motor, versión)
 └─ capa menciones         spans → binding a símbolos     proyección o transacción
```

- Las capas deterministas se **derivan bajo demanda** y se cachean por hash de hoja; no
  son nodos del grafo hasta que algo las referencia.
- Una dirección hasta el símbolo **existe** (`hoja#hash @ offset`) sin que exista un
  nodo: materialización perezosa. Un corpus de 10⁴ documentos tiene 10⁸ grafemas; no
  pueden ser nodos.
- El anclaje primitivo `(hoja, rango, hash)` unifica oración, sintagma, mención,
  argumento de un hecho y región opaca. Los ids de nodo del árbol de markup son el caso
  privilegiado donde además hay identidad estructural.

## 5. Revisiones y transacciones

```
Revision {
  id        : hash(contenido)
  roots     : {tree-id → merkle-root}      ; una entrada por árbol tocado
  parents   : [revision-id]
  tx        : transaction-id
  actor, engines, timestamp, provenance
}

Transaction = TransactionPlan validado + aplicado
TransactionPlan = datos EDN: [primitive-op ...]
```

Operaciones primitivas: `add-node`, `add-edge`, `remove-edge`, `replace` (= `add-node`
+ re-enlace + `supersedes`), `move` (re-enlace dentro de un árbol). No hay `update`:
los nodos no cambian.

- El plan se **valida** (tipos, capabilities, invariantes de árbol, evidencia) antes de
  aplicarse. Código no confiable (scripts de usuario, hooks, agentes) produce planes;
  nunca toca el store.
- Commit por **compare-and-swap** sobre el head. Conflicto → reconciliación por árbol
  (cambios en árboles distintos son independientes por construcción) o `ConflictSet`
  explícito.
- Efectos externos (git, filesystem, APIs, subagentes) salen como `EffectPlan` a un
  outbox persistente; su resultado entra como una transacción nueva.

## 6. Identidad, sucesión y detección de mutaciones

**Identidad** = hash. Trivial y exacta. Dos nodos con el mismo contenido canónico son el
mismo nodo, en cualquier árbol, en cualquier revisión.

**Sucesión** = arista `supersedes`, registrada por la transacción que reemplaza `X` por
`X'`. La transacción *sabe* que lo hizo; no hay que adivinarlo. Las aristas colgadas de
`X` se re-anclan a `X'` por regla explícita (según tipo) o quedan marcadas para revisión.

**Mutación** = un árbol apunta a otro nodo donde antes apuntaba a éste. Se detecta
comparando la evidencia de cada arista con la revisión actual:

| Estado del anclaje | Condición |
|---|---|
| `intact` | el referente está en el árbol y el hash coincide con la evidencia |
| `superseded` | el referente fue reemplazado y existe `supersedes` hacia el nuevo |
| `drifted` | el referente fue reemplazado **sin** sucesión registrada (cambio externo) |
| `orphan` | el referente no está en ningún árbol de la revisión |

Solo `drifted` requiere heurística (reconciliación por sample/posición), y se marca con
confianza. Es el caso de un `.md` editado fuera del kernel: no hubo transacción, así que
no hay `supersedes`. Los estados son consultables por nodo ("qué está anclado en qué") y
por revisión (diff de anclajes), y `drifted`/`orphan` alimentan el bucle autopoiético.

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

La decisión bb vs Node **no se toma en el primer slice**: el kernel `.cljc` corre en ambos
y la UI o tree-sitter la forzarán hacia Node + WASM cuando toque.

## 9. Roadmap (por analogía con Git)

Cada hito es un vertical slice verificable, no una capa de abstracción.

| # | Hito | Análogo Git | Criterio de salida |
|---|---|---|---|
| 0 | **Blobs**: pool de nodos content-addressed en memoria; hash canónico; clases S/M/G | `hash-object`, `cat-file` | mismo contenido ⇒ mismo id; suite de conformidad de hashing |
| 1 | **Trees**: árboles-índice con `ownership` ordenado; Merkle perezoso; un nodo en N árboles | `write-tree`, `ls-tree` | cambiar un nodo invalida solo las ramas correctas de cada árbol |
| 2 | **Commits**: revisiones, transacciones, `TransactionPlan` EDN validado, `supersedes`, CAS de heads | `commit`, `log`, `diff` | describir a mano una transacción y obtener exactamente su revisión; diff entre revisiones |
| 3 | **Persistencia**: log append-only + CAS en disco; reload reproduce estado e historial | `.git/objects`, refs | cerrar y abrir el proceso ⇒ mismo estado, mismos hashes |
| 4 | **Primera superficie**: Markdown → CST → AST neutro → árbol-índice + hojas; emisor inverso-correcto; regiones `opaque` | `add`, `checkout` | `parse(render(A)) == A` por property testing; informe de direccionabilidad por documento |
| 5 | **Evidencia y anclajes**: aristas entre capas con evidencia; estados `intact/superseded/drifted/orphan`; consulta "qué está anclado en qué" | `blame` | un cambio externo produce `drifted` detectable y re-anclable |
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
5.  Un nodo puede pertenecer a N árboles; tiene un padre por árbol.
6.  Toda arista entre capas o hacia lo opaco/externo lleva evidencia (hash + provenance).
7.  La sucesión se registra con `supersedes`; nunca se infiere dentro de una transacción.
8.  concat(tokens(parse(s))) == s para todo CST.
9.  parse_f(render_f(A)) == A para todo AST estructural canónico A y formato f.
10. Lo derivado se reconstruye desde log + pool; no tiene autoridad.
11. Un efecto externo nunca muta el grafo directamente.
12. Toda operación sobre un nodo :opaque es reemplazo del blob completo, o se rechaza.
```
