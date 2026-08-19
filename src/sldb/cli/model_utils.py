from __future__ import annotations
import sys
from importlib import import_module
from pathlib import Path
from typing import Any
from sldb.core.exceptions import SLDBModelError, SLDBStoreError
from sldb.models.structured_doc import StructuredNLDoc
from sldb.store.io import load_store_index

def _setup_sys_path(pythonpath: str | None) -> None:
    search_paths = [str(Path.cwd().resolve())]
    if pythonpath:
        search_paths.insert(0, str(Path(pythonpath).resolve()))
    for path in reversed(search_paths):
        if path not in sys.path:
            sys.path.insert(0, path)

def _get_module(module_name: str) -> Any:
    try:
        return import_module(module_name)
    except ImportError as exc:
        raise SLDBModelError(f"Failed to import module '{module_name}'.") from exc

def _import_module_attr(module_name: str, attr_path: str) -> Any:
    obj = _get_module(module_name)
    try:
        for attr in attr_path.split("."):
            obj = getattr(obj, attr)
    except AttributeError as exc:
        raise SLDBModelError(f"Attribute '{attr_path}' not found in '{module_name}'.") from exc
    return obj

def resolve_model_ref(model_ref: str, pythonpath: str | None = None) -> type[StructuredNLDoc]:
    if ":" not in model_ref:
        raise SLDBModelError("Model reference must use the form 'module:ClassName'.")
    _setup_sys_path(pythonpath)
    module_name, attr_path = model_ref.split(":", 1)
    obj = _import_module_attr(module_name, attr_path)
    if not isinstance(obj, type) or not issubclass(obj, StructuredNLDoc):
        raise SLDBModelError(f"'{model_ref}' is not a StructuredNLDoc subclass.")
    return obj

def registered_model(store_path: Path, model_name: str, pythonpath: str | None) -> tuple[type, Any, Any]:
    from sldb.cli.federated_utils import _find_federated_model
    idx = load_store_index(store_path)
    entry = next((m for m in idx.models if m.name == model_name), None)
    if entry is not None:
        return resolve_model_ref(entry.model_ref, pythonpath), entry, idx
    federated = _find_federated_model(store_path, model_name, pythonpath)
    if federated is not None:
        return federated
    raise SLDBStoreError(f"Model '{model_name}' not registered.")
