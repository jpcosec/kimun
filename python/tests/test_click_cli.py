"""click_cli adapter: v1 ClickScanner semantics, runtime-missing behaviour, exit codes."""
import importlib.util
import json
import shutil
import subprocess
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path

from _support_a import ADAPTERS, run, run_json, snapshot

HAS_CLICK = importlib.util.find_spec("click") is not None
NEEDS_CLICK = unittest.skipUnless(HAS_CLICK, "click is not installed: click_cli scan tests skipped")

APP = '''
import click

@click.group()
def cli():
    """Root."""

@cli.command()
@click.argument("name")
@click.option("--count", "-c", default=3, help="How many.")
@click.option("--mode", type=click.Choice(["a", "b"]), default="a", help="Mode.")
@click.option("--flag", is_flag=True)
def hello(name, count, mode, flag):
    """Say hello.

    Longer text.
    """

@cli.command(hidden=True)
@click.option("--path", type=click.Path(), required=True)
def secret(path):
    pass

@cli.command(help="Help string wins")
def nodoc():
    pass

@cli.group()
def sub():
    """A subgroup (not a leaf)."""

def build():
    return cli
'''


def run_without_click(*args):
    """Run click_cli.py with `click` hidden from find_spec, whether or not it is installed."""
    code = textwrap.dedent("""
        import importlib.util, runpy, sys
        real = importlib.util.find_spec
        importlib.util.find_spec = lambda name, *a, **k: None if name == "click" else real(name, *a, **k)
        sys.argv = sys.argv[1:]
        runpy.run_path(sys.argv[0], run_name="__main__")
    """)
    p = subprocess.run([sys.executable, "-c", code, str(ADAPTERS / "click_cli.py"), *map(str, args)],
                       capture_output=True, text=True, env={"PYTHONDONTWRITEBYTECODE": "1", "PATH": ""})
    return p.returncode, p.stdout, p.stderr


class ClickCliTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="kimun-click-"))
        self.addCleanup(shutil.rmtree, self.tmp, True)
        (self.tmp / "app.py").write_text(APP, encoding="utf-8")

    def test_describe_when_runtime_missing(self):
        rc, out, _ = run_without_click("--describe")
        d = json.loads(out)
        self.assertEqual((rc, d["adapter"], d["profile"]), (0, "click-cli", "cli"))
        self.assertEqual(d["runtime"], {"available": False, "missing": ["click"]})

    def test_scan_exits_3_when_runtime_missing(self):
        rc, out, err = run_without_click(self.tmp, "--factory", "app:build")
        self.assertEqual((rc, out), (3, ""))
        self.assertEqual(err.strip().splitlines()[-1], "error: runtime missing: click")

    def test_describe_reports_runtime(self):
        rc, out, _ = run("click_cli", "--describe")
        d = json.loads(out)
        self.assertEqual((rc, d["protocol"], d["languages"]), (0, 1, ["python"]))
        self.assertEqual(d["runtime"]["available"], HAS_CLICK)
        self.assertEqual(d["runtime"]["missing"], [] if HAS_CLICK else ["click"])

    def test_usage_error_exits_2(self):
        self.assertEqual(run("click_cli")[0], 2)

    @NEEDS_CLICK
    def test_leaf_commands_become_facts(self):
        facts = run_json("click_cli", self.tmp, "--factory", "app:build")["facts"]
        by_id = {f["id"]: f for f in facts}
        self.assertEqual([f["id"] for f in facts if f["kind"] == "command"], ["hello", "nodoc", "secret"])
        hello = by_id["hello"]
        self.assertEqual((hello["signature"], hello["synopsis"], hello["doc"]), ("pron hello", "Say hello.\n\nLonger text.\n", "Say hello.\n\nLonger text."))
        self.assertTrue(by_id["secret"]["attrs"]["hidden"])
        self.assertEqual(by_id["nodoc"]["synopsis"], "Help string wins")
        count = by_id["hello#count"]
        self.assertEqual((count["attrs"]["default"], count["attrs"]["names"], count["attrs"]["action"]),
                         ("3", ["--count", "-c"], "Option"))
        self.assertEqual(by_id["hello#mode"]["attrs"]["choices"], ['"a"', '"b"'])
        self.assertTrue(by_id["hello#name"]["attrs"]["required"])
        self.assertEqual(by_id["hello#name"]["relations"], [{"type": "contained-in", "target": "hello"}])
        self.assertEqual([a["names"] for a in hello["attrs"]["arguments"]], [["name"], ["--count", "-c"], ["--mode"], ["--flag"]])

    @NEEDS_CLICK
    def test_program_option_deterministic_and_sorted(self):
        a = run("click_cli", self.tmp, "--factory", "app:build", "--program", "tool")
        self.assertEqual(a, run("click_cli", self.tmp, "--factory", "app:build", "--program", "tool"))
        facts = json.loads(a[1])["facts"]
        self.assertEqual(next(f for f in facts if f["id"] == "hello")["signature"], "tool hello")
        ids = [f["id"] for f in facts]
        self.assertEqual(ids, sorted(ids))

    @NEEDS_CLICK
    def test_factory_errors_exit_1(self):
        for ref in ("nomod:build", "app:missing", "appnocolon"):
            rc, out, err = run("click_cli", self.tmp, "--factory", ref)
            self.assertEqual((rc, out), (1, ""), ref)
            self.assertTrue(err.strip().splitlines()[-1].startswith("error: "), ref)

    @NEEDS_CLICK
    def test_nothing_is_written_into_root(self):
        before = snapshot(self.tmp)
        run_json("click_cli", self.tmp, "--factory", "app:build", bytecode=True)
        self.assertEqual(snapshot(self.tmp), before)


if __name__ == "__main__":
    unittest.main()
