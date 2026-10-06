#!/usr/bin/env python3
"""Scoreboard of the parity effort: every test function of sldb v1 against kimun.

For each `def test_*` under v1's tests/ it reports exactly one status. Explicit decisions
(the data files under docs/v2/tests/) outrank the heuristics; the first that applies wins:

  gap       - listed in parity-gaps.edn: a ported test showed kimun does NOT hold the v1
              promise; test/kimun/parity/known_gaps_test.cljc pins the current behaviour
  n/a       - listed in parity-na.edn with a written reason (v1 mechanism, why kimun has
              none, what holds the equivalent promise now)
  mapped    - listed in parity-map.edn: an existing kimun deftest that does not name the
              v1 function asserts the same promise; the deftest must exist
  ported    - a kimun test file names the v1 function in a line that is a port. A name
              that appears only in comments saying n/a / deferred / not ported / partial /
              "no counterpart" is NOT a port (see `mentioned`)
  oracle    - listed in parity-oracle.edn as a test whose SUBJECT is the template engine
              (extract / render / validate / segments) AND it produced recorded cases in
              a differential corpus (test/fixtures/parity/{engine,segments}/*/meta.edn)
  mentioned - named in kimun test files, but only in such negative comments: unclassified
  corpus    - produced corpus cases but nobody declared it engine-subject: the corpus
              only proves what it compares, so this is unclassified work too
  open      - none of the above

Data files (EDN, read with a tiny built-in reader; `--data-dir` overrides their folder):
  parity-na.edn      {"file::test" "reason"}
  parity-map.edn     {"file::test" {:test "kimun.ns-test/deftest-name" :note "..."}}
  parity-oracle.edn  {"file::test" "what the corpus proves for it"}
  parity-gaps.edn    {"file::test" {:promise "..." :kimun "..." :fix "..."}}

Checked and reported as errors: a mapping whose deftest does not exist; an `ns/deftest`
named inside an n/a reason (or a map note, or a gap) that does not exist; an oracle entry
without corpus cases; an entry for a v1 test that does not exist; a key listed in two
data files. Reported as warnings: a data-file entry for a test that a kimun file also
ports by name.

Usage: coverage.py V1_ROOT KIMUN_ROOT [--list-open] [--json] [--strict]
                   [--allow-open-prefix PREFIX]... [--allow-gaps] [--data-dir DIR]
--strict exits 1 when anything is open / mentioned / corpus (outside the allowed
prefixes), when there is any gap (unless --allow-gaps), or on any error above.
"""
import argparse, ast, collections, json, pathlib, re, sys

STATUSES = ("ported", "mapped", "oracle", "n/a", "gap", "mentioned", "corpus", "open")
UNCLASSIFIED = ("mentioned", "corpus", "open")
NAME = re.compile(r"\btest_[A-Za-z0-9_]+")
V1_ALIAS = re.compile(r"v1[:\s]+`?([a-z0-9][a-z0-9_-]+)")
NEGATIVE = re.compile(
    r"(?<![\w/])n/a(?![\w/])|\bdeferr?ed\b|\bdiferid|\bnot\s+(?:yet\s+)?ported\b|\bno\s+portad"
    r"|\bno\s+(?:kimun\s+)?counterpart|\bsin\s+contraparte|\bsin\s+equivalente|\bno\s+aplica\b"
    r"|\bpartial(?:ly)?\b|\bparcial(?:mente)?\b|\bpending\b|\bpendiente|\bretired\b|\bretirad",
    re.I)
FRESH = re.compile(r"^\s*[;#]+\s*\(?v1\b", re.I)
REF = re.compile(r"\b((?:kimun|sldb)(?:\.[a-z0-9-]+)+)/([A-Za-z0-9*+!?<>=_.-]*[A-Za-z0-9*+!?<>=_-])")

# ---------------------------------------------------------------- v1 side
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

# ---------------------------------------------------------------- kimun mentions
def names_in(line):
    return set(NAME.findall(line)) | {"test_" + m.replace("-", "_") for m in V1_ALIAS.findall(line)}

def is_comment(line, mark):
    return line.lstrip().startswith(mark)

def scan_mentions(root):
    """name -> {"port": [(file, line)], "neg": [(file, line)]}.

    A mention in code or in a string is a port. A mention in a comment is negative when
    the comment says n/a / deferred / partial / ... on the same line (test names removed
    first, so `test_x_partial_y` is not a marker) or on an earlier line of the same
    contiguous comment block, unless a fresh `;; v1: ...` statement started in between.
    """
    found = collections.defaultdict(lambda: {"port": [], "neg": []})
    for base, pats, mark in (("test", ("*.cljc", "*.clj"), ";"), ("python", ("*.py",), "#")):
        d = root / base
        if not d.exists():
            continue
        for pat in pats:
            for p in sorted(d.rglob(pat)):
                rel = str(p.relative_to(root))
                tainted = False
                for i, line in enumerate(p.read_text(errors="replace").splitlines(), 1):
                    if not is_comment(line, mark):
                        tainted = False
                        for n in names_in(line):
                            found[n]["port"].append((rel, i))
                        continue
                    bare = NAME.sub(" ", line)
                    here = bool(NEGATIVE.search(bare))
                    if FRESH.search(line):
                        tainted = False
                    neg = here or tainted
                    for n in names_in(line):
                        found[n]["neg" if neg else "port"].append((rel, i))
                    tainted = tainted or here
    return found

def oracle_names(root):
    names = set()
    for corpus in ("engine", "segments"):
        d = root / "test" / "fixtures" / "parity" / corpus
        if d.exists():
            for p in d.glob("*/meta.edn"):
                names |= set(re.findall(r"::(test_[A-Za-z0-9_]+)", p.read_text(errors="replace")))
    return names

# ---------------------------------------------------------------- tiny EDN reader
TOKEN = re.compile(r'\s*(?:;[^\n]*|,|(\{)|(\})|"((?:[^"\\]|\\.)*)"|(:[^\s{}",;]+)|([^\s{}",;]+))', re.S)

def read_edn_map(path):
    """Reads {"k" "v" | {:kw "v" ...}} - strings, keywords and nested maps only."""
    if not path.exists():
        return {}
    text, pos, toks = path.read_text(), 0, []
    while pos < len(text):
        m = TOKEN.match(text, pos)
        if not m or m.end() == pos:
            if text[pos:].strip():
                raise ValueError(f"{path}: cannot read at offset {pos}: {text[pos:pos+40]!r}")
            break
        pos = m.end()
        if m.group(1): toks.append(("{",))
        elif m.group(2): toks.append(("}",))
        elif m.group(3) is not None:
            toks.append(("s", re.sub(r'\\(.)', lambda e: {"n": "\n", "t": "\t"}.get(e.group(1), e.group(1)), m.group(3))))
        elif m.group(4): toks.append(("k", m.group(4)[1:]))
        elif m.group(5): toks.append(("s", m.group(5)))
    it = iter(toks)
    def value(tok):
        if tok[0] == "{":
            out = {}
            for k in it:
                if k[0] == "}":
                    return out
                out[k[1]] = value(next(it))
            raise ValueError(f"{path}: unbalanced map")
        return tok[1]
    first = next(it, None)
    return value(first) if first else {}

# ---------------------------------------------------------------- deftest lookup
class Deftests:
    def __init__(self, root):
        self.root, self.cache = root, {}
    def file_of(self, ns):
        rel = ns.replace(".", "/").replace("-", "_")
        for ext in (".cljc", ".clj", ".bb"):
            p = self.root / "test" / (rel + ext)
            if p.exists():
                return p
        return None
    def problem(self, ref):
        """None when `ns/name` is a deftest of the file of `ns`, else why not."""
        if "/" not in ref:
            return f"{ref}: not of the form ns/deftest"
        ns, name = ref.split("/", 1)
        p = self.file_of(ns)
        if p is None:
            return f"{ref}: no test file for namespace {ns}"
        if p not in self.cache:
            self.cache[p] = set(re.findall(r"\(\s*(?:deftest|defspec)\s+(?:\^\S+\s+)*([^\s()\[\]]+)", p.read_text(errors="replace")))
        return None if name in self.cache[p] else f"{ref}: no such deftest in {p.relative_to(self.root)}"

def refs_in(text):
    return [f"{ns}/{name}" for ns, name in REF.findall(text) if ns.endswith("-test")]

# ---------------------------------------------------------------- main
def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("v1"); ap.add_argument("kimun")
    ap.add_argument("--list-open", action="store_true"); ap.add_argument("--json", action="store_true")
    ap.add_argument("--strict", action="store_true"); ap.add_argument("--allow-gaps", action="store_true")
    ap.add_argument("--allow-open-prefix", action="append", default=[], metavar="PREFIX")
    ap.add_argument("--data-dir", default=None)
    a = ap.parse_args()
    v1, kimun = pathlib.Path(a.v1), pathlib.Path(a.kimun)
    data = pathlib.Path(a.data_dir) if a.data_dir else kimun / "docs" / "v2" / "tests"
    tests, mentions, corpus = v1_tests(v1), scan_mentions(kimun), oracle_names(kimun)
    na, mp, orc, gaps = (read_edn_map(data / f"parity-{k}.edn") for k in ("na", "map", "oracle", "gaps"))
    defs, errors, warnings = Deftests(kimun), [], []
    keys = {f"{r}::{n}" for r, n in tests}
    files = (("na", na), ("map", mp), ("oracle", orc), ("gaps", gaps))
    for label, d in files:
        for k in sorted(set(d) - keys):
            errors.append(f"parity-{label}.edn: no such v1 test: {k}")
    for i, (la, da) in enumerate(files):
        for lb, db in files[i + 1:]:
            for k in sorted(set(da) & set(db)):
                errors.append(f"{k}: listed in both parity-{la}.edn and parity-{lb}.edn")
    for k, v in sorted(mp.items()):
        if not isinstance(v, dict) or not v.get("test"):
            errors.append(f"parity-map.edn: {k}: needs {{:test \"ns/deftest\" :note \"...\"}}"); continue
        for ref in [v["test"]] + refs_in(v.get("note", "")):
            why = defs.problem(ref)
            if why: errors.append(f"parity-map.edn: {k}: broken mapping: {why}")
    for k, v in sorted(na.items()):
        if not isinstance(v, str) or len(v.strip()) < 40:
            errors.append(f"parity-na.edn: {k}: the reason must say the v1 mechanism, why kimun has none, and what holds the promise now")
            continue
        for ref in refs_in(v):
            why = defs.problem(ref)
            if why: errors.append(f"parity-na.edn: {k}: broken reference: {why}")
    for k, v in sorted(gaps.items()):
        if not isinstance(v, dict) or not all(v.get(f) for f in ("promise", "kimun", "fix")):
            errors.append(f"parity-gaps.edn: {k}: needs :promise :kimun :fix"); continue
        for ref in refs_in(" ".join(str(x) for x in v.values())):
            why = defs.problem(ref)
            if why: errors.append(f"parity-gaps.edn: {k}: broken reference: {why}")
    rows = []
    for rel, name in tests:
        key, m = f"{rel}::{name}", mentions.get(name, {"port": [], "neg": []})
        if key in orc and name not in corpus:
            errors.append(f"parity-oracle.edn: {key}: no corpus case names it")
        explicit = "gap" if key in gaps else "n/a" if key in na else "mapped" if key in mp else None
        if explicit in ("n/a", "mapped") and m["port"]:
            f, ln = m["port"][0]
            warnings.append(f"{key}: {explicit} by data file, but {f}:{ln} names it as a port")
        status = (explicit or ("ported" if m["port"] else "oracle" if key in orc and name in corpus
                               else "mentioned" if m["neg"] else "corpus" if name in corpus else "open"))
        rows.append({"file": rel, "test": name, "status": status,
                     "where": [f"{f}:{ln}" for f, ln in (m["port"] or m["neg"])[:3]]})
    allowed = lambda r: any(r["file"].startswith(p) for p in a.allow_open_prefix)
    blocking = [r for r in rows if (r["status"] in UNCLASSIFIED and not allowed(r))
                or (r["status"] == "gap" and not a.allow_gaps)]
    failed = a.strict and bool(blocking or errors)
    if a.json:
        print(json.dumps({"tests": rows, "errors": errors, "warnings": warnings}, indent=1))
        sys.exit(1 if failed else 0)
    by_file = collections.OrderedDict()
    for r in rows:
        by_file.setdefault(r["file"], collections.Counter())[r["status"]] += 1
    tot = collections.Counter(r["status"] for r in rows)
    heads = ("ported", "mapped", "oracle", "n/a", "gap", "ment.", "corpus", "open")
    line = lambda label, c: f"{label:58} {sum(c.values()):5} " + " ".join(f"{c[s]:>6}" for s in STATUSES)
    print(f"{'v1 test file':58} {'total':>5} " + " ".join(f"{h:>6}" for h in heads))
    for rel, c in by_file.items():
        print(line(rel, c))
    print(line("TOTAL", tot))
    if a.list_open:
        for s in UNCLASSIFIED + ("gap",):
            sel = [r for r in rows if r["status"] == s]
            if sel:
                print(f"\n{s}:")
                for r in sel:
                    note = "  (allowed prefix)" if allowed(r) and s != "gap" else ""
                    print(f"  {r['file']}::{r['test']}{note}" + (f"  <- {', '.join(r['where'])}" if r["where"] else ""))
    if warnings:
        print("\nwarnings:", *warnings, sep="\n  ")
    if errors:
        print("\nerrors:", *errors, sep="\n  ")
    if a.strict:
        n_allowed = sum(1 for r in rows if r["status"] in UNCLASSIFIED and allowed(r))
        print(f"\nstrict: {'FAIL' if failed else 'ok'} - {len(blocking)} blocking, {len(errors)} errors"
              + (f", {n_allowed} unclassified under allowed prefixes" if n_allowed else "")
              + (f", {tot['gap']} gaps allowed" if a.allow_gaps and tot["gap"] else ""))
    sys.exit(1 if failed else 0)

if __name__ == "__main__":
    main()
