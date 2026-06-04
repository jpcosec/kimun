---
id: '002'
domain: cli
status: open
priority: p1
depends_on: ['001']
created: '2026-06-04'
---

# Implement semantic export command

## Objective

Expose an SLDB command or API that emits the KGDB semantic export payload from a store.

## Reference

- Inbox: `desk/inbox/20260604-000004-question-semantic-export-for-kgdb.md`

## Candidate CLI

```bash
sldb semantic export --store .sldb --pythonpath . --format kgdb
```

Alternative names are acceptable if they fit SLDB CLI grammar better.

## Requirements

- Rebuild or require fresh semantic indexes explicitly.
- Emit JSON/YAML to stdout or output path.
- Include enough provenance for KGDB to preserve source identity.

## Validation

- CLI test over a temporary store.
- Roundtrip fixture consumed by KGDB contract tests when available.
