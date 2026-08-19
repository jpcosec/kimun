from __future__ import annotations
from pathlib import Path
from typing import Any
from sldb.cli.store_context import get_store_context
from sldb.core.exceptions import SLDBStoreError
from sldb.store.io import load_store_index, save_store_index
from sldb.store.layout import store_exists
from sldb.store.models import StoreEntry, StoreIndex

def add_store(args: Any) -> int:
    sp, root = get_store_context(args.store)
    other = Path(args.path).resolve()
    _check_other_exists(other)
    name = args.name or other.parent.name
    rel = _link_store(sp, root, other, name)
    print(f"Linked '{name}' at {rel}")
    return 0

def _check_other_exists(other: Path) -> None:
    if not store_exists(other):
        raise SLDBStoreError(f"No store at {other}")

def _check_store_linked(idx: StoreIndex, name: str) -> None:
    if any(s.name == name for s in idx.stores):
        raise SLDBStoreError(f"Store '{name}' already linked.")

def _link_store(sp: Path, root: Path, other: Path, name: str) -> str:
    idx = load_store_index(sp)
    _check_store_linked(idx, name)
    rel = str(other.relative_to(root)) if other.is_relative_to(root) else str(other)
    idx.stores.append(StoreEntry(name=name, path=rel))
    save_store_index(sp, idx)
    return rel
