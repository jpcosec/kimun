import re
from sldb.core.ast import AST_Handler
from sldb.cli.section_record_cls import SectionRecord
from .utils import _slugify

def extract_sections(markdown: str) -> list[SectionRecord]:
    nodes = AST_Handler().split_nodes(markdown)
    sections: list[SectionRecord] = []
    stack: list[SectionRecord] = []
    for node in nodes:
        _process_node(node, stack, sections)
    return sections

def _process_node(node, stack, sections):
    if not re.fullmatch(r"h[1-6]", node.tag or ""): return
    title = (node.find_leaf_text() or node.content or "").strip()
    if not title: return
    _build_section(node, title, stack, sections)

def _build_section(node, title, stack, sections):
    level = int(node.tag[1])
    while stack and stack[-1].level >= level: stack.pop()
    slug = _slugify(title)
    path = _build_path(stack, slug)
    _finalize_section(node, title, slug, level, path, stack, sections)

def _build_path(stack, slug):
    parent = "/".join(item.slug for item in stack)
    return f"{parent}/{slug}" if parent else slug

def _finalize_section(node, title, slug, level, path, stack, sections):
    start = node.map[0] + 1 if node.map else None
    end = node.map[1] + 1 if node.map else None
    section = SectionRecord(
        title=title, slug=slug, level=level,
        path=path, line_start=start, line_end=end,
    )
    sections.append(section)
    stack.append(section)
