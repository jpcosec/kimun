---
id: atom-decision-no-separate-lisp-metalanguage
title: 'Decision: no separate Lisp metalanguage'
five_wh_one_plus: why
tags:
- system:sldb
- epoch:v2
- domain:decisions
provenance: docs/v2/02-sustrato-computacional.md
---

# Decision: no separate Lisp metalanguage

## Answer

The separate Lisp DSL existed because the kernel was Rust and needed an embeddable extension language (Steel). With a Clojure kernel, Clojure is already the Lisp, and a second language would cost a reader, macro expander, VM, spec and docs for nothing. What survives is the architectural boundary: untrusted code (user scripts, hooks, agents) produces plans as EDN data (TransactionPlan, QueryPlan, EffectPlan) and the kernel validates them before applying. Untrusted scripting runs in SCI with a namespace allowlist, which implements the capability model.
