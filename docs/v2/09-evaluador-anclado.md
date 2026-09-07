# kimun — Evaluador anclado, escritura y derivados (pista S, §F y §E del plan)

> Condensa los cuatro specs Python del evaluador de `knowledge` v1 (`semantic-anchoring`,
> `usability`, `components D1–D3`, `database`) en el vocabulario de `02`, y fija cómo la
> escritura, los valores derivados y la propagación por hash pasan por el kernel. Llega en
> S4 (lectura) y S5 (escritura y derivados); S0 solo reserva la superficie (`06 §B`: un
> primer token que no es grupo devuelve `:eval/not-available`, exit 6).

## 1. Qué es

Un **evaluador de s-expresiones anclado a la KB**: `kimun next task`, `kimun check
atom "…" --summary`, `kimun rel Task Atom`. La forma superficial (tokens) se desazucara a
una s-expresión EDN; cada símbolo se resuelve contra los **anchors** de la KB (nombres
canónicos de modelos, documentos, relaciones, operaciones, proyecciones y expresiones) y el
resultado se evalúa sobre el índice (`06 §7`). El evaluador no tiene una puerta lateral al
store: **lee del índice y escribe por `TransactionPlan`**, como cualquier grupo v1.

| pieza Python (`knowledge` v1, `src/knowledge/`) | en `kimun` | hito |
|---|---|---|
| `sexpr.py` (lector/impresor propio) | `edn/read-string` + `pr-str`; una s-expr es EDN | S4 |
| `anchors.py` (13 anchors YAML) | `:anchors` del índice; `AnchorDoc` es un descriptor de modelo (`resources/models/AnchorDoc.edn`) con `kind ∈ #{model doc relation operation projection expr}`; los 13 anchors viven en `resources/grammar/` como gramática por defecto y se ingestan como documentos | S4 |
| `resolution.py` (cascada) | `kimun.surface.eval.resolve`: misma cascada; cada símbolo termina en `:resolved`, `:ambiguous` (exit 2) o `:missing` (exit 1) | S4 |
| `session.py` | `.kimun/session.edn` (derivado, gitignored): contexto con TTL y `:revision`; una sesión más vieja que el head se descarta con aviso | S4 |
| `surface.py` (desugar) | `kimun.surface.eval.desugar`: `next task` → `(next Task)`, `check atom "x" --summary` → `(check Atom "x" :summary true)` | S4 |
| `evaluator.py` | `kimun.surface.eval.core`: `rel related common also filter-by next check` sobre el índice; desaparece "corre project" (era un efecto escondido) | S4 |
| `ops/read.py` | `kimun.surface.eval.read` | S4 |
| `ops/write.py` (no existía: v1 era solo lectura) | `kimun.surface.eval.write`: `assert`, `create`, `ingest`, `next --advance` | S5 |
| `render.py` | `kimun.cli.out` (envelope `06 §3`) | S0 |
| `bridges/{sldb,kgdb}` | desaparecen: el índice y el pool son el único backend | — |

## 2. Resolución de símbolos (cascada)

1. literal EDN (string, número, keyword) → valor;
2. nombre exacto de anchor (`Task`, `Atom`, `next`) → `:resolved` con el `tree-id` del anchor;
3. alias declarado en el anchor (`tarea` → `Task`) → `:resolved`;
4. prefijo único entre anchors del mismo `kind` → `:resolved` con `:warning :prefix`;
5. varios candidatos → `:ambiguous {:candidates [...]}` (exit 2, nunca se elige uno);
6. ninguno → `:missing {:symbol s}` (exit 1).

La sesión (`session.edn`) aporta el contexto implícito (`--in`, último modelo, último doc)
solo si su `:revision` es alcanzable desde el head actual.

## 3. Escritura: todo es un plan

| operación | plan | evidencia |
|---|---|---|
| `create Model campo=valor…` | render del descriptor (`07 §3`) → `ast->plan` + `reference` doc→modelo | `{:actor "agent/x" :engine "kimun.eval" :expr <edn/expr>}` |
| `ingest path.md` / `docs track` | árbol nuevo o `markdown->update-plan` (reingesta sin `:replace`) | actor |
| `assert doc campo=valor` / `fields set` / `docs update` | `kimun.surface.edit`: LCS por bloques del AST → `:replace` (⇒ `supersedes`), `:add-edge`, `:detach` | actor + `:expr` |
| `next Task --advance` | `assert` del siguiente estado según `:state` del descriptor (`07 §2`) | actor + `:expr` |

La expresión evaluada se guarda como nodo `:opaque {:format "edn/expr"}` con una arista
`derived` desde el bloque nuevo hacia ella (`:engine "kimun.eval"`): **la provenance de
cada escritura vive en el pool**, no en un log aparte. Después del commit, el `EffectPlan`
(`06 §8`) escribe el Markdown proyectado vía outbox; `outbox/log.edn` registra
`{:tree :revision :path :hash-c}`. Este es el camino que los agentes deben usar en lugar de
editar Markdown a mano: `status` detecta la edición externa y `reconcile` la propone.

## 4. Derivados y propagación por hash

`derive define NAME --expr '(…)' [--engine sci|shell:CMD]` crea un valor
`:opaque {:format "edn/value"}` en el árbol `derived` con aristas `derived` valor→expr y
valor→cada input (posiciones de documentos, campos, otros derivados). Como `02 §6.1` invalida
`derived` bajo sucesión, **la validez de un derivado es `anchor/states` sobre sus aristas**:
`intact` = vigente, `superseded`/`orphan` = stale. No hay un tracker aparte.

- `derive status [--stale]`: lista derivados y su estado por input.
- `derive run [--stale] [NAME]`: reevalúa (actor `kimun/derive`, capability propia),
  `:replace` del valor ⇒ `supersedes` ⇒ lo que dependía de él queda stale a su vez.
- `--propagate` (o `:derive/auto true` en `store.edn`) encadena `derive run --stale` hasta
  punto fijo; el orden es topológico sobre las aristas `derived`, ciclos ⇒ `:derive/cycle` exit 3.
- Motores: **SCI** con allowlist puro (sin IO, timeout) para expresiones sobre el índice;
  **`shell:CMD`** opt-in por store (`:engines #{:shell}` en `store.edn`) cuyo stdout EDN/JSON es
  el valor — así un resultado de código empírico entra a la KB con provenance.
- Un derivado puede ser input de un campo `render•` de un documento: el flujo por hash llega
  hasta el Markdown proyectado (`fields set` ⇒ stale ⇒ `derive run` ⇒ el `.md` cambia; DoD de S5).

## 5. Spec ejecutable

Los 17 casos de `tests/test_acceptance.py`, los 12 de `tests/test_wrapper.py` (KB efímera
construida por plan, no por ficheros) y los compliance portables de `knowledge` v1 se
portan a `test/kimun/surface/eval/*_test.cljc` más goldens de CLI (`test/fixtures/cli/s4/`).
En S7 la KB deja `legos/knowledge` hacia `pron`; los tests y anchors de v1 se quedan con `knowledge` v1, que sigue viva (`08 §0`), y se copian aquí como spec ejecutable.

## 6. Lo que no se copia de v1

"Corre project" como efecto implícito; excepciones tragadas como "vacío válido"; el cliente
Python in-process (`bridges/`); la sesión sin revisión. Y ninguna escritura fuera de plan.
