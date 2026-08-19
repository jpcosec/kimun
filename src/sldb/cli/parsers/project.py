from __future__ import annotations
import argparse

def add_project_commands(s: argparse._SubParsersAction) -> None:
    _init(s)
    _example(s)

def _init(s):
    p = s.add_parser("init", help=argparse.SUPPRESS)
    p.add_argument("path", nargs="?", default=".")
    p.add_argument("--force", action="store_true")

def _example(s):
    p = s.add_parser("example", help=argparse.SUPPRESS)
    p.add_argument("path", nargs="?", default=".")
