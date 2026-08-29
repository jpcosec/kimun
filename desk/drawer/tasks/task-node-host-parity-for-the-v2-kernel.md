# Node host parity for the v2 kernel

ID: task-node-host-parity-for-the-v2-kernel
Status: deferred
Priority: medium

## Goal

Triage and resolve the inbox message promoted from `desk/inbox/20260829-174021-suggestion-node-host-parity-for-the-v2-kernel.md`.

## Scope

Run the whole bb suite on ClojureScript/Node (nbb or shadow-cljs): implement sldb.host.* cljs branches (hash via crypto, NFC via String.normalize, ulid via crypto.randomBytes, fs-store via fs), prove the golden fixtures yield identical ids on both hosts. Deferred from the first slice (docs/v2/02 section 8.1).

## Source

- `desk/inbox/20260829-174021-suggestion-node-host-parity-for-the-v2-kernel.md`

## Done When

- The message is resolved, answered, or promoted into active work.
