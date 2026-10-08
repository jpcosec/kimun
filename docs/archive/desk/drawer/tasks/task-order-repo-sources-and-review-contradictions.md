> **Archivado 2026-10-08 — planning-era, pre-v2.** El drawer vigente se describe en `docs/v2/05-estado.md` §6; este backlog no entra al store `.kimun`.

---
id: task-order-repo-sources-and-review-contradictions
status: open
summary: Define source-authority order for this planning workspace and record source contradictions that need normalization.
tags:
- workspace:desk
- artifact:task
- system:sldb
history: ["2026-07-29: opened for planning-only source ordering and contradiction review."]
references:
- desk/atoms/canonical-ast.md
- desk/atoms/decision-v1-parity-before-scope-expansion.md
- desk/atoms/decision-clojure-core-with-minimal-python.md
- desk/atoms/decision-graph-store-over-yaml-indexes.md
- desk/atoms/decision-links-and-anchors-are-canonical.md
- docs/architecture/target-system-overview.md
pills:
- desk/contexts/pill-planning-contracts.md
files:
- docs/architecture/contracts/repo-source-order-and-contradiction-review.md
checklists: []
---

# Order repo sources and review contradictions

## Rationale

_Explain why this task exists or the business driver behind it._

The workspace already has frozen Phase 1 contracts, target architecture notes, historical reports, and workflow artifacts. A single ordering rule is needed so planning stays deterministic and historical docs do not override current contract truth.

## Goal

_Describe the concrete result this task must produce._

Define the authority order for repo sources and identify current contradictions or stale statements between workspace docs.

## Scope

_State what is in scope and what is out of scope._

- **In-Scope**:
  - source-authority order for this planning worktree
  - contradictions between README/FAQ/desk/architecture/reports
  - Phase 1 scope ambiguities that could misroute planning
- **Out-of-Scope**:
  - runtime implementation
  - code parity claims
  - file moves or non-planning cleanup

## Implementation Path

_Outline the expected implementation route or affected surface._

1. Recover workspace guardrails and desk routing.
2. Compare current contract docs, architecture docs, and historical reports.
3. Write one contract artifact with precedence rules, contradictions, and normalization decisions.

## Validation

_List the checks required before this task can close._

- Does the artifact cite governing atoms and target architecture docs?
- Does it define one unambiguous source order?
- Does it mark stale or lower-authority sources explicitly?
- Does it avoid implementation claims?

## Done When

_Name the observable condition that makes the task complete._

The `repo-source-order-and-contradiction-review.md` contract exists and captures the authority order plus contradiction review for the current planning workspace.

