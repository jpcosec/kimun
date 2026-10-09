---
kind: unclear
sender_project: sldb-refactor-worktree
created_at: 2026-08-29T16:16:50
status: open
---

# legacy atoms relinked after retirement

16 legacy atoms contradicting docs/v2 were moved to raw/source/atoms-retired/ and untracked (2026-08-29, zero-context audit round 1). Wikilinks in surviving legacy atoms and ids in docs/architecture/spec2viz/*.yml were rewritten with sed to the superseding epoch:v2 atom because 'deskops edit atom' cannot target legacy-format atoms. Two surviving atoms got one-line in-place fixes (markdown-importer/emitter depends_on; ports-and-adapters library list).
