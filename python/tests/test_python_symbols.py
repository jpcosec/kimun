"""python_symbols adapter: v1 PythonScanner semantics, contract output, exit codes."""
import hashlib
import json
import shutil
import tempfile
import unittest
from pathlib import Path

from _support_a import PYTREE, run, run_json, snapshot


def facts_of(root, *extra):
    return run_json("python_symbols", root, *extra)["facts"]


class PythonSymbolsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="kimun-pysym-"))
        self.addCleanup(shutil.rmtree, self.tmp, True)

    # v1: tests/selfdoc/test_python_scanner.py::test_scanner_uses_qualified_names_hashes_and_static_imports
    def test_scanner_uses_qualified_names_hashes_and_static_imports(self):
        source = self.tmp / "src" / "demo"
        source.mkdir(parents=True)
        text = ('import os\nfrom . import sibling\nclass Box:\n    """A box."""\n'
                '    def open(self, key: str) -> None:\n        """Open it."""\n')
        (source / "mod.py").write_text(text, encoding="utf-8")
        facts = facts_of(self.tmp / "src", "--package", "demo")
        mods = {f["path"]: f for f in facts if f["kind"] == "module"}
        got = []
        for f in facts:
            if f["kind"] == "module":
                continue
            m = f["attrs"]["module"]
            got.append((f"python:{m}:{f['qualified_name']}", f["signature"], mods[f["path"]]["attrs"]["imports"]))
        self.assertEqual(got, [("python:demo.demo.mod:Box", None, [".", "os"]),
                               ("python:demo.demo.mod:Box.open", "open(self, key: str)", [".", "os"])])
        box = next(f for f in facts if f["qualified_name"] == "Box")
        self.assertEqual(box["source_hash"], "sha256:" + hashlib.sha256(text.encode()).hexdigest())
        self.assertEqual(box["span"], {"start_line": 3, "end_line": 6})
        self.assertEqual(box["doc"], "A box.")
        self.assertEqual(box["attrs"]["native_kind"], "ClassDef")
        self.assertEqual(box["relations"], [{"type": "contained-in", "target": "demo.demo.mod"}])
        self.assertEqual([r["target"] for r in mods["demo/mod.py"]["relations"]
                          if r["type"] == "imports" and "external" not in r], [".", "os"])

    def test_fixture_tree_semantics(self):
        by_id = {f["id"]: f for f in facts_of(PYTREE)}
        self.assertEqual(by_id["pkg"]["kind"], "module")
        self.assertEqual(by_id["pkg"]["doc"], "Demo package used as a shared adapter fixture.")
        self.assertEqual(by_id["pkg"]["attrs"]["imports"], [".", ".core", "os", "sys,json", "typing"])
        self.assertEqual(by_id["pkg.core"]["attrs"]["imports"], ["__future__", "collections", "os"])
        self.assertEqual(by_id["pkg.sub"]["attrs"]["module"], "pkg.sub")
        self.assertEqual(by_id["pkg.core.fetch"]["attrs"]["native_kind"], "AsyncFunctionDef")
        self.assertEqual(by_id["pkg.core.fetch"]["signature"], "fetch(url: str, *, timeout: float=1.5)")
        self.assertEqual(by_id["pkg.core.configured"]["signature"],
                         "configured(first: int, second: str='x', *args, flag: bool=False, **kwargs)")
        self.assertEqual(by_id["pkg.core.configured"]["span"], {"start_line": 13, "end_line": 33})
        self.assertEqual(by_id["pkg.core.decorated"]["span"]["start_line"], 47)  # def line, not decorator
        self.assertEqual(by_id["pkg.core.configured.inner.deepest"]["kind"], "function")
        self.assertEqual(by_id["pkg.core.configured.inner.deepest"]["relations"],
                         [{"type": "contained-in", "target": "pkg.core.configured.inner"}])
        self.assertEqual(by_id["pkg.core.Engine.start"]["kind"], "method")
        self.assertEqual(by_id["pkg.core.Engine.Inner"]["kind"], "class")
        self.assertIsNone(by_id["pkg.core.plain"]["doc"])
        self.assertIn("pkg.core._Hidden._private", by_id)  # v1 skips nothing by name
        self.assertIn("pkg.sub.util._private_helper", by_id)
        # same-qualname redefinition (property setter) keeps both records, unique ids
        self.assertIn("pkg.core.Engine.label~2", by_id)
        # defs under `if` are not walked; non-.py files are not scanned; lambdas are no symbols
        self.assertFalse([i for i in by_id if "windows_only" in i or "lam" in i or "data" in i])
        for f in by_id.values():
            self.assertFalse(f["path"].startswith("/"))

    # v1: tests/selfdoc/test_python_scanner.py::test_source_graph_keeps_imports_references_and_containment_separate
    def test_source_graph_keeps_imports_references_and_containment_separate(self):
        source = self.tmp / "src" / "demo"
        source.mkdir(parents=True)
        (source / "types.py").write_text("class Thing:\n    pass\n", encoding="utf-8")
        (source / "consumer.py").write_text(
            "from .types import Thing as LocalThing\nclass Use:\n    value: LocalThing\n", encoding="utf-8")
        by_id = {f["id"]: f for f in facts_of(self.tmp / "src", "--package", "demo")}
        thing = "demo.demo.types.Thing"
        use = by_id["demo.demo.consumer.Use"]
        edges = {(r["type"], r["target"]) for r in use["relations"]}
        self.assertIn(("references", thing), edges)
        self.assertIn(("contained-in", "demo.demo.consumer"), edges)  # v1 `contains`, inverted
        module = by_id["demo.demo.consumer"]
        resolved = {(r["type"], r["target"]) for r in module["relations"] if "external" in r}
        self.assertIn(("imports", thing), resolved)
        self.assertIn({"type": "imports", "target": thing, "external": False}, module["relations"])
        self.assertIn({"type": "imports", "target": ".types"}, module["relations"])  # raw list untouched
        self.assertEqual(module["attrs"]["imports"], [".types"])
        # containment, imports and references stay separate relation types
        self.assertNotIn(("imports", "demo.demo.consumer"), edges)
        self.assertNotIn(("contained-in", thing), edges)

    def test_external_and_module_import_targets(self):
        (self.tmp / "a.py").write_text("import os.path\nfrom . import b\nfrom b import f\nx: os.PathLike = 1\n")
        (self.tmp / "b.py").write_text("def f(): pass\n")
        by_id = {f["id"]: f for f in facts_of(self.tmp)}
        rels = [(r["type"], r["target"], r["external"]) for r in by_id["a"]["relations"] if "external" in r]
        self.assertEqual(rels, [("imports", "b", True), ("imports", "b.f", False), ("imports", "os.path", True),
                                ("references", "os.path", True)])

    # v1: tests/selfdoc/test_python_scanner.py::test_source_graph_rejects_an_endpoint_outside_registered_relation_types
    # Adapter-level part only: the relation vocabulary and its endpoint rules hold for every emitted
    # relation. The registry rejection itself (RelationTypeDoc source/target types, cardinality) is kimun-side.
    def test_relation_vocabulary_and_endpoints_are_closed(self):
        facts = facts_of(PYTREE)
        by_id = {f["id"]: f for f in facts}
        for f in facts:
            for r in f["relations"]:
                self.assertIn(r["type"], {"contained-in", "imports", "references"})
                self.assertEqual(sorted(r), ["external", "target", "type"] if "external" in r else ["target", "type"])
                if r["type"] == "contained-in":
                    self.assertNotEqual(f["kind"], "module")
                    self.assertIn(by_id[r["target"]]["kind"], {"module", "class", "function", "method"})
                elif "external" in r:
                    self.assertEqual(r["target"] in by_id, not r["external"], r)
                else:  # raw module import list: module facts only
                    self.assertEqual((r["type"], f["kind"]), ("imports", "module"))

    def test_describe(self):
        rc, out, _ = run("python_symbols", "--describe")
        d = json.loads(out)
        self.assertEqual((rc, d["adapter"], d["protocol"], d["profile"], d["languages"]),
                         (0, "python-symbols", 1, "symbols", ["python"]))
        self.assertEqual(d["runtime"], {"available": True, "missing": []})
        self.assertTrue({"name", "help"} <= set(d["options"][0]))

    def test_envelope_sorted_and_deterministic(self):
        a, b = run("python_symbols", PYTREE), run("python_symbols", PYTREE, "--system", "demo")
        self.assertEqual(a, b)
        doc = json.loads(a[1])
        self.assertEqual((doc["protocol"], doc["adapter"], doc["profile"], doc["language"]),
                         (1, "python-symbols", "symbols", "python"))
        ids = [f["id"] for f in doc["facts"]]
        self.assertEqual(ids, sorted(ids))
        self.assertEqual(len(ids), len(set(ids)))
        for f in doc["facts"]:
            self.assertEqual(set(f), {"id", "kind", "name", "qualified_name", "path", "span", "source_hash",
                                      "signature", "synopsis", "doc", "relations", "attrs"})

    def test_syntax_error_exits_1_with_empty_stdout(self):
        (self.tmp / "ok.py").write_text("x = 1\n")
        (self.tmp / "bad.py").write_text("def broken(:\n")
        rc, out, err = run("python_symbols", self.tmp)
        self.assertEqual((rc, out), (1, ""))
        self.assertTrue(err.strip().splitlines()[-1].startswith("error: bad.py"))

    def test_usage_error_exits_2(self):
        self.assertEqual(run("python_symbols")[0], 2)
        self.assertEqual(run("python_symbols", PYTREE, "--bogus")[0], 2)

    def test_missing_root_exits_1(self):
        rc, out, err = run("python_symbols", self.tmp / "nope")
        self.assertEqual((rc, out), (1, ""))
        self.assertTrue(err.strip().startswith("error:"))

    def test_nothing_is_written_into_root(self):
        root = self.tmp / "tree"
        shutil.copytree(PYTREE, root)
        before = snapshot(root)
        run_json("python_symbols", root, bytecode=True)
        self.assertEqual(snapshot(root), before)


if __name__ == "__main__":
    unittest.main()
