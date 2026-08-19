from typing import Any
from pydantic import BaseModel, Field

class RenderContext(BaseModel):
    """Contract for the data passed to the renderer."""
    model_name: str = Field(description="The name of the Pydantic model being rendered.")
    data: dict[str, Any] = Field(description="The structured data to be injected into the template.")
    template_path: str | None = Field(default=None, description="Optional path to the source template.")
