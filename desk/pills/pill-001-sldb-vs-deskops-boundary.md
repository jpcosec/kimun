---
pill_type: decision
scope: domain
nature: context
bound_to: sldb CLI, desk workspace, architecture docs
created: "2026-05-20"
lifecycle: current
---

# Keep SLDB generic and move workflow behavior toward deskops

SLDB should own structured Markdown contracts, stores, validation, and generic navigation/query surfaces.

Desk-specific semantics such as inbox triage, task lifecycle, pill routing, rituals, cross-repo messaging, and close-loop operational automation should not harden into generic SLDB behavior unless they are truly reusable across many unrelated repos.

Thin bridge behavior in SLDB is acceptable when it helps users reach the right workspace, but the owning runtime for the workflow should be `deskops`.
