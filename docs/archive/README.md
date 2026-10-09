# Archivo

Material histórico, congelado el 2026-10-08. No es autoridad y no entra al store `.kimun`:
`kimun.include.edn` solo declara `desk/models/*.edn` y los documentos bajo `desk/`.
Cada fichero Markdown lleva un banner al principio que dice qué era, cuándo se archivó y qué
lo superó.

## Contenido

| ruta | qué fue | superado por |
|---|---|---|
| `architecture/` | Contratos, diagramas y vistas de la etapa pre-v2 (Rust/Lisp, familia reversible/no-reversible, documento como raíz). | `docs/v2/` (autoridad: `docs/v2/README.md`). |
| `architecture/spec2viz/` | Specs `target-*.yml`, `manifest.yml`, sub-desk (deskops) y el store `.sldb` del proyecto spec2viz embebido. | `docs/architecture/spec2viz/v2-*.yml` + `rendered/v2/*.mmd` (se quedan ahí) y `docs/v2/07-modelos-como-nodos.md`. |
| `v2/09-evaluador-anclado.md` | Spec del evaluador de s-expressions anclado y de los derivados (S4–S5). | No implementado; pendientes en `docs/v2/12-ecosistema-y-pendientes.md`. |
| `v2/tests/parity-serve-checklist.md` | Checklist de rutas HTTP que faltaban en `serve`. | `docs/v2/tests/parity-v1.md` (D1b) y `src/kimun/surface/http/routes.cljc`. |
| `desk/rituals/` | Rituales planning-era (phase, execution, testing, closeout). | `AGENTS.md` y `desk/rituals/ritual-zero-context-audit-gate.md`. |
| `desk/contexts/` | Pills planning-era (planning-contracts, python, rust, specyaml, índice). | `AGENTS.md` y `desk/contexts/pill-guardrail-v2-implementation-gate.md`. |
| `desk/drawer/` | Backlog de features, tracks y tasks pre-v2. | `docs/v2/05-estado.md` §6. |
| `scripts/` | Utilidades pre-v2 (el servidor de anotaciones de las vistas UML y los scripts de bootstrap/validación de vistas) que leían `desk/contexts/`. | Sin lector activo; el JSON anotado quedó en `desk/contexts/vistas-uml.annotations.json` del archivo (`docs/archive/desk/README.md`). |

## Lo que NO se archiva

- `docs/architecture/spec2viz/v2-*.yml` y `docs/architecture/spec2viz/rendered/v2/`: son las
  specs y los diagramas v2 vigentes (fuente en YAML, render en Mermaid).
- `desk/rituals/ritual-zero-context-audit-gate.md` y
  `desk/contexts/pill-guardrail-v2-implementation-gate.md`: siguen en el desk activo, ya
  reescritos para el flujo kimun + workers herdr.
