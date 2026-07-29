---
id: decision-rusqlite-store
title: Decision rusqlite store
five_wh_one_plus: why
tags:
- architecture:decision
- concept:store
provenance: desk/drawer/features/feature-rust-library-stack.md
---

# Decision rusqlite store

`rusqlite` is selected for managing the local `.sldb/` database. Given the append-only and immutable nature of the graph store, its synchronous and lightweight access model is ideal for controlling SQLite file-locks and transactions directly from Rust.
