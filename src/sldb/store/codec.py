from __future__ import annotations
from typing import Any, Protocol, runtime_checkable

from sldb.store.runtime_codec import RuntimeCodec


@runtime_checkable
class StoreCodec(Protocol):
    """Turns a document's raw text into a field payload for a given model type.
    The store treats model_type as opaque; the codec knows how to decode it."""
    def extract(self, model_type: Any, markdown_text: str) -> dict[str, Any]: ...


default_codec: StoreCodec = RuntimeCodec()
