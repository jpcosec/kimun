# Semantic vs Physical Search

## Related Concepts

- [Store](store.atom.md)
- [Tracked Document](tracked-document.atom.md)
- [AST View](ast-view.atom.md)

## What It Is

`physical` search means searching concrete names and structure such as file paths, tracked doc names, field paths, section titles, and physical anchors. `semantic` search means searching explicit semantic tags and derived meaning such as document or section concepts.

## Why It Matters

Users often know either a path-like token or a meaning-like concept, but not both. SLDB keeps both layers available so navigation can work from structure or from meaning.

## How It Works

Physical matching works over tracked names, paths, and section or field addressing. Semantic matching works over model or doc semantic tags and section context materialized during store updates.

## When It Shows Up

You encounter this distinction in `find --in semantic|physical|both`, `sections find --in ...`, and the AST views that expose physical versus semantic edges.

## Where It Lives

Physical information comes from file locations and document structure. Semantic information lives in model semantics, tracked doc tags, and section-context artifacts in the store runtime indexes.

## Who Uses It

Anyone trying to retrieve docs by concept, not only by path, uses semantic search. Anyone trying to jump to a known file, doc name, field, or section uses physical search.
