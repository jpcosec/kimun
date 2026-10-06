# AST View

## Related Concepts

- [Semantic vs Physical Search](semantic-vs-physical.atom.md)
- [Tracked Document](tracked-document.atom.md)
- [Store](store.atom.md)

## What It Is

The AST view is SLDB's normalized graph representation of stores, models, documents, sections, fields, and their semantic or physical relationships.

## Why It Matters

It lets users inspect what SLDB thinks exists, which is often the fastest way to debug a confusing query result or understand how a tracked doc is indexed.

## How It Works

`ast show` serializes a store, model, document, or field target into a normalized payload. For tracked documents, the output can include sections, field ownership, context index entries, and graph edges.

## When It Shows Up

You encounter the AST when debugging store shape, checking section ownership, understanding semantic versus physical indexing, or building other tooling on top of the normalized graph.

## Where It Lives

The view is materialized by the CLI graph helpers and exposed through `sldb ast show` and `sldb ast schema`.

## Who Uses It

Advanced users and tooling use the AST when they need more than the high-level command outputs.
