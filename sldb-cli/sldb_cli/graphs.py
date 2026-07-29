from typing import List, Dict
from sldb_cli.store import Store
from sldb_cli.documents import SLDBDocument

class SLDBGraph:
    def __init__(self, store: Store):
        self.store = store

    def get_neighbors(self, node_hash: str) -> Dict[str, List[dict]]:
        """
        Retrieves all neighbors (incoming and outgoing) for a specific node in the graph.
        Returns a dictionary categorizing edges by relation_type.
        """
        outgoing = self.store.get_outgoing_edges(node_hash)
        incoming = self.store.get_incoming_edges(node_hash)
        
        relations = {"outgoing": {}, "incoming": {}}
        
        for edge in outgoing:
            rel_type = edge.relation_type
            if rel_type not in relations["outgoing"]:
                relations["outgoing"][rel_type] = []
            relations["outgoing"][rel_type].append(edge.target_hash)

        for edge in incoming:
            rel_type = edge.relation_type
            if rel_type not in relations["incoming"]:
                relations["incoming"][rel_type] = []
            relations["incoming"][rel_type].append(edge.source_hash)

        return relations

    def trace_lineage(self, start_hash: str, max_depth: int = 10) -> List[str]:
        """
        Traces the lineage of a document family or history chain via 'derived_from' relations.
        """
        lineage = [start_hash]
        current_hash = start_hash
        depth = 0
        
        while depth < max_depth:
            incoming = self.store.get_incoming_edges(current_hash)
            # Find the predecessor in the lineage chain
            predecessor = next((e.source_hash for e in incoming if e.relation_type == "derived_from"), None)
            
            if not predecessor:
                break
                
            lineage.append(predecessor)
            current_hash = predecessor
            depth += 1
            
        return lineage
