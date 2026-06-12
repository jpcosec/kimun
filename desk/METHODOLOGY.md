# Workflow Policy

> **No task is complete without testing.**

## Quick Links

- [Task Management](./tasks/Board.md) - Active tasks
- [Pills](./pills/) - Context pills

---

## The Desk Surface Model

```
desk/
  tasks/       # Active work surface. Tasks deleted when resolved.
  pills/       # Context pills bound to tasks. Audited after each step.
  drawers/     # Local unclear points, questions, suggestions, and task candidates.
  inbox/       # Cross-project requests routed by deskops, not local staging.
```

**Rule:** Desk surfaces should reference only durable repo context: task files, pills, local drawer notes, cross-project inbox requests, source files, tests, docs, and commits.

---

## Context Pills

### What They Are

Pre-drafted rationale that makes tasks unambiguous. A pill should exist before implementation when the task depends on rationale, constraints, terminology, or boundaries that are not obvious from code or durable docs.

Each pill captures:
- **Why** this approach over alternatives
- **What** constraints/guardrails drive the decision
- **Where** in the codebase changes apply
- **How** the pattern/model informs implementation
- **Language** - terminology conventions, naming rules
- **Scope** - context vs. implementation artifact

### Dimensions

| Dimension | Values / Meaning |
| --------- | ---------------- |
| Type | `guardrail`, `decision`, `pattern`, `model`, `warning` |
| Scope | `global`, `domain`, `component`, `feature`, `task` |
| Nature | `context` (rationale), `implementation` (artifact guidance), `operational` (workflow rule) |
| Language | Human language or implementation language affected by the pill |
| Bound To | Task, component, command, document, model, or workflow surface the pill applies to |
| Lifecycle | `draft`, `current`, `deprecated`, `superseded` |
| Owner | Human, agent, repo area, or downstream system responsible for keeping it accurate |
| Review Trigger | Event that should force re-audit, such as task close, API change, model change, or failed validation |

### Pill Lifecycle

```
Drafted -> Bound to task (desk/pills/) -> Audited after step ->
  -> Still needed? Keep.
  -> Redundant with code/docs? Delete.
  -> Complete. Move the knowledge into code/docs, then delete or deprecate.
```

**Non-redundancy rule:** Code is truth. Docs is index. Context is reasoning (subset of subset). Context pills must not repeat what is already in code or docs.

---

## Context Audit Ritual

After each step/execution:

```
1. CHECK  -> Is every task aspect covered by a pill?
2. AUDIT  -> Are pills still accurate or stale?
3. UPDATE -> Update stale pills or delete them.
4. BIND   -> Link new pills to tasks as needed.
```

---

## Pre-Execution Gate

Before starting any task, subagent must ask:

> **"Is there any ambiguous or unclear aspect not covered by the context machine?"**

- **If no:** Proceed with execution.
- **If yes:** create or request the missing context before proceeding. Do not proceed until task is unambiguous.

---

## The Rituals

### 1. Initialization Ritual

Before starting any work:

```
1. ATOMIZE   -> Break into smallest possible child tasks
2. DEDUPE    -> Merge overlapping items
3. CLEAN     -> Delete legacy content
4. AUDIT     -> Verify existing work before claiming completion:
               - Check git history for relevant commits
               - Verify artifacts exist as specified
               - Run tests to confirm state
5. RESOLVE   -> Resolve contradictory end states
6. BIND      -> Link context pills to tasks
7. INDEX     -> Regenerate desk/tasks/Board.md
8. EXECUTE   -> Begin work with explicit boundaries
```

> **Critical:** Do not mark a task "completed" without auditing git history. Trust the code, not the task file.

### 2. Execution Ritual

When a task is **done**:

```
1. INVALIDATE -> Check if existing tests are broken. Update/delete.
2. VERIFY    -> Add new tests where necessary.
3. TEST      -> Run the relevant test or validation suite. ALL required checks must pass.
4. CHANGELOG -> Update the changelog when the change is user-visible or release-relevant.
5. AUDIT     -> Run Context Audit Ritual (check pills, delete stale).
6. DELETE    -> Remove task file.
7. BOARD     -> Update desk/tasks/Board.md.
8. COMMIT    -> Make atomic commit.
```

### 3. Phase Completion Ritual

When all tasks in a phase or delivery slice are done:

```
1. COMPILE   -> Rebuild if applicable (bundles, dist).
2. AUDIT     -> Run the relevant tests and quality checks.
3. REGRESS   -> Fix any test failures.
4. FLOW      -> Move needed pill knowledge into code/docs. Delete redundant pills.
5. ADVANCE   -> Move to next phase.
```

---

## The Tasks Board

**Location:** `desk/tasks/Board.md`

```
# Tasks Board

> Single entry point for all active work. Read this before starting any task.

## Active (status=open|in_progress)
| ID | Domain | Task | Priority | Depends On | Pills |
|----|--------|------|----------|------------|-------|

## Blocked (status=blocked)
| ID | Domain | Blocker | Gate |
|----|--------|--------|------|

## Ready to Promote (from drawers/)
| ID | Domain | Item |
|----|--------|------|
```

Completed tasks are not kept on the board. When a task is resolved, delete its task file and remove its active row in the same atomic commit as the resolving change.

---

## Commit Triggers

Commits are made **only** when the Execution Ritual completes.

| Situation | Commit? | Message |
|-----------|---------|---------|
| Phase or slice objectives fully checked | Yes | Use a concise scoped message |
| Critical bug fix mid-phase | Yes | `fix(<scope>): <description>` |
| Chores (deps, config) | Yes | `chore(<scope>): <description>` |
| Slice not done | No | Work-in-progress is not a commit |

---

## Pre-Completion Audit

Before marking a task as **completed**, verify:

```
1. GIT HISTORY  -> Do commits match the completed work?
2. ARTIFACTS   -> Do all specified outputs exist at the specified locations?
3. TESTS       -> Do all tests pass?
4. CLEAN TREE  -> Are all untracked files either gitignored or tracked?
```

If any check fails:
- **Git history mismatch** -> Either correct the commit history or update task state to reflect reality
- **Artifacts missing** -> Implement them
- **Tests failing** -> Fix tests first
- **Dirty tree** -> Clean up before declaring done

> **Rule:** Trust the code, not the task file. The task file describes intent; git history is truth.

---

## Commit Message Format

```
<type>(<scope>): <description>

Types:   feat, fix, docs, refactor, chore, test, perf
Scopes:  use the smallest accurate repo area, component, command, or workflow surface.
```

---

## What NOT to Do (Anti-Patterns)

- [ ] Proceed with ambiguous task without creating or requesting missing context
- [ ] Create implementation artifacts instead of referencing existing pills
- [ ] Let pills drift from code/docs (redundant or stale)
- [ ] Keep pills after their knowledge has been explicitly moved into code/docs
- [ ] Mark task complete without auditing git history
- [ ] Commit with untracked files (gitignore or track first)
- [ ] Skip tests to "get it done"
- [ ] Force-push to hide failures
- [ ] Reference surfaces below current layer
