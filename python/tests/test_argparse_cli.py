"""argparse_cli adapter: v1 ParserScanner semantics, contract output, exit codes."""
import json
import shutil
import tempfile
import textwrap
import unittest
from pathlib import Path

from _support_a import run, run_json, snapshot

NESTED = '''
import argparse

def build():
    parser = argparse.ArgumentParser(prog="app")
    parser.add_argument("--verbose", action="store_true")
    sub = parser.add_subparsers(dest="cmd")
    group = sub.add_parser("docs", help="Documents")
    leaves = group.add_subparsers(dest="operation")
    leaf = leaves.add_parser("export", help="Export documents")
    exclusive = leaf.add_mutually_exclusive_group(required=True)
    exclusive.add_argument("--json", action="store_true")
    exclusive.add_argument("--yaml", action="store_true")
    leaf.add_argument("--limit", type=int, default=3, choices=[1, 3, 5])
    return parser
'''

HIDDEN = '''
import argparse

def build():
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers()
    sub.add_parser("public")
    hidden = sub.add_parser("old", help=argparse.SUPPRESS)
    hidden.add_subparsers().add_parser("run", help="Old command")
    return parser
'''

DEFAULT = '''
import argparse

def build():
    parser = argparse.ArgumentParser()
    child = parser.add_subparsers().add_parser("run")
    child.add_argument("--count", default=%s)
    return parser
'''

WIDE = '''
import argparse

def build():
    parser = argparse.ArgumentParser(prog="app")
    child = parser.add_subparsers().add_parser("run")
    child.add_argument("--a-long-option-name", choices=["first", "second"])
    child.add_argument("--another-long-option-name", required=True)
    return parser
'''


class ArgparseCliTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="kimun-argp-"))
        self.addCleanup(shutil.rmtree, self.tmp, True)

    def factory(self, source, name="fx"):
        (self.tmp / f"{name}.py").write_text(textwrap.dedent(source), encoding="utf-8")
        return f"{name}:build"

    def scan(self, source, *extra, env=None):
        return run_json("argparse_cli", self.tmp, "--factory", self.factory(source), *extra, env=env)["facts"]

    @staticmethod
    def commands(facts):
        return [f for f in facts if f["kind"] in ("command", "group")]

    # v1: tests/selfdoc/test_parser.py::test_nested_arguments_and_required_exclusive_group
    def test_nested_arguments_and_required_exclusive_group(self):
        facts = self.scan(NESTED)
        leaf = next(f for f in self.commands(facts) if f["kind"] == "command")
        by_name = {a["names"][0]: a for a in leaf["attrs"]["arguments"]}
        self.assertEqual(leaf["attrs"]["path"], ["docs", "export"])
        self.assertEqual(leaf["id"], "docs export")
        self.assertEqual(by_name["--verbose"]["default"], "false")
        self.assertEqual(by_name["--limit"]["choices"], ["1", "3", "5"])
        self.assertEqual(by_name["--limit"]["value_type"], "builtins.int")
        self.assertTrue(leaf["attrs"]["exclusive_groups"][0]["required"])
        self.assertEqual(leaf["attrs"]["exclusive_groups"][0]["arguments"], ["json", "yaml"])
        # the group fact, and one argument fact per own argument contained in its command
        self.assertEqual(next(f for f in facts if f["id"] == "docs")["kind"], "group")
        arg = next(f for f in facts if f["id"] == "docs export#limit")
        self.assertEqual((arg["kind"], arg["relations"]), ("argument", [{"type": "contained-in", "target": "docs export"}]))
        self.assertEqual(arg["attrs"]["nargs"], None)
        self.assertEqual(arg["attrs"]["action"], "argparse._StoreAction")

    # v1: tests/selfdoc/test_parser.py::test_hidden_parent_suppresses_descendants_but_help_omission_does_not
    def test_hidden_parent_suppresses_descendants_but_help_omission_does_not(self):
        shown = [f["attrs"]["path"] for f in self.commands(self.scan(HIDDEN))]
        self.assertEqual(shown, [["public"]])
        everything = self.commands(self.scan(HIDDEN, "--include-hidden"))
        self.assertTrue(next(f for f in everything if f["attrs"]["path"] == ["old", "run"])["attrs"]["hidden"])

    # v1: tests/selfdoc/test_parser.py::test_contract_hash_changes_with_parser_default
    # (adapter half: the facts are stable across runs and change with the default;
    #  the `provenance` contract hash itself is kimun-side, see report)
    def test_facts_change_with_parser_default(self):
        one = run("argparse_cli", self.tmp, "--factory", self.factory(DEFAULT % 1, "fx1"))
        again = run("argparse_cli", self.tmp, "--factory", self.factory(DEFAULT % 1, "fx1"))
        two = run("argparse_cli", self.tmp, "--factory", self.factory(DEFAULT % 2, "fx2"))
        self.assertEqual(one[0:2], again[0:2])
        self.assertNotEqual(one[1], two[1])
        count = next(f for f in json.loads(two[1])["facts"] if f["id"] == "run#count")
        self.assertEqual(count["attrs"]["default"], "2")

    # v1: tests/selfdoc/test_parser.py::test_contract_hash_does_not_depend_on_terminal_width
    # (adapter half: output is byte-identical under any COLUMNS)
    def test_facts_do_not_depend_on_terminal_width(self):
        factory = self.factory(WIDE)
        narrow = run("argparse_cli", self.tmp, "--factory", factory, env={"COLUMNS": "40"})
        wide = run("argparse_cli", self.tmp, "--factory", factory, env={"COLUMNS": "160"})
        self.assertEqual(narrow[0], 0)
        self.assertEqual(narrow, wide)

    def test_describe(self):
        rc, out, _ = run("argparse_cli", "--describe")
        d = json.loads(out)
        self.assertEqual((rc, d["adapter"], d["protocol"], d["profile"], d["languages"]),
                         (0, "argparse-cli", 1, "cli", ["python"]))
        self.assertEqual(d["runtime"], {"available": True, "missing": []})
        self.assertIn("--factory", [o["name"] for o in d["options"]])

    def test_envelope_sorted_and_deterministic(self):
        factory = self.factory(NESTED)
        a = run("argparse_cli", self.tmp, "--factory", factory)
        self.assertEqual(a, run("argparse_cli", self.tmp, "--factory", factory))
        doc = json.loads(a[1])
        self.assertEqual((doc["adapter"], doc["profile"], doc["language"]), ("argparse-cli", "cli", "python"))
        ids = [f["id"] for f in doc["facts"]]
        self.assertEqual(ids, sorted(ids))
        self.assertEqual(len(ids), len(set(ids)))
        self.assertNotIn("", ids)

    def test_factory_output_does_not_pollute_stdout(self):
        facts = self.scan("import argparse\nprint('noise')\ndef build():\n    print('more')\n    return argparse.ArgumentParser()\n")
        self.assertEqual(facts, [])

    def test_exit_codes(self):
        (self.tmp / "boom.py").write_text("def build():\n    raise RuntimeError('nope')\n")
        (self.tmp / "wrong.py").write_text("def build():\n    return 3\n")
        for args in (["--factory", "boom:build"], ["--factory", "missing_mod:build"],
                     ["--factory", "wrong:build"], ["--factory", "nocolon"]):
            rc, out, err = run("argparse_cli", self.tmp, *args)
            self.assertEqual((rc, out), (1, ""), args)
            self.assertTrue(err.strip().splitlines()[-1].startswith("error: "), args)
        self.assertEqual(run("argparse_cli", self.tmp)[0], 2)
        self.assertEqual(run("argparse_cli")[0], 2)

    def test_nothing_is_written_into_root(self):
        self.factory(NESTED)
        before = snapshot(self.tmp)
        run_json("argparse_cli", self.tmp, "--factory", "fx:build", bytecode=True)
        self.assertEqual(snapshot(self.tmp), before)


if __name__ == "__main__":
    unittest.main()
