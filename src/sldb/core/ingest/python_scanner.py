from __future__ import annotations
from pathlib import Path
from wiki_compiler.adapters.ontology_facets import scan_python_file
from .contracts import KnowledgeNode

class PythonScanner:
    @property
    def supported_extensions(self) -> set[str]: return {".py"}

    def scan(self, path: Path, project_root: Path) -> list[KnowledgeNode]:
        return scan_python_file(project_root, path)
