from pydantic import BaseModel, Field
from sldb.store.models.semantic_node import SemanticNode


class SemanticDAG(BaseModel):
    nodes: list[SemanticNode] = Field(default_factory=list)
    equivalences: dict[str, list[str]] = Field(default_factory=dict)
