---
id: atom-decision-pure-cljc-kernel-with-hosts-as-adapters
title: 'Decision: pure cljc kernel with hosts as adapters'
five_wh_one_plus: how
tags:
- system:sldb
- epoch:v2
- domain:decisions
provenance: docs/v2/02-sustrato-computacional.md
---

# Decision: pure cljc kernel with hosts as adapters

## Answer

The kernel is written as pure Clojure .cljc (data plus functions, no I/O) and its definitive host is Babashka (decision 14): native CLI, instant start, no JVM for users. Node/ClojureScript parity (datascript, ProseMirror, sql.js) stays in the drawer and is retaken only if asked. Tree-sitter is a Rust pod in-process (decision 29), not web-tree-sitter WASM in Node. The host is a ports-and-adapters concern; Python remains an optional orchestration adapter via subprocess or HTTP, not FFI. Compiling Clojure itself to WASM is not attempted yet (GraalVM native-image WASM is experimental, jank is early).
