from pydantic import BaseModel, Field
from sldb.store.models.section_context_record import SectionContextRecord


class DocSections(BaseModel):
    doc_name: str
    sections: list[SectionContextRecord] = Field(default_factory=list)
