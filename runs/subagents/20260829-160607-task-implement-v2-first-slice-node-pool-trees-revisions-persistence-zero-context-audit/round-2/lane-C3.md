# Lane C3 — docs/architecture, drawer, tasks, rituals, entry docs (round 2)

Model: claude-haiku-4-5 · Explore (read-only) · fresh context · 2026-08-29 · 28 files read

## Review
- Note [high]: docs/faq.md:18 — "the task stays planning-only" — contradicts the v2 implementation epoch
- Note [high]: desk/rituals/execution.md:7, testing.md:4, closeout.md:5,16 — planning-only rituals still live (mitigated by pill:16-22)
- Note [high]: contracts/graph-store-contract.md:39 "redb is the default embedded backend" and python-clojure-ownership-and-ffi-contract.md:30 "Blake3-based hashing" — lane claims NOT covered by the Superseded header
- Note [medium]: contracts/target-system-overview.md:13 "Phase 1: replicate the required v1-visible workflows" — lane claims not covered
- Note [medium]: AGENTS.md:27-30 "Planning-task rule" — planning-only closure rules; v2 implementation tasks follow the audit-gate lifecycle
- Note [low]: docs/faq.md:13 planning-era framing

## Answers
1. No drawer feature/track/task competes with the four milestone tasks.
2. Planning-only statements remaining: docs/faq.md:18; desk/rituals/execution.md:7; testing.md:4; closeout.md:16; AGENTS.md:27-30.
3. Claimed uncovered sentences: target-system-overview:13; python-clojure-ownership-and-ffi-contract:30; graph-store-contract:39.

## Verdict
Counts: retire=3 rewrite=6 neutral=1. Files read=28.

## Triage note (main session)
The three "uncovered" contract sentences ARE quoted verbatim in their Superseded headers (the header lists exactly "redb is the default embedded backend", "Blake3-based hashing", "Phase-1 v1-parity-first sequencing"); the lane misread "the header quotes the sentence as no longer holding" as "not covered". False positives. Legacy rituals stay by user decision. Real fixes: docs/faq.md:18 and AGENTS.md planning-task rule → add an implementation-task rule.
