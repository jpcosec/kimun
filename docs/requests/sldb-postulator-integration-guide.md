---
status: done
priority: p1
assigned_to: sldb-team
created: 2026-05-26
labels:
  - integration
  - postulator
  - workflow
  - store
---

# Integration Guide: minimal SLDB workflow for Postulator-style pipeline repos

This guide captures the smallest stable SLDB recommendation for a downstream pipeline repo that already generates reviewable Markdown and wants contract validation plus tracked-document workflows without making SLDB the owner of runtime artifacts.

## Recommended Store Convention

Use one repo-local `.sldb/` at the pipeline repo root.

Put only SLDB metadata there:

- registered model indexes
- tracked document indexes
- integrity hashes
- semantic and section indexes

Do not move generated runtime artifacts into `.sldb/`.

If the pipeline writes files under paths such as `data/jobs/<source>/<job_id>/nodes/...`, keep those files where they already belong and let tracked docs point at them by physical path when needed.

## Recommended First Cases

- first end-to-end case: cover letter
- second structural validation case: CV with Pandoc fenced div blocks

The CV answer depends on the validated shallow-body pattern documented in `docs/requests/sldb-pandoc-fenced-div-support.md`.

## Minimal Workflow

Initialize the store:

```bash
python -m sldb stores init --path .
```

Register the model:

```bash
python -m sldb models add myapp.docs:CoverLetterDoc --store .sldb --pythonpath .
```

Inspect what is registered:

```bash
python -m sldb models list --store .sldb
```

Track an existing generated document:

```bash
python -m sldb docs track data/jobs/acme/123/nodes/cover-letter.md --model CoverLetterDoc --store .sldb --pythonpath .
```

Or create one from payload data:

```bash
python -m sldb docs create --model CoverLetterDoc -o data/jobs/acme/123/nodes/cover-letter.md payload.yaml --store .sldb --pythonpath .
```

Update a tracked document from payload data:

```bash
python -m sldb docs update cover-letter payload.yaml --store .sldb --pythonpath .
```

Validate round-trip behavior:

```bash
python -m sldb validate myapp.docs:CoverLetterDoc --input data/jobs/acme/123/nodes/cover-letter.md --pythonpath .
```

Rebuild indexes after bulk changes:

```bash
python -m sldb stores update --store .sldb --pythonpath .
```

## Ownership Boundary

SLDB should own:

- document contracts
- validation
- tracked logical names
- queryable store metadata

The downstream pipeline should own:

- artifact generation
- artifact directory layout
- review UI parsing and runtime orchestration

That boundary keeps SLDB reusable and prevents the pipeline's runtime tree from becoming store-owned state.

## When To Go Beyond The Minimal Path

Stay with shallow document models while the downstream UI already knows how to interpret the rich Markdown structure.

Move to deeper custom models only when the pipeline needs typed querying or mutation over internal structures such as Pandoc fenced div attributes, repeated job blocks, or embedded review state.
