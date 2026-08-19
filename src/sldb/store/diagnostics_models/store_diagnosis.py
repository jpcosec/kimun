from __future__ import annotations
from dataclasses import dataclass, field
from enum import Enum
from sldb.store.diagnostics_models.model_diagnosis import ModelDiagnosis
from sldb.store.diagnostics_models.diagnosis_note import DiagnosisNote


@dataclass
class StoreDiagnosis:
    """Full diagnostic state for the entire store."""
    hash_a_ok: bool
    models: list[ModelDiagnosis] = field(default_factory=list)

    @property
    def is_valid(self) -> bool:
        """Returns True if the store is in a valid state."""
        bad = {DiagnosisNote.DATA_MUTATION, DiagnosisNote.MISSING}
        return self.hash_a_ok and all((m.hash_b_ok for m in self.models)) and all((d.note not in bad for m in self.models for d in m.documents))
