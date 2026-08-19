import re
import difflib
from typing import Any
from .search_record import SearchRecord

def flatten_payload(payload: Any, prefix: str = "") -> list[tuple[str, Any]]:
    if not isinstance(payload, dict): return [(prefix, payload)]
    return [item for key, value in payload.items() for item in flatten_payload(value, f"{prefix}.{key}" if prefix else key)]

def _slugify(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-") or "section"

def _annotation_name(annotation: Any) -> str:
    if annotation is None: return "Any"
    return annotation if isinstance(annotation, str) else getattr(annotation, "__name__", repr(annotation))

def _matches_term(haystacks: list[str], term: str, regex: bool, fuzzy: bool) -> bool:
    if regex: return any(re.search(term, value) for value in haystacks)
    if fuzzy: return any(difflib.SequenceMatcher(a=term.lower(), b=v.lower()).ratio() >= 0.7 or term.lower() in v.lower() for v in haystacks)
    return any(term == value or term in value for value in haystacks)

def search_records(records: list[SearchRecord], term: str, search_in: str, regex: bool = False, fuzzy: bool = False, kinds: set[str] | None = None) -> list[SearchRecord]:
    if term in {"stores", "models", "docs", "sections", "fields"}: return [r for r in records if r.kind == (term[:-1] if term.endswith("s") else term)]
    if kinds: records = [r for r in records if r.kind in kinds]
    if not term: return records
    return [r for r in records if _matches_term([v for v in ((r.physical if search_in in {"physical", "both"} else []) + (r.semantic + (r.about or []) if search_in in {"semantic", "both"} else [])) if v], term, regex, fuzzy)]

def _about_terms(breadcrumbs: list[str], semantic_tags: list[str]) -> list[str]:
    seen, terms = set(), []
    for raw in (b.strip() for b in breadcrumbs):
        if raw and raw not in seen: seen.add(raw); terms.append(raw)
        norm = _slugify(raw).replace("-", " ").strip()
        if norm and norm not in seen: seen.add(norm); terms.append(norm)
    for tag in semantic_tags:
        if tag not in seen: seen.add(tag); terms.append(tag)
    return terms
