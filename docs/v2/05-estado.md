# SLDB v2 — Estado del kernel (2026-08-29)

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
| 0 kernel | `sldb.kernel.{ports,err,canon,node,pool,edge,tree,plan,revision,heads,store}` | puertos del host; errores tipados; bytes canónicos + hash; nodos S/M/G; pool; aristas con evidencia; árboles de **posiciones** con Merkle perezoso; `TransactionPlan` con 7 chequeos; revisiones inmutables, sucesión, ConflictSet/rebase, diff; heads con CAS; puerto `Backend` + open/replay/verify |
| 1 host | `sldb.host.{default,hash,text,ulid,fs-store}` | SHA-256, NFC + grafemas (UAX #29), ULID, backend de archivos (`objects/`, `log.edn`, `heads.edn`, `store.edn`) |
| 2 superficie | `sldb.surface.markdown.{cst,inline,ast,render,plan,report}` | Markdown ⇄ AST neutro ⇄ árbol de documento en el pool; render canónico; informe de direccionabilidad |

2.137 líneas de `src`, 1.507 de `test`. Hitos cerrados del roadmap (`02 §9`): **0, 1, 2, 3, 4**.

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

Lo que **no** hay: CLI, API/HTTP, consultas más allá de `get-object`/`diff`/recorrer
árboles, índices derivados, Node/cljs probado, estados de anclaje, semántica (M/G solo
como datos), efectos, GC. Cada uno tiene su drawer (§6).

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

## 4. Cómo se trabaja (y por qué)

1. **Gate de cero contexto** antes de codificar (`desk/rituals/ritual-zero-context-audit-gate.md`):
   lanes Haiku frescos, read-only, auditan la spec y la task hasta que no queda ningún
   hallazgo alto/medio. Ha reescrito `02` dos veces y `04` una; cada vez encontró vacíos
   reales (contenido canónico, orden de hermanos, `ConflictSet`, unescape, dedentado…).
2. **Ejecutor** implementa con `bb lint && bb test && bb oracle` verdes, deja evidencia en
   `runs/subagents/<ts>-<task>/` (task/next/graph/git-status, `validation.log`,
   `result-summary.md`) y cierra con `deskops closeout commit` + `deskops advance`.
3. **Trazabilidad**: `docs/v2/tests/promises.md` mapea cada promesa normativa de `02`/`04`
   a su test; lanes tester frescos generan la tabla y los huecos.
4. **Anillos**: `bb lint` falla ante un `require` hacia fuera o un var público sin docstring.
5. **Lo que no es apuntalar lo hecho va al drawer** (`deskops inbox` → `promote`).

## 5. Evidencia y trazas

- `runs/subagents/` — 12 directorios: gate del primer slice (5 rondas), ejecutores de los
  hitos 0–4, lanes tester, gate del hito 4 (2 rondas), cierre de estándares y spec2viz.
  Índice: `runs/subagents/index.jsonl`.
- 30 atoms `epoch:v2` (autoridad conceptual); 186 legado (19 retirados en `raw/source/atoms-retired/`).
- 6 diagramas spec2viz en `docs/architecture/spec2viz/v2-*.yml` (render en `rendered/v2/`).
- 30 commits el 2026-08-29 en `refactor-target`; tag `pre-v2-planning-freeze` marca el estado anterior.

## 6. Drawer (13 tasks) — siguiente paso sugerido en negrita

**`task-milestone-5a-anchor-states`** (estados deterministas
`intact/superseded/orphan` por extremo y por arista, consultas de anclaje, disparador
`supersedes` del re-anclaje, forma del `:fingerprint` externo; `02 §6.1-6.4`) ·
`task-milestone-5b-drifted-reconciliation` (propuestas fuera del pool que reclasifican un
`orphan` como `drifted`; `02 §6.5`, `04 §8`) · `task-node-host-parity-for-the-v2-kernel` (probar `.cljc` + puertos en
Node: `Intl.Segmenter`, `crypto`, `fs`) · conformidad CommonMark completa · cobertura/lint
(clj-kondo, JVM) · contención entre procesos · monotonicidad ULID · objetos inalcanzables/GC ·
reescritura de specs spec2viz pre-v2 · oráculo externo (hecho; cerrable).

## 7. Deuda aceptada

`transclusion` sin definir (hito 4+); bytes de `:fingerprint` por `:kind` externo (forma en el hito 5a; la receta por kind es contrato del motor emisor);
rituales `execution/testing/closeout` legado intactos (la pill `pill-guardrail-v2-implementation-gate`
manda para tasks v2); `Board.md` legado no editable por CLI; `deskops` no puede editar
atoms/board en formato legado y `atoms add-namespace` sobrescribe el fichero (ambos en
`desk/inbox`); `_` como énfasis y tablas/footnotes/imágenes fuera del perfil Markdown.
