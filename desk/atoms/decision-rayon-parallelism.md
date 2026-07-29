---
id: decision-rayon-parallelism
title: Decision rayon parallelism
five_wh_one_plus: why
tags:
- architecture:decision
- concept:rust-core
provenance: desk/drawer/features/feature-rust-library-stack.md
---

# Decision rayon parallelism

`rayon` enables data-parallelism during the Importer phase. Operations like reading files, parsing ASTs, computing hashes, and extracting anchors can be executed concurrently across all CPU cores, significantly speeding up repository ingestion.
