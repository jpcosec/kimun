from sldb_cli.store import Store
from sldb_cli.links import LinkResolver
from sldb_cli.documents import SLDBDocument

class DocumentComposer:
    """
    Handles composition (transclusion) of multiple documents or document fragments
    into a single cohesive representation.
    """
    def __init__(self, store: Store):
        self.store = store
        self.resolver = LinkResolver(store)

    def compose(self, root_hash: str) -> str:
        """
        Recursively resolves transclusions (e.g. ![transclude:: [[target]]])
        and builds a single composed markdown string.
        """
        node = self.store.get_node(root_hash)
        if not node or not node.payload:
            return ""

        composed_text = node.payload
        # In a real implementation, we would parse the AST, detect transclusions,
        # fetch the target nodes via self.store, and replace the transclusion 
        # tags with the actual payload of the targets.
        
        # This is a scaffold for the composition engine.
        return composed_text

    def resolve_anchor(self, anchor_path: str) -> str:
        """
        Resolves `#anchor` notation by extracting the target hash 
        and traversing the document's AST headers.
        """
        # Scaffold logic for anchor resolution
        if "#" not in anchor_path:
            return anchor_path
            
        doc_hash, anchor_id = anchor_path.split("#", 1)
        resolved = self.resolver.resolve_anchor(doc_hash, anchor_id)
        return resolved if resolved else doc_hash
