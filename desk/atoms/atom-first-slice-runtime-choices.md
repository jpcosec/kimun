---
id: atom-first-slice-runtime-choices
title: First-slice runtime choices
five_wh_one_plus: when
tags:
- system:sldb
- epoch:v2
- domain:clojure-core
provenance: docs/v2/02-sustrato-computacional.md
---

# First-slice runtime choices

## Answer

For milestones 0-3: SHA-256 through a host Hasher protocol (Babashka: java.security.MessageDigest; ClojureScript/Node: crypto), BLAKE3 later, algorithm recorded in the store descriptor; persistence to files only (objects/<hash> CAS, log.edn append-only one transaction per line, heads.edn written atomically by rename); tests with clojure.test plus clojure.test.check (bundled in Babashka), generative tests meaning test.check properties over generators of nodes, trees and plans; hand-described transactions as test/fixtures/tx-<n>.edn with :plan and :expected; validation on Babashka only (bb test), ClojureScript host parity as a later task; all code .cljc with reader conditionals confined to sldb.host.*; M and G as data shapes only; evidence = bb test output in runs/subagents/<run>/validation.log plus a commit; Babashka >= 1.3 required on the executor host.
