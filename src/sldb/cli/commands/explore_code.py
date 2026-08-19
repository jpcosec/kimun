import ast
import re
from pathlib import Path

from .explore_hit import ExploreHit

class ExploreCode:
    def search(self, term: str, code_root: Path, regex: bool) -> list[ExploreHit]:
        if not code_root.exists():
            return []
        hits: list[ExploreHit] = []
        for path in sorted(code_root.rglob("*.py")):
            hits.extend(self._scan_python_file(path, term, regex))
        return hits

    def _scan_python_file(self, path: Path, term: str, regex: bool) -> list[ExploreHit]:
        module = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
        hits = self._check_module_doc(module, path, term, regex)
        hits.extend(self._walk_docstring_nodes(module, path, term, regex))
        return hits

    def _check_module_doc(self, module: ast.AST, path: Path, term: str, regex: bool) -> list[ExploreHit]:
        module_doc = ast.get_docstring(module)
        if module_doc and self._matches(term, module_doc, regex):
            return [self._make_module_hit(path, module_doc)]
        return []

    def _make_module_hit(self, path: Path, doc: str) -> ExploreHit:
        return ExploreHit(
            source="docstrings",
            kind="module",
            path=str(path),
            anchor=path.stem,
            line=1,
            snippet=self._compact(doc),
        )

    def _walk_docstring_nodes(self, module: ast.AST, path: Path, term: str, regex: bool) -> list[ExploreHit]:
        hits: list[ExploreHit] = []
        for node in ast.walk(module):
            hit = self._check_node_doc(node, path, term, regex)
            if hit:
                hits.append(hit)
        return hits

    def _check_node_doc(self, node: ast.AST, path: Path, term: str, regex: bool) -> ExploreHit | None:
        if not isinstance(node, (ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef)):
            return None
        doc = ast.get_docstring(node)
        if not doc or not self._matches(term, doc, regex):
            return None
        return self._make_node_hit(node, path, doc)

    def _make_node_hit(self, node: ast.AST, path: Path, doc: str) -> ExploreHit:
        return ExploreHit(
            source="docstrings",
            kind="class" if isinstance(node, ast.ClassDef) else "function",
            path=str(path),
            anchor=node.name,
            line=getattr(node, "lineno", 1),
            snippet=self._compact(doc),
        )

    def _matches(self, term: str, haystack: str, regex: bool) -> bool:
        if regex:
            return re.search(term, haystack, flags=re.IGNORECASE) is not None
        return term.lower() in haystack.lower()

    def _compact(self, text: str) -> str:
        return " ".join(line.strip() for line in text.splitlines() if line.strip())
