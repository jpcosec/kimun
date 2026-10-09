# kimun (SLDB v2) — Estado (2026-10-08)

> Solo estado actual y decisiones vigentes. La autoridad y el orden de lectura están en
> `docs/v2/README.md`. Los pendientes, en `12-ecosistema-y-pendientes.md`. La historia
> (pista S, drawer pre-v2, evidencia de los gates de cero contexto) está en `docs/archive/`.

## 1. Qué hay hoy

Un motor de versionado content-addressed sobre lenguaje estructurado, en Clojure `.cljc`
puro sobre Babashka, más las superficies que lo usan. Cada capacidad tiene un dueño:

- **Kernel (formato 2 y 3)** — pool de nodos S/M/G, árboles de posiciones con Merkle, aristas
  tipadas con evidencia, revisiones inmutables con sucesión, estados de anclaje derivados y
  reconciliación de ediciones externas. `open` lee las cabezas sin replay y `stores upgrade`
  pasa un store 1→2 (reconstruido) o 2→3 (en el sitio). Contrato: `02-sustrato-computacional.md`;
  forma en disco: `10-escala.md`.
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
  recorridos, análisis y export; con los stores enlazados alcanzables dentro (calificados
  por nombre de enlace; `--local` los deja fuera). CLI: `06-superficies-y-cli.md`.
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
590 funciones de test de v1: 518 portadas, 1 mapeada, 14 oráculo, 55 n/a, 0 brechas, 1
corpus y 1 abierta. Detalle y desviaciones: `tests/parity-v1.md`.

**Store real** (1.551 documentos migrados de v1; antes → después, `10-escala.md §9`):
`docs show` 187,9 s → 0,37 s; `docs list` 180,3 s → 0,77 s; `stores check` 173,2 s → 18,7 s;
`stores verify` 228,4 s → 32,0 s; `.kimun/` 606 MB → 97,7 MB; migración desde v1 522 s → 71 s.
Abrir y commitear no crecen con la historia.

**Suite** — 888 tests / 9.577 aserciones, 109 namespaces (última corrida de `bb test`,
2026-10-09, con el pod tree-sitter construido), 0 fallos. `bb oracle`: 9/9 ids
canónicos; `bb lint`: 99 ficheros.

**Formato 4 de este repo** (desk de 433 KB, 539 documentos; antes = formato 3 de S1b, después
= formato 4): aparente **8,07 MB → 2,51 MB** (objetos 12.494 → 4.808; tree-object 1,65 MB →
3,9 KB; plan 4,03 MB → 907 KB; el registro: anchor externo 172 KB + descriptor 186 KB +
nodo raíz 62 KB + aristas 489 KB). El árbol del documento ya no está en el CAS: vive en
`<store>/cache/` (derivado, gitignored, borrable; 2,26 MB, medido aparte por `bb store-size`).
Detalle en `10-escala.md §11`. Lo que el formato 4 todavía **no** hace: el plan resuelto del
build sigue en el CAS, así que el store no baja de los 433 KB del desk que indexa (`12 §1`).

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
| 21 | **autoridad por clase** (2026-10-08): bytes → archivo (+git); estructura → pool como caché verificable; hechos → pool, decisiones humanas además como documentos. Revisa `01 §4.1` | `14 §2` |
| 22 | todo hecho vive en un `W_i`; contexto por defecto = el documento `source`; ruteo entre contextos solo por proyecciones promovidas | `14 §3` |
| 23 | `R`, `A`, `B` son posiciones en taxonomías con herencia; las propiedades algebraicas de `R` se declaran por contexto; el vocabulario `L_i` es un documento `source` | `14 §4` |
| 24 | **(EN REVISIÓN, ver `drafts/capa-g-dudas.md`)** afirmación = `:status` en evidencia de arista `projection`; `V`/`S` índice derivado por `(hecho, W_i)`; nada pasa a `TRUE` sin decisión registrada | `14 §5` |
| 25 | conflicto lógico = propuesta fuera del pool; se resuelve por transacción; nunca en silencio | `14 §6` |
| 26 | cada `W_i` se evalúa como matrices booleanas por relación; índices derivados; nunca `p×p` global; motor = implementación | `14 §8` |
| 27 | **formato 4 del store**: registro por documento (anchor + clase + hash raíz + hash de contenido); el árbol/bloques/nodos de un documento con archivo son caché verificable (root hash + posiciones referenciadas, `<store>/cache/`); contenido por referencia con `inline` por caso; log como caché bajo `:git :ignore`; decisiones humanas como documentos | `14 §10`, `14 §18–§19` |
| 28 | modelo = símbolo-tipo + selector + proyecciones; el template de v1 es un caso especial; la paridad v1 es su regresión | `14 §9` |
| 29 | tree-sitter como pod es el parser de la capa M, por fases; SLDB-MD sigue como perfil cerrado; `blocks.cljc` como oráculo hasta paridad byte a byte | `14 §11`, spike R2 |
| 30 | Datalog para la consulta de G; Datalevin (pod) candidato con respaldo in-process; índice G derivado | `14 §8`, spike R1 |
| 31 | **(EN REVISIÓN, ver `drafts/capa-g-dudas.md`)** salida de prompt = `proposed` por defecto; políticas de promoción por store/contexto (harness) | `14 §7` |
| 32 | unidad de anclaje = hoja + grafemas NFC | `14 §7` |
| 33 | unidad de escritura = revisión por acción + log de hechos en flujo consolidado | `14 §12` |
| 34 | transversales de primera clase = pack mínimo de autodescripción (clase de datos, procedencia, contexto, kimun mismo); texto y código | `14 §12`, `§16 P13` |
| 35 | cualquiera propone; el humano tiene la autoridad máxima; UI por definir | `14 §12` |
| 36 | el diccionario de signos cortos es otro grafo, por contexto | `14 §16 P14` |
| 37 | hechos de la forma más simple + caché de derivados por inputs | `14 §12` |
| 38 | embeddings sin idioma fijo ni plantilla; modelo multilingüe; `dog/perro/hund` misma vecindad es un test | `14 §16 P15` |
| 39 | lenguaje de selección por spike en etapa A; índice por revisión derivado también en `:git :track` | `14 §12` |
| 40 | **un documento se lee bajo el contrato con el que se escribió** (A1, 2026-10-08): la evidencia de la arista `derived` root→fuente registra `:contract` = id del descriptor, y la sucesión del modelo no la toca; `docs show|list`, `fields`, `sections`, `find`, `edges` y `stores check|health` leen ese descriptor (el nodo viejo sigue en el CAS) hasta que `docs update` o `stores update`/`--backfill` re-renderizan el payload registrado bajo el contrato actual. Un documento sin `:contract` (previo a A1 o migrado de v1) cae al contrato actual | `06`, `07 §1` |
