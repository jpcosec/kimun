> **Superseded (2026-09-06).** Governed by `docs/v2/05-estado.md (pista S) and docs/v2/08-distribucion.md §1 (A4)`: the Python evaluator, its tests and the 13 anchors move to this repo in S7; `legos/knowledge` becomes `provenance`.

# Core Source Downstream Coverage Contract

## Purpose and governing sources

This contract records which concepts from the six root core source docs must exist downstream in concise atoms and aligned spec2viz views.

Governing sources:
- `also_core.md`
- `core_README.md`
- `diagramas_core.md`
- `interfaces.md`
- `libraries_core.md`
- `plan_core.md`

Aligned architecture surfaces:
- `desk/atoms/`
- `docs/architecture/spec2viz/target-components.yml`
- `docs/architecture/spec2viz/target-runtime.yml`
- `docs/architecture/spec2viz/target-store-graph.yml`
- `docs/architecture/spec2viz/target-anchoring.yml`

## In-scope

- downstream representation of missing operational architecture concepts
- concise atom decomposition where long concepts need multiple atoms
- spec2viz alignment for runtime, store, and boundary surfaces

## Explicit non-goals

- no runtime implementation claims
- no code validation claims
- no Phase 2 scope expansion
- no replacement of the six root core docs as primary authority

## Required downstream concepts

The following concepts must exist downstream.

### Atoms

- `conformance-suite`
- `golden-fixture`
- `reference-behavior`
- `identity-stability-rules`
- `node-reconciliation`
- `failure-model`
- `degraded-mode`
- `corruption-state`
- `compatibility-surface`
- `migration-unit`
- `garbage-collection`
- `retention-policy`
- `backup-export`
- `store-recovery`
- `observability-surface`
- `threat-model`
- `transaction-plan`
- `query-plan`
- `projection-plan`
- `effect-plan`

### Spec2viz surfaces

- conformance surface
- capability gate surface
- plan-compilation surface
- observability surface
- migration/compatibility surface
- backup/recovery surface
- garbage-collection/retention surface
- failure/degraded/corruption state surface

## Mapping rules

- Keep atoms short and single-purpose.
- Split long operational concepts into several atoms instead of one long atom.
- Use spec2viz to show component/runtime/store placement, not prose duplication.
- Treat these downstream surfaces as aligned materializations of the six root core docs.

## Downstream constraints

- No new atom should overload conformance, failure, recovery, and compatibility into one note.
- No spec2viz view should imply Python-centered authority.
- Backup/recovery and observability must appear as first-class architecture concerns, not incidental notes.
- Plan compilation must remain explicit: Lisp compiles to plan families before kernel execution.
