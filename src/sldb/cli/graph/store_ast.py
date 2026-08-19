from typing import Any
from sldb.cli.store_context import get_store_context
from sldb.cli.model_utils import resolve_model_ref
from sldb.store.io import load_documents_index, load_models_index, load_store_index
from sldb.store.query import load_runtime_documents
from .utils import _annotation_name

def _build_model_entry(entry: Any, root: Any, pythonpath: str | None) -> dict[str, Any]:
    model_type, models_idx = resolve_model_ref(entry.model_ref, pythonpath), load_models_index(root / entry.models_index)
    return {"name": entry.name, "model_ref": entry.model_ref, "path": entry.path, "version": getattr(models_idx, "version", 1), "canonical": getattr(models_idx, "canonical", False), "family": getattr(models_idx, "family", None), "semantics": list(getattr(models_idx, "semantics", [])), "base_models": list(getattr(models_idx, "base_models", [])), "fields": [{"name": k, "annotation": _annotation_name(f.annotation), "description": f.description or ""} for k, f in model_type.model_fields.items()], "documents": [{"name": d.name, "path": d.path, "semantic_tags": list(d.semantic_tags)} for d in sorted(load_documents_index(root / models_idx.documents_index).documents, key=lambda i: i.name)]}

def _get_linked_docs(store_path: Any, pythonpath: str | None) -> list[dict[str, Any]]:
    return [{"store": d.store_name, "model": d.model_name, "doc": d.name, "path": d.path, "semantic_tags": d.semantic_tags} for d in load_runtime_documents(store_path, resolve_model_ref, pythonpath, include_linked=True) if d.store_name != "local"]

def build_store_ast(store_arg: str | None, pythonpath: str | None, include_linked: bool = False) -> dict[str, Any]:
    store_path, root = get_store_context(store_arg)
    store_index = load_store_index(store_path)
    ast = {"store": {"path": str(store_path), "root": str(root), "linked_stores": [{"name": e.name, "path": e.path} for e in sorted(store_index.stores, key=lambda i: i.name)], "models": [_build_model_entry(e, root, pythonpath) for e in sorted(store_index.models, key=lambda i: i.name)]}}
    if include_linked: ast["linked_documents"] = _get_linked_docs(store_path, pythonpath)
    return ast
