# Opsys CLI Grammar

## Purpose

This document proposes a canonical CLI grammar for the `opsys` primitive system.

The command surface should not be assembled ad hoc. It should emerge from the primitive model.

That means:

- nouns map to primitives
- verbs map to valid operations on those primitives
- state and relation verbs stay regular across the system

## Design Principles

The CLI should be:

- noun-first
- semantically regular
- recoverable from partial knowledge
- explicit about state and scope
- consistent across documentary and bridge primitives

## Core Shape

The proposed top-level grammar is:

```text
opsys <noun> <verb> [args...]
```

Examples:

```text
opsys records show issue-014
opsys routines run report-ux-issue
opsys checklists run cli-onboarding
opsys hooks test help-change-trigger
opsys references verify issue-014
opsys probes run cli-onboarding-smoke
```

## Noun Set

The first stable noun set should mirror the primitives.

### Documentary nouns

- `artifacts`
- `schemas`
- `records`
- `relations`
- `steps`
- `routines`
- `checklists`
- `rituals`
- `views`

### Control nouns

- `conditions`
- `hooks`
- `roles`
- `states`
- `transitions`
- `triggers`

### Bridge nouns

- `references`
- `constraints`
- `lints`
- `probes`
- `transforms`

## Canonical Verb Families

Not every noun supports every verb, but the family should remain shared.

### Inspect verbs

- `show`
- `list`
- `query`
- `explain`

### Authoring verbs

- `create`
- `update`
- `edit`
- `remove`

### Relation verbs

- `link`
- `unlink`
- `trace`

### Execution verbs

- `run`
- `resume`
- `complete`
- `close`

### Validation verbs

- `check`
- `verify`
- `test`
- `audit`

### State verbs

- `transition`
- `enable`
- `disable`

### Projection verbs

- `render`
- `project`
- `summarize`

## Recommended Primitive-to-Verb Mapping

### artifacts

- `create`
- `show`
- `list`
- `update`
- `link`
- `archive`

### schemas

- `show`
- `list`
- `validate`
- `diff`
- `promote`

### records

- `create`
- `show`
- `list`
- `update`
- `transition`
- `link`
- `verify`

### relations

- `link`
- `unlink`
- `show`
- `query`

### steps

- `show`
- `run`
- `skip`
- `explain`

### routines

- `create`
- `show`
- `list`
- `run`
- `resume`
- `complete`
- `review`

### checklists

- `create`
- `show`
- `list`
- `run`
- `check`
- `summarize`

### conditions

- `create`
- `show`
- `list`
- `evaluate`
- `query`

### hooks

- `create`
- `show`
- `list`
- `enable`
- `disable`
- `test`
- `trigger`

### rituals

- `create`
- `show`
- `list`
- `run`
- `close`
- `audit`

### views

- `create`
- `show`
- `list`
- `query`
- `render`

### references

- `create`
- `show`
- `list`
- `verify`
- `trace`
- `repair`

### constraints

- `create`
- `show`
- `list`
- `check`
- `violations`

### lints

- `show`
- `list`
- `run`
- `result`
- `fix`

### probes

- `create`
- `show`
- `list`
- `run`
- `result`
- `explain`

### transforms

- `create`
- `show`
- `list`
- `preview`
- `run`
- `diff`

## Recovery Rule

The grammar should support both discovery and inspection.

That means every important noun should provide:

1. `list`
2. `show`
3. at least one action verb

This avoids a common CLI failure mode where the user must already know the exact object name before they can discover anything.

## Scope Rule

Where scope matters, the CLI should express it explicitly and consistently.

Recommended scope flags:

- `--local`
- `--global`
- `--linked`
- `--store PATH`
- `--project PATH`

The meaning of each flag should remain stable across noun groups.

If a flag means something narrower than its label suggests, rename it.

## State Rule

Where lifecycle matters, state should not hide inside custom verbs.

Prefer:

```text
opsys records transition issue-014 --to active
opsys hooks enable help-change-trigger
opsys rituals close weekly-ux-review
```

Avoid one-off verbs when a shared transition or enable/disable pattern is enough.

## Bridge Rule

Documentary-to-code linkage should use the same grammar discipline.

Prefer:

```text
opsys references verify issue-014
opsys constraints check cli-help-coverage
opsys lints run broken-references
opsys probes run onboarding-path
opsys transforms preview routine-to-checklist
```

## Examples

### Report a UX issue

```text
opsys records create --schema ux-issue-report
opsys records update ux-issue-001
opsys references create ux-issue-001 --target src/sldb/cli/parser.py
opsys references verify ux-issue-001
opsys records transition ux-issue-001 --to active
```

### Run a CLI UX routine

```text
opsys routines run cli-onboarding-walkthrough
opsys checklists run cli-onboarding-checks
opsys probes run cli-onboarding-smoke
opsys records create --schema ux-test-report
```

### Close a ritualized review

```text
opsys rituals run weekly-onboarding-review
opsys views query open-ux-issues
opsys hooks test onboarding-surface-change
opsys rituals close weekly-onboarding-review
```

## Smallest Credible First Slice

The smallest CLI slice that still proves the grammar is:

1. `records list/show/create/update/transition`
2. `routines list/show/run/resume/complete`
3. `checklists show/run/summarize`
4. `references create/show/verify`
5. `probes show/run/result`
6. `views show/query`

That is enough to support documentary operations plus one real code-bridge path.

## Open Questions

1. Should noun groups stay fully primitive-based, or should some product-facing composite nouns exist as shortcuts?
2. Which verbs should be reserved globally to avoid synonym drift?
3. Should `run`, `check`, and `verify` have strict semantic distinctions enforced by policy?
4. Which bridge nouns should ship in the first CLI slice versus stay internal until stable?
