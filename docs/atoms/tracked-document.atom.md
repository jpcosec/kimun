# Tracked Document

## Related Concepts

- [Store](store.atom.md)
- [StructuredNLDoc Model](structurednldoc-model.atom.md)
- [Compose vs Recover](compose-vs-recover.atom.md)

## What It Is

A tracked document is one Markdown file that has been registered in a store under a short logical name and associated with one registered SLDB model.

## Why It Matters

Tracking is what lets SLDB talk about a document by stable logical identity instead of only by physical file path.

## How It Works

When you create or track a document, the store records its logical name, physical path, payload hash, content hash, and semantic tags. Commands can then refer to the doc by that tracked name, by `Model/DocName`, or sometimes by file path.

## When It Shows Up

You encounter tracked docs when you run `docs create`, `docs track`, `docs update`, `docs show`, `fields ...`, `sections ...`, `find`, `recover`, and `compose`.

## Where It Lives

The content lives in the Markdown file you created or tracked. The tracked metadata lives inside the store indexes under `.sldb/`.

## Who Uses It

Any user or tool that wants stable queries, field updates, section navigation, or semantic search over Markdown documents uses tracked docs.
