from __future__ import annotations

from pathlib import Path

from sldb.runtime.validation import extract_model_data
from sldb.store.layout import project_root, store_exists
from sldb.store.io import (
    load_documents_index,
    load_models_index,
    load_store_index,
)
from sldb.store.query_engine.models import RuntimeDocument


def _resolve_path(base: Path, maybe_relative: str) -> Path:
    """Resolves a potentially relative path against a base path."""
    path = Path(maybe_relative)
    return path if path.is_absolute() else (base / path).resolve()


def _load_doc(doc, root, model_type, m_entry, s_name, s_path) -> RuntimeDocument | None:
    d_path = root / doc.path
    if not d_path.exists():
        return None
    return RuntimeDocument(store_name=s_name, store_path=s_path, model_name=m_entry.name, model_type=model_type, name=doc.name, path=doc.path, payload=extract_model_data(model_type, d_path.read_text(encoding="utf-8")), semantic_tags=list(doc.semantic_tags))

def _load_one(s_path: Path, s_name: str, resolver, p_path) -> list[RuntimeDocument]:
    root = project_root(s_path)
    docs = []
    for m in load_store_index(s_path).models:
        m_type = resolver(m.model_ref, p_path)
        d_idx = load_documents_index(root / load_models_index(root / m.models_index).documents_index)
        docs.extend([d for doc in d_idx.documents if (d := _load_doc(doc, root, m_type, m, s_name, s_path))])
    return docs

def load_runtime_documents(store_path: Path, resolve_model_ref, pythonpath: str | None = None, include_linked: bool = False) -> list[RuntimeDocument]:
    docs = _load_one(store_path, "local", resolve_model_ref, pythonpath)
    if include_linked:
        for linked in load_store_index(store_path).stores:
            if store_exists(linked_store := _resolve_path(project_root(store_path), linked.path)):
                docs.extend(_load_one(linked_store, linked.name, resolve_model_ref, pythonpath))
    return docs


from sldb.store.query_engine.structural import (  # noqa: E402
    list_structural as list_structural,
    get_structural as get_structural,
)
from sldb.store.query_engine.structural_queries import (  # noqa: E402
    glob_structural as glob_structural,
    find_structural as find_structural,
)
from sldb.store.query_engine.semantic import (  # noqa: E402
    list_semantic as list_semantic,
    get_semantic as get_semantic,
    glob_semantic as glob_semantic,
    find_semantic as find_semantic,
)
from sldb.store.query_engine.global_semantic import (  # noqa: E402
    get_global_semantic as get_global_semantic,
    list_global_semantic as list_global_semantic,
)
