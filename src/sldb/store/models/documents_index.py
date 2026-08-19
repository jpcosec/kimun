from pydantic import BaseModel, Field
from sldb.store.models.document_entry import DocumentEntry


class DocumentsIndex(BaseModel):
    documents: list[DocumentEntry] = Field(default_factory=list)
