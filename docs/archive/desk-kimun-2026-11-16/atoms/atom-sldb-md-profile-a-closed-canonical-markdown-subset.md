---
id: atom-sldb-md-profile-a-closed-canonical-markdown-subset
title: 'SLDB-MD profile: a closed canonical Markdown subset'
five_wh_one_plus: what
tags:
- system:sldb
- epoch:v2
- domain:documents
provenance: docs/v2/04-superficie-markdown.md
---

# SLDB-MD profile: a closed canonical Markdown subset

## Answer

Milestone 4 implements the SLDB-MD profile, a closed subset of CommonMark with exactly one canonical spelling per construct: ATX headings 1-6, paragraphs with nested-or-disjoint inline marks (emphasis, strong, code, link), fenced code, block quotes, lists (unordered with -, ordered with n.), thematic breaks. Anything outside the profile is preserved byte-for-byte as an opaque node and never descended; full CommonMark conformance is a later drawer task. The profile is what makes parse(render(A)) == A a provable property and the addressability report exact.
