from pydantic import BaseModel, Field

class SourceSpan(BaseModel):
    line_start: int | None = Field(default=None, description="1-based start line.")
    line_end: int | None = Field(default=None, description="1-based end line.")
