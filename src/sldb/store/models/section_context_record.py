from pydantic import BaseModel, Field


class SectionContextRecord(BaseModel):
    path: str
    title: str
    breadcrumbs: list[str] = Field(default_factory=list)
    about: list[str] = Field(default_factory=list)
    semantic_tags: list[str] = Field(default_factory=list)
    slug: str = ''
    level: int = 0
    line_start: int | None = None
    line_end: int | None = None
