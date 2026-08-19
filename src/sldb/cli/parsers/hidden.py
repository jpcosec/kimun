from __future__ import annotations
import argparse
from .legacy import add_raw_query_commands, add_raw_link_commands

def add_hidden_compat_commands(s: argparse._SubParsersAction) -> None:
    _hidden_store(s); _hidden_model(s); _hidden_doc(s)
    add_raw_query_commands(s, hidden=True)
    add_raw_link_commands(s, hidden=True)

def _hidden_store(s):
    p = s.add_parser("store", help=argparse.SUPPRESS)
    sub = p.add_subparsers(dest="store_command", required=True)
    _hs_init(sub); _hs_add(sub); _hs_check(sub); _hs_update(sub); _hs_map(sub)

def _hs_init(s):
    i = s.add_parser("init", help="Init .sldb store.")
    i.add_argument("--path", default=".")
    i.add_argument("--force", action="store_true")

def _hs_add(s):
    a = s.add_parser("add", help="Link federated store.")
    a.add_argument("path")
    a.add_argument("--name")
    a.add_argument("--store")

def _hs_check(s):
    c = s.add_parser("check", help="Integrity check.")
    c.add_argument("--store")
    c.add_argument("--format", choices=("text", "json", "yaml"), default="text")
    c.add_argument("--pythonpath")

def _hs_update(s):
    u = s.add_parser("update", help="Recompute store hashes.")
    u.add_argument("--wait", action="store_true")
    u.add_argument("--verbose", action="store_true")
    u.add_argument("--store")
    u.add_argument("--pythonpath")

def _hs_map(s):
    m = s.add_parser("semantic-map", help="Map equivalent semantic concepts.")
    m.add_argument("concept_a")
    m.add_argument("concept_b")
    m.add_argument("--store")

def _hidden_model(s):
    p = s.add_parser("model", help=argparse.SUPPRESS)
    sub = p.add_subparsers(dest="model_command", required=True)
    a = sub.add_parser("add", help="Register model.")
    a.add_argument("model"); a.add_argument("--store"); a.add_argument("--pythonpath")
    a.add_argument("--canonical", action="store_true")
    u = sub.add_parser("update", help="Update model hashes.")
    u.add_argument("model"); u.add_argument("--store"); u.add_argument("--pythonpath")

def _hidden_doc(s):
    p = s.add_parser("doc", help=argparse.SUPPRESS)
    sub = p.add_subparsers(dest="doc_command", required=True)
    _hd_add(sub); _hd_track(sub); _hd_update(sub); _hd_untrack(sub)

def _hd_add(s):
    a = s.add_parser("add", help="Create + track doc.")
    a.add_argument("--model", required=True); a.add_argument("-o", "--output", required=True)
    a.add_argument("payload"); a.add_argument("--name"); a.add_argument("--store")
    a.add_argument("--pythonpath")

def _hd_track(s):
    t = s.add_parser("track", help="Track existing doc.")
    t.add_argument("path"); t.add_argument("--model", required=True); t.add_argument("--name")
    t.add_argument("--store"); t.add_argument("--pythonpath")
    t.add_argument("--force", action="store_true")

def _hd_update(s):
    u = s.add_parser("update", help="Update doc content.")
    u.add_argument("doc"); u.add_argument("payload"); u.add_argument("--store")
    u.add_argument("--pythonpath")

def _hd_untrack(s):
    r = s.add_parser("untrack", help="Remove a tracked doc from the store.")
    r.add_argument("doc"); r.add_argument("--store"); r.add_argument("--pythonpath")
