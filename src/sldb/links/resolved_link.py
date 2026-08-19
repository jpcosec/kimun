from dataclasses import dataclass

@dataclass
class ResolvedLink:
    raw: str
    target: str
    kind: str
    resolved: bool
    path: str | None = None
    source: str | None = None
    predicate: str | None = None
    w5h1_type: str | None = None
