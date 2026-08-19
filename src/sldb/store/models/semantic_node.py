from pydantic import BaseModel, Field


class SemanticNode(BaseModel):
    id: str
    parents: list[str] = Field(default_factory=list)
