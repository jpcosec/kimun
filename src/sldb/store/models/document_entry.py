from pydantic import BaseModel, Field


class DocumentEntry(BaseModel):
    name: str
    path: str
    hash_c: str = ''
    hash_d: str = ''
    semantic_tags: list[str] = Field(default_factory=list)
