# kimun (SLDB v2) — documentos de dirección

Condensación de la reorientación del 2026-08-29: de "kernel para documentos estructurados"
a "base de datos de lenguaje estructurado sobre el modelo S/M/G".

| Doc | Qué responde |
|---|---|
| `01-orden-filosofico.md` | qué es el objeto persistido, qué garantías tiene sentido pedirle, qué se retira del diseño anterior |
| `02-sustrato-computacional.md` | estructuras de datos, invariantes, runtime, y roadmap por analogía con el modelo de objetos de Git |
| `03-estandares-de-codigo.md` | anillos de dependencia (`bb lint`), compartimentación, docstrings, estándares de test |
| `04-superficie-markdown.md` | perfil SLDB-MD: CST lossless, AST neutro con marcas por grafema, render canónico, mapeo al pool, opacos, informe de direccionabilidad |
| `06-superficies-y-cli.md` | producto `kimun`: superficie CLI (grupos v1 + evaluador), envelope, exit codes, store `.kimun/`, índices y efectos |
| `07-modelos-como-nodos.md` | modelos como descriptores EDN opacos en el árbol `models`: definición, versionado por `supersedes`, proyección auditable, frontmatter |
| `08-distribucion.md` | topología de repos (A1–A4), toolchain bb, `bb jar` / `bb release`, layout de bundle y launcher, workflows y gate humano de publicación |
| `09-evaluador-anclado.md` | evaluador de s-expresiones anclado (port del Python), escritura por plan, derivados y propagación por hash |
| `05-estado.md` | **empieza aquí**: qué existe, cómo se usa, decisiones confirmadas, evidencia, drawer y siguiente paso |
| `tests/promises.md` | trazabilidad promesa de la spec → test que la prueba (kernel, superficie Markdown, pista S) |

## Diagramas (spec2viz, fuente YAML en `docs/architecture/spec2viz/v2-*.yml`, render en `rendered/v2/*.mmd`)

| diagrama | qué muestra |
|---|---|
| `v2-rings` | anillos kernel / host / tests y las dependencias permitidas entre namespaces |
| `v2-cas-objects` | modelo de objetos content-addressed: Revision, tree-set, edge-set, descriptor, tree object, Node, Edge, plan resuelto, heads, log |
| `v2-transaction-flow` | secuencia plan → validación → aplicación → objetos → persistencia (CAS de heads) |
| `v2-replace-reanchoring` | pasos de `:replace` y las reglas de re-anclaje por tipo de arista |
| `v2-store-layout` | open/replay/verify sobre el backend de archivos |
| `v2-markdown-surface` | pipeline de la superficie Markdown: texto → CST → AST → plan → árbol de posiciones → render |

Los `.mmd` son proyecciones: para cambiar un diagrama se edita el YAML y se vuelve a renderizar (`spec2viz diagram render … --renderer mermaid`).

## Autoridad

1. `docs/v2/01-orden-filosofico.md`
2. `docs/v2/02-sustrato-computacional.md`
3. `desk/atoms/` (los atoms etiquetados `epoch:v2` prevalecen sobre los anteriores)
4. `raw/source/` — material fuente congelado (tag `pre-v2-planning-freeze`), no autoritativo
5. `docs/architecture/` — contratos y diagramas de la etapa anterior; se consideran
   superados donde contradigan a `docs/v2/`, y se irán regenerando desde los nuevos atoms

## Lo que `docs/v2` supersede explícitamente

- round-trip textual exacto y árboles lossless (Rowan) como requisito del kernel
- Lisp como metalenguaje separado de Clojure
- Rust como lenguaje del kernel y PyO3 como boundary
- familia reversible / no reversible como unidad primaria de clasificación
- documento como raíz de autoridad
- el ADR `sldb-text-layer-vs-kgdb-graph-layer.md` en cuanto a S y G como stores separados
