from pathlib import Path
import re

from .explore_hit import ExploreHit

class ExploreDocs:
    def search(self, term: str, docs_root: Path, regex: bool) -> list[ExploreHit]:
        roots = self._get_doc_roots(docs_root)
        hits: list[ExploreHit] = []
        for root in roots:
            hits.extend(self._scan_root(root, term, regex))
        return hits

    def _get_doc_roots(self, docs_root: Path) -> list[Path]:
        roots = [docs_root]
        if docs_root == Path("docs"):
            roots.extend([Path("README.md"), Path(".sldb/README.md")])
        return roots

    def _scan_root(self, root: Path, term: str, regex: bool) -> list[ExploreHit]:
        if root.is_file():
            return self._scan_markdown_file(root, term, regex)
        if not root.exists():
            return []
        hits: list[ExploreHit] = []
        for path in sorted(root.rglob("*.md")):
            hits.extend(self._scan_markdown_file(path, term, regex))
        return hits

    def _scan_markdown_file(self, path: Path, term: str, regex: bool) -> list[ExploreHit]:
        lines = path.read_text(encoding="utf-8").splitlines()
        hits: list[ExploreHit] = []
        heading = path.stem
        for line_no, line in enumerate(lines, start=1):
            heading = self._update_heading(line, heading)
            if self._matches(term, line, regex):
                hits.append(self._make_doc_hit(path, heading, line_no, line.strip()))
        return hits

    def _update_heading(self, line: str, heading: str) -> str:
        stripped = line.strip()
        if stripped.startswith("#"):
            return stripped.lstrip("#").strip() or heading
        return heading

    def _make_doc_hit(self, path: Path, heading: str, line_no: int, snippet: str) -> ExploreHit:
        return ExploreHit(
            source="docs",
            kind="markdown",
            path=str(path),
            anchor=heading,
            line=line_no,
            snippet=snippet,
        )

    def _matches(self, term: str, haystack: str, regex: bool) -> bool:
        if regex:
            return re.search(term, haystack, flags=re.IGNORECASE) is not None
        return term.lower() in haystack.lower()
