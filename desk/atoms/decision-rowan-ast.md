---
id: decision-rowan-ast
title: Decision rowan AST
five_wh_one_plus: why
tags:
- architecture:decision
- concept:ast
provenance: desk/drawer/features/feature-rust-library-stack.md
---

# Decision rowan AST

To satisfy the strictly reversible parsing requirement (`AST -> render -> AST`) for reversible document families, `rowan` (lossless syntax trees) is chosen. It retains all bytes, including whitespace and trivia, guaranteeing that rendering a non-mutated AST yields the exact original text.
