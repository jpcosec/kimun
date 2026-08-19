TOP_LEVEL_HELP = """SLDB CLI

Run commands as `sldb ...` or `python -m sldb ...`, not `bash sldb ...`.

SLDB works with three core ideas: a model ref such as `myapp.docs:Book` names a
`StructuredNLDoc` contract, a tracked doc is one Markdown file registered under a
short store name such as `book` or `Book/book`, and a store is the optional `.sldb/`
workspace that keeps model registrations, tracked doc indexes, hashes, and semantic
artifacts without owning the Markdown files themselves. `physical` search means names,
paths, sections, and field addresses. `semantic` search means explicit tags and derived
document/section meaning.

Common workflows:
  sldb stores init --path .
  sldb models add myapp.docs:Book --store .sldb --pythonpath src
  sldb models list --store .sldb
  sldb docs create --model Book -o docs/book.md data.yaml --store .sldb --pythonpath src
  sldb help docs
  sldb faq
  sldb faq store
  sldb find docs --in physical
  sldb find type.documentation.Readme --in semantic --global
  sldb ast show docs/book
  sldb explore store
  sldb inbox "The meaning of semantic vs physical is still unclear" --kind unclear
  sldb inbox --list
  sldb fields append docs/book/tasks '{"title":"Ship CLI","status":"open"}'

Primary surfaces:
  help      Curated first-use guidance
  faq       Question-oriented onboarding answers
  inbox     Log unclear points or suggestions to the active project desk
  stores    Store lifecycle and federation
  models    Model contracts and code generation
  predicates Store-backed semantic link predicates
  docs      Tracked document workflows
  fields    Field inspection and mutation
  sections  Section context and navigation
  ast       Normalized store/model/document graph
  find      Unified semantic + physical retrieval
  explore   Deep markdown docs and docstring search

Advanced:
  legacy    Raw query and link commands from the pre-redesign CLI

Use `sldb help <topic>` for focused help on: stores, models, predicates, docs, fields, sections, ast, find, faq, inbox, explore, legacy.
"""

SHORT_ARGPARSE_HELP = """SLDB CLI

Run commands as `sldb ...` or `python -m sldb ...`, not `bash sldb ...`.

SLDB's main workflow is:
  1. `stores init` to create a project workspace when you need tracked docs
  2. `models add module:Class` to register a `StructuredNLDoc` contract
  3. `models list` to inspect what the store already knows
  4. `docs create` or `docs track` to bring Markdown into the store
  5. `fields`, `sections`, `find`, and `ast` to inspect or mutate tracked data

Primary surfaces:
  help      Curated first-use guidance
  faq       Question-oriented onboarding answers
  stores    Store lifecycle and federation
  models    Model contracts and code generation
  predicates Store-backed semantic link predicates
  docs      Tracked document workflows
  fields    Field inspection and mutation
  sections  Section context and navigation
  find      Unified semantic + physical retrieval
  ast       Normalized store/model/document graph
  explore   Deep markdown docs and docstring search
  inbox     Log unclear points or suggestions to desk/inbox/

Other commands:
  extract, render, validate   Direct model-first operations without a store
  init, example               Bootstrapping helpers
  legacy                      Compatibility surface for older raw commands

Use `sldb help` for the full onboarding help, `sldb find --help` for query examples,
and `sldb docs --help` for document lifecycle details.
"""

TOPIC_HELP = {
    "find": """sldb find

Unified retrieval over the SLDB graph.

Use `--in physical` when you know a file name, tracked doc name, field path, or section
title. Use `--in semantic` when you know a tag or concept such as
`type.documentation.Readme`. Use `--in both` when you want SLDB to search both layers.

Examples:
  sldb find docs --in physical
  sldb find models
  sldb find status --in both --where 'value = "open"'
  sldb find Readme --in semantic --global
  sldb find auth --in physical --regex
  sldb find tasks --type section --where '"Tasks" in about'
  sldb find tasks --type section --where '"Roadmap" in breadcrumbs'

Flags:
  --in semantic|physical|both   Search mode, default both
  --global                      Include linked stores
  --regex                       Treat the term as a regex
  --fuzzy                       Use fuzzy matching
  --type                        Restrict to store|model|doc|section|field
  --where                       Filter docs/fields/sections using a predicate

Section --where predicates:
  title ~ "pattern"             Regex match on section title
  "term" in about               Term in derived about vocabulary
  "term" in breadcrumbs         Term in hierarchical breadcrumbs
  "tag" in semantic_tags        Tag in document-level semantic tags
  path = "..."                  Exact section path match
""",
    "ast": """sldb ast

Return the normalized AST/graph that SLDB defines.

Use this when you want to inspect how SLDB sees a tracked resource, including sections,
field ownership, context index, and the semantic/physical edges that power navigation.

Examples:
  sldb ast show
  sldb ast show models/Book
  sldb ast show docs/my-book
  sldb ast show fields/status
  sldb ast schema
""",
    "fields": """sldb fields

CRUD plus append/clean operations over model-shaped document fields.

Use fields commands when you want to read or mutate data values without hand-editing the
whole Markdown file. Targets always point into either a registered model schema or a
tracked document payload.

Examples:
  sldb fields show models/Book
  sldb fields show docs/my-book/title
  sldb fields update docs/my-book/title 'Updated title'
  sldb fields append docs/my-book/tasks '{"title":"Fix bug","status":"open"}'
  sldb fields clean docs/my-book/tasks --dedupe --drop-empty

Target forms:
  models/<Model>
  models/<Model>/<field.path>
  docs/<DocName>/<field.path>
  docs/<Model>/<DocName>/<field.path>
""",
    "docs": """sldb docs

Document lifecycle commands.

`docs create` renders a new Markdown file from a registered model plus inline YAML/JSON
or a YAML/JSON file, then tracks it. `docs track` validates and registers an existing
Markdown file. `docs update` re-renders an already tracked doc with new payload data.
`docs recover` resolves `[[links]]` and `[predicate:: [[links]]]`. Predicate axes come
from definitions registered in the selected store. Unregistered predicates use `CUSTOM`.
`docs compose` expands `![[transclusions]]`.

Examples:
  sldb docs create --model Book -o docs/book.md data.yaml
  sldb docs track docs/book.md --model Book
  sldb docs update my-book '{"title":"Updated"}'
  sldb docs show my-book
  sldb docs recover my-book --store .sldb --format json
  sldb docs compose my-book -o -
  sldb docs explore compose --source all
""",
    "predicates": """sldb predicates

Manage the predicate vocabulary used by `[predicate:: [[target]]]` links.

Definitions are persisted in the selected store. Each definition has a link name, a
semantic axis, and an optional description. New stores include editable defaults.
Unknown or removed predicates remain parseable and recover with axis `CUSTOM`.

Examples:
  sldb predicates add depends_on --axis DEPENDENCY --description "Declares a dependency." --store .sldb
  sldb predicates list --store .sldb
  sldb predicates show depends_on --store .sldb --format yaml
  sldb predicates validate --store .sldb
  sldb predicates validate depends_on --store .sldb --format json
  sldb predicates remove depends_on --store .sldb
  sldb docs recover doc.md --store .sldb --format json

Subcommands:
  add       Register a unique predicate name and axis
  list      List definitions in text, JSON, or YAML
  show      Show one definition
  validate  Validate one definition or the complete registry
  remove    Remove a definition; existing links then use `CUSTOM`
""",
    "models": """sldb models

Model contract commands.

`models list` shows the registered model names in one store. `models add` expects a
Python import reference in the form `module:ClassName`. The class must be a
`StructuredNLDoc` subclass. Use `--pythonpath` when the module lives in your current
project instead of an installed package.

Examples:
  sldb models list --store .sldb
  sldb models add myapp.docs:Book --store .sldb --pythonpath src
  sldb models update Book --store .sldb --pythonpath src
  sldb models create Book --template book.md --fields fields.yaml --output myapp/models.py
""",
    "sections": """sldb sections

Section context and navigation.

Sections are heading-based views over tracked docs. Use them when a shallow `title + body`
document model still needs deeper navigation through headings, breadcrumbs, and local
context terms.

Examples:
  sldb sections show docs/roadmap
  sldb sections find tasks --in semantic
  sldb sections find tasks --where '"Roadmap" in breadcrumbs'
  sldb sections fields docs/roadmap/roadmap
  sldb sections fields docs/roadmap/roadmap/tasks

Subcommands:
  show    List sections in a document with context
  find    Search sections by term or section context predicate
  fields  Show fields owned by a section path
""",
    "stores": """sldb stores

Store lifecycle commands.

A store is the `.sldb/` workspace for model registrations, tracked doc indexes, hashes,
and semantic artifacts. A local `.sldb/` is the normal project workflow, but a global
`~/.sldb/` can also exist and local takes precedence when both are present. When no local
store exists, store-based commands now tell you whether a global store was found and
whether you should pass `--store` explicitly.

Examples:
  sldb stores init --path .
  sldb stores add ~/.sldb/shared --name shared
  sldb stores check --store .sldb
  sldb stores update --store .sldb --pythonpath src
""",
    "faq": """sldb faq

Browse onboarding questions without opening the whole FAQ markdown file.

Examples:
  sldb faq
  sldb faq 2
  sldb faq store
  sldb faq semantic --format yaml

Behavior:
  no question    List available questions with indexes and slugs
  index          Show one question by number
  slug/text      Show the first matching question by slug or text fragment
""",
    "inbox": """sldb inbox

Write an unclear point or suggestion into the target project's `desk/inbox/` as a timestamped markdown note.

If the active project has a store and a registered `InboxNoteDoc`, new notes are also
auto-tracked into that store.

Short title-only placeholders are rejected. Give the note at least one short explanatory
sentence or use a multi-line body.

Examples:
  sldb inbox "The docs still do not define tracked doc names clearly" --kind unclear
  sldb inbox "Add examples for docs update" --kind suggestion --title "docs update examples"
  sldb inbox --list
  sldb inbox --show 20260520-120000-unclear-tracked-doc-names

Flags:
  --kind unclear|suggestion   Type of desk note to write
  --title TEXT                Optional H1 for the note
  --desk-root PATH            Desk root override instead of the active project desk
  --store PATH                Resolve the target project desk from this store
  --pythonpath PATH           Project path for resolving a registered InboxNoteDoc model
  --author TEXT               Source label, default `cli`
  --list                      List inbox notes
  --show ID                   Show one inbox note by id or slug fragment
""",
    "explore": """sldb explore

Deep search over markdown docs and Python docstrings.

Use this when curated help is not enough and you want to inspect the written knowledge in
`docs/`, `README.md`, `.sldb/README.md`, or Python docstrings across `src/`.

Examples:
  sldb explore store
  sldb explore StructuredNLDoc --source docstrings
  sldb explore compose --source docs
  sldb explore 'semantic.*physical' --regex --source all

Flags:
  --source all|docs|docstrings  Restrict where SLDB searches
  --regex                       Treat the term as a regex
  --docs-root PATH              Docs directory to scan, default `docs`
  --code-root PATH              Python source directory to scan, default `src`
  --max-results N               Limit result count, default 20
""",
    "legacy": """sldb legacy

Compatibility surface for the raw query/link commands.

Examples:
  sldb legacy ls st
  sldb legacy get 'st.{Book}.my-book.title'
  sldb legacy find 'st.{Book}' --where 'status = "accepted"'
  sldb legacy recover my-book
""",
}
