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

class SemanticDAGIO:
    @staticmethod
    def load(store_path: Path) -> SemanticDAG:
        dag_file = semantic_dag_path(store_path)
        if not dag_file.exists():
            return SemanticDAG()
        data = yaml.safe_load(dag_file.read_text(encoding="utf-8")) or {}
        return SemanticDAG(**data)

    @staticmethod
    def save(store_path: Path, dag: SemanticDAG) -> None:
        StoreIOUtils._atomic_write(
            semantic_dag_path(store_path),
            yaml.safe_dump(dag.model_dump(), sort_keys=False),
        )

