from typing import Any, Dict
from sldb_cli.documents import SLDBDocument
import os

class Materializer:
    """
    Handles materialization of SLDB documents back into filesystem representations
    and tracks provenance metadata.
    """
    def __init__(self, base_dir: str):
        self.base_dir = base_dir

    def materialize(self, doc: SLDBDocument, path: str):
        """
        Materializes a document into a raw file at the specified path.
        """
        full_path = os.path.join(self.base_dir, path)
        os.makedirs(os.path.dirname(full_path), exist_ok=True)
        
        with open(full_path, 'w', encoding='utf-8') as f:
            f.write(doc.raw_text)

    def extract_provenance(self, frontmatter_dict: Dict[str, Any]) -> str:
        """
        Extracts the provenance tracing (source origin) from the frontmatter.
        """
        return frontmatter_dict.get("provenance", "unknown")

    def attach_provenance(self, frontmatter_dict: Dict[str, Any], origin: str):
        """
        Attaches a provenance metadata source to a document's frontmatter.
        """
        frontmatter_dict["provenance"] = origin
