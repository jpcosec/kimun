import unittest
from sldb_cli.documents import SLDBDocument, DocumentFamily
from sldb_cli.cache import Cache

class TestDocuments(unittest.TestCase):
    def test_document_creation(self):
        doc = SLDBDocument("Hello world", source="test.md")
        self.assertEqual(doc.raw_text, "Hello world")
        
    def test_document_family(self):
        family = DocumentFamily("doc-123")
        doc1 = SLDBDocument("V1")
        doc2 = SLDBDocument("V2")
        family.add_version(doc1)
        family.add_version(doc2)
        
        self.assertEqual(family.get_latest().raw_text, "V2")

class TestCache(unittest.TestCase):
    def test_cache_operations(self):
        cache = Cache()
        cache.set("key1", "value1")
        self.assertEqual(cache.get("key1"), "value1")
        cache.invalidate("key1")
        self.assertIsNone(cache.get("key1"))

if __name__ == '__main__':
    unittest.main()
