"""Freeze the v1 selfdoc ground truth for test/kimun/parity/adapters_parity_test.cljc.

v1 documented itself with `sldb selfdoc python-sync --package sldb --architecture-spec
docs/architecture/spec2viz/python-ast.yml` (source root `src`, system `sldb`, output `knowledge`) and
`sldb selfdoc sync` (factory `sldb.cli.parser:build_parser`, hidden commands excluded). This script records,
for a sample of v1 source files and CLI groups:
  v1_live     what v1's own scanner + `_fields` / `command_fields` / `surface_fields` give NOW (parser-owned fields),
  v1_live_authored  the rest of the payload v1's planner would write now (`{**existing, **fields}`, validated,
                    minus the parser-owned fields): the authored fields kept from the tracked document, or the
                    defaults of a new one,
  v1_tracked  the payload of the document v1 actually tracks under knowledge/ (null: never generated), possibly stale.
Outputs: src/ (verbatim copies of the sampled sources, path kept relative to the scanned root), symbols.json,
cli-facts.json (argparse adapter output restricted to the sampled groups), cli.json, EXPECTED-DIFFS.edn.
v1 is only imported and read (bytecode writing disabled); nothing is written outside this directory.

Usage: python3 generate.py <v1-project> <recorded-v1-store>   (the latter from scripts/parity/record_v1_store.py)
"""
import sys

sys.dont_write_bytecode = True
import collections, glob, json, os, shutil, subprocess  # noqa: E402
from pathlib import Path  # noqa: E402

HERE = Path(__file__).resolve().parent
V1, STORE = Path(sys.argv[1]).resolve(), Path(sys.argv[2]).resolve()
sys.path.insert(0, str(V1 / "src"))
os.chdir(V1)
from sldb.selfdoc.python_scanner import PythonScanner  # noqa: E402
from sldb.selfdoc import python_materialize as pm, materialize as mz  # noqa: E402
from sldb.selfdoc.projection import command_fields, surface_fields  # noqa: E402
from sldb.cli.selfdoc_context import load_parser  # noqa: E402
from sldb.selfdoc.parser import ParserScanner  # noqa: E402

SYSTEM, PACKAGE, OUTPUT, FACTORY = "sldb", "sldb", "knowledge", "sldb.cli.parser:build_parser"
SPEC = "docs/architecture/spec2viz/python-ast.yml"
FILES = """sldb/core/contracts/marker.py sldb/store/io/documents_index.py sldb/store/diagnostics_models/store_diagnosis.py
sldb/cli/commands/fields.py sldb/cli/commands/legacy.py sldb/core/ast/markdown_ast_handler.py sldb/cli/warning_output.py
sldb/selfdoc/planned_document.py sldb/selfdoc/python_materialize.py sldb/selfdoc/projection.py sldb/selfdoc/kgdb_sync.py
sldb/store/models/models_index.py sldb/links/composer.py sldb/api/corpus/index_projection.py""".split()
EXTRA = sys.argv[3:]  # optional: more source files to sample (v1 has no `async def` anywhere, so none is sampled)
GROUPS = ["selfdoc", "stores", "init"]


def tracked(model):
    out = {}
    for f in glob.glob(str(STORE / model / "*.json")):
        p = json.load(open(f))["payload"]
        out[p["id"]] = p
    return out


def entry(name, sub, fields, plan, old, **more):
    payload = plan.payload.model_dump(mode="json")
    assert all(payload[k] == v for k, v in fields.items()), name
    return {"name": name, "path": f"{OUTPUT}/{sub}/{name}.md", "v1_live": fields,
            "v1_live_authored": {k: v for k, v in payload.items() if k not in fields}, "v1_tracked": old.get(name), **more}


def dump(path, head, docs, key="documents"):
    """One document per line: reviewable diffs without a megabyte of indentation."""
    q = lambda v: json.dumps(v, sort_keys=True, ensure_ascii=False)  # noqa: E731
    open(path, "w", encoding="utf-8").write(
        "{" + "".join(f"{q(k)}: {q(v)},\n" for k, v in sorted(head.items())) + q(key) + ": [\n"
        + ",\n".join(q(d) for d in docs) + "\n]}\n")


def diffs(docs, why_absent):
    out = []
    for d in docs:
        t = d["v1_tracked"]
        if t is None:
            out.append((d["name"], None, why_absent))
        else:
            stale = sorted(k for k, v in d["v1_live"].items() if t.get(k) != v)
            if stale:
                out.append((d["name"], stale, "v1's tracked document is stale: v1's own scanner over today's v1 source "
                            "gives kimun's value (frozen as v1_live); `sldb selfdoc check` reports the same drift"))
    return out


files = FILES + EXTRA
spec = pm._architecture_spec(V1 / SPEC, V1)
records = [r for r in PythonScanner().scan(V1 / "src", PACKAGE) if r.path in files]
old = tracked("PythonSymbolDoc")
docs, names = [], collections.Counter()
for r in records:
    fields = pm._fields(r, SYSTEM, spec)
    names[fields["id"]] += 1
    docs.append(entry(fields["id"], "symbols", fields, pm._plan(r, SYSTEM, V1 / OUTPUT, spec), old, kind=r.kind))
shutil.rmtree(HERE / "src", ignore_errors=True)
for f in files:
    (HERE / "src" / f).parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(V1 / "src" / f, HERE / "src" / f)
collisions = sorted(n for n, c in names.items() if c > 1)
dump(HERE / "symbols.json", {"invocation": {"adapter": "python-symbols", "root": "src", "package": PACKAGE, "system": SYSTEM,
                                            "output": OUTPUT, "architecture_spec": spec},
                             "collisions": collisions,
                             "collision_error": "Python symbols collide when converted to document IDs"}, docs)

crecords = [r for r in ParserScanner(False).scan(load_parser(FACTORY, V1 / "src", None)) if r.path[0] in GROUPS]
plans = {p.payload.id: p for p in mz.plan_documents(crecords, SYSTEM, FACTORY, V1 / OUTPUT)}
cold, sold, cdocs = tracked("CliCommandDoc"), tracked("SurfaceDoc"), []
for r in crecords:
    if not r.group:
        f = command_fields(r, SYSTEM, FACTORY)
        cdocs.append(entry(f["id"], "commands", f, plans[f["id"]], cold, model="CliCommandDoc"))
    if len(r.path) == 1:
        f = surface_fields(r, crecords, SYSTEM, FACTORY)
        cdocs.append(entry(f["id"], "surfaces", f, plans[f["id"]], sold, model="SurfaceDoc"))
adapter = HERE.parents[3] / "python" / "kimun" / "adapters" / "argparse_cli.py"
run = subprocess.run([sys.executable, str(adapter), str(V1 / "src"), "--system", SYSTEM, "--factory", FACTORY],
                     capture_output=True, text=True, check=True, env={**os.environ, "PYTHONDONTWRITEBYTECODE": "1"})
facts = json.loads(run.stdout)
facts["facts"] = [f for f in facts["facts"] if f["id"].split("#")[0].split(" ")[0] in GROUPS]
dump(HERE / "cli-facts.json", {k: v for k, v in facts.items() if k != "facts"}, facts["facts"], "facts")
dump(HERE / "cli.json", {"invocation": {"adapter": "argparse-cli", "factory": FACTORY, "system": SYSTEM, "output": OUTPUT,
                                        "groups": GROUPS}}, cdocs)

q = json.dumps
lines = [""";; Differences kimun's planned documents are ASSERTED to have against the documents v1 TRACKS (the `v1_tracked`
;; payloads of symbols.json / cli.json). Against what v1 generates now (`v1_live`) there is no tolerated difference.
;; Shape: a vector of maps, one of
;;   {:document "<document id>" :fields ["field" ...] :reason "..."}  exactly these parser-owned fields differ,
;;   {:document "<document id>" :absent "v1-tracked" :reason "..."}   v1 tracks no such document.
;; The test fails on any difference NOT listed and on a STALE entry (listed but not observed). No silent skips.
;; Generated by generate.py; do not edit by hand."""]
absent = ("v1 never generated it: the symbol/command was added to v1's source after its last `selfdoc sync` "
          "(v1's scanner emits it today, frozen as v1_live)")
rows = diffs(docs, absent) + diffs(cdocs, absent)
seen, body = set(), []
for name, stale, reason in rows:
    if name in seen:
        continue
    seen.add(name)
    if name in collisions:
        reason = ("two v1 symbols (a property and its setter) map to this one document id, so v1's python-sync raises "
                  "'Python symbols collide when converted to document IDs' and never wrote it; kimun reports the same collision")
    body.append("{:document %s, %s, :reason %s}" % (q(name), ":absent \"v1-tracked\"" if stale is None else ":fields [%s]" % " ".join(map(q, stale)), q(reason)))
open(HERE / "EXPECTED-DIFFS.edn", "w").write("\n".join(lines) + "\n[" + "\n ".join(body) + "]\n")
print("source files", len(files), "symbol docs", len(docs), "collisions", collisions, "cli docs",
      collections.Counter(d["model"] for d in cdocs), "cli facts", len(facts["facts"]), "expected diffs", len(body),
      collections.Counter("absent" if s is None else "stale" for _, s, _ in rows))
