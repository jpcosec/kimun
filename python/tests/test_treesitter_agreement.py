"""Agreement of the tree-sitter adapter with the ast adapter (python_symbols) on the shared fixture trees."""
from __future__ import annotations

import json
import os
import subprocess
import sys
import unittest
from pathlib import Path

try:
    import tree_sitter  # noqa: F401
    import tree_sitter_python  # noqa: F401
except ImportError as exc:  # pragma: no cover - depends on the interpreter
    raise unittest.SkipTest(f"tree_sitter runtime not importable in {sys.executable}: {exc}")

HERE = Path(__file__).resolve().parent
ADAPTERS = HERE.parent / "kimun" / "adapters"
FIXTURES = HERE.parent.parent / "test" / "fixtures" / "adapters"
ENV = {**os.environ, "PYTHONDONTWRITEBYTECODE": "1"}
# facts both adapters can express; signature/imports are compared separately (see the ast adapter docs)
COMMON = ("id", "kind", "name", "qualified_name", "path", "span", "source_hash", "doc")


def facts_of(adapter: str, root: Path) -> dict:
    r = subprocess.run([sys.executable, str(ADAPTERS / adapter), str(root)], capture_output=True, text=True, env=ENV)
    assert r.returncode == 0, f"{adapter} failed: {r.stderr}"
    return {f["id"]: f for f in json.loads(r.stdout)["facts"]}


def contained_in(fact: dict) -> list:
    return [r["target"] for r in fact["relations"] if r["type"] == "contained-in"]


class AgreementTest(unittest.TestCase):
    def check(self, tree: str) -> int:
        root = FIXTURES / tree
        ast_facts, ts_facts = facts_of("python_symbols.py", root), facts_of("treesitter_symbols.py", root)
        self.assertEqual(sorted(ts_facts), sorted(ast_facts), "fact id sets differ")
        for fid, a in ast_facts.items():
            t = ts_facts[fid]
            for key in COMMON:
                self.assertEqual(t[key], a[key], f"{fid}: {key}")
            self.assertEqual(contained_in(t), contained_in(a), f"{fid}: contained-in")
            self.assertEqual(t["signature"] is None, a["signature"] is None, f"{fid}: signature presence")
        print(f"agreement[{tree}]: compared {len(ast_facts)} facts x {len(COMMON) + 1} properties", file=sys.stderr)
        return len(ast_facts)

    def test_shared_pytree(self):
        self.assertGreater(self.check("pytree"), 3)

    def test_edge_case_tree(self):
        """Decorators, async, nesting, conditional defs, docstring forms, duplicate ids, relative imports."""
        self.assertGreater(self.check("pytree_ts"), 19)

    def test_signature_and_imports_agree_on_edge_tree(self):
        root = FIXTURES / "pytree_ts"
        a, t = facts_of("python_symbols.py", root), facts_of("treesitter_symbols.py", root)
        for fid in a:
            self.assertEqual(t[fid]["signature"], a[fid]["signature"], fid)
            imports = lambda f: [r for r in f["relations"] if r["type"] == "imports" and "external" not in r]
            self.assertEqual(imports(t[fid]), imports(a[fid]), fid)


if __name__ == "__main__":
    unittest.main()
