from __future__ import annotations

import re
from typing import Any

import yaml
from jinja2 import Environment

from sldb.core.handlers.utils import parse_marker
from sldb.core.renderer_engine.python_expr import PythonExpressionRenderer


class BaseRenderer:
    """
    Base class for rendering components.
    """

    def __init__(self, jinja_env: Environment):
        self.jinja_env = jinja_env
        self.py_renderer = PythonExpressionRenderer()

    def replace_markers(self, text: str, data: dict[str, Any]) -> str:
        """
        Replaces ⸢...⸥ markers in the provided text.
        """

        def sub_marker(match):
            marker = parse_marker(match.group(1))

            if marker.kind == "py":
                return self.py_renderer.render(marker.name, data, match.group(0))

            val = data.get(marker.name)
            if val is None:
                if marker.is_optional or marker.kind == "render":
                    return ""
                return match.group(0)

            if self._is_table_marker(marker):
                return self._render_markdown_table(val, self._table_columns(marker, val))

            if "dict" in marker.traits or isinstance(val, (dict, list)):
                if "list" in marker.traits and not isinstance(val, (dict, list)):
                    return str(val)
                return yaml.dump(val, allow_unicode=True, sort_keys=False).strip()

            return str(val)

        rendered = re.sub(r"⸢([^⸥]+)⸥", sub_marker, text)
        return self.jinja_env.from_string(rendered).render(**data)

    def _is_table_marker(self, marker: Any) -> bool:
        return any(trait == "table" or trait.startswith("table[") for trait in marker.traits)

    def _table_columns(self, marker: Any, value: Any) -> list[str]:
        for trait in marker.traits:
            if trait.startswith("table[") and trait.endswith("]"):
                return [col.strip() for col in trait[6:-1].split(",") if col.strip()]
        if isinstance(value, list) and value:
            first = value[0].model_dump() if hasattr(value[0], "model_dump") else value[0]
            if isinstance(first, dict):
                return [str(key) for key in first.keys()]
        return []

    def _render_markdown_table(self, value: Any, columns: list[str]) -> str:
        rows = value if isinstance(value, list) else []
        if not columns:
            return "| |\n| --- |"

        header = "| " + " | ".join(columns) + " |"
        separator = "| " + " | ".join("---" for _ in columns) + " |"
        rendered_rows = []
        for row in rows:
            row_data = row.model_dump() if hasattr(row, "model_dump") else row
            if not isinstance(row_data, dict):
                row_data = {}
            cells = [self._format_table_cell(row_data.get(column, "")) for column in columns]
            rendered_rows.append("| " + " | ".join(cells) + " |")
        return "\n".join([header, separator] + rendered_rows)

    def _format_table_cell(self, value: Any) -> str:
        if value is None:
            return ""
        return str(value).replace("\n", " ").replace("|", "\\|")

    def get_node_source(self, node, block_text: str, block_start_line: int) -> str:
        """Helper to get node source from block text."""
        lines = block_text.splitlines()
        start = node.map[0] - block_start_line
        end = node.map[1] - block_start_line
        return "\n".join(lines[start:end])
