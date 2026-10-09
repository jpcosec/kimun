# GATE15 — Carril B (ejecutabilidad), ronda 3

Auditor sin contexto. Solo lectura. Documento auditado: `docs/v2/15-capa-g.md` + `test/fixtures/g/*.edn`.
Kernel actual: `src/kimun/kernel/{node,edge,plan,store,anchor}.cljc`, `src/kimun/surface/adapters.cljc`.
Evidencia de spikes citada por el 15: `~/.cache/coord-briefs/R1-report.md`, `R3-report.md`.

## Hallazgos

1. **`bloqueante`** — Esquema del datom contradictorio entre el 15 y el dueño del formato.
   - 15 §8: «Esquema de datoms: `{:e <id> :a <id> :v <id|literal> :prov {…}}`» y «Fija B-A: el `:a` de un datom es el **id del término predicado `R`** (no una keyword de atributo)».
   - `10 §11` (forma en disco, dueño declarado por 15 §11): «Esquema de datoms: `{:e <id> :a <keyword> :v <id|literal> :prov {…}}`».
   - Corrección mínima: alinear `10 §11` con 15 §8 (`:a` = id de `R`) o declarar en §11 qué dueño gana.

2. **`bloqueante`** — La garantía «nunca `p×p`» del 15 §8 contradice la representación que manda el 14 y es internamente incoherente.
   - 15 §8: «no hay producto cartesiano de los términos del contexto ni matrices densas término×término»; «La cota es el **número de hechos de `R` en el `W_i`** (lineal en hechos, no en términos)»; «el cierre (`I* = ⋁_k I^k` por *squaring*) … y no amplía los candidatos».
   - 14 §8 / decisión 26: «cada `W_i` se materializa, para evaluar, como matrices booleanas por relación (`A × B`) … y **nunca crecen `p×p` sobre el corpus entero**». Esas matrices son densas sobre los términos que ocurren, y el cierre transitivo **sí** añade pares `(A,C)` que no aparecen en ningún hecho.
   - Corrección mínima: precisar que el veto `p×p` es contra el corpus/taxonomía entera, que la matriz por relación puede ser densa sobre los términos que ocurren en hechos de `R` en el `W_i`, y que el cierre amplía pares (acotarlo por alcanzabilidad, no por el número de hechos).

3. **`menor`** — Cita entrecomillada sin fuente.
   - 15 §8: «Es la lectura (b) de «a lo sumo por `W_i`»».
   - La frase «a lo sumo por `W_i`» no existe en `14`, `05` ni `02` (grep en `docs/`: solo aparece en `15`). `14 §8` dice «nunca crecen `p×p` sobre el corpus entero».
   - Corrección mínima: quitar las comillas o atribuir la lectura (b) al propio 15.

4. **`grave`** — El 15 §4 afirma que los ids de árbol salen del contenido; el 02 fija que son ULIDs nominales.
   - 15 §4: «Consolidar es determinista: los ids de árbol, nodo y arista salen del contenido».
   - `02 §3.1`: «Un árbol tiene un **id nominal**, no content-addressed: un ULID … acuñado al crearlo (`:new-tree`)»; `02 §10` inv. 13: «Los ids de árbol son nominales (refs); los ids de nodo, arista y objeto de árbol son content-addressed».
   - Corrección mínima: decir que los ids de nodo y arista salen del contenido y que los de árbol son ULIDs nominales (re-crear un árbol de contexto no es idempotente).

5. **`grave`** — Reconstructibility de lo `assert`ado: el 15 §4 solo cubre `facts assert`, pero el §9 introduce otra ruta de escritura.
   - 15 §4: «**`facts assert` siempre materializa un documento de decisión `:action :assert`** … Sin este documento, un `include build` limpio perdería el hecho». 15 §9: «`facts plan [FILE|-] [--write]` … con `--write` lo aplica».
   - `14 §2` (capa G): «Reconstruible … lo proyectado sí (motores); lo afirmado se reconstruye desde los documentos de decisión»; decisión 27.
   - Corrección mínima: que todo `--write` de un `:fact/entry` materialice su documento de decisión, o declarar que el fichero `FILE` es un documento `source` rastreado.

6. **`grave`** — «Informar los pendientes al abrir» por el puerto `Backend` cambia el contrato de `open` sin declararlo.
   - 15 §4: «al **abrir** un store con entradas pendientes … las entradas del flujo no consolidadas se informan»; 15 §11 (kernel): «el puerto `Backend` … hay que informar los pendientes al abrir».
   - `10 §6`: «`open` … Lee exactamente esos dos objetos [`:head`, `:heads-root`]»; `02 §8.1`: «`open` lee descriptor y cabezas … (sin replay)».
   - Corrección mínima: informar los pendientes en el CLI tras `open` (no en el puerto), o declarar la revisión de `10 §6`/`02 §8.1` en §11.

7. **`grave`** — El punto de extensión nombrado para `propositions` devuelve documentos, no plan.
   - 15 §7: «hay que despachar por perfil el plan (`plan-documents`), que hoy ramifica `(if (= profile "cli") (plan-cli …) (plan-symbols …))`»; «`facts plan` … compone un `TransactionPlan`».
   - `src/kimun/surface/adapters.cljc` `plan-documents`: contrato `{:documents [PLANNED] :problems [..]}`; los hechos `propositions` son nodos/aristas, no documentos (`11 §6`).
   - Corrección mínima: nombrar un planificador nuevo que devuelva ops de plan (`plan-propositions`) y dejar `plan-documents` para perfiles de documento.

8. **`menor`** — El 15 §9 añade grupos y warnings que son propiedad del `06`, pero `06` no está en «Revisa».
   - 15 §9: grupos `facts`, `contexts`, `ask`, `conflicts`, `graph rule`; 15 §4: warning «tipo `facts/pending-upstream`, `:data {:pending n}`» del «envelope de `06 §1`».
   - 15 §11: la tabla «Revisa» lista `02`, `10`, `11`, `12`, kernel — **no** `06`; `06 §1`/`06 §3` son dueños del inventario y de los códigos de salida.
   - Corrección mínima: añadir `06` a «Revisa» (inventario de grupos + envelope/códigos).

9. **`menor`** — Enunciados normativos del 15 sin fila en la tabla de promesas (§10).
   - No tienen test en §10: §5 «`:candidates` existe **solo** en `:kind :ambiguous` … en `:contradiction` la clave está **ausente**» (A-H9); §7 «sale 0 aunque falte el runtime»; §8 «**Ninguna arista entra al índice sin procedencia**» y «El fallback in-process nunca desaparece»; §9 «Su deriva con el repo es un fallo (`contexts/conflict`, 4)».
   - `03 §5`: «Para cada promesa normativa … existe un test que fallaría si la promesa se rompiera; la tabla `tests/promises.md` lo registra».
   - Corrección mínima: añadir una fila por cada enunciado.

10. **`menor`** — Los fixtures no materializan lo que §2/§13 describen para contextos y taxonomías.
    - 15 §2: «Un `W_i` es un nodo `:fact/:context` **y** un árbol-índice `{:kind :context}` cuyos hijos son los términos y relaciones admisibles»; §10 promete `g/taxonomy-admits-one-position-per-node`.
    - `test/fixtures/g/`: no hay ningún descriptor `{:kind :taxonomy}` ni aristas `ownership` (grep: solo comentarios en `context.edn`); la promesa no puede ejercerse desde los fixtures tal como §13 los enumera.
    - Corrección mínima: añadir los descriptores de las tres taxonomías y las aristas `ownership` del árbol de contexto a los fixtures, o acotar la promesa.

11. **`menor`** — El 15 §8 no asigna anillo al evaluador Datalog in-process.
    - 15 §8: «Camino estable: evaluador Datalog semi-naive in-process sobre el índice»; §8: el índice vive en `<store>/index/graph/`.
    - `03 §1`: ring 0 `kimun.kernel.*` es «puro: datos + funciones … Lo que necesita de la plataforma (hash, …, ficheros) lo define como puerto».
    - Corrección mínima: declararlo superficie (ring 3) o un núcleo puro de ring 0 alimentado por el host.

## Ambigüedades

- 15 §8: «matrices booleanas por relación (filas × columnas) **sobre los términos que ocurren en hechos de `R`**» vs «no hay … matrices densas término×término». (i) la matriz es densa sobre los términos que ocurren; (ii) solo se prohíbe la matriz sobre la taxonomía completa admisible.
- 15 §4: «los ids de árbol, nodo y arista salen del contenido». (i) se refiere a los *objetos* de árbol (tree-hash, content-addressed); (ii) se refiere a los ids de árbol (ULIDs).
- 15 §6: «`:prov` obligatoria» en `binding`. (i) la clave `:prov` (vector de spans) pasa a ser obligatoria en la evidencia; (ii) solo se reafirma que la arista lleva evidencia (`:ref-hash`/origen/`:context`, ya obligatorios en `02 §3.2`).
- 15 §3/§7: `:status :proposed` como default frente a `14 §5`, que fija `:status ∈ {observed confirmed rejected retracted}`. (i) se amplía el enum de `edge/statuses` con `:proposed`; (ii) `:status` de decisión + campo `:curation` aparte (opción (b) de §12 Q1).

## Carril B — respuestas

- **¿Implementable sin violar `03`?** Sí, con el kernel puro. Cambios al kernel exigidos y declarados en §11: `node/shapes` (§1–§2: `:triple :object` id, `:context` con `:granularity`/`:of`, `:proposition` anidada — hoy `node.cljc` exige `(string? (:object c))` sin restringir a término y `:form` solo admite hojas atómicas), `edge/statuses` + `required-evidence` (§3: `:status` de decisión y `:tier` obligatorio; `binding` con `:prov`; `semantic` con `:relation`/`:context-target`/`:phi` — hoy `edge/statuses` = `#{:sinnvoll :sinnlos :unsinnig}`), `plan` (chequeo cross-op sin `:projection`), `Backend` (F6) y `anchor/resolves?` (endurecer a árbol `:context`; ya es puro y lee `[:content :text]`). No exigen kernel: §5 (conflictos), §7 (`adapters`), motor/índice de §8 y §9 (superficie), §10. Sin sección que obligue a I/O en ring 0; falta la asignación de anillo de §8 (F11).
- **¿Reconstruibles `index/graph/` y `facts.log.edn` (02 inv. 10, `14 §2`)?** `index/graph/`: sí en principio (el `:prov` sale de la evidencia de la arista, y las aristas viven en la revisión, alcanzable desde log+pool), pero hoy queda indefinido por F1 y necesita el `:tier` nuevo de §3. `facts.log.edn`: parcialmente; F4 (ruta `plan --write` sin documento de decisión) y F3 (árboles ULID) rompen la afirmación tal como está escrita.
- **¿Cada promesa de §10 tiene test que la rompería y es escribible con `bb test`?** La mayoría sí: son funciones puras (kernel/`adapters`) o CLI con directorio temporal, como las filas ya existentes en `tests/promises.md` (p. ej. `cli/include_test/build-reproduces-the-store`). Faltan filas para varios enunciados normativos (F9); además los nombres `g/…` no siguen la convención `*_test.cljc` de `03 §5`.
- **¿`propositions` compatible con el protocolo 1 de `11` (un adaptador existente sigue válido)?** Sí, aditivo: `11 §3–5` (invocación, `--describe`, stdout JSON, exits) se conservan y `15 §7` repite el mismo mapa de exits (`missing` 1, `conflict` 4, `runtime|failed|invalid-output` 5, `cli/usage` 6). Requiere ramificar por perfil `validate` (hoy `fact-keys` es único y `(contains? profile-models …)` rechaza el perfil nuevo) y `plan-documents` (hoy `(if (= profile "cli") (plan-cli …) (plan-symbols …))`), exactamente lo que el 15 declara (B-7). El desajuste es el contrato de retorno de `plan-documents` (F7), no la compatibilidad de `symbols`/`cli`.
- **¿Costes `p×p` con los símbolos (prohibido por `14 §8`)?** F2: el 15 §8 afirma cota lineal en hechos y niega matrices densas, pero a la vez define matrices booleanas por relación sobre los términos observados y un cierre por *squaring* que añade pares; contradice `14 §8`/decisión 26 y su propia «lectura (b)».

## Veredicto

**ABIERTO** — bloqueantes: F1 (esquema del datom `:a`: 15 §8 vs `10 §11`) y F2 (`p×p`/matrices densas/cierre: 15 §8 vs `14 §8` y decisión 26).
