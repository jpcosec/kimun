from pydantic import BaseModel, Field


class ModelsIndex(BaseModel):
    name: str
    model_ref: str
    path: str
    documents_index: str
    sections_index: str = ''
    hash_b: str = ''
    version: int = 1
    canonical: bool = False
    family: str | None = None
    semantics: list[str] = Field(default_factory=list)
    base_models: list[str] = Field(default_factory=list)
