# SLDB FAQ

This FAQ is the first-use orientation layer for SLDB. It answers the most common onboarding questions directly and points to the atom docs under `docs/atoms/` when you need the durable definition behind each answer.

## How do I run the CLI correctly?

Run it as `sldb ...` or `python -m sldb ...`.

Do not run `bash sldb ...`. `sldb` is a CLI entrypoint, not a shell script.

Examples:

```bash
sldb help
python -m sldb help docs
```

## What exactly is a store?

A store is SLDB's metadata workspace for model registrations, tracked document indexes, integrity hashes, semantic artifacts, and section indexes. It does not replace your Markdown files and it does not become the canonical source-of-truth for the document text.

See the durable definition in the [Store atom](atoms/store.atom.md).

## Is `.sldb/` required?

No, not for every operation.

- You can use direct model commands such as `extract`, `render`, and `validate` without a store.
- You do need a store when you want model registration, tracked docs, project-level queries, semantic search, section indexes, or integrity checks.
- The normal project-local workflow uses `.sldb/`.
- A global `~/.sldb/` can also exist, and the local store wins when both exist.

## What files live inside the store?

Today the docs describe these current store surfaces:

- model registrations and per-model indexes
- tracked document indexes
- integrity hashes
- semantic artifacts and section indexes

In this repo's current layout, that means files under `.sldb/core/` plus rebuildable runtime artifacts under `.sldb/runtime/`.

In practical terms, the store holds metadata about documents, not the documents themselves.

## What does `semantic` vs `physical` mean?

`physical` means concrete names and structure such as paths, tracked doc names, section titles, and field paths.

`semantic` means concept-like information such as explicit semantic tags and derived section meaning.

Use `physical` when you know something path-like. Use `semantic` when you know something meaning-like.

Examples:

```bash
sldb find roadmap --in physical
sldb find type.documentation.architecture --in semantic
```

See the [Semantic vs Physical Search atom](atoms/semantic-vs-physical.atom.md).

## What format does `models add` expect?

It expects a Python import reference in the form `module:ClassName`.

Examples:

```bash
sldb models add myapp.docs:Book --store .sldb --pythonpath src
sldb models add docs.models:FAQDoc --store .sldb --pythonpath .
```

It does not take a raw file path as the model identifier. If the module is not importable from the default Python path, pass `--pythonpath`.

See the [Model Reference atom](atoms/model-reference.atom.md).

## How do I see which models are already registered?

Use `sldb models list`.

Examples:

```bash
sldb models list --store .sldb
sldb models list --store ~/.sldb --format yaml
```

If you run a store-based command outside a repo with a local `.sldb/`, SLDB will now tell you whether it found a global `~/.sldb/` and whether you should pass `--store` explicitly.

## What identifier do tracked docs use?

A tracked doc has a logical store name plus a physical file path.

Depending on the command, SLDB may accept one of these forms:

- the tracked doc name, such as `roadmap`
- the qualified form, such as `RoadmapDoc/roadmap`
- the physical path, such as `docs/roadmap.md`

The key distinction is:

- physical path = where the Markdown file lives
- tracked doc name = the logical handle inside the store

Use `--name` when you want explicit control over the tracked name instead of relying on derived defaults.

See the [Tracked Document atom](atoms/tracked-document.atom.md).

## What is the difference between `docs create`, `track`, `update`, `recover`, and `compose`?

They do different jobs:

- `docs create`: render a new Markdown file from a registered model plus payload data, then track it
- `docs track`: validate and register an existing Markdown file
- `docs update`: re-render an already tracked doc with new payload data
- `docs recover`: resolve `[[links]]` and report what they point to
- `docs compose`: expand `![[transclusions]]` into a composed output

The important distinction is that `recover` and `compose` are link/transclusion commands, not payload extraction commands.

See the [Compose vs Recover atom](atoms/compose-vs-recover.atom.md).

## What is a `StructuredNLDoc` model?

It is the typed document contract used by SLDB.

It defines:

- the Markdown template
- the Pydantic fields
- the field descriptions
- optional semantics

That contract is what makes `Markdown -> payload -> Markdown` workflows possible.

See the [StructuredNLDoc Model atom](atoms/structurednldoc-model.atom.md).

## What inputs does `docs create` accept?

`docs create` renders from a registered model plus payload data.

The payload can be:

- inline JSON or YAML
- a JSON or YAML file path

Examples:

```bash
sldb docs create --model Book -o docs/book.md '{"title":"My Book"}'
sldb docs create --model Book -o docs/book.md data.yaml
```

It does not generate a template on its own. The template already lives in the registered model.

See the [Payload Inputs atom](atoms/payload-inputs.atom.md).

## How do I interact with the store in practice?

The normal lifecycle is:

```bash
sldb stores init --path .
sldb models add myapp.docs:Book --store .sldb --pythonpath src
sldb docs create --model Book -o docs/book.md data.yaml --store .sldb --pythonpath src
sldb stores check --store .sldb
sldb stores update --store .sldb --pythonpath src
```

Use `stores check` when you want integrity diagnostics. Use `stores update` after bulk changes or when section and semantic indexes need a rebuild.

## How do I inspect the AST, and when should I use it?

Use `ast show` when you want to inspect how SLDB currently understands a store, model, document, section hierarchy, or field ownership.

Examples:

```bash
sldb ast show
sldb ast show models/Book
sldb ast show docs/roadmap
```

This is the best debugging surface when `find`, `sections`, or field ownership results do not match your mental model.

See the [AST View atom](atoms/ast-view.atom.md).

## How do I get data out of SLDB?

There are several ways, depending on the level you want:

- `extract`: get payload from one Markdown file using a direct model ref
- `docs show`: inspect a tracked document payload and AST
- `fields show`: inspect one field schema or one field value
- `fields query`: query the same field path across tracked docs
- `find`: retrieve docs, sections, and fields semantically or physically
- `ast show`: inspect the normalized graph view

Examples:

```bash
sldb extract myapp.docs:Book docs/book.md output.yaml
sldb docs show my-book --store .sldb --format yaml
sldb fields query title --store .sldb --format yaml
```

## How do I update data in tracked docs?

Use the smallest command that matches the change you want:

- `docs update` when you want to re-render the full tracked document from payload data
- `fields update` when you want to change one existing field value
- `fields create` when the field path is missing and should be created
- `fields append` when the target is a list field
- `fields clean` when you want to normalize a list field

Examples:

```bash
sldb docs update my-book '{"title":"Updated Title"}' --store .sldb
sldb fields update docs/my-book/title '"Updated Title"' --store .sldb
sldb fields append docs/my-book/tags '"dessert"' --store .sldb
```

## When should I use `compose` vs `recover`?

Use `recover` when your question is "what links are here and do they resolve?"

Use `compose` when your question is "what does this document look like after transclusions are expanded?"

Examples:

```bash
sldb docs recover roadmap --store .sldb
sldb docs compose roadmap --store .sldb -o -
```

## What should a first-time workflow look like?

If you are evaluating SLDB for the first time:

1. Read `sldb help` and `sldb help docs`.
2. Inspect this repo's [atom docs](atoms/) if a core concept is still unclear.
3. Create a simple `StructuredNLDoc` model with good field descriptions.
4. Run `sldb validate <module:Class> --data data.yaml` or `--input doc.md`.
5. Initialize a store only when you need tracked docs and project-level queries.
6. Use `find`, `sections`, and `ast show` once the store exists.

If you want deeper written guidance from the CLI, use `sldb explore <term>`.

## Is there a CLI command for browsing the FAQ one question at a time?

Yes. Use `sldb faq`.

- `sldb faq` lists the available questions
- `sldb faq 2` shows one question by number
- `sldb faq semantic` shows the first question whose slug or text matches `semantic`

Examples:

```bash
sldb faq
sldb faq store
sldb faq semantic --format yaml
```

This is useful when you want entry-level onboarding help without scanning the whole file.

## Is there a CLI command for logging unclear points or suggestions?

Yes. Use `sldb inbox`.

This command writes a timestamped markdown note into the target project's `desk/inbox/` so confusion and improvement ideas land in the right workspace instead of disappearing into chat history.
If the project already has a store and a registered `InboxNoteDoc`, the note is also auto-tracked into that store.

Examples:

```bash
sldb inbox "The docs still do not define tracked doc names clearly" --kind unclear
sldb inbox "Add more examples for docs update" --kind suggestion --title "docs update examples"
sldb inbox --list
sldb inbox --show tracked-doc-names
```

Use `--kind unclear` for unresolved confusion and `--kind suggestion` for proposed improvements.
If you need to force a different target, pass `--desk-root` explicitly or `--store` to anchor the desk to a specific project root.
