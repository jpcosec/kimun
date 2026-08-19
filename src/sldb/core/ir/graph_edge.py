from typing import Any
from pydantic import BaseModel, Field

class GraphEdge(BaseModel):
    source: str = Field(description="Source node id.")
    target: str = Field(description="Target node id.")
    relation: str = Field(description="Typed relation between nodes.")
    metadata: dict[str, Any] = Field(default_factory=dict)
