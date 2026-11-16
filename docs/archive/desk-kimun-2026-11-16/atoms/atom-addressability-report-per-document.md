---
id: atom-addressability-report-per-document
title: Addressability report per document
five_wh_one_plus: what
tags:
- system:sldb
- epoch:v2
- domain:addressability
provenance: docs/v2/04-superficie-markdown.md
---

# Addressability report per document

## Answer

report(ast) returns {:blocks :structural :opaque :coverage :opaque-regions [{:index :format :lines}]} where coverage is the fraction of graphemes that live in :text leaves over the graphemes of the canonical render; it is how the tool states its limits (docs/v2/01 section 4.7): what part of a document is operable and exactly which regions are opaque and why.
