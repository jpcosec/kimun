from __future__ import annotations
from pathlib import Path
from typing import Any
import yaml
from sldb.cli.store_context import get_store_context
from sldb.runtime.validation import render_model_markdown, validate_model_input_roundtrip
from sldb.core.exceptions import SLDBValidationError, SLDBASTError, SLDBError

def _is_path(path: Path) -> bool:
    try:
        return path.exists()
    except OSError:
        return False

def parse_yaml_string(payload: str) -> dict[str, Any]:
    try:
        data = yaml.safe_load(payload)
        if not isinstance(data, dict):
            raise SLDBASTError("Payload must be object.")
        return data
    except yaml.YAMLError as e:
        raise SLDBASTError(f"Parse error: {e}")

def parse_payload(payload_arg: str) -> dict[str, Any]:
    p = Path(payload_arg)
    if _is_path(p):
        return yaml.safe_load(p.read_text(encoding="utf-8"))
    return parse_yaml_string(payload_arg)

def validate_and_render(model_type: Any, data: dict[str, Any], fail_msg: str = "Idempotency fail") -> str:
    rendered = render_model_markdown(model_type, data)
    valid, details = validate_model_input_roundtrip(model_type, rendered)
    if not valid:
        raise SLDBValidationError(fail_msg, details)
    return rendered

def get_store_and_index(args: Any) -> tuple[Any, Any, Any]:
    from sldb.store.io import load_store_index
    sp, root = get_store_context(args.store)
    return sp, root, load_store_index(sp)

def find_doc_in_store(root: Path, idx: Any, doc_ref: str) -> tuple[Any, Any, Any, Any]:
    import sldb.store.io as sio
    from sldb.store.section_rebuild import rebuild_sections_indexes
    for m_entry in idx.models:
        m_idx = sio.load_models_index(root / m_entry.models_index)
        d_idx = sio.load_documents_index(root / m_idx.documents_index)
        doc = next((d for d in d_idx.documents if d.name == doc_ref or d.path == doc_ref), None)
        if doc:
            return (m_entry, m_idx, d_idx, doc)
    raise SLDBError(f"Doc '{doc_ref}' not found.")

def resolve_doc_path(raw_path: str, root: Path) -> Path:
    path = Path(raw_path)
    return path.resolve() if path.is_absolute() else (root / path).resolve()

from sldb.cli.model_utils import resolve_model_ref

def write_and_track(args: Any, sp: Path, root: Path, idx: Any, model_type: Any, entry: Any, rendered: str) -> None:
    from sldb.store.ops import track_document
    out = resolve_doc_path(args.output, root)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(rendered + "\n", encoding="utf-8")
    track_document(sp, root, idx, model_type, entry, out, args.name or out.stem, resolve_model_ref, args.pythonpath)
    print(f"Created and tracked '{args.name or out.stem}'")

def validate_path(args: Any, model_type: Any, path: Path) -> None:
    if args.force:
        return
    valid, details = validate_model_input_roundtrip(model_type, path.read_text(encoding="utf-8"))
    if not valid:
        raise SLDBValidationError("Idempotency fail", details)

def write_doc_and_update_hashes(root: Path, doc: Any, model_type: Any, rendered: str) -> None:
    from sldb.store.hashing import hash_text, hash_fields
    doc_path = root / doc.path
    doc_path.write_text(rendered + "\n", encoding="utf-8")
    doc.hash_c = hash_text(rendered + "\n")
    doc.hash_d = hash_fields(model_type, rendered + "\n")

def save_update_indexes(sp: Path, root: Path, idx: Any, m_entry: Any, m_idx: Any, d_idx: Any, pythonpath: str) -> None:
    import sldb.store.io as sio, sldb.store.semantic as ssem, sldb.store.ops as so
    from sldb.store.section_rebuild import rebuild_sections_indexes
    with sio.store_lock(sp):
        sio.save_documents_index(root / m_idx.documents_index, d_idx)
        m_idx.hash_b = ""
        sio.save_models_index(root / m_entry.models_index, m_idx)
        ssem.rebuild_semantic_indexes(sp, root, resolve_model_ref, pythonpath)
        so.cascade_hash_a(sp, root, idx)

def save_untrack_indexes(sp: Path, root: Path, idx: Any, m_entry: Any, m_idx: Any, d_idx: Any, pythonpath: str) -> None:
    import sldb.store.io as sio, sldb.store.hashing as sh, sldb.store.ops as so, sldb.store.semantic as ssem
    from sldb.store.section_rebuild import rebuild_sections_indexes
    with sio.store_lock(sp):
        sio.save_documents_index(root / m_idx.documents_index, d_idx)
        m_idx.hash_b = sh.hash_documents_index(d_idx)
        sio.save_models_index(root / m_entry.models_index, m_idx)
        ssem.rebuild_semantic_indexes(sp, root, resolve_model_ref, pythonpath)
        rebuild_sections_indexes(sp, root, resolve_model_ref, pythonpath)
        so.cascade_hash_a(sp, root, idx)
