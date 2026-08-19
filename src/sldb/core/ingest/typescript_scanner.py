from __future__ import annotations
from pathlib import Path
from .contracts import KnowledgeNode, SystemIdentity, SemanticFacet, ComplianceFacet

class TypeScriptScanner:
    @property
    def supported_extensions(self) -> set[str]: return {".ts", ".tsx"}

    def scan(self, path: Path, project_root: Path) -> list[KnowledgeNode]:
        rel_path = path.relative_to(project_root).as_posix()
        return [
            KnowledgeNode(
                identity=SystemIdentity(node_id=f"file:{rel_path}", node_type="file"),
                semantics=SemanticFacet(intent=f"TypeScript source file `{rel_path}`.", raw_docstring=None),
                compliance=ComplianceFacet(status="implemented", failing_standards=[]),
            )
        ]
