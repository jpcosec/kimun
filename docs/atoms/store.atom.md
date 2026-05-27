# Store

## Related Concepts

- [Tracked Document](tracked-document.atom.md)
- [Semantic vs Physical Search](semantic-vs-physical.atom.md)
- [AST View](ast-view.atom.md)

## What It Is

An SLDB store is the metadata workspace that registers models, tracks documents, keeps integrity hashes, and materializes semantic or section indexes without becoming the source-of-truth for the Markdown content itself.

## Why It Matters

It gives SLDB stable names, query surfaces, and integrity checks across many Markdown files. Without a store, SLDB can still render and extract a single document, but it cannot manage a project-level graph of tracked docs.

## How It Works

The store keeps YAML indexes for registered models and tracked docs plus hash layers that detect content drift, payload drift, missing files, and contract changes. The Markdown files stay where they already live in the repo.

## When It Shows Up

You encounter the store when you run `stores init`, `models add`, `docs track`, `docs create`, `stores check`, `stores update`, or semantic and section queries.

## Where It Lives

The normal project-local location is `.sldb/`. A global `~/.sldb/` can also exist, and the local store takes precedence when both are present.

## Who Uses It

Users and automation use the store when they need a queryable workspace of models and tracked docs instead of one-off extract/render operations.
