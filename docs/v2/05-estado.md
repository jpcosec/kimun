# kimun (SLDB v2) — Estado (2026-10-08)

> Solo estado actual y decisiones vigentes. La autoridad y el orden de lectura están en
> `docs/v2/README.md`. Los pendientes, en `12-ecosistema-y-pendientes.md`. La historia
> (pista S, drawer pre-v2, evidencia de los gates de cero contexto) está en `docs/archive/`.

## 1. Qué hay hoy

Un motor de versionado content-addressed sobre lenguaje estructurado, en Clojure `.cljc`
puro sobre Babashka, más las superficies que lo usan. Cada capacidad tiene un dueño:

- **Kernel (formato 2)** — pool de nodos S/M/G, árboles de posiciones con Merkle, aristas
  tipadas con evidencia, revisiones inmutables con sucesión, estados de anclaje derivados y
  reconciliación de ediciones externas. `open` lee las cabezas sin replay y `stores upgrade`
  pasa un store 1→2. Contrato: `02-sustrato-computacional.md`; forma en disco: `10-escala.md`.
- **Superficie Markdown** — perfil cerrado SLDB-MD: texto → CST → AST neutro → árbol de
  documento en el pool, render canónico y reingesta de ficheros editados por fuera. Dueño:
  `04-superficie-markdown.md`.
- **Modelos** — descriptores EDN como nodos opacos del árbol `models`, versionados por
  sucesión y proyectables para auditarlos, más el modo directo de extract/render/validate.
  Dueño: `07-modelos-como-nodos.md`; CLI: `06-superficies-y-cli.md`.
- **Documentos tipados** — documentos Markdown rastreados contra un modelo, con estado,
  payload extraíble y reingesta. CLI: `06-superficies-y-cli.md`.
- **Campos y secciones** — edición y consulta de campos y secciones de un documento. CLI:
  `06-superficies-y-cli.md`.
- **Índice, `find` y corpus** — índice derivado cacheado, direcciones, búsqueda física y
  semántica, `--where` y ranking de corpus. CLI: `06-superficies-y-cli.md`.
- **Aristas, grafo y predicates** — el grafo como vista derivada: relaciones y tipos,
  recorridos, análisis y export. CLI: `06-superficies-y-cli.md`.
- **Semántica** — tags como términos `:symbol/:term`, jerarquía de padres y equivalencias.
  CLI: `06-superficies-y-cli.md`.
- **Journal** — vista del log y del DAG de revisiones, sin segundo almacén. CLI:
  `06-superficies-y-cli.md`.
- **Diagnósticos** — veredictos y reparaciones del store, con categorías que nombran la
  acción. CLI: `06-superficies-y-cli.md`.
- **Inclusión en git** — `kimun.include.edn` declara qué versiona un repo. Dueño:
  `13-include.md`.
- **Adaptadores** — protocolo 1 fuera de proceso (incluido tree-sitter), que convierte
  código en documentos. Dueño: `11-adaptadores.md`.
- **Migración desde v1** — `migrate --from-v1` trae un store sldb v1 de forma idempotente.
  CLI: `06-superficies-y-cli.md`.
- **`serve`** — API HTTP sobre los comandos, con token opcional. CLI:
  `06-superficies-y-cli.md`.
- **Empaquetado** — `bb jar`, `bb release` y el launcher. Dueño: `08-distribucion.md`.
- **No implementado** — el evaluador anclado (S4) y los derivados (S5), el cliente Python y
  los motores de derivados. `06 §6`, `docs/archive/v2/09-evaluador-anclado.md`.

## 2. Medidas

**Paridad con sldb v1** (2026-10-08, `python3 scripts/parity/coverage.py <v1> .`), sobre las
590 funciones de test de v1: 516 portadas, 1 mapeada, 14 oráculo, 55 n/a, 2 brechas, 1
corpus y 1 abierta. Detalle y desviaciones: `tests/parity-v1.md`.

**Store real** (1.551 documentos migrados de v1; antes → después, `10-escala.md §9`):
`docs show` 187,9 s → 0,37 s; `docs list` 180,3 s → 0,77 s; `stores check` 173,2 s → 18,7 s;
`stores verify` 228,4 s → 32,0 s; `.kimun/` 606 MB → 97,7 MB; migración desde v1 522 s → 71 s.
Abrir y commitear no crecen con la historia.

**Suite** — 877 tests / 9.458 aserciones, 107 namespaces (última corrida de `bb test`,
2026-10-08), 0 fallos. `bb oracle`: 9/9 ids
canónicos; `bb lint`: 96 ficheros.

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
| 8 | no hay Lisp aparte (EDN + SCI); kernel `.cljc` con hosts como adaptadores. La parte "cero Rust" queda **reemplazada por la 19** (2026-10-06) | `02 §8`, `atom-decision-no-separate-lisp-metalanguage`, `atom-decision-no-rust-in-the-repository` → decisión 19 |
| 9 | los estados deterministas son **tres** (`intact`/`superseded`/`orphan`), se calculan **por extremo** y la arista toma el peor; `drifted` es un `orphan` que la reconciliación supo nombrar, no un cuarto estado | `02 §6.2, §6.5`, `atom-anchor-state-is-derived-and-computed-per-endpoint`, `atom-drifted-is-a-reconciled-orphan` |
| 10 | una propuesta de reconciliación vive **fuera del pool**; aceptarla es una transacción `supersedes` cuya única evidencia es el actor: responde quien acepta, no el heurístico | `02 §6.5`, inv. 18 |
| 11 | el `:fingerprint` externo es `<alg>:<hex>` con el algoritmo del store; el kernel valida la forma y compara, y qué bytes se digieren es contrato del motor emisor | `02 §6.4`, `atom-external-fingerprint-form-and-who-computes-it` |
| 12 | **un solo producto v2**: sldb v1 y kgdb se congelan (`v1-frozen`); la lectura del evaluador de `pron` se porta aquí (S4, pendiente) | `06`, `08 §1`, plan 2026-09-06 |
| 13 | los modelos son **nodos del árbol**: descriptores EDN como opacos `edn/model` en el árbol `models`, versionados por `:replace` ⇒ `supersedes`, proyectables para auditarse | `07` |
| 14 | **Babashka es el host definitivo del CLI**; el kernel sigue `.cljc`; la paridad Node queda en el drawer (UI) | `08 §2` |
| 15 | rutas **relativas** en `store.edn` (`:links`), índices y export; nunca absolutas (vicio v1) | `06 §2` |
| 16 | toda escritura (evaluador incluido) es un `TransactionPlan`; los derivados se invalidan por `anchor/states` sobre aristas `derived`, sin tracker aparte | `docs/archive/v2/09-evaluador-anclado.md §3–4` |
| 17 | **tres nombres para tres cosas** (2026-09-07): el producto v2 es **`kimun`** (Mapudungun *kimün*); **`pron`** es la KB **y** su evaluador Python en un solo paquete (repo `jpcosec/pron`, pip `pron`), que importa `kinesis`; `knowledge` queda congelado como el legacy de julio. Nada de v2 usa la palabra `knowledge` como identificador | `08 §0` |
| 18 | **v1 sigue en v1** (2026-10-06): los consumidores de sldb v1 (deskops, pron, legos, UIs…) se quedan en v1 por ahora; kimun avanza sin migrarlos. Como kimun no está desplegado, sus stores y su historial se pueden regenerar cuando haga falta ("retrackear"): la paridad byte a byte con v1 es referencia, no obligación | `12-ecosistema-y-pendientes.md` |
| 19 | **política de lenguajes** (2026-10-06): la lógica de alto nivel se escribe mayormente en Clojure (kernel `.cljc`, superficies, CLI). Se admiten servicios nativos (Rust, C, Zig, Go…) si son fácilmente portables y empaquetables por plataforma, y plugins en cualquier lenguaje (Python, Java, JS/TS…) para no reimplementar lo que ya existe. Condición: pocos y con frontera clara — un protocolo documentado (adaptadores fuera de proceso, `11-adaptadores.md`; pods de Babashka), nunca lógica de dominio repartida entre lenguajes. Primer candidato: tree-sitter (C) como pod para el parseo de Markdown y código | `11-adaptadores.md` |
| 20 | **los namespaces del código pasan de `sldb.*` a `kimun.*`** (2026-10-08), solo en este repo: `sldb.kernel.*` → `kimun.kernel.*`, `sldb.host.*` → `kimun.host.*`, `sldb.surface.markdown.*` → `kimun.surface.markdown.*`. Los **identificadores de datos** se mantienen intactos porque entran en los hashes: nombres de engine (`sldb.surface.markdown`), ids de nodo `sldb://…`, tag lang `sldb/tag`, contextos `sldb/semantic/…`, contrato de export `sldb_kgdb_semantic_export` y keywords de error/formato. El lint de anillos resuelve el prefijo más específico, así que `kimun.kernel./host./surface.markdown.` conservan las mismas reglas | `03 §1` |
