# K2b-0 — inventario de lectores

Base `c8ca2f25`, worktree `/tmp/kimun-k2b0-wt`. Solo lectura de `src/`; nada cambiado. Las líneas
son de ese commit. Todos los `grep` son sobre `src --include=*.cljc` y se excluyó
`src/kimun/kernel/legacy` (formato 1, no se escribe).

Convención: **[leído]** = lo vi en el código; **[inf]** = inferencia mía, sin prueba ejecutada.

## 0. Qué es «el índice mmap» y «las tres aristas» en el código de hoy

La Revisión (`kernel/revision.cljc` l. 12–24) apunta a **tres** mmap más una cuarta estructura mmap
fuera de la Revisión:

| mmap | clave → valor | dónde |
|---|---|---|
| `:trees` | id de árbol (ULID, ruta = `canon/digest` de la clave) → `[descriptor-id merkle-root]` | `revision.cljc` l. 20, 815–816 |
| `:edges` | id de arista (64 hex) → `true` (aristas activas, no `:ownership`) | l. 20, 817–818 |
| `:incidence` | id de nodo → vector de ids de arista (≤ `hub-threshold` = 16) o `{:set <raíz mmap>}` | l. 21–24, 730–768, 819–820 |
| `:heads-root` (en `heads.edn`, no en la Revisión) | id de árbol → última revisión que lo tocó | `kernel/heads.cljc`, `store.cljc` l. 205 |

`14 §20` y `12 §1` K2b dicen «índice `mmap` ordenado por hashes internos / por ids internos» sin
decir cuál de los tres. **El texto no distingue**; por eso el inventario cubre los cuatro. Las claves
64-hex (`:edges`, `:incidence`) son su propio camino; `:trees` y `:heads-root` usan ULIDs
(`mmap.cljc` l. 100–104 `path`). Esto es ambigüedad del spec: lo anoto como hecho para Q4.

Las tres aristas por documento (escritas por `surface/docs.cljc`) para un documento tipado con
archivo, en la forma vigente:

| arista | sitio de escritura | evidencia |
|---|---|---|
| **A1** `reference` raíz→descriptor de clase | `registry` l. 752; `binding-ops` l. 811–812 | `{:ref-hash <clase> :actor}` (por archivo: sin `:contract`; `binding-ops` l. 812 `dissoc` las marcas si hay archivo) |
| **A2** `derived` raíz→descriptor de clase | `registry` l. 753–754; `binding-ops` l. 813–814 | `{:ref-hash <clase> :engine "kimun.surface.models" :version "1"}` |
| **A3** `reference` raíz→nodo `external` (archivo) | `registry` l. 747–751; `binding-ops` l. 809–810 | `{:ref-hash <ext> :actor :contract :parse-contract [:ancestors]}` y en `binding-ops` además `:content-hash` |

Un documento **genérico** (K3, sin modelo) solo tiene A3 (`generic-ops` l. 1005–1008). Un documento
con `:inline?` añade una cuarta `derived` raíz→source sign (`binding-ops` l. 816–817, no se escribe
por defecto).

## 1. Aristas por documento: quién lee cada una

Todas las lecturas pasan por un único punto: `edges-by-from` (`docs.cljc` l. 106–110; recorre
`(:edges store)`, es decir **el mmap `:edges` entero**) o `edges-from` (l. 112–118; usa
`revision/edges-touching`, es decir **`:incidence`**). Luego `describe` (l. 260) y `describe-generic`
(l. 1021) las consumen. `grep` usado:

```
grep -rn ":derived" src --include=*.cljc | grep -v kernel/legacy
grep -rn ":reference" src --include=*.cljc | grep -v kernel/legacy
grep -rn "(:edges " src --include=*.cljc | grep -v "kernel/legacy\|kernel/revision\|kernel/anchor"
grep -n "anchor-edge\|ext-edge\|by-from\|extracted" src/kimun/surface/docs.cljc
```

### A1 — `reference` raíz→clase

| lector | para qué | ¿recalculable? |
|---|---|---|
| `docs.cljc describe` l. 270–273 (`model-ids`, `entry`, `any`) | **encontrar la clase vigente del documento**: `entry` = el modelo actual cuyo id es el destino de una arista a modelo. Tras una sucesión de modelo el kernel re-ancla A1 al sucesor (`revision.cljc re-anchor` l. 479–493, `:reference` y `:binding` reciben una arista sucesora). Sin A1 `entry` es nil → `:state :orphan` (l. 294) y `:model` cae a `any` (l. 281) | **Parcial [inf]**. El descriptor del árbol guarda `:class` (l. 288, `(get-in d [:class])`) pero es el id **de cuando se extrajo**, no el sucesor; el sucesor se obtiene siguiendo `supersedes` (que vive en el kernel, no por documento) o recomputando por nombre. Hoy lo materializa el kernel **por documento** |
| `anchor-edge` l. 131–137 (cuando no hay archivo) | portar `:contract`/`:ancestors`/`:parse-contract`/`:content-hash` de un documento sin archivo (`:inline`) | n/a para documentos con archivo: A1 no lleva marcas (`binding-ops` l. 812) |
| `registry-stale` l. 768–772, `binding-ops` l. 819–821, `generic-ops` l. 1011–1013 | decidir qué aristas del documento anterior se quitan al reescribir (`source-or-file?`) | A1 no entra en `stale` (su destino es modelo, no `source-or-file?`); se recalcula con `wanted` |
| `untrack-plan` l. 1157–1177 y `owned-edge?` l. 171–176 | al hacer `docs untrack` quita todas las aristas de `(:edges doc)` cuyo destino es modelo / fuente / archivo | se reconstruye de `describe`; no es información propia |
| `kernel anchor.cljc` `states`/`anchored-in`/`report`/`diff` (l. 111–186) y `reconcile/orphans` (l. 50–60) | tratan toda arista `:reference` como **ancla** y le calculan estado (`intact`/`superseded`/`orphan`) | **[leído]**: A1, A2 y A3 entran en `anchors-of` (l. 119–120, `anchor-types` l. 16). Fuera de `src/` **ningún llamador** de `anchor/states`, `anchor/report`, `anchored-in` ni `reconcile/orphans` salvo `surface/edges.cljc` l. 224 (`anchor/state` de **una** arista semántica). Sí los usan tests (`test/kimun/kernel/anchor_test.cljc`, `reconcile_test.cljc`) |
| `kernel/revision re-anchor` l. 479–493 | **escritor**, no lector: usa `:incidence` (`ws-touching` l. 363–368) del nodo **clase** viejo para re-anclar A1 y desactivar A2 | **Aquí la incidencia del descriptor de clase es un hub** (ver `measurement.md`: 1 hub de 1000 ids a N=500) |

### A2 — `derived` raíz→clase

| lector | para qué | ¿recalculable? |
|---|---|---|
| `docs.cljc describe` l. 274 (`extracted`) y l. 295 | `:state`: `:superseded` si el id de la clase vigente **no** está entre los destinos de aristas `derived` activas. Es la **única** lectura de A2 en `surface/` | **Equivalente por otra vía [inf]**: `:contract` de la evidencia de A3 (id del descriptor con que se extrajo) comparado con el id de `entry`. El código ya lo hace para `chain-moved?` con `:ancestors` (l. 296) y `payload-of` lee `:contract-id` (l. 282). No lo verifiqué ejecutando |
| `parse_contract.cljc` l. 60–62 `extractor` | **constante** `["kimun.surface.models" "1"]` documentada como «la que registra la arista `derived`». Va en `:components` del parse contract (l. 120 `:extractor extractor`) | **Ya duplicada**: el par engine/version de A2 es el mismo que `:extractor` de `:parse-contract` en la evidencia de A3 |
| `kernel/revision.cljc` l. 493 | **kernel**: al reemplazar un nodo, `:derived` que lo apunta se desactiva (02 §6.1). Es la semántica de A2: la invalidación la hace el kernel, no la superficie | semántica de kernel, no de `docs` |
| `kernel/edge.cljc` l. 28, 32 | evidencia obligatoria de `:derived`: `#{:ref-hash :engine :version}` | n/a |
| `anchor.cljc` | como A1 (ancla) | ver A1 |

### A3 — `reference` raíz→archivo (anclaje externo)

Es la que **más lectores** tiene y la única con información que no está en otro lado.

| lector | para qué | ¿recalculable? |
|---|---|---|
| `docs.cljc describe` l. 275–277 (`ext-edge`, `ext`, `path`), l. 303–305 | **ruta del archivo** (`:path`), `:fingerprint`, `:qualified`, `source-of` (l. 304–306) | el nodo `external` **sí** está en el store (A3 lo apunta); lo que A3 aporta es **el vínculo raíz→archivo**. La ruta también está en la entrada de ruta (`:routes`, `route-entry`) y en el nodo `external` [leído, `registry` l. 758–761] → el vínculo **sí** es recalculable de ahí **[inf]** |
| `docs.cljc anchor-edge` l. 131–137 y `describe` l. 282–288 | **evidencia**: `:contract` (clase con que se extrajo), `:ancestors` (M1g), `:parse-contract` (K6; l. 284), `:content-hash` (l. 286–287, con respaldo en el descriptor `d`), `:field-hashes` (l. 288, hoy no se escribe: «readers recompute», l. 804–806) | **`:contract`**: también en el descriptor del árbol como `:class` (l. 289; `(or (get-in d [:class]) …)`), pero solo cuando el documento se registró con `registry` (formato 4 por referencia); `binding-ops` (v1/inline) lo guarda **solo** aquí. **`:ancestors`**: **solo aquí** (no en el descriptor; `:units`/`:marks` sí están en el descriptor, `describe` l. 290–291, `ancestors` no) **[leído]**. **`:parse-contract`**: **solo aquí** (digest + componentes; lo lee `diagnostics.cljc` l. 28, 109–116 y `describe-generic` l. 1037). |
| `describe-generic` l. 1021–1042 | documentos genéricos: `:path`, `:fingerprint`, `:parse-contract` | `:parse-contract` solo aquí |
| `kernel/store.cljc route-coverage` l. 636–660 (l. 650) | cobertura de rutas: un árbol `:cached?` cuenta como anclado solo si hay arista `:reference` activa de su raíz a un nodo `external` | recalculable desde la entrada de ruta (`:routes`) **[inf]** (la ruta ya nombra `:tree-id`) |
| `registry-stale` l. 768–772 / `generic-ops` l. 1011–1013 | al mover/reescribir se quita la A3 vieja si ya no se quiere | se deriva de `wanted` |
| `untrack-plan` l. 1175 | quitarla al hacer `docs untrack` | idem |
| `anchor.cljc` | como A1 (ancla); además es la ancla que pierde su `:external` en `placements` | ver A1 |

**Resumen de unicidad:** lo que **solo** vive en A3 es el contenido de la evidencia: `:parse-contract`
(K6, `M1h` v2 añade la huella del selector), `:ancestors` (`M1g`), `:content-hash` de documentos no
cacheados. Todo lo demás de A1–A3 (el vínculo y los ids) aparece en el descriptor del árbol
(`:class`, `:content-hash`), en el nodo `external` o en la entrada de ruta.

## 2. mmap `:edges` / `:incidence`: quién los lee

`grep` usado:

```
grep -rn "incidence" src --include=*.cljc | grep -v "kernel/legacy\|kernel/mmap"
grep -rn "active-edges\|edges-touching\|edge-count\|edge-active?" src --include=*.cljc | grep -v legacy
grep -rn "(:edges store)\|(:edges s)" src --include=*.cljc | grep -v legacy
```

### `:edges` (conjunto de aristas activas)

| lector | para qué |
|---|---|
| `revision.cljc active-edges` l. 105–111, `edge-active?` l. 113–121, `edge-count` l. 149–155, `diff` l. 604–642 (`mmap/diff :edges`) | API del kernel: enumeración y diff de aristas entre revisiones |
| `docs.cljc edges-by-from` l. 106–110 (vía `(:edges store)` perezoso) | **construye el mapa raíz→aristas de todos los documentos** (`documents`, l. 311; `generic-documents`, l. 1047). Es el lector dominante de `:edges` para documentos |
| `kernel/store.cljc route-coverage` l. 653 | recorre aristas activas para anclas externas |
| `kernel/anchor.cljc active-edges` l. 43–49 → `context` | estado de anclas por revisión |
| `kernel/upgrade.cljc` l. 49 | `format-2-head-state`: comparación al subir de formato |
| `surface/models/store.cljc history` l. 181 y `surface/semantic.cljc` l. 199, `surface/edges.cljc` l. 227 | enumeran todas las aristas para encontrar `supersedes` y aristas semánticas **(no las del documento)** pero **iteran todas, incluidas las tres por documento** |
| `revision.cljc lazy-store` l. 218–260 | vista `:edges` del store abierto (perezosa, `delay`) |
| `kernel/store.cljc reachable` l. 703 | alcanzabilidad: la raíz `:edges` de la Revisión se sigue como `:obj` (y las **aristas** como `:soft`, l. 709) |
| `verify`/`verify-full` (`store.cljc` l. 872, 942) | recalculan la incidencia desde `:edges` (docstring l. 945) |

### `:incidence`

| lector | para qué |
|---|---|
| `revision.cljc edges-touching` l. 136–147 | **único lector**: una búsqueda por nodo |
| llamadores de `edges-touching`: `ws-touching` (`revision.cljc` l. 363–368, usado por `re-anchor` l. 479–493) y `docs.cljc edges-from` l. 112–118 | re-anclar al cambiar de modelo (escritura); leer las aristas de un puñado de raíces (`docs show`/`find-document` por nombre, l. 342) sin cargar `:edges` entero |
| `store.cljc reachable` l. 703, `verify-full` | alcanzabilidad y recomputación («never trusted by verification», `revision.cljc` l. 24) |

`:incidence` es **derivable de `:edges`** (docstring `revision.cljc` l. 24: «derived from :edges,
deterministic, and never trusted by verification (a full replay recomputes it)»). La razón de que
exista es costo: `re-anchor` y las lecturas por raíz cuestan «las aristas que tocan al nodo», no el
conjunto entero (l. 480–481).

## 3. mmap `:trees` y `:heads-root`

Para completar el inventario (son parte de «el mmap» pero **no** del texto «las tres aristas»):

- `:trees` lo leen `tree-ids`/`tree-count`/`tree-entry`/`tree-descriptors`/`tree-at`/`trees-named`
  (`revision.cljc` l. 157–263) y de ahí todo lo que enumera documentos: `docs.cljc` 11 usos de
  `revision/tree-*`, `kernel/store.cljc` 31 menciones (incluye `heads-record`, `reachable`),
  `journal.cljc`, `cli/commands/docs.cljc`, `cli/commands/edges.cljc`, `upgrade.cljc`, `anchor.cljc`.
  Es **el registro de qué árboles (documentos) existen y su Merkle root**: no hay otra copia en el
  store (el descriptor está en el CAS, pero alcanzable **solo** desde este mapa). Es la «única
  copia» del registro de documentos. [leído]
- `:heads-root` lo leen/escriben `heads.cljc`, `store.cljc open-from-heads` (l. 424–494) y
  `commit!` (l. 536–): detección de escritor perdedor por árbol (`02 §5`). Recalculable
  solo reproduciendo el log. [inf]

## 4. «Redundante de verdad» frente a «parece redundante pero es la única copia»

| pieza | veredicto | por qué [leído] |
|---|---|---|
| `:ref-hash` de A1, A2 y A3 | **redundante de verdad** | kernel exige `:ref-hash` (`edge.cljc` l. 28–32) y el kernel lo fija igual a `:to` en el re-anclaje (`revision.cljc` l. 479–489); `resolve-edge` l. 470–477 |
| `:engine "kimun.surface.models"` `:version "1"` de A2 | **redundante de verdad** | constante; la misma en `parse-contract/extractor` (l. 60–62), que va en el `:parse-contract` de A3 |
| existencia de A2 como señal de `:intact`/`:superseded` | **parece redundante, pero no hay copia idéntica** | la información («con qué contrato se extrajo») sí está en `:contract` de A3 y en `:class` del descriptor, pero A2 es lo que el **kernel** invalida solo (re-anchor l. 493). Cambiar de A2 a comparar ids es un cambio de **semántica de superficie** (lo calcula `describe`, no el kernel); lo noto, no lo propongo |
| existencia de A1 como «clase vigente» | **única copia del sucesor** | tras sucesión el kernel mueve A1 al nuevo descriptor; ningún otro objeto por documento dice «clase vigente». El descriptor del árbol conserva la clase **vieja** |
| `:contract` en A3 | **también en el descriptor** (`:class`) para documentos registrados con `registry`; **única copia** en `binding-ops` (v1) | `describe` l. 289 usa los dos |
| `:ancestors` en A3 | **única copia** | M1g/M1h (`registry` l. 750–751, `binding-ops` l. 807–808); `describe` l. 283 |
| `:parse-contract` en A3 | **única copia** | K6; `diagnostics.cljc` |
| `:content-hash` en A3 | **también en el descriptor** (formato 4, `describe` l. 286–287) | solo `binding-ops` (v1 sin caché) lo deja en la arista |
| vínculo raíz→archivo (A3 como arista) | **recalculable** de nodo `external` + entrada de ruta | pero lo lee `route-coverage` (kernel) y `describe`; ver `changes.md` |
| `:incidence` | **redundante de verdad** como contenido, **no** como costo | derivable de `:edges` (docstring l. 24) |
| `:edges` | **única copia** de «qué aristas están activas en la revisión R» | la Revisión no tiene otra lista; el log guarda planes (K2a los sacó del CAS) |
| `:trees` | **única copia** del registro de documentos por revisión | ver §3 |
