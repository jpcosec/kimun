# Archivo · scripts

Scripts de la etapa pre-v2 (diagramas) movidos desde `scripts/` (raíz) el 2026-10-08. No es
autoridad y no entra al store `.kimun`. Se archivaron porque, tras grepear `src/`, `test/`,
`bb.edn`, `docs/` (excepto `docs/archive/`) y `.github/`, ninguno tiene uso vivo en el repo;
solo aparecen referenciados entre sí y por material ya archivado en `docs/archive/`
(spec2viz/vistas) y por registros de tareas cerradas bajo `desk/tasks/`.

## Contenido

- `annotate_server.py` — servidor HTTP local (puerto 8765) que sirve `docs/architecture/` y
  persiste marcas de anotación en `desk/contexts/vistas-uml.annotations.json` sobre el HTML
  de vistas UML. Planning-only; depende de rutas `docs/architecture/…` que ya no existen en
  la raíz.
- `generate_vistas_html.py` — genera `docs/architecture/sldb_Kernel_Vistas_UML.html` a partir
  de `docs/architecture/vistas/template.html`, `vistas.yml` y los `*.mmd`. Artefacto
  generado según el `diagram-traceability-contract`; sus fuentes (`docs/architecture/vistas/`)
  ya están archivadas.
- `bootstrap_diagram_ui_from_git.sh` — clona `deskops`, `sldb` (rama `refactor-target`) y
  `spec2viz` bajo `<target>/.diagram-ui/vendor/` y copia el bundle de UI de diagramas
  (HTML, `spec2viz/`, `vistas/`, `annotate_server.py`) a otro proyecto. Utilidad de
  replicación hacia repos externos, sin uso interno.
- `replicate_diagram_ui.sh` — variante de `bootstrap_diagram_ui_from_git.sh` que copia el
  HTML de vistas, el servidor de anotaciones y el fichero de anotaciones desde un proyecto
  origen a otro. `TARGET_SERVER_REL` apunta por defecto a `scripts/annotate_server.py`.
- `prompt_bootstrap_diagram_ui.md` — prompt de una sola página que documenta el uso de
  `scripts/bootstrap_diagram_ui_from_git.sh` (p. ej. `~/overclock`) y su validación vía
  `curl` al servidor local.
- `validate_spec_traceability.py` — valida la trazabilidad spec ↔ atoms ↔ vistas ↔ puml ↔
  HTML contra los contratos `specyaml-schema-contract` y `diagram-traceability-contract`
  (exit 0 = verde). Era la gate de la etapa de diagramas pre-v2; sus rutas objetivo
  (`docs/architecture/spec2viz`, `core-diagrams`, `desk/atoms`) y los contratos que cita ya
  están archivados o superados por `docs/v2/`.
