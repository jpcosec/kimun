# SLDB v2 — documentos de dirección

Condensación de la reorientación del 2026-08-29: de "kernel para documentos estructurados"
a "base de datos de lenguaje estructurado sobre el modelo S/M/G".

| Doc | Qué responde |
|---|---|
| `01-orden-filosofico.md` | qué es el objeto persistido, qué garantías tiene sentido pedirle, qué se retira del diseño anterior |
| `02-sustrato-computacional.md` | estructuras de datos, invariantes, runtime, y roadmap por analogía con el modelo de objetos de Git |
| `03-estandares-de-codigo.md` | anillos de dependencia (`bb lint`), compartimentación, docstrings, estándares de test |
| `tests/promises.md` | trazabilidad promesa de la spec → test que la prueba (primer slice) |

## Diagramas (spec2viz, fuente YAML en `docs/architecture/spec2viz/v2-*.yml`, render en `rendered/v2/*.mmd`)

| diagrama | qué muestra |
|---|---|
| `v2-rings` | anillos kernel / host / tests y las dependencias permitidas entre namespaces |
| `v2-cas-objects` | modelo de objetos content-addressed: Revision, tree-set, edge-set, descriptor, tree object, Node, Edge, plan resuelto, heads, log |
| `v2-transaction-flow` | secuencia plan → validación → aplicación → objetos → persistencia (CAS de heads) |
| `v2-replace-reanchoring` | pasos de `:replace` y las reglas de re-anclaje por tipo de arista |
| `v2-store-layout` | open/replay/verify sobre el backend de archivos |

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
