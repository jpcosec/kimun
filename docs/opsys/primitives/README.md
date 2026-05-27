# Opsys Primitives

## Purpose

This document defines a small primitive set for building an operational surface whose job is to standardize procedures, documentation, and knowledge.

The goal is not to model every workflow detail at once. The goal is to define a stable base that can compose into reporting, task management, review flows, checklists, routines, and rituals.

This primitive set is not only static. Each primitive should also be actionable through a CLI surface.

The intended result is a semantic actionable machine for development: a system where operational objects are not only documented, but can also be inspected, created, linked, advanced, checked, and triggered through explicit commands.

The current focus is the documentary surface first. That means the first operational layer is made of structured documents and document-like records.

But that documentary surface should not stay isolated. It should eventually connect to code through:

- references
- linting
- testing
- automatic transforms
- consistency checks between documentary and code surfaces

## Design Goal

An operational surface should let a team do four things well:

1. store durable knowledge
2. run repeatable procedures
3. evaluate quality and readiness
4. trigger the right behavior under the right conditions

To support real development work, it should also do a fifth thing:

5. expose those capabilities as stable semantic actions

And a sixth:

6. bridge documentary and code surfaces without collapsing them into one undifferentiated layer

That suggests four primitive families:

- knowledge primitives
- execution primitives
- control primitives
- projection primitives

And one cross-cutting requirement:

- every first-class primitive should have an action surface

## Static And Actionable Aspects

Each primitive should be understood through two aspects.

### Static aspect

What the primitive is.

This is the schema and representation side:

- fields
- state
- structure
- relations
- stored evidence

### Actionable aspect

What can be done with the primitive.

This is the CLI and operational side:

- create
- show
- list
- update
- link
- check
- run
- close
- trigger
- project

The primitive is only complete when both aspects are defined.

## Documentary First, Code Connected

The documentary surface is the first operational substrate because it is easier to author, inspect, review, diff, and standardize.

That gives the system an initial durable layer for:

- procedures
- reports
- issues
- tasks
- decisions
- guides
- checklists

But development work eventually needs documentary-code alignment.

That alignment should happen through explicit bridges, not by treating prose and code as the same thing.

The right model is:

- documentary records express intent, policy, workflow, evidence, and declared structure
- code expresses implementation, executable behavior, and runtime constraints
- bridges connect the two and make mismatches visible

## Bridge Primitives

These are likely cross-surface primitives rather than purely documentary ones.

### Reference

A `Reference` links one documentary or operational object to one code object or code region.

Examples:

- a task references a CLI parser file
- a ritual references a test suite
- a checklist item references a linter rule
- a report references a function, class, or command implementation

Why it exists:

- to connect intent to implementation
- to support traceability
- to make navigation bidirectional

Action surface:

- `link-reference`
- `show-references`
- `verify-references`

### Constraint

A `Constraint` expresses a rule that should hold across documentary and code surfaces.

Examples:

- every first-class CLI noun must have help coverage
- every routine with a hook must define a trigger
- every documented command example should resolve to a real command path

Why it exists:

- to formalize alignment rules
- to move expectations out of implicit team memory

Action surface:

- `show-constraint`
- `check-constraint`
- `list-violations`

### Transform

A `Transform` converts one surface representation into another while preserving meaning.

Examples:

- generate a CLI help artifact from structured command definitions
- derive checklist stubs from routine definitions
- materialize code annotations from documentary records
- build documentary views from code metadata

Why it exists:

- to reduce duplication
- to synchronize surfaces intentionally
- to make semantic updates cheaper

Action surface:

- `show-transform`
- `run-transform`
- `preview-transform`
- `diff-transform`

### Lint

A `Lint` is an executable quality rule over one or more surfaces.

Examples:

- broken references
- undocumented command groups
- stale examples
- routine/checklist mismatch

Why it exists:

- to make drift visible early
- to operationalize standards

Action surface:

- `run-lint`
- `list-lints`
- `show-lint-result`

### Probe

A `Probe` is a test or executable check that validates whether documentary claims and code behavior still align.

Examples:

- run documented CLI examples and compare output shape
- verify that documented command groups exist
- verify that referenced files and symbols resolve

Why it exists:

- to test cross-surface truth
- to reduce silent divergence between docs and implementation

Action surface:

- `run-probe`
- `list-probes`
- `show-probe-result`

## Semantic Actionability

The CLI should not be a loose bag of commands. It should be the action surface of the primitive system.

That means:

- nouns should map to primitives
- verbs should map to valid primitive actions
- state transitions should be explicit
- relations should be navigable
- checks should be executable
- routines should be runnable
- hooks should be inspectable and triggerable
- views should be queryable projections over records and state

This is why the system can be described as a semantic actionable machine.

It is semantic because the objects have explicit meaning and typed relations.

It is actionable because those meanings are bound to verbs and transitions, not only to documentation.

It is a machine because the user can move through a structured operational state-space instead of improvising each workflow from scratch.

## Primitive Families

## Knowledge Primitives

### Artifact

An `Artifact` is any durable operational object.

Examples:

- task
- issue
- report
- decision
- guide
- spec
- note

Why it exists:

- to make knowledge addressable
- to give operations a durable substrate
- to let procedures and evidence point somewhere concrete

Action surface:

- `create`
- `show`
- `list`
- `update`
- `archive`
- `link`

### Schema

A `Schema` is the structural contract for an artifact.

It defines:

- required fields
- optional fields
- allowed state vocabulary
- relation types
- evidence expectations

Why it exists:

- to standardize shape
- to make records comparable
- to keep operational artifacts queryable

Action surface:

- `show-schema`
- `validate-schema`
- `diff-schema`
- `promote-schema`

### Record

A `Record` is a concrete instance of a schema.

Examples:

- one UX issue report
- one release checklist
- one onboarding task

Why it exists:

- to represent real operational state
- to turn abstract schemas into inspectable units

Action surface:

- `create`
- `show`
- `list`
- `update`
- `transition`
- `attach-evidence`
- `relate`

### Relation

A `Relation` is a typed connection between records.

Examples:

- `depends_on`
- `blocks`
- `derived_from`
- `evidences`
- `supersedes`
- `validated_by`

Why it exists:

- to turn isolated records into operational knowledge
- to make causality and dependency explicit

Action surface:

- `link`
- `unlink`
- `show-links`
- `query-links`

### Evidence

`Evidence` is the supporting material attached to a record.

Examples:

- transcript
- screenshot
- command output
- diff
- validation result
- reference document

Why it exists:

- to support trust
- to preserve context
- to reduce argument from memory alone

Action surface:

- `attach`
- `show`
- `list`
- `verify`

## Execution Primitives

### Step

A `Step` is the smallest executable instruction.

Examples:

- run `sldb -h`
- inspect available models
- compare expected and actual output

Why it exists:

- to make procedures decomposable
- to give workflows atomic resolution

Action surface:

- `show-step`
- `run-step`
- `skip-step`
- `explain-step`

### Sequence

A `Sequence` is an ordered iterable of steps.

Why it exists:

- to represent procedures without yet adding role, trigger, or lifecycle semantics

Action surface:

- `show-sequence`
- `run-sequence`
- `resume-sequence`
- `next-step`
- `previous-step`

### Routine

A `Routine` is a reusable sequence with a defined purpose and expected outcome.

Examples:

- report a UX issue
- test CLI onboarding discoverability
- review a proposal before implementation

Why it exists:

- to standardize repeated work
- to make good practice reusable

Action surface:

- `create-routine`
- `list-routines`
- `show-routine`
- `run-routine`
- `resume-routine`
- `complete-routine`
- `review-routine`

### Checklist

A `Checklist` is an iterable set of checks that evaluate whether something is ready, correct, complete, or safe.

Important distinction:

- a routine answers: what do we do
- a checklist answers: what must be true

Why it exists:

- to standardize quality control
- to separate execution from verification

Action surface:

- `show-checklist`
- `run-checklist`
- `check`
- `mark-pass`
- `mark-fail`
- `summarize-checklist`

## Control Primitives

### Condition

A `Condition` is a reusable predicate over context or state.

Examples:

- no local store exists
- help text changed
- onboarding surface was modified
- issue severity is high

Why it exists:

- to make applicability explicit
- to reuse the same logic across workflows

Action surface:

- `show-condition`
- `evaluate-condition`
- `list-matches`

### Hook

A `Hook` is a condition attached to an action.

Shape:

- if condition holds, perform or require action

Examples:

- when CLI help changes, trigger UX CLI testing
- when a release touches onboarding surfaces, require checklist completion

Why it exists:

- to turn passive rules into operational behavior
- to support automation or semi-automation

Action surface:

- `show-hook`
- `list-hooks`
- `enable-hook`
- `disable-hook`
- `test-hook`
- `trigger-hook`

### Role

A `Role` is the actor type responsible for execution, review, ownership, or closure.

Examples:

- reporter
- maintainer
- reviewer
- release owner

Why it exists:

- to standardize responsibility
- to reduce ambiguity around ownership

Action surface:

- `show-role`
- `assign-role`
- `list-owned`

### State

A `State` is controlled lifecycle vocabulary.

Examples:

- draft
- active
- blocked
- done
- deprecated

Why it exists:

- to make operational progress visible
- to constrain workflow drift

Action surface:

- `show-state`
- `list-by-state`
- `transition`

### Transition

A `Transition` is an allowed movement between states.

Examples:

- draft -> active
- active -> blocked
- active -> done

Why it exists:

- to give state discipline
- to make progression rules explicit

Action surface:

- `show-transitions`
- `validate-transition`
- `apply-transition`

### Trigger

A `Trigger` is the event or cadence that activates a routine, checklist, or ritual.

Examples:

- before release
- after CLI surface change
- on issue report creation
- every Friday

Why it exists:

- to standardize activation timing

Action surface:

- `show-trigger`
- `list-triggered`
- `fire-trigger`

### Ritual

A `Ritual` is a routine with explicit trigger, role, and closure semantics.

It is not just repetition. It is socially meaningful repetition.

Examples:

- weekly onboarding-friction review
- pre-release CLI UX review

Why it exists:

- to stabilize recurring operational behavior
- to attach meaning, cadence, and accountability to repeated work

Action surface:

- `create-ritual`
- `show-ritual`
- `run-ritual`
- `close-ritual`
- `audit-ritual`

## Projection Primitives

### View

A `View` is a projection over records, states, or relations.

Examples:

- board
- inbox
- open issues by domain
- routines by trigger
- blocked tasks by owner

Why it exists:

- to make the system navigable
- to expose the right slice for the current activity

Action surface:

- `show-view`
- `list-views`
- `query-view`
- `render-view`

## Minimal Core

If the system must start small, the smallest credible primitive set is:

1. `Artifact`
2. `Schema`
3. `Record`
4. `Step`
5. `Routine`
6. `Condition`
7. `Relation`
8. `View`

From that base, the rest can be derived later.

## Recommended First-Class Core

If the system is allowed a slightly richer surface, the recommended first-class primitives are:

1. `Artifact`
2. `Record`
3. `Relation`
4. `Step`
5. `Routine`
6. `Condition`
7. `Checklist`
8. `Hook`
9. `Ritual`
10. `View`

Treat `Schema`, `Role`, `State`, `Transition`, `Trigger`, and `Evidence` as essential companion primitives.

For the documentary-first slice, keep `Reference`, `Constraint`, `Transform`, `Lint`, and `Probe` as bridge primitives that may become first-class once the code linkage layer starts shipping.

## Composition Rules

The key compositions are:

- `Document` = `Artifact` + `Schema`
- `IssueReport` = `Record` of a reporting schema
- `Procedure` = `Sequence` of `Step`
- `Routine` = `Procedure` + `Outcome` + reusable purpose
- `Checklist` = iterable collection of checks over conditions
- `Hook` = `Condition` + `Action`
- `Ritual` = `Routine` + `Trigger` + `Role` + closure semantics
- `Board` = `View` over records with state
- `CodeLinkedRecord` = `Record` + `Reference`
- `CrossSurfaceRule` = `Constraint` + `Lint` or `Probe`
- `ProjectionTransform` = `Transform` over documentary or code records

Operationally, each composition should also expose its own verb family.

Examples:

- `IssueReport` should support create, inspect, attach-evidence, and close
- `Routine` should support run, resume, inspect-progress, and complete
- `Checklist` should support run, evaluate, summarize, and gate
- `Ritual` should support trigger, run, review, and close
- `CodeLinkedRecord` should support show-references, verify-references, and trace-to-code
- `CrossSurfaceRule` should support check, report-violations, and explain-remediation

## Important Distinctions

### Routine vs Checklist

- routine: what to do
- checklist: what must be true

### Routine vs Ritual

- routine: repeatable procedure
- ritual: routine with trigger, meaning, cadence, and closure

### Condition vs Hook

- condition: reusable predicate
- hook: condition bound to an action

### Artifact vs Record

- artifact: the operational kind
- record: one concrete instance of that kind

## Three-Layer Model

To avoid conceptual drift, keep these layers distinct.

### Knowledge Layer

What is true, known, observed, or decided.

Uses:

- artifact
- schema
- record
- relation
- evidence
- reference

### Procedure Layer

What people do.

Uses:

- step
- sequence
- routine
- checklist
- transform

### Control Layer

When things apply, who owns them, and how they move.

Uses:

- condition
- hook
- role
- state
- transition
- trigger
- ritual
- constraint
- lint
- probe

## Example: UX Issue Reporting

One instance of UX issue reporting can be composed as:

- artifact: `IssueReport`
- schema: report fields such as `what`, `why`, `where`, `how_fix`, `how_not_fix`
- record: one concrete CLI UX issue
- evidence: command transcript and references
- routine: gather transcript, state user intent, compare expected vs actual, propose fix direction
- checklist: report includes reproduction, impact, expected behavior, actual behavior, acceptance criteria
- hook: if issue affects onboarding surface, require CLI UX test routine
- view: open UX issues by domain
- references: parser, help, resolver, and command implementation files
- probe: verify whether documented command paths still match runtime behavior

## Example: CLI UX Testing

One instance of CLI UX testing can be composed as:

- artifact: `UXTestReport`
- routine: run a guided first-use or partial-knowledge walkthrough
- steps: execute likely commands in likely order
- conditions: local store present or absent, shared/global expectations, doc/runtime divergence
- checklist: can the user discover the next command, recover from failure, understand scope
- hook: if help text changes, run the routine
- ritual: pre-release onboarding-path review
- references: CLI parser, help text source, README, and tests
- lint: stale help examples and deprecated surfaced commands
- probe: run documentary examples against the CLI and compare expected shapes

## Direction For Opsys

The likely `opsys` direction is not just a document library. It is a primitive system for operational composition.

One good framing is:

- `Step` as the atomic instruction primitive
- `Condition` as the atomic evaluative primitive
- `Routine` as iterable instructions
- `Checklist` as iterable checks
- `Hook` as reusable conditional trigger logic
- `Ritual` as a routine with hooks, cadence, ownership, and closure

On top of that documentary layer, add bridge primitives that connect to code:

- `Reference` for traceability
- `Constraint` for declared cross-surface rules
- `Lint` for cheap frequent enforcement
- `Probe` for executable alignment checks
- `Transform` for controlled synchronization

But the important extension is this:

- each primitive must be both representable and operable
- the CLI is the operational grammar of the primitive system
- the command tree should emerge from primitive nouns and primitive verbs
- documentary and code surfaces should remain distinct but interoperable

That lets the system standardize:

- procedures
- documentation
- knowledge
- validation
- escalation
- recurring operational review

## Command Grammar Direction

One strong CLI direction is:

- noun-first command groups for primitives
- shared verb families across primitives
- explicit transition and query verbs where state matters

Example shape:

```text
opsys artifacts create
opsys records show
opsys routines run
opsys checklists run
opsys hooks test
opsys rituals close
opsys views query
opsys references verify
opsys lints run
opsys probes run
opsys transforms preview
```

This keeps the surface semantically regular.

The user learns:

- what kind of thing they are acting on
- what class of action is valid on it
- how to inspect or advance operational state

## Primitive Capability Matrix

The first credible matrix is:

| Primitive | Static | Inspect | Create | Update | Run | Check | Relate | Transition | Project |
|-----------|--------|---------|--------|--------|-----|-------|--------|------------|---------|
| Artifact  | yes | yes | yes | yes | no  | no  | yes | maybe | yes |
| Schema    | yes | yes | maybe | yes | no  | yes | no  | maybe | no  |
| Record    | yes | yes | yes | yes | no  | yes | yes | yes | yes |
| Step      | yes | yes | yes | yes | yes | maybe | no  | maybe | no  |
| Routine   | yes | yes | yes | yes | yes | yes | yes | yes | yes |
| Checklist | yes | yes | yes | yes | yes | yes | yes | yes | yes |
| Condition | yes | yes | yes | yes | no  | yes | yes | maybe | yes |
| Hook      | yes | yes | yes | yes | yes | yes | yes | yes | yes |
| Ritual    | yes | yes | yes | yes | yes | yes | yes | yes | yes |
| View      | yes | yes | maybe | yes | maybe | maybe | yes | no  | yes |
| Reference | yes | yes | yes | yes | no  | yes | yes | no  | yes |
| Constraint| yes | yes | yes | yes | no  | yes | yes | maybe | yes |
| Transform | yes | yes | yes | yes | yes | yes | yes | maybe | yes |
| Lint      | yes | yes | yes | yes | yes | yes | yes | maybe | yes |
| Probe     | yes | yes | yes | yes | yes | yes | yes | maybe | yes |

## Actionability Rule

No first-class primitive should be introduced unless these questions can be answered:

1. What is its schema shape?
2. How is it inspected?
3. How is it created?
4. How is it advanced or updated?
5. How is it related to other primitives?
6. What checks can be executed on it?
7. What view exposes it?

For bridge primitives, also ask:

8. What documentary surface does it touch?
9. What code surface does it touch?
10. How is drift detected between them?

If those questions do not yet have answers, the concept may still be useful, but it is not ready to become a first-class operational primitive.

## Open Design Questions

The next questions to settle are:

1. Should `Checklist` be modeled as a sequence of `Condition` objects, or as a sequence of richer `Check` objects that each wrap a condition and result format?
2. Should `Ritual` inherit from `Routine`, or should it compose a routine plus trigger metadata?
3. Should `Hook` always execute an action, or can it also impose a gate or requirement without full automation?
4. Which primitives should be mandatory in the first `opsys` slice, and which should stay conceptual until needed?
5. Which shared verb family should be canonical across primitives: `show/list/create/update/run/check/link/transition/query`, or a different set?
6. At what point do `Reference`, `Constraint`, `Lint`, `Probe`, and `Transform` become first-class instead of remaining bridge concepts?
