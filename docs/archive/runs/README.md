# Archivo · runs

Evidencia de ejecución histórica, congelada el 2026-10-08. No es autoridad y no entra al
store `.kimun` (`kimun.include.edn` solo declara `desk/models/*.edn` y los documentos bajo
`desk/`).

## Contenido

- `subagents/` — directorios de evidencia del flujo pre-v2 de workers herdr (un directorio
  por run: `20260829`–`20260831`), cada uno con `brief.md`, `task.txt`, `git-status.txt`,
  `graph.txt`, `next.txt`, `triage.md` y rondas `round-1..N`, más un `index.jsonl`. Se
  archivó el 2026-10-08 porque corresponde al workflow anterior a la reorientación v2
  (pre-zero-context-audit-gate); la evidencia v2 vigente se guarda en `runs/` y las
  referencias de proceso viven en `AGENTS.md` y `docs/v2/`.

## Lo que NO se archiva

- `runs/bench/` — benchmark v2 vigente.
- `runs/parity/` — informes de paridad v2 vigentes.
