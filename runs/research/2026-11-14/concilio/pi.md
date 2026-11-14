# Pi — aterrizaje en el kernel actual y contrato mínimo S–M

> Investigación solicitada por el dueño (2026-11-14). No modifica producto, no ejecuta tests, no
> hace commits. Insumos: `AGENTS.md`, `docs/v2/00-nucleo.md`, `01`, `02`, `05`, `10`, `12`, `14`,
> borradores de `docs/v2/drafts/`, `docs/research/autopoiesis/README.md` y sus fuentes, y el código
> del kernel en `src/kimun/kernel/`.

## 0. Veredicto de aterrizaje

- El kernel actual **sí tiene sensorización**: fingerprints externos, hashes de contenido, Merkle por
  árbol, revisiones, evidencia, estados de anclaje y reconciliación.
- El kernel actual **no tiene identidad de unidad observada**: todos sus ids (`node-id`, `edge-id`,
  `revision-id`, `descriptor-id`) se derivan de contenido o de una estructura content-addressed.
- La corrección del dueño no exige tirar el CAS ni el Merkle. Exige **separar** tres cosas que hoy
  están fundidas: `identidad de unidad`, `observación/estado` y `localizador`.
- El camino mínimo S–M es añadir un registro estable de **unidades y linaje**, dejar el CAS como
  almacenamiento de contenido/observaciones, y recién después adaptar aristas, anclajes y
  reconciliación. No implementar K1/K2b/S2/G1 con el marco viejo “identidad = lugar”.

## 1. Contradicciones con los documentos y el código actuales

| # | Documento / código | Qué afirma hoy | Contradicción con la corrección 2026-11-14 | Disposición |
|---|---|---|---|---|
| 1 | `docs/v2/02-sustrato-computacional.md §6`; `§2.1` | “Identidad = hash. Trivial y exacta.” `id(nodo) = H(...)` | El dueño dice que la identidad **no es hash**. El hash solo sensoriza/verifica estado. | Reescribir el contrato; no borrar el hash, reubicarlo como estado. |
| 2 | `src/kimun/kernel/node.cljc` (`identity-form`, `node-id`) | El id del nodo es el hash canónico de `{:class :kind :content}` | Convierte contenido en identidad. Dos signos idénticos pasan a ser “el mismo” nodo. | Mantener como `content-id`/estado de observación; no como id de unidad. |
| 3 | `src/kimun/kernel/edge.cljc` (`edge-id`) | El id de arista es hash de la arista; `:from`/`:to` son hashes de nodo | Las aristas referencian contenido, no unidades observadas. La misma afirmación sobre otra observación no distingue continuidad. | El id de arista puede seguir siendo id de aserción; los extremos deben pasar a ids de unidad. |
| 4 | `src/kimun/kernel/revision.cljc` | `revision-id = H(canonical-bytes Revision)` | Una revisión es un snapshot; su hash es estado, no identidad de unidad. | Aceptable como estado/snapshot; aclarar que no identifica unidades. |
| 5 | `src/kimun/kernel/tree.cljc` (`descriptor-id`) | El descriptor del árbol es content-addressed; el `tree-id` es ULID nominal | El ULID nominal es el único precedente de id no-hash, pero solo para árboles. | Precedente interno, **no** decisión aprobada para unidades. |
| 6 | `docs/v2/01-orden-filosofico.md §4.3` | “La identidad de un nodo es su contenido. Dos signos idénticos son el mismo nodo.” | El dueño niega identidad por contenido y por topología. La igualdad de bytes no prueba continuidad de una unidad. | Revisar el principio 3; separar igualdad de contenido de continuidad. |
| 7 | `docs/v2/14-juego-del-lenguaje.md §21`; `docs/v2/drafts/ids-desde-matrix.md` | “ID = identidad (nombre, ruta, símbolo); hash = mutación.” IDs estructurales/semánticos. | El dueño ahora dice que la identidad **tampoco es anclaje estructural/topológico**. Ruta y lugar contextual son localizadores, no identidad. | §21 queda superado en su parte positiva; conservar solo “hash no es identidad”. |
| 8 | `docs/v2/drafts/fuentes-identidad.md`; `docs/v2/drafts/preguntas-abiertas.md` D0 | Opciones (a) IDs taxonómicos puros y (b) CAS + capa de identidades | Ambas opciones presuponen que identidad = lugar (estructural/semántico). No explican la continuidad de la unidad observada. | La pregunta A/B era prematura; reformular D0. |
| 9 | `runs/research/2026-10-13/concilio/g-profundo/integracion-d0-g-completo.md` | Declara D0 resuelto como “identidad = lugar”; propone refactor de kernel | Contradice la corrección actual del dueño. | Documento histórico, **no autoridad**; no usarlo como base de implementación. |
| 10 | `docs/v2/12-ecosistema-y-pendientes.md §1` (D0, K1) | D0 = “identidad = lugar (estructural S / semántico M/G)”; K1 = “ids por lugar” | La identidad no es lugar. K1 queda mal definido antes de empezar. | Reescribir D0/K1; no implementar K1 con ese enunciado. |
| 11 | `src/kimun/kernel/anchor.cljc` (`resolves?`) | Un `:sign` resuelve si tiene posiciones en un árbol; M/G resuelven si existen en el CAS | No puede expresar “la misma unidad cambió de estado”: el cambio de contenido produce otro id y el original queda `orphan`. M/G quedan identificados por hash. | El anclaje debe comparar observaciones de una unidad, no existencia/posición de nodos. |
| 12 | `src/kimun/kernel/standoff.cljc` | Dirección `[leaf-id start end]`, con `leaf-id` = hash de la hoja | La dirección es estado/localizador, no identidad. Si la hoja cambia, la dirección vieja no sobrevive. | Reinterpretar como fragmento de localizador; la identidad debe ser externa a la hoja. |
| 13 | `docs/v2/02-sustrato-computacional.md §6.2`; `src/kimun/kernel/reconcile.cljc` | `intact/superseded/orphan`; `drifted` por posición/fingerprint/Dice; aceptar crea `supersedes` | La reconciliación **infiere** continuidad; el dueño pide identidad explícita de topologías marcadas y cambios en el tiempo. La heurística puede proponer, no identificar. | Mantener propuestas; separar “propuesta de continuidad” de “identidad de unidad”. |
| 14 | `docs/v2/14-juego-del-lenguaje.md §20` | Merkle sobre lo trackeado; bajar por la rama que cambió | “Trackeado” está definido como proyección que alimenta el grafo, no como topología marcada. | Alineado en el principio; falta definir la unidad marcada y su membrana. |
| 15 | `docs/v2/10-escala.md §11`; `src/kimun/kernel/revision.cljc` (`register-tree`) | Formato 4: `{fingerprint, clase, root-hash, content-hash}` por documento; árbol en caché | Es un registro de **estado por documento**, no de unidades marcadas ni de linaje. | Reutilizar como observación; no confundirlo con identidad. |
| 16 | `src/kimun/surface/docs.cljc` | Anchor externo por archivo; `:contract` en evidencia; `:sample` de 80 caracteres | `02 §6.4` pide 200 grafemas NFC; además el registro es por documento, no por topología marcada. | Deuda de sensorización; no es el eje de identidad, pero afecta comparaciones. |
| 17 | `docs/v2/01-orden-filosofico.md §8` | El bucle autopoiético son `drifted`/`orphan` convertidos en preguntas/tareas/alertas | El dueño amplía las señales a mutaciones de topología y quizá acciones; no solo anclajes rotos. | Ampliar taxonomía de señales; `drifted/orphan` es un subconjunto. |
| 18 | `docs/v2/14-juego-del-lenguaje.md §18.8` | “Dos documentos idénticos tienen los mismos nodos” | Compatible si “nodo” = observación de contenido; contradictorio si “nodo” = unidad con identidad. | Separar nodo-observación de unidad; no usar la igualdad de nodos como identidad. |
| 19 | `docs/v2/02-sustrato-computacional.md §6.2`; `anchor.cljc` | M/G resuelven por existir en el CAS | La identidad M no puede ser el hash de la forma canónica; el lugar contextual es localizador. G queda fuera de alcance. | Corregir la concesión de M; no diseñar G. |
| 20 | `docs/v2/05-estado.md` decisiones 1, 3, 4, 46 | `class/kind` al hash; timestamp fuera de arista; ids de árbol ULID; revisión de 8 campos; D0 pendiente | No todas caen: revisión, arista y árbol pueden seguir siendo estado/estructura. Lo que cae es **usar el hash como identidad de nodo/unidad**. | No reescribir todo el kernel; aislar la capa de identidad. |

## 2. Propuesta conceptual por nivel

> Esto es **propuesta**, no decisión del dueño. El dueño fijó qué debe lograrse; no fijó estos nombres,
> campos ni codificación.

Términos propuestos:

- **Unidad observada (`U`)**: topología marcada, delimitada por una membrana/selector. No es un nodo
  del pool; puede corresponder a una región, un subárbol, un archivo o un símbolo.
- **Identidad (`I`)**: continuidad declarada de `U` a través de observaciones y revisiones. Se asigna
  al marcar; no se deriva del contenido ni del lugar actual. Debe sobrevivir a editar y mover; copiar,
  dividir, fusionar y borrar exigen procedencia explícita.
- **Observación (`O`)**: lectura fechada de `U` en una revisión: localizador + estado + contrato +
  evidencia. Es lo que produce la sensorización.
- **Localizador (`L`)**: dónde está `U` ahora: ruta, rango, selector de AST, ruta de contexto. Es
  mutable y versionado; no identifica.
- **Estado (`E`)**: huella/fingerprint/Merkle root de lo seleccionado. Es lo que compara el sensor.
- **Contrato (`C`)**: nivel, perfil, parser, selector, normalización, clase y versión. Toda comparación
  es relativa a `C`: cambiar `C` no es cambiar la fuente.

| Nivel | Unidad | Identidad | Estado | Localizador | Contrato |
|---|---|---|---|---|---|
| **S** (texto/código como signo) | topología marcada en un artefacto: archivo, región, subárbol AST | linaje de la marca; id opaco asignado al marcar | fingerprint del artefacto + Merkle/hash de la proyección marcada | artefacto + ruta + rango/selector en la revisión | tipo de archivo, perfil, parser, selector, clase, normalización |
| **M** (desacople estructural/semántico) | símbolo/proposición como unidad de significado | linaje del símbolo en su contexto; equivalencia es curaduría | hash de la forma canónica **como estado**, no como id | ruta de contextos + signo/término | vocabulario del contexto, forma canónica, versión del motor |
| **G** | fuera de alcance | no se diseña | no se diseña | no se diseña | no se diseña |

Notas:

- La misma cosa puede conservar identidad en un nivel y cambiarla en otro. Ejemplo del coordinador:
  mover una función de archivo y cambiar su implementación puede mantener la unidad M (símbolo) y
  cambiar la unidad S (región textual). El contrato debe declarar el nivel.
- La identidad no se deduce de que los bytes sean iguales. La igualdad de bytes solo prueba que dos
  observaciones tienen el mismo estado; no prueba que sean la misma unidad.
- La ausencia de evidencia suficiente no se resuelve inventando continuidad: se marca `ambiguo` y se
  deja visible.

## 3. Contrato mínimo S–M para el kernel actual

### 3.1 Registros mínimos (propuesta)

```
Unit        {id, level, membrane, contract, lineage}
Observation {unit, revision, locator, state, contract, evidence, status}
Lineage     {unit, kind, from, to, actor, revision,
             kind ∈ continues | moved | copied-from | split-from | merged-from | retired}
```

- `Unit.id`: opaco, estable, **no** derivado de hash, ruta ni contenido. La codificación queda abierta.
- `membrane`: qué queda dentro/fuera de la unidad (selector y límites). No es una ruta.
- `contract`: lo necesario para volver a observar la misma clase de topología.
- `Observation.state`: fingerprint/Merkle/hash de lo seleccionado. Es el resultado del sensor.
- `Lineage`: procedencia explícita de continuidad, copia, división, fusión o retiro.
- El **store estable** guarda `Unit`, `Lineage` y la última `Observation` aceptada; el **caché** guarda
  árboles, bloques y proyecciones; el **CAS** guarda objetos de contenido para deduplicar/verificar.
  El CAS no es la identidad.

### 3.2 Operación de sensorización

1. Para cada `Unit`, resolver su `locator` bajo su `contract`.
2. Calcular `state` de lo seleccionado.
3. Comparar con la última `Observation` aceptada.
4. Emitir una señal; no mutar el grafo por la señal.

Señales mínimas propuestas (nombres abiertos): `unchanged`, `mutated`, `moved`, `missing`,
`ambiguous`, `contract-changed`, `result-changed`.

- `mutated`: mismo localizador, estado distinto.
- `moved`: el localizador anterior no resuelve y el nuevo resuelve con el mismo estado (o con
  evidencia suficiente).
- `missing`: no resuelve y no hay candidato.
- `ambiguous`: hay más de un candidato o la evidencia no alcanza.
- `contract-changed`: cambió parser/selector/perfil/clase; no es cambio de fuente.
- `result-changed`: para ejecuciones marcadas, cambió el resultado observado o sus dependencias.

### 3.3 Las dos verdades, sin diseñar G

- **V1 — vigencia de lo marcado**: `state` observado = `state` registrado en la última aceptación.
  Una mutación la invalida. Es una verdad operacional de S/M, no verdad de G.
- **V2 — verdad del resultado**: si se marcan ejecuciones, hay que distinguir resultado observado,
  condiciones/dependencias de ejecución y criterio de validación. Que un proceso termine no acredita
  cualquier afirmación sobre el resultado. Es especificación pendiente; no imponer TRUE/FALSE/UNKNOWN
  a G.

### 3.4 Acciones / regulación

Señal ≠ acción. Las acciones son transacciones explícitas con actor y evidencia:

- aceptar una observación (actualiza `state` aceptado);
- reubicar (`moved`) o declarar `missing`;
- re-marcar (nuevo contrato/selector);
- copiar, dividir, fusionar, retirar;
- reanclar dependencias afectadas.

Ninguna acción debe inferir identidad por posición, por Dice o por igualdad de bytes sin declararlo.

### 3.5 Aterrizaje concreto en módulos actuales

| Pieza actual | Qué conservar | Qué reinterpretar / separar |
|---|---|---|
| `src/kimun/kernel/node.cljc` | canonicalización, validación de forma, hash de contenido | `node-id` deja de ser id de unidad; pasa a `content-id`/estado de observación |
| `src/kimun/kernel/edge.cljc` | evidencia por tipo, id de aserción, inmutabilidad | `:from`/`:to` deben referir unidades; `:ref-hash` pasa a estado observado |
| `src/kimun/kernel/revision.cljc` | revisión como snapshot, log, heads, CAS | `revision-id` sigue siendo hash de snapshot; no identidad |
| `src/kimun/kernel/tree.cljc` | árboles de posiciones, Merkle, ULID nominal | el ULID es precedente de id nominal; no adoptarlo automáticamente para unidades |
| `src/kimun/kernel/anchor.cljc` | estados derivados y consultas | pasar de “nodo existe/tiene posición” a “unidad tiene observación y estado” |
| `src/kimun/kernel/reconcile.cljc` | propuestas fuera del pool, aceptación explícita | las propuestas son candidatos de continuidad, no identidad; la unidad ya existe |
| `src/kimun/kernel/standoff.cljc` | direcciones virtuales y capas UAX #29 | `[leaf-id start end]` es fragmento de localizador, no identidad |
| `src/kimun/kernel/store.cljc` + formato 4 | store estable vs caché, `register-tree` | `register-tree` alimenta `Observation`; falta el registro de `Unit`/`Lineage` |
| `src/kimun/surface/docs.cljc` | anchor externo, `:contract` | el registro por documento no sustituye el registro por unidad marcada |

Módulos nuevos propuestos, sin código: `kimun.kernel.unit`, `kimun.kernel.observation`,
`kimun.kernel.lineage`. La regla de anillos debe impedir que `unit/lineage` dependa de superficies.

## 4. Casos

| Caso | Identidad | Estado | Localizador | Contrato | Señal / acción |
|---|---|---|---|---|---|
| **Mover** | misma unidad | usualmente igual; si además se edita, distinto | cambia | igual | `moved`; si hay ambigüedad, declarar movimiento y no adivinar |
| **Editar** | misma unidad | cambia | igual | igual | `mutated`; V1 se invalida; dependencias afectadas se revisan |
| **Copiar** | nueva unidad por defecto; la fuente conserva la suya | igual al de la fuente al momento de copiar | nuevo | igual o distinto | `copied-from` explícito; la igualdad de bytes no prueba continuidad |
| **Dividir** | la unidad vieja se retira/sucede; nacen dos unidades | dos estados nuevos | dos localizadores | puede cambiar por rama | `split-from` explícito; mapear aristas y declarar qué queda huérfano |
| **Fusionar** | las unidades viejas se retiran; nace una nueva | estado nuevo (Merkle de la fusión) | nuevo | puede cambiar | `merged-from` explícito; declarar pérdidas y reanclajes |
| **Borrar** | la unidad no se borra del registro; se marca retirada/ausente | sin estado o estado anterior conservado como historia | no resuelve | igual | `missing`/`retired`; dependencias `orphan` declaradas, nunca desaparición silenciosa |

Matices:

- **Mover + editar** en el mismo acto: la identidad puede mantenerse si hay evidencia de continuidad;
  si no, se separa en `moved` + `mutated` y se deja la decisión al actor.
- **Copiar y borrar el original** no equivale automáticamente a mover. Si se quiere conservar la
  unidad, hay que declararlo; si no, es una unidad nueva y la vieja queda retirada.
- **Dividir/fusionar** no son operaciones de árbol (`:detach`/`:move`); son operaciones de linaje.
  El árbol puede cambiar después, pero la identidad se decide en el linaje.
- **Nivel M**: mover una función entre archivos puede conservar la unidad M y cambiar la S. El contrato
  debe decir qué nivel se está observando; no hay una identidad única para todos los niveles.

## 5. Qué está fijado por el dueño y qué es propuesta

### Fijado por el dueño (2026-11-14)

- Kimun es como el sistema nervioso de un sistema autopoiético de conocimiento: detecta señales,
  permite regulación y otras acciones.
- Las señales incluyen mutaciones de topología y quizá acciones.
- Merkle es la sensorización; los nodos representan qué tenemos sensorizado, normalmente documentos y
  código.
- La identidad **no es hash** y **no es anclaje estructural/topológico**. Hay que definir otra cosa
  que identifique topologías marcadas y cambios en el tiempo; Git inspira, pero hay niveles de
  abstracción.
- Dos verdades: (1) lo marcado no cambió desde la huella, invalidado por mutación; (2) si se marcan
  ejecuciones, verdad del resultado.
- Alcance actual S–M; G viene después.

### Propuesta (no aprobada)

- La separación `Unit / Observation / Lineage / locator / state / contract`.
- El id opaco asignado, no derivado; la membrana como selector; el linaje explícito.
- La taxonomía de señales y la regla “señal ≠ acción”.
- Mantener el CAS como almacenamiento de contenido/observaciones y el Merkle como sensorización.
- Los módulos `kimun.kernel.unit`, `kimun.kernel.observation`, `kimun.kernel.lineage`.
- Los casos: editar/mover conservan identidad; copiar crea unidad nueva; dividir/fusionar/borrar
  requieren linaje explícito.
- Que el localizador M sea la ruta de contextos y el estado M el hash de la forma canónica.

### Abierto (no convertir en decisión)

- Codificación del id de unidad (UUID, ULID, contador, nombre nominal u otra).
- Formato en disco y migración de stores existentes.
- Si la identidad M es por contexto o cross-contexto; cómo se cura `equivalent`.
- Si una ejecución marcada es una unidad de nivel S, M o un tercer tipo.
- Política exacta de aceptación/rechazo de señales y de resolución de ambigüedad.
- Si el linaje se guarda como aristas, como registro aparte o como ambos.

## 6. Límites de este informe

- No se ejecutaron tests ni se modificó producto.
- No se diseñó G.
- No se declara aprobada ninguna elección de UUID, ruta, CAS o formato.
- El informe `runs/research/2026-10-13/concilio/g-profundo/integracion-d0-g-completo.md` se cita como
  antecedente, no como autoridad: su tesis “identidad = lugar” contradice la corrección actual.
