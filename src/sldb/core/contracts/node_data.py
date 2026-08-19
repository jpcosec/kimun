from typing import Any
from pydantic import BaseModel, Field
from .marker import Marker

class NodeData(BaseModel):
    """Contract for data extracted from a Markdown node."""
    field_name: str = Field(description="The name of the model field this node maps to.")
    value: Any = Field(description="The extracted value, typed according to the model.")
    marker: Marker = Field(description="The structured marker that produced this data.")
    metadata: dict[str, Any] = Field(default_factory=dict, description="Additional handler-specific metadata.")
