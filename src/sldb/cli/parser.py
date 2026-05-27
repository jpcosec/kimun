from __future__ import annotations

import argparse


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="sldb",
        description=(
            "SLDB: structured Markdown models plus an optional store. Use `sldb help` "
            "for first-use guidance and `sldb explore` for deep docs/docstring search."
        ),
        formatter_class=argparse.RawTextHelpFormatter,
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    _add_basic_commands(subparsers)
    _add_project_commands(subparsers)
    _add_public_group_commands(subparsers)
    _add_help_commands(subparsers)
    _add_faq_commands(subparsers)
    _add_inbox_commands(subparsers)
    _add_explore_commands(subparsers)
    _add_ast_commands(subparsers)
    _add_find_commands(subparsers)
    _add_legacy_commands(subparsers)
    _add_hidden_compat_commands(subparsers)
    return parser


def _add_basic_commands(
    subparsers: argparse._SubParsersAction[argparse.ArgumentParser],
) -> None:
    p = subparsers.add_parser("extract", help="Extract data from Markdown.")
    p.add_argument("model", help="Model ref: module:Class")
    p.add_argument("input", help="Markdown file")
    p.add_argument("output", help="Output JSON/YAML")
    p.add_argument("--format", choices=("json", "yaml"), default=None)
    p.add_argument("--pythonpath", help="Project path")

    p = subparsers.add_parser("render", help="Render Markdown from data.")
    p.add_argument("model", help="Model ref: module:Class")
    p.add_argument("input", help="Data file")
    p.add_argument("output", help="Output .md")
    p.add_argument("--pythonpath", help="Project path")

    p = subparsers.add_parser("validate", help="Validate idempotency.")
    p.add_argument("model", help="Model ref: module:Class")
    g = p.add_mutually_exclusive_group(required=True)
    g.add_argument("--input", help="Markdown file")
    g.add_argument("--data", help="Data file")
    p.add_argument("--format", choices=("text", "json", "yaml"), default="text")
    p.add_argument("--pythonpath", help="Project path")


def _add_project_commands(
    subparsers: argparse._SubParsersAction[argparse.ArgumentParser],
) -> None:
    p = subparsers.add_parser("init", help=argparse.SUPPRESS)
    p.add_argument("path", nargs="?", default=".")
    p.add_argument("--force", action="store_true")

    p = subparsers.add_parser("example", help=argparse.SUPPRESS)
    p.add_argument("path", nargs="?", default=".")


def _add_public_group_commands(
    subparsers: argparse._SubParsersAction[argparse.ArgumentParser],
) -> None:
    _add_stores_group(subparsers)
    _add_models_group(subparsers)
    _add_docs_group(subparsers)
    _add_fields_group(subparsers)
    _add_sections_group(subparsers)


def _add_stores_group(
    subparsers: argparse._SubParsersAction[argparse.ArgumentParser],
) -> None:
    p = subparsers.add_parser("stores", help="Store lifecycle and federation.")
    s = p.add_subparsers(dest="stores_command", required=True)

    i = s.add_parser("init", help="Init .sldb store.")
    i.add_argument("--path", default=".")
    i.add_argument("--force", action="store_true")

    a = s.add_parser("add", help="Link federated store.")
    a.add_argument("path", help="Store path")
    a.add_argument("--name", help="Store name")
    a.add_argument("--store", help="Local store path")

    c = s.add_parser("check", help="Integrity check.")
    c.add_argument("--store", help="Store path")
    c.add_argument("--format", choices=("text", "json", "yaml"), default="text")
    c.add_argument("--pythonpath", help="Project path")

    u = s.add_parser("update", help="Recompute store hashes.")
    u.add_argument("--wait", action="store_true", help="Wait for store lock if busy")
    u.add_argument(
        "--verbose", action="store_true", help="Print individual skip details"
    )
    u.add_argument("--store", help="Store path")
    u.add_argument("--pythonpath", help="Project path")

    m = s.add_parser("semantic-map", help="Map equivalent semantic concepts.")
    m.add_argument("concept_a", help="First concept")
    m.add_argument("concept_b", help="Second concept")
    m.add_argument("--store", help="Store path")

    listing = s.add_parser("list", help="List federated stores.")
    listing.add_argument("--store", help="Store path")
    listing.add_argument("--format", choices=("text", "json", "yaml"), default="text")


def _add_models_group(
    subparsers: argparse._SubParsersAction[argparse.ArgumentParser],
) -> None:
    p = subparsers.add_parser("models", help="Model contracts and generation.")
    s = p.add_subparsers(dest="models_command", required=True)

    a = s.add_parser("add", help="Register model.")
    a.add_argument("model", help="Model ref")
    a.add_argument("--store", help="Store path")
    a.add_argument("--pythonpath", help="Project path")
    a.add_argument("--canonical", action="store_true")

    u = s.add_parser("update", help="Update model hashes.")
    u.add_argument("model", help="Model name")
    u.add_argument("--store", help="Store path")
    u.add_argument("--pythonpath", help="Project path")

    listing = s.add_parser("list", help="List registered models.")
    listing.add_argument("--store", help="Store path")
    listing.add_argument("--format", choices=("text", "json", "yaml"), default="text")

    show = s.add_parser("show", help="Show registered model info.")
    show.add_argument("model", help="Model name")
    show.add_argument("--store", help="Store path")
    show.add_argument("--pythonpath", help="Project path")

    validate = s.add_parser("validate", help="Validate a registered model or draft.")
    validate.add_argument("model", help="Model name")
    validate.add_argument("--store", help="Store path")
    validate.add_argument("--pythonpath", help="Project path")
    validate.add_argument(
        "--promote",
        action="store_true",
        help="Promote a valid draft into the active model and reindex it",
    )
    validate.add_argument("--format", choices=("text", "json", "yaml"), default="text")

    template = s.add_parser(
        "template", help="Inspect or edit a registered model template."
    )
    ts = template.add_subparsers(dest="template_command", required=True)

    tshow = ts.add_parser("show", help="Show the active or draft template.")
    tshow.add_argument("model", help="Model name")
    tshow.add_argument("--store", help="Store path")
    tshow.add_argument("--pythonpath", help="Project path")
    tshow.add_argument(
        "--draft",
        action="store_true",
        help="Show the draft template instead of the active one",
    )

    tedit = ts.add_parser("edit", help="Write a template draft for a registered model.")
    tedit.add_argument("model", help="Model name")
    tedit.add_argument("--input", required=True, help="Template markdown path")
    tedit.add_argument("--store", help="Store path")
    tedit.add_argument("--pythonpath", help="Project path")

    fields = s.add_parser("fields", help="Edit registered model fields through drafts.")
    fs = fields.add_subparsers(dest="fields_command", required=True)

    fadd = fs.add_parser("add", help="Add a field to the model draft.")
    fadd.add_argument("model", help="Model name")
    fadd.add_argument("field", help="Field name")
    fadd.add_argument(
        "--type", dest="field_type", required=True, help="Python type annotation"
    )
    fadd.add_argument("--description", required=True, help="Field description")
    fadd.add_argument("--default", help="Inline YAML/JSON default value")
    fadd.add_argument("--store", help="Store path")
    fadd.add_argument("--pythonpath", help="Project path")

    frm = fs.add_parser("remove", help="Remove a field from the model draft.")
    frm.add_argument("model", help="Model name")
    frm.add_argument("field", help="Field name")
    frm.add_argument("--store", help="Store path")
    frm.add_argument("--pythonpath", help="Project path")

    create = s.add_parser("create", help="Generate a StructuredNLDoc model.")
    create.add_argument("name", help="Class name")
    create.add_argument("--template", required=True, help="Template markdown path")
    create.add_argument("--fields", required=True, help="Field spec YAML path")
    create.add_argument("--output", default="-", help="Output Python file or -")
    create.add_argument("--stdout", action="store_true", help="Print generated code")


def _add_docs_group(
    subparsers: argparse._SubParsersAction[argparse.ArgumentParser],
) -> None:
    p = subparsers.add_parser(
        "docs",
        help="Tracked document workflows.",
        description=(
            "Operate on tracked Markdown documents. `create`, `track`, and `update` "
            "work on model payloads. `recover` and `compose` work on explicit "
            "Markdown links and transclusions."
        ),
        formatter_class=argparse.RawTextHelpFormatter,
    )
    s = p.add_subparsers(dest="docs_command", required=True)

    a = s.add_parser("create", help="Create and track document.")
    a.add_argument("--model", required=True)
    a.add_argument("-o", "--output", required=True)
    a.add_argument("payload", help="Data file or inline YAML/JSON")
    a.add_argument("--name", help="Doc name")
    a.add_argument("--store", help="Store path")
    a.add_argument("--pythonpath", help="Project path")

    t = s.add_parser("track", help="Track existing doc.")
    t.add_argument("path", help="Document path")
    t.add_argument("--model", required=True)
    t.add_argument("--name", help="Doc name")
    t.add_argument("--store", help="Store path")
    t.add_argument("--pythonpath", help="Project path")
    t.add_argument("--force", action="store_true")

    u = s.add_parser("update", help="Update doc content.")
    u.add_argument("doc", help="Doc name or path")
    u.add_argument("payload", help="Data file or inline YAML/JSON")
    u.add_argument("--store", help="Store path")
    u.add_argument("--pythonpath", help="Project path")

    rm = s.add_parser("untrack", help="Remove a tracked doc from the store.")
    rm.add_argument("doc", help="Doc name or tracked path")
    rm.add_argument("--store", help="Store path")
    rm.add_argument("--pythonpath", help="Project path")

    show = s.add_parser("show", help="Show document AST and payload.")
    show.add_argument("doc", help="Doc name or Model/DocName or tracked path")
    show.add_argument("--store", help="Store path")
    show.add_argument("--pythonpath", help="Project path")
    show.add_argument("--format", choices=("json", "yaml"), default="json")

    recover = s.add_parser(
        "recover",
        help="Resolve [[links]] and report their targets.",
        description=(
            "Resolve Obsidian-style `[[links]]` inside a document. This command does "
            "not extract payload data; it reports which link targets resolve through "
            "tracked docs or physical paths."
        ),
        epilog=(
            "Examples:\n"
            "  sldb docs recover roadmap --store .sldb\n"
            "  sldb docs recover docs/roadmap.md --links-only\n"
            "  sldb docs recover roadmap --include-transclusions --depth 2 --format yaml\n"
        ),
        formatter_class=argparse.RawTextHelpFormatter,
    )
    recover.add_argument("doc", help="Doc name or path")
    recover.add_argument("--store", help="Store path")
    recover.add_argument(
        "--format",
        choices=("text", "json", "yaml"),
        default="text",
        help="Output format for the recovery report",
    )
    recover.add_argument(
        "--depth",
        type=int,
        default=1,
        help="Recursive recovery depth when following resolved links",
    )
    recover.add_argument(
        "--links-only",
        action="store_true",
        help="Print only unique resolved/unresolved link targets",
    )
    recover.add_argument(
        "--include-transclusions",
        action="store_true",
        help="Also inspect ![[transclusions]] as recoverable targets",
    )

    listing = s.add_parser("list", help="List tracked documents.")
    listing.add_argument("--store", help="Store path")
    listing.add_argument("--format", choices=("text", "json", "yaml"), default="text")

    compose = s.add_parser(
        "compose",
        help="Expand ![[transclusions]] into composed Markdown.",
        description=(
            "Expand Obsidian-style `![[transclusions]]` recursively into one composed "
            "output document. This operates on Markdown content, not on extracted "
            "payload values."
        ),
        epilog=(
            "Examples:\n"
            "  sldb docs compose roadmap --store .sldb -o -\n"
            "  sldb docs compose docs/roadmap.md --format markdown\n"
            "  sldb docs compose roadmap --format yaml\n"
        ),
        formatter_class=argparse.RawTextHelpFormatter,
    )
    compose.add_argument("doc", help="Doc name or path")
    compose.add_argument("--store", help="Store path")
    compose.add_argument(
        "-o",
        "--output",
        default="-",
        help="Output path or - for stdout",
    )
    compose.add_argument(
        "--format",
        choices=("markdown", "json", "yaml"),
        default="markdown",
        help="Return composed markdown or a structured report",
    )

    explore = s.add_parser(
        "explore", help="Search tracked docs, repo docs, and docstrings."
    )
    explore.add_argument("term", help="Search term or regex")
    explore.add_argument(
        "--source",
        choices=("all", "docs", "docstrings"),
        default="all",
        help="Where to search: markdown docs, Python docstrings, or both",
    )
    explore.add_argument("--regex", action="store_true")
    explore.add_argument("--docs-root", default="docs", help="Docs directory to scan")
    explore.add_argument("--code-root", default="src", help="Python source directory to scan")
    explore.add_argument("--max-results", type=int, default=20)
    explore.add_argument("--format", choices=("text", "json", "yaml"), default="text")


def _add_fields_group(
    subparsers: argparse._SubParsersAction[argparse.ArgumentParser],
) -> None:
    p = subparsers.add_parser("fields", help="Field CRUD, append, clean, and query.")
    s = p.add_subparsers(dest="fields_command", required=True)

    show = s.add_parser("show", help="Show model field schema or document field value.")
    show.add_argument("target", help="models/<Model>[/field] or docs/<Doc>/<field>")
    show.add_argument("--store", help="Store path")
    show.add_argument("--pythonpath", help="Project path")
    show.add_argument("--format", choices=("json", "yaml"), default="json")

    query = s.add_parser("query", help="Query a field path across tracked docs.")
    query.add_argument("field", help="Field path, eg status or tasks.status")
    query.add_argument("--store", help="Store path")
    query.add_argument("--pythonpath", help="Project path")
    query.add_argument("--global", dest="global_scope", action="store_true")
    query.add_argument("--format", choices=("json", "yaml"), default="json")

    for name, help_text in (
        ("create", "Create a missing field value."),
        ("update", "Update an existing field value."),
        ("remove", "Remove a field value."),
        ("append", "Append to a list field."),
    ):
        cmd = s.add_parser(name, help=help_text)
        cmd.add_argument(
            "target", help="docs/<Doc>/<field> or docs/<Model>/<Doc>/<field>"
        )
        cmd.add_argument("value", help="Inline YAML/JSON value")
        cmd.add_argument("--store", help="Store path")
        cmd.add_argument("--pythonpath", help="Project path")

    clean = s.add_parser("clean", help="Clean a list field.")
    clean.add_argument(
        "target", help="docs/<Doc>/<field> or docs/<Model>/<Doc>/<field>"
    )
    clean.add_argument("--store", help="Store path")
    clean.add_argument("--pythonpath", help="Project path")
    clean.add_argument("--dedupe", action="store_true")
    clean.add_argument("--drop-empty", action="store_true")


def _add_sections_group(
    subparsers: argparse._SubParsersAction[argparse.ArgumentParser],
) -> None:
    p = subparsers.add_parser("sections", help="Section context and navigation.")
    s = p.add_subparsers(dest="sections_command", required=True)

    show = s.add_parser("show", help="Show sections in a document.")
    show.add_argument("doc", help="Doc name or Model/DocName")
    show.add_argument("--store", help="Store path")
    show.add_argument("--pythonpath", help="Project path")
    show.add_argument("--format", choices=("json", "yaml", "text"), default="text")

    find = s.add_parser("find", help="Search sections semantically or physically.")
    find.add_argument("term")
    find.add_argument(
        "--in",
        dest="search_in",
        choices=("semantic", "physical", "both"),
        default="both",
    )
    find.add_argument("--store", help="Store path")
    find.add_argument("--pythonpath", help="Project path")
    find.add_argument("--global", dest="global_scope", action="store_true")
    find.add_argument("--regex", action="store_true")
    find.add_argument("--fuzzy", action="store_true")
    find.add_argument(
        "--rebuild",
        action="store_true",
        help="Force runtime IR rebuild (ignore persisted section index)",
    )
    find.add_argument("--where", help="Section context predicate")
    find.add_argument("--format", choices=("json", "yaml", "text"), default="text")

    fields = s.add_parser("fields", help="Show fields owned by a section.")
    fields.add_argument("target", help="docs/<Doc> or docs/<Doc>/<section_path>")
    fields.add_argument("--store", help="Store path")
    fields.add_argument("--pythonpath", help="Project path")
    fields.add_argument("--format", choices=("json", "yaml", "text"), default="json")


def _add_help_commands(
    subparsers: argparse._SubParsersAction[argparse.ArgumentParser],
) -> None:
    p = subparsers.add_parser("help", help="Curated CLI help.")
    p.add_argument(
        "topic",
        nargs="?",
        help="stores, models, docs, fields, sections, ast, find, faq, inbox, explore, legacy",
    )


def _add_faq_commands(
    subparsers: argparse._SubParsersAction[argparse.ArgumentParser],
) -> None:
    p = subparsers.add_parser("faq", help="Browse the first-use FAQ by question.")
    p.add_argument(
        "question",
        nargs="?",
        help="Question index, slug, or text fragment. Omit to list available questions.",
    )
    p.add_argument("--format", choices=("text", "json", "yaml"), default="text")
    p.add_argument("--faq-path", default="docs/faq.md", help="FAQ markdown path")


def _add_inbox_commands(
    subparsers: argparse._SubParsersAction[argparse.ArgumentParser],
) -> None:
    p = subparsers.add_parser(
        "inbox", help="Log unclear points or suggestions into the repo desk."
    )
    p.add_argument("message", nargs="?", help="Inbox note body")
    p.add_argument(
        "--kind",
        choices=("unclear", "suggestion"),
        default="unclear",
        help="Type of desk note to write",
    )
    p.add_argument("--title", help="Short title for the note")
    p.add_argument(
        "--desk-root",
        help="Desk root directory override; defaults to the active project desk",
    )
    p.add_argument(
        "--store",
        help="Store path used to resolve the target project root for the default desk",
    )
    p.add_argument(
        "--pythonpath",
        help="Project path used when auto-tracking inbox notes through a registered InboxNoteDoc model",
    )
    p.add_argument("--author", default="cli", help="Source label for the inbox note")
    p.add_argument("--list", action="store_true", help="List desk inbox notes")
    p.add_argument("--show", help="Show one inbox note by filename, stem, or slug fragment")
    p.add_argument("--limit", type=int, default=20, help="Limit listed notes")
    p.add_argument("--format", choices=("text", "json", "yaml"), default="text")


def _add_explore_commands(
    subparsers: argparse._SubParsersAction[argparse.ArgumentParser],
) -> None:
    p = subparsers.add_parser(
        "explore", help="Search markdown docs and Python docstrings."
    )
    p.add_argument("term", help="Search term or regex")
    p.add_argument(
        "--source",
        choices=("all", "docs", "docstrings"),
        default="all",
        help="Where to search: markdown docs, Python docstrings, or both",
    )
    p.add_argument("--regex", action="store_true")
    p.add_argument("--docs-root", default="docs", help="Docs directory to scan")
    p.add_argument("--code-root", default="src", help="Python source directory to scan")
    p.add_argument("--max-results", type=int, default=20)
    p.add_argument("--format", choices=("text", "json", "yaml"), default="text")


def _add_ast_commands(
    subparsers: argparse._SubParsersAction[argparse.ArgumentParser],
) -> None:
    p = subparsers.add_parser("ast", help="Inspect the normalized SLDB graph.")
    s = p.add_subparsers(dest="ast_command", required=True)

    show = s.add_parser("show", help="Show AST for a target.")
    show.add_argument("target", nargs="?", default="store")
    show.add_argument("--store", help="Store path")
    show.add_argument("--pythonpath", help="Project path")
    show.add_argument("--format", choices=("json", "yaml", "text"), default="json")

    schema = s.add_parser("schema", help="Show node and edge schema.")
    schema.add_argument("--format", choices=("json", "yaml", "text"), default="json")


def _add_find_commands(
    subparsers: argparse._SubParsersAction[argparse.ArgumentParser],
) -> None:
    p = subparsers.add_parser(
        "find",
        help="Unified semantic + physical retrieval.",
        description=(
            "Search the SLDB graph by physical structure, semantic tags, or both. "
            "Use `physical` for names, paths, section titles, and field addresses. "
            "Use `semantic` for tags or meaning-like concepts."
        ),
        epilog=(
            "Examples:\n"
            "  sldb find roadmap --in physical --type doc\n"
            "  sldb find type.documentation.architecture --in semantic --type doc\n"
            "  sldb find tasks --type section --where '\"Roadmap\" in breadcrumbs'\n"
            "  sldb find title --type field --select doc,field,value\n\n"
            "Typical shapes:\n"
            "  term      a path-like token, tracked name, section title, or semantic tag\n"
            "  --where   a simple filter such as 'value = \"open\"' or '\"X\" in breadcrumbs'\n"
            "  --select  comma-separated output fields such as doc,path,value\n"
        ),
        formatter_class=argparse.RawTextHelpFormatter,
    )
    p.add_argument("term", help="Resource term, semantic tag, or physical token")
    p.add_argument(
        "--in",
        dest="search_in",
        choices=("semantic", "physical", "both"),
        default="both",
    )
    p.add_argument("--global", dest="global_scope", action="store_true")
    p.add_argument("--regex", action="store_true")
    p.add_argument("--fuzzy", action="store_true")
    p.add_argument(
        "--type",
        choices=("all", "store", "model", "doc", "section", "field"),
        default="all",
    )
    p.add_argument("--select", help="Comma-separated projection fields")
    p.add_argument("--where", help="Filter expression")
    p.add_argument(
        "--rebuild",
        action="store_true",
        help="Force runtime IR rebuild (ignore persisted section index)",
    )
    p.add_argument("--store", help="Store path")
    p.add_argument("--pythonpath", help="Project path")
    p.add_argument("--format", choices=("text", "json", "yaml"), default="text")


def _add_legacy_commands(
    subparsers: argparse._SubParsersAction[argparse.ArgumentParser],
) -> None:
    p = subparsers.add_parser(
        "legacy", help="Compatibility surface for the raw query DSL."
    )
    s = p.add_subparsers(dest="legacy_command", required=True)
    _add_raw_query_commands(s)
    _add_raw_link_commands(s)


def _add_hidden_compat_commands(
    subparsers: argparse._SubParsersAction[argparse.ArgumentParser],
) -> None:
    _add_hidden_store_group(subparsers)
    _add_hidden_model_group(subparsers)
    _add_hidden_doc_group(subparsers)
    _add_hidden_raw_query_commands(subparsers)
    _add_hidden_raw_link_commands(subparsers)


def _add_hidden_store_group(
    subparsers: argparse._SubParsersAction[argparse.ArgumentParser],
) -> None:
    p = subparsers.add_parser("store", help=argparse.SUPPRESS)
    s = p.add_subparsers(dest="store_command", required=True)
    i = s.add_parser("init", help="Init .sldb store.")
    i.add_argument("--path", default=".")
    i.add_argument("--force", action="store_true")
    a = s.add_parser("add", help="Link federated store.")
    a.add_argument("path")
    a.add_argument("--name")
    a.add_argument("--store")
    c = s.add_parser("check", help="Integrity check.")
    c.add_argument("--store")
    c.add_argument("--format", choices=("text", "json", "yaml"), default="text")
    c.add_argument("--pythonpath")
    u = s.add_parser("update", help="Recompute store hashes.")
    u.add_argument("--wait", action="store_true", help="Wait for store lock if busy")
    u.add_argument(
        "--verbose", action="store_true", help="Print individual skip details"
    )
    u.add_argument("--store")
    u.add_argument("--pythonpath")
    m = s.add_parser("semantic-map", help="Map equivalent semantic concepts.")
    m.add_argument("concept_a")
    m.add_argument("concept_b")
    m.add_argument("--store")


def _add_hidden_model_group(
    subparsers: argparse._SubParsersAction[argparse.ArgumentParser],
) -> None:
    p = subparsers.add_parser("model", help=argparse.SUPPRESS)
    s = p.add_subparsers(dest="model_command", required=True)
    a = s.add_parser("add", help="Register model.")
    a.add_argument("model")
    a.add_argument("--store")
    a.add_argument("--pythonpath")
    a.add_argument("--canonical", action="store_true")
    u = s.add_parser("update", help="Update model hashes.")
    u.add_argument("model")
    u.add_argument("--store")
    u.add_argument("--pythonpath")


def _add_hidden_doc_group(
    subparsers: argparse._SubParsersAction[argparse.ArgumentParser],
) -> None:
    p = subparsers.add_parser("doc", help=argparse.SUPPRESS)
    s = p.add_subparsers(dest="doc_command", required=True)
    a = s.add_parser("add", help="Create + track doc.")
    a.add_argument("--model", required=True)
    a.add_argument("-o", "--output", required=True)
    a.add_argument("payload")
    a.add_argument("--name")
    a.add_argument("--store")
    a.add_argument("--pythonpath")
    t = s.add_parser("track", help="Track existing doc.")
    t.add_argument("path")
    t.add_argument("--model", required=True)
    t.add_argument("--name")
    t.add_argument("--store")
    t.add_argument("--pythonpath")
    t.add_argument("--force", action="store_true")
    u = s.add_parser("update", help="Update doc content.")
    u.add_argument("doc")
    u.add_argument("payload")
    u.add_argument("--store")
    u.add_argument("--pythonpath")
    r = s.add_parser("untrack", help="Remove a tracked doc from the store.")
    r.add_argument("doc")
    r.add_argument("--store")
    r.add_argument("--pythonpath")


def _add_hidden_raw_query_commands(
    subparsers: argparse._SubParsersAction[argparse.ArgumentParser],
) -> None:
    _add_raw_query_commands(subparsers, hidden=True)


def _add_hidden_raw_link_commands(
    subparsers: argparse._SubParsersAction[argparse.ArgumentParser],
) -> None:
    _add_raw_link_commands(subparsers, hidden=True)


def _add_raw_query_commands(
    subparsers: argparse._SubParsersAction[argparse.ArgumentParser],
    hidden: bool = False,
) -> None:
    p = subparsers.add_parser(
        "ls", help=argparse.SUPPRESS if hidden else "List raw nodes."
    )
    p.add_argument("address", help="Address (st.*, se.*)")
    p.add_argument("--store", help="Store path")
    p.add_argument("--pythonpath", help="Project path")

    p = subparsers.add_parser(
        "get", help=argparse.SUPPRESS if hidden else "Get raw node data."
    )
    p.add_argument("address", help="Address")
    p.add_argument("--store", help="Store path")
    p.add_argument("--format", choices=("json", "yaml", "text"), default="json")
    p.add_argument("--pythonpath", help="Project path")

    p = subparsers.add_parser(
        "glob", help=argparse.SUPPRESS if hidden else "Expand wildcard addresses."
    )
    p.add_argument("address", help="Wildcard address")
    p.add_argument("--store", help="Store path")
    p.add_argument("--pythonpath", help="Project path")

    p = subparsers.add_parser(
        "raw-find" if hidden else "find",
        help=argparse.SUPPRESS if hidden else "Filter raw query results.",
    )
    p.add_argument("address", help="Address scope")
    p.add_argument("--where", required=True, help="Predicate expression")
    p.add_argument("--store", help="Store path")
    p.add_argument("--pythonpath", help="Project path")
    p.set_defaults(legacy_query_name="find")


def _add_raw_link_commands(
    subparsers: argparse._SubParsersAction[argparse.ArgumentParser],
    hidden: bool = False,
) -> None:
    p = subparsers.add_parser(
        "recover", help=argparse.SUPPRESS if hidden else "Recover links."
    )
    p.add_argument("doc", help="Doc name or path")
    p.add_argument("--store", help="Store path")
    p.add_argument("--format", choices=("text", "json", "yaml"), default="text")
    p.add_argument("--depth", type=int, default=1, help="Recovery depth (default: 1)")
    p.add_argument("--links-only", action="store_true", help="Only return link targets")
    p.add_argument("--include-transclusions", action="store_true")

    p = subparsers.add_parser(
        "compose", help=argparse.SUPPRESS if hidden else "Compose transclusions."
    )
    p.add_argument("doc", help="Doc name or path")
    p.add_argument("--store", help="Store path")
    p.add_argument("-o", "--output", default="-")
    p.add_argument("--format", choices=("markdown", "json", "yaml"), default="markdown")
