# How To Test UX CLI

## Purpose

This guide explains how to test CLI user experience, not only CLI correctness.

A CLI can be technically correct and still fail the user if it does not help them discover valid next steps, understand scope, or recover from partial knowledge.

## What UX CLI Testing Is

UX CLI testing checks whether a person can move through a command surface with an understandable mental model.

The primary question is not only "did the command run?" but also:

- could the user infer what to do next
- could the user understand why the command failed
- could the user recover without external help
- did the command names and flags match the user's expectations

## Core Testing Contexts

Always include these contexts when relevant:

### first use

The user has almost no product context.

### partial knowledge

The user knows one concept but not the exact command, object name, or scope.

### wrong assumption

The user uses a reasonable but incorrect mental model and the CLI must help them recover.

### uninitialized workspace

The user runs commands from a folder that lacks the local state the system expects.

### shared or global state

The user expects some information to exist beyond the current folder.

### degraded documentation alignment

Help text, README, and runtime behavior do not fully agree.

## Test Method

Run UX CLI testing as a guided walkthrough.

### 1. define the user intent

Write the goal in plain language.

Example:

- list available models
- inspect one particular tracked document
- understand whether the current folder is active

### 2. define the starting knowledge

State what the user knows and does not know.

Example:

- knows the tool name
- does not know the exact model name
- suspects a global/shared setup exists

### 3. define the starting state

State the environment.

Example:

- no local store
- project store exists elsewhere
- shell opened from a sibling directory

### 4. walk the natural command path

Prefer the commands a real user would try first.

Examples:

- bare command
- `-h`
- `help`
- likely noun surface such as `models`, `docs`, `stores`

### 5. record each expectation break

For every step, capture:

- what the user expected
- what they saw
- whether the CLI gave them a recoverable next step

### 6. classify the issue

Classify each friction point as one or more of:

- naming mismatch
- missing discovery surface
- scope ambiguity
- hidden state dependency
- misleading help
- recoverability failure
- documentation/runtime divergence

## What To Observe

Look for these signals:

- the user must already know the answer in order to ask the question
- help is reference-heavy but path-light
- flags suggest one scope model but implement another
- the CLI relies on local state without surfacing that dependency clearly
- recovery paths exist in theory but are not discoverable from the current failure

## Output Structure

A UX CLI test result should include:

- user intent
- starting knowledge
- starting state
- walkthrough transcript summary
- friction points
- root-cause hypotheses
- expected product behavior
- recommended fix direction

## Sample Script

Use a script like this when testing a first-use or partial-knowledge path:

```md
Goal: list available models and inspect one of them.

Known:
- tool name is known
- exact model name is unknown

State:
- current folder is not initialized
- user suspects a shared/global setup exists

Walkthrough:
1. run bare command
2. run `-h`
3. run `help`
4. run the most likely noun surface
5. try the obvious inspect command
6. note whether discovery is possible without leaving the CLI
```

## Pass Heuristics

A CLI path is strong when:

1. the next likely command is visible
2. failure messages expose the relevant missing scope or state
3. discovery commands exist before inspect commands require exact identifiers
4. help text and runtime behavior agree

## Failure Heuristics

A CLI path is weak when:

1. the user must consult source code or outside chat to continue
2. the CLI names one concept but implements another
3. a common starting state immediately dead-ends
4. a supported shared/global mode exists but is not operationally legible

## Routine, Ritual, Hooks, Checklists

This work fits naturally into an `opsys` direction built from iterable operational primitives.

One useful framing is:

- a routine is an iterable set of instructions
- a ritual is a routine plus a meaningful hook or trigger
- a checklist is an iterable set of conditionals or checks
- a hook is a reusable conditional that runs on a specified condition

Under that framing, UX CLI testing usually starts as a routine.

It becomes a ritual when the team attaches explicit cadence, trigger conditions, ownership, and closure semantics such as:

- before every onboarding-facing release
- after command-surface redesign
- when help text and runtime behavior diverge

## Common Mistakes

Do not:

- treat correctness tests as sufficient UX evidence
- test only happy paths from a fully initialized workspace
- skip the first-use and partial-knowledge states
- over-index on one command instead of the whole recovery path
- stop at surface symptoms without naming the broken mental model
