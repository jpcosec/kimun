# Contention test for heads commit!

ID: task-contention-test-for-heads-commit
Status: deferred
Priority: medium

## Goal

Triage and resolve the inbox message promoted from `desk/inbox/20260829-174024-suggestion-contention-test-for-heads-commit.md`.

## Scope

Exercise sldb.kernel.heads/commit! under real concurrent writers (futures on JVM, separate bb processes on the file backend) and prove the CAS retry and ConflictSet paths; also two processes committing to the same directory.

## Source

- `desk/inbox/20260829-174024-suggestion-contention-test-for-heads-commit.md`

## Done When

- The message is resolved, answered, or promoted into active work.
