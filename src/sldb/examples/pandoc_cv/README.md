# Pandoc CV Example

This example shows the smallest supported pattern for Markdown CV files that already use Pandoc fenced div blocks such as `::: {.job ...}` and `::: {.education ...}`.

What it demonstrates:

- keep the CV as a shallow `title + body` document contract
- preserve fenced div blocks and their attributes verbatim inside the body
- let downstream tools keep parsing those blocks structurally

Files:

- `cv_model.py` - minimal `StructuredNLDoc` model
- `cv.input.md` - sample CV with fenced div blocks

Try it:

```bash
python -m sldb validate sldb.examples.pandoc_cv.cv_model:PandocCVDoc --input src/sldb/examples/pandoc_cv/cv.input.md --pythonpath .
```

Current boundary:

- Yes: SLDB can preserve and round-trip the fenced div blocks under a shallow body field.
- Not yet first-class: SLDB does not interpret fenced div attributes as dedicated typed fields unless you add a custom model/handler strategy for that format.
