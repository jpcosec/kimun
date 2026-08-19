from abc import ABC, abstractmethod
from typing import List
from sldb.core.node import SLDBNode

class BaseASTHandler(ABC):
    """Abstract base class for parsing content into a unified SLDBNode tree."""
    @abstractmethod
    def split_nodes(self, raw_content: str) -> List[SLDBNode]:
        pass
