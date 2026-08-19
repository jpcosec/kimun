from typing import Any
from sldb.store.query import load_runtime_documents
from sldb.cli.store_context import get_store_context
from sldb.cli.model_utils import resolve_model_ref

def resolve_runtime_doc(store_arg: str | None, doc_ref: str, pythonpath: str | None) -> dict[str, Any]:
    store_path, root = get_store_context(store_arg)
    docs = load_runtime_documents(store_path, resolve_model_ref, pythonpath)
    normalized = doc_ref.strip("/")
    candidates = [doc for doc in docs if normalized in [doc.name, doc.path, f"{doc.model_name}/{doc.name}"]]
    return _build_doc_result(candidates, doc_ref, root)

def _build_doc_result(candidates, doc_ref, root):
    if not candidates: raise ValueError(f"Unknown document target: {doc_ref}")
    if len(candidates) > 1: raise ValueError(f"Ambiguous document target '{doc_ref}'. Use 'Model/DocName' or a tracked path.")
    doc = candidates[0]
    template: str | None = getattr(doc.model_type, "__template__", None)
    return {
        "store": doc.store_name, "model": doc.model_name, "name": doc.name, "path": doc.path,
        "absolute_path": str((root / doc.path).resolve()), "payload": doc.payload,
        "semantic_tags": doc.semantic_tags, "template": template,
    }
