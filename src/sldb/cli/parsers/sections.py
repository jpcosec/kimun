from __future__ import annotations
import argparse

def add_sections_group(s: argparse._SubParsersAction) -> None:
    p = s.add_parser("sections", help="Section context and navigation.")
    sub = p.add_subparsers(dest="sections_command", required=True)
    _show(sub); _find(sub); _fields(sub)

def _show(s):
    sh = s.add_parser("show", help="Show sections in a document.")
    sh.add_argument("doc", help="Doc name or Model/DocName")
    sh.add_argument("--store", help="Store path")
    sh.add_argument("--pythonpath", help="Project path")
    sh.add_argument("--format", choices=("json", "yaml", "text"), default="text")

def _find(s):
    f = s.add_parser("find", help="Search sections semantically or physically.")
    f.add_argument("term")
    f.add_argument("--in", dest="search_in", choices=("semantic", "physical", "both"), default="both")
    f.add_argument("--store", help="Store path")
    f.add_argument("--pythonpath", help="Project path")
    for flag in ("--global", "--regex", "--fuzzy", "--rebuild"):
        f.add_argument(flag, action="store_true", dest="global_scope" if flag == "--global" else None)
    f.add_argument("--where", help="Section context predicate")
    f.add_argument("--format", choices=("json", "yaml", "text"), default="text")

def _fields(s):
    f = s.add_parser("fields", help="Show fields owned by a section.")
    f.add_argument("target", help="docs/<Doc> or docs/<Doc>/<section_path>")
    f.add_argument("--store", help="Store path")
    f.add_argument("--pythonpath", help="Project path")
    f.add_argument("--format", choices=("json", "yaml", "text"), default="json")
