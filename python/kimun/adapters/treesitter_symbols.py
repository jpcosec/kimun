#!/usr/bin/env python3
"""tree-sitter symbols adapter (protocol 1, profile "symbols").

Emits the language-neutral fact schema of CONTRACT.md for python and javascript
sources.  Definitions are found with tree-sitter queries kept in the per-language
table ``LANGUAGES``; adding a language is adding one entry (query, capture->kind
map, how name / doc / signature are read).  Grammar packages are imported lazily.
"""
from __future__ import annotations

import argparse
import ast
import hashlib
import importlib
import inspect
import json
import os
import re
import sys

PROTOCOL = 1
ADAPTER = "treesitter-symbols"


# --------------------------------------------------------------------------- readers
# Each reader takes (node, src) where src is the file bytes; nodes are tree-sitter nodes.

def text(node, src: bytes) -> str:
    return src[node.start_byte:node.end_byte].decode("utf-8", "replace")


def field_text(node, src, field):
    child = node.child_by_field_name(field)
    return text(child, src) if child is not None else None


def first_statement(block):
    """First non-comment named child of a body-like node."""
    for child in block.named_children if block is not None else []:
        if child.type != "comment":
            return child
    return None


def py_string_value(node, src):
    """Evaluate a (possibly implicitly concatenated) string literal; None if not a plain str."""
    try:
        value = ast.literal_eval("(" + text(node, src) + "\n)")
    except (ValueError, SyntaxError):
        return None
    return value if isinstance(value, str) else None


def py_doc(node, src):
    body = node if node.type == "module" else node.child_by_field_name("body")
    stmt = first_statement(body)
    if stmt is None or stmt.type != "expression_statement" or stmt.named_child_count != 1:
        return None
    expr = stmt.named_children[0]
    if expr.type not in ("string", "concatenated_string"):
        return None
    value = py_string_value(expr, src)
    return None if value is None else inspect.cleandoc(value)


def py_end_line(node):
    """Line of the last real token: tree-sitter lets a block swallow trailing comment lines, ast does not."""
    while True:
        kids = [c for c in node.children if c.type != "comment"]
        if not kids:
            return node.end_point[0] + 1
        node = kids[-1]


def py_norm(t):
    """Whitespace-collapse and requote simple "x" strings as 'x' (what ast.unparse prints)."""
    return re.sub(r'(?<![\w"])"([^"\'\\\n]*)"(?!")', r"'\1'", " ".join(t.split()))


def py_param(p, src):
    if p.type == "typed_default_parameter":
        return f"{field_text(p, src, 'name')}: {py_norm(field_text(p, src, 'type'))}={py_norm(field_text(p, src, 'value'))}"
    if p.type == "default_parameter":
        return f"{field_text(p, src, 'name')}={py_norm(field_text(p, src, 'value'))}"
    return py_norm(text(p, src))


def py_signature(node, src):
    if node.type != "function_definition":
        return None
    params = node.child_by_field_name("parameters")
    inner = [py_param(c, src) for c in params.named_children if c.type != "comment"]
    return f"{field_text(node, src, 'name')}({', '.join(inner)})"


def py_native_kind(node, src):
    if node.type == "class_definition":
        return "ClassDef"
    is_async = any(c.type == "async" for c in node.children if c.start_byte < node.child_by_field_name("name").start_byte)
    return "AsyncFunctionDef" if is_async else "FunctionDef"


def py_module_name(rel: str) -> str:
    parts = rel[:-len(os.path.splitext(rel)[1])].split("/")
    if parts[-1] == "__init__":
        parts.pop()
    return ".".join(parts)


def py_imports(root, src):
    out = set()
    for stmt in root.named_children:
        if stmt.type == "import_statement":
            names = [field_text(c, src, "name") if c.type == "aliased_import" else text(c, src)
                     for c in stmt.children_by_field_name("name")]
            out.add(",".join("".join(n.split()) for n in names))
        elif stmt.type == "future_import_statement":
            out.add("__future__")
        elif stmt.type == "import_from_statement":
            out.add("".join(field_text(stmt, src, "module_name").split()))
    return out


def js_clean_jsdoc(raw: str):
    lines = raw[3:-2].split("\n") if raw.endswith("*/") else raw[3:].split("\n")
    lines = [re.sub(r"^\s*\*? ?", "", ln).rstrip() for ln in lines]
    while lines and not lines[0]:
        lines.pop(0)
    while lines and not lines[-1]:
        lines.pop()
    return "\n".join(lines)


def js_doc(node, src, anchor):
    prev = anchor.prev_sibling
    if prev is None or prev.type != "comment":
        return None
    raw = text(prev, src)
    if not raw.startswith("/**") or prev.end_point[0] < anchor.start_point[0] - 1:
        return None
    return js_clean_jsdoc(raw)


def js_signature(node, src):
    fn = node.child_by_field_name("value") if node.type == "variable_declarator" else node
    if fn is None or fn.type == "class_declaration":
        return None
    params = fn.child_by_field_name("parameters") or fn.child_by_field_name("parameter")
    name = field_text(node, src, "name")
    if params is None:
        return None
    inner = text(params, src)
    inner = inner[1:-1] if params.type == "formal_parameters" else inner
    return f"{name}({' '.join(inner.split())})"


def js_module_name(rel: str) -> str:
    return rel[:-len(os.path.splitext(rel)[1])].replace("/", ".")


def js_imports(root, src):
    out = set()
    for stmt in root.named_children:
        if stmt.type == "import_statement":
            source = stmt.child_by_field_name("source")
            if source is not None:
                out.add(text(source, src)[1:-1])
    return out


def js_keep(node):
    """Only module-level `const f = <function|arrow>` bindings count; declarators otherwise."""
    if node.type != "variable_declarator":
        return True
    value = node.child_by_field_name("value")
    return value is not None and value.type in ("arrow_function", "function_expression",
                                                "function", "generator_function")


# --------------------------------------------------------------------------- language table
# query:        S-expression; capture names are keys of `kinds` ("method" is decided structurally:
#               a "function" directly inside a class becomes a method)
# transparent:  node types allowed between a definition and its owner (anything else = not a
#               definition the language scans, e.g. a def under `if` in python)
# anchor_types: wrappers climbed for span + doc lookup (python: none, so decorators are excluded)
# doc/signature/native_kind/module_name/imports/keep: readers (see above)
LANGUAGES = {
    "python": {
        "extensions": (".py",),
        "package": "tree_sitter_python",
        "query": "(class_definition) @class (function_definition) @function",
        "kinds": {"class": "class", "function": "function"},
        "root_type": "module",
        "transparent": {"block", "decorated_definition"},
        "anchor_types": set(),
        "name": lambda n, s: field_text(n, s, "name"),
        "doc": lambda n, s, a: py_doc(n, s),
        "module_doc": lambda r, s: py_doc(r, s),
        "signature": py_signature,
        "native_kind": py_native_kind,
        "module_name": py_module_name,
        "imports": py_imports,
        "keep": lambda n: True,
        "end_line": lambda n: py_end_line(n),
        "skip_dirs": (),
    },
    "javascript": {
        "extensions": (".js", ".mjs", ".cjs", ".jsx"),
        "package": "tree_sitter_javascript",
        "query": """(function_declaration) @function (generator_function_declaration) @function
                    (class_declaration) @class (method_definition) @function
                    (lexical_declaration (variable_declarator) @function)
                    (variable_declaration (variable_declarator) @function)""",
        "kinds": {"class": "class", "function": "function"},
        "root_type": "program",
        "transparent": {"class_body", "export_statement", "lexical_declaration", "variable_declaration"},
        "anchor_types": {"export_statement", "lexical_declaration", "variable_declaration"},
        "name": lambda n, s: field_text(n, s, "name"),
        "doc": js_doc,
        "module_doc": lambda r, s: None,
        "signature": js_signature,
        "native_kind": lambda n, s: n.type,
        "module_name": js_module_name,
        "imports": js_imports,
        "keep": js_keep,
        "end_line": lambda n: n.end_point[0] + 1,
        "skip_dirs": ("node_modules",),
    },
}


# --------------------------------------------------------------------------- runtime
def load_runtime(languages):
    """Import tree_sitter and the given grammars; return (tree_sitter module, {lang: Language}, missing)."""
    missing, langs, ts = [], {}, None
    try:
        ts = importlib.import_module("tree_sitter")
    except ImportError:
        missing.append("tree_sitter")
    for name in languages:
        pkg = LANGUAGES[name]["package"]
        try:
            mod = importlib.import_module(pkg)
            if ts is not None:
                langs[name] = ts.Language(mod.language())
        except ImportError:
            missing.append(pkg)
    return ts, langs, missing


def describe() -> dict:
    _, _, missing = load_runtime(LANGUAGES)
    return {"adapter": ADAPTER, "protocol": PROTOCOL, "profile": "symbols", "languages": sorted(LANGUAGES),
            "options": [{"name": "--language", "help": "python|javascript (default: inferred from the files; required when both are present)"},
                        {"name": "--system", "help": "accepted for uniformity; unused"}],
            "runtime": {"available": not missing, "missing": missing}}


def find_files(root: str, language: str):
    spec = LANGUAGES[language]
    out = []
    for base, dirs, files in os.walk(root):
        dirs[:] = sorted(d for d in dirs if d not in spec["skip_dirs"])
        for f in files:
            if f.endswith(spec["extensions"]) and not f.endswith(".min.js"):
                out.append(os.path.relpath(os.path.join(base, f), root).replace(os.sep, "/"))
    return sorted(out, key=lambda r: tuple(r.split("/")))


# --------------------------------------------------------------------------- scanning
def fact(**kw):
    base = {"id": None, "kind": None, "name": None, "qualified_name": None, "path": None, "span": None,
            "source_hash": None, "signature": None, "synopsis": None, "doc": None, "relations": [], "attrs": {}}
    base.update(kw)
    return base


def rels(owner, imports=()):
    r = [{"type": "imports", "target": t} for t in sorted(imports)]
    if owner is not None:
        r.append({"type": "contained-in", "target": owner})
    return sorted(r, key=lambda x: (x["type"], x["target"]))


class Ids:
    """Unique fact ids across the run; a repeated candidate gets ``~2``, ``~3`` ... in scan order."""

    def __init__(self):
        self.seen = {}

    def take(self, candidate):
        n = self.seen[candidate] = self.seen.get(candidate, 0) + 1
        return candidate if n == 1 else f"{candidate}~{n}"


def scan_file(ts, lang_obj, spec, rel, root, idgen):
    with open(os.path.join(root, rel), "rb") as fh:
        src = fh.read()
    tree = ts.Parser(lang_obj).parse(src)
    if tree.root_node.has_error:
        print(f"warning: {rel}: syntax errors, facts are best-effort", file=sys.stderr)
    digest = "sha256:" + hashlib.sha256(src).hexdigest()
    module = spec["module_name"](rel)
    rootn = tree.root_node
    nlines = len(src.decode("utf-8", "replace").splitlines())
    mid = idgen.take(module or "__init__")
    imports = sorted(spec["imports"](rootn, src))
    facts = [fact(id=mid, kind="module", name=module.rsplit(".", 1)[-1] if module else "__init__",
                  qualified_name=module, path=rel, span={"start_line": 1, "end_line": max(nlines, 1)},
                  source_hash=digest, doc=spec["module_doc"](rootn, src), relations=rels(None, imports),
                  attrs={"native_kind": rootn.type, "module": module, "imports": imports})]
    captured = {}
    for cap, nodes in ts.QueryCursor(ts.Query(lang_obj, spec["query"])).captures(rootn).items():
        for n in nodes:
            if spec["keep"](n):
                captured[(n.start_byte, n.end_byte, n.type)] = (cap, n)
    key = lambda n: (n.start_byte, n.end_byte, n.type)

    def anchor(n):
        while n.parent is not None and n.parent.type in spec["anchor_types"]:
            n = n.parent
        return n

    def owner_of(n):
        p = anchor(n).parent
        while p is not None and p.type in spec["transparent"]:
            p = p.parent
        if p is None:
            return None
        if p.type == spec["root_type"]:
            return "root"
        return key(p) if key(p) in captured else None

    items = sorted(captured.values(), key=lambda cn: (cn[1].start_byte, -cn[1].end_byte))
    owners = {key(n): owner_of(n) for _, n in items}
    ids, qual, kinds = {}, {}, {}

    def resolve(k):  # qualified name, only for definitions whose owner chain is valid
        if k in qual:
            return qual[k]
        o = owners.get(k, None)
        if o is None:
            qual[k] = None
        else:
            cap, n = captured[k]
            name = spec["name"](n, src)
            parent = None if o == "root" else resolve(o)
            qual[k] = None if (o != "root" and parent is None) or name is None else (name if o == "root" else f"{parent}.{name}")
        return qual[k]

    for cap, n in items:
        k = key(n)
        q = resolve(k)
        if q is None:
            continue
        o = owners[k]
        kind = spec["kinds"][cap]
        if kind == "function" and o != "root" and captured[o][0] == "class":
            kind = "method"
        a = anchor(n)
        ids[k] = idgen.take(f"{module}.{q}" if module else q)
        owner_id = mid if o == "root" else ids.get(o)
        facts.append(fact(id=ids[k], kind=kind, name=spec["name"](n, src), qualified_name=q, path=rel,
                          span={"start_line": a.start_point[0] + 1, "end_line": spec["end_line"](a)},
                          source_hash=digest, signature=spec["signature"](n, src), doc=spec["doc"](n, src, a),
                          relations=rels(owner_id), attrs={"native_kind": spec["native_kind"](n, src), "module": module}))
    return facts


def infer_language(root):
    present = [lang for lang in sorted(LANGUAGES) if find_files(root, lang)]
    if len(present) > 1:
        return None, present
    return (present[0] if present else "python"), present


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(prog="treesitter_symbols.py", description=__doc__.splitlines()[0])
    ap.add_argument("root", nargs="?")
    ap.add_argument("--system")
    ap.add_argument("--language", choices=sorted(LANGUAGES))
    ap.add_argument("--describe", action="store_true")
    try:
        args = ap.parse_args(argv)
    except SystemExit as e:
        return 0 if e.code in (0, None) else 2
    if args.describe:
        print(json.dumps(describe(), sort_keys=True))
        return 0
    if not args.root:
        print("error: ROOT is required", file=sys.stderr)
        return 2
    root = args.root
    if not os.path.isdir(root):
        print(f"error: not a directory: {root}", file=sys.stderr)
        return 1
    language = args.language
    if language is None:
        language, present = infer_language(root)
        if language is None:
            print(f"error: both {' and '.join(present)} sources found: pass --language", file=sys.stderr)
            return 2
    ts, langs, missing = load_runtime([language])
    if missing:
        print("error: runtime missing: " + ", ".join(missing), file=sys.stderr)
        return 3
    spec, facts, idgen = LANGUAGES[language], [], Ids()
    try:
        for rel in find_files(root, language):
            facts.extend(scan_file(ts, langs[language], spec, rel, root, idgen))
    except OSError as e:
        print(f"error: {e}", file=sys.stderr)
        return 1
    facts.sort(key=lambda f: f["id"])
    print(json.dumps({"protocol": PROTOCOL, "adapter": ADAPTER, "profile": "symbols", "language": language,
                      "facts": facts}, sort_keys=True, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
