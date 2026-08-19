from pydantic import BaseModel, Field
from sldb.store.models.doc_sections import DocSections


class SectionsIndex(BaseModel):
    documents: list[DocSections] = Field(default_factory=list)
