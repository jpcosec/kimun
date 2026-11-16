---
id: atom-reversibility-scope-in-v2
title: Reversibility scope in v2
five_wh_one_plus: how_not
tags:
- system:sldb
- epoch:v2
- domain:emitters
provenance: docs/v2/01-orden-filosofico.md
---

# Reversibility scope in v2

## Answer

Do not promise round-trip between the canonical model and its surfaces. Inside S, text to concrete syntax tree is reversible by construction (every byte belongs to a node). S to M is a mapping under context, deterministic given (sign, W_i, engine) but not invertible. M to S is never an inverse: a symbol has many signs and choosing one is generation (deconversion). The guarantee that replaces exact render equality is: reversible in S, traceable through S-M-G, generative back; plus transparency (what is anchored in what) and mutation detection.
