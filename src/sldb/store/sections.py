from __future__ import annotations
import re
from pathlib import Path
from sldb.store.io import load_documents_index, load_models_index, save_models_index, save_sections_index, load_store_index
from sldb.store.layout import sections_index_relpath
from sldb.store.models.doc_sections import DocSections
from sldb.store.models.section_context_record import SectionContextRecord
from sldb.store.models.sections_index import SectionsIndex
from sldb.store.semantic import RebuildReport, _about_terms, _slugify
import logging

logger = logging.getLogger(__name__)

def rebuild_sections_indexes(
    store_path: Path,
    project_root: Path,
    resolve_model_ref,
    pythonpath: str | None = None,
    report: RebuildReport | None = None,
) -> RebuildReport:
    """Build and persist section context index for all tracked documents."""
    if report is None:
        report = RebuildReport()
    store_index = load_store_index(store_path)

    for model_entry in store_index.models:
        model_type = resolve_model_ref(model_entry.model_ref, pythonpath)
        models_idx = load_models_index(project_root / model_entry.models_index)
        docs_idx = load_documents_index(project_root / models_idx.documents_index)
        doc_sections_list: list[DocSections] = []

        for doc in docs_idx.documents:
            doc_path = project_root / doc.path
            if not doc_path.exists():
                report.docs_skipped_missing += 1
                report.verbose.append(f"sections: {doc.name} — missing file {doc_path}")
                logger.warning(
                    "Sections rebuild: doc '%s' missing at %s", doc.name, doc_path
                )
                continue
            report.docs_processed += 1
            markdown = doc_path.read_text(encoding="utf-8")
            sections = _extract_sections(markdown)
            if not sections:
                report.docs_empty_sections += 1
                report.verbose.append(f"sections: {doc.name} — no headings found")
                logger.info("Sections rebuild: doc '%s' has no headings", doc.name)
            semantic_tags = list(doc.semantic_tags) if doc.semantic_tags else []
            section_records: list[SectionContextRecord] = []
            stack: list[tuple[int, list[str]]] = []

            for sec in sections:
                while stack and stack[-1][0] >= sec["level"]:
                    stack.pop()
                breadcrumbs = list(stack[-1][1]) if stack else []
                breadcrumbs.append(sec["title"])
                stack.append((sec["level"], breadcrumbs))
                if sec.get("line_start") is None:
                    report.headings_no_map += 1
                    report.verbose.append(
                        f"sections: {doc.name} — heading '{sec['title']}' has no line map"
                    )
                    logger.warning(
                        "Sections rebuild: heading '%s' in doc '%s' has no line map",
                        sec["title"],
                        doc.name,
                    )
                section_records.append(
                    SectionContextRecord(
                        path=sec["path"],
                        title=sec["title"],
                        breadcrumbs=breadcrumbs,
                        about=_about_terms(breadcrumbs, semantic_tags),
                        semantic_tags=semantic_tags,
                        slug=sec["slug"],
                        level=sec["level"],
                        line_start=sec.get("line_start"),
                        line_end=sec.get("line_end"),
                    )
                )
            doc_sections_list.append(
                DocSections(doc_name=doc.name, sections=section_records)
            )

        if doc_sections_list:
            sections_rel = sections_index_relpath(model_entry.name)
            sections_path = project_root / sections_rel
            save_sections_index(
                sections_path, SectionsIndex(documents=doc_sections_list)
            )
            models_idx.sections_index = sections_rel
            save_models_index(project_root / model_entry.models_index, models_idx)

    return report



def _extract_sections(markdown: str) -> list[dict]:
    """Extract section headings from markdown."""
    nodes = []
    from markdown_it import MarkdownIt
    from markdown_it.tree import SyntaxTreeNode

    md = MarkdownIt("gfm-like")
    tokens = md.parse(markdown)
    root = SyntaxTreeNode(tokens)
    for child in root.children:
        nodes.append(
            {
                "type": child.type,
                "tag": child.tag,
                "content": (
                    child.children[0].content if child.children else child.content
                )
                or "",
                "map": list(child.map) if child.map else None,
            }
        )

    sections: list[dict] = []
    stack: list[dict] = []
    for node in nodes:
        if not re.fullmatch(r"h[1-6]", node.get("tag") or ""):
            continue
        title = (node.get("content") or "").strip()
        if not title:
            continue
        level = int(node["tag"][1])
        while stack and stack[-1]["level"] >= level:
            stack.pop()
        slug = _slugify(title)
        parent = "/".join(item["slug"] for item in stack)
        path = f"{parent}/{slug}" if parent else slug
        map_vals = node.get("map") or [None, None]
        start = map_vals[0]
        end = map_vals[1]
        sec = {
            "title": title,
            "slug": slug,
            "level": level,
            "path": path,
            "line_start": start + 1 if start is not None else None,
            "line_end": end + 1 if end is not None else None,
        }
        sections.append(sec)
        stack.append(sec)
    return sections



def _extract_sections(markdown: str) -> list[dict]:
    """Extract section headings from markdown."""
    nodes = []
    from markdown_it import MarkdownIt
    from markdown_it.tree import SyntaxTreeNode

    md = MarkdownIt("gfm-like")
    tokens = md.parse(markdown)
    root = SyntaxTreeNode(tokens)
    for child in root.children:
        nodes.append(
            {
                "type": child.type,
                "tag": child.tag,
                "content": (
                    child.children[0].content if child.children else child.content
                )
                or "",
                "map": list(child.map) if child.map else None,
            }
        )

    sections: list[dict] = []
    stack: list[dict] = []
    for node in nodes:
        if not re.fullmatch(r"h[1-6]", node.get("tag") or ""):
            continue
        title = (node.get("content") or "").strip()
        if not title:
            continue
        level = int(node["tag"][1])
        while stack and stack[-1]["level"] >= level:
            stack.pop()
        slug = _slugify(title)
        parent = "/".join(item["slug"] for item in stack)
        path = f"{parent}/{slug}" if parent else slug
        map_vals = node.get("map") or [None, None]
        start = map_vals[0]
        end = map_vals[1]
        sec = {
            "title": title,
            "slug": slug,
            "level": level,
            "path": path,
            "line_start": start + 1 if start is not None else None,
            "line_end": end + 1 if end is not None else None,
        }
        sections.append(sec)
        stack.append(sec)
    return sections

