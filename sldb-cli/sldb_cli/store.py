import sldb_ffi

class Store:
    def __init__(self, path: str):
        self._store = sldb_ffi.SldbStore(path)
    
    def get_node(self, hash: str):
        return self._store.get_node(hash)
    
    def get_edge(self, hash: str):
        return self._store.get_edge(hash)
    
    def get_outgoing_edges(self, source_hash: str):
        return self._store.get_outgoing_edges(source_hash)

    def get_incoming_edges(self, target_hash: str):
        return self._store.get_incoming_edges(target_hash)

    def search_nodes_by_payload(self, query: str):
        return self._store.search_nodes_by_payload(query)
