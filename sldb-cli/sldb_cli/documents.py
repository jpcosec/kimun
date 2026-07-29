class SLDBDocument:
    def __init__(self, raw_text: str, source: str = None):
        self.raw_text = raw_text
        self.source = source
        self.ast = None
        self.hash = None
        
    def set_ast(self, ast: list):
        self.ast = ast

    def set_hash(self, doc_hash: str):
        self.hash = doc_hash

class DocumentFamily:
    def __init__(self, canonical_id: str):
        self.canonical_id = canonical_id
        self.versions = []
        
    def add_version(self, doc: SLDBDocument):
        self.versions.append(doc)
        
    def get_latest(self) -> SLDBDocument:
        if not self.versions:
            return None
        return self.versions[-1]
