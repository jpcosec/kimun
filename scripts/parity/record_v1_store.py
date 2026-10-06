"""Record what sldb v1 itself says about every document of a v1 store.

For each entry under `.sldb/core/documents/<Model>/` writes one JSON file
`<out>/<Model>/<entry>.json` with the entry's `name`, `path`, stored
`source_hash`, `content_hash` and `semantic_tags`, plus what v1's own API
computes now from the file on disk: `payload` (`validation.extract_model_data`,
JSON-normalised as scripts/parity/record_engine.py does: `default=str`, key
order kept), `content_hash_live` (`sldb.store.hashing.hash_payload`) and
`source_hash_live` (`hash_text`), or `error` when v1 does not extract it.

v1 is pointed at the originals read-only: nothing but imports and file reads,
bytecode writing disabled. The output goes OUTSIDE the repositories by default.

Usage: python3 scripts/parity/record_v1_store.py <v1-project> [--out DIR] [--only MODEL]
"""
import sys

sys.dont_write_bytecode = True

import argparse, importlib, json, os  # noqa: E402

ap = argparse.ArgumentParser()
ap.add_argument("v1")
ap.add_argument("--out", default="/tmp/kimun-parity/v1-store")
ap.add_argument("--only")
a = ap.parse_args()
v1, out = os.path.abspath(a.v1), os.path.abspath(a.out)
if os.path.commonpath([v1, out]) == v1:
    sys.exit("refusing to write inside the v1 project")
sys.path[:0] = [os.path.join(v1, "src"), v1]
os.chdir(v1)

import yaml  # noqa: E402
from sldb.runtime import validation  # noqa: E402
from sldb.store.hashing import hash_payload, hash_text  # noqa: E402

index = yaml.safe_load(open(".sldb/core/store_index.yaml", encoding="utf-8"))
refs = {m["name"]: m["model_ref"] for m in index["models"]}
root = ".sldb/core/documents"
counts = {"recorded": 0, "error": 0}
for model in sorted(os.listdir(root)):
    if a.only and model != a.only:
        continue
    cls, cls_error = None, None
    try:
        mod, _, name = refs[model].partition(":")
        cls = getattr(importlib.import_module(mod), name)
    except Exception as e:  # the class is gone or the model is not in the index
        cls_error = {"type": type(e).__name__, "message": str(e)[:300]}
    os.makedirs(os.path.join(out, model), exist_ok=True)
    for shard in sorted(os.listdir(os.path.join(root, model))):
        entry = yaml.safe_load(open(os.path.join(root, model, shard), encoding="utf-8"))
        rec = {"name": entry.get("name"), "model": model, "path": entry.get("path"),
               "source_hash": entry.get("source_hash"), "content_hash": entry.get("content_hash"),
               "semantic_tags": entry.get("semantic_tags") or []}
        try:
            if cls is None:
                raise RuntimeError("model class not importable: %s" % cls_error)
            text = open(entry["path"], encoding="utf-8").read()
            rec["source_hash_live"] = hash_text(text)
            payload = validation.extract_model_data(cls, text)
            payload = payload.model_dump(mode="json") if hasattr(payload, "model_dump") else payload
            rec["content_hash_live"] = hash_payload(payload)
            rec["payload"] = json.loads(json.dumps(payload, default=str))
            counts["recorded"] += 1
        except Exception as e:
            rec["error"] = {"type": type(e).__name__, "message": str(e)[:300]}
            counts["error"] += 1
        with open(os.path.join(out, model, os.path.splitext(shard)[0] + ".json"), "w", encoding="utf-8") as f:
            json.dump(rec, f, ensure_ascii=False, indent=1, default=str)
            f.write("\n")
print("recorded %(recorded)d documents, %(error)d that v1 itself does not extract" % counts, "->", out)
