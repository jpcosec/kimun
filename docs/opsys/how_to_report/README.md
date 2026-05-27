# How To Report

## Purpose

This guide defines how to write a report that is operationally useful.

A good report does not only say that something feels wrong. It captures the user intent, the context, the break in expectation, the evidence, and the next decision surface.

## What A Report Is

A report is a durable account of an observed problem, ambiguity, friction point, or operational mismatch.

It should help another person answer:

- what happened
- to whom it happened
- under which conditions it happened
- why it matters
- what should change
- what should not be changed by accident while fixing it

## When To Report

Write a report when:

- a user cannot complete an intended path
- the product behaves correctly but opaquely
- two valid interpretations of the system compete with each other
- documentation and runtime behavior diverge
- a repeated confusion signal appears across sessions
- the team needs a durable artifact instead of chat memory

## What Makes A Report Useful

A useful report is:

- concrete
- reproducible
- scoped
- evidence-backed
- written from user intent first, not from implementation first
- explicit about the difference between expected and actual behavior

## Reporting Structure

Use this structure.

### what

What happened.

State the user intent and the observed failure or friction.

### why

Why it matters.

Describe the user impact, product impact, operational cost, or risk.

### when

When it appears.

Describe the timing, trigger, workflow stage, or preconditions.

### where

Where it happens.

List surfaces, commands, screens, docs, components, or repositories involved.

### how_fix

How the issue should be approached.

Describe the direction of the fix, acceptance conditions, and decision boundaries.

### how_not_fix

How the issue should not be handled.

List anti-fixes, misleading shortcuts, or changes that would hide the signal without solving it.

### who

Who is acting.

Name the actor type, not just a person.

Examples:

- first-use CLI user
- maintainer in a downstream repo
- reviewer trying to verify a workflow

### whom

Whom it affects.

List all affected audiences, not just the first reporter.

### which

Which exact paths are implicated.

This includes commands, states, modes, flags, artifacts, and variants.

### with

With which context.

Capture environment, prior knowledge, assumptions, and neighboring state.

## Minimal Evidence Set

Attach or summarize:

- reproduction steps
- command transcript or screenshots
- expected behavior
- actual behavior
- relevant references
- open questions if certainty is incomplete

## Severity Cues

Increase urgency when the issue:

- blocks first use
- produces the wrong mental model
- causes destructive actions
- creates invisible state mismatches
- scales across many repos or users

## Report Skeleton

```md
# Report Title

## Summary

One short paragraph with the user intent, the failure, and the impact.

## What

## Why

## When

## Where

## Who

## Whom

## Which

## With

## Reproduction

## Expected Behavior

## Actual Behavior

## Root Cause Hypothesis

## How Fix

## How Not Fix

## Acceptance Criteria
```

## Common Mistakes

Do not:

- write only symptoms with no user intent
- jump straight to a fix with no evidence
- confuse one user's workaround with a product solution
- hide uncertainty instead of naming it
- mix several unrelated issues into one report

## Product Positioning

As an `opsys` product artifact, a report should be reusable across tools and repos.

That means the structure should survive beyond one command surface and should later fit stateful operational handling such as tasks, routines, checklists, and conditional hooks.
