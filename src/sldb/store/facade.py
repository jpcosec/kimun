"""
Store facade for decoupling CLI from low-level store operations.
"""
from pathlib import Path
from typing import Any
from sldb.store.io import load_store_index, load_models_index, load_documents_index
from sldb.cli.store_context import get_store_context
from sldb.core.exceptions import SLDBError

def get_tracked_docs(model_name: str, store_path: str | None = None) -> list[tuple[str, str]]:
    sp, root = get_store_context(store_path)
    m_entry = next((m for m in load_store_index(sp).models if m.name == model_name), None)
    if not m_entry:
        raise SLDBError(f"Model '{model_name}' not found.")
    d_idx = load_documents_index(root / load_models_index(root / m_entry.models_index).documents_index)
    return [
        (d.name, str(Path(d.path) if Path(d.path).is_absolute() else root / d.path))
        for d in d_idx.documents
    ]
