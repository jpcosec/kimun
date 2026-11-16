FILE:desk/tasks/task-finish-wip-leer-readers-by-class.md
---
id: task-finish-wip-leer-readers-by-class
status: draft
summary: ''
tags:
- workspace:desk
- artifact:task
current_node: ritual-execution
history: []
references:
- docs/v2/drafts/diseno-leer.md
- docs/v2/drafts/diseno-evaluar.md
- docs/v2/drafts/diseno-generar.md
- docs/v2/drafts/traspaso-2026-11-17.md
- docs/coordinacion/reglas-ejecutor.md
- docs/coordinacion/validate.sh
- AGENTS.md
depends_on:
- task-toolchain-on-robocop2-and-suite-reproduced
pills: []
files:
- src/kimun/surface/reading.cljc
- test/kimun/surface/reading_test.cljc
- test/kimun/cli/reading_test.cljc
- test/kimun/cli/declare_test.cljc
- test/kimun/cli/declare_project_test.cljc
- test/kimun/cli/mcp_test.cljc
task_type: task.code
inherits_from: []
inherit_acceptance_context: false
atoms: []
---

# Finish wip-leer readers by class

## Rationale

_Explain why this task exists or the business driver behind it._

The owner's criterion of done, 2026-11-16: kimun must ingest an advanced project such as deskops. The piece that opens it is 'readers by class': a declaration (reading (files "glob") reader-fn) whose function receives a view of each matching Markdown file (:path :front :sections :text) and declares propositions from it. Design: docs/v2/drafts/diseno-leer.md §3-§5 (this task is its 'cut 1'). The code is written on branch wip/leer (one commit, 83379739, on top of main's 1f933ab0) but its executor was cut off: three older tests fail and the trial on the real desk was never run.

## Goal

_Describe the concrete result this task must produce._

Branch wip/leer is rebased on origin/main, its tests and the whole suite pass, the readers have been run over a copy of the real deskops desk with the numbers recorded, and main is fast-forwarded to it and pushed.

## Scope

_State what is in scope and what is out of scope._

In scope: src/kimun/surface/reading.cljc, test/kimun/surface/reading_test.cljc, test/kimun/cli/reading_test.cljc (all already on the branch); the three tests that fail; the reader drafts runs/subagents/2026-11-16-leer/deskops-readers/{base,atoms,tasks,materializations}.clj (on the branch, never run); README.md and docs/v2/drafts/diseno-leer.md (add a section 'Decidido al implementar'). Out of scope: any write under ~/proyectos/SLDB_1 (read-only: copy the desk, never run sldb or deskops inside it); readers for the other desk classes; any cache.

## Implementation Path

_Outline the expected implementation route or affected surface._

1. git fetch; git worktree add /tmp/kimun-leer-wt origin/wip/leer; git rebase origin/main (on conflict keep both sides' help lines in src/kimun/cli/main.cljc). 2. Fix the three tests: on this branch the :summary of 'declare read' gains the keys :readings :read-propositions :reading-errors only when the project declares readers (docstring of src/kimun/cli/commands/declare.cljc); test/kimun/cli/declare_test.cljc line 25 and test/kimun/cli/declare_project_test.cljc line 36 compare the whole map (= {:files 2 :relations 2 :propositions N} ...) and test/kimun/cli/mcp_test.cljc does the same: first run them to see the actual map; if the extra keys appear for a project WITHOUT readers that is a bug in the code (fix the code, not the test); otherwise update the expected map. 3. Reality trial: D=$(mktemp -d); cp -r ~/proyectos/SLDB_1/deskops/desk $D/desk; mkdir $D/kimun; copy the four reader drafts into $D/kimun/; (cd $D && git init -q && git add -A && git commit -qm copy); then from $D: 'time <repo>/bin/kimun declare read --format edn' and 'declare terms', 'declare about <one task id>'. Fix readers until the reading errors are explained. 4. Write runs/subagents/<date>-leer/REPORT.md (what was done, the numbers, every point where the design was ambiguous or wrong, declared limits). 5. Validate, commit ('feat(declare): readers by class' + a separate 'chore(evidence)' commit, trailer 'Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>'), git checkout main, git merge --ff-only, push, remove the worktree and delete the branch locally and on origin.

## Validation

_List the checks required before this task can close._

- bb lint exits 0
- The directed run of kimun.surface.reading-test kimun.cli.reading-test kimun.cli.declare-test kimun.cli.declare-project-test kimun.cli.mcp-test together with twenty other namespaces has 0 failures (a sci evaluator inside Babashka can pass alone and fail in company)
- bb test: line 2 is tree-sitter pod: available and the last line has :fail 0 :error 0
- bb oracle exits 0 and bin/kimun include check is clean
- REPORT.md has, measured on the copy of the desk: Markdown files matched by each reader, propositions read (:read-propositions), reading errors (:reading-errors) with each distinct cause explained, and wall-clock seconds of declare read

## Done When

_Name the observable condition that makes the task complete._

origin/main contains the feature commit and the evidence commit, the five validation outputs are under runs/subagents/<date>-leer/coordinator/, and REPORT.md states the four measured numbers. If declare read on the desk takes more than 10 s, say so in REPORT.md: that number decides the task task-cache-of-readings-by-git-blob-hash.

FILE:instruccion
Preflight solo-TaskDoc de la tarea task-finish-wip-leer-readers-by-class: reformula el encargo tal como lo leería un ejecutor sin ningún otro contexto, lista ambigüedades con cita textual del input y da el DRY-RUN sin ejecutar nada. No ejecutes nada.

