# Promesas de la spec → tests que las prueban (primer slice)

Trazabilidad entre cada enunciado normativo de `docs/v2/02-sustrato-computacional.md`
(hitos 0–3) y el test que **fallaría si la promesa se rompiera**. Fuente: dos lanes
tester de contexto fresco (`runs/subagents/20260829-174447-…-testing/lane-{1,2}.md`)
más los tests añadidos en `test/sldb/kernel/hardening_test.cljc`. Un test que compara
la función consigo misma no cuenta. Estado tras el cierre: **0 promesas del primer
slice sin test**; las de hitos 4+ figuran como *diferidas* con su drawer.

Leyenda: `ok` probado · `oracle` probado además por una implementación independiente ·
`deferred` fuera del primer slice (drawer task indicada).

## §2 / §2.1 — nodos y contenido canónico

| promesa | test |
|---|---|
| id(nodo) = H(canonical-bytes {:class :kind :content}) | node_test/golden-fixture…, canon_test/digest-… · **oracle**: `scripts/canon_oracle.py` (Python, 9/9) |
| class y kind entran al hash; address no | node_test/class-and-kind-enter-the-id, node_test/address-is-derived |
| 9 filas class/kind con sus formas | node_test/golden-fixture (una por fila), node_test/invalid-shapes-are-rejected, hardening/node-extra-keys-and-literals |
| literales de :proposition/:triple solo atómicos | hardening/node-extra-keys-and-literals |
| NFC antes de imprimir; NFD y NFC ⇒ mismo nodo; el nodo se guarda normalizado | canon_test/nfc-equivalence, node_test/nfc-equivalent…, hardening/canon-unicode-beyond-latin (Hangul, árabe, emoji) |
| claves de mapa y sets sin orden; vectores con orden; un espacio | canon_test/printed-form, map-key-order-insensitive, set-order-insensitive, vector-order-sensitive |
| floats/ratios/#inst/#uuid/chars rechazados (también anidados) | canon_test/rejects-non-admitted, hardening/node-extra-keys-and-literals |
| enteros sin `+` ni ceros; keywords con caracteres especiales | hardening/canon-unicode-beyond-latin |
| normalize idempotente y estable al hash | canon_test/normalize-is-idempotent-and-hash-stable |
| provenance/timestamp/evidencia no entran al nodo | por construcción (`identity-form` solo tiene class/kind/content) + node_test/every-generated-node… |
| pool: put idempotente; nodo manipulado rechazado (inv. 1); reconstruible (inv. 10) | pool_test/* |
| M y G solo formas de datos en 0–3 | node_test (formas) — la ausencia de evaluación es de diseño; hito 7 |

## §3 / §3.1 / §3.2 — árboles, objetos de árbol, aristas

| promesa | test |
|---|---|
| id de árbol nominal ULID; descriptor content-addressed | tree_test/ulid-ids-are-nominal, descriptor-is-a-cas-object |
| objeto de árbol {:node :children [[c hash]…]}; merkle-root | tree_test/golden-fixture-trees-edn |
| orden de hermanos entra al hash (inv. 14) | tree_test/sibling-order-enters-the-hash, reordering-siblings-changes-parent-hash |
| dirty = nodo + ancestros; commit recomputa solo eso; compartición estructural | tree_test/dirty-set-is-exactly-the-path-to-root, changing-one-leaf-changes-only-its-path; hardening/tree-boundaries (200 niveles) |
| un padre por árbol; nodo en N árboles con hashes independientes (inv. 5) | tree_test/one-node-in-two-trees…; hardening/replace-in-one-tree-leaves-other-trees-untouched |
| árbol válido: sin ciclos, orden denso, sin duplicados | tree_test/remove-and-move-keep-validity, generated-trees-are-valid…; hardening/tree-boundaries (borde de orden, hijo duplicado, reemplazo de raíz, árbol de un nodo) |
| Edge/Evidence: campos obligatorios por tipo; exactamente un origen; status válido | edge_test/evidence-requirements, ownership-and-non-ownership-shapes |
| timestamp fuera del id (inv. 16); mismo origen ⇒ misma arista; orígenes distintos ⇒ dos | edge_test/timestamp-is-not-part-of-an-edge…, revision_test/adding-an-existing-edge-is-a-no-op |
| claves extra de evidencia entran al id; origen string vacío admitido, nil no | hardening/edge-evidence-extra-keys-enter-the-id, edge-empty-origin-is-still-a-string |
| un binding por W_i; contextos distintos ⇒ aristas distintas | hardening/multi-context-bindings-are-distinct-edges |
| :ref-hash como detector de mutación | **deferred** → `task-milestone-5-anchor-states-and-drifted-reconciliation` |

## §4.1 — stand-off

| promesa | test |
|---|---|
| :span es un nodo ordinario que crea el plan; content-addressed | hardening/span-nodes-are-materialized-by-the-plan |
| capas UAX #29 y direcciones virtuales | **deferred** → hito 4/5 (drawer: milestone-4, milestone-5) |

## §5 / §5.1 — revisiones y plan

| promesa | test |
|---|---|
| Revision de 8 campos; id = H(los 8); tx = H(plan resuelto) | revision_test/fixture-tx-001…, heads-advance-only-through-a-commit (inv. 17) |
| timestamp formato fijo y entra al id de revisión | plan_test/schema…, hardening/timestamp-enters-the-revision-id |
| :engines solo de esta transacción | revision_test/fixture-tx-001 (`{}` sin herencia) |
| seis ops; ops desconocidas rechazadas | plan_test/schema-and-pure-data-check-7 |
| check 1 base-cas (árboles tocados) | revision_test/the-seven-checks…, heads_test/commit-through-atom-and-conflict-set |
| check 2 ids-exist (nodo, árbol, alias desconocido, alias duplicado, alias antes de declarar, remove-edge de ownership por id) | revision_test/the-seven-checks…, hardening/alias-misuse-is-rejected, ownership-edges-are-not-removed-by-id |
| check 3 tree-integrity (raíz movida, segundo padre, ciclo) | revision_test/the-seven-checks…, hardening/move-to-own-descendant… |
| check 4 evidence-ref-hash (estado resuelto) | revision_test/the-seven-checks… |
| check 5 capability (:all, entrada por árbol, actor ausente) | plan_test/capabilities-check-5, revision_test/the-seven-checks… |
| check 6 opaque-replace-only | plan_test/opaque-replace-only-check-6 |
| check 7 pure-data (funciones, floats, timestamp) | plan_test/schema…, revision_test/the-seven-checks… |
| todo-o-nada; el store de entrada no muta (inv. 15) | revision_test/apply-is-deterministic-and-all-or-nothing, the-seven-checks (sin cambio de estado) |
| plan vacío ⇒ revisión no-op válida | hardening/empty-plan-is-a-valid-no-op-revision |
| :replace: mismo :order, subárbol heredado, supersedes con actor del plan | revision_test/replace-keeps-order…, tree_test/replace-keeps-order-and-subtree |
| :replace re-ancla reference/binding en cualquier extremo; semantic/projection quedan; derived se invalida | revision_test/replace-keeps-order…, hardening/semantic-and-projection-stay-derived-is-invalidated-on-replace |
| :replace en un árbol no toca otros árboles del nodo | hardening/replace-in-one-tree-leaves-other-trees-untouched |
| cadena X''→X'→X, re-anclaje paso a paso | hardening/chained-replace-follows-step-by-step |
| :move marca dirty (cambia el root) | hardening/move-marks-dirty-and-changes-the-root |
| heads avanzan solo para árboles tocados | hardening/heads-advance-only-for-touched-trees |
| determinismo; secuencias encadenan revisiones | revision_test/generated-plans-apply-deterministically, generated-plan-sequences-chain-revisions |

## §5.2 — ConflictSet, rebase, diff

| promesa | test |
|---|---|
| :same-parent-edit | heads_test/commit-through-atom-and-conflict-set, revision_test/fixture-tx-002-conflict |
| :superseded-target y :removed-target (superseded prevalece) | hardening/conflict-kinds-removed-and-superseded-target |
| :remove-edge de arista ya quitada por head ⇒ :removed-target | hardening/remove-edge-already-removed-by-head-conflicts |
| rebase automático en árboles disjuntos; edge-set por unión | revision_test/fixture-tx-002-conflict, hardening/rebase-merges-edge-sets-by-union |
| diff {:trees {:added :removed :moved} :edges :superseded} | revision_test/replace-keeps-order… (diff) |
| CAS por entrada; commit! con reintentos y plan como función del store | heads_test/cas-per-entry, hardening/concurrent-commits-on-disjoint-trees-all-land |

## §8.1 — backend de archivos

| promesa | test |
|---|---|
| objects/<id> con H(fichero) == id (inv. 17) | store_test/every-object-file-hashes-to-its-name |
| store.edn con :format-version :hash-alg :capabilities | hardening/store-failure-modes |
| log.edn una Transaction por línea; línea corrupta ⇒ :store/corrupt-log con número de línea | hardening/store-failure-modes |
| heads.edn atómico; CAS obsoleto rechazado sin escribir | store_test/stale-heads-cas-is-rejected-without-writing |
| open = replay + comprobación de ids; replay solo con log + objects | store_test/close-and-reopen…, replay-from-log-and-objects-only, reopen-from-a-separate-bb-process |
| verify: objeto corrupto o ausente, nombrado | store_test/corrupting-one-object…, hardening/store-failure-modes (:missing) |
| algoritmo de hash del descriptor debe coincidir | hardening/store-failure-modes |
| cualquier secuencia de planes recarga idéntica | store_test/any-valid-plan-sequence-reloads-identically |
| rebuild-indexes | **deferred** → hito 4+ (índices derivados) |

## Invariantes §10

1 pool_test/tampered…, store_test/close-and-reopen · 2 node_test/same-content-same-id · 3 revision_test/heads-advance-only… · 4 tree_test/generated-trees-are-valid · 5 tree_test/one-node-in-two-trees · 6 edge_test/evidence-requirements · 7 revision_test/replace-keeps-order… · 8, 9 **deferred** (hito 4) · 10 pool_test/pool-is-rebuildable, store_test/replay… · 11 **deferred** (efectos) · 12 plan_test/opaque-replace-only · 13 tree_test/ulid-ids-are-nominal · 14 tree_test/sibling-order… · 15 revision_test/the-seven-checks · 16 edge_test/timestamp…, hardening/timestamp-enters-the-revision-id · 17 store_test/every-object-file…

## Deuda aceptada

- Paridad de host (Node): `task-node-host-parity-for-the-v2-kernel`.
- Cobertura/lint externos: `task-test-coverage-and-lint-tooling-for-babashka`.
- Dos procesos reales escribiendo el mismo directorio: `task-contention-test-for-heads-commit` (en memoria ya cubierto).
- Monotonicidad de ULID: `task-ulid-monotonicity-and-clock-adapter`.
