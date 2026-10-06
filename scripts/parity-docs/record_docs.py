"""Record real documents of sldb v1's own store as a differential fixture.

For a handful of models of the `.sldb` store of the v1 repo, takes up to N of
their tracked Markdown files and writes, per document, the kimun descriptor of
its model class, the Markdown as it is on disk and the payload v1 extracts from
it: test/fixtures/parity/docs/<Model>--<doc>/{descriptor.edn,doc.md,payload.json}.
Usage: python3 scripts/parity-docs/record_docs.py <v1-repo> <out-dir> [per-model]
"""
import importlib, json, os, sys

v1, out = sys.argv[1], sys.argv[2]
per_model = int(sys.argv[3]) if len(sys.argv) > 3 else 5
here = os.path.dirname(os.path.abspath(__file__))
sys.path[:0] = [os.path.join(v1, "src"), v1, os.path.join(here, "..", "..", "python")]
os.chdir(v1)
import yaml  # noqa: E402
from kimun import models as km  # noqa: E402
from sldb.runtime import validation  # noqa: E402

index = yaml.safe_load(open(".sldb/core/store_index.yaml"))
done = skipped = 0
for m in index["models"]:
    mod, _, cls_name = m["model_ref"].partition(":")
    try:
        cls = getattr(importlib.import_module(mod), cls_name)
    except Exception:
        continue
    template = getattr(cls, "__template__", "")
    if "⸢py•" in template or "{{" in template:
        continue
    shards = os.path.join(".sldb/core/documents", m["name"])
    if not os.path.isdir(shards):
        continue
    taken = 0
    for shard in sorted(os.listdir(shards)):
        if taken >= per_model:
            break
        entry = yaml.safe_load(open(os.path.join(shards, shard)))
        if not os.path.isfile(entry["path"]):
            continue
        text = open(entry["path"], encoding="utf-8").read()
        try:
            data = validation.extract_model_data(cls, text)
            payload = data.model_dump(mode="json") if hasattr(data, "model_dump") else data
            payload = json.loads(json.dumps(payload, default=str))
        except Exception:
            skipped += 1
            continue
        d = os.path.join(out, f"{m['name']}--{entry['name']}"[:120])
        os.makedirs(d, exist_ok=True)
        open(os.path.join(d, "descriptor.edn"), "w", encoding="utf-8").write(km.edn(km.descriptor(cls)))
        open(os.path.join(d, "doc.md"), "w", encoding="utf-8").write(text)
        json.dump(payload, open(os.path.join(d, "payload.json"), "w", encoding="utf-8"), ensure_ascii=False, sort_keys=True, indent=1)
        taken += 1
        done += 1
print(f"recorded {done} documents, skipped {skipped} that v1 itself does not extract")
