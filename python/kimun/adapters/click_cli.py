#!/usr/bin/env python3
"""click CLI adapter (protocol 1): port of sldb v1 selfdoc ClickScanner.

Imports ``--factory module:attr`` (ROOT on sys.path; a click Group, or a callable
returning one), never invokes a handler, and prints one fact per leaf command and
per parameter. Needs the third-party ``click`` package (exit 3 when missing).
"""
from __future__ import annotations

import sys

sys.dont_write_bytecode = True

import argparse
import importlib
import importlib.util
import json
from enum import Enum
from pathlib import Path
from typing import Any

NAME = "click-cli"
OPTIONS = [{"name": "--factory", "help": "module:attr naming a click.Group or a callable returning one (required)"},
           {"name": "--pythonpath", "help": "extra sys.path entry, relative to ROOT unless absolute"},
           {"name": "--program", "help": "program name used in usage strings (default: pron, as in v1)"},
           {"name": "--system", "help": "system name (accepted, not used by the scan)"}]


def runtime() -> dict:
    missing = [] if importlib.util.find_spec("click") else ["click"]
    return {"available": not missing, "missing": missing}


def describe() -> dict:
    return {"adapter": NAME, "protocol": 1, "profile": "cli", "languages": ["python"],
            "options": OPTIONS, "runtime": runtime()}


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



def _first_line(doc):
    return (doc or "").strip().splitlines()[0] if doc and doc.strip() else ""


def _callback_doc(command):
    return getattr(command.callback, "__doc__", None)


def _synopsis(command) -> str:
    return command.help or command.short_help or _first_line(_callback_doc(command))


def _default(click, param) -> str:
    unset = getattr(click.core, "UNSET", None)
    if param.default is None or (unset is not None and param.default is unset):
        return "SUPPRESS"
    return json_value(param.default)


def _choices(param):
    values = getattr(getattr(param, "type", None), "choices", None)
    return None if not values else [json_value(v) for v in values]


def _argument(click, param) -> dict:
    t = getattr(param, "type", None)
    return {"names": list(param.opts or [param.name]),
            "required": bool(param.required or isinstance(param, click.Argument)),
            "default": _default(click, param), "choices": _choices(param), "nargs": None,
            "value_type": None if t is None else type(t).__name__, "action": type(param).__name__,
            "const": "", "help": getattr(param, "help", None) or ""}


def records_of(click, group, program: str) -> list[dict]:
    out = []
    for name, command in sorted(group.commands.items()):
        if isinstance(command, click.Group):
            continue
        doc = _callback_doc(command) or ""
        out.append({"path": [name], "synopsis": _synopsis(command), "description": doc.strip(),
                    "usage": f"{program} {name}", "group": False, "hidden": bool(command.hidden),
                    "arguments": [_argument(click, p) for p in command.params], "exclusive_groups": [],
                    "own": [(p.name, _argument(click, p)) for p in command.params]})
    return out


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
    ap = argparse.ArgumentParser(prog=NAME, description="click CLI facts (protocol 1).")
    ap.add_argument("root", nargs="?", metavar="ROOT")
    ap.add_argument("--describe", action="store_true")
    ap.add_argument("--system")
    ap.add_argument("--factory")
    ap.add_argument("--pythonpath")
    ap.add_argument("--program", default="pron")
    args = ap.parse_args(argv)
    if args.describe:
        emit(describe())
        return 0
    if args.root is None or not args.factory:
        ap.error("ROOT and --factory are required")
    state = runtime()
    if not state["available"]:
        sys.stderr.write(f"error: runtime missing: {', '.join(state['missing'])}\n")
        return 3
    import click
    real_stdout = sys.stdout
    sys.stdout = sys.stderr  # factories may print; stdout carries JSON only
    try:
        obj = load_factory(args.factory, Path(args.root), args.pythonpath)
        group = obj if isinstance(obj, click.Group) else obj()
        if not isinstance(group, click.Group):
            raise ValueError("Click factory must return click.Group")
        facts = facts_of(records_of(click, group, args.program))
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
