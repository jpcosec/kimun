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

class DocumentsIndexIO:
    @staticmethod
    def load(path: Path) -> DocumentsIndex:
        data = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
        return DocumentsIndex(**data)

    @staticmethod
    def save(path: Path, index: DocumentsIndex) -> None:
        StoreIOUtils._atomic_write(
            path,
            yaml.safe_dump(index.model_dump(), sort_keys=False),
        )
