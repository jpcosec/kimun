from typing import Any
from pydantic import BaseModel, Field

class DocumentContext(BaseModel):
    physical: dict[str, Any] = Field(default_factory=dict)
    semantic: dict[str, Any] = Field(default_factory=dict)
