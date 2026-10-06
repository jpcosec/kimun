"""pytest plugin: record every segment / field-ownership call the sldb v1 suite makes.

Loaded with `-p record_segments` (see record_segments.sh). Nothing is written into the v1 repo.
One directory per distinct case under $KIMUN_PARITY_OUT (test/fixtures/parity/segments/<hash>/).

Entry points (`:op` in meta.edn, `:fn` is the fully-qualified v1 function):

  :render_with_spans           sldb.core.segments.instrumented_render.render_with_spans
                               (model_type, payload, ref_root=None) -> (text, [(meta, start, end)])
                               The store-free core of GET /document/segments: build_document_segments
                               (store_path, pythonpath, doc) only looks the document up in a store and
                               hands its model + payload to this function, so it is not a case of its
                               own. Every time the suite calls it, the recorder checks that its `text`
                               and `segments` equal the ones recorded here (`doc_mismatch` in the
                               summary line must be 0).
  :build_extraction_segments   sldb.api.segments.build_extraction_segments.build_extraction_segments
                               (model_type, markdown, ref_root=None) -> ExtractionSegments
                               The store-free core of POST /extract/segments.
  :map_fields_to_sections      sldb.api.records.map_fields._map_fields_to_sections
                               (template, sections, known_fields=None) -> {field: section path}
  :extract_sections            sldb.api.records.extract.extract_sections
                               (markdown) -> [SectionRecord]   (produces the `sections` argument above)

Files of a case directory (JSON is UTF-8, cheshire-readable; EDN is clojure.edn-readable):

  meta.edn        {:op :<entry point> :fn "<v1 function>" :tests ["<pytest node id>" …]}
                  plus :retired "07 §8" when the template uses a construct retired in kimun
                  (`⸢py•` marker or `{{ }}` Jinja). Never part of the hash.
  descriptor.edn  the model descriptor (kimun.models.descriptor). Absent for the two section ops,
                  which take text, not a model class.
  payload.json    input payload, in v1's insertion order (render_with_spans).
  input.md        input markdown, byte-exact, no newline added (build_extraction_segments,
                  extract_sections).
  template.md     input template, byte-exact (map_fields_to_sections).
  args.json       every other argument, always present:
                    render_with_spans / build_extraction_segments  {"ref_root": bool}  (whether a
                        ref_root was passed; the path itself is a tmp dir and is not recorded -
                        what it resolved to is in refs.json. Always false for a model without
                        `__compositions__`, where the argument is inert)
                    map_fields_to_sections   {"sections": [{title slug level path line_start line_end …}],
                                              "known_fields": [sorted names] | null}
                    extract_sections         {}
  refs.json       {ref: markdown} and
  ref-models.edn  {model-ref: descriptor}: only when the model has `__compositions__`; what v1's
                  `compose_field` read (same convention as the engine corpus).
  output.json     the full return value. Pydantic models are `model_dump(mode="json")` (v1's own field
                  names, nulls kept, declaration order), tuples become lists:
                    render_with_spans          {"text": str,
                                                "spans": [{"meta": {"path" "marker" "traits" "_order" …},
                                                           "start": int, "end": int}],
                                                "segments": [Segment …]}
                        `text` and `spans` are the function's own return value. `segments` is derived
                        by this recorder with v1's own code - exactly the body of
                        build_document_segments after the store lookup: build_node over every span,
                        build_segment_tree, then the DocumentSegments boundary validation - so it is
                        what /document/segments answers in `segments` for that model + payload.
                    build_extraction_segments  ExtractionSegments {model text payload canonical_text
                                                fields unmatched coverage}
                    map_fields_to_sections     {field: section path}
                    extract_sections           [{title slug level path line_start line_end}]
  error.json      {"type": "<exception class>", "message": "<str(exc)>"} instead of output.json when
                  the call raised.

Not recorded as cases, because they only add a store lookup on top of a recorded function:
build_document_segments (see above) and sldb.api.segments.extract_markdown_segments.extract_markdown_segments
(store_path, pythonpath, model, markdown), which loads the registered model, calls
build_extraction_segments(model_type, markdown, store_path.parent) and re-raises any failure as
SLDBValidationError("The markdown does not extract as '<model>': <message on one line>"); error.json
holds the exception build_extraction_segments itself raised.

What the corpus depends on: the v1 checkout (tests/test_document_segments.py walks every document of
v1's own tracked `.sldb` store, which is where ~1550 of the render_with_spans cases come from; pytest
runs with the v1 repo as cwd, which is what relative composition refs resolve against when no
ref_root is passed) and the installed pydantic (its version is in the URL of a ValidationError
message). Nothing depends on the clock or on pytest's tmp dir.

Case id = sha256(op, descriptor EDN, input markdown/template, payload, args, refs, ref models)[:16],
all in insertion order. Outputs, test ids, the clock and ref_root paths are not hashed. If two calls
with the same id ever disagree on the outcome the second one is filed under a hash that also covers
its outcome and meta.edn gets `:collision "<first id>"`.
"""
from __future__ import annotations

import dataclasses
import hashlib
import json
import os
import sys
from pathlib import Path

from kimun.models import Kw, descriptor, edn
from record_engine import _RETIRED, _composition_context  # importing registers no hooks

OUT = Path(os.environ["KIMUN_PARITY_OUT"])
_CASES: dict[str, dict] = {}
_CURRENT = {"id": "<setup>"}
_PATCHES: list[tuple[str, object, object]] = []  # (attribute name, original, wrapper)
_DOC_STACK: list[list] = []
STATS = {"no_descriptor": 0, "collisions": 0, "doc_calls": 0, "doc_mismatch": 0}


def _dump(v):
    return json.dumps(v, ensure_ascii=False, indent=1, default=str)


def _plain(v):
    return json.loads(json.dumps(v, default=str))


def _err(e):
    return {"type": type(e).__name__, "message": str(e)}


def _section(s):
    if dataclasses.is_dataclass(s) and not isinstance(s, type):
        return dataclasses.asdict(s)
    if hasattr(s, "model_dump"):
        return s.model_dump(mode="json")
    return {k: getattr(s, k, None) for k in ("title", "slug", "level", "path", "line_start", "line_end")}


def _record(op, fn, *, desc=None, template="", input_md=None, template_md=None, payload=None, args=None,
            ctx=None, output=None, error=None):
    args = args or {}
    parts = [op, desc or "", input_md or "", template_md or "", _dump(payload) if payload is not None else "",
             _dump(args), _dump(ctx["refs"]) if ctx else "", edn(ctx["models"]) if ctx else ""]
    key = hashlib.sha256("\x00".join(parts).encode()).hexdigest()[:16]
    outcome = _dump({"output": output, "error": error})
    case = {"op": op, "fn": fn, "descriptor": desc, "input": input_md, "template_md": template_md, "payload": payload,
            "args": args, "ctx": ctx, "output": output, "error": error, "outcome": outcome, "tests": [],
            "retired": bool(_RETIRED.search(template or template_md or ""))}
    seen = _CASES.get(key)
    if seen is not None and seen["outcome"] != outcome:
        STATS["collisions"] += 1
        case["collision"] = key
        key = hashlib.sha256("\x00".join(parts + [outcome]).encode()).hexdigest()[:16]
    case = _CASES.setdefault(key, case)
    if _CURRENT["id"] not in case["tests"]:
        case["tests"].append(_CURRENT["id"])


def _describe(cls):
    try:
        return edn(descriptor(cls))
    except Exception:
        STATS["no_descriptor"] += 1
        return None


def _ref_root_arg(cls, ref_root):
    """Whether a ref_root was passed; always false for a model that composes nothing, where it is inert."""
    return ref_root is not None and bool(getattr(cls, "__compositions__", None))


def _boundary_segments(cls, payload, text, spans):
    """The body of v1's build_document_segments after its store lookup, with v1's own functions."""
    from sldb.api.segments.document_segments import DocumentSegments
    from sldb.api.segments.segment_nodes import build_node
    from sldb.core.segments.segment_tree import build_segment_tree

    nodes = [build_node(dict(meta), start, end, text, cls, payload) for meta, start, end in spans]
    doc = DocumentSegments(id="", model=cls.__name__, text=text, payload=dict(payload),
                           segments=build_segment_tree(nodes, text))
    return doc.model_dump(mode="json")["segments"]


def _wrappers():
    from importlib import import_module  # `from pkg import name` would get the re-exported function

    extract_mod = import_module("sldb.api.records.extract")
    map_mod = import_module("sldb.api.records.map_fields")
    doc_mod = import_module("sldb.api.segments.build_document_segments")
    ext_mod = import_module("sldb.api.segments.build_extraction_segments")
    render_mod = import_module("sldb.core.segments.instrumented_render")

    o_render, o_ext, o_doc = render_mod.render_with_spans, ext_mod.build_extraction_segments, doc_mod.build_document_segments
    o_map, o_sections = map_mod._map_fields_to_sections, extract_mod.extract_sections

    def render_with_spans(model_type, payload, ref_root=None):
        desc = _describe(model_type)
        if desc is None:
            if _DOC_STACK:
                _DOC_STACK[-1].append(None)
            return o_render(model_type, payload, ref_root)
        common = dict(desc=desc, template=getattr(model_type, "__template__", "") or "", payload=_plain(payload),
                      args={"ref_root": _ref_root_arg(model_type, ref_root)},
                      ctx=_composition_context(model_type, payload, ref_root))
        fn = "sldb.core.segments.instrumented_render.render_with_spans"
        try:
            text, spans = o_render(model_type, payload, ref_root)
        except Exception as e:
            _record("render_with_spans", fn, error=_err(e), **common)
            raise
        output = {"text": text, "spans": [{"meta": _plain(meta), "start": start, "end": end} for meta, start, end in spans]}
        try:
            output["segments"] = _boundary_segments(model_type, payload, text, spans)
        except Exception as e:  # never observed; kept visible instead of silently dropped
            output["segments_error"] = _err(e)
        _record("render_with_spans", fn, output=output, **common)
        if _DOC_STACK:
            _DOC_STACK[-1].append(output)
        return text, spans

    def build_extraction_segments(model_type, markdown, ref_root=None):
        desc = _describe(model_type)
        if desc is None:
            return o_ext(model_type, markdown, ref_root)
        fn = "sldb.api.segments.build_extraction_segments.build_extraction_segments"
        common = dict(desc=desc, template=getattr(model_type, "__template__", "") or "", input_md=markdown,
                      args={"ref_root": _ref_root_arg(model_type, ref_root)})
        try:
            out = o_ext(model_type, markdown, ref_root)
        except Exception as e:
            payload = None
            if getattr(model_type, "__compositions__", None):
                try:
                    from sldb.runtime.validation import extract_model_data
                    payload = extract_model_data(model_type, markdown)
                except Exception:
                    pass
            _record("build_extraction_segments", fn, error=_err(e),
                    ctx=_composition_context(model_type, payload, ref_root), **common)
            raise
        _record("build_extraction_segments", fn, output=out.model_dump(mode="json"),
                ctx=_composition_context(model_type, out.payload, ref_root), **common)
        return out

    def build_document_segments(store_path, pythonpath, doc):
        """Not a case of its own (it needs a store): checks that what it answers in `segments` and `text`
        is what the render_with_spans case under it recorded."""
        _DOC_STACK.append([])
        try:
            out = o_doc(store_path, pythonpath, doc)
        finally:
            calls = _DOC_STACK.pop()
        STATS["doc_calls"] += 1
        dumped = out.model_dump(mode="json")
        if len(calls) != 1 or calls[0] is None or (calls[0]["text"], calls[0].get("segments")) != (dumped["text"], dumped["segments"]):
            STATS["doc_mismatch"] += 1
        return out

    def _map_fields_to_sections(template, sections, known_fields=None):
        fn = "sldb.api.records.map_fields._map_fields_to_sections"
        common = dict(template_md=template, args={"sections": [_section(s) for s in sections],
                                                  "known_fields": sorted(known_fields) if known_fields is not None else None})
        try:
            out = o_map(template, sections, known_fields=known_fields)
        except Exception as e:
            _record("map_fields_to_sections", fn, error=_err(e), **common)
            raise
        _record("map_fields_to_sections", fn, output=dict(out), **common)
        return out

    def extract_sections(markdown):
        fn = "sldb.api.records.extract.extract_sections"
        try:
            out = o_sections(markdown)
        except Exception as e:
            _record("extract_sections", fn, input_md=markdown, error=_err(e))
            raise
        _record("extract_sections", fn, input_md=markdown, output=[_section(s) for s in out])
        return out

    for name, orig, wrapper in (("render_with_spans", o_render, render_with_spans),
                                ("build_extraction_segments", o_ext, build_extraction_segments),
                                ("build_document_segments", o_doc, build_document_segments),  # check only
                                ("_map_fields_to_sections", o_map, _map_fields_to_sections),
                                ("extract_sections", o_sections, extract_sections)):
        wrapper.__wrapped__ = orig
        _PATCHES.append((name, orig, wrapper))


def _sweep():
    """v1 imports these functions by name, so every module holding the original gets the wrapper."""
    for mod in list(sys.modules.values()):
        ns = getattr(mod, "__dict__", None)
        if not isinstance(ns, dict):
            continue
        for name, orig, wrapper in _PATCHES:
            if ns.get(name) is orig:
                ns[name] = wrapper


def pytest_configure(config):
    _wrappers()
    _sweep()


def pytest_runtest_setup(item):
    _CURRENT["id"] = item.nodeid
    _sweep()


def pytest_sessionfinish(session, exitstatus):
    OUT.mkdir(parents=True, exist_ok=True)
    for key, c in sorted(_CASES.items()):
        d = OUT / key
        d.mkdir(exist_ok=True)
        if c["descriptor"] is not None:
            (d / "descriptor.edn").write_text(c["descriptor"] + "\n", encoding="utf-8")
        if c["input"] is not None:
            (d / "input.md").write_text(c["input"], encoding="utf-8")
        if c["template_md"] is not None:
            (d / "template.md").write_text(c["template_md"], encoding="utf-8")
        if c["payload"] is not None:
            (d / "payload.json").write_text(_dump(c["payload"]) + "\n", encoding="utf-8")
        (d / "args.json").write_text(_dump(c["args"]) + "\n", encoding="utf-8")
        if c["ctx"]:
            (d / "refs.json").write_text(json.dumps(c["ctx"]["refs"], ensure_ascii=False, indent=1, sort_keys=True) + "\n", encoding="utf-8")
            (d / "ref-models.edn").write_text(edn(c["ctx"]["models"]) + "\n", encoding="utf-8")
        if c["error"] is not None:
            (d / "error.json").write_text(_dump(c["error"]) + "\n", encoding="utf-8")
        else:
            (d / "output.json").write_text(_dump(c["output"]) + "\n", encoding="utf-8")
        meta = {Kw("op"): Kw(c["op"]), Kw("fn"): c["fn"], Kw("tests"): sorted(c["tests"])}
        if c["retired"]:
            meta[Kw("retired")] = "07 §8"
        if c.get("collision"):
            meta[Kw("collision")] = c["collision"]
        (d / "meta.edn").write_text(edn(meta) + "\n", encoding="utf-8")
    print(f"\nrecord_segments: {len(_CASES)} cases this run; {STATS}")
