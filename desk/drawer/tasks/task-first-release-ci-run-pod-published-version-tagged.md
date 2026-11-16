---
id: task-first-release-ci-run-pod-published-version-tagged
status: deferred
summary: ''
tags:
- workspace:desk
- artifact:task
- source:inbox
current_node: ''
history: []
references: []
depends_on: []
pills: []
files: []
task_type: ''
inherits_from: []
inherit_acceptance_context: false
atoms: []
---

# First release CI run pod published version tagged

## Rationale

_Explain why this task exists or the business driver behind it._

Promoted from the inbox note `desk/inbox/20261116-145647-suggestion-first-release-ci-run-pod-published-version-tagged.md`.

## Goal

_Describe the concrete result this task must produce._

Triage and resolve the inbox message promoted from `desk/inbox/20261116-145647-suggestion-first-release-ci-run-pod-published-version-tagged.md`.

## Scope

_State what is in scope and what is out of scope._

_Describe the incoming message with enough evidence to triage._

Rationale: Approved plan stage 6. The release CI was written and never executed; release.edn has :sha256 nil; only linux-amd64 was built and tested.

Goal: An installable, tagged kimun.

Scope: .github/workflows/, scripts/release.clj, release.edn, install docs.

Implementation Path: Run the CI, publish the pod, pin its hash, test the other platforms, document install.

Done When: The bundle installs on a clean machine and the tour runs.

Task type when authored: task.code

Why it is in the drawer: created as a task on 2026-11-16 and rejected by the cold-context review (desk/reviews/task-first-release-ci-run-pod-published-version-tagged/preflight-1.md lists what is missing). Author it in full with deskops add task --from-drawer when its turn comes.

## Implementation Path

_Outline the expected implementation route or affected surface._

To be defined when the task is promoted.

## Validation

_List the checks required before this task can close._

- 

## Done When

_Name the observable condition that makes the task complete._

The message is resolved, answered, or promoted into active work.
