"""Freezes the differential fixture of the edge index from a v1 store (read-only).

Usage (read-only on the v1 repo; v1 is only parsed, never executed against its .sldb):
  cd <v1-root> && PYTHONPATH=<v1>/src:<v1>:<kimun>/python python3 -B generate.py <v1-root> <out-dir>

Writes, in <out-dir>:
  models.edn      one descriptor per line: the `kimun.models` export plus `:semantics` (the class's
                  OWN `__semantics__`), for every chosen document's model, its MRO and the
                  registered models that carry `extends` edges (RitualDoc and its bases).
  documents.json  per chosen document: name, model, path (store-relative), text (the full source),
                  payload (what the codec extracts, JSON-safe) and semantic_tags (as v1 recorded).
  edges.json      {index_version, documents, relation_counts, nodes, edges, predicates}:
                  the edges v1's index holds for those documents, their models, the store node and
                  the tag DAG, read straight from `.sldb/runtime/edges/**` and `edges.yaml`
                  (never through v1's API, which writes indexes on read).
                  `composes` shard targets are `sldb://unresolved_path/<ref>`; v1 resolves them
                  when composing the index, so the record carries `resolved_target` (owner document
                  node id, or null = the edge is dropped from the composed index).

A document whose prose held an absolute path into the v1 checkout has it replaced by
<repo> in `text` and `payload` (`scrubbed_abs_path: true`); it sat in a code fence, so sections, tags
and edges are unaffected (v1's recorded hashes in node semantics no longer match such texts).

Deterministic: sorted selection, sorted keys, sorted edges. No absolute paths are written
(the model nodes' `path` semantic, an absolute path to the Python source, is removed).
"""
import importlib, json, sys
from pathlib import Path
from posixpath import normpath
import yaml
from kimun.models import Kw, descriptor, edn
from sldb.store.codec import default_codec

ROOT = "StructuredNLDoc"
DEFAULT_K = 1
PER_MODEL = {"BoardDoc": 99, "TaskDoc": 99, "AtomDoc": 2, "ChecklistDoc": 2, "EdgeDoc": 2, "ConditionDoc": 2,
             "RoutineDoc": 2, "PythonSymbolDoc": 2, "MoveDoc": 2}
EXPLICIT = {"RelationTypeDoc": ["reltype-extends", "reltype-composes", "reltype-applies_to_source", "references"]}
EXTRA_MODELS = ["RitualDoc"]  # no documents, but carries the only real `extends` edges
STORE = "sldb://store"
ABS = "/" + "home/jp/proyectos/hum-ecosystem"  # an absolute path inside a code fence of one document's prose
PLACEHOLDER = "<repo>"


def load(p):
    return yaml.load(p.read_text(encoding="utf-8"), Loader=yaml.CSafeLoader)


def norm(path):
    return normpath(path.strip().strip("`").replace("\\", "/")).lstrip("/")


def main(v1: Path, out: Path) -> None:
    rt = v1 / ".sldb/runtime"
    index = yaml.safe_load((v1 / ".sldb/core/store_index.yaml").read_text(encoding="utf-8"))
    entries = {m["name"]: m for m in index["models"]}
    cls_of = lambda name: getattr(importlib.import_module(entries[name]["model_ref"].partition(":")[0]),
                                  entries[name]["model_ref"].partition(":")[2])
    # path -> owner node id over the WHOLE store (what composition resolution sees)
    owners = {}
    for mdir in sorted((v1 / ".sldb/core/documents").iterdir()):
        for sh in sorted(mdir.glob("*.yaml")):
            s = yaml.safe_load(sh.read_text(encoding="utf-8"))
            owners.setdefault(norm(s["path"]), f"sldb://document/{mdir.name}:{s['name']}")
    # ---- selection
    chosen = []  # (model, shard dict)
    for name in sorted(entries):
        shards = sorted((v1 / ".sldb/core/documents" / name).glob("*.yaml"))
        if not shards:
            continue
        if name in EXPLICIT:
            pick = [p for p in shards if p.stem in EXPLICIT[name]]
        else:
            pick = shards[:PER_MODEL.get(name, DEFAULT_K)]
        chosen += [(name, yaml.safe_load(p.read_text(encoding="utf-8")), p.stem) for p in pick]
    # ---- models.edn
    models, seen = [], set()
    def add_model(cls):
        if cls.__name__ in seen:
            return
        seen.add(cls.__name__)
        d = descriptor(cls)
        d[Kw("semantics")] = dict(cls.__dict__.get("__semantics__", {}) or {})
        models.append(edn(d))
    for name in sorted({c[0] for c in chosen} | set(EXTRA_MODELS)):
        for c in cls_of(name).__mro__:
            if hasattr(c, "model_fields") and c.__name__ not in (ROOT, "BaseModel"):
                add_model(c)
    model_names = sorted({c[0] for c in chosen} | set(EXTRA_MODELS))
    # ---- documents.json
    docs, doc_ids = [], []
    for name, s, stem in chosen:
        text = (v1 / s["path"]).read_text(encoding="utf-8")
        assert not s["path"].startswith("/"), s["path"]
        payload = default_codec.extract(cls_of(name), text)
        payload = payload if isinstance(payload, dict) else payload.model_dump()
        payload = json.dumps(payload, default=str, ensure_ascii=False)
        scrubbed = ABS in text or ABS in payload
        text, payload = text.replace(ABS, PLACEHOLDER), json.loads(payload.replace(ABS, PLACEHOLDER))
        docs.append({"name": s["name"], "model": name, "path": s["path"], "text": text, "payload": payload,
                     "semantic_tags": sorted(set(s.get("semantic_tags") or [])), "scrubbed_abs_path": scrubbed})
        doc_ids.append(f"{name}:{s['name']}")
    docs.sort(key=lambda d: (d["model"], d["name"]))
    doc_ids.sort()
    fixture_doc_nodes = {f"sldb://document/{i}" for i in doc_ids}
    # ---- edges
    nodes, edges = {}, []
    def take_nodes(d):
        for n in d["nodes"]:
            if n["node_type"] == "sldb_model":
                n["semantics"].pop("path", None)
            nodes.setdefault(n["id"], {"id": n["id"], "type": n["node_type"], "facets": n["facets"], "semantics": n["semantics"]})
    def take_edges(d, scope, by):
        for e in d["edges"]:
            rec = {"scope": scope, "relation": e["relation"], "source": e["source"], "target": e["target"],
                   "metadata": e["metadata"], "contributed_by": by}
            if e["relation"] == "composes":
                rec["resolved_target"] = owners.get(norm(e["metadata"].get("ref_path", "")))
            edges.append(rec)
    for name, s, stem in chosen:
        d = load(rt / "edges" / name / f"{stem}.yaml")
        take_nodes(d); take_edges(d, "document", f"{name}:{s['name']}")
    for name in model_names:
        d = load(rt / "edges" / f"{name}.yaml")
        take_nodes(d); take_edges(d, "model", f"model:{name}")
    top = load(rt / "edges.yaml")
    take_nodes(top)
    for e in top["edges"]:
        if e["relation"] == "has_model" and e["target"] == f"sldb://model/{e['target'].rsplit('/',1)[1]}" \
                and e["target"].rsplit("/", 1)[1] in model_names:
            edges.append({"scope": "store", "relation": "has_model", "source": e["source"], "target": e["target"],
                          "metadata": e["metadata"], "contributed_by": "store"})
    # semantic DAG: parent edges whose child tag is used by anything in the fixture, plus ancestors
    used = {n["id"] for n in nodes.values() if n["type"] == "semantic_tag"}
    sem = [e for e in top["edges"] if e["relation"] == "semantic_parent"]
    changed = True
    keep = []
    while changed:
        changed = False
        for e in sem:
            if e["source"] in used and e["target"] not in used:
                used.add(e["target"]); changed = True
    for e in sem:
        if e["source"] in used:
            edges.append({"scope": "semantic", "relation": "semantic_parent", "source": e["source"], "target": e["target"],
                          "metadata": e["metadata"], "contributed_by": "semantic_dag"})
    take_nodes({"nodes": [n for n in top["nodes"] if n["id"] in used or n["id"] == STORE]})
    # endpoints flags
    all_v1 = {n["id"] for n in top["nodes"]} | set(owners.values())
    for f in sorted((rt / "edges").glob("*.yaml")):
        all_v1 |= {n["id"] for n in load(f)["nodes"]}
    for e in edges:
        ends = [e["source"], e["target"]]
        if e.get("resolved_target"):
            ends[1] = e["resolved_target"]
        e["target_in_fixture"] = (ends[1] in nodes or ends[1] in fixture_doc_nodes) and not ends[1].startswith("sldb://unresolved_path/")
        e["source_in_fixture"] = ends[0] in nodes
        e["target_in_v1_index"] = ends[1] in all_v1 or ends[1] in nodes
    edges.sort(key=lambda e: json.dumps([e["scope"], e["relation"], e["source"], e["target"], e["metadata"]], sort_keys=True))
    counts = {}
    for e in edges:
        counts.setdefault(e["scope"], {}).setdefault(e["relation"], 0)
        counts[e["scope"]][e["relation"]] += 1
    wanted = {r["name"] for r in index.get("predicates", [])}
    edges_doc = {"index_version": top["edges_version"], "documents": doc_ids, "relation_counts": counts,
                 "nodes": sorted(nodes.values(), key=lambda n: n["id"]), "edges": edges,
                 "predicates": sorted(index.get("predicates", []), key=lambda p: p["name"])}
    dump = lambda o: json.dumps(o, indent=1, ensure_ascii=False, sort_keys=True) + "\n"
    (out / "models.edn").write_text("\n".join(models) + "\n", encoding="utf-8")
    (out / "documents.json").write_text(dump(docs), encoding="utf-8")
    (out / "edges.json").write_text(dump(edges_doc), encoding="utf-8")
    print(len(docs), "documents,", len({d["model"] for d in docs}), "models,", len(models), "descriptors,",
          len(edges), "edges,", len(nodes), "nodes")


if __name__ == "__main__":
    main(Path(sys.argv[1]), Path(sys.argv[2]))
