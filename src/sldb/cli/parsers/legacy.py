from __future__ import annotations
import argparse

def add_legacy_commands(s: argparse._SubParsersAction) -> None:
    p = s.add_parser("legacy", help="Compatibility surface for the raw query DSL.")
    sub = p.add_subparsers(dest="legacy_command", required=True)
    add_raw_query_commands(sub)
    add_raw_link_commands(sub)

def add_raw_query_commands(s: argparse._SubParsersAction, hidden: bool = False) -> None:
    _ls(s, hidden); _get(s, hidden); _glob(s, hidden); _find(s, hidden)

def _ls(s, hidden):
    p = s.add_parser("ls", help=argparse.SUPPRESS if hidden else "List raw nodes.")
    p.add_argument("address", help="Address (st.*, se.*)")
    p.add_argument("--store", help="Store path")
    p.add_argument("--pythonpath", help="Project path")

def _get(s, hidden):
    p = s.add_parser("get", help=argparse.SUPPRESS if hidden else "Get raw node data.")
    p.add_argument("address", help="Address")
    p.add_argument("--store", help="Store path")
    p.add_argument("--format", choices=("json", "yaml", "text"), default="json")
    p.add_argument("--pythonpath", help="Project path")

def _glob(s, hidden):
    p = s.add_parser("glob", help=argparse.SUPPRESS if hidden else "Expand wildcard addresses.")
    p.add_argument("address", help="Wildcard address")
    p.add_argument("--store", help="Store path")
    p.add_argument("--pythonpath", help="Project path")

def _find(s, hidden):
    p = s.add_parser("raw-find" if hidden else "find", help=argparse.SUPPRESS if hidden else "Filter raw query results.")
    p.add_argument("address", help="Address scope")
    p.add_argument("--where", required=True, help="Predicate expression")
    p.add_argument("--store", help="Store path")
    p.add_argument("--pythonpath", help="Project path")
    p.set_defaults(legacy_query_name="find")

def add_raw_link_commands(s: argparse._SubParsersAction, hidden: bool = False) -> None:
    _recover(s, hidden); _compose(s, hidden)

def _recover(s, hidden):
    p = s.add_parser("recover", help=argparse.SUPPRESS if hidden else "Recover links.")
    p.add_argument("doc", help="Doc name or path")
    p.add_argument("--store", help="Store path")
    p.add_argument("--format", choices=("text", "json", "yaml"), default="text")
    p.add_argument("--depth", type=int, default=1, help="Recovery depth (default: 1)")
    p.add_argument("--links-only", action="store_true", help="Only return link targets")
    p.add_argument("--include-transclusions", action="store_true")

def _compose(s, hidden):
    p = s.add_parser("compose", help=argparse.SUPPRESS if hidden else "Compose transclusions.")
    p.add_argument("doc", help="Doc name or path")
    p.add_argument("--store", help="Store path")
    p.add_argument("-o", "--output", default="-")
    p.add_argument("--format", choices=("markdown", "json", "yaml"), default="markdown")
