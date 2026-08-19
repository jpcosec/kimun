from __future__ import annotations
import re
from pathlib import Path
from ontology.contracts.facets import ASTFacet, ComplianceFacet, SemanticFacet
from kgdb.contracts.node import KnowledgeNode
from kgdb.contracts.base import SystemIdentity
from .ignore_rule import IgnoreRule
from .python_scanner import PythonScanner
from .typescript_scanner import TypeScriptScanner
from wiki_compiler.protocols import ScannerPlugin

IO_LINE_RE = re.compile(r"^(?:input|inputs|output|outputs|i/o):\s*(?P<medium>memory|disk|network)\s*\|\s*(?P<path>[^|]+?)\s*(?:\|\s*(?P<schema>.+))?$", re.IGNORECASE)

def scan_codebase(project_root: Path, source_roots: list[Path] | None = None, wikiignore_path: Path | None = None, plugins: list[ScannerPlugin] | None = None) -> list[KnowledgeNode]:
    roots = source_roots or [project_root / "src"]
    ignore_rules = load_wikiignore_rules(wikiignore_path or project_root / ".wikiignore")
    plugins = plugins or [PythonScanner(), TypeScriptScanner()]
    nodes: list[KnowledgeNode] = []
    extension_map = {ext: plugin for plugin in plugins for ext in plugin.supported_extensions}
    for root in roots:
        if not root.exists(): continue
        _scan_root(root, project_root, ignore_rules, extension_map, nodes)
    return nodes

def _scan_root(root: Path, project_root: Path, ignore_rules: list[IgnoreRule], extension_map: dict, nodes: list[KnowledgeNode]) -> None:
    for file_path in sorted(p for p in root.rglob("*") if p.is_file()):
        if any(part.startswith(".") for part in file_path.parts): continue
        rel_path = file_path.relative_to(project_root).as_posix()
        reason = match_ignore_reason(rel_path, ignore_rules)
        if reason is not None:
            nodes.append(build_ignored_file_node(rel_path, reason))
            continue
        plugin = extension_map.get(file_path.suffix)
        if plugin: nodes.extend(plugin.scan(file_path, project_root))

def scan_python_sources(project_root: Path, source_roots: list[Path] | None = None, wikiignore_path: Path | None = None) -> list[KnowledgeNode]:
    return scan_codebase(project_root, source_roots, wikiignore_path, [PythonScanner()])

def load_wikiignore_rules(wikiignore_path: Path) -> list[IgnoreRule]:
    if not wikiignore_path.exists(): return []
    rules = []
    for raw_line in wikiignore_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"): continue
        pattern, _, comment = line.partition(" #")
        rules.append(IgnoreRule(pattern=pattern.strip(), reason=comment.strip() or None))
    return rules

def match_ignore_reason(path: str, rules: list[IgnoreRule]) -> str | None:
    for rule in rules:
        if rule.matches(path): return rule.reason or "Ignored by .wikiignore"
    return None

def build_ignored_file_node(rel_path: str, reason: str) -> KnowledgeNode:
    return KnowledgeNode(
        identity=SystemIdentity(node_id=f"file:{rel_path}", node_type="file"),
        semantics=SemanticFacet(intent=f"Ignored source file `{rel_path}`.", raw_docstring=None),
        ast=ASTFacet(construct_type="script", signatures=[f"module {rel_path}"], dependencies=[]),
        compliance=ComplianceFacet(status="exempt", exemption_reason=reason, failing_standards=[]),
    )
