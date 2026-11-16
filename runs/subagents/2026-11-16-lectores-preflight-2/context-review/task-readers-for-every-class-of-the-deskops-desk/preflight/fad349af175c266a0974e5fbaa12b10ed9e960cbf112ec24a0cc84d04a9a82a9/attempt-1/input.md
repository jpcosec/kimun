FILE:desk/tasks/task-readers-for-every-class-of-the-deskops-desk.md
---
sldb:
  by: agent
  at: '2026-11-16T23:32:27Z'
  authority: generated
  via: save_document_payload
id: task-readers-for-every-class-of-the-deskops-desk
status: draft
summary: ''
tags:
- workspace:desk
- artifact:task
current_node: ritual-execution
history: []
references: []
depends_on: []
pills: []
files: []
task_type: task.docs
inherits_from: []
inherit_acceptance_context: false
atoms: []
from_drawer: desk/drawer/tasks/task-readers-for-every-class-of-the-deskops-desk.md
---

# Readers for every class of the deskops desk

## Rationale

_Explain why this task exists or the business driver behind it._

The owner's criterion of done (2026-11-16) asks kimun to ingest an advanced project such as deskops. kimun is a Clojure tool run by Babashka (bb). A 'reader' is a declaration, written in a file kimun/NAME.clj of the observed project, of the form (reading (files "glob") (fn [doc] ...)): the function gets a view of each matching Markdown file (doc has :path, :front = the YAML frontmatter as a map with string keys or nil, :sections, :text) and declares propositions (R A B) from it; the observed project is never edited. Cut 1 (commit 2e350e22, report runs/subagents/2026-11-16-leer/REPORT.md) reads three classes of the deskops desk: atoms, materializations and tasks (2679 propositions from 410 of its 920 Markdown files, 0 errors, 2.2 s). This task is cut 2 of docs/v2/drafts/diseno-leer.md section 5: readers for the remaining classes.

## Goal

_Describe the concrete result this task must produce._

A set of reader declarations under runs/subagents/YYYY-MM-DD-lectores-desk/readers/ (YYYY-MM-DD = the day of the run) such that, run over a copy of the desk /home/robocop/proyectos/SLDB_1/deskops/desk, every Markdown file of the desk that has YAML frontmatter is the source of at least one proposition, with the numbers measured and written in a REPORT.md.

## Scope

_State what is in scope and what is out of scope._

In scope: new files runs/subagents/YYYY-MM-DD-lectores-desk/readers/*.clj, starting from a copy of the four of cut 1 (runs/subagents/2026-11-16-leer/deskops-readers/base.clj, atoms.clj, tasks.clj, materializations.clj; leave those originals untouched), and REPORT.md plus the command outputs in the same run folder. Classes to add, by folder of the desk and the frontmatter keys measured on 2026-11-16: desk/contexts (pills: id, tags); desk/drawer/tasks (id, status, owner_repo, node_role, depends_on, requires, parent_id, children: read them with a class DrawerTask, and change the tasks reader's glob so a drawer task is not also a Task); desk/inbox (no id key: use the file name without .md as the id; kind, sender_project, target_project, status, acknowledged_by); desk/lifecycle (two shapes: rules with condition, subject, severity, moment; and relation documents with source_id, target_id, relation_type: declare for the document three propositions, relation-source, relation-target and relation-type, do not try to create a relation named after relation_type); desk/registry (id, path, status); desk/reviews (id, kind, fact, subject or subject_ref, engine, actor, verdict); desk/rituals, desk/roles, desk/runtimes (look at their frontmatter first and read id plus every key that names another document). References: values like doc:TaskCodeDoc:task-x@sha256:abc or RitualDoc:ritual-closeout must become the bare id (task-x, ritual-closeout) through one helper function defined in base.clj and used by every reader, so the same thing is one term. Every reader starts with (when-let [f (:front doc)] ...) so a file without frontmatter declares nothing instead of failing. Out of scope: any change under src/ or test/ of kimun (if a bug or limit of kimun blocks a class, stop that class and describe it in REPORT.md with the command and its output); any write under /home/robocop/proyectos/SLDB_1 (read-only; never run sldb or deskops anywhere); comparing with sldb's edge index (a later task); any cache. Rules that settle every class, so nothing is left to invent: (a) each reader declares (isa ID Class) with these class names: Pill (desk/contexts), DrawerTask, InboxNote, LifecycleRule, LifecycleRelation, Repository (desk/registry), Review, Ritual, Role, Runtime; (b) (tagged ID tag) for each value of tags; (c) for every other frontmatter key listed above, or found in rituals, roles and runtimes, whose value is a non-empty string or a list of strings, one proposition per value with a relation named has-KEY (key in kebab-case, for example has-target-project), declared in base.clj; keys whose value is a map or a long free text (condition, message, where, reason, attributes, validation) are skipped and named in REPORT.md; (d) a value is passed through the reference helper always: it only changes values of the forms doc:Model:id@sha256:..., doc:Model:id and Model:id; (e) in desk/lifecycle a file is a LifecycleRelation when its frontmatter has the key source_id, otherwise a LifecycleRule; (f) in reviews use subject when it is non-empty, otherwise subject_ref, as the value of has-subject; (g) the goal is zero files with frontmatter and no proposition; a remaining file is acceptable only when kimun itself cannot read it, and then it is named with the command and output that shows it.

## Implementation Path

_Outline the expected implementation route or affected surface._

Work in the clone /home/robocop/proyectos/kimun on branch main (git pull --ff-only first; if it has local changes stop and report). Every kimun call is: flock /tmp/kimun-bb.lock env KIMUN_POD=/home/robocop/proyectos/kimun/pods/treesitter/target/release/pod-kimun-treesitter /home/robocop/bin/memcap 3 /home/robocop/proyectos/kimun/bin/kimun ARGS (PATH must include $HOME/.local/bin). 1. Read docs/v2/drafts/diseno-leer.md (sections 3 and 7), runs/subagents/2026-11-16-leer/REPORT.md and the four readers of cut 1. 2. D=$(mktemp -d); cp -r /home/robocop/proyectos/SLDB_1/deskops/desk $D/desk; mkdir $D/kimun; copy your readers to $D/kimun/; (cd $D && git init -q && git add -A && git -c user.name=x -c user.email=x@x commit -qm copy). The readers are found only if committed or present under $D/kimun; re-copy them after each edit. 3. From $D run '... bin/kimun declare read --format edn > read.edn' and time it; :data :summary has :readings, :read-propositions and :reading-errors, and :data :reading-errors lists each failure with its file. Each read proposition carries :source {:path FILE}. 4. Coverage: with a short bb or python3 script kept in the run folder, list every desk/**/*.md of the copy, mark whether it has frontmatter and whether some proposition of read.edn has it as :source :path, and write coverage.txt: totals per top folder of the desk and the list of files with frontmatter and no proposition. 5. Iterate until that list is empty and every reading error is fixed in a reader or explained. 6. Sanity of asking: '... bin/kimun declare about ID --format edn' for one id of each new class returns rows; save the outputs in about.txt. 7. Write REPORT.md in Spanish. 8. Commit only the run folder with the message 'chore(evidence): readers for every class of the deskops desk' and the trailer 'Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>'. Do not push; the coordinator reviews and pushes.

## Validation

_List the checks required before this task can close._

- coverage.txt shows 0 Markdown files with frontmatter and no proposition, or names each remaining file with the reason
- read.edn has :reading-errors 0, or REPORT.md names each distinct cause with its count
- REPORT.md has per reader: its glob, files matched and propositions read; the total :read-propositions; the wall-clock seconds of declare read; the files without frontmatter per folder (not read by design)
- REPORT.md states how many distinct raw reference forms were normalized to a bare id and shows three examples before and after
- about.txt has one declare about per new class and each returns at least one row
- git status in /home/robocop/proyectos/kimun shows no change outside the run folder, and nothing under /home/robocop/proyectos/SLDB_1 was modified (git -C /home/robocop/proyectos/SLDB_1/deskops status --short gives the same output before and after; save both in the run folder)

## Done When

_Name the observable condition that makes the task complete._

The run folder runs/subagents/YYYY-MM-DD-lectores-desk/ is committed on main with readers/*.clj, read.edn, coverage.txt, about.txt, the coverage script and REPORT.md, and the six validation checks hold. If declare read takes more than 10 s, REPORT.md says so: that number decides the drawer entry task-cache-of-readings-by-git-blob-hash.

FILE:instruccion
Preflight solo-TaskDoc de la tarea task-readers-for-every-class-of-the-deskops-desk: reformula el encargo tal como lo leería un ejecutor sin ningún otro contexto, lista ambigüedades con cita textual del input y da el DRY-RUN sin ejecutar nada. No ejecutes nada.

