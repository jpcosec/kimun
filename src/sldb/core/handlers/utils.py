from __future__ import annotations
from sldb.core.contracts import Marker

def parse_marker(inner: str) -> Marker:
    head, _, prop = inner.partition("•")
    parts = _split_marker_head(head)
    return Marker(
        kind=parts[0] if parts else "rev",
        traits=parts[1:] if parts else [],
        name=prop.strip() if prop else ""
    )

def _split_marker_head(head: str) -> list[str]:
    parts, current, depth = [], [], 0
    for char in head:
        depth = _process_char(char, depth)
        if char == "," and depth == 0:
            current = _append_part(current, parts)
            continue
        current.append(char)
    _append_part(current, parts)
    return parts

def _process_char(char: str, depth: int) -> int:
    if char == "[":
        return depth + 1
    if char == "]" and depth > 0:
        return depth - 1
    return depth

def _append_part(current: list[str], parts: list[str]) -> list[str]:
    if part := "".join(current).strip():
        parts.append(part)
    return []
