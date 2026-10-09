# GATE15 — Carril B (ejecutabilidad), ronda 4

Auditor sin contexto. Solo lectura. Kernel actual: `src/kimun/kernel/*`, `src/kimun/surface/*`,
`src/kimun/host/*`; anillos en `03 §1`. Documento auditado: `docs/v2/15-capa-g.md`.

Evidencia ejecutada: `memcap timeout 300 bb -e "(require 'kimun.g.fixtures-test) …"` →
`{:test 5, :pass 400, :fail 0, :error 0}`.

## Respuestas directas a las preguntas del carril

- **Secciones que exigen cambio de kernel:** §1 (`kimun.kernel.node/shapes` para `:proposition`
  con formas anidadas/hojas de término y `kimun.kernel.plan` con el chequeo nuevo), §3
  (`kimun.kernel.edge/statuses` + `required-evidence`), §6 (`kimun.kernel.anchor/resolves?`),
  §8 (`kimun.kernel.datalog`, namespace nuevo). No exigen kernel: §2, §4, §5 (el informe de
  conflicto es caché del host; el evaluador es el de §8), §7 (solo `kimun.surface.adapters`),
  §9 (CLI), §10 (tests).
- **Índice `index/graph/`:** reconstruible desde el pool (datoms derivados de aristas
  `projection` + nodos, `02 §10` inv. 10; esquema en `10 §11`). **`facts.log.edn`:** es caché
  bajo `:git :ignore`, pero su reconstrucción depende de que los documentos de decisión
  lleven el contenido del hecho (H4) y de que las instrucciones `contexts create` estén
  versionadas (H5).
- **Tabla de promesas (`§10`):** escribible con `bb test` (el `fixtures_test.cljc` ya corre
  verde), salvo dos filas no falsables por su forma (H10).
- **Compatibilidad con el protocolo 1 de `11`:** sí, aditiva (mismo `protocol: 1`, perfil
  nuevo en `profile-models`, `plan-propositions` aparte). Requisito: `validate` debe despachar
  por perfil; hoy `fact-keys` se exige a *todo* hecho (`adapters.cljc:82`), y un hecho de
  `propositions` fallaría con `:adapters/missing-key`.
- **Costes `p×p`:** sí hay riesgo en `W_i :granularity :corpus` (H2); para `W_i` de documento
  la cota de §8 se mantiene.

## Hallazgos

1. **bloqueante** — `15 §11`: "`kimun.kernel.anchor/resolves?` (endurecer a «pertenece a un
   árbol `:context`»)". Choca con `15 §2`: el árbol `:context` es "un árbol-índice `{:kind
   :context}` cuyos hijos son los términos y relaciones admisibles" (el fixture
   `context.edn` no cuelga ningún `:fact` ni `:symbol/:proposition`). Como los hechos no
   ocupan posiciones en ese árbol, endurecer `resolves?` para `:fact` los deja **todos**
   `orphan`, contra `15 §6` ("Un hecho cuya hoja no cambió conserva `intact` su ancla tras un
   `rebuild`") y la promesa `g/intact-facts-survive-rebuild`. `02 §6.2` arrastra la misma
   concesión ("El hito 7 la endurece a «pertenece a algún árbol `:context`»") sin notar que el
   árbol sólo contiene vocabulario. Corrección: endurecer sólo los términos (que sí son hijos)
   y anclar los hechos por su cadena `binding`/`projection`, o declarar en §2 que el árbol
   `:context` cuelga también hechos y proposiciones.

2. **grave** — `15 §8`: "Un `W_i :granularity :corpus` sigue esa misma regla: la matriz nunca
   sale de los términos que ocurren en hechos de `R` en ese `W_i`" y "a lo sumo `|sujetos de R
   en W_i| · |objetos de R en W_i|`, nunca el corpus". Choca con `14 §8`: "nunca crecen `p×p`
   sobre el corpus entero" (y `05` decisión 26: "nunca `p×p` global"). Un `W_i` de corpus tiene
   por definición los términos de sus hechos, así que su matriz y su cota son `p×p` sobre ese
   corpus; la justificación de §8 ("no prohíbe la representación por contexto") se anula cuando
   el contexto *es* el corpus. Corrección: acotar la matriz al subgrafo alcanzable por cierre o
   prohibir la representación densa en `:granularity :corpus`.

3. **grave** — `15 §3`: "El **sentido** (`sinnvoll|sinnlos|unsinnig`) no vive en la arista: es
   el índice derivado `S` de §8" y "`unsinnig` solo lo marca un humano"; `15 §4` materializa
   `:action :unsinnig` como documento. No se fija dónde queda la marca humana en el pool: un
   documento de decisión es un archivo del repo, y el evaluador `S` es kernel ring 0 puro que
   lee pool/índice, no archivos. `14 §5` exige "`S ∈ #{sinnvoll sinnlos unsinnig}` … calculados
   por el evaluador booleano y guardados como índice derivado". Corrección: definir el nodo o
   arista del pool (p. ej. un hecho `(sense <fact> unsinnig) @ W_i` con evidencia de actor) del
   que `S` derive `unsinnig`.

4. **grave** — `15 §4`: "`facts assert` siempre materializa un documento de decisión `:action
   :assert` … con el hecho, su contexto y su evidencia", pero la forma mínima del documento es
   `:target {:fact <id> :edge <projection-edge-id>}`. Choca con `14 §2`: "lo afirmado se
   reconstruye desde los documentos de decisión" (y `14 §10`/decisión 27: el log es caché bajo
   `:git :ignore`). Un id es un hash no invertible: con `facts.log.edn` descartado, `include
   build` no puede rehacer el nodo `:fact/:triple`, su proposición, `binding`, `span` ni hoja.
   Corrección: incluir el `:fact/entry` canónico completo (nodos+aristas) en el documento
   `:action :assert`, o declarar que sólo es reconstruible re-ejecutando el motor.

5. **grave** — `15 §2`: "`{:section …}`/`{:corpus …}` … se declaran por instrucción (`contexts
   create`, §9)". No se fija dónde vive esa instrucción versionada; `15 §4` sólo declara
   documentos bajo `:decisions` y `14 §2` exige reconstruir el store "desde archivos +
   decisiones". Un `include build --rebuild` no puede recrear contextos `:section`, `:corpus` o
   `:transversal` si su declaración no está en un fichero rastreado. Corrección: declarar las
   instrucciones `contexts create` en `kimun.include.edn` (dueño `13`), como se hace con `:decisions`.

6. **menor** — `15 §7`: "`facts plan` … resuelve/crea los términos y la proposición, materializa
   las hojas y `:sign/:span`". La salida `propositions` trae `context` con
   `:name/:granularity/:of`; si el `W_i` no existe, el plan no puede referenciar su nodo
   (chequeo `ids-exist`, `02 §5.1`). Corrección: declarar que `plan-propositions` crea, o exige
   preexistente, el nodo `:fact/:context` y su árbol.

7. **menor** — `15 §11` atribuye a `kimun.kernel.node/shapes` exigir "`:triple` con `:object`
   id" y ":proposition … hojas solo ids de término". `node/validate` no tiene pool
   (`02 §2.1`): `shapes` sólo distingue tipos de string y no puede separar un id de término de
   un literal. La promesa `g/fact-object-must-be-a-term` no se cumple en `shapes`. Corrección:
   poner ese chequeo en `plan`/revisión (donde hay pool), no en `node/shapes`.

8. **menor** — `15 §11` lista en la fila de kernel sólo `kimun.kernel.edge/statuses`, pero los
   cambios de `15 §3` (`:tier` obligatorio en `projection`), `§6` (`:prov` obligatoria en
   `binding`) y `§2` (`:relation/:context-target/:phi` en `semantic`) tocan
   `kimun.kernel.edge/required-evidence` (`src/kimun/kernel/edge.cljc`). Corrección: añadirlo a
   la fila de kernel de §11.

9. **menor** — `15 §5`: "`ask` devuelve `:ambiguous` con candidatos y nunca elige" frente a
   "`:candidates` existe **solo** en `:kind :ambiguous`". Para un informe `:kind
   :contradiction` (el fixture `conflict.edn`) no hay `:candidates`, y la promesa
   `g/ask-on-open-conflict-is-ambiguous-with-candidates` exige candidatos. Corrección: precisar
   que `ask` sobre `:contradiction` devuelve los hechos en conflicto, o que la unión de
   contextos emite `:kind :ambiguous` con candidatos.

10. **menor** — `15 §10` incluye filas que no son falsables por un test unitario: "el fallback
    Datalog in-process nunca desaparece" (`g/in-process-fallback-never-goes-away`) y
    "`adapters --describe` sale 0 aunque falte el runtime" (exige subproceso). `03 §5` exige que
    el test "fallaría si la promesa se rompiera". Corrección: reformular la primera a un
    comportamiento observable ("una consulta funciona sin el pod Datalevin") y reconocer la
    segunda como test de integración de subproceso.

## Ambigüedades

- `15 §7`: "Todas las claves están siempre presentes; valen `null` o `[]` cuando no aplican" vs
  "`tier` y `status` son obligatorios". Lectura (a): obligatorios = presentes y no nulos.
  Lectura (b): obligatorios = presentes, pero pueden ser `null` como el resto.
- `15 §8`: "evaluar en el `W_i` — álgebra booleana: `V_i`, `S_i`, `I`" junto a "Matrix/wittgenstein
  entra como motor externo … evaluador" y "Camino estable: evaluador Datalog semi-naive
  in-process … única vía offline". Lectura (a): el kernel in-process también calcula `V`/`S`/`I`
  y el cierre. Lectura (b): el in-process sólo recorre el grafo (Datalog) y `V`/`S`/`I` exigen
  el motor externo, con lo que no hay evaluación offline completa.
- `15 §1`: "Un hecho siempre tiene al menos una proposición de la que es proyección … un
  `:add-node` de `:fact/:triple` que no lleve una arista `:projection` hacia él **en el mismo
  plan** se rechaza". Lectura (a): invariante del pool (ningún hecho sin proyección activa).
  Lectura (b): invariante sólo del plan; un `:remove-edge` posterior de la `projection` deja un
  hecho admitido sin proyección.
- `15 §4`: "el hecho reconstruible vive como documento de decisión". Lectura (a): el documento
  de decisión lleva contenido suficiente para reconstruir el hecho. Lectura (b): sólo referencia
  ids y la reconstrucción depende de re-ejecutar el motor (choca con H4).

## Veredicto

**ABIERTO** — bloqueante: H1 (`anchor/resolves?` endurecido a un árbol `:context` que no
contiene hechos). H2 puede ser bloqueante si un `W_i :corpus` abarca el store.
