---
kind: unclear
sender_project: sldb-refactor-worktree
created_at: 2026-08-29T15:47:45
status: open
---

# deskops atoms add-namespace overwrites hierarchical tag-namespaces.yaml

deskops/atom_tags.py load_namespaces reads only the top-level 'namespaces' key and _write_registry dumps only that key, so 'deskops atoms add-namespace' replaced this repo's hierarchical tag-namespaces.yaml (system/domain tree with spec2viz_nodes and rules) with a flat file. Restored from git on 2026-08-29 and appended a 'namespaces' key so both formats coexist. add-namespace should preserve unknown top-level keys, and validation should accept the hierarchical domain leaves.
