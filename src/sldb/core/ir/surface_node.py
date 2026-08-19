from typing import Any
from pydantic import BaseModel, Field
from .source_span import SourceSpan

class SurfaceNode(BaseModel):
    kind: str = Field(description="Surface syntax node kind.")
    text: str = Field(default="", description="Original reversible text content.")
    span: SourceSpan = Field(default_factory=SourceSpan)
    children: list["SurfaceNode"] = Field(default_factory=list)
    metadata: dict[str, Any] = Field(default_factory=dict)

SurfaceNode.model_rebuild()
