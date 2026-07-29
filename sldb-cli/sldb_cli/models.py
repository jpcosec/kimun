from pydantic import BaseModel, Field
from typing import List, Dict, Optional, Any

class SLDBFrontmatter(BaseModel):
    """
    Base model for standard SLDB frontmatter fields.
    """
    id: str
    status: Optional[str] = "draft"
    tags: List[str] = Field(default_factory=list)
    history: List[str] = Field(default_factory=list)
    references: List[str] = Field(default_factory=list)

class StructuredNLDoc(BaseModel):
    """
    Core Model representing a fully structured SLDB Document.
    """
    frontmatter: SLDBFrontmatter
    payload: str
    ast: Optional[List[Dict[str, Any]]] = None
    
    @classmethod
    def validate_schema(cls, data: dict):
        """
        Validates raw dictionary data against the schema.
        """
        return cls(**data)
