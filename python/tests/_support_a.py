"""Shared helpers for the lane-A adapter tests (subprocess runner, tree snapshots)."""
import json
import os
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
PYTHON_DIR = HERE.parent
REPO = PYTHON_DIR.parent
ADAPTERS = PYTHON_DIR / "kimun" / "adapters"
PYTREE = REPO / "test" / "fixtures" / "adapters" / "pytree"


def run(adapter, *args, env=None, bytecode=False):
    """Run adapters/<adapter>.py; returns (returncode, stdout, stderr) as text."""
    full = {k: v for k, v in os.environ.items() if k != "PYTHONDONTWRITEBYTECODE"}
    if not bytecode:
        full["PYTHONDONTWRITEBYTECODE"] = "1"
    full.update(env or {})
    p = subprocess.run([sys.executable, str(ADAPTERS / f"{adapter}.py"), *map(str, args)],
                       capture_output=True, text=True, encoding="utf-8", env=full)
    return p.returncode, p.stdout, p.stderr


def run_json(adapter, *args, **kw):
    rc, out, err = run(adapter, *args, **kw)
    assert rc == 0, err
    return json.loads(out)


def snapshot(root):
    """Every path below root with its size, to prove a scan wrote nothing."""
    return sorted((str(p.relative_to(root)), p.stat().st_size if p.is_file() else -1)
                  for p in Path(root).rglob("*"))
