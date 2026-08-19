from pydantic import BaseModel, Field


class SemanticDocumentRecord(BaseModel):
    model: str
    path: str
    tags: list[str] = Field(default_factory=list)
