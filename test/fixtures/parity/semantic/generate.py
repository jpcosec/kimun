"""Freezes the differential fixtures of the semantic layer from a v1 store.

Usage (from the v1 repo root, read-only):
  PYTHONPATH=<v1>/src:<v1>:<kimun>/python python3 -B generate.py <v1-root> <out-dir>

Writes `models.edn` (one descriptor per line: the `kimun.models` export plus
`:semantics`, the class's OWN `__semantics__`, which the exporter does not emit
yet) and `documents.json` (per document: name, model, path, the payload fields
`tags-of` reads — `tags` and `semantic_tags`, as v1 extracted them — and the
`semantic_tags` v1 recorded in the document shard).
"""
import importlib, json, sys
from pathlib import Path
import yaml
from kimun.models import Kw, descriptor, edn
from sldb.store.codec import default_codec

PER_MODEL = 5
ROOT = "StructuredNLDoc"

def main(v1: Path, out: Path) -> None:
    index = yaml.safe_load((v1 / ".sldb/core/store_index.yaml").read_text(encoding="utf-8"))
    models, docs, seen = [], [], set()
    def add_model(cls):
        if cls.__name__ in seen:
            return
        seen.add(cls.__name__)
        d = descriptor(cls)
        d[Kw("semantics")] = dict(cls.__dict__.get("__semantics__", {}) or {})
        models.append(edn(d))
    for entry in sorted(index["models"], key=lambda m: m["name"]):
        shards = sorted((v1 / ".sldb/core/documents" / entry["name"]).glob("*.yaml"))
        if not shards:
            continue
        mod, _, name = entry["model_ref"].partition(":")
        cls = getattr(importlib.import_module(mod), name)
        for c in cls.__mro__:
            if hasattr(c, "model_fields") and c.__name__ not in (ROOT, "BaseModel"):
                add_model(c)
        for shard in shards[:PER_MODEL]:
            s = yaml.safe_load(shard.read_text(encoding="utf-8"))
            payload = default_codec.extract(cls, (v1 / s["path"]).read_text(encoding="utf-8"))
            payload = payload if isinstance(payload, dict) else payload.model_dump()
            docs.append({"name": s["name"], "model": entry["name"], "path": s["path"],
                         "payload": {k: payload[k] for k in ("semantic_tags", "tags") if k in payload},
                         "expected": s.get("semantic_tags") or []})
    (out / "models.edn").write_text("\n".join(models) + "\n", encoding="utf-8")
    (out / "documents.json").write_text(json.dumps(docs, indent=1, ensure_ascii=False, sort_keys=True) + "\n", encoding="utf-8")
    print(len(docs), "documents,", len({d["model"] for d in docs}), "models with documents,", len(models), "descriptors")

if __name__ == "__main__":
    main(Path(sys.argv[1]), Path(sys.argv[2]))
