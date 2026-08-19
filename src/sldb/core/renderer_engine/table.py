from __future__ import annotations

import re
from typing import Any

from sldb.core.renderer_engine.base import BaseRenderer


class TableRenderer(BaseRenderer):
    """
    Handles rendering of table blocks.
    """

    def render(self, node, block_text: str, data: dict[str, Any]) -> str:
        lines = block_text.splitlines()
        if len(lines) < 3: return self.replace_markers(block_text, data)
        header, sep, row_tpl = lines[0], lines[1], lines[2]
        match = re.search(r"⸢([^⸥]+)⸥", row_tpl)
        if not match: return self.replace_markers(block_text, data)
        inner = match.group(1)
        root_prop = inner.split("•", 1)[-1].strip() if "•" in inner else inner.strip()
        items = self._get_items_to_render(data.get(root_prop, {}))
        return self._render_rows(header, sep, row_tpl, items, block_text, data)

    def _get_items_to_render(self, rows_data: Any) -> list:
        if isinstance(rows_data, list): return rows_data
        if isinstance(rows_data, dict):
            keys = sorted(rows_data.keys(), key=lambda x: int(x) if str(x).isdigit() else x)
            return [rows_data[k] for k in keys]
        return []

    def _render_rows(self, header: str, sep: str, row_tpl: str, items: list, block_text: str, data: dict[str, Any]) -> str:
        rendered_rows = []
        for item in items:
            item_data = item.model_dump() if hasattr(item, "model_dump") else item
            rendered_rows.append(self.replace_markers(row_tpl, item_data))
        if rendered_rows: return "\n".join([header, sep] + rendered_rows)
        return self.replace_markers(block_text, data)
