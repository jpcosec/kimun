from __future__ import annotations
import re
from datetime import date, datetime
from typing import Any
import yaml
from sldb.core.exceptions import SLDBASTError
from sldb.core.handlers.base import BaseNodeHandler
from sldb.core.handlers.utils import parse_marker
from sldb.core.node import SLDBNode

MARKER_PATTERN = r"⸢([^⸥]+)⸥"

class YamlNodeHandler(BaseNodeHandler):
    def compile_recipe(self, node: SLDBNode) -> list[dict[str, Any]]:
        content = node.content.strip()
        if single_recipe := self._try_single_marker(content):
            return single_recipe
        if field_markers := self._compile_field_markers(content):
            return [{"markers": field_markers, "handler": "yaml"}]
        return []

    def _try_single_marker(self, content: str) -> list[dict[str, Any]] | None:
        matches = list(re.finditer(MARKER_PATTERN, content))
        if len(matches) == 1:
            marker = parse_marker(matches[0].group(1))
            if (marker.is_reversible or marker.is_optional) and "dict" in marker.traits:
                return [{"name": marker.name, "marker": marker, "handler": "yaml"}]
        return None

    def _compile_field_markers(self, content: str) -> list[dict[str, Any]]:
        markers, lines = [], content.splitlines()
        for idx, line in enumerate(lines):
            self._process_yaml_line(line, lines, idx, markers)
        return markers

    def _process_yaml_line(self, line: str, lines: list[str], idx: int, markers: list[dict[str, Any]]) -> None:
        if not (match := re.search(MARKER_PATTERN, line)):
            return
        marker = parse_marker(match.group(1))
        if not marker.is_reversible and not marker.is_optional:
            return
        if (key := self._extract_yaml_key(line, match, lines, idx)) is not None:
            markers.append({"key": key, "marker": marker})

    def _extract_yaml_key(self, line: str, match: re.Match, lines: list[str], idx: int) -> str | None:
        key = self._inline_key(line[: match.start()])
        if key is None and line.strip() == match.group(0):
            key = self._previous_mapping_key(lines, idx)
        return key

    def _inline_key(self, text: str) -> str | None:
        return match.group(1) if (match := re.match(r"^\s*([A-Za-z0-9_-]+)\s*:\s*$", text)) else None

    def _previous_mapping_key(self, lines: list[str], start_idx: int) -> str | None:
        for idx in range(start_idx - 1, -1, -1):
            if stripped := lines[idx].strip():
                return match.group(1) if (match := re.match(r"^([A-Za-z0-9_-]+)\s*:\s*$", stripped)) else None
        return None

    def extract_data(self, node: SLDBNode, recipe: dict[str, Any]) -> Any:
        if node.type not in ["fence", "front_matter"]:
            return None
        try:
            return self._parse_and_extract_yaml(node.content.strip(), recipe)
        except Exception as e:
            raise SLDBASTError(f"Failed to parse YAML content for {recipe['name']}") from e

    def _parse_and_extract_yaml(self, content: str, recipe: dict[str, Any]) -> Any:
        clean_content = re.sub(MARKER_PATTERN, "", content).strip() or content
        data = yaml.safe_load(clean_content)
        if "markers" in recipe:
            if not isinstance(data, dict):
                return None
            return {i["marker"].name: self._normalize_yaml_value(data.get(i["key"])) for i in recipe["markers"]}
        return {recipe["name"]: data}

    def _normalize_yaml_value(self, value: Any) -> Any:
        if isinstance(value, (datetime, date)):
            return value.isoformat()
        return value
