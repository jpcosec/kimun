from dataclasses import dataclass
from typing import Any

@dataclass
class SearchRecord:
    kind: str
    store_name: str
    name: str
    physical: list[str]
    semantic: list[str]
    payload: dict[str, Any]
    value: Any = None
    model_name: str | None = None
    doc_name: str | None = None
    field_path: str | None = None
    path: str | None = None
    title: str | None = None
    about: list[str] | None = None
    owning_section: str | None = None

    def as_dict(self) -> dict[str, Any]:
        d = _build_base_dict(self)
        _add_kind_specific_data(self, d)
        return d

def _build_base_dict(r: SearchRecord) -> dict[str, Any]:
    return {"kind": r.kind, "store": r.store_name, "name": r.name, "model": r.model_name, "doc": r.doc_name, "field": r.field_path, "path": r.path, "title": r.title, "value": r.value, "semantic": r.semantic, "about": r.about or []}

def _add_kind_specific_data(r: SearchRecord, d: dict[str, Any]) -> None:
    if r.kind == "section":
        d["breadcrumbs"] = r.payload.get("breadcrumbs", [])
    if r.kind == "field":
        d["owning_section"] = r.owning_section
