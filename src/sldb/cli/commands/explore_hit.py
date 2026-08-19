from dataclasses import dataclass

@dataclass
class ExploreHit:
    source: str
    kind: str
    path: str
    anchor: str
    line: int
    snippet: str
