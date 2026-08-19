from pydantic import BaseModel, Field
from sldb.store.models.semantic_document_record import SemanticDocumentRecord


class SemanticIndex(BaseModel):
    tags: dict[str, list[str]] = Field(default_factory=dict)
    documents: dict[str, SemanticDocumentRecord] = Field(default_factory=dict)
