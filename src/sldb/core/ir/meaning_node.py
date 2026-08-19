from typing import Any
from pydantic import BaseModel, Field
from .source_span import SourceSpan

class MeaningNode(BaseModel):
    kind: str = Field(description="Logical or operational node kind.")
    name: str = Field(description="Stable local identifier.")
    title: str | None = Field(default=None)
    model: str | None = Field(default=None)
    field_path: str | None = Field(default=None)
    value: Any = Field(default=None)
    owning_section: str | None = Field(
        default=None, description="Section path that owns this node."
    )
    span: SourceSpan = Field(default_factory=SourceSpan)
    children: list["MeaningNode"] = Field(default_factory=list)
    metadata: dict[str, Any] = Field(default_factory=dict)

MeaningNode.model_rebuild()
