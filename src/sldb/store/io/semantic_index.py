import contextlib
import fcntl
import os
import tempfile
from pathlib import Path

import yaml

from sldb.core.exceptions import SLDBStoreError
from sldb.store.layout import (
    lock_path,
    semantic_dag_path,
    semantic_index_path,
    store_index_path,
)
from sldb.store.models import (
    DocumentsIndex,
    ModelsIndex,
    SectionsIndex,
    SemanticDAG,
    SemanticIndex,
    StoreIndex,
)

_LOCK_TIMEOUT = 10

from sldb.store.io.utils import StoreIOUtils

class SemanticIndexIO:
    @staticmethod
    def load(store_path: Path) -> SemanticIndex:
        index_file = semantic_index_path(store_path)
        if not index_file.exists():
            return SemanticIndex()
        data = yaml.safe_load(index_file.read_text(encoding="utf-8")) or {}
        return SemanticIndex(**data)

    @staticmethod
    def save(store_path: Path, index: SemanticIndex) -> None:
        StoreIOUtils._atomic_write(
            semantic_index_path(store_path),
            yaml.safe_dump(index.model_dump(), sort_keys=False),
        )
