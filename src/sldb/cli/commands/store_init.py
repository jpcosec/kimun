from __future__ import annotations
from pathlib import Path
from typing import Any
from sldb.core.exceptions import SLDBStoreError
from sldb.store.io import save_semantic_dag, save_semantic_index, save_store_index
from sldb.store.layout import store_exists
from sldb.store.models import SemanticDAG, StoreIndex, SemanticIndex
from sldb.store.predicates import default_predicates

def init_store(args: Any) -> int:
    root = Path(args.path).resolve()
    sp = root / ".sldb"
    _check_not_exists(sp, args.force)
    save_store_index(sp, StoreIndex(predicates=default_predicates()))
    save_semantic_dag(sp, SemanticDAG(equivalences={}))
    save_semantic_index(sp, SemanticIndex())
    print(f"Initialized store at {sp}")
    return 0

def _check_not_exists(sp: Path, force: bool) -> None:
    if store_exists(sp) and not force:
        raise SLDBStoreError(f"Store exists at {sp}.")
