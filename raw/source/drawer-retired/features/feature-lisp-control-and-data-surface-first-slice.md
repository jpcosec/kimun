---
id: feature-lisp-control-and-data-surface-first-slice
status: proposed
summary: First implementation macrotask for Lisp-authored control/data surfaces that compile into kernel-validated plans without becoming authority.
tags:
- workspace:desk
- artifact:feature
- system:sldb
history:
- "2026-07-29: drafted as first-slice macrotask planning feature."
references:
- also_core.md
- core_README.md
- interfaces.md
- plan_core.md
- desk/atoms/lisp-metalanguage.md
- desk/atoms/lisp-schema-language.md
- desk/atoms/lisp-macro-language.md
- desk/atoms/transaction-plan.md
- desk/atoms/query-plan.md
- desk/atoms/projection-plan.md
- desk/atoms/effect-plan.md
- docs/architecture/target-system-overview.md
- docs/architecture/spec2viz/target-components.yml
- docs/architecture/spec2viz/target-runtime.yml
depends_on:
- feature-clojure-kernel-foundation-first-slice
---

# Lisp control and data surface first slice

## Goal

Define the first implementation lane for Lisp-authored schema and macro surfaces that lower into kernel-controlled plans.

## Includes

- authored Lisp as input surface
- schema/data declarations needed by the first slice
- macro/control forms needed by the first slice
- lowering into transaction/query/projection/effect plans only through kernel validation
- no direct privileged persistence path outside the kernel

## Excludes

- standalone Lisp authority
- hidden macro mutation channels
- broad programmable runtime beyond first-slice workflows

## Must stay true

- authored Lisp is input, not canonical state
- any kernel-emitted Lisp is materialization, not authority
- all executable behavior passes through kernel validation and capabilities

## Needs design or grounding before promotion

- minimal first-slice Lisp surface area
- exact schema form set needed for Markdown roundtrip support
- exact macro/control forms needed before query/effect expansion
- whether projection/effect plan families are partially stubbed or fully exposed in the first slice

## Exit shape

A promotable implementation task set exists for the minimal Lisp-authored control/data surface required by the first slice.
