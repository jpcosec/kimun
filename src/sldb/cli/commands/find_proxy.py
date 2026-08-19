from typing import Any

class _RuntimeDocProxy:
    payload: dict[str, Any]
    name: str
    model_type: type

    def __init__(self, payload: dict[str, Any], name: str, model_type: type) -> None:
        self.payload = payload
        self.name = name
        self.model_type = model_type
