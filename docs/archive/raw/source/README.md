# raw/source — material fuente congelado

Todo lo anotado antes de la reorientación v2, movido aquí el 2026-08-29. El estado previo
completo está en el tag `pre-v2-planning-freeze`. Nada aquí es autoritativo: `docs/v2/`
y los atoms `epoch:v2` prevalecen.

| Carpeta | Contenido |
|---|---|
| `core/` | transcripción de la conversación de diseño del kernel (Rust → Clojure): `also_core`, `core_README`, `diagramas_core`, `interfaces`, `libraries_core`, `plan_core`, `reasoning` |
| `architecture/` | overview y ADRs de la etapa intermedia (`sldb-text-layer-vs-kgdb-graph-layer`, `structured-text`) |
| `sldb-v1/` | README, FAQ y workspaces del SLDB v1 (Python / Pydantic / Markdown) |
| `drawer-features/` | features del drawer de la etapa Rust |
| `subagent/` | exploraciones de ontología de atoms, mapas de comportamiento, pseudocódigo AST |
| `scripts/` | `pivot_to_clojure.py` (reemplazo textual Rust→Clojure), `update_atoms.py` |
| `ecosystem/` | copias de referencia: `SMG_DEFINITIONS.md` (hum-ecosystem/docs/concepts), `sldb-v1-smg-ir.md` (sldb v1), `matrix-tractatus-intro.md` (Matrix/Neurips_peiper/Intro.md) |

Referencias externas no copiadas:

- `/home/jp/proyectos/Matrix` — Matrix Engine y Tractatus Knowledge Machine (espíritu de v2)
- `/home/jp/proyectos/hum-ecosystem/docs/concepts/` — `KG_TREES.md`, `HEURISTIC_OF_SANITY.md`, `DECONVERSION.md`, `FAMILIES.md`
- `/home/jp/proyectos/hum-ecosystem/docs/architecture/nl_sl_kg_pipeline.md`
