# SLDB Desk Surface Spec

## Product Goal

Provide a minimal but credible local desk surface for the SLDB repo so current work, decisions, inbox notes, and future workflow boundaries can be represented as structured Markdown and queried through the store.

## Real Use Case

When the repo evolves through multiple documentation, CLI, and architecture slices, contributors need a local operational layer that records what was done, why it was done, what remains open, and which parts should eventually move into `deskops`.

## Minimal Feature Slice To Deliver

The first credible slice is:

- a board
- local desk task docs
- local desk pill docs
- a desk standards doc
- a desk spec doc
- inbox notes tracked through a local inbox-note model

## What Is Currently Missing

The desk is still missing richer lifecycle operations, explicit cross-linking between notes and tasks, status transition commands, and any true orchestration or ritual automation beyond the manual workflow carried out in this repo.

## Non-Goals For This Delivery

- implementing full `deskops`
- cross-repo routing semantics
- automated commit rituals
- moving all desk logic out of SLDB immediately

## Acceptance Criteria

The local desk surfaces exist as structured documents, validate against local models, and are tracked in the store so they can be queried as part of the repo's normal documentation workspace.
