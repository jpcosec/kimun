from __future__ import annotations

import ast
import json
import re
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

import yaml


@dataclass
class ExploreHit:
    source: str
    kind: str
    path: str
    anchor: str
    line: int
    snippet: str


class ExploreCLI:
    """Search repo docs and Python docstrings."""

    def run(self, args: Any) -> int:
        hits: list[ExploreHit] = []
        if args.source in {"all", "docs"}:
            hits.extend(self._search_docs(args.term, Path(args.docs_root), args.regex))
        if args.source in {"all", "docstrings"}:
            hits.extend(
                self._search_docstrings(args.term, Path(args.code_root), args.regex)
            )

        hits = hits[: args.max_results]
        payload = [asdict(hit) for hit in hits]
        if args.format == "json":
            print(json.dumps({"results": payload}, indent=2))
        elif args.format == "yaml":
            print(yaml.safe_dump({"results": payload}, sort_keys=False, allow_unicode=True))
        else:
            for hit in payload:
                print(self._format_text(hit))
        return 0 if payload else 1

    def _search_docs(self, term: str, docs_root: Path, regex: bool) -> list[ExploreHit]:
        roots = [docs_root]
        if docs_root == Path("docs"):
            roots.extend([Path("README.md"), Path(".sldb/README.md")])
        hits: list[ExploreHit] = []
        for root in roots:
            if root.is_file():
                hits.extend(self._scan_markdown_file(root, term, regex))
                continue
            if not root.exists():
                continue
            for path in sorted(root.rglob("*.md")):
                hits.extend(self._scan_markdown_file(path, term, regex))
        return hits

    def _scan_markdown_file(self, path: Path, term: str, regex: bool) -> list[ExploreHit]:
        lines = path.read_text(encoding="utf-8").splitlines()
        heading = path.stem
        hits: list[ExploreHit] = []
        for line_no, line in enumerate(lines, start=1):
            stripped = line.strip()
            if stripped.startswith("#"):
                heading = stripped.lstrip("#").strip() or heading
            if self._matches(term, line, regex):
                hits.append(
                    ExploreHit(
                        source="docs",
                        kind="markdown",
                        path=str(path),
                        anchor=heading,
                        line=line_no,
                        snippet=stripped,
                    )
                )
        return hits

    def _search_docstrings(
        self, term: str, code_root: Path, regex: bool
    ) -> list[ExploreHit]:
        if not code_root.exists():
            return []
        hits: list[ExploreHit] = []
        for path in sorted(code_root.rglob("*.py")):
            module = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
            module_doc = ast.get_docstring(module)
            if module_doc and self._matches(term, module_doc, regex):
                hits.append(
                    ExploreHit(
                        source="docstrings",
                        kind="module",
                        path=str(path),
                        anchor=path.stem,
                        line=1,
                        snippet=self._compact(module_doc),
                    )
                )
            hits.extend(self._walk_docstring_nodes(module, path, term, regex))
        return hits

    def _walk_docstring_nodes(
        self, module: ast.AST, path: Path, term: str, regex: bool
    ) -> list[ExploreHit]:
        hits: list[ExploreHit] = []
        for node in ast.walk(module):
            if not isinstance(node, (ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef)):
                continue
            doc = ast.get_docstring(node)
            if not doc or not self._matches(term, doc, regex):
                continue
            hits.append(
                ExploreHit(
                    source="docstrings",
                    kind="class" if isinstance(node, ast.ClassDef) else "function",
                    path=str(path),
                    anchor=node.name,
                    line=getattr(node, "lineno", 1),
                    snippet=self._compact(doc),
                )
            )
        return hits

    def _matches(self, term: str, haystack: str, regex: bool) -> bool:
        if regex:
            return re.search(term, haystack, flags=re.IGNORECASE) is not None
        return term.lower() in haystack.lower()

    def _compact(self, text: str) -> str:
        return " ".join(line.strip() for line in text.splitlines() if line.strip())

    def _format_text(self, hit: dict[str, Any]) -> str:
        return (
            f"{hit['source']}:{hit['kind']} | {hit['path']}:{hit['line']} | "
            f"{hit['anchor']} | {hit['snippet']}"
        )
