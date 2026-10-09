---
kind: unclear
sender_project: sldb-refactor-worktree
created_at: 2026-08-29T15:46:42
status: open
---

# deskops edit atom cannot target legacy atoms

The 204 pre-v2 atoms in desk/atoms lack the artifact.atom marker, so 'deskops edit atom <id> provenance ...' fails with 'No artifact.atom file found'. Provenance paths were rewritten with sed after moving 'source docs/' to 'raw/source/' (2026-08-29). Need either a migration command for legacy atoms or a documented way to bring them under the modeled artifact format.
