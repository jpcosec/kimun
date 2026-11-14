# Revisión D0 desde dos etapas: realidad, anchoring y hecho detectado (Agy — 2026-11-14)

> **Alcance (solo investigación):** Sin implementación, sin ejecución de tests y sin editar código ni `docs/v2/`. Distingue explícitamente las **palabras textuales del dueño** ([`brief-d0-dos-etapas.md`](file:///home/jp/proyectos/kimun/runs/research/2026-11-14/concilio/brief-d0-dos-etapas.md)), lo **verificado en las fuentes**, y las **inferencias técnicas propias**.

---

## 1. Palabras textuales del dueño y contraste con las fuentes

### 1.1 Cita literal del dueño (`brief-d0-dos-etapas.md`)
> «Mira, creo que el problema de identidad y nodo esta resuelto en el tractatus de wittgenstein. El principio 1 es que el mundo es el conjunto de todos los hechos, no de todas las cosas. Los hechos son proposiciones que tienen alguna relacion con la realidad. El valor de verdad de una proposicion esta en la realidad. Una proposicion es una trio (R A B) donde R es una relación entre los hechos A y B. entiendes a lo que voy?»
>
> «Es que realidad y hecho son cosas distintas»
>
> «son 2 etapas. 1- anchoring y 2- el hecho detectado. revisa zettelskasten pa entender»
>
> «pulento, destrabamos el bloqueo entonces? conversa con el concilio, enviale explicitamente lo que te dije y las fuentes para que entiendan»

### 1.2 Contraste con las fuentes citadas (sin atribuciones indebidas)
1. **Tractatus local ([`tractatus_tree_de.json`](file:///home/jp/proyectos/wittgenstein/Matrix/Old/tractatus/truth_machine/tractatus_tree_de.json)):**
   - **Lo que sí converge con el dueño:** `1` (*«Die Welt ist alles, was der Fall ist»*) y `1.1` (*«Die Welt ist die Gesamtheit der Tatsachen, nicht der Dinge»*) fijan que el mundo es totalidad de hechos, no de cosas aisladas. `2.06` define la **realidad** (*Wirklichkeit*) como el subsistir y no subsistir de estados de cosas (*«Das Bestehen und Nichtbestehen von Sachverhalten ist die Wirklichkeit»*). `4.05` (*«Die Wirklichkeit wird mit dem Satz verglichen»*) y `4.06` (*«Nur dadurch kann der Satz wahr oder falsch sein, indem er ein Bild der Wirklichkeit ist»*) establecen que la verdad o falsedad de la proposición se evalúa comparándola con la realidad; y `3.14` señala que el signo proposicional mismo es un hecho perceptible (*«Das Satzzeichen ist eine Tatsache»*).
   - **Matiz crítico (no atribuir al Tractatus):** En `2`, `2.01` (*«Der Sachverhalt ist eine Verbindung von Gegenständen. (Sachen, Dingen.)»*), `2.02` (*«Der Gegenstand ist einfach»*) y `2.021`, el *Tractatus* funda los estados de cosas en **objetos simples** (*Gegenstände*) que forman la sustancia del mundo y no son compuestos. El **esquema recursivo `(R A B)`** donde `A` y `B` son a su vez hechos es la formalización propia del dueño para `kimun` ([`00-nucleo.md`](file:///home/jp/proyectos/kimun/docs/v2/00-nucleo.md) §1) y su paper, **no** una tesis textual del *Tractatus*.
2. **Paper del dueño ([`02_philosophical_foundation.md`](file:///home/jp/proyectos/wittgenstein/Matrix/paper_v2/sections/02_philosophical_foundation.md) y [`03_proposed_representation.md`](file:///home/jp/proyectos/wittgenstein/Matrix/paper_v2/sections/03_proposed_representation.md)):**
   - `02` (l. 3, 5): distingue explícitamente proposición/hecho de realidad (*«A proposition is a description of a fact in relation to reality... true or false not based on the fact itself, but on its relationship with reality»*), y separa **signo** (*sign*, medio perceptible como `3`, `00000011`, `drei`, o `gift` inglés/alemán, l. 13) de **símbolo** y **nombre** dentro de un espacio lógico y contexto (*«A symbol is a sign and a structure within a logical space, used within a context»*, l. 15).
   - `03` (l. 3–8, 44–52, 73): modela cada contexto $W_i = (L_i, V_i, S_i)$ con máscara de sentido $S_i$ y capa de verdad $V_i$, califica el proceso de *«infinitely recursive»* (l. 73) y detecta colisiones/ambigüedades entre objetos vía $(W_i \otimes W_i^T) - \mathbb{I}$ introduciendo propiedades distintivas en $L_i$.
3. **Zettelkasten (`es.wikipedia.org/wiki/Zettelkasten` y `niklas-luhmann-archiv.de/bestand/zettelkasten/tutorial`):**
   - El método de Luhmann separa físicamente (1) la referencia bibliográfica/extracto hacia la fuente externa (*Bibliographie* / notas de literatura con ubicación en el texto) de (2) la ficha autónoma direccionable (*Zettel* con posición en *Zettelfolgen*/*Folgezettel* e *interactive links* cruzados e índices como *Schlagwortregister*). Su valor no reside en copiar el libro ni en su encuadernación original, sino en los vínculos cruzados entre fichas. Ni Wikipedia ni el tutorial del archivo especifican reglas algorítmicas de identidad temporal en software ante edición o división de archivos.

---

## 2. Qué queda resuelto conceptualmente de D0 al distinguir realidad, anchor y hecho detectado

Hasta ahora `D0` permanecía atascada porque el debate intentaba colapsar en **un mismo nodo de `S`** tres cosas incompatibles: la cosa/bytes cambiantes del entorno, su localización estructural en un archivo y la unidad de sentido estable a través del tiempo. La corrección en **tres términos / dos etapas** resuelve el nudo conceptual:

1. **Realidad (fuera de `kimun`, autoridad fuente):** El mundo cambiante —código en el árbol de trabajo, documentos Markdown, salidas de ejecución, entorno externo (`00-nucleo.md` §6.1–6.2)— frente al cual las proposiciones son verdaderas o falsas. No se copia durablemente en un CAS como respaldo del ambiente.
2. **Etapa 1 — Anchoring (del signo al soporte localizado):** Equivale a la **nota bibliográfica/referencia de página** en Zettelkasten y al **signo** (`02_philosophical_foundation.md` l. 13; `00-nucleo.md` §3.1). Fija cómo `kimun` sensoriza una porción perceptible del ambiente (ruta, selector AST/CST/grafema NFC, huella Merkle, digest de contrato `K6` o recibo de ejecución).
3. **Etapa 2 — El hecho detectado / nodo relacional `(R A B)`:** Equivale a la **ficha direccionable en la red** de Zettelkasten y al **símbolo/hecho en su espacio lógico contextual** (`02_philosophical_foundation.md` l. 15; `14` cabecera: espacio mínimo de convergencia con relaciones declarables y embeddings).

**Qué se destraba conceptualmente:** Una cosa/archivo que cambia en la realidad **no** define la identidad del hecho detectado (`Etapa 2`), ni hace que el hash o la ruta sean la identidad; la mutación en la realidad altera la relación de **soporte** (`Etapa 1`) y la **verdad** evaluable contra esa realidad, preservando la distinción `signo ≠ símbolo` (`00-nucleo.md` §3).

---

## 3. Identidad propia de cada etapa y cómo se conectan (inferencia con casos)

> *(Nota de atribución: el dueño fija las dos etapas, que realidad ≠ hecho, y el esquema `(R A B)`; la modelación siguiente es propuesta técnica nuestra, abierta a confirmación donde persisten opciones de [`ambiguedades.md`](file:///home/jp/proyectos/kimun/runs/research/2026-11-14/concilio/ambiguedades.md).)*

### 3.1 Identidad en cada etapa
- **Nodo/entidad de Etapa 1 (Anchor / Sensor en `S`):** Su identidad operativa es de **soporte localizado y verificable** en un instante/contrato: `(ruta_cualificada_K8, selector_topológico, contrato_K6, huella_Merkle)` (o recibo determinista en ejecuciones). No tiene «significado propio» fuera de lo que sensoriza; si se mueven o reescriben los bytes del archivo, el *anchor* anterior queda `drifted` o `orphan` y nace/se actualiza una observación nueva en `S`.
- **Nodo de Etapa 2 (Hecho detectado / Unidad convergente):** Tiene **identidad propia desacoplada del texto y de la ruta** (como un *Zettel* cuya dirección en la red sobrevive aunque cambie la edición del libro citado). Lo que lo individúa funcionalmente en el sistema es **el nodo relacional donde convergen `(R A B)`** (jerárquicas, horizontales, métricas, recursivas) y sus embeddings continuos dentro de un contexto. Su representación técnica exacta (ID opaco de nacimiento, coordenada genética o ID + alias) sigue abierta en `A2.1`.

### 3.2 Conexión entre Etapa 1 y Etapa 2
Se conectan mediante **vínculos explícitos de soporte/procedencia revisables** (`00-nucleo.md` §2.3–2.4; equivalente a la cita en un *Zettel*):
- Cardinalidad `N:M`: una unidad de Etapa 2 puede apoyarse en `0`, `1` o `N` *anchors* de Etapa 1, y un mismo *anchor* de Etapa 1 puede sostener múltiples unidades de Etapa 2.

### 3.3 Casos concretos
1. **Refactorización sin pérdida de unidad:** La función `reconcile` se mueve de `kernel/reconcile.cljc` a `kernel/anchor_reconcile.cljc` editando dos líneas.
   - *Etapa 1:* El *anchor* `(kernel/reconcile.cljc, defn reconcile, sha256:H1)` deja de coincidir con la realidad (`orphan`/`drifted`); se detecta el nuevo *anchor* en `anchor_reconcile.cljc` (`sha256:H2`).
   - *Etapa 2:* La unidad que representa el hecho/contrato de `reconcile` (y sus relaciones con requisitos y tests) **conserva su identidad propia**, marca la deriva de su soporte y enlaza el nuevo *anchor* tras declaración/reconciliación explícita.
2. **Mismo signo en dos contextos (`gift` o `a/README.md` vs `b/README.md`):**
   - *Etapa 1:* Son dos *anchors* distintos por su ruta/espacio en `S`, aunque su texto tenga igual hash.
   - *Etapa 2:* Son dos unidades distintas (ej. `gift@en` = regalo vs `gift@de` = veneno; `02_philosophical_foundation.md` l. 13), evitando la colisión por igualdad de bytes (`00-nucleo.md` §1.4, §3.1).
3. **Diferentes signos, mismo hecho (`3`, `00000011`, `drei`):**
   - *Etapa 1:* Tres *anchors* distintos en fuentes diferentes.
   - *Etapa 2:* Pueden converger hacia la misma unidad/símbolo sólo mediante una relación/equivalencia declarada explícitamente (`00-nucleo.md` §3.2), nunca por coincidencia implícita.

---

## 4. Criterios de verdad: anchor (Etapa 1) vs hecho detectado (Etapa 2)

El dueño precisa: *«El valor de verdad de una proposicion esta en la realidad»* y *«realidad y hecho son cosas distintas»*. Esto separa dos regímenes que no deben mezclarse:

1. **Criterio de verificación en Etapa 1 (Anchoring — fidelidad al soporte en la realidad):**
   - Responde a las **dos verificaciones** de la cabecera de `14`:
     - **Verificación 1 (topológica):** ¿Lo marcado en el archivo real sigue teniendo la huella Merkle y selector registrados bajo el contrato `K6`? (Estado derivado: vigente/`intact` vs mutado/`drifted` vs ausente/`orphan`; `02 §6.2`).
     - **Verificación 2 (ejecuciones, cuando tenga contrato):** ¿La ejecución marcada produjo el resultado declarado al contrastarse con la realidad operacional bajo su contrato?
   - **Límite estricto:** Que un *anchor* sea verdadero («el texto dice exactamente X» o «el test `T` devolvió `exit 0`») **no prueba la verdad sustantiva de la proposición sobre el mundo**, sólo que el soporte sígnico está vigente.
2. **Criterio de sentido y verdad en Etapa 2 (Hecho detectado / Proposición `(R A B)`):**
   - Primero requiere **sentido** en su espacio lógico $L_i$ (máscara $S_i$: *sinnvoll* / *sinnlos* / *unsinnig*; `03_proposed_representation.md` l. 38; `00-nucleo.md` §4.1).
   - Luego su **valor de verdad** ($V_i$) **se evalúa comparando la proposición con la realidad** (`tractatus_tree_de.json` `4.05`–`4.06`; `02_philosophical_foundation.md` l. 5). Ni extraerla desde un documento ni recibir un embedding alto la vuelven verdadera (`00-nucleo.md` §4.3).
   - **Acoplamiento entre ambos criterios:** Si en Etapa 1 un *anchor* de soporte falla Verificación 1 (`drifted`/`orphan`), el hecho en Etapa 2 **no pasa a falso automático** ni se oculta la mutación: pierde verificación vigente de soporte y queda marcado para revisión contra la realidad (`00-nucleo.md` §2.4).

---

## 5. Veredicto sobre D0: **PARCIALMENTE DESBLOQUEADA**

**Clasificación:** **`D0` queda parcialmente desbloqueada** — desbloqueada en el principio ontológico/arquitectónico (realidad ≠ Etapa 1 *anchoring* ≠ Etapa 2 *hecho detectado*), pero **bloqueada por tres puntos técnicos precisos** antes de modificar `K1` o `bb oracle`:

1. **Punto preciso 1 — Codificación positiva de la identidad del nodo en Etapa 2 (`A2.1`):** La cabecera de [`14-juego-del-lenguaje.md`](file:///home/jp/proyectos/kimun/docs/v2/14-juego-del-lenguaje.md) sigue vigente y advierte literalmente: *«Su definición y representación técnica siguen por elaborar; esto sustituye la propuesta de §21 ‹identidad = lugar› y no aprueba CAS, UUID ni otra codificación»*. Saber que el nodo es un hecho/unidad en red Zettelkasten no fija aún su formato de identificador durable ni la política de alias frente a `bb oracle` (`scripts/canon_oracle.py`).
2. **Punto preciso 2 — Modelo de grafo entre etapas (`A1.1`):** Decidir si una unidad tiene **un único nodo** con facetas de *anchoring* en `S`, o **dos nodos explícitamente separados** (`Anchor` en Etapa 1 ↔ `Unidad/Hecho` en Etapa 2) ligados por aristas de soporte (como en `Zettelkasten` bibliografía ↔ ficha). Esto bloquea el esquema de `src/kimun/kernel/node.cljc` y `anchor.cljc`.
3. **Punto preciso 3 — Reglas de transición temporal, niveles de abstracción y resolución de ambigüedades (`A2.2`):** Definir qué mutaciones en Etapa 1 habilitan una transición declarada `continues`/`moved`/`split`/`merged` sobre la misma unidad de Etapa 2, cómo operan los «niveles de abstracción» (`14` cabecera) y cómo se desambigua en casos concurrentes o $N:M$ sin auto-reparar silenciosamente (`02 §10`, inv. 7 y 18).

---

## 6. Qué implementación concreta queda autorizada hoy (sin inventar formato de ID ni inferir verdad)

Bajo la cabecera de [`14-juego-del-lenguaje.md`](file:///home/jp/proyectos/kimun/docs/v2/14-juego-del-lenguaje.md) (*«esta dirección no implica implementación»*):

- **Autorizable sin romper el bloqueo de D0 (todo en Etapa 1 / sensorización e invariantes):**
  - **`K6`** (digest de contrato parser/perfil/selector en caché para distinguir `source-changed` de `contract-changed`).
  - **`S1`** (cerrar el ~3 % de divergencia del parser Markdown CST de tree-sitter sin cambiar aún el default hasta tener `K6`).
  - **Diseño y sensorización de Etapa 1 por ruta (`K2a` en modo observación + evolución de `kimun stores sense`):** Recalcular el Merkle por ruta exclusivamente sobre lo *trackeado* y emitir señales de Verificación 1 (`vigente` / `mutado` / `ausente`), **conservando los IDs vigentes del kernel** sin alterar `bb oracle` ni `canon.cljc`.
  - **`K8` acotado a localizador de Etapa 1:** Rutas cualificadas con alias no ambiguo como dirección física en `S`, explicitando que localiza archivos y no identifica hechos en Etapa 2.
  - **Hardening e invariantes:** `C1`/`F25` (atomicidad archivo fuente↔store) y pruebas de contención en `heads.edn` ([`Board.md`](file:///home/jp/proyectos/kimun/desk/tasks/Board.md)).
- **Prohibido implementar hoy:**
  - Introducir un nuevo formato de ID (`UUID`, ULID, hash semántico, Zettel-ID) en `K1`/`K2b` o cambiar `bb oracle`.
  - Auto-fusionar nodos de Etapa 2 o inferir su identidad por igualdad de bytes o similitud de embeddings.
  - Construir el evaluador de verdad en la realidad (`G` / `S4`–`S5`) o persistir valores `TRUE`/`FALSE` inferidos automáticamente desde cambios de Etapa 1.

---

## 7. Contradicciones detectadas con el backlog (`12 §1`), `spec-final-candidato.md` y `docs/v2/`

1. **[`docs/v2/12-ecosistema-y-pendientes.md`](file:///home/jp/proyectos/kimun/docs/v2/12-ecosistema-y-pendientes.md) (`§1`, filas `D0` y `K1`) y `14 §21`:** Siguen enunciando *«identidad = lugar»* e *«ids por lugar»* (aunque `12` ya lleva aviso superior en cabecera). En la visión de dos etapas, el lugar pertenece a **Etapa 1 (anchoring)**, no a la identidad de **Etapa 2 (hecho)**.
2. **[`docs/v2/01-orden-filosofico.md`](file:///home/jp/proyectos/kimun/docs/v2/01-orden-filosofico.md) (`§2`, `§4.3`) y [`docs/v2/02-sustrato-computacional.md`](file:///home/jp/proyectos/kimun/docs/v2/02-sustrato-computacional.md) (`§2.1`, `§6`, inv. 2, 13, 17) y [`docs/v2/05-estado.md`](file:///home/jp/proyectos/kimun/docs/v2/05-estado.md) (dec. 1, 4, 46):** Siguen declarando `identidad = hash` o `identidad = contenido`, lo cual confunde el registro de estado perceptible (Etapa 1) con la identidad del hecho detectado (Etapa 2) y además choca con la regla de autoridad de `docs/v2/README.md` porque `14` no declara revisar `01 §2`/`§4.3` ([`auditoria-spec.md`](file:///home/jp/proyectos/kimun/runs/research/2026-11-14/concilio/auditoria-spec.md) §4).
3. **[`spec-final-candidato.md`](file:///home/jp/proyectos/kimun/runs/research/2026-10-13/concilio/produccion/spec-final-candidato.md) (`§5.4`) y `14 §18.8`:** `§5.4` postula la *«ruta cualificada relativa»* como candidato para identidad de documento y `14 §18.8` dice que *«dos documentos idénticos tienen los mismos nodos»*; ambos colapsan Etapa 1 (ruta/bytes) con Etapa 2 (hechos contextuales independientes).
4. **[`spec-final-candidato.md`](file:///home/jp/proyectos/kimun/runs/research/2026-10-13/concilio/produccion/spec-final-candidato.md) (`§3.2`) y [`roadmap-produccion.md`](file:///home/jp/proyectos/kimun/runs/research/2026-10-13/concilio/produccion/roadmap-produccion.md) (fila `D0/K1-contract`):** Exigen que *«la estructura asumida debe poder repararse automáticamente»*, lo que contradice `02 §10` inv. 7/18 y `00-nucleo.md` §2.4 (la reconciliación propone y el actor declara; ninguna pérdida de soporte se oculta).
5. **[`spec-final-candidato.md`](file:///home/jp/proyectos/kimun/runs/research/2026-10-13/concilio/produccion/spec-final-candidato.md) (`§2` reglas) y [`00-nucleo.md`](file:///home/jp/proyectos/kimun/docs/v2/00-nucleo.md) (`§3.3`):** `spec §2` llama *«unidad de anclaje»* a `hoja + rango de grafemas NFC` (granularidad de Etapa 1), chocando nominalmente con «unidad = nodo con identidad propia» (Etapa 2); `00-nucleo.md §3.3` todavía nombra el enunciado antiguo de `D0` sobre «identidad por contenido de los objetos».
6. **Secuencia `S–M` vs `G` (`roadmap-produccion.md` Gate A, `12 §1` fila `G3`):** Si el «hecho detectado» `(R A B)` forma parte ya de la Etapa 2 en `S–M`, hay que deslindar explícitamente qué parte de `(R A B)` vive como **relación declarable en `S–M`** (discreto/estructural/semántico para LLMs) frente a qué parte pertenece a **G** (evaluación de verdad en la realidad, matrices `V_i`/`S_i` e inferencia), sin reintroducir `G` en Gate A antes de cerrar `S–M`.

---

## 8. Propuesta de primer *slice* verificable S–M (Etapa 1 ↔ Etapa 2)

Sin tocar `bb oracle` ni adelantar el motor `G`, el primer *slice* verificable debería probar la **separación limpia entre realidad, Etapa 1 (*anchoring*) y Etapa 2 (*hecho/unidad vinculada*)** sobre el flujo real del repositorio (extendiendo el enfoque de [`EVIDENCE.md`](file:///home/jp/proyectos/kimun/runs/subagents/2026-11-14-sense/EVIDENCE.md)):

1. **Alcance del *slice*:** Un archivo fuente con una función marcada `f` y una nota/especificación vinculada `U` que enuncia una relación sobre `f` (con clave temporal experimental marcada como provisional hasta cerrar `A2.1`), conectadas por una arista explícita de soporte `U --[ancla-en]--> Anchor(f)` bajo contrato `K6`.
2. **Cuatro aserciones falsables (sin evaluar verdad en `G`):**
   - **Caso A — Realidad intacta:** `kimun stores sense` calcula el Merkle de Etapa 1 en `S`; la Verificación 1 confirma que el fragmento de `f` no mutó respecto a su huella y `U` mantiene soporte vigente.
   - **Caso B — Mutación en la realidad (edición de `f`):** Se modifica el cuerpo de `f` en el archivo sin borrarla; Etapa 1 reporta señal de deriva (`source/mutated`) con el selector afectado. **`U` no se reescribe ni se borra**, ni declara falsa a `f`; queda con bandera observable de *soporte derivado que requiere revisión*.
   - **Caso C — Movimiento vs. clon homónimo:** Se mueve `f` a otro archivo o se duplica en `copia/f`; Etapa 1 detecta desaparición en la ruta original y dos huellas compatibles en la realidad. El sistema **no** fusiona ni reancla en silencio: expone el candidato y exige confirmación explícita para vincular el nuevo *anchor* de Etapa 1 a `U`.
   - **Caso D — Pérdida de soporte sin destrucción de red:** Si se borra el archivo de `f`, Etapa 1 reporta `orphan` y `U` conserva su dirección en la red de vínculos de Etapa 2 con pérdida de soporte declarada explícitamente (`00-nucleo.md` §2.4).
