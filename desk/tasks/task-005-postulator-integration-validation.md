---
id: task-005
domain: integration/store/workflow
status: open
priority: p1
depends_on: []
created: "2026-05-26"
---

# Define validation and workflow guidance for Postulator integration

## Objective

Turn the current Postulator integration questions into a concrete validation and guidance task so an external pipeline repo can adopt SLDB with a minimal, explicit workflow.

## Reference

- `docs/workspaces.md`
- `docs/faq.md`
- `src/sldb/store/`
- `src/sldb/cli/commands/docs.py`
- `src/sldb/cli/commands/models.py`

## What To Fix

The integration path is underspecified in three places:

- how to validate the current Pandoc-oriented document shape
- what repo-local `.sldb/` convention should be recommended
- what the minimal track/update/round-trip workflow should be for generated documents in a pipeline repo

## How To Do It

Write an integration note or guide that answers those three questions with the smallest stable recommendation.

Keep SLDB responsible for contracts and tracked documents, not for owning runtime artifact trees that belong to the downstream pipeline.

Use cover letter as the first end-to-end example and CV as the higher-risk structural validation case.

## Validation

- confirm the recommended repo-local store layout
- confirm how tracked docs should reference generated files under pipeline-managed directories
- demonstrate a minimal command sequence for init, model registration, track or create, update, and round-trip validation
- ensure the guidance stays aligned with the Pandoc fenced-div answer from `task-004`

## Done When

There is a concrete, minimal SLDB integration workflow for Postulator-style pipeline repos, including store convention, tracking boundaries, and validation guidance.
