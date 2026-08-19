import difflib
import re
from typing import Any

def _slugify(text: str) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")
    return slug or "section"

def _annotation_name(annotation: Any) -> str:
    if annotation is None:
        return "Any"
    if isinstance(annotation, str):
        return annotation
    return getattr(annotation, "__name__", repr(annotation))

def _matches_term(haystacks: list[str], term: str, regex: bool, fuzzy: bool) -> bool:
    if regex:
        return any(re.search(term, value) is not None for value in haystacks)
    if fuzzy:
        target = term.lower()
        return any(difflib.SequenceMatcher(a=target, b=value.lower()).ratio() >= 0.7 or target in value.lower() for value in haystacks)
    return any(term == value or term in value for value in haystacks)
