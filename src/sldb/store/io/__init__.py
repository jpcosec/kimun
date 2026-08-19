from pathlib import Path
from sldb.store.io.lock import StoreIOLock
from sldb.store.io.store_index import StoreIndexIO
from sldb.store.io.models_index import ModelsIndexIO
from sldb.store.io.documents_index import DocumentsIndexIO
from sldb.store.io.sections_index import SectionsIndexIO
from sldb.store.io.semantic_dag import SemanticDAGIO
from sldb.store.io.semantic_index import SemanticIndexIO
from sldb.store.models import DocumentsIndex, ModelsIndex, SectionsIndex, SemanticDAG, SemanticIndex, StoreIndex

def store_lock(store_path: Path, wait: bool = False):
    return StoreIOLock(store_path).acquire(wait)

def load_store_index(store_path: Path) -> StoreIndex:
    return StoreIndexIO.load(store_path)

def save_store_index(store_path: Path, index: StoreIndex) -> None:
    return StoreIndexIO.save(store_path, index)

def load_models_index(path: Path) -> ModelsIndex:
    return ModelsIndexIO.load(path)

def save_models_index(path: Path, index: ModelsIndex) -> None:
    return ModelsIndexIO.save(path, index)

def load_documents_index(path: Path) -> DocumentsIndex:
    return DocumentsIndexIO.load(path)

def save_documents_index(path: Path, index: DocumentsIndex) -> None:
    return DocumentsIndexIO.save(path, index)

def load_sections_index(path: Path) -> SectionsIndex:
    return SectionsIndexIO.load(path)

def save_sections_index(path: Path, index: SectionsIndex) -> None:
    return SectionsIndexIO.save(path, index)

def load_semantic_dag(store_path: Path) -> SemanticDAG:
    return SemanticDAGIO.load(store_path)

def save_semantic_dag(store_path: Path, dag: SemanticDAG) -> None:
    return SemanticDAGIO.save(store_path, dag)

def load_semantic_index(store_path: Path) -> SemanticIndex:
    return SemanticIndexIO.load(store_path)

def save_semantic_index(store_path: Path, index: SemanticIndex) -> None:
    return SemanticIndexIO.save(store_path, index)
