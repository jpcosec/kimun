# Migración del store real de sldb v1 a kimun — 2026-10-06

Generado por `scripts/parity/compare_store.clj` sobre lo grabado por `scripts/parity/record_v1_store.py`.

## Totales

|  | total | migrated | skipped | failed | failed by reason |
|---|---|---|---|---|---|
| models | 35 | 35 | 0 | 0 | {} |
| documents, pass 1 (strict) | 1551 | 1504 | 0 | 47 | {:no-round-trip 47} |
| documents, pass 2 (--force) | 1551 | 47 | 1504 | 0 | {} |
| documents, pass 3 (re-run) | 1551 | 0 | 1551 | 0 | {} |

## Comparación con v1 (1551 documentos grabados)

| medida | resultado |
|---|---|
| status | {:payload-differs 7, :payload-equal 1544} |
| content-hash-vs-v1-live | {:differs-with-payload 7, :equal 1544} |
| content-hash-vs-v1-stored | {:differs-stale-v1-entry 2, :differs-with-payload 7, :equal 1542} |
| source-hash-vs-v1-live | {:equal 1551} |
| paths-relative | {true 1551} |

## Por modelo

| model | docs | strict migrated | strict failed | payload equal | payload differs | other |
|---|---|---|---|---|---|---|
| ArchitectureNarrativeDoc | 9 | 9 | 0 | 9 | 0 | {} |
| AtomDoc | 4 | 4 | 0 | 4 | 0 | {} |
| AtomicDocumentDoc | 10 | 10 | 0 | 10 | 0 | {} |
| BoardDoc | 2 | 0 | 2 | 2 | 0 | {} |
| ChecklistDoc | 12 | 12 | 0 | 12 | 0 | {} |
| CliCommandDoc | 68 | 68 | 0 | 68 | 0 | {} |
| ConditionDoc | 20 | 20 | 0 | 20 | 0 | {} |
| DeskSpecDoc | 1 | 1 | 0 | 1 | 0 | {} |
| DeskStandardsDoc | 1 | 1 | 0 | 1 | 0 | {} |
| EdgeDoc | 24 | 24 | 0 | 24 | 0 | {} |
| FAQDoc | 1 | 1 | 0 | 1 | 0 | {} |
| InboxNoteDoc | 5 | 0 | 5 | 5 | 0 | {} |
| MoveDoc | 37 | 21 | 16 | 37 | 0 | {} |
| OperatorDoc | 12 | 12 | 0 | 12 | 0 | {} |
| PackagingDoc | 1 | 1 | 0 | 1 | 0 | {} |
| PillDoc | 15 | 0 | 15 | 15 | 0 | {} |
| PythonSymbolDoc | 1279 | 1279 | 0 | 1279 | 0 | {} |
| RelationTypeDoc | 15 | 15 | 0 | 15 | 0 | {} |
| RequestDoc | 2 | 0 | 2 | 2 | 0 | {} |
| RoutineDoc | 4 | 4 | 0 | 4 | 0 | {} |
| StoreReadmeDoc | 1 | 1 | 0 | 1 | 0 | {} |
| SurfaceDoc | 21 | 21 | 0 | 21 | 0 | {} |
| TaskDoc | 7 | 0 | 7 | 0 | 7 | {} |

## Diferencias de payload por causa

- **field-only-in-kimun** — 7 campos en 7 documentos (TaskDoc). Ejemplo: `TaskDoc/task-borrar-la-capa-runtime-edges-y-podar-el-surface-publico` campo `from_drawer`
  - v1: ``
  - kimun: `""`

## Documentos no migrados tras --force

Ninguno.


## Rendimiento

| medida | valor |
|---|---|
| log-lines | 1586 |
| rerun-ms | 6530 |
| objects | 82677 |
| docs-per-second | 24.2 |
| kimun-bytes | 97441769 |
| strict-ms | 62063 |
| log-bytes | 18100232 |
| force-pass-ms | 9133 |
| strict-docs | 1504 |
| `kimun --version` (exit 0) | 235 ms |
| `kimun stores check` (exit 0) | 19080 ms |
| `kimun docs list` (exit 0) | 779 ms |
| `kimun docs show arch-code-docstring-indexing-handoff` (exit 0) | 386 ms |
| `kimun stores verify` (exit 0) | 31981 ms |
| profile: bb-startup-and-load | 223 ms |
| profile: read-log-file | 147 ms |
| profile: read-all-objects | 1208 ms |
| profile: kernel-open | 1 ms |
| profile: documents-listing | 62 ms |
| profile: extract-every-document | 4596 ms |
