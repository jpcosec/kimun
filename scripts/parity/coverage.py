#!/usr/bin/env python3
"""Scoreboard of the parity effort: every test function of sldb v1 against kimun.

For each `def test_*` under v1's tests/ it reports one status:
  ported  - a kimun test file names the v1 function (the porting rule: every
            ported test carries a comment or string with the v1 name)
  oracle  - the function produced recorded cases in a differential corpus
            (test/fixtures/parity/{engine,segments}/*/meta.edn name it)
  n/a     - listed in docs/v2/tests/parity-na.edn with a written reason
  open    - none of the above

Usage: coverage.py V1_ROOT KIMUN_ROOT [--list-open] [--json]
"""
import ast, json, pathlib, re, sys, collections

def v1_tests(root):
    out = []
    for p in sorted((root / "tests").rglob("test_*.py")):
        tree = ast.parse(p.read_text())
        rel = str(p.relative_to(root / "tests"))
        for node in tree.body:
            if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name.startswith("test_"):
                out.append((rel, node.name))
            elif isinstance(node, ast.ClassDef):
                for sub in node.body:
                    if isinstance(sub, (ast.FunctionDef, ast.AsyncFunctionDef)) and sub.name.startswith("test_"):
                        out.append((rel, sub.name))
    return out

def kimun_names(root):
    text = []
    for base, pats in (("test", ("*.cljc", "*.clj")), ("python", ("*.py",))):
        d = root / base
        if d.exists():
            for pat in pats:
                for p in d.rglob(pat):
                    text.append(p.read_text(errors="replace"))
    blob = "\n".join(text)
    return set(re.findall(r"\btest_[A-Za-z0-9_]+", blob)) | {
        "test_" + m.replace("-", "_") for m in re.findall(r"v1[:\s]+`?([a-z0-9][a-z0-9_-]+)", blob)}

def oracle_names(root):
    names = set()
    for corpus in ("engine", "segments"):
        d = root / "test" / "fixtures" / "parity" / corpus
        if d.exists():
            for p in d.glob("*/meta.edn"):
                names |= set(re.findall(r"::(test_[A-Za-z0-9_]+)", p.read_text(errors="replace")))
    return names

def na_entries(root):
    p = root / "docs" / "v2" / "tests" / "parity-na.edn"
    if not p.exists():
        return {}
    return dict(re.findall(r'"([^"\s]+::test_[A-Za-z0-9_]+)"\s+"((?:[^"\\]|\\.)*)"', p.read_text()))

def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    v1, kimun = pathlib.Path(args[0]), pathlib.Path(args[1])
    tests, named, oracle, na = v1_tests(v1), kimun_names(kimun), oracle_names(kimun), na_entries(kimun)
    rows = []
    for rel, name in tests:
        key = f"{rel}::{name}"
        status = ("n/a" if key in na else "ported" if name in named else "oracle" if name in oracle else "open")
        rows.append((rel, name, status))
    if "--json" in sys.argv:
        print(json.dumps([{"file": r, "test": n, "status": s} for r, n, s in rows], indent=1)); return
    by_file = collections.OrderedDict()
    for rel, _, s in rows:
        by_file.setdefault(rel, collections.Counter())[s] += 1
    tot = collections.Counter(s for _, _, s in rows)
    print(f"{'v1 test file':58} {'total':>5} {'ported':>6} {'oracle':>6} {'n/a':>4} {'open':>4}")
    for rel, c in by_file.items():
        print(f"{rel:58} {sum(c.values()):5} {c['ported']:6} {c['oracle']:6} {c['n/a']:4} {c['open']:4}")
    print(f"{'TOTAL':58} {sum(tot.values()):5} {tot['ported']:6} {tot['oracle']:6} {tot['n/a']:4} {tot['open']:4}")
    stale = sorted(k for k in na if tuple(k.split("::")) not in {(r, n) for r, n, _ in rows})
    if stale:
        print("\nstale n/a entries (no such v1 test):", *stale, sep="\n  ")
    if "--list-open" in sys.argv:
        print("\nopen:")
        for rel, name, s in rows:
            if s == "open":
                print(f"  {rel}::{name}")

if __name__ == "__main__":
    main()
