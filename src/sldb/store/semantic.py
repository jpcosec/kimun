from __future__ import annotations

import logging
import re
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

from sldb.runtime.validation import extract_model_data
from sldb.store.io import (
    load_documents_index,
    load_models_index,
    load_sections_index,
    load_semantic_dag,
    load_store_index,
    save_documents_index,
    save_models_index,
    save_sections_index,
    save_semantic_dag,
    save_semantic_index,
)
from sldb.store.layout import sections_index_relpath
from sldb.store.models import (
    DocSections,
    SectionContextRecord,
    SectionsIndex,
    SemanticDAG,
    SemanticDocumentRecord,
    SemanticIndex,
    SemanticNode,
)
from sldb.store.semantic_tags import collect_document_semantic_tags, _prefix_edges

logger = logging.getLogger(__name__)


@dataclass
class RebuildReport:
    docs_processed: int = 0
    docs_skipped_missing: int = 0
    docs_empty_sections: int = 0
    headings_no_map: int = 0
    verbose: list[str] = field(default_factory=list)


def _process_doc(doc, doc_path, model_type, m_name, report):
    report.docs_processed += 1
    try: payload = extract_model_data(model_type, doc_path.read_text(encoding="utf-8"))
    except Exception: payload = {}
    doc.semantic_tags = collect_document_semantic_tags(model_type, payload)
    return SemanticDocumentRecord(model=m_name, path=doc.path, tags=doc.semantic_tags)

def _process_model_semantics(m_entry, root, resolver, py_path, report, docs_dict, t_to_d, p_by_n):
    m_idx = load_models_index(root / m_entry.models_index)
    d_idx = load_documents_index(root / m_idx.documents_index)
    for doc in d_idx.documents:
        if not (d_path := root / doc.path).exists():
            report.docs_skipped_missing += 1; continue
        docs_dict[doc.name] = _process_doc(doc, d_path, resolver(m_entry.model_ref, py_path), m_entry.name, report)
        for tag in doc.semantic_tags:
            t_to_d[tag].append(doc.name)
            for p, c in _prefix_edges(tag): p_by_n[c].add(p); p_by_n.setdefault(p, set())
    save_documents_index(root / m_idx.documents_index, d_idx)

def rebuild_semantic_indexes(store_path: Path, project_root: Path, resolve_model_ref, pythonpath: str | None = None, report: RebuildReport | None = None) -> RebuildReport:
    report = report or RebuildReport()
    d_dict, t_to_d, p_by_n, existing = {}, defaultdict(list), defaultdict(set), load_semantic_dag(store_path)
    for m in load_store_index(store_path).models: _process_model_semantics(m, project_root, resolve_model_ref, pythonpath, report, d_dict, t_to_d, p_by_n)
    for c, parents in existing.equivalences.items():
        p_by_n.setdefault(c, set())
        for p in parents: p_by_n[c].add(p); p_by_n.setdefault(p, set())
    _save_indexes(store_path, p_by_n, existing.equivalences, t_to_d, d_dict)
    return report


def _save_indexes(s_path, p_by_n, equiv, t_to_d, docs):
    nodes = [SemanticNode(id=n_id, parents=sorted(p)) for n_id, p in sorted(p_by_n.items())]
    save_semantic_dag(s_path, SemanticDAG(nodes=nodes, equivalences=equiv))
    tags = {t: sorted(d) for t, d in sorted(t_to_d.items())}
    save_semantic_index(s_path, SemanticIndex(tags=tags, documents=docs))


def _about_terms(breadcrumbs: list[str], semantic_tags: list[str]) -> list[str]:
    seen, terms = set(), []
    for b in breadcrumbs:
        if (r := b.strip()) and r not in seen: seen.add(r); terms.append(r)
        if (n := _slugify(r).replace("-", " ").strip()) and n not in seen: seen.add(n); terms.append(n)
    for tag in semantic_tags:
        if tag not in seen: seen.add(tag); terms.append(tag)
    return terms


def _process_doc_sections(doc, d_path, report):
    report.docs_processed += 1
    if not (secs := _extract_sections(d_path.read_text(encoding="utf-8"))): report.docs_empty_sections += 1
    tags, records, stack = list(doc.semantic_tags or []), [], []
    for s in secs:
        while stack and stack[-1][0] >= s["level"]: stack.pop()
        b_crumbs = list(stack[-1][1]) if stack else []
        b_crumbs.append(s["title"]); stack.append((s["level"], b_crumbs))
        if s.get("line_start") is None: report.headings_no_map += 1
        records.append(SectionContextRecord(path=s["path"], title=s["title"], breadcrumbs=b_crumbs, about=_about_terms(b_crumbs, tags), semantic_tags=tags, slug=s["slug"], level=s["level"], line_start=s.get("line_start"), line_end=s.get("line_end")))
    return DocSections(doc_name=doc.name, sections=records)

def _process_model_sections(m_entry, root, report):
    m_idx = load_models_index(root / m_entry.models_index)
    d_sections = []
    for doc in load_documents_index(root / m_idx.documents_index).documents:
        d_path = root / doc.path
        if not d_path.exists(): report.docs_skipped_missing += 1; report.verbose.append(f"sections: {doc.name} — missing file {d_path}"); logger.warning(f"Sections rebuild: doc '{doc.name}' missing at {d_path}"); continue
        d_sections.append(_process_doc_sections(doc, d_path, report))
    if d_sections:
        s_rel = sections_index_relpath(m_entry.name)
        save_sections_index(root / s_rel, SectionsIndex(documents=d_sections))
        m_idx.sections_index = s_rel; save_models_index(root / m_entry.models_index, m_idx)

def rebuild_sections_indexes(store_path: Path, project_root: Path, resolve_model_ref, pythonpath: str | None = None, report: RebuildReport | None = None) -> RebuildReport:
    report = report or RebuildReport()
    for m in load_store_index(store_path).models: _process_model_sections(m, project_root, report)
    return report


def _parse_md_nodes(markdown: str):
    from markdown_it import MarkdownIt
    from markdown_it.tree import SyntaxTreeNode
    return [{"type": c.type, "tag": c.tag, "content": (c.children[0].content if c.children else c.content) or "", "map": list(c.map) if c.map else None} for c in SyntaxTreeNode(MarkdownIt("gfm-like").parse(markdown)).children]

def _build_section(node, stack):
    title = (node.get("content") or "").strip()
    if not title: return None
    level = int(node["tag"][1])
    while stack and stack[-1]["level"] >= level: stack.pop()
    slug, parent = _slugify(title), "/".join(i["slug"] for i in stack)
    m_vals = node.get("map") or [None, None]
    return {"title": title, "slug": slug, "level": level, "path": f"{parent}/{slug}" if parent else slug, "line_start": m_vals[0] + 1 if m_vals[0] is not None else None, "line_end": m_vals[1] + 1 if m_vals[1] is not None else None}

def _extract_sections(markdown: str) -> list[dict]:
    sections, stack = [], []
    for node in _parse_md_nodes(markdown):
        if not re.fullmatch(r"h[1-6]", node.get("tag") or ""): continue
        sec = _build_section(node, stack)
        if sec: sections.append(sec); stack.append(sec)
    return sections


def _slugify(text: str) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")
    return slug or "section"


def add_semantic_equivalence(store_path: Path, local_tag: str, global_tag: str) -> None:
    """Adds a semantic equivalence mapping to the store DAG."""
    dag = load_semantic_dag(store_path)
    mapped = set(dag.equivalences.get(local_tag, []))
    mapped.add(global_tag)
    dag.equivalences[local_tag] = sorted(mapped)
    save_semantic_dag(store_path, dag)
