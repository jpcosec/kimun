from __future__ import annotations

import re
from datetime import date
from datetime import datetime
from typing import Any

import yaml

from sldb.core.exceptions import SLDBASTError
from sldb.core.handlers.base import BaseNodeHandler
from sldb.core.handlers.utils import parse_marker
from sldb.core.node import SLDBNode

MARKER_PATTERN = r"⸢([^⸥]+)⸥"


class YamlNodeHandler(BaseNodeHandler):
    """
    Handler for extracting data from YAML, front-matter, and fence nodes.
    """

    def compile_recipe(self, node: SLDBNode) -> list[dict[str, Any]]:
        content = node.content.strip()
        matches = list(re.finditer(MARKER_PATTERN, content))
        if len(matches) == 1:
            marker = parse_marker(matches[0].group(1))
            if (marker.is_reversible or marker.is_optional) and "dict" in marker.traits:
                return [
                    {
                        "name": marker.name,
                        "marker": marker,
                        "handler": "yaml",
                    }
                ]

        field_markers = self._compile_field_markers(content)
        if field_markers:
            return [
                {
                    "markers": field_markers,
                    "handler": "yaml",
                }
            ]
        return []

    def _compile_field_markers(self, content: str) -> list[dict[str, Any]]:
        markers: list[dict[str, Any]] = []
        lines = content.splitlines()
        for idx, line in enumerate(lines):
            match = re.search(MARKER_PATTERN, line)
            if not match:
                continue

            marker = parse_marker(match.group(1))
            if not marker.is_reversible and not marker.is_optional:
                continue

            before_marker = line[: match.start()]
            key = self._inline_key(before_marker)
            if key is None and line.strip() == match.group(0):
                key = self._previous_mapping_key(lines, idx)
            if key is None:
                continue

            markers.append({"key": key, "marker": marker})
        return markers

    def _inline_key(self, text: str) -> str | None:
        match = re.match(r"^\s*([A-Za-z0-9_-]+)\s*:\s*$", text)
        if match:
            return match.group(1)
        return None

    def _previous_mapping_key(self, lines: list[str], start_idx: int) -> str | None:
        for idx in range(start_idx - 1, -1, -1):
            stripped = lines[idx].strip()
            if not stripped:
                continue
            match = re.match(r"^([A-Za-z0-9_-]+)\s*:\s*$", stripped)
            if match:
                return match.group(1)
            return None
        return None

    def extract_data(self, node: SLDBNode, recipe: dict[str, Any]) -> Any:
        if node.type not in ["fence", "front_matter"]:
            return None

        content = node.content.strip()
        try:
            clean_content = re.sub(MARKER_PATTERN, "", content).strip()
            if not clean_content:
                clean_content = content

            data = yaml.safe_load(clean_content)
            if "markers" in recipe:
                if not isinstance(data, dict):
                    return None
                return {
                    item["marker"].name: self._normalize_yaml_value(data.get(item["key"]))
                    for item in recipe["markers"]
                }
            return {recipe["name"]: data}
        except Exception as e:
            raise SLDBASTError(f"Failed to parse YAML content for {recipe['name']}") from e

    def _normalize_yaml_value(self, value: Any) -> Any:
        if isinstance(value, datetime):
            return value.isoformat()
        if isinstance(value, date):
            return value.isoformat()
        return value
