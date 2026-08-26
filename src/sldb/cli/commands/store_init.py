from __future__ import annotations
from pathlib import Path
from typing import Any
from sldb.store.io import save_semantic_dag, save_semantic_index, save_store_index
from sldb.store.layout import store_exists
from sldb.store.models import SemanticDAG, StoreIndex, SemanticIndex
from sldb.store.predicates import default_predicates

def init_store(args: Any) -> int:
    root = Path(args.path).resolve()
    sp = root / ".sldb"
    preexisting = store_exists(sp)
    if preexisting and not args.force:
        print(f"Store already exists at {sp}. Use --force to reinitialize."); return 0
    if preexisting:
        import shutil; shutil.rmtree(sp)
    _create_store(sp)
    print(f"{'Reinitialized' if preexisting else 'Initialized'} store at {sp}")
    return 0

def _create_store(sp: Path) -> None:
    save_store_index(sp, StoreIndex(predicates=default_predicates()))
    save_semantic_dag(sp, SemanticDAG(equivalences={}))
    save_semantic_index(sp, SemanticIndex())