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
| `:ref-hash` como detector de mutación (estados de anclaje, §6.2) | anchor_test/* (ver §6) |

## §4.1 — stand-off

| promesa | test |
|---|---|
| :span es un nodo ordinario que crea el plan; content-addressed | hardening/span-nodes-are-materialized-by-the-plan |
| capas UAX #29 y direcciones virtuales | standoff_test (hito 6; cache de capas suministrada por el host, `sldb.host.default/layer-cache`) |

## §5 / §5.1 — revisiones y plan

| promesa | test |
|---|---|
| Revision de 8 campos; id = H(los 8); tx = H(plan resuelto) | revision_test/fixture-tx-001…, heads-advance-only-through-a-commit (inv. 17). *(2026-10-06, escala)* el fixture fija dos ids: formato 1 (`:roots`, tree-set, edge-set; motor `legacy.revision`) y formato 2 (`:trees :edges :incidence` raíces de mmap) — `test_util/tx-001-revision-ids` |
| *(2026-10-06, escala)* formato 2 y motor legado dan los mismos árboles, aristas, nodos, incidencia y rechazos | revision_format2_test/format-2-and-legacy-engines-agree-on-random-histories, accessors-answer-from-the-mmaps |
| *(2026-10-06, escala)* mmap: la raíz depende solo del contenido (cualquier orden de inserción/borrado); `:new` = exactamente los nodos nuevos; todo nodo hashea a su id | mmap_test/history-independence, insert-then-remove-…, new-is-exactly-the-nodes-not-reachable-from-the-old-root, new-objects-hash-to-their-ids-and-close-the-root |
| *(2026-10-06, escala)* `:incidence`: forma canónica (vector ≤ 16, `{:set raíz}` por encima) función del conjunto; un concentrador cuesta pocos nodos por arista | revision_format2_test/the-incidence-value-of-a-node-depends-only-on-its-set-of-edges, a-hub-keeps-its-edges-in-a-nested-set-… |
| *(2026-10-06, escala)* `commit!` escribe exactamente los objetos nuevos y nunca lista el backend | revision_format2_test/commit-writes-exactly-the-new-objects-and-never-lists-the-backend |
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

## §6 — identidad, sucesión y estados de anclaje (hito 5a)

| promesa | test |
|---|---|
| §6.1 el re-anclaje se dispara también con un `:add-edge` de `supersedes` nuevo; `old` = `:to`, `new` = `:from`; el par `[old new]` llega al resultado de la transacción | anchor_test/adding-a-supersedes-edge-re-anchors-and-records-the-pair |
| §6.1 el no-op idempotente nunca vuelve a re-anclar | anchor_test/adding-a-supersedes-edge… (bloque `testing`) |
| §6.1 `reference`/`binding` siguen al sucesor en cualquiera de los dos extremos | anchor_test/adding-a-supersedes-edge…, revision_test/the-seven-checks… (vía `:replace`) |
| §6.2 anclaje = las cinco aristas entre capas; `ownership` y `supersedes` no lo son | anchor_test/anchors-are-the-five-cross-layer-types |
| §6.2 `intact` cuando el referente resuelve; un símbolo sin árbol resuelve en el primer slice | anchor_test/a-placed-referent-is-intact |
| §6.2 `orphan` cuando el referente deja de ocupar posición y no hay sucesión | anchor_test/detaching-the-referent-orphans-the-anchor |
| §6.2 el estado de la arista es el peor de sus dos extremos; `superseded` gana a la presencia | anchor_test/detaching-the-referent…, adding-a-supersedes-edge… |
| §6.2 un `:span` hereda el suelo de su hoja; un rango que se pasa del texto no resuelve | anchor_test/a-span-whose-leaf-is-replaced-is-orphan-and-its-leaf-is-superseded, a-span-whose-range-runs-past-its-leaf-is-orphan |
| §6.2 un id que no está en el pool es `orphan` | anchor_test/an-endpoint-that-is-not-in-the-pool-is-orphan |
| §6.2 cadena de sucesión: ambigua desde cualquier paso, o seguida hasta el final | anchor_test/two-successors-make-the-chain-ambiguous, a-chain-of-succession-is-followed-to-the-last-node |
| §6.3 `anchored-in`: posiciones en orden de documento, `:as-from`/`:as-to` por id ascendente, un bucle en los dos vectores | anchor_test/anchored-in-answers-what-is-anchored-in-what, diff-reports-added-removed-and-changed-anchors |
| §6.3 `report` lleva siempre los tres estados y los cinco tipos, con ceros | anchor_test/report-always-carries-the-three-states-and-the-five-types |
| §6.3 `diff`: `:added`, `:removed`, `:changed` con `:before`/`:after` | anchor_test/diff-reports-added-removed-and-changed-anchors |
| §6.3 las consultas son derivadas, puras y ordenadas (inv. 18) | anchor_test/queries-are-pure-derived-and-deterministic |
| §6.3 `:anchor/unknown-revision` y `:anchor/not-an-anchor` | anchor_test/the-two-error-types |
| §6.4 `:fingerprint` externo con la forma `<alg>:<hex>` y el algoritmo del store; `:node/invalid` si no | node_test/invalid-shapes-are-rejected, golden-fixture-covers-every-row-and-freezes-ids · **oracle**: id congelado recomputado en Python |
| inv. 18: el estado de un anclaje es derivado y no se persiste | anchor_test/queries-are-pure-derived-and-deterministic |
| §9 hito 5a: detachar deja `orphan`; registrar `supersedes` deja `superseded` y re-ancla | anchor_test/detaching-the-referent-orphans-the-anchor, adding-a-supersedes-edge-re-anchors-and-records-the-pair |

## §6.5 — reconciliación de `drifted` (hito 5b)

| promesa | test |
|---|---|
| la entrada son los extremos `orphan`, no los ids de arista; los anclajes afectados viajan en `:edges` | reconcile_test/position-names-the-node-that-took-the-path |
| método `:position` con sus dos confianzas (1.0 si el candidato no estaba en el árbol en `:base`, 0.9 si sí) | reconcile_test/position-names-the-node-that-took-the-path, a-candidate-that-was-already-in-the-tree-is-proposed-with-less-confidence |
| método `:fingerprint`: mismo locator, huella distinta, confianza 1.0; gana el ranking | reconcile_test/fingerprint-names-the-same-locator-with-a-different-digest |
| método `:sample`: el mejor Dice, no un Dice cualquiera; y el umbral `:min-confidence` | reconcile_test/sample-names-the-best-dice-match-above-the-threshold |
| un método que no aplica no produce candidato y nunca es un error | reconcile_test/fingerprint-names… (`:position` descartado por kind distinto) |
| `dice` sobre trigramas de grafema en NFC: identidad, disjuntos, dos vacíos, vacío contra no vacío, simetría, textos de menos de tres grafemas, clústeres | reconcile_test/dice-is-a-deterministic-grapheme-trigram-coefficient |
| solo la mejor propuesta por `orphan` salvo `:all?`; el orden es el mismo en los dos casos | reconcile_test/fingerprint-names… (`:all?`), position-names… (una sola) |
| la propuesta vive fuera del pool: calcularla no crea ninguna arista | reconcile_test/a-proposal-is-outside-the-pool-until-an-actor-accepts-it |
| aceptar es una transacción `supersedes` cuya única evidencia es el actor; ni confianza ni método llegan a la arista | reconcile_test/a-proposal-is-outside-the-pool-until-an-actor-accepts-it |
| aceptada, el anclaje pasa a `superseded`, se re-ancla y no queda ningún `orphan` | reconcile_test/a-proposal-is-outside-the-pool-until-an-actor-accepts-it |
| `:base` obligatorio cuando la revisión no tiene exactamente un padre (`:reconcile/base-required`) | reconcile_test/base-is-required-when-the-revision-has-no-single-parent |
| §04 §8 `markdown->update-plan` no emite `:replace` ni `supersedes`; lo que sobrevive a la edición sigue colocado e `intact`; lo sustituido queda `orphan` | drift_test/external-edit-orphans-the-anchor |
| §04 §8 el documento se sigue leyendo tras la reingesta; un root distinto es `:markdown/root-changed` | drift_test/update-plan-keeps-the-document-readable-and-refuses-a-changed-root |
| §9 hito 5b: un `.md` editado fuera del kernel deja `orphan`, la reconciliación lo reclasifica `drifted` nombrando al sustituto, y aceptarla lo deja `superseded` y re-anclado | drift_test/external-edit-orphans-the-anchor |
| inv. 18 (segunda mitad): una propuesta no es objeto hasta que se acepta | reconcile_test/a-proposal-is-outside-the-pool-until-an-actor-accepts-it |

## §8.1 — backend de archivos

| promesa | test |
|---|---|
| objects/<id> con H(fichero) == id (inv. 17) | store_test/every-object-file-hashes-to-its-name |
| store.edn con :format-version :hash-alg :capabilities | hardening/store-failure-modes |
| log.edn una Transaction por línea; línea corrupta ⇒ :store/corrupt-log con número de línea | hardening/store-failure-modes. *(2026-10-06, escala)* lo lanzan `replay`/`replay-open`; `open` de formato 2 no lee el log; `stores verify`/`journal verify` lo informan como `store/corrupt` (`:replay-failed`): cli/stores_test/verify-detects-a-damaged-log, cli/journal_test/verify-detects-a-truncated-log-… |
| heads.edn atómico; CAS obsoleto rechazado sin escribir | store_test/stale-heads-cas-is-rejected-without-writing. *(2026-10-06, escala)* `Backend/commit-tx!` bajo lock exclusivo: con cabezas obsoletas ⇒ `:store/stale` y ni `log.edn` ni `heads.edn` cambian (los objetos del perdedor quedan sin referencia); el store abre, pasa `verify-full` y un reintento funciona: store_test/two-writers-on-the-same-loaded-store-the-loser-leaves-nothing-visible, concurrent-writers-on-the-fs-backend-exactly-one-wins, cli/journal_test/a-losing-writer-is-stale-and-leaves-the-store-readable |
| *(2026-10-06, escala)* ventana de caída: una última línea que las cabezas no registran se ignora al abrir, se informa (`:uncommitted-tail`) y el siguiente commit la descarta; un log que no termina en las cabezas impide escribir | store_test/log-tail-rule, a-writer-dead-between-log-append-and-heads-write-…, the-full-verification-reports-an-uncommitted-tail-and-open-ignores-it, a-log-that-does-not-end-at-the-heads-refuses-further-commits |
| open = replay + comprobación de ids; replay solo con log + objects | store_test/close-and-reopen…, replay-from-log-and-objects-only, reopen-from-a-separate-bb-process. *(2026-10-06, escala)* formato 2: `open` = `store.edn` + `heads.edn` (`{:head :heads-root}`) + Revision cabeza y raíz de cabezas comprobadas por hash y forma, sin replay ni log: store_test/open-reads-only-the-head-revisions-and-never-the-log, open-checks-heads-edn-and-the-head-revisions; el replay con comprobación de ids pasa a `replay-open`/`verify-full`: replay-from-log-and-objects-only, the-full-verification-detects-a-damaged-log-and-heads-that-disagree-with-it; formato 1 sigue abriendo por replay: revision_format2_test/a-format-1-store-opens-commits-and-reopens-through-the-legacy-path |
| verify: objeto corrupto o ausente, nombrado | store_test/corrupting-one-object…, hardening/store-failure-modes (:missing). *(2026-10-06, escala)* `verify` (`stores check`) = hash de todo lo alcanzable + final del log frente a las cabezas; `verify-full` (`stores verify`) = replay + `verify`. Cada clase de objeto dañado (nodo, objeto de árbol, rama y hoja de mmap, las tres raíces, conjunto anidado, Revision antigua, cabeza de otro árbol; alterado, ilegible, borrado) informada y nunca lanzada; la Revision cabeza y la raíz de cabezas las rechaza `open`: cli/diagnostics_test/check-reports-each-kind-of-damaged-object, store_test/a-tampered-object-is-detected-by-the-hash-check |
| algoritmo de hash del descriptor debe coincidir | hardening/store-failure-modes (formato 1 por replay y, *(2026-10-06, escala)*, formato 2 desde las cabezas) |
| cualquier secuencia de planes recarga idéntica | store_test/any-valid-plan-sequence-reloads-identically (*(2026-10-06, escala)* `open`, `replay-open` y el valor en memoria coinciden; `verify` y `verify-full` pasan) |
| *(2026-10-06, escala)* el valor perezoso y el ansioso muestran las mismas vistas; la caché de objetos no oculta un objeto alterado | store_test/the-lazy-and-the-eager-store-show-the-same-views, the-fs-backend-parses-each-object-at-most-once-per-instance, the-object-cache-never-hides-a-tampered-object |
| *(2026-10-06, escala)* `stores upgrade`: formato 1 → 2 conserva cada nodo, arista y árbol, actor/timestamp/engines/ops de cada transacción y la cadena de padres; verifica antes de reemplazar; el original queda como respaldo; idempotente | upgrade_test/a-format-1-history-upgrades-to-format-2-and-keeps-everything, any-format-1-history-upgrades-to-the-same-trees-and-edges, upgrade-refuses-what-it-cannot-do-safely, cli/upgrade_test/a-format-1-store-answers-the-same-after-the-upgrade |
| rebuild-indexes | **deferred** → hito 4+ (índices derivados) |

## Invariantes §10

1 pool_test/tampered…, store_test/close-and-reopen · 2 node_test/same-content-same-id · 3 revision_test/heads-advance-only… · 4 tree_test/generated-trees-are-valid · 5 tree_test/one-node-in-two-trees · 6 edge_test/evidence-requirements · 7 revision_test/replace-keeps-order… · 8, 9 **deferred** (hito 4) · 10 pool_test/pool-is-rebuildable, store_test/replay… · 11 **deferred** (efectos) · 12 plan_test/opaque-replace-only · 13 tree_test/ulid-ids-are-nominal · 14 tree_test/sibling-order… · 15 revision_test/the-seven-checks · 16 edge_test/timestamp…, hardening/timestamp-enters-the-revision-id · 17 store_test/every-object-file… · 18 anchor_test/queries-are-pure-derived-and-deterministic, reconcile_test/a-proposal-is-outside-the-pool-until-an-actor-accepts-it

## §3.1 (revisado 2026-08-29) — árboles de posiciones

| promesa | test |
|---|---|
| un nodo puede ocurrir en varias posiciones de un árbol y en varios árboles; cada posición tiene un padre (inv. 5) | tree_test/one-node-at-several-positions-and-in-several-trees, hardening/tree-boundaries ("same node at two positions") |
| subárboles idénticos comparten objeto (dentro y entre árboles) | tree_test/identical-subtrees-share-objects, one-node-at-several-positions… |
| posiciones por path; `positions`, `node-at`, `paths-of`, `from-objects` reconstruye desde el CAS | tree_test/positions-and-paths, from-objects-rebuilds-a-committed-tree |
| `replace` es por posición (otras ocurrencias intactas); `detach`; `move` con corrección de path | tree_test/replace-keeps-position-and-subtree, detach-and-move-keep-validity |
| ops de propiedad por path en planes (`:parent`, `:at`, `:from/:to`, `detach`); conflictos por path padre | revision_test/the-seven-checks…, heads_test/commit-through-atom-and-conflict-set, hardening/conflict-kinds-removed-and-superseded-target |
| reordenar hermanos cambia el hash sii cambia la secuencia (nodo, hash) | tree_test/reordering-siblings-changes-parent-hash |

## §04 — superficie Markdown (hito 4)

| promesa | test |
|---|---|
| inv. 8: `(cst/text (cst/parse s)) == s` ∀ `s` (`\r\n`, tabs, blancos finales, sin salto final, vacío) | cst_test/lossless-for-any-string, lossless-edge-cases |
| reglas de segmentación §3.1 (fences `~` vs `` ` `` y longitud, quote cortado por blanco, listas por familia de marcador, blanco+indentado, continuación perezosa, html/table hasta blanco) | cst_test/segmentation-rules |
| inv. 9: `(parse (render A)) == A` ∀ `A` canónico (`gen-ast`) | roundtrip_test/parse-render-identity (10×300 en desarrollo, 150 en suite) |
| `render` idempotente | roundtrip_test/render-is-idempotent |
| escapes: todo grafema escapable y todo inicio de línea peligroso vuelven literal (párrafo y heading) | roundtrip_test/escapes-round-trip |
| inline §5: cada regla de `:unparsed`, `snake_case` literal, code span con backticks internos, links con énfasis, `<`/`_`/`!`/`]` | inline_test/profile-rules |
| orden canónico y validez de marcas §4; offsets en grafemas; NFC antes de offsets | inline_test/marks-canonical-order, grapheme-offsets |
| deletreos canónicos (heading, viñetas, numeración desde `:start`, soft breaks, quotes, fences, blancos indentados en items) | roundtrip_test/canonical-spellings |
| fixture dorado `profile.md` ⇄ `profile.ast.edn`, render byte a byte | roundtrip_test/golden-profile |
| fixture `outside-profile.md`: informe congelado, opacos verbatim, estable | plan_test/golden-outside-profile |
| mapeo §8: `markdown->plan` → store → `store->ast` == `parse`; `store->markdown` == `render`; nodos de las formas §8 | plan_test/store-round-trip |
| hojas de texto compartidas entre documentos (marcas en el bloque) | plan_test/text-leaves-are-shared |
| `store->ast` rechaza árboles que no son documentos (`:markdown/not-a-document`) | plan_test/not-a-document |
| informe §9: coverage 1.0 sin opacos; paths completos y grafemas por región | plan_test/coverage, golden-outside-profile |
| `TextSegmenter`: `(apply str (graphemes s)) == s`, clústeres UAX #29 | inline_test/grapheme-offsets (emoji ZWJ, é) |

Bugs de kernel encontrados por el hito 4: el modelo "un padre por nodo por árbol" no podía representar listas (items idénticos) → árboles de posiciones (`atom-decision-trees-are-trees-of-positions-git-like`).

## Pista S0 — CLI `kimun` y distribución (docs/v2/06, 08)

| promesa | test |
|---|---|
| `kimun --version` lee `resources/VERSION`; `version` es alias | cli/main_test/version-matches-resources-VERSION |
| envelope `{:ok :command :store :revision :data :warnings}` / `{:ok false :error :exit}` en json/edn/text | cli/main_test/edn-format-round-trips-the-envelope, text-format-is-human-readable, json-format-renders-namespaced-types-as-strings |
| primer token desconocido ⇒ superficie del evaluador (stub `:eval/not-available`, exit 6 hasta S4) | cli/main_test/unknown-first-token-is-the-evaluator-stub |
| opción desconocida / `--format` inválido / valor ausente ⇒ exit 6 | cli/main_test/unknown-option-is-a-usage-error |
| `stores init` crea `.kimun/{store.edn,objects}` con `:links []`; doble init ⇒ exit 5 | cli/stores_test |
| discovery `--store` > `KIMUN_STORE` > walk-up; sin store ⇒ exit 5 con path | cli/stores_test |
| `stores verify` detecta objeto corrupto ⇒ `:bad`, exit 5 | cli/stores_test |
| salida estable de `version/init/check` | cli/golden_test + `test/fixtures/cli/s0/*.json` |
| anillo `kimun.*` y regla "sin `def` mutable en anillo 0" | lint_test |
| `bb release --local-bb` produce `bin/kimun, bin/kimun.cmd, lib/bb, lib/kimun.jar, VERSION, LICENSE`, tar.gz y `SHA256SUMS` | cli/release_test/local-bb-release-bundle-runs-without-bb-on-path |
| el tarball corre con `PATH=/usr/bin:/bin`; `--version` == in-process; `--` preserva `--help`; `stores init/check` desde el bundle; arranque < 1.5 s | cli/release_test/local-bb-release-bundle-runs-without-bb-on-path |
| plataforma desconocida ⇒ exit 2 | cli/release_test/release-rejects-unknown-platform-and-version-drift |

## Deuda aceptada

- Paridad de host (Node): `task-node-host-parity-for-the-v2-kernel`.
- Cobertura/lint externos: `task-test-coverage-and-lint-tooling-for-babashka`.
- Dos procesos reales escribiendo el mismo directorio: `task-contention-test-for-heads-commit` (en memoria ya cubierto). *(2026-10-06, escala)* el lock de `commit-tx!` lo cubren hilos de una misma JVM (store_test/concurrent-writers-on-the-fs-backend-exactly-one-wins, parity/closed_gaps_test/many-concurrent-writers-leave-a-valid-store) y otro proceso que tiene el lock (`store/busy` sin `--wait`, espera con `--wait`: parity/closed_gaps_test/a-write-while-another-process-holds-the-store-fails-fast, with-wait-a-write-blocks-until-the-lock-is-released-then-succeeds); no dos procesos que confirman a la vez.
- *(2026-10-06, escala)* `:incidence` "nunca de confianza": la recalcula el replay de `verify-full`, pero ningún test altera una `:incidence` dejando el resto válido (alterarla cambia el id de la Revision, que el hash ya detecta).
- *(2026-10-06, escala)* objetos sin referencia (de un escritor perdedor, nodos de raíces de cabezas reemplazadas): ningún comando los informa; GC en el hito 8.
- Monotonicidad de ULID: `task-ulid-monotonicity-and-clock-adapter`.
