from pydantic import BaseModel, Field
from .source_span import SourceSpan

class SectionContextEntry(BaseModel):
    node_id: str = Field(description="Stable section node identifier.")
    path: str = Field(description="Hierarchical section path.")
    title: str = Field(description="Human section title.")
    breadcrumbs: list[str] = Field(default_factory=list)
    about: list[str] = Field(default_factory=list)
    semantic_tags: list[str] = Field(default_factory=list)
    span: SourceSpan = Field(default_factory=SourceSpan)
