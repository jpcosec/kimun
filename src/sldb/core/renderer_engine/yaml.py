from __future__ import annotations

import re
from typing import Any

import yaml

from sldb.core.renderer_engine.base import BaseRenderer
from sldb.core.handlers.utils import parse_marker


class YamlRenderer(BaseRenderer):
    """
    Handles rendering of YAML, front-matter, and fence blocks.
    """

    def render(self, node, block_text: str, data: dict[str, Any]) -> str:
        is_front_matter = node.type == "front_matter"
        if is_front_matter:
            front_matter_body = re.sub(r"^---\n?", "", block_text.strip())
            front_matter_body = re.sub(r"\n?---$", "", front_matter_body)
            content = self._render_frontmatter_body(front_matter_body, data)
            return f"---\n{content}\n---"
        content = self.replace_markers(block_text, data)
        return content

    def _render_frontmatter_body(self, body: str, data: dict[str, Any]) -> str:
        rendered_lines: list[str] = []
        inline_field_pattern = re.compile(r"^\s*([A-Za-z0-9_-]+)\s*:\s*⸢([^⸥]+)⸥\s*$")
        for line in body.splitlines():
            match = inline_field_pattern.match(line)
            if not match:
                rendered_lines.append(self.replace_markers(line, data))
                continue

            key = match.group(1)
            marker = parse_marker(match.group(2))
            value = data.get(marker.name)
            if value is None and (marker.is_optional or marker.kind == "render"):
                value = None
            elif value is None:
                rendered_lines.append(self.replace_markers(line, data))
                continue

            rendered_lines.append(
                yaml.safe_dump({key: value}, allow_unicode=True, sort_keys=False).strip()
            )
        return "\n".join(rendered_lines)
