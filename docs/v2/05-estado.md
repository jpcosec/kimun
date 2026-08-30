# SLDB v2 — Estado del kernel (2026-08-30)

> Punto de entrada para quien retoma el trabajo: qué existe, cómo se usa, qué se decidió y
> qué sigue. Se actualiza al cerrar cada hito. Autoridad: `01` (por qué) > `02` (qué
> construye) > `03` (cómo se escribe) > `04` (superficie Markdown) > este documento.

## 1. Qué hay

Un **motor de versionado content-addressed sobre lenguaje estructurado**, en Clojure
`.cljc` puro, que corre en Babashka; y una primera superficie de entrada (Markdown).
Piénsalo como los objetos de Git (blobs, trees, commits, object store) más `git add`
para Markdown — todavía sin línea de comandos.

| anillo | namespaces | qué hacen |
|---|---|---|
| 0 kernel | `sldb.kernel.{ports,err,canon,node,pool,edge,tree,plan,revision,heads,store,anchor,reconcile}` | puertos del host; errores tipados; bytes canónicos + hash; nodos S/M/G; pool; aristas con evidencia; árboles de **posiciones** con Merkle perezoso; `TransactionPlan` con 7 chequeos; revisiones inmutables, sucesión, ConflictSet/rebase, diff; heads con CAS; puerto `Backend` + open/replay/verify; **estados de anclaje** derivados y sus consultas; **reconciliación** de `orphan` en propuestas fuera del pool |
| 1 host | `sldb.host.{default,hash,text,ulid,fs-store}` | SHA-256, NFC + grafemas (UAX #29), ULID, backend de archivos (`objects/`, `log.edn`, `heads.edn`, `store.edn`) |
| 2 superficie | `sldb.surface.markdown.{cst,inline,ast,render,plan,report}` | Markdown ⇄ AST neutro ⇄ árbol de documento en el pool; render canónico; informe de direccionabilidad; **reingesta** de un fichero editado fuera del kernel |

2.544 líneas de `src`, 1.922 de `test`. Hitos cerrados del roadmap (`02 §9`): **0, 1, 2, 3, 4, 5a, 5b**.

## 2. Cómo se usa hoy

```bash
bb lint     # anillos + docstrings (docs/v2/03)
bb test     # 99 tests / 413 aserciones
bb oracle   # reimplementación Python independiente de canonical-bytes: 9/9 ids
```

```clojure
(require '[sldb.host.default :as host] '[sldb.host.fs-store :as fs]
         '[sldb.kernel.store :as store] '[sldb.surface.markdown.plan :as md])
(def b  (fs/backend "/tmp/mi-store"))
(def s0 (store/init! b host/host {"jp" :all}))
(def r  (store/commit! b s0 (md/markdown->plan host/host (slurp "doc.md")
                              {:tree-id "01ARZ3NDEKTSV4RRFFQ69G5FAV" :name "doc" :actor "jp"
                               :timestamp "2026-08-29T12:00:00.000Z" :base (:head s0)})))
(md/store->markdown host/host (:store r) "01ARZ3NDEKTSV4RRFFQ69G5FAV")   ; == render(parse(doc.md))
(store/verify b (store/open b host/host))                                ; {:ok? true ...}
```

Y, cuando alguien edita ese `doc.md` por fuera (hitos 5a/5b):

```clojure
(require '[sldb.kernel.anchor :as anchor] '[sldb.kernel.reconcile :as rec])
(def r2 (store/commit! b (:store r)                                       ; reingesta: ni un :replace
          (md/markdown->update-plan host/host (:store r) "01ARZ3NDEKTSV4RRFFQ69G5FAV"
            (slurp "doc.md") {:actor "jp" :timestamp "..." :base (:revision-id r)})))
(anchor/report (:store r2) (:revision-id r2))       ; {:by-state {:orphan 1 ...} :orphans [...]}
(def p (first (rec/proposals (:store r2) (:revision-id r2) {})))
p                                                    ; {:old … :candidate … :confidence 1.0 :method :position …}
(store/commit! b (:store r2) (rec/accept-plan (:store r2) (:revision-id r2) p
                               {:actor "jp" :timestamp "..."}))           ; el anclaje pasa a superseded y se re-ancla
```

Lo que **no** hay: CLI, API/HTTP, consultas más allá de `get-object`/`diff`/anclajes/recorrer
árboles, índices derivados (Datascript), Node/cljs probado, stand-off por debajo del
párrafo materializado, semántica (M/G solo como datos), efectos, GC. Cada uno tiene su
drawer (§6).

## 3. Decisiones de diseño (todas confirmadas por el usuario)

| # | decisión | dónde |
|---|---|---|
| 1 | `class`/`kind` entran al hash del nodo; mismo texto como `:text` y `:opaque` son dos nodos | `02 §2.1`, `atom-canonical-content-and-node-hashing` |
| 2 | sucesión: `reference`/`binding` siguen al sucesor (en cualquier extremo); `semantic`/`projection` quedan `superseded`; `derived` se invalida | `02 §6.1`, `atom-re-anchoring-rules-under-succession` |
| 3 | el timestamp no entra en el id de arista: misma afirmación por el mismo origen = una arista | `02 §3.2`, `atom-evidence-required-per-edge-type` |
| 4 | ids de árbol nominales (ULID), descriptor en CAS, Revision de 8 campos | `02 §3.1, §5`, `atom-revision-id-edge-set-and-diff` |
| 5 | primer slice: SHA-256, backend de archivos, validación solo en Babashka, M/G como datos | `02 §8.1`, `atom-first-slice-runtime-choices` |
| 6 | **árboles de posiciones** (Git-like): un nodo puede ocurrir N veces en un árbol; ownership por path; op `:detach` | `02 §3.1, §5.1`, `atom-decision-trees-are-trees-of-positions-git-like` |
| 7 | superficie Markdown como **perfil cerrado** (SLDB-MD): fuera del perfil = opaco; marcas inline viven en el bloque, las hojas de texto son puras y compartidas; `_` nunca es énfasis; párrafos canónicos de una línea | `04`, `atom-sldb-md-profile-…`, `atom-inline-marks-live-on-the-block-…` |
| 8 | no hay Lisp aparte (EDN + SCI); cero Rust (WASM ajeno); kernel `.cljc` con hosts como adaptadores | `02 §8`, atoms `decision-*` |
| 9 | los estados deterministas son **tres** (`intact`/`superseded`/`orphan`), se calculan **por extremo** y la arista toma el peor; `drifted` es un `orphan` que la reconciliación supo nombrar, no un cuarto estado | `02 §6.2, §6.5`, `atom-anchor-state-is-derived-and-computed-per-endpoint`, `atom-drifted-is-a-reconciled-orphan` |
| 10 | una propuesta de reconciliación vive **fuera del pool**; aceptarla es una transacción `supersedes` cuya única evidencia es el actor: responde quien acepta, no el heurístico | `02 §6.5`, inv. 18 |
| 11 | el `:fingerprint` externo es `<alg>:<hex>` con el algoritmo del store; el kernel valida la forma y compara, y qué bytes se digieren es contrato del motor emisor | `02 §6.4`, `atom-external-fingerprint-form-and-who-computes-it` |

## 4. Cómo se trabaja (y por qué)

1. **Gate de cero contexto** antes de codificar (`desk/rituals/ritual-zero-context-audit-gate.md`):
   lanes Haiku frescos, read-only, auditan la spec y la task hasta que no queda ningún
   hallazgo alto/medio. Ha reescrito `02` tres veces y `04` dos; cada vez encontró vacíos
   reales (contenido canónico, orden de hermanos, `ConflictSet`, unescape, dedentado,
   el contrato de errores de las consultas de anclaje, el orden de los paths, el caso de
   la cadena de sucesión cíclica, la entrada de la reconciliación…). Dos avisos aprendidos
   en los gates de 5a/5b, anotados en sus `triage.md`: hay que **decirle a la lane que "el
   código todavía no existe" no es un hallazgo** —si no, un modelo débil sin nada que
   encontrar re-describe la propia task como si fuera su hueco— y hay que **snapshotear
   `task.txt` después** de las correcciones de la ronda, no antes.
2. **Ejecutor** implementa con `bb lint && bb test && bb oracle` verdes, deja evidencia en
   `runs/subagents/<ts>-<task>/` (task/next/graph/git-status, `validation.log`,
   `result-summary.md`) y cierra con `deskops closeout commit` + `deskops advance`.
3. **Trazabilidad**: `docs/v2/tests/promises.md` mapea cada promesa normativa de `02`/`04`
   a su test; lanes tester frescos generan la tabla y los huecos.
4. **Anillos**: `bb lint` falla ante un `require` hacia fuera o un var público sin docstring.
5. **Lo que no es apuntalar lo hecho va al drawer** (`deskops inbox` → `promote`).

## 5. Evidencia y trazas

- `runs/subagents/` — 16 directorios: gate del primer slice (5 rondas), ejecutores de los
  hitos 0–5b, lanes tester, gate del hito 4 (2 rondas), gates de 5a y 5b (3 rondas cada uno),
  cierre de estándares y spec2viz. Índice: `runs/subagents/index.jsonl`.
- 34 atoms `epoch:v2` (autoridad conceptual); 186 legado (19 retirados en `raw/source/atoms-retired/`).
- 6 diagramas spec2viz en `docs/architecture/spec2viz/v2-*.yml` (render en `rendered/v2/`).
- 30 commits el 2026-08-29 y 7 el 2026-08-30 en `refactor-target`; tag `pre-v2-planning-freeze`
  marca el estado anterior.

## 6. Qué sigue, y el drawer (13 tasks)

El siguiente hito del roadmap es el **6 (stand-off)**: hojas con offsets, capas UAX #29 bajo
demanda y el anclaje `(hoja, rango, hash)` sin materializar nodos. Todo lo que necesita ya
existe — el puerto de grafemas, los nodos `:span` y la regla de §6.2 que hace que un span
herede el suelo de su hoja — y ya está anotado como candidato en el drawer
(`task-milestone-6-stand-off-below-the-paragraph`).

`task-node-host-parity-for-the-v2-kernel` (probar `.cljc` + puertos en Node:
`Intl.Segmenter`, `crypto`, `fs`) · conformidad CommonMark completa · cobertura/lint
(clj-kondo, JVM) · contención entre procesos · monotonicidad ULID · objetos inalcanzables/GC ·
reescritura de specs spec2viz pre-v2 · oráculo externo (hecho; cerrable).

## 7. Deuda aceptada

`transclusion` sin definir (hito 4+); `:symbol` y `:fact` resuelven por existir en el CAS
porque todavía no viven en árboles `:context` (concesión explícita de `02 §6.2`, la endurece
el hito 7);
rituales `execution/testing/closeout` legado intactos (la pill `pill-guardrail-v2-implementation-gate`
manda para tasks v2); `Board.md` legado no editable por CLI; `deskops` no puede editar
atoms/board en formato legado y `atoms add-namespace` sobrescribe el fichero (ambos en
`desk/inbox`); `_` como énfasis y tablas/footnotes/imágenes fuera del perfil Markdown.
