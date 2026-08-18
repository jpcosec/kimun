"""
Store facade for decoupling CLI from low-level store operations.
"""
from pathlib import Path
from typing import Any
from sldb.store.io import load_store_index, load_models_index, load_documents_index
from sldb.store.layout import get_store_context
from sldb.core.exceptions import SLDBError

def get_tracked_docs(model_name: str, store_path: str | None = None) -> list[tuple[str, str]]:
    sp, root = get_store_context(store_path)
    idx = load_store_index(sp)
    m_entry = next((m for m in idx.models if m.name == model_name), None)
    if not m_entry:
        raise SLDBError(f"Model '{model_name}' not found.")
    m_idx = load_models_index(root / m_entry.models_index)
    d_idx = load_documents_index(root / m_idx.documents_index)
    docs = []
    for doc in d_idx.documents:
        doc_path = Path(doc.path)
        if not doc_path.is_absolute():
            doc_path = root / doc_path
        docs.append((doc.name, str(doc_path)))
    return docs
