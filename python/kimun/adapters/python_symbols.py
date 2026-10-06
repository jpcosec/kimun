#!/usr/bin/env python3
"""Static Python symbol adapter (protocol 1): port of sldb v1 selfdoc PythonScanner.

Scans every ``*.py`` below ROOT with ``ast`` (no import, no execution) and
prints the JSON fact document described in the adapter contract.
"""
from __future__ import annotations

import sys

sys.dont_write_bytecode = True

import argparse
import ast
import hashlib
import json
from pathlib import Path

NAME = "python-symbols"
OPTIONS = [{"name": "--package", "help": "package prefix prepended to every module name"},
           {"name": "--system", "help": "system name (accepted, not used by the scan)"}]


def describe() -> dict:
    return {"adapter": NAME, "protocol": 1, "profile": "symbols", "languages": ["python"],
            "options": OPTIONS, "runtime": {"available": True, "missing": []}}


def module_name(path: Path, root: Path, package: str | None) -> str:
    parts = list(path.relative_to(root).with_suffix("").parts)
    if parts[-1] == "__init__":
        parts.pop()
    return ".".join(([package] if package else []) + parts)


def import_name(node) -> str:
    if isinstance(node, ast.Import):
        return ",".join(alias.name for alias in node.names)
    return f"{'.' * node.level}{node.module or ''}"


def imports_of(tree: ast.Module) -> list[str]:
    return sorted({import_name(n) for n in tree.body if isinstance(n, (ast.Import, ast.ImportFrom))})


def signature_of(node) -> str | None:
    if not isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
        return None
    return f"{node.name}({ast.unparse(node.args)})"


class Ids:
    """Hand out unique fact ids; a repeated candidate gets ``~2``, ``~3`` ... in scan order."""

    def __init__(self) -> None:
        self.seen: dict[str, int] = {}

    def take(self, candidate: str) -> str:
        n = self.seen.get(candidate, 0) + 1
        self.seen[candidate] = n
        return candidate if n == 1 else f"{candidate}~{n}"


def fact(**kw) -> dict:
    base = {"id": "", "kind": "", "name": "", "qualified_name": "", "path": None, "span": None,
            "source_hash": None, "signature": None, "synopsis": None, "doc": None,
            "relations": [], "attrs": {}}
    base.update(kw)
    base["relations"] = sorted(base["relations"], key=lambda r: (r["type"], r["target"]))
    return base


def walk(nodes, ctx, parent_id, parents, out) -> None:
    for node in nodes:
        if not isinstance(node, (ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef)):
            continue
        qual = ".".join([*parents, node.name])
        is_class = isinstance(node, ast.ClassDef)
        parent_is_class = bool(parents) and ctx["class_stack"][-1]
        kind = "class" if is_class else ("method" if parent_is_class else "function")
        fid = ctx["ids"].take(f"{ctx['module']}.{qual}" if ctx["module"] else qual)
        ctx["owners"][id(node)] = fid
        out.append(fact(id=fid, kind=kind, name=node.name, qualified_name=qual, path=ctx["path"],
                        span={"start_line": node.lineno, "end_line": node.end_lineno},
                        source_hash=ctx["hash"], signature=signature_of(node), doc=ast.get_docstring(node),
                        relations=[{"type": "contained-in", "target": parent_id}],
                        attrs={"native_kind": type(node).__name__, "module": ctx["module"]}))
        ctx["class_stack"].append(is_class)
        walk(node.body, ctx, fid, [*parents, node.name], out)
        ctx["class_stack"].pop()


def relative_module(module: str, requested: str | None, level: int) -> str:
    """Resolve an ImportFrom module spelling against its importer's package (v1 rule)."""
    if not level:
        return requested or ""
    package = module.split(".")[:-1]
    base = package[:len(package) - level + 1]
    return ".".join([*base, *(requested or "").split(".")]).strip(".")


class EdgeVisitor(ast.NodeVisitor):
    """v1 PythonSourceFacts: every import binding and every simple annotated-assignment reference.

    Owners are the enclosing scanned symbol facts (defs the scanner does not walk, e.g. under
    ``if``, do not own anything: their imports/references belong to the nearest scanned fact).
    """

    def __init__(self, module: str, module_id: str, owners: dict) -> None:
        self.module, self.known, self.stack = module, owners, [module_id]
        self.bindings: list[dict] = []
        self.references: list[dict] = []

    def generic_visit(self, node) -> None:
        fid = self.known.get(id(node))
        if fid:
            self.stack.append(fid)
        super().generic_visit(node)
        if fid:
            self.stack.pop()

    def visit_Import(self, node) -> None:
        for alias in node.names:
            self._bind(alias.name, None, alias.asname, 0)

    def visit_ImportFrom(self, node) -> None:
        for alias in node.names:
            self._bind(node.module or "", alias.name, alias.asname, node.level)

    def _bind(self, module, name, alias, level) -> None:
        self.bindings.append({"owner": self.stack[-1], "module": module, "name": name, "level": level,
                              "local": alias or (name or module.split(".")[0])})

    def visit_AnnAssign(self, node) -> None:
        if isinstance(node.target, ast.Name):
            names = sorted({n.id for n in ast.walk(node.annotation) if isinstance(n, ast.Name)})
            self.references += [{"owner": self.stack[-1], "name": n} for n in names]
        self.generic_visit(node)


def resolve(files: list[dict], facts_by_id: dict) -> None:
    """Attach resolved ``imports`` and ``references`` relations (v1 python_source_graph)."""
    modules, symbols = {}, {}
    for f in files:
        modules.setdefault(f["module"], f["id"])
        for sid, name in f["symbols"]:
            symbols[f"{f['module']}:{name}"] = sid

    def target(f, b):
        module = relative_module(f["module"], b["module"], b["level"])
        key = f"{module}:{b['name']}"
        if b["name"] and key in symbols:
            return symbols[key], False
        if module in modules:
            return modules[module], False
        return (f"{module}.{b['name'] or ''}".strip(".") or "."), True

    for f in files:
        resolved = [{**b, "target": target(f, b)[0], "ext": target(f, b)[1]} for b in f["bindings"]]
        parents = f["parents"]
        edges = []
        for b in resolved:
            edges.append((b["owner"], "imports", (b["target"], b["ext"])))
        for r in f["references"]:
            owner = r["owner"]
            while owner:
                hit = next(((b["target"], b["ext"]) for b in resolved if b["owner"] == owner and b["local"] == r["name"]), None)
                if hit:
                    edges.append((r["owner"], "references", hit))
                    break
                owner = parents.get(owner)
        for owner, kind, (tgt, external) in edges:
            rels = facts_by_id[owner]["relations"]
            rel = {"type": kind, "target": tgt, "external": external}
            if rel not in rels:
                rels.append(rel)


def scan(root: Path, package: str | None) -> list[dict]:
    out: list[dict] = []
    files: list[dict] = []
    ids = Ids()
    for path in sorted(root.rglob("*.py")):
        if not path.is_file():
            continue
        try:
            text = path.read_text(encoding="utf-8")
            tree = ast.parse(text, filename=str(path))
        except Exception as exc:  # noqa: BLE001 - any unreadable/unparsable source fails the scan
            raise ScanError(f"{path.relative_to(root).as_posix()}: {type(exc).__name__}: {exc}") from exc
        module = module_name(path, root, package)
        rel = path.relative_to(root).as_posix()
        digest = "sha256:" + hashlib.sha256(text.encode()).hexdigest()
        imports = imports_of(tree)
        mid = ids.take(module or "__init__")
        out.append(fact(id=mid, kind="module", name=module.rsplit(".", 1)[-1] if module else "__init__",
                        qualified_name=module, path=rel,
                        span={"start_line": 1, "end_line": max(1, len(text.splitlines()))},
                        source_hash=digest, doc=ast.get_docstring(tree),
                        relations=[{"type": "imports", "target": i} for i in imports],
                        attrs={"native_kind": "Module", "module": module, "imports": imports}))
        ctx = {"module": module, "path": rel, "hash": digest, "ids": ids, "class_stack": [], "owners": {}}
        first = len(out)
        walk(tree.body, ctx, mid, [], out)
        edges = EdgeVisitor(module, mid, ctx["owners"])
        edges.visit(tree)
        mine = out[first:]
        files.append({"module": module, "id": mid, "bindings": edges.bindings, "references": edges.references,
                      "symbols": [(f["id"], f["name"]) for f in mine],
                      "parents": {f["id"]: f["relations"][0]["target"] for f in mine}})
    by_id = {f["id"]: f for f in out}
    resolve(files, by_id)
    for f in out:
        f["relations"] = sorted(f["relations"], key=lambda r: (r["type"], r["target"], r.get("external", False)))
    return sorted(out, key=lambda f: f["id"])


class ScanError(Exception):
    """A source could not be read or parsed."""


def emit(doc: dict) -> None:
    sys.stdout.buffer.write((json.dumps(doc, sort_keys=True, ensure_ascii=False) + "\n").encode("utf-8"))


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(prog=NAME, description="Static Python symbol facts (protocol 1).")
    ap.add_argument("root", nargs="?", metavar="ROOT")
    ap.add_argument("--describe", action="store_true")
    ap.add_argument("--system")
    ap.add_argument("--package")
    args = ap.parse_args(argv)
    if args.describe:
        emit(describe())
        return 0
    if args.root is None:
        ap.error("ROOT is required")
    root = Path(args.root)
    if not root.is_dir():
        sys.stderr.write(f"error: not a directory: {args.root}\n")
        return 1
    try:
        facts = scan(root, args.package)
    except ScanError as exc:
        sys.stderr.write(f"error: {exc}\n")
        return 1
    emit({"protocol": 1, "adapter": NAME, "profile": "symbols", "language": "python", "facts": facts})
    return 0


if __name__ == "__main__":
    sys.exit(main())
