from sldb_cli.store import Store
from typing import Optional, List

class LinkResolver:
    """
    Resolves [link:: [[target]]] and contextual anchors within the SLDB graph.
    """
    def __init__(self, store: Store):
        self.store = store

    def resolve_anchor(self, node_hash: str, anchor_id: str) -> Optional[str]:
        """
        Attempts to resolve an internal anchor to a specific chunk hash if the AST 
        supports granular targeting.
        """
        node = self.store.get_node(node_hash)
        if not node:
            return None
        
        # In a fully integrated AST, we would query the tree-sitter or rowan AST 
        # for a heading or anchor ID. For now, we return the parent node hash.
        return node.hash

    def extract_outgoing_links(self, document_ast: list) -> List[str]:
        """
        Given a parsed markdown AST, extracts all the link targets.
        """
        targets = []
        for token in document_ast:
            if token.get("type") == "inline":
                content = token.get("content", "")
                if "::" in content and "[[" in content:
                    # Simple heuristic extraction for SLDB typed links: [type:: [[target]]]
                    start = content.find("[[") + 2
                    end = content.find("]]", start)
                    if start != -1 and end != -1:
                        targets.append(content[start:end])
        return targets
