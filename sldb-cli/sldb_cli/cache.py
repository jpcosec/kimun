from typing import Any, Optional

class Cache:
    """
    In-memory cache for fast lookups of nodes, graph lineages, 
    and document versions during runtime execution.
    """
    def __init__(self):
        self._store = {}
        
    def get(self, key: str) -> Optional[Any]:
        return self._store.get(key)
        
    def set(self, key: str, value: Any):
        self._store[key] = value
        
    def invalidate(self, key: str):
        if key in self._store:
            del self._store[key]
            
    def clear(self):
        self._store.clear()
