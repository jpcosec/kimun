---
id: atom-lossless-cst-and-canonical-render-for-markdown
title: Lossless CST and canonical render for Markdown
five_wh_one_plus: how
tags:
- system:sldb
- epoch:v2
- domain:emitters
provenance: docs/v2/04-superficie-markdown.md
---

# Lossless CST and canonical render for Markdown

## Answer

cst/parse is total and lossless: a vector of blocks with their original lines, the detected newline and the trailing-newline flag, so concat reproduces the bytes (invariant 8). The AST is the lossy neutral form; render(ast) emits one canonical spelling: one blank line between blocks, escapes for \ * _ [ ] backtick < and for leading block markers, code fences longer than any inner backtick run, - and n. list markers with two-space continuation, > quote prefixes, --- breaks, opaque blobs verbatim, final newline; so parse(render(A)) == A for every canonical A (invariant 9).
