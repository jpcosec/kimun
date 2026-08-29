---
id: atom-canonical-content-and-node-hashing
title: Canonical content and node hashing
five_wh_one_plus: how
tags:
- system:sldb
- epoch:v2
- domain:hashing
provenance: docs/v2/02-sustrato-computacional.md
---

# Canonical content and node hashing

## Answer

id(node) = H(canonical-bytes({:class :kind :content})). canonical-bytes is canonical EDN: map keys sorted by printed form, sets sorted, vectors in order, no metadata, no extra whitespace, strings NFC UTF-8, dates as ISO-8601 strings. class and kind enter the hash (same text as :text and as :opaque are different signs); address, provenance, timestamps and evidence do not. Per-class content shapes: :sign/:text {:text}, :sign/:block {:format :type :attrs}, :sign/:opaque {:format :blob}, :sign/:external {:locator :sample :fingerprint}, :sign/:span {:leaf :range}, :symbol/:term {:name :lang}, :symbol/:proposition {:form [...]}, :fact/:triple {:subject :predicate :object :context}, :fact/:context {:name}. In milestones 0-3 M and G are data shapes only.
