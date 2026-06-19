# Text Layer vs Graph Layer

## Related Concepts

- [Structured Text](structured-text.atom.md)
- [Store](store.atom.md)
- [Semantic vs Physical Search](semantic-vs-physical.atom.md)

## What It Is

The text layer and graph layer are complementary but different parts of the knowledge system. In this architecture, SLDB owns the text layer and KGDB owns the graph layer.

## Why It Matters

If the layers blur together, authored document truth, structural query, composition, traversal, and inference become hard to place correctly. Keeping the split explicit prevents duplicated behavior and protects the role of readable source documents.

## How It Works

SLDB owns canonical human-authored documents, AST-like document structure, extraction, rendering, structural query, textual composition, and graph-ready export. KGDB consumes exported knowledge structures and owns graph persistence, traversal, equivalence, inference, and higher-order relational reasoning.

## When It Shows Up

You encounter this distinction when deciding whether a feature belongs in document parsing, section/field querying, composition, semantic export, or instead in downstream graph traversal and reasoning.

## Where It Lives

The text layer lives in repo documents, SLDB models, and store artifacts. The graph layer lives downstream in KGDB's graph-native persistence and query/runtime surfaces.

## Who Uses It

Contributors use this boundary to decide feature placement. Tooling uses it to keep authored textual truth separate from derived graph truth.
