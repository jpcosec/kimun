from typing import Any
from pydantic import BaseModel, Field
from .graph_edge import GraphEdge

class GraphView(BaseModel):
    nodes: list[dict[str, Any]] = Field(default_factory=list)
    edges: list[GraphEdge] = Field(default_factory=list)
