from __future__ import annotations

from sldb.core.contracts import Marker


def parse_marker(inner: str) -> Marker:
    """
    Parses a marker string into its components.

    Args:
        inner: The content inside ⸢...⸥.

    Returns:
        A Marker model instance.
    """
    head, _, prop = inner.partition("•")
    parts = _split_marker_head(head)
    kind = parts[0] if parts else "rev"
    traits = parts[1:] if parts else []
    name = prop.strip() if prop else ""
    return Marker(kind=kind, traits=traits, name=name)


def _split_marker_head(head: str) -> list[str]:
    parts: list[str] = []
    current: list[str] = []
    bracket_depth = 0
    for char in head:
        if char == "[":
            bracket_depth += 1
        elif char == "]" and bracket_depth:
            bracket_depth -= 1

        if char == "," and bracket_depth == 0:
            part = "".join(current).strip()
            if part:
                parts.append(part)
            current = []
            continue
        current.append(char)

    part = "".join(current).strip()
    if part:
        parts.append(part)
    return parts
