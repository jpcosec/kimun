from __future__ import annotations
import argparse

def add_predicates_group(s: argparse._SubParsersAction) -> None:
    p = s.add_parser("predicates", help="Store-backed semantic link predicates.")
    sub = p.add_subparsers(dest="predicates_command", required=True)
    _add(sub); _list(sub); _show(sub); _validate(sub); _remove(sub)

def _add(s):
    a = s.add_parser("add", help="Register a predicate definition.")
    a.add_argument("name", help="Predicate name used in [name:: [[target]]].")
    a.add_argument("--axis", required=True, help="Semantic axis, for example HOW.")
    a.add_argument("--description", default="", help="Predicate description.")
    a.add_argument("--store", help="Store path")

def _list(s):
    l = s.add_parser("list", help="List predicate definitions.")
    l.add_argument("--store", help="Store path")
    l.add_argument("--format", choices=("text", "json", "yaml"), default="text")

def _show(s):
    sh = s.add_parser("show", help="Show one predicate definition.")
    sh.add_argument("name", help="Predicate name")
    sh.add_argument("--store", help="Store path")
    sh.add_argument("--format", choices=("text", "json", "yaml"), default="text")

def _validate(s):
    v = s.add_parser("validate", help="Validate predicate definitions.")
    v.add_argument("name", nargs="?", help="Optional predicate name")
    v.add_argument("--store", help="Store path")
    v.add_argument("--format", choices=("text", "json", "yaml"), default="text")

def _remove(s):
    r = s.add_parser("remove", help="Remove a predicate definition.")
    r.add_argument("name", help="Predicate name")
    r.add_argument("--store", help="Store path")
