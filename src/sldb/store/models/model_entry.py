from pydantic import BaseModel, Field


class ModelEntry(BaseModel):
    name: str
    model_ref: str
    path: str
    models_index: str
    version: int = 1
    canonical: bool = False
    family: str | None = None
    semantics: list[str] = Field(default_factory=list)
