from __future__ import annotations

from sldb.store.io import load_models_index, load_sections_index
from sldb.store.models import SectionContextRecord


def section_record_to_entry(sec: SectionContextRecord) -> dict:
    return {
        "path": sec.path, "title": sec.title, "breadcrumbs": list(sec.breadcrumbs),
        "about": list(sec.about) if sec.about else [], "slug": sec.slug,
        "semantic_tags": list(sec.semantic_tags) if sec.semantic_tags else [],
        "level": sec.level, "line_start": sec.line_start, "line_end": sec.line_end,
    }


def load_persisted_sections(store_index, root, model_name: str, doc_name: str):
    for model_entry in store_index.models:
        if model_entry.name == model_name:
            return load_model_sections(root, model_entry, doc_name)
    return None


def load_model_sections(root, model_entry, doc_name):
    models_idx = load_models_index(root / model_entry.models_index)
    if not models_idx.sections_index:
        return None
    sections_idx = load_sections_index(root / models_idx.sections_index)
    return next((ds for ds in sections_idx.documents if ds.doc_name == doc_name), None)
