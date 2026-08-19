import os
import re
from pathlib import Path

content = """import contextlib
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

class StoreIOLock:
    def __init__(self, store_path: Path):
        self.store_path = store_path

    @contextlib.contextmanager
    def acquire(self, wait: bool = False):
        lock_file = lock_path(self.store_path)
        lock_file.parent.mkdir(parents=True, exist_ok=True)
        lock_fd = os.open(lock_file, os.O_CREAT | os.O_RDWR, 0o644)
        try:
            self._lock(lock_fd, wait)
            yield
            fcntl.lockf(lock_fd, fcntl.LOCK_UN)
        finally:
            os.close(lock_fd)

    def _lock(self, lock_fd: int, wait: bool):
        flags = fcntl.LOCK_EX
        if not wait:
            flags |= fcntl.LOCK_NB
        try:
            fcntl.lockf(lock_fd, flags)
        except BlockingIOError:
            raise SLDBStoreError(
                f"Store at {self.store_path} is busy. "
                "Use --wait to block."
            )

class StoreIOUtils:
    @staticmethod
    def _atomic_write(path: Path, content: str) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        fd, tmp_path = tempfile.mkstemp(
            dir=path.parent, prefix=path.name + ".", suffix=".tmp"
        )
        try:
            os.write(fd, content.encode("utf-8"))
            os.fsync(fd)
            os.close(fd)
            fd = None
            os.replace(tmp_path, path)
        finally:
            if fd is not None:
                os.close(fd)
            if os.path.exists(tmp_path):
                os.unlink(tmp_path)


class StoreIndexIO:
    @staticmethod
    def load(store_path: Path) -> StoreIndex:
        index_file = store_index_path(store_path)
        if not index_file.exists():
            raise FileNotFoundError(f"No store_index.yaml at {store_path}")
        data = yaml.safe_load(index_file.read_text(encoding="utf-8")) or {}
        return StoreIndex(**data)

    @staticmethod
    def save(store_path: Path, index: StoreIndex) -> None:
        StoreIOUtils._atomic_write(
            store_index_path(store_path),
            yaml.safe_dump(index.model_dump(), sort_keys=False),
        )

class ModelsIndexIO:
    @staticmethod
    def load(path: Path) -> ModelsIndex:
        data = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
        return ModelsIndex(**data)

    @staticmethod
    def save(path: Path, index: ModelsIndex) -> None:
        StoreIOUtils._atomic_write(
            path,
            yaml.safe_dump(index.model_dump(), sort_keys=False),
        )


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

class SectionsIndexIO:
    @staticmethod
    def load(path: Path) -> SectionsIndex:
        if not path.exists():
            return SectionsIndex()
        data = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
        return SectionsIndex(**data)

    @staticmethod
    def save(path: Path, index: SectionsIndex) -> None:
        StoreIOUtils._atomic_write(
            path,
            yaml.safe_dump(index.model_dump(), sort_keys=False),
        )


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
"""

parts = re.split(r'\nclass ', content)
header = parts[0]
classes = parts[1:]

files = {
    'StoreIOLock': 'lock.py',
    'StoreIOUtils': 'utils.py',
    'StoreIndexIO': 'store_index.py',
    'ModelsIndexIO': 'models_index.py',
    'DocumentsIndexIO': 'documents_index.py',
    'SectionsIndexIO': 'sections_index.py',
    'SemanticDAGIO': 'semantic_dag.py',
    'SemanticIndexIO': 'semantic_index.py'
}

for cls in classes:
    cls_name = cls.split(':')[0].split('(')[0].strip()
    fname = files.get(cls_name)
    if not fname: continue
    
    file_path = Path("src/sldb/store/io") / fname
    
    extra_imports = ""
    if cls_name != 'StoreIOUtils' and 'StoreIOUtils' in cls:
        extra_imports = "from sldb.store.io.utils import StoreIOUtils\n"
        
    file_path.write_text(header + "\n" + extra_imports + "\nclass " + cls)

