---
id: atom-external-fingerprint-form-and-who-computes-it
title: External fingerprint form and who computes it
five_wh_one_plus: how
tags:
- system:sldb
- epoch:v2
- domain:anchors
provenance: docs/v2/02-sustrato-computacional.md
---

# External fingerprint form and who computes it

## Answer

An external sign node carries a locator, a sample and a fingerprint. The fingerprint is written as the store hash algorithm name, then a colon, then the lower case hex digest; the kernel validates only that form and compares for equality, because it performs no I/O and never recomputes a digest. Which bytes are digested depends on the locator kind and is the contract of the engine that emits the anchor, recorded in the engine and version of the evidence — the file contents for a file locator, the response body for a url locator, and the NFC UTF-8 of the sample for a text locator. The kernel does not close the list of locator kinds. A resource that changes produces a different content-addressed node, so unless a transaction recorded the succession the old node becomes orphan and reconciliation can name the new one by matching locator.
