#!/usr/bin/env python3
"""argparse CLI adapter (protocol 1): port of sldb v1 selfdoc ParserScanner.

Imports ``--factory module:attr`` (ROOT on sys.path), builds the parser without
dispatching any handler and prints one fact per command/group and per argument.
"""
from __future__ import annotations

import sys

sys.dont_write_bytecode = True

import argparse
import importlib
import json
from enum import Enum
from pathlib import Path
from typing import Any

NAME = "argparse-cli"
OPTIONS = [{"name": "--factory", "help": "module:callable returning an argparse.ArgumentParser (required)"},
           {"name": "--pythonpath", "help": "extra sys.path entry, relative to ROOT unless absolute"},
           {"name": "--include-hidden", "help": "also emit commands/arguments whose help is SUPPRESS"},
           {"name": "--system", "help": "system name (accepted, not used by the scan)"}]


def describe() -> dict:
    return {"adapter": NAME, "protocol": 1, "profile": "cli", "languages": ["python"],
            "options": OPTIONS, "runtime": {"available": True, "missing": []}}


def qualified_name(value: Any) -> str:
    return f"{value.__module__}.{value.__qualname__}"


def _json_default(value: Any) -> Any:
    if isinstance(value, Path):
        return str(value)
    if isinstance(value, Enum):
        return value.value
    if callable(value) and hasattr(value, "__qualname__"):
        return qualified_name(value)
    raise ValueError(f"Unsupported parser metadata type: {type(value).__name__}")


def json_value(value: Any) -> str:
    return json.dumps(value, sort_keys=True, ensure_ascii=False, default=_json_default)


def argument_record(action: argparse.Action) -> dict:
    return {"names": list(action.option_strings or [action.dest]), "required": bool(action.required),
            "default": "SUPPRESS" if action.default == argparse.SUPPRESS else json_value(action.default),
            "choices": None if action.choices is None else [json_value(v) for v in action.choices],
            "nargs": action.nargs,
            "value_type": qualified_name(action.type) if callable(action.type) else action.type,
            "action": qualified_name(type(action)), "const": json_value(action.const), "help": action.help or ""}


def own_actions(parser: argparse.ArgumentParser, include_hidden: bool) -> list:
    return [a for a in parser._actions
            if not isinstance(a, (argparse._SubParsersAction, argparse._HelpAction))
            and (include_hidden or a.help != argparse.SUPPRESS)]


def constraints(parser: argparse.ArgumentParser) -> list[dict]:
    return [{"required": bool(g.required), "arguments": [a.dest for a in g._group_actions]}
            for g in parser._mutually_exclusive_groups]


class Walker:
    """Same traversal as v1 ParserScanner, keeping the actions for per-argument facts."""

    def __init__(self, include_hidden: bool) -> None:
        self.include_hidden = include_hidden
        self.records: list[dict] = []

    def walk(self, parser, path, synopsis, inherited, groups, hidden) -> None:
        actions = own_actions(parser, self.include_hidden)
        own = [argument_record(a) for a in actions]
        all_args, all_groups = inherited + own, groups + constraints(parser)
        subs = [a for a in parser._actions if isinstance(a, argparse._SubParsersAction)]
        if path:
            self.records.append({"path": path, "synopsis": synopsis, "description": parser.description or "",
                                 "usage": " ".join(parser.format_usage().split()), "group": bool(subs),
                                 "hidden": hidden, "arguments": all_args, "exclusive_groups": all_groups,
                                 "own": list(zip((a.dest for a in actions), own))})
        for action in subs:
            self.children(action, path, all_args, all_groups, hidden)

    def children(self, action, path, inherited, groups, hidden) -> None:
        helps = {item.dest: item.help for item in action._choices_actions}
        for name, child in sorted(action.choices.items()):
            help_text = helps.get(name, "") or ""
            suppressed = hidden or help_text == argparse.SUPPRESS
            if suppressed and not self.include_hidden:
                continue
            self.walk(child, [*path, name], "" if suppressed else help_text, inherited, groups, suppressed)


def facts_of(records: list[dict]) -> list[dict]:
    out, ids = [], set()
    for r in records:
        cid = " ".join(r["path"])
        out.append(_fact(id=cid, kind="group" if r["group"] else "command", name=r["path"][-1],
                         qualified_name=cid, signature=r["usage"], synopsis=r["synopsis"] or None,
                         doc=r["description"] or None,
                         attrs={"hidden": r["hidden"], "exclusive_groups": r["exclusive_groups"],
                                "arguments": r["arguments"], "path": r["path"]}))
        ids.add(cid)
        seen: dict[str, int] = {}
        for dest, rec in r["own"]:
            n = seen[dest] = seen.get(dest, 0) + 1
            aid = f"{cid}#{dest}" if n == 1 else f"{cid}#{dest}~{n}"
            out.append(_fact(id=aid, kind="argument", name=rec["names"][0], qualified_name=aid,
                             doc=rec["help"] or None, relations=[{"type": "contained-in", "target": cid}],
                             attrs={**rec, "dest": dest}))
    return sorted(out, key=lambda f: f["id"])


def _fact(**kw) -> dict:
    base = {"id": "", "kind": "", "name": "", "qualified_name": "", "path": None, "span": None,
            "source_hash": None, "signature": None, "synopsis": None, "doc": None, "relations": [], "attrs": {}}
    base.update(kw)
    return base


def load_factory(ref: str, root: Path, pythonpath: str | None):
    location = Path(pythonpath) if pythonpath else root
    location = location if location.is_absolute() else root / location
    for entry in (str(root.resolve()), str(location.resolve())):
        if entry not in sys.path:
            sys.path.insert(0, entry)
    module, sep, name = ref.partition(":")
    if not sep or not module or not name:
        raise ValueError("factory must use module:callable syntax")
    obj = importlib.import_module(module)
    for part in name.split("."):
        obj = getattr(obj, part)
    return obj


def emit(doc: dict) -> None:
    sys.stdout.buffer.write((json.dumps(doc, sort_keys=True, ensure_ascii=False) + "\n").encode("utf-8"))


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(prog=NAME, description="argparse CLI facts (protocol 1).")
    ap.add_argument("root", nargs="?", metavar="ROOT")
    ap.add_argument("--describe", action="store_true")
    ap.add_argument("--system")
    ap.add_argument("--factory")
    ap.add_argument("--pythonpath")
    ap.add_argument("--include-hidden", action="store_true")
    args = ap.parse_args(argv)
    if args.describe:
        emit(describe())
        return 0
    if args.root is None or not args.factory:
        ap.error("ROOT and --factory are required")
    real_stdout = sys.stdout
    sys.stdout = sys.stderr  # factories may print; stdout carries JSON only
    try:
        parser = load_factory(args.factory, Path(args.root), args.pythonpath)()
        if not isinstance(parser, argparse.ArgumentParser):
            raise ValueError("Parser factory must return argparse.ArgumentParser")
        walker = Walker(args.include_hidden)
        walker.walk(parser, [], "", [], [], False)
        facts = facts_of(walker.records)
    except BaseException as exc:  # noqa: BLE001 - factory code is arbitrary, incl. SystemExit
        sys.stdout = real_stdout
        sys.stderr.write(f"error: {type(exc).__name__}: {exc}\n".replace("\n\n", "\n"))
        return 1
    finally:
        sys.stdout = real_stdout
    emit({"protocol": 1, "adapter": NAME, "profile": "cli", "language": "python", "facts": facts})
    return 0


if __name__ == "__main__":
    sys.exit(main())
