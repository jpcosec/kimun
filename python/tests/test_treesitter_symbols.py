"""Tests of the tree-sitter symbols adapter (python + javascript), skipped without the runtime."""
from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

try:
    import tree_sitter  # noqa: F401
    import tree_sitter_javascript  # noqa: F401
    import tree_sitter_python  # noqa: F401
except ImportError as exc:  # pragma: no cover - depends on the interpreter
    raise unittest.SkipTest(f"tree_sitter runtime not importable in {sys.executable}: {exc}")

HERE = Path(__file__).resolve().parent
ADAPTER = HERE.parent / "kimun" / "adapters" / "treesitter_symbols.py"
JSTREE = HERE.parent.parent / "test" / "fixtures" / "adapters" / "jstree"
ENV = {**os.environ, "PYTHONDONTWRITEBYTECODE": "1"}


def run(*args, code=None, env=None):
    cmd = [sys.executable, str(ADAPTER), *args]
    if code is not None:  # run the adapter under a prologue (used to hide packages)
        cmd = [sys.executable, "-c", code + f"\nimport runpy,sys\nsys.argv={[str(ADAPTER), *args]!r}\nrunpy.run_path({str(ADAPTER)!r}, run_name='__main__')"]
    return subprocess.run(cmd, capture_output=True, text=True, env=env or ENV)


def scan(root, *args):
    r = run(str(root), *args)
    assert r.returncode == 0, r.stderr
    doc = json.loads(r.stdout)
    return doc, {f["id"]: f for f in doc["facts"]}


class PythonScanTest(unittest.TestCase):
    SOURCE = '''"""Mod doc."""
import os, sys as s
from . import x
from ..a import b
from __future__ import annotations


@deco
async def top(a, b: int = 1, *rest, c="q"):
    """Top.

        Indented.
    """
    def inner(): pass


class K:
    "k doc"

    def m(self): pass

    class N:
        def d(self):
            # trailing comment
            return 1

# trailing comment after class
if True:
    def hidden(): pass
'''

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        pkg = Path(self.tmp.name, "pkg")
        pkg.mkdir()
        (pkg / "__init__.py").write_text("")
        (pkg / "mod.py").write_text(self.SOURCE)
        self.doc, self.facts = scan(self.tmp.name)

    def test_envelope_and_ids(self):
        self.assertEqual((self.doc["protocol"], self.doc["adapter"], self.doc["profile"], self.doc["language"]),
                         (1, "treesitter-symbols", "symbols", "python"))
        self.assertEqual(sorted(self.facts), ["pkg", "pkg.mod", "pkg.mod.K", "pkg.mod.K.N", "pkg.mod.K.N.d",
                                              "pkg.mod.K.m", "pkg.mod.top", "pkg.mod.top.inner"])
        self.assertEqual([f["id"] for f in self.doc["facts"]], sorted(self.facts))

    def test_kinds_spans_and_decorators(self):
        f = self.facts
        self.assertEqual([f[i]["kind"] for i in ("pkg.mod", "pkg.mod.top", "pkg.mod.top.inner", "pkg.mod.K", "pkg.mod.K.m")],
                         ["module", "function", "function", "class", "method"])
        self.assertEqual(f["pkg.mod.top"]["span"], {"start_line": 9, "end_line": 14})  # starts at `async def`, not @deco
        self.assertEqual(f["pkg.mod.top"]["attrs"]["native_kind"], "AsyncFunctionDef")
        self.assertEqual(f["pkg.mod.K.N.d"]["span"], {"start_line": 23, "end_line": 25})  # trailing comment excluded...
        self.assertEqual(f["pkg.mod.K"]["span"], {"start_line": 17, "end_line": 25})  # ...also from the enclosing class
        self.assertEqual(f["pkg.mod"]["span"], {"start_line": 1, "end_line": 29})

    def test_docs_signature_relations(self):
        f = self.facts
        self.assertEqual(f["pkg.mod"]["doc"], "Mod doc.")
        self.assertEqual(f["pkg.mod.top"]["doc"], "Top.\n\nIndented.")
        self.assertEqual(f["pkg.mod.K"]["doc"], "k doc")
        self.assertIsNone(f["pkg.mod.K.m"]["doc"])
        self.assertEqual(f["pkg.mod.top"]["signature"], "top(a, b: int=1, *rest, c='q')")
        self.assertIsNone(f["pkg.mod.K"]["signature"])
        self.assertEqual(f["pkg.mod.K.N.d"]["relations"], [{"type": "contained-in", "target": "pkg.mod.K.N"}])
        self.assertEqual(f["pkg.mod.top.inner"]["relations"], [{"type": "contained-in", "target": "pkg.mod.top"}])
        self.assertEqual([r for r in f["pkg.mod"]["relations"]],
                         [{"type": "imports", "target": "."}, {"type": "imports", "target": "..a"},
                          {"type": "imports", "target": "__future__"}, {"type": "imports", "target": "os,sys"}])
        self.assertTrue(f["pkg.mod"]["source_hash"].startswith("sha256:"))
        self.assertEqual(f["pkg"]["name"], "pkg")  # __init__.py is the package module

    def test_conditional_definitions_are_not_symbols(self):
        self.assertNotIn("pkg.mod.hidden", self.facts)

    def test_docstring_forms(self):
        d = Path(self.tmp.name, "docs.py")
        d.write_text('def a():\n    r"""raw \\d"""\n'
                     'def b():\n    "x\\ty" \'z\'\n'
                     'def c():\n    f"{1}"\n'
                     'def d():\n    b"bytes"\n'
                     'def e():\n    pass\n    "late"\n')
        _, facts = scan(self.tmp.name)
        expected = ["raw \\d", "x       yz", None, None, None]  # cleandoc expands tabs, as ast.get_docstring does
        self.assertEqual([facts[f"docs.{n}"]["doc"] for n in "abcde"], expected)

    def test_duplicate_ids_get_suffix(self):
        Path(self.tmp.name, "dup.py").write_text("def f(): pass\ndef f(): pass\n")
        _, facts = scan(self.tmp.name)
        self.assertIn("dup.f", facts)
        self.assertIn("dup.f~2", facts)

    def test_no_bytecode_written_to_root(self):
        self.assertEqual(list(Path(self.tmp.name).rglob("__pycache__")), [])


class JavascriptScanTest(unittest.TestCase):
    def setUp(self):
        self.doc, self.facts = scan(JSTREE)

    def test_ids_kinds_spans(self):
        self.assertEqual(self.doc["language"], "javascript")
        got = {i: (f["kind"], f["span"]["start_line"], f["span"]["end_line"]) for i, f in self.facts.items()}
        self.assertEqual(got, {
            "index": ("module", 1, 45), "index.add": ("function", 10, 13), "index.plain": ("function", 16, 16),
            "index.stream": ("function", 19, 19), "index.Shape": ("class", 24, 34),
            "index.Shape.constructor": ("method", 26, 26), "index.Shape.area": ("method", 29, 29),
            "index.Shape.create": ("method", 31, 31), "index.Shape.load": ("method", 33, 33),
            "index.double": ("function", 37, 37), "index.expr": ("function", 39, 39),
            "lib.util": ("module", 1, 4), "lib.util.Hidden": ("class", 4, 4), "lib.util.helper": ("function", 2, 2)})

    def test_jsdoc_and_signatures(self):
        f = self.facts
        self.assertEqual(f["index.add"]["doc"], "Add two numbers.\n\n@param {number} a\n@param {number} b")
        self.assertIsNone(f["index.plain"]["doc"])  # `//` comments are not JSDoc
        self.assertEqual(f["index.stream"]["doc"], "Async generator.")
        self.assertEqual(f["index.Shape.area"]["doc"], "Area of the shape.")
        self.assertEqual(f["index.double"]["doc"], "Arrow binding.")
        self.assertIsNone(f["index.Shape.create"]["doc"])
        self.assertEqual(f["index.add"]["signature"], "add(a, b = 1)")
        self.assertEqual(f["lib.util.helper"]["signature"], "helper(x)")

    def test_relations(self):
        f = self.facts
        self.assertEqual(f["index"]["relations"], [{"type": "imports", "target": "./lib/util.js"},
                                                   {"type": "imports", "target": "node:fs"}])
        self.assertEqual(f["index.Shape.area"]["relations"], [{"type": "contained-in", "target": "index.Shape"}])
        self.assertEqual(f["index.add"]["relations"], [{"type": "contained-in", "target": "index"}])

    def test_nested_and_conditional_functions_skipped(self):
        for absent in ("index.add.hidden", "index.conditional", "index.counter"):
            self.assertNotIn(absent, self.facts)


class CliTest(unittest.TestCase):
    def test_describe(self):
        d = json.loads(run("--describe").stdout)
        self.assertEqual((d["adapter"], d["protocol"], d["profile"], d["languages"]),
                         ("treesitter-symbols", 1, "symbols", ["javascript", "python"]))
        self.assertEqual(d["runtime"], {"available": True, "missing": []})

    def test_language_ambiguous_requires_flag(self):
        with tempfile.TemporaryDirectory() as t:
            Path(t, "a.py").write_text("def f(): pass\n")
            Path(t, "b.js").write_text("function g() {}\n")
            r = run(t)
            self.assertEqual((r.returncode, r.stdout), (2, ""))
            self.assertIn("--language", r.stderr)
            self.assertEqual(sorted(scan(t, "--language", "javascript")[1]), ["b", "b.g"])
            self.assertEqual(sorted(scan(t, "--language", "python")[1]), ["a", "a.f"])

    def test_missing_runtime(self):
        code = "import sys\nsys.modules['tree_sitter']=None\n"
        r = run("--describe", code=code)
        d = json.loads(r.stdout)
        self.assertEqual((r.returncode, d["runtime"]["available"], d["runtime"]["missing"][0]), (0, False, "tree_sitter"))
        with tempfile.TemporaryDirectory() as t:
            Path(t, "a.py").write_text("x = 1\n")
            r = run(t, code=code)
            self.assertEqual((r.returncode, r.stdout), (3, ""))
            self.assertTrue(r.stderr.strip().endswith("error: runtime missing: tree_sitter, tree_sitter_python")
                            or "error: runtime missing: tree_sitter" in r.stderr, r.stderr)

    def test_missing_javascript_grammar_does_not_break_python(self):
        code = "import sys\nsys.modules['tree_sitter_javascript']=None\n"
        d = json.loads(run("--describe", code=code).stdout)
        self.assertEqual(d["runtime"], {"available": False, "missing": ["tree_sitter_javascript"]})
        with tempfile.TemporaryDirectory() as t:
            Path(t, "a.py").write_text("def f(): pass\n")
            r = run(t, code=code)
            self.assertEqual(r.returncode, 0, r.stderr)
            Path(t, "b.js").write_text("function g() {}\n")
            r = run(t, "--language", "javascript", code=code)
            self.assertEqual((r.returncode, r.stdout), (3, ""))
            self.assertIn("error: runtime missing: tree_sitter_javascript", r.stderr)


if __name__ == "__main__":
    unittest.main()
