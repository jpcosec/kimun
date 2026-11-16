# K2b-0 — qué cambiaría al retirar cada cosa

Sin proponer un diseño: solo consecuencias, cada una con su cita. **[leído]** = visto en el código;
**[inf]** = inferencia mía, sin ejecutar. Base `c8ca2f25`.

## 0. Tres mecanismos del código que deciden las consecuencias

1. **Qué entra en el id de una revisión.** La Revisión es el mapa `{:trees :edges :incidence :parents :tx
   :actor :engines :timestamp}` y `revision-id = H(canonical-bytes Revision)` (`revision.cljc` l. 26–27,
   l. 821–830). Quitar o cambiar una clave, o el contenido de un mmap, cambia el id de **toda** revisión
   que se escriba desde entonces. Las revisiones ya escritas conservan su id (el objeto no cambia).
2. **Qué entra en el id de una arista.** `edge-id = H(canonical-bytes (identity-form edge))` y
   `identity-form` incluye `:type :from :to :evidence …` (`edge.cljc` l. 70–78). Mover o cambiar
   cualquier clave de la evidencia cambia el id de la arista.
3. **Cómo se lee un store existente.** `open` valida los objetos de la cabeza con `revision-object?`,
   que **exige** las claves `:tx :parents :trees :edges` (`store.cljc` l. 366–367, usado en l. 430);
   `empty-store` solo conoce `:format-version` 2, 3 y 4 y para otro valor falla con «unsupported store
   format» (l. 296–298). `verify-full` y `replay-open` **re-aplican cada plan registrado con el motor
   actual** y exigen que el id de transacción y el de revisión coincidan con el log
   (`replay-log`, `store.cljc` l. 326–345: «replayed revision id differs from log»). Es decir: un motor
   que escriba otra forma de Revisión no puede, sin más, reproducir los logs de stores existentes; el
   precedente en el código es el despacho por `:format-version` (formato 1 → `kimun.kernel.legacy.revision`,
   docstring de `revision.cljc`).

Precedentes de evolución del store en el código (hechos, sin recomendar): (a) formato 1→2: reconstruir
con el motor nuevo en un directorio hermano, verificar y renombrar (`kernel/upgrade.cljc`,
`stores.cljc upgrade-store`); (b) formato 3→4: no es reescritura en sitio, reporta `:needs-rebuild` y
exige `include build --rebuild` (`10 §11`, l. 481–483); (c) esquema de rutas 1→2 (C5): en sitio, mismo
formato 4, el esquema vive en `store.edn :route-schema` y la Revisión **no** cambia
(`store.cljc upgrade-routes!` l. 578–634; `stores.cljc upgrade-routes` l. 590–618).

## 1. `bb oracle`

`scripts/canon_oracle.py` (`main`, l. 150–163) recomputa **solo** `node_id(class, kind, content)` de las
9 filas de `test/fixtures/nodes.edn` y las compara con `expected-id`. Corrió hoy en `c8ca2f25`:
`9 rows, 0 mismatches`. **No calcula ids de aristas, de Revisión, de transacción ni de mmap.** Por lo
tanto, retirar aristas o mmap **no mueve el oráculo** mientras la forma canónica de un **nodo** no cambie.
(`12 l. 74` dice «`bb oracle` (ids canónicos) solo puede cambiar dentro de K1»; `Q4` de `plan-2.md` lo
cita como si retirar mmap lo tocara: lo medido es que el oráculo no cubre esas formas. Los ids de aristas
y de Revisión los cubren pruebas Clojure, no el oráculo; no las listé una por una: `grep -rln "edge-id\|revision-id" test`
daría la lista.)

## 2. Retirar una de las tres aristas por documento (a nivel superficie, `surface/docs.cljc`, sin tocar el kernel)

Efecto común, **[leído]**:

- **No cambia el formato del store.** Los tres tipos (`:reference`, `:derived`, `:ownership`…) son
  kernel genérico (`edge.cljc` l. 28–32); el motor aplica los planes que haya. El `:format-version`
  seguiría en 4 y la forma de la Revisión es la misma; lo que cambia es **qué planes se escriben**.
- **Cambia los ids de las revisiones futuras** (el contenido de `:edges` e `:incidence` y el plan
  resuelto cambian → `tx` e id de revisión), no los de las ya escritas.
- **Un store existente seguiría teniendo la arista** en sus documentos viejos. Quitarla de los viejos
  no ocurre solo: `registry-stale` (`docs.cljc` l. 768–772), `binding-ops` (l. 819–821) y `generic-ops`
  (l. 1011–1013) retiran solo aristas cuyo destino es `source-or-file?` (el archivo o la fuente
  inline), **no** las de destino modelo (A1, A2); `untrack-plan` (l. 1175) sí quita las de `owned-edge?`
  (modelo, fuente o archivo).
- **Lectura de stores existentes:** los lectores deberían aceptar documentos con y sin la arista
  (mezcla en el mismo store), o el store se reconstruye (`include build --rebuild`).
  `documents`/`describe` ya son tolerantes a ausencia de A2 (`:superseded`, l. 295) pero **no** a la de A1
  (`:orphan`, l. 294).

Por arista:

| arista | qué lector deja de tener su dato | cambia ids de arista | observaciones [leído] |
|---|---|---|---|
| **A2** `derived`→clase | `describe` l. 274, 295: el estado `:intact`/`:superseded` hoy sale de «la arista `derived` sobreviviente» (el kernel la desactiva al sustituir el nodo, `revision.cljc` l. 493). Sin A2 el kernel no marca nada y `describe` no tendría con qué decidir `:superseded` salvo otro dato | A1 y A3 no cambian | la información «con qué contrato se extrajo» sigue en `:contract` de A3 y `:class` del descriptor (`describe` l. 282, 289); el par engine/version se repite en `:extractor` del `:parse-contract` de A3 (`parse_contract.cljc` l. 60–62, 120). Documentos con `:inline?` añaden una `derived` raíz→source que **no** es esta (`binding-ops` l. 815–817) |
| **A1** `reference`→clase | `describe` l. 270–273 (`model-ids`, `entry`): sin A1 `entry` es nil → `:state :orphan` (l. 294) y `:model`, `:model-version`, `:model-id` se pierden. Tras sucesión de modelo el kernel re-ancla A1 al sucesor (`re-anchor`, l. 479–491); el descriptor del árbol guarda la clase **vieja** | A2 y A3 no cambian | `describe`, `anchor-edge` (sin archivo, l. 131–137) y `untrack` la leen. Con documento sin archivo A1 es además el ancla de la evidencia (`:contract`, `:parse-contract`…) |
| **A3** `reference`→archivo | `describe` l. 275–277, 284–288, 289, 303–306: `:path`, `:fingerprint`, `:parse-contract`, `:contract-id`, `:ancestors`, `:content-hash`; `describe-generic` l. 1021–1042 (única arista de un genérico); `route-coverage` (`store.cljc` l. 636–660): un árbol cacheado cuenta como anclado solo con A3 activa | **sí, el id de A3 cambia** si la evidencia se mueve (§0.2) y con él `:edges`, `:incidence`, el plan y la revisión | contiene la **única copia** de `:parse-contract` y `:ancestors` (`readers.md` §4); el nodo `external` (con locator y fingerprint) queda en el store y es el destino de la arista, no se retira con ella |

Efecto en las lecturas masivas: `edges-by-from` (`docs.cljc` l. 106–110) recorre `:edges` completo, y
cada documento aporta 3 entradas (1 si es genérico). Retirar aristas reduce ese recorrido; **no medí**
la diferencia de tiempo (no hay variante sin cambiar `src/`).

## 3. Retirar `:incidence` de la Revisión

- **Sí cambia el formato del store**: `revision-object?` (`store.cljc` l. 366–367) no pide `:incidence`,
  pero `references` (l. 703) distingue la Revisión formato 2+ por `(and (map? o) (:incidence o) (:tx o))`
  y camina `:edges :incidence :parents`; la forma `{:roots … :tx}` (l. 704) es la de formato 1. Sin
  `:incidence` esa rama no reconoce la Revisión (`:else []` → nada alcanzable) **[leído]**.
- **Sí cambia los ids de revisión** de toda revisión nueva (§0.1) y por tanto no se pueden reproducir los
  logs existentes con el motor nuevo sin despacho por formato (§0.3).
- **No cambia ids de nodo ni de arista** (edge-id no depende de la Revisión).
- **Lecturas que se quedan sin índice** [leído]: `edges-touching` (`revision.cljc` l. 136–147), que
  alimentan `ws-touching` → `re-anchor` (l. 363–368, 479–493) y `docs.cljc edges-from` (l. 112–118,
  usado por `find-document`, l. 342). El docstring de `edges-touching` fija su razón: costar «las aristas
  que tocan al nodo, no el conjunto de aristas» (l. 480–481 «Costs the edges touching `old`»). Sin
  `:incidence` esas lecturas pasan a recorrer `:edges` (`edges-by-from`), o a otra fuente. No medí el
  costo.
- Medido: `:incidence` vivo = 330.462 B a N=500 (52 % de los tres mmap de la Revisión); su hub de la
  clase tiene 2·N ids.

## 4. Retirar `:edges` de la Revisión

- **Sí cambia el formato del store**: `revision-object?` exige `:edges` (l. 367) → un store sin él se
  reporta `:not-a-revision` (l. 430, `checked-head-object`).
- Es la **única copia** de «qué aristas están activas en la revisión R» (`readers.md` §4): lo leen
  `active-edges`, `edge-active?`, `edge-count`, `diff` (`revision.cljc` l. 105–155, 604–642),
  `anchor.cljc active-edges` (l. 43–49), `upgrade.cljc` l. 49, `route-coverage`,
  `models/store.cljc history` l. 181, `semantic.cljc` l. 199, `edges.cljc` l. 227 y `docs.cljc
  edges-by-from`. `:incidence` se **deriva** de `:edges` (docstring `revision.cljc` l. 24; `verify-full`
  lo recomputa, `store.cljc` l. 945), así que retirar `:edges` sin reemplazo deja a `:incidence` sin
  fuente verificable.
- Los ids de arista no cambian; los de revisión sí (§0.1).

## 5. Retirar `:trees` (id de árbol → `[descriptor merkle-root]`) o `:heads-root`

El texto de K2b dice «índice mmap ordenado por hashes internos» sin nombrarlo (`readers.md` §0).
Solo para que el dueño lo vea completo:

- `:trees` es **la única copia** del registro de documentos por revisión (`readers.md` §3); `reachable`
  lo sigue como `:trees` (`store.cljc` l. 703) y de él cuelgan los descriptores. Retirarlo cambia el
  formato y el id de revisión y deja sin enumeración a `tree-ids`, `tree-descriptors`, `tree-at`,
  `trees-named`, `diff` (`revision.cljc` l. 157–263, 582–642) y a todo lo que lista documentos
  (`docs.cljc`, `journal.cljc`, `cli/commands/docs.cljc`, `cli/commands/edges.cljc`).
- `:heads-root` vive en `heads.edn` y no es de la Revisión: `open-from-heads` (`store.cljc` l. 424–494) y
  `commit!` (l. 536–) lo usan para el escritor perdedor por árbol. No entra en el id de revisión.
  Retirarlo cambia `heads.edn` y el protocolo de commit, no los ids.

## 6. Retirar las tres aristas a la vez a nivel de superficie, y dejar `:edges`/`:incidence` como están

Combinación de §2 para A1, A2 y A3: mismo formato (4), sin bump; ids de revisión nuevos; `describe`
pierde `model-ids`, `extracted`, `ext-edge` y las cuatro marcas de A3 (`:contract-id`, `:ancestors`,
`:parse-contract`, `:content-hash`): esa información tendría que estar en otro objeto o recomputarse;
no hay en el código hoy otro objeto por documento que guarde `:parse-contract` ni `:ancestors`
(`readers.md` §4). `route-coverage` pierde el criterio «arista `:reference` a `external`». Ahorro de
bytes medido a N=500 (S-bulk): aristas 617.500 B + el mmap atribuible a ellas (`:edges` +
`:incidence` = 467.362 B) = **1.084.862 B (54,2 % de los objetos)**, con la salvedad de que en S-commits
(un commit por documento) el mmap de la historia es ×15 mayor y no sale de ahí al retirar aristas
nuevas (los nodos de las revisiones viejas siguen alcanzables por `:parents`; `store.cljc reachable`
l. 703).

## 7. Qué dice cada precedente sobre el costo del cambio

| opción | ¿cambia `:format-version`? | ¿id de revisión? | ¿id de arista? | ¿`bb oracle`? | ¿lee stores existentes tal cual? |
|---|---|---|---|---|---|
| retirar A1/A2/A3 (superficie) | no | solo revisiones nuevas | A3 sí (si mueve evidencia); A1, A2 no | no | sí si los lectores aceptan ambos; si no, `include build --rebuild` (precedente 3→4) |
| retirar `:incidence` | sí (forma de la Revisión) | sí, las nuevas | no | no | no sin despacho por formato (`replay-log`) |
| retirar `:edges` | sí | sí, las nuevas | no | no | no sin despacho por formato |
| retirar `:trees` | sí | sí, las nuevas | no | no | no sin despacho por formato |
| retirar `:heads-root` | no (`heads.edn`, no la Revisión), pero cambia el protocolo de commit | no | no | no | `heads.edn` de stores existentes se leería con la forma vieja |
