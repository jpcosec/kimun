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

The kernel is written as pure Clojure .cljc (data plus functions, no I/O) so it runs unchanged on Babashka (native CLI, instant start, no JVM for users), ClojureScript on Node (datascript, web-tree-sitter WASM, ProseMirror, sql.js) and the JVM if ever needed. The host is a ports-and-adapters concern; Python remains an optional orchestration adapter via subprocess or HTTP, not FFI. Compiling Clojure itself to WASM is not attempted yet (GraalVM native-image WASM is experimental, jank is early); WASM is used for modules hosted by Node. The bb-versus-Node choice is deferred past the first slice, which is pure.
