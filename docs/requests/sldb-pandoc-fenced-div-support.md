---
status: done
priority: p1
assigned_to: sldb-team
created: 2026-05-26
labels:
  - validation
  - pandoc
  - cv
  - markdown
---

# Validation Note: Pandoc fenced div CV support in SLDB

SLDB can support Pandoc fenced div CV documents without forcing a redesign, as long as the model stays shallow and treats the fenced div region as Markdown body content rather than as already-typed field structure.

## Answer

Yes, with a clear boundary.

- supported now: preserve and round-trip fenced div blocks such as `::: {.job ...}` and `::: {.education ...}`
- not first-class yet: automatic extraction of fenced div attributes like `role`, `org`, `dates`, or `degree` into dedicated typed fields

## Recommended Modeling Pattern

Use a minimal `title + body` model:

```python
from pydantic import Field
from sldb import StructuredNLDoc


class PandocCVDoc(StructuredNLDoc):
    __template__ = """
# ⸢rev•title⸥

⸢rev•body⸥
""".strip()

    title: str = Field(description="CV title shown in the H1 heading.")
    body: str = Field(description="Body after the H1, including Pandoc fenced div blocks.")
```

This keeps the current document shape intact and lets a downstream review UI continue to parse the fenced div blocks structurally.

## Minimal Example

See:

- `src/sldb/examples/pandoc_cv/cv_model.py`
- `src/sldb/examples/pandoc_cv/cv.input.md`

Validation command:

```bash
python -m sldb validate sldb.examples.pandoc_cv.cv_model:PandocCVDoc --input src/sldb/examples/pandoc_cv/cv.input.md --pythonpath .
```

## Practical Constraint

Keep one short lead paragraph after the H1 before the first fenced div.

That gives the current reversible extraction model a stable anchor while the fenced div blocks remain intact in the body field.

## Limitation Statement

If the product needs typed queries like "all jobs where role = Staff Engineer" directly from the fenced div attributes, the current generic body strategy is not enough by itself.

That would require either:

- a custom model/handler path for the Pandoc fenced div grammar, or
- a preprocessing step that maps those blocks into a more explicitly typed SLDB contract

For current CV-review workflows that already parse the div blocks externally, the shallow-body strategy is the smallest compatible integration path.
