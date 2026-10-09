# GATE15 · Carril B (ejecutabilidad) — ronda 5

> Auditor sin contexto previo. Solo lectura. Documento auditado: `docs/v2/15-capa-g.md`
> (R4, commit `7e08ef40`) + `test/fixtures/g/*.edn`. Autoridad superior citada: `14`, `02`,
> `03`, `05`, `10`, `11`. Citas exactas.

## Hallazgos

1. **grave** — El protocolo `propositions` no tiene camino de ejecución especificado.
   15 §7: «`facts plan` (o `contexts define` para el vocabulario) resuelve/crea los términos
   y la proposición, materializa las hojas y `:sign/:span` de cada `prov`, y compone un
   `TransactionPlan``; y «El planificador nuevo, `kimun.surface.adapters/plan-propositions``;
   y «Hoy `plan-documents` ramifica `(if (= profile "cli") (plan-cli …) (plan-symbols …))`;
   `propositions` no entra por ahí».
   — No cubre / choca: 15 §9 define «`facts plan [FILE|-] [--write]` lee un `:fact/entry`
   (§4; forma en disco en `10 §11`)», y `11 §7` sólo da `adapters scan|sync|check|list`
   (documentos); §7 mismo dice que `plan-documents` «no sirve aquí». No hay subcomando ni
   función que ejecute el adaptador y entregue su JSON a `plan-propositions`.
   — Corrección mínima: fijar el punto de entrada (p. ej. `facts plan --adapter NAME ROOT`,
   o un subcomando `adapters propositions`) que corre el adaptador y llama a `plan-propositions`.

2. **grave** — `resolves?` de un hecho es circular tal como está definido.
   15 §6: «un **hecho** resuelve por (a) su `:content :context` apuntando a un nodo
   `:fact/:context` existente en el CAS y (b) su cadena de anclaje `binding`/`projection`
   resuelta»; y «El estado de un hecho es el **peor** de las aristas de su cadena de anclaje».
   — Choca: `02 §6.2` calcula el estado «por extremo» y «Estado de la arista = el peor de sus
   dos extremos»; el otro extremo de la `projection` es el hecho, de modo que (b) depende del
   estado de la arista que depende de (a)/(b).
   — Corrección mínima: definir `resolves?` de un hecho sólo por (a) + existencia en CAS, y
   dejar la resolución de la cadena a la regla de estado (que ya es «el peor de la cadena»),
   excluyendo el extremo del propio hecho.

3. **grave** — El cierre `I*` sobre un `W_i :granularity :corpus` puede crecer `p×p`.
   15 §8: «Se calcula por **alcanzabilidad** (BFS / semi-naive) **sobre la forma dispersa**,
   con memoria `O(pares alcanzables)`» y «**Sí añade pares** `(A, C)` que no aparecen en
   ningún hecho»; la prohibición que fija es sólo «La forma densa queda **prohibida** para
   todo `W_i :granularity :corpus`».
   — Choca: 14 §8 «nunca crecen `p×p` sobre el corpus entero» (y `05` decisión 26, «nunca
   `p×p` global»); sobre un corpus con una cadena transitiva de `p` símbolos el cierre
   materializado es Θ(p²) y 15 no lo excluye ni lo capa.
   — Corrección mínima: para `:granularity :corpus`, no materializar `I*` (calcúlalo bajo
   demanda) o declarar un tope de pares, manteniendo la memoria en `O(aristas)`.

4. **menor** — Las promesas de complejidad de memoria de §10 no son rompibles con `bb test`.
   15 §10: «la representación por `R` y `W_i` es **dispersa** (memoria `O(hechos de R en
   W_i)`)» (test `g/candidate-pairs-are-sparse-not-dense`).
   — Choca: `03 §5` exige «un test que **fallaría si la promesa se rompiera**»; una cota
   asintótica no se puede falsificar midiendo memoria.
   — Corrección mínima: reformular la promesa como observable (no se materializa estructura
   densa por encima de `:graph/dense-terms`; densa rechazada en corpus).

5. **menor** — El informe `:ambiguous` no es reconstruible desde log+pool.
   15 §5: «`:candidates [{:fact <id> :context <W_j> :phi "0.97"} …]`» presentado como «caché
   derivada en `<store>/index/graph/conflicts/<conflict-id>.edn` (gitignored, reconstruible
   por cabecera;».
   — Choca: `02 §10` inv. 10 «Lo derivado se reconstruye desde log + pool; no tiene
   autoridad»; el `:phi` de un candidato **no promovido** lo produce un motor de convergencia
   y no queda en aristas del pool (sólo el `:phi` de aristas `:semantic` promovidas, 15 §2).
   — Corrección mínima: persistir el `:phi`/entrada de convergencia en la evidencia del pool,
   o declarar que ese caché sólo se reconstruye re-ejecutando el motor.

6. **menor** — El chequeo «última `projection` activa» se atribuye a `plan` sin acceso al
   estado activo.
   15 §11: «`kimun.kernel.plan` (… `:plan/rejected` … de un `:remove-edge` de la última
   `projection` activa; …)».
   — Choca: `src/kimun/kernel/plan.cljc` se documenta como «A plan is pure EDN data; it
   applies entirely or not at all», y son las ops de `apply-plan` (con el store) las que ven
   las aristas activas (`02 §5.1`: «viven en las aristas activas de la revisión»).
   — Corrección mínima: atribuir el chequeo a `apply-plan` con el store, o declarar que el
   validador del plan recibe el conjunto de aristas activas.

7. **menor** — El umbral `:graph/dense-terms` no tiene camino definido hasta el evaluador de
   anillo 0.
   15 §8: «parámetro `:graph/dense-terms` de `store.edn` (default **4096** términos por
   relación por `W_i`)».
   — No cubre: `02 §8.1` define `store.edn` como «descriptor: `:hash-alg`, `:capabilities`,
   `:format-version`», y `src/kimun/kernel/store.cljc` (`open`) sólo extrae
   `{:keys [capabilities hash-alg] fv :format-version}` al valor del store.
   — Corrección mínima: declarar que el descriptor completo (o esa clave) llega al evaluador
   como parámetro inyectado por el host.

8. **menor** — El álgebra `V/S/I` y el cierre no tienen namespace asignado en el inventario
   del kernel.
   15 §8: «El evaluador in-process (ring 0) calcula `V`, `S`, `I` y el cierre sobre la forma
   dispersa»; 15 §11 sólo nombra `kimun.kernel.datalog` («evaluador semi-naive, **ring 0
   puro** sobre valores»).
   — Choca: `03 §2` «Un namespace = una responsabilidad nombrable en una frase».
   — Corrección mínima: nombrar el namespace del álgebra booleana/cierre en §11 (o declarar
   que vive en `kimun.kernel.datalog`).

9. **menor** — El default 4096 se declara «medido» sin cita de la medición.
   15 §8: «bajo un umbral medido y declarado: parámetro `:graph/dense-terms` … default
   **4096**».
   — No cubre: `14 §8` deja el motor al índice real («El motor definitivo se confirma en la
   etapa A con el índice real»); no hay run/bench que sostenga 4096.
   — Corrección mínima: citar la medición que fija 4096 o marcarlo provisional de etapa A.

## Ambigüedades

- **A1** · 15 §6 «su cadena de anclaje `binding`/`projection` resuelta».
  (i) la cadena entera, incluido el extremo del hecho → circular con §6 «el **peor** de las
  aristas de su cadena»; (ii) sólo los otros extremos de la cadena (proposición, spans/hojas)
  → no circular.
- **A2** · 15 §8 «con memoria `O(pares alcanzables)`».
  (i) los pares verdaderos del cierre, representación mínima → compatible con 14 §8 (el veto
  es a la matriz densa); (ii) materialización Θ(p²) sobre un corpus → viola 14 §8.
- **A3** · 15 §4 «con el `:fact/entry` canónico completo … y añade `:leaves`».
  (i) `:leaves` dentro de `:fact/entry` (como `test/fixtures/g/decision.edn`);
  (ii) clave hermana de `:fact/entry` en el documento de decisión.
- **A4** · 15 §7 «`facts plan` … compone un `TransactionPlan`».
  (i) `facts plan` acepta salida de adaptador JSON; (ii) `facts plan` lee `:fact/entry`
  (§9) y `plan-propositions` tiene un llamante distinto no especificado.
- **A5** · 15 §1 «**Revisa** `02 §2.1` (`:object <id|literal>` pasa a `:object <id>`)».
  (i) `node/shapes` se endurece; (ii) `node/shapes` no cambia y el chequeo vive sólo en
  `plan` (§11: «el chequeo … vive aquí, **no** en `node/shapes`»).

## Respuestas directas (Carril B)

- **Anillos/`03`.** No encontré violación de anillos: `facts.log.edn`, `index/graph/`,
  documentos de decisión y `:graph/dense-terms` quedan en host/surface y el kernel recibe
  valores. Cambios de kernel declarados en §11 por sección: §1–§2 (`node/shapes`), §3
  (`edge/statuses` + `required-evidence`), §6 (`anchor/resolves?`), §7 (`plan`), §8
  (`kimun.kernel.datalog`, nuevo). Sin cambio de kernel: §4, §5, §9, §10. Objeciones: 6–8.
- **Reconstruibilidad (inv. 10).** Sí para `index/graph/`: `10 §11` es dueño del esquema
  `{:e :a :v :prov}` y todos los campos de `:prov` están en la evidencia de la arista. El
  `facts.log.edn` no es derivado del pool (es el flujo de escritura); 15 §4 acierta al situar
  lo reconstruible en los documentos de decisión + vocabulario `source`. Salvedad: hallazgo 5.
- **Tabla §10.** Cubre casi todo enunciado normativo con un test nominal; escribible con
  `bb test` (incluidas las filas de subproceso). Falla la falsabilidad de las cotas de
  memoria (hallazgo 4) y las filas aún no están registradas en `tests/promises.md`
  (dueño en la tabla de autoridad, `README.md`).
- **Compatibilidad protocolo 1.** Sí: `symbols`/`cli` no cambian; `validate` ya rechaza el
  perfil desconocido (`11 §4.3`) y la extensión es aditiva por perfil. El perfil nuevo no
  tiene consumidor (hallazgo 1).
- **`p×p`.** La representación dispersa (`O(hechos de R en W_i)`) cumple 14 §8; el único
  coste que puede crecer `p×p` con los símbolos es el cierre en `:granularity :corpus`
  (hallazgo 3 / ambigüedad A2).

## Veredicto

**CERRADO CON MENORES** — sin bloqueantes. Hallazgos 1–3 son graves pero no bloquean la
implementación (admiten lectura correctiva local o lectura alternativa: A1–A2).
