# GATE15 — Carril B (ejecutabilidad)

Auditoría de solo lectura sobre `docs/v2/15-capa-g.md` y `test/fixtures/g/*.edn` contra
`02`, `03`, `06`, `10`, `11`, `14` y el kernel actual (`src/kimun/kernel/*`,
`src/kimun/surface/*`, `src/kimun/host/*`). No se editó nada.

## Hallazgos

### H1 — §1 y §3 exigen cambios al kernel, y §15 §11 no los localiza en módulos (menor)

- `15 §1`: "`R`, `A` y `B` son **ids de nodos `:symbol/:term`**, nunca strings ni literales
  crudos" y "**Revisa** `02 §2.1` (`:object <id|literal>` pasa a `:object <id>`)".
- `15 §3`: "`:status :proposed|:observed|:confirmed|:rejected|:retracted`" y "**Revisa**
  `02 §3.2`, donde `projection :status` valía `sinnvoll|sinnlos|unsinnig`".
- Kernel actual: `src/kimun/kernel/node.cljc` `shapes` valida `:fact/:triple` con
  `(contains? c :object)` (acepta literal crudo) y `:symbol/:proposition` con
  `(every? … (rest (:form c)))` (rechaza formas anidadas); `src/kimun/kernel/edge.cljc`
  `statuses` es `#{:sinnvoll :sinnlos :unsinnig}` y `edge/validate` rechaza `:proposed`.
- Corrección: en la tabla "Revisa" de `§11`, nombrar los módulos que cambian
  (`kimun.kernel.node/shapes`, `kimun.kernel.edge/statuses`) además de `02 §2.1`/`§3.2`.

### H2 — La invariante "un hecho siempre tiene una proposición" no es ejecutable ni testeable (grave)

- `15 §1`: "Un hecho siempre tiene al menos una proposición de la que es proyección (§3)".
- `02 §5.1` fija los siete chequeos del plan (CAS, existencia, árbol, evidencia, capability,
  opaco, datos puros); ninguno cruza `:add-node :fact/:triple` con `:add-edge :projection`.
  El kernel (`plan.cljc`, `store.cljc`) no contiene ninguna referencia a `projection`.
- Corrección: añadir el chequeo del plan (o regla de `facts plan`) que rechace un
  `:fact/:triple` sin `:projection` en el mismo plan y una fila en `§10`.

### H3 — `§8` permite `p×p` cuando el contexto es `:granularity :corpus` (grave)

- `15 §8`: "**Nunca `p×p` global:** la intensidad se calcula sobre pares candidatos, a lo
  sumo por `W_i`".
- `15 §2`: "**Más grueso:** `:corpus` con `:of {:globs […]}`" — un `W_i` puede abarcar el
  corpus entero, con lo que "por `W_i`" vuelve a ser global.
- `14 §8`: "**nunca crecen `p×p` sobre el corpus entero** (límite que wittgenstein aprendió
  con tres OOM)".
- Corrección: acotar `§8` (prohibir matrices densas cuando el `W_i` es `:corpus`, o exigir
  candidatos acotados por relación) y reflejarlo en la promesa de `§10`.

### H4 — `facts.log.edn` e `index/graph/` son forma en disco y `§11` dice no tocar su dueño (bloqueante)

- `15 §4`: "`<store>/facts.log.edn`, append-only, **una línea por hecho** con su procedencia";
  `15 §8`: "Vive en `<store>/index/graph/`".
- `15 §11`: "**No toca:** … `10` (formato 3 en disco)".
- `10 §0`: "Este documento es el **único dueño del formato en disco**"; `14 §10`:
  "`10-escala.md` sigue siendo el dueño del formato y recibirá esta sección".
- Corrección: mover la definición de `facts.log.edn` e `index/graph/` a `10` y añadir `10` a
  la tabla "Revisa" de `§11`.

### H5 — `:upstream {:pending n}` es un cambio de envelope cuyo dueño es `06` (grave)

- `15 §4`: "Al abrir: las entradas del flujo no consolidadas se informan
  (`:upstream {:pending n}`)".
- `06 §1` define el envelope como `{:ok :command :store :revision :data :warnings}`; no hay
  `:upstream`. `15 §9`: "Envelope y códigos de salida consistentes con `06 §1`".
- `15 §11` no lista `06`.
- Corrección: añadir `06 §1` a "Revisa" (o emitir el aviso por `:warnings`).

### H6 — La tabla de promesas de `§10` no cubre varios enunciados normativos (grave)

- `15 §10`: "Todo `deferred` hasta la etapa A/C (implementación)"; 21 filas.
- Sin fila que las rompa quedan, entre otras, `15 §1` ("`:form` admite formas anidadas"),
  `15 §1` ("el hecho es el **estado** de la proposición", H2), `15 §2` (transversales
  `(member X <term>) @ W_transversal`), `15 §3` ("`unsinnig` solo lo marca un humano",
  "La promoción automática solo existe por política declarada", ":rejected se conserva"),
  `15 §5` (las tres resoluciones por separado) y `15 §9` (mapa de exits `ask/ambiguous`→2,
  `conflicts/conflict`→4).
- `03 §5`: "Para cada promesa normativa … existe un test que **fallaría si la promesa se
  rompiera**".
- Corrección: añadir una fila por cada enunciado normativo sin test (al menos los citados).

### H7 — `g/no-p-by-p-structures` no es un test que "rompa" en `bb test` (menor)

- `15 §10`: "ninguna estructura crece `p×p` sobre el corpus | `g/no-p-by-p-structures`".
- `03 §5`: "Un test que compara la función consigo misma no cuenta".
- Corrección: reformularla como test de cota/medición sobre un `W_i` acotado, o retirarla y
  cubrir H3 con una regla de construcción.

### H8 — `propositions` es compatible con `11` solo cambiando también el planificador (menor)

- `15 §7`: "Extiende el protocolo 1 de `11` … `kimun.surface.adapters/validate` se extiende
  con el perfil `propositions`".
- `src/kimun/surface/adapters.cljc`: `profile-models` solo tiene `"symbols"`/`"cli"`, y
  `plan-documents` despacha `(if (= profile "cli") (plan-cli …) (plan-symbols …))`.
- `11 §4.3`: la validación exige "perfil conocido, `facts` vector".
- Los adaptadores existentes (`symbols`/`cli`) siguen válidos: la extensión es aditiva si
  `validate` y `plan-documents` despachan por perfil.
- Corrección: precisar en `§7` que hay que despachar `validate` **y** el plan por perfil.

### H9 — `:tier` en la evidencia de `projection`: ¿obligatorio? (menor)

- `15 §3` lista `:tier :observed|:confirmed|:inferred|:statistical` en la evidencia de
  `projection`, pero no dice si es campo obligatorio; `02 §3.2` solo lo tiene como clave
  adicional opcional.
- Corrección: fijar en `§3` que `:tier` es obligatorio en `projection` (y añadirlo al
  mínimo de `02 §3.2`).

### H10 — "Idempotente" en `§4` vale para aristas, no para revisiones (menor)

- `15 §4`: "reintentar la consolidación es idempotente"; test `§10`
  "`g/facts-log-consolidation-is-idempotent` (los ids de arista no llevan timestamp)".
- `02 §10` inv. 16: "El timestamp … entra en el id de la revisión"; `02 §5`:
  `revision-id = H(canonical-bytes(Revision))`.
- Corrección: decir en `§4` que la idempotencia es de nodos/aristas, no de revisiones.

### Respuestas directas

- **Reconstructibilidad (`02` inv. 10, `14 §2`).** `index/graph/` sí: sus datoms
  `{:e :a :v :prov}` se derivan de los nodos `:fact/:triple` y de las aristas
  `binding`/`projection` con su evidencia, una vez que `§3` cambie `edge/statuses`; hoy el
  índice no podría llevar `:status :proposed`/`:tier`. El `facts.log.edn` **no** es
  reconstruible desde el pool (es la intención de escritura); `15 §4` lo llama caché y
  delega el contenido reconstruible a documentos de decisión/vocabulario, pero omite el caso
  de hechos `:proposed` de motor sin documento, que solo se reconstruyen re-ejecutando el
  motor (`14 §2`: "lo proyectado sí (motores)").
- **Anillos (`03 §1`).** Ninguna sección viola los anillos si el I/O de `facts.log.edn` y
  del índice queda en `kimun.host.*`/CLI (ring 3) y el índice se pasa al kernel como valor.
  `§4` exige, sí, una operación nueva en el puerto `Backend` (`kimun.kernel.store`) y un
  cambio en `open` para informar pendientes.
- **Kernel: qué sección exige cambio.** `§1` (node shapes `:triple`/`:proposition`), `§2`
  (`anchor/resolves?`: endurecer "resuelve por existir en el CAS" a "pertenece a un árbol
  `:context`", declarado en `§11`), `§3` (`edge/statuses`), `§4` (`store`+`Backend`). No
  exigen cambio de kernel: `§5`, `§6` (usa `anchor`/`reconcile`), `§7`, `§8`, `§9`.
- **Promesas.** La tabla tiene test para 21 enunciados, pero faltan los de H6 y el de H7 no
  es un test de ruptura; el resto es escribible con `bb test` (los `g/...` como
  `test/kimun/g/*_test.cljc`, ring 4).

## Ambigüedades

- **A1 — "observed"/"confirmed" viven en dos ejes.** `15 §3` define
  `:status :proposed|:observed|:confirmed|:rejected|:retracted` y
  `:tier :observed|:confirmed|:inferred|:statistical`; la promesa de `§10` dice "nada pasa a
  `TRUE` sin actor o motor `:confirmed`". Lectura (a): `:confirmed` es `:tier`. Lectura (b):
  `:confirmed` es `:status` de decisión (pero "motor" nunca es `:confirmed`, pues "la salida
  de un motor es siempre `:proposed`").
- **A2 — "a lo sumo por `W_i`".** `15 §8` prohíbe `p×p` "global" y lo permite "a lo sumo por
  `W_i`". Lectura (a): las matrices densas por `W_i` son siempre legales (choca con `14 §8`
  si el `W_i` es `:corpus`, H3). Lectura (b): "por `W_i`" implica contextos pequeños y
  candidatos acotados, sin materializar denso.
- **A3 — el `:status` de la arista.** `15 §3` pone el estado de decisión en la evidencia de
  `projection`; `02 §3.2` exige ahí el sentido (`sinnvoll|sinnlos|unsinnig`). Lectura (a):
  `§3` reemplaza el sentido en la arista y `V`/`S` solo existen en el índice. Lectura (b):
  `§3` añade claves y el sentido sigue siendo obligatorio en la arista.
- **A4 — hecho sin proyección.** `15 §1` "el hecho es el **estado** de la proposición"
  sugiere que un `:fact/:triple` sin `projection` no es un hecho; `02 §2.1` lo define como
  nodo válido del pool por su forma. Lectura (a): el nodo existe pero no es hecho.
  Lectura (b): no debe poder entrar al pool (H2).

## Veredicto

**ABIERTO.** Bloqueantes:

1. **H4** — `§4`/`§8` definen `facts.log.edn` e `index/graph/` (forma en disco) mientras
   `§11` declara que no toca a su dueño, `10` ("único dueño del formato en disco").
