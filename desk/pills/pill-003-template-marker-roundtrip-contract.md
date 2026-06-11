---
pill_type: pattern
scope: domain
nature: implementation
bound_to: template markers, render/extract roundtrip
created: "2026-06-11"
lifecycle: current
---

# Keep template markers reversible at the document boundary

Template marker features must preserve the SLDB reversible document contract: values rendered through a marker should extract back to the same structured value unless the task explicitly defines a lossy format.

New marker types should be implemented at the template/render/extract boundary, not by changing stored document semantics to match presentation details.

For table-like markers, the table syntax is presentation. The structured field remains the source of truth, and tests should cover render, extract, and full roundtrip behavior.
