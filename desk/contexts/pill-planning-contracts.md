---
id: pill-planning-contracts
tags:
- workspace:desk
---

# Pill: Planning Contract Guardrails

- Planning tasks in this worktree produce contract docs, not code.
- Each task must create exactly one named output artifact.
- Each artifact must cite the governing atoms and target architecture docs named by the task.
- Each artifact must state in-scope behavior, out-of-scope behavior, and downstream constraints.
- Validation is attestation that the contract is atom-backed, dependency-aligned, and makes no implementation claims.
- A planning task closes only when the artifact path is listed in `files:` and the board/task metadata matches the artifact delivered.
