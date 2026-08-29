---
id: atom-decision-first-slice-kernel-semantics-confirmed
title: 'Decision: first-slice kernel semantics confirmed'
five_wh_one_plus: why
tags:
- system:sldb
- epoch:v2
- domain:decisions
provenance: runs/subagents/20260829-160607-task-implement-v2-first-slice-node-pool-trees-revisions-persistence-zero-context-audit/triage.md
---

# Decision: first-slice kernel semantics confirmed

## Answer

On 2026-08-29, after the zero-context audit gate closed and milestone 0 shipped, the user confirmed five semantics that the main session had chosen during triage: class and kind enter the node hash (same content in another class/kind is another node); under succession reference and binding edges follow the successor while semantic and projection edges stay superseded for review and derived edges are recomputed; the timestamp is outside the edge id so the same assertion by the same origin is one idempotent edge and different origins are two evidences; tree ids are nominal ULIDs with descriptors as CAS objects and the Revision hashes exactly eight fields; the first slice uses SHA-256, a files-only backend, Babashka-only validation and M/G as data shapes. These are user decisions, not implementation conveniences, and later milestones must not silently revisit them.
