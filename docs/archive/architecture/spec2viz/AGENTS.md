> **Archivado 2026-10-08.** Modo de trabajo del sub-desk pre-v2 de spec2viz (deskops). Superado por el `AGENTS.md` de la raíz y por el desk de kimun (`.kimun`, construido con `bin/kimun include build`). Los diagramas v2 vigentes quedan en `docs/architecture/spec2viz/v2-*.yml`.

# AGENTS — spec2viz

## Scope

- spec2viz es la capa de especificación machine-readable entre `desk/atoms/` del repo sldb y las proyecciones de diagramas (`core-diagrams/`, vistas HTML).
- Tiene desk propio (`desk/`): sus tasks gobiernan specs, vistas, generadores y validadores.
- Vive dentro de un worktree planning-only: ningún task afirma progreso de implementación del kernel.

## Mandatory recovery order

1. `AGENTS.md`
2. `manifest.yml`
3. `desk/tasks/Board.md`
4. pills en `desk/contexts/`
5. `desk/rituals/execution.md` → `testing.md` → `closeout.md`
6. atoms en `desk/atoms/`
7. contratos en `../contracts/specyaml-schema-contract.md` y `../contracts/diagram-traceability-contract.md`

## Hard rules

- `../sldb_Kernel_Vistas_UML.html` es GENERADO (`../../../scripts/generate_vistas_html.py`): prohibido editarlo a mano.
- Todo cambio cierra con `python3 ../../../scripts/validate_spec_traceability.py` en verde (warnings permitidos, errores no).
- Los `atoms:` de los specs referencian atoms del repo padre (`../../../desk/atoms/`); los atoms de este desk gobiernan la capa de trazabilidad misma.
- Simetría obligatoria: `views:` de un spec ↔ `specs:` de la vista en `../vistas/vistas.yml`.
