---
id: feature-security-threat-model-and-capabilities
status: proposed
summary: Define tclojure boundaries, capability controls, effect isolation, and threat-model assumptions across kernel and external surfaces.
tags:
- workspace:desk
- artifact:feature
- system:sldb
history:
- "2026-07-29: drafted as cross-cutting macrotask planning feature."
references:
- interfaces.md
- libraries_core.md
- desk/atoms/threat-model.md
- desk/atoms/capability-model.md
- desk/atoms/hook-runtime.md
- docs/architecture/spec2viz/target-components.yml
- docs/architecture/spec2viz/target-runtime.yml
depends_on:
- feature-effect-system-and-automation
---

# Security threat model and capabilities

## Goal

Define the macrotask for explicit tclojure boundaries and capability enforcement across product surfaces and external effects.

## Includes

- threat model
- capability gates
- effect isolation
- external-surface tclojure boundaries

## Excludes

- security by undocumented convention
- broad auth product design unless explicitly scoped

## Needs design or grounding before promotion

- attacker model
- local vs remote tclojure assumptions
- capability grant/review model
- auditability requirements
