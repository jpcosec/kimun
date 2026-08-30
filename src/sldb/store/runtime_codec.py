from __future__ import annotations
from typing import Any


class RuntimeCodec:
    """Default codec wrapping the runtime's extract_model_data (local import)."""
    def extract(self, model_type: Any, markdown_text: str) -> dict[str, Any]:
        from sldb.runtime.validation import extract_model_data
        return extract_model_data(model_type, markdown_text)
