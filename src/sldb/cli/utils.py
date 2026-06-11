from __future__ import annotations

import sys
from importlib import import_module
import json
from pathlib import Path
from typing import Any

import yaml

from sldb.core.exceptions import SLDBModelError
from sldb.models.structured_doc import StructuredNLDoc


def resolve_model_ref(
    model_ref: str, pythonpath: str | None = None
) -> type[StructuredNLDoc]:
    """Resolve a string reference into a StructuredNLDoc class."""
    if ":" not in model_ref:
        raise SLDBModelError("Model reference must use the form 'module:ClassName'.")

    search_paths = [str(Path.cwd().resolve())]
    if pythonpath:
        search_paths.insert(0, str(Path(pythonpath).resolve()))

    for path in reversed(search_paths):
        if path not in sys.path:
            sys.path.insert(0, path)

    module_name, attr_path = model_ref.split(":", 1)
    try:
        module = import_module(module_name)
    except ImportError as exc:
        raise SLDBModelError(f"Failed to import module '{module_name}'.") from exc

    obj: Any = module
    try:
        for attr in attr_path.split("."):
            obj = getattr(obj, attr)
    except AttributeError as exc:
        raise SLDBModelError(
            f"Attribute '{attr_path}' not found in '{module_name}'."
        ) from exc

    if not isinstance(obj, type) or not issubclass(obj, StructuredNLDoc):
        raise SLDBModelError(f"'{model_ref}' is not a StructuredNLDoc subclass.")

    return obj


def read_text(path: str) -> str:
    """Read text from a file or stdin."""
    if path == "-":
        return sys.stdin.read()
    return Path(path).read_text(encoding="utf-8")


def write_text(path: str, content: str) -> None:
    """Write text to a file or stdout."""
    if path == "-":
        sys.stdout.write(content)
        return
    Path(path).write_text(content, encoding="utf-8")


def get_store_context(
    store_arg: str | None, mode: str = "default"
) -> tuple[Path, Path]:
    """Resolve store path and project root.

    When *mode* is ``"readonly"`` and no local store is found,
    falls back to the global ``~/.sldb/`` with a warning instead of
    raising an error.
    """
    from sldb.store.resolver import global_store_path, find_local_store
    from sldb.core.exceptions import SLDBStoreError

    if store_arg:
        sp = _resolve_store_arg(store_arg)
    else:
        found = find_local_store()
        if found:
            sp = found
        else:
            cwd = Path.cwd().resolve()
            global_store = global_store_path().resolve()
            if global_store.exists():
                if mode == "readonly":
                    print(
                        f"[warning] No local .sldb store; falling back to "
                        f"global store at {global_store}",
                        file=sys.stderr,
                    )
                    sp = global_store
                else:
                    raise SLDBStoreError(
                        "No local .sldb store found from "
                        f"{cwd}. A global store exists at {global_store}. "
                        f"Pass --store {global_store} to use it, or run "
                        "'sldb stores init --path .' to create a local store."
                    )
            else:
                raise SLDBStoreError(
                    "No local .sldb store found from "
                    f"{cwd}. No global store exists at {global_store}. "
                    "Run 'sldb stores init --path .' to create one, or pass --store PATH."
                )
    from sldb.store.layout import project_root, store_exists
    from sldb.store.migration import migrate_store_layout

    root = project_root(sp)
    if store_exists(sp):
        migrate_store_layout(sp, root)
    return sp, root


def _resolve_store_arg(store_arg: str) -> Path:
    """Resolve an explicit store path or a linked store alias from the local store."""
    from sldb.store.resolver import find_local_store
    from sldb.store.layout import project_root, store_exists
    from sldb.store.io import load_store_index
    from sldb.core.exceptions import SLDBStoreError

    candidate = Path(store_arg).resolve()
    if store_exists(candidate):
        return candidate

    local_store = find_local_store()
    if local_store is None:
        return candidate

    local_root = project_root(local_store)
    store_index = load_store_index(local_store)
    linked = next((entry for entry in store_index.stores if entry.name == store_arg), None)
    if linked is None:
        return candidate

    linked_path = Path(linked.path)
    resolved = linked_path if linked_path.is_absolute() else (local_root / linked_path)
    resolved = resolved.resolve()
    if not store_exists(resolved):
        raise SLDBStoreError(f"Linked store '{store_arg}' does not exist at {resolved}.")
    return resolved


def registered_model(
    store_path: Path, model_name: str, pythonpath: str | None
) -> tuple[type, Any, Any]:
    """Helper to load a registered model and its store metadata."""
    from sldb.store.io import load_store_index

    idx = load_store_index(store_path)
    entry = next((m for m in idx.models if m.name == model_name), None)
    if entry is not None:
        return resolve_model_ref(entry.model_ref, pythonpath), entry, idx

    federated = _find_federated_model(store_path, model_name, pythonpath)
    if federated is not None:
        return federated

    from sldb.core.exceptions import SLDBStoreError

    raise SLDBStoreError(f"Model '{model_name}' not registered.")


def _find_federated_model(
    destination_store: Path, model_name: str, pythonpath: str | None
) -> tuple[type, Any, Any] | None:
    """Find a model in linked stores and register a local document index for it."""
    from sldb.store.resolver import find_local_store
    from sldb.store.layout import project_root, store_exists
    from sldb.store.io import (
        load_store_index,
    )

    if ":" not in model_name:
        return None

    store_alias, remote_model_name = model_name.split(":", 1)
    linked_store = None
    for registry_store in _model_registry_stores(destination_store, find_local_store()):
        registry_root = project_root(registry_store)
        registry_index = load_store_index(registry_store)
        linked = next(
            (entry for entry in registry_index.stores if entry.name == store_alias), None
        )
        if linked is None:
            continue
        linked_path = Path(linked.path)
        candidate = (
            linked_path if linked_path.is_absolute() else registry_root / linked_path
        ).resolve()
        if store_exists(candidate):
            linked_store = candidate
            break
    if linked_store is None:
        return None

    destination_root = project_root(destination_store)
    destination_index = load_store_index(destination_store)
    linked_root = project_root(linked_store)
    linked_index = load_store_index(linked_store)
    remote_entry = next(
        (entry for entry in linked_index.models if entry.name == remote_model_name), None
    )
    if remote_entry is None:
        return None

    model_type = resolve_model_ref(remote_entry.model_ref, pythonpath)
    local_entry = _ensure_federated_model_entry(
        destination_store,
        destination_root,
        destination_index,
        model_type,
        remote_entry.model_ref,
        linked_root / remote_entry.path,
    )
    return model_type, local_entry, load_store_index(destination_store)


def _model_registry_stores(destination_store: Path, local_store: Path | None) -> list[Path]:
    stores = [destination_store]
    if local_store is not None and local_store.resolve() != destination_store.resolve():
        stores.append(local_store.resolve())
    return stores


def _ensure_federated_model_entry(
    store_path: Path,
    root: Path,
    store_index: Any,
    model_type: type[StructuredNLDoc],
    model_ref: str,
    model_path: Path,
) -> Any:
    from sldb.store.io import (
        load_store_index,
        save_documents_index,
        save_models_index,
        store_lock,
    )
    from sldb.store.layout import documents_index_relpath, models_index_relpath
    from sldb.store.models import DocumentsIndex, ModelEntry, ModelsIndex
    from sldb.store.ops import cascade_hash_a
    from sldb.store.semantic_tags import flatten_model_semantics

    existing = next(
        (entry for entry in store_index.models if entry.name == model_type.__name__), None
    )
    if existing is not None:
        return existing

    mi_rel = models_index_relpath(model_type.__name__)
    di_rel = documents_index_relpath(model_type.__name__)
    try:
        rel_model_path = str(model_path.resolve().relative_to(root))
    except ValueError:
        rel_model_path = str(model_path.resolve())

    with store_lock(store_path):
        latest_index = load_store_index(store_path)
        existing = next(
            (entry for entry in latest_index.models if entry.name == model_type.__name__),
            None,
        )
        if existing is not None:
            return existing

        save_documents_index(root / di_rel, DocumentsIndex())
        save_models_index(
            root / mi_rel,
            ModelsIndex(
                name=model_type.__name__,
                model_ref=model_ref,
                path=rel_model_path,
                documents_index=di_rel,
                hash_b="",
                version=1,
                canonical=False,
                semantics=flatten_model_semantics(model_type),
            ),
        )
        entry = ModelEntry(
            name=model_type.__name__,
            model_ref=model_ref,
            path=rel_model_path,
            models_index=mi_rel,
            version=1,
        )
        latest_index.models.append(entry)
        cascade_hash_a(store_path, root, latest_index)
        return entry


def parse_data_value(raw: str) -> Any:
    """Parse JSON/YAML scalars or objects from a CLI string."""
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return yaml.safe_load(raw)


def deep_get(payload: Any, path: str) -> Any:
    value = payload
    for part in _split_path(path):
        if isinstance(value, dict):
            if part not in value:
                raise KeyError(path)
            value = value[part]
            continue
        if isinstance(value, list):
            value = value[int(part)]
            continue
        raise KeyError(path)
    return value


def deep_set(payload: Any, path: str, new_value: Any, create: bool = False) -> Any:
    parts = _split_path(path)
    target = payload
    for part in parts[:-1]:
        if isinstance(target, dict):
            if part not in target:
                if not create:
                    raise KeyError(path)
                target[part] = {}
            target = target[part]
            continue
        if isinstance(target, list):
            target = target[int(part)]
            continue
        raise KeyError(path)

    leaf = parts[-1]
    if isinstance(target, dict):
        if not create and leaf not in target:
            raise KeyError(path)
        target[leaf] = new_value
        return payload
    if isinstance(target, list):
        target[int(leaf)] = new_value
        return payload
    raise KeyError(path)


def deep_delete(payload: Any, path: str) -> Any:
    parts = _split_path(path)
    target = payload
    for part in parts[:-1]:
        target = target[part] if isinstance(target, dict) else target[int(part)]
    leaf = parts[-1]
    if isinstance(target, dict):
        target.pop(leaf, None)
        return payload
    if isinstance(target, list):
        target.pop(int(leaf))
        return payload
    raise KeyError(path)


def ensure_list(payload: Any, path: str) -> list[Any]:
    value = deep_get(payload, path)
    if not isinstance(value, list):
        raise TypeError(f"Target '{path}' is not a list field.")
    return value


def _split_path(path: str) -> list[str]:
    parts = [part for part in path.split(".") if part]
    if not parts:
        raise KeyError(path)
    return parts
