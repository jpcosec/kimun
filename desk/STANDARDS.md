# SLDB Desk Standards

## Purpose

This desk exists to make the repo's current operational state explicit, queryable, and durable without mixing every workflow decision directly into generic SLDB infrastructure.

The desk should explain what the repo is trying to deliver next, what local execution rules apply, and what decisions or pills future work should inherit before more code changes happen.

## Task Rules

1. Every non-trivial delivery slice should have a task document before or during execution.
2. If the execution depends on a local architectural decision or recurring rule, write a pill instead of relying on chat memory.
3. Update the board when a task closes so the current desk state remains queryable.
4. Validate new desk docs against their local models before tracking them.
5. Rebuild the store after tracking or materially changing desk docs.
6. Keep SLDB generic where possible; if a behavior is really desk workflow semantics, push it toward `deskops`.

## Current Priority

The current priority is to finish the desk surface enough that it can serve as a real handoff point to `deskops`: standards, spec, board, tasks, pills, and inbox notes should all be modelled and tracked before richer workflow automation is attempted.
