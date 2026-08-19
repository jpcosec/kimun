SECTIONS_HELP = """sldb sections

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
"""

STORES_HELP = """sldb stores

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
"""

FAQ_HELP = """sldb faq

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
"""

INBOX_HELP = """sldb inbox

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
"""


