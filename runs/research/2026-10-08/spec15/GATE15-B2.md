# GATE15 — Carril B (ejecutabilidad), ronda 2

Auditor sin contexto. Solo lectura; citas exactas. Documento auditado: `docs/v2/15-capa-g.md`
+ `test/fixtures/g/*.edn`. Kernel actual: `src/kimun/kernel/*` (anillo 0 puro),
`src/kimun/surface/*`, `src/kimun/host/*`.

## Hallazgos

1. **`bloqueante` — la cota de candidatos del §8 puede ser `p×p`, que `14 §8` prohíbe.**
   - Cita 15 §8: «la cota es medible (§10): en un `W_i`, los pares evaluados son a lo sumo
     `Σ_r |A_r|·|B_r|` de los candidatos por relación, y ningún `W_i :corpus` materializa una
     matriz cuadrada de término×término».
   - Cita 15 §8 (misma sección): «la evaluación usa solo pares candidatos acotados **por
     relación** (§1: los `A` bajo el eje de `R` con los `B` bajo el suyo), nunca el producto
     cartesiano de todos los términos del contexto».
   - Cita que choca 14 §8: «y **nunca crecen `p×p` sobre el corpus entero** (límite que
     wittgenstein aprendió con tres OOM)». Con `:axis ["thing" "thing"]` (fixture
     `vocabulary.edn`, §13) y `thing` raíz de `terms`, «los `A` bajo el eje … con los `B` bajo
     el suyo» *es* el producto cartesiano de los términos del contexto: la cota `|A_r|·|B_r|`
     crece cuadrática en los símbolos, y en un `W_i :granularity :corpus` es `p×p` sobre el
     corpus. La frase «nunca el producto cartesiano de todos los términos» no se sigue de la
     definición de candidato que da la línea anterior.
   - Corrección mínima: definir «candidato» como solo los pares efectivamente asertados o
     consultados (nunca el cruce de los conjuntos bajo los ejes) y expresar la cota en número
     de hechos/pares observados, no en `Σ_r |A_r|·|B_r|`.

2. **`grave` — el perfil `propositions` rompe la etiqueta de `source_hash` del protocolo 1.**
   - Cita 15 §3: «La clave de la procedencia es **`:source-hash`** y su etiqueta
     **`sha-256:<hex>`** (`02 §6.4`, decisión 11) en todos los sitios de `15` y en los
     fixtures»; cita 15 §7: `"source_hash": "sha-256:…"`.
   - Cita 11 §4.1: «`source_hash` | `"sha256:<hex>"` o null». Implementación:
     `kimun.surface.adapters/validate` valida `#"sha256:[0-9a-f]{64}"`
     (`src/kimun/surface/adapters.cljc:94`). Un adaptador `propositions` que emita
     `sha-256:<hex>` es rechazado por `:adapters/source-hash`; uno que emita `sha256:<hex>`
     contradice 15 §3/§7 y la promesa `g/source-hash-is-single-key`.
   - Corrección mínima: unificar en `sha256:<hex>` (lo que ya valida `validate`) o listar en
     15 §11 la revisión de `11 §4.1` y del regex; no puede haber dos etiquetas bajo el mismo
     protocolo.

3. **`grave` — §1 introduce una comprobación nueva del plan y §11 no toca `kimun.kernel.plan`.**
   - Cita 15 §1: «un `:add-node` de `:fact/:triple` que no lleve una arista `:projection`
     hacia él **en el mismo plan** se rechaza (`:plan/rejected`)».
   - Choca con 02 §5.1: los chequeos del plan son siete y ninguno cubre esto
     (`plan.cljc`: «base-cas, ids-exist, tree-integrity, evidence-ref-hash, capability,
     opaque-replace-only, pure-data»). 15 §11 lista como cambios de kernel `node/shapes`,
     `edge/statuses`, el puerto `Backend` y `anchor/resolves?`, pero **no** el plan.
   - Corrección mínima: añadir `kimun.kernel.plan` (chequeo nuevo `:plan/rejected`) a la
     tabla «Revisa: kernel (código)» de §11.

4. **`grave` — el log de hechos no es reconstruible desde el pool ni desde documentos para un `assert` directo.**
   - Cita 15 §4: «el hecho reconstruible vive como documento de decisión o como documento
     `source` de vocabulario»; y §4 fija las acciones materializables como documento:
     `:confirm|:reject|:retract|:promote|:separate-contexts|:wrong-source|:unsinnig`. `15 §9`
     define `facts assert FILE|-`, que **no** está en ese conjunto.
   - Choca con 14 §2/decisión 27: «el store siempre se reconstruye desde archivos +
     decisiones»; 02 §10 inv. 10; promesa §10 `g/rebuild-reproduces-contexts-and-facts`.
     Bajo `:git :ignore` el `facts.log.edn` es caché: un hecho asertado sin decisión asociada
     desaparece en un `include build` limpio.
   - Corrección mínima: que `assert` materialice también un documento de decisión (añadir
     `:assert` al `:action` de §4) o declarar explícitamente que un `assert` no consolidado es
     pérdida no reconstruible (lo que contradice 14 §2).

5. **`grave` — §6 exige contar grafemas de la hoja dentro del kernel, lo que choca con hojas por referencia (formato 3) y con el anillo 0.**
   - Cita 15 §6: «El estado de un hecho es el **peor** de las aristas de su cadena de anclaje:
     `intact < superseded < orphan`», apoyado en `02 §6.2` («`b ≤` número de grafemas del
     `:text` de `L`»).
   - Choca con 14 §10 (forma 3, decisión 27): «hojas `:text` con su texto dentro del objeto →
     **contenido por referencia**: el objeto guarda el id (hash); los bytes se leen del
     archivo por el anchor externo». `anchor.cljc` `resolves?` lee
     `(get-in lf [:content :text])` y llama `ports/graphemes`; sin texto en la hoja, toda
     cadena queda `orphan` (o el kernel necesita I/O, prohibido por 03 §1).
   - Corrección mínima: declarar que las hojas referenciadas por hechos se persisten con su
     texto (`inline` obligatorio para ellas), o inyectar en `anchor/resolves?` un lector de
     texto del host, y listarlo en §11.

6. **`menor` — la tabla de promesas §10 no cubre todos los enunciados normativos.**
   - Cita 15 §10: «Cada fila es un enunciado **normativo** del 15; el test debe **fallar si la
     promesa se rompe**». Faltan filas para: §6 «aceptar la propuesta re-ancla (`supersedes`) y
     el hecho pasa a `superseded`»; §2 «contexto por defecto: cada documento `source` abre su
     propio `W_i`»; §3 «un `:sign/:opaque` no lleva verdad»; §1 «una taxonomía admite cada nodo
     en **una** posición».
   - Choca con 03 §5: «Para cada promesa normativa … existe un test que **fallaría si la
     promesa se rompiera**». Todas las filas actuales sí son escribibles con `bb test` (los
     tests `g/…` caen en kernel/surface/CLI, ring 4).
   - Corrección mínima: añadir esas filas a §10.

7. **`menor` — §7 describe mal el código actual (`validate` no cae en `symbols`).**
   - Cita 15 §7: «sin el despacho, `propositions` cae en `symbols`».
   - En `src/kimun/surface/adapters.cljc`, `validate` rechaza el perfil desconocido con
     `:adapters/profile` (`(contains? profile-models …)`), así que `plan-documents` retorna sin
     documentos; el fallback silencioso a `symbols` solo existe en el `(if (= profile "cli")
     (plan-cli …) (plan-symbols …))` de `plan-documents`.
   - Corrección mínima: precisar que `validate` lo rechaza y que el despacho hace falta en
     `plan-documents` (que sí ramifica por defecto a `symbols`).

8. **`menor` — §9 usa comandos/opciones que su propia tabla no declara.**
   - Cita 15 §9: «sin `--write`/`assert`, `facts plan` solo muestra el `TransactionPlan`» y
     §7: «`facts plan` (o `contexts define` …)». La tabla del grupo `facts` (§9) lista
     `list|show|assert|retract|consolidate|anchors`, **sin `plan`** y sin `--write`.
   - Corrección mínima: añadir `plan` (y `--write`) a la tabla de §9 o dejar de citarlos.

## Ambigüedades

- **A. §8 `:a <keyword>` vs §1 predicado = id de término.**
  Cita: «Esquema de datoms: `{:e <id> :a <keyword> :v <id|literal> :prov {…}}`» frente a §1
  «`R`, `A` y `B` son **ids de nodos `:symbol/:term`**». Lectura (i): `:a` es el término
  predicado (un id, por tanto string), y `<keyword>` está mal. Lectura (ii): `:a` es una
  keyword de atributo y el triple se codifica en otro sitio (no se dice dónde).
- **B. §8 «candidatos».**
  Cita: «pares candidatos acotados **por relación** (§1: los `A` bajo el eje de `R` con los
  `B` bajo el suyo)». Lectura (i): candidato = cruce de los conjuntos bajo los ejes (implica
  `|A_r|·|B_r|` y el `p×p` del hallazgo 1). Lectura (ii): candidato = solo los pares de hechos
  asertados/consultados (cota por número de hechos).
- **C. §2/Q6 `semantic` con evidencia de ruteo.**
  Cita: «`{:type :semantic :evidence {:relation :equivalent|:projection :context <W_i>
  :context-target <W_j> :phi "<str>" :ref-hash <nodo destino>}}`». Lectura (i): `ref-hash` es
  el nodo destino del ruteo (el otro contexto/hecho). Lectura (ii): `ref-hash` sigue siendo el
  `:to` de la arista y `:context-target` es el destino; el texto no lo distingue.

## Cobertura directa de las preguntas del carril

- **Anillos / kernel sin I/O:** implementable sin violar 03 salvo el punto del hallazgo 5.
  Exigen cambio de kernel (§11): §1 y §2 (`kimun.kernel.node/shapes`), §3
  (`kimun.kernel.edge/statuses` + `:tier` obligatorio), §4 (puerto `Backend`, informar
  pendientes) y §6 (`anchor/resolves?`). El hallazgo 3 falta en esa lista (`plan`). §5, §7, §8
  y §9 no exigen kernel: son superficie/CLI (anillo 3) y Datalog in-process puro.
- **Reconstruibilidad:** `index/graph/` sí es reconstruible por cabecera desde pool+revisión
  (datoms = proyección de nodos/aristas activas; `:prov` sale de la evidencia). `facts.log.edn`
  no es reconstruible desde el pool (es flujo de entrada) y, para `assert` directo, tampoco
  desde documentos (hallazgo 4).
- **Promesas → tests:** todas las filas de §10 nombran un test escribible con `bb test`; faltan
  filas para varios enunciados (hallazgo 6).
- **Compatibilidad con el protocolo 1 de `11`:** incompatible por `source_hash`
  (`sha256:` vs `sha-256:`) salvo revisión de `11 §4.1` (hallazgo 2); el resto es aditivo
  (perfil nuevo, dispatch por perfil en `validate` y `plan-documents`), como declara §7.
- **Coste `p×p`:** sí, con la definición de candidato del §8 (hallazgo 1, ambigüedad B).

## Veredicto

`ABIERTO` — bloqueantes: **(1)** la cota de pares candidatos del §8 crece `p×p` bajo su propia
definición y contradice `14 §8`, prohibición dura.
