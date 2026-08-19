import logging
import re
from sldb.core.contracts import MARKER_PATTERN, parse_marker

logger = logging.getLogger(__name__)

def _field_template_line_map(markdown: str, known_fields: set[str] | None = None) -> dict[str, int]:
    field_lines: dict[str, int] = {}
    for line_no, line in enumerate(markdown.split("\n"), 1):
        for match in re.finditer(MARKER_PATTERN, line):
            _process_marker(match, line_no, known_fields, field_lines)
    return field_lines

def _process_marker(match, line_no, known_fields, field_lines):
    marker = parse_marker(match.group(1))
    if marker.kind not in ("rev", "optrev") or not marker.name: return
    if known_fields is not None and marker.name not in known_fields:
        logger.warning("Template marker '%s' references unknown field '%s'", match.group(0), marker.name)
    field_lines[marker.name] = line_no

def _map_fields_to_sections(template: str, sections: list, known_fields: set[str] | None = None) -> dict[str, str]:
    field_lines = _field_template_line_map(template, known_fields=known_fields)
    heading_lines = sorted((s.line_start, s.path) for s in sections if s.line_start is not None)
    result: dict[str, str] = {}
    for field_path, line_no in field_lines.items():
        _assign_owning(field_path, line_no, heading_lines, result)
    return result

def _assign_owning(field_path, line_no, heading_lines, result):
    owning = None
    for heading_line, section_path in heading_lines:
        if heading_line <= line_no: owning = section_path
    if owning is not None: result[field_path] = owning
