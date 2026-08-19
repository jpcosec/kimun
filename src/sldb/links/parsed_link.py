from dataclasses import dataclass

@dataclass
class ParsedLink:
    raw: str
    target: str
    kind: str
    predicate: str | None = None
    w5h1_type: str | None = None
