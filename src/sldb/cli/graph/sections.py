import logging
import re
from sldb.core.ast import AST_Handler
from sldb.core.contracts import MARKER_PATTERN, parse_marker
from .section_record import SectionRecord
from .utils import _slugify

logger = logging.getLogger(__name__)

def extract_sections(markdown: str) -> list[SectionRecord]:
    sections, stack = [], []
    for node in (n for n in AST_Handler().split_nodes(markdown) if re.fullmatch(r"h[1-6]", n.tag or "")):
        if not (title := (node.find_leaf_text() or node.content or "").strip()): continue
        while stack and stack[-1].level >= int(node.tag[1]): stack.pop()
        slug = _slugify(title)
        path = f"{'/'.join(i.slug for i in stack)}/{slug}" if stack else slug
        sec = SectionRecord(title, slug, int(node.tag[1]), path, node.map[0] + 1 if node.map else None, node.map[1] + 1 if node.map else None)
        sections.append(sec); stack.append(sec)
    return sections

def _field_template_line_map(markdown: str, known_fields: set[str] | None = None) -> dict[str, int]:
    field_lines = {}
    for line_no, line in enumerate(markdown.split("\n"), 1):
        for m in re.finditer(MARKER_PATTERN, line):
            if (marker := parse_marker(m.group(1))).kind in ("rev", "optrev") and marker.name:
                if known_fields is not None and marker.name not in known_fields: logger.warning("Template marker '%s' references unknown field '%s'", m.group(0), marker.name)
                field_lines[marker.name] = line_no
    return field_lines

def _map_fields_to_sections(template: str, sections: list, known_fields: set[str] | None = None) -> dict[str, str]:
    field_lines = _field_template_line_map(template, known_fields=known_fields)
    heading_lines = sorted((s.line_start, s.path) for s in sections if s.line_start is not None)
    return {fp: owning for fp, lno in field_lines.items() if (owning := next((p for hl, p in reversed(heading_lines) if hl <= lno), None))}
