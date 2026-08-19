from __future__ import annotations

import re
from pathlib import Path
from typing import Any

from sldb.store.io import load_store_index


def _model_scope_docs(store_path: Path, scope: str, recursive: bool, resolve_model_ref, pythonpath: str | None = None) -> list[Any]:
    return StructuralEngine.model_scope_docs(store_path, scope, recursive, resolve_model_ref, pythonpath)
def list_structural(store_path: Path, address: str, resolve_model_ref, pythonpath: str | None = None) -> list[str]:
    return StructuralEngine.list_structural(store_path, address, resolve_model_ref, pythonpath)
def get_structural(store_path: Path, address: str, resolve_model_ref, pythonpath: str | None = None) -> Any:
    return StructuralEngine.get_structural(store_path, address, resolve_model_ref, pythonpath)

class StructuralEngine:
    """Engine for processing structural queries."""
    @classmethod
    def model_scope_docs(cls, store_path: Path, scope: str, recursive: bool, resolve_model_ref, pythonpath: str | None = None) -> list[Any]:
        from sldb.store.query import load_runtime_documents
        docs = load_runtime_documents(store_path, resolve_model_ref, pythonpath)
        if scope == "*": return docs
        base_doc = next((doc for doc in docs if doc.model_name == scope), None)
        if not base_doc: return []
        if not recursive: return [doc for doc in docs if doc.model_name == scope]
        return [doc for doc in docs if issubclass(doc.model_type, base_doc.model_type)]

    @classmethod
    def list_structural(cls, store_path: Path, address: str, resolve_model_ref, pythonpath: str | None = None) -> list[str]:
        if address == "st": return sorted(f"st.{{{e.name}}}" for e in load_store_index(store_path).models)
        match = re.fullmatch(r"st\.\{([^{}+]+)(\+)?\}(?:\.([^.]+))?", address)
        return cls._list_match(match, store_path, resolve_model_ref, pythonpath) if match else []

    @classmethod
    def _list_match(cls, match, store_path: Path, resolve_model_ref, pythonpath: str | None) -> list[str]:
        m_name, r_flag, d_name = match.groups()
        docs = cls.model_scope_docs(store_path, m_name, bool(r_flag), resolve_model_ref, pythonpath)
        if d_name is None: return sorted(doc.name for doc in docs)
        target = next((doc for doc in docs if doc.name == d_name), None)
        return cls._list_target_fields(target) if target else []

    @classmethod
    def _list_target_fields(cls, target) -> list[str]:
        results = []
        for key in sorted(target.payload.keys()):
            field = getattr(target.model_type, "model_fields", {}).get(key)
            desc = field.description if field and field.description else ""
            results.append(f"{key}: {desc}" if desc else key)
        return results

    @classmethod
    def get_structural(cls, store_path: Path, address: str, resolve_model_ref, pythonpath: str | None = None) -> Any:
        match = re.fullmatch(r"st\.\{([^{}+]+)(\+)?\}\.([^.]+)(?:\.(.+))?", address)
        return cls._get_match(match, store_path, resolve_model_ref, pythonpath) if match else None

    @classmethod
    def _get_match(cls, match, store_path: Path, resolve_model_ref, pythonpath: str | None) -> Any:
        m_name, r_flag, d_name, f_name = match.groups()
        docs = cls.model_scope_docs(store_path, m_name, bool(r_flag), resolve_model_ref, pythonpath)
        target = next((doc for doc in docs if doc.name == d_name), None)
        if target is None: return None
        return cls._get_field(target, f_name) if f_name else target.payload

    @classmethod
    def _get_field(cls, target, field_name: str) -> Any:
        value: Any = target.payload
        for part in field_name.split("."):
            if isinstance(value, dict) and part in value: value = value[part]
            else: return None
        return value
