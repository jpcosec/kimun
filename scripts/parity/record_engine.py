"""pytest plugin: record every extract/render call the sldb v1 suite makes.

Loaded with `-p record_engine` (see record_engine.sh). Wraps `Validator.extract` and
`Validator.render` of v1 and writes one gzipped JSON line per distinct case to
`$KIMUN_PARITY_OUT.cases.jsonl.gz` (e.g. test/fixtures/parity/engine.cases.jsonl.gz).
A record is `{"id": "<hash>", …}`, one field per file the case directory used to hold
(descriptor.edn, input.md, payload.json, rendered.md, error.json, meta.edn), each the
byte-exact text of that file; a file the case did not have is absent. The same format is
what scripts/parity/pack_corpus.clj writes when packing an older directory corpus. A render
of a model with `__compositions__` also carries what the composition read: `refs`
(`{ref: markdown}` for every ref that resolved) and `ref-models` (`{model-ref: descriptor}`).
Neither enters the case hash, so recording them leaves every case id unchanged.
"""
from __future__ import annotations

import gzip
import hashlib
import json
import os
import re
from pathlib import Path

from kimun.models import Kw, descriptor, edn

OUT = Path(os.environ["KIMUN_PARITY_OUT"])
_CASES: dict[str, dict] = {}
_CURRENT = {"id": "<setup>"}
_RETIRED = re.compile(r"⸢py•|\{\{")


def pack(out, fields_by_id):
    """Write a packed corpus: one gzipped JSON line per case, `id` first, other
    keys in the order each caller gives. Returns the path written."""
    path = Path(str(out) + ".cases.jsonl.gz")
    path.parent.mkdir(parents=True, exist_ok=True)
    data = "".join(json.dumps(f, ensure_ascii=False) + "\n" for f in fields_by_id).encode("utf-8")
    path.write_bytes(gzip.compress(data, mtime=0))  # mtime pinned so re-recording is byte-stable
    return path


def _norm(payload):
    return json.dumps(payload, sort_keys=True, ensure_ascii=False, indent=1, default=str)


def _composition_context(cls, data, ref_root):
    """What v1's `compose_field` reads for this render: resolved ref texts and ref models."""
    comps = getattr(cls, "__compositions__", None) or {}
    if not comps:
        return None
    from sldb.models.structured_doc import StructuredNLDoc

    refs, models = {}, {}
    for spec in comps.values():
        ref = spec.get("model")
        try:
            mt = StructuredNLDoc._resolve_composition_model(None, ref)
            if mt is not None and isinstance(ref, str):
                models[ref] = descriptor(mt)
        except Exception:
            pass
        raw = data.get(spec.get("source_field") or "") if isinstance(data, dict) else None
        for r in raw if isinstance(raw, list) else []:
            try:
                path = StructuredNLDoc._resolve_ref(r, ref_root)
                if path.exists():
                    refs[str(r).strip().strip("`")] = path.read_text(encoding="utf-8")
            except Exception:
                pass
    return {"refs": refs, "models": models}


def _record(op, cls, desc_edn, text_in, payload, text_out, error, ctx=None):
    key = hashlib.sha256("\x00".join([op, desc_edn, text_in or "", _norm(payload) if payload is not None else "",
                                      text_out or "", json.dumps(error or {})]).encode()).hexdigest()[:16]
    case = _CASES.setdefault(key, {"op": op, "descriptor": desc_edn, "input": text_in, "payload": payload,
                                   "rendered": text_out, "error": error, "tests": [],
                                   "template": getattr(cls, "__template__", "") or "", "ctx": ctx})
    if _CURRENT["id"] not in case["tests"]:
        case["tests"].append(_CURRENT["id"])


def _wrap():
    from sldb.runtime import validation

    V = validation.Validator
    orig_extract, orig_render = V.extract, V.render

    def extract(self, markdown):
        try:
            desc = edn(descriptor(self.model_type))
        except Exception:
            return orig_extract(self, markdown)
        try:
            out = orig_extract(self, markdown)
        except Exception as e:
            _record("extract", self.model_type, desc, markdown, None, None,
                    {"type": type(e).__name__, "message": str(e)[:500]})
            raise
        _record("extract", self.model_type, desc, markdown, out, None, None)
        return out

    def render(self, data, ref_root=None):
        try:
            desc = edn(descriptor(self.model_type))
        except Exception:
            return orig_render(self, data, ref_root)
        ctx = _composition_context(self.model_type, data, ref_root)
        try:
            out = orig_render(self, data, ref_root)
        except Exception as e:
            _record("render", self.model_type, desc, None, json.loads(json.dumps(data, default=str)), None,
                    {"type": type(e).__name__, "message": str(e)[:500]}, ctx)
            raise
        _record("render", self.model_type, desc, None, json.loads(json.dumps(data, default=str)), out, None, ctx)
        return out

    V.extract, V.render = extract, render


def pytest_configure(config):
    _wrap()


def pytest_runtest_setup(item):
    _CURRENT["id"] = item.nodeid


def pytest_sessionfinish(session, exitstatus):
    def fields(key, c):
        f = {"id": key, "descriptor": c["descriptor"] + "\n"}
        if c["input"] is not None:
            f["input"] = c["input"]
        if c["payload"] is not None:
            # insertion order is kept on purpose: v1 dumps mappings in payload order
            f["payload"] = json.dumps(c["payload"], ensure_ascii=False, indent=1, default=str) + "\n"
        if c["rendered"] is not None:
            f["rendered"] = c["rendered"]
        if c["error"] is not None:
            f["error"] = json.dumps(c["error"], ensure_ascii=False) + "\n"
        if c.get("ctx"):
            f["refs"] = json.dumps(c["ctx"]["refs"], ensure_ascii=False, indent=1, sort_keys=True) + "\n"
            f["ref-models"] = edn(c["ctx"]["models"]) + "\n"
        meta = {Kw("op"): Kw(c["op"]), Kw("tests"): sorted(c["tests"])}
        if _RETIRED.search(c["template"]):
            meta[Kw("retired")] = "07 §8"
        f["meta"] = edn(meta) + "\n"
        return f

    path = pack(OUT, [fields(k, c) for k, c in sorted(_CASES.items())])
    print(f"\nrecord_engine: {len(_CASES)} cases written to {path}")
