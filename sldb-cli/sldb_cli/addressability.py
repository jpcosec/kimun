import sldb_ffi
from typing import List

class Addressability:
    """
    Handles generation of deterministic addresses, content hashing, 
    and history tracking.
    """
    
    @staticmethod
    def generate_hash(content: str) -> str:
        """
        Uses the rust core's blake3 hashing algorithm to generate deterministic IDs.
        """
        return sldb_ffi.hash_data(content)
        
    @staticmethod
    def construct_derived_address(parent_hash: str, block_type: str, index: int) -> str:
        """
        Constructs a derived address for a sub-block based on its parent's hash.
        """
        content = f"{parent_hash}::{block_type}::{index}"
        return Addressability.generate_hash(content)

    @staticmethod
    def get_history_chain(store, start_hash: str) -> List[str]:
        """
        Retrieves the chronological history chain for a document version 
        by walking back the 'previous_version' relations.
        """
        history = [start_hash]
        current = start_hash
        
        while True:
            edges = store.get_incoming_edges(current)
            prev = next((e.source_hash for e in edges if e.relation_type == "previous_version"), None)
            if not prev:
                break
            history.append(prev)
            current = prev
            
        return history
