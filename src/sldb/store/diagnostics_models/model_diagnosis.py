from __future__ import annotations
from dataclasses import dataclass, field
from enum import Enum
from sldb.store.diagnostics_models.document_diagnosis import DocumentDiagnosis


@dataclass
class ModelDiagnosis:
    """Diagnostic state for a model and its documents."""
    name: str
    hash_b_ok: bool
    documents: list[DocumentDiagnosis] = field(default_factory=list)
